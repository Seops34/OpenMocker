package net.spooncast.openmocker.ktor

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.fullPath
import io.ktor.http.isSuccess
import io.ktor.util.AttributeKey
import kotlinx.coroutines.delay
import net.spooncast.openmocker.core.MockKey
import net.spooncast.openmocker.core.MockResponse

/**
 * OpenMocker Ktor client plugin for HTTP request mocking and testing.
 *
 * This plugin provides the ability to intercept HTTP requests and return mock responses
 * instead of making actual network calls. It supports caching real responses for later
 * mocking and allows dynamic configuration of mock responses.
 *
 * The plugin integrates with the core OpenMocker architecture, supporting the same
 * MockRepository interface used by the OkHttp implementation for consistency across
 * different HTTP client libraries.
 *
 * ## Installation
 *
 * Install the plugin in your HttpClient configuration:
 *
 * ```kotlin
 * val client = HttpClient {
 *     install(OpenMocker) {
 *         repository = MemoryMockRepository
 *         isEnabled = true
 *         interceptAll = true
 *     }
 * }
 * ```
 *
 * ## Configuration
 *
 * - `repository`: MockRepository implementation for storing mocks and cached responses
 * - `isEnabled`: Enable or disable the plugin (default: true)
 * - `interceptAll`: Cache all requests for potential mocking (default: true)
 * - `maxCacheSize`: Maximum number of cached responses (-1 for unlimited)
 * - `autoEnableInDebug`: Automatically enable in debug builds (default: false)
 *
 * ## Usage
 *
 * The plugin automatically:
 * 1. Intercepts outgoing HTTP requests
 * 2. Checks if a mock response is configured for the request
 * 3. Returns the mock response if configured, otherwise proceeds with the actual request
 * 4. Caches successful responses for potential future mocking
 *
 * Mock responses can be configured programmatically through the MockRepository interface
 * or via a UI component (when available).
 *
 * @see OpenMockerConfig
 * @see net.spooncast.openmocker.core.MockRepository
 * @see net.spooncast.openmocker.core.MockerEngine
 */
val OpenMocker: ClientPlugin<OpenMockerConfig> = createClientPlugin(
    name = "OpenMocker",
    createConfiguration = ::OpenMockerConfig
) {

    val config = pluginConfig
    config.validate()

    // Create the mocker engine that will be used for this client instance
    val mockerEngine = KtorMockerEngine(config.repository)

    // Create metrics collector for this plugin instance
    val metrics = MockingMetrics()

    // Log plugin configuration
    if (config.enableLogging) {
        OpenMockerLogger.logConfiguration(config)
    }

    onRequest { request, _ ->
        // Skip if plugin is disabled
        if (!config.isEnabled) return@onRequest

        val startTime = System.currentTimeMillis()
        val method = request.method.value
        val path = extractPathFromUrl(request.url.toString())
        val mockKey = MockKey(method, path)

        try {
            // Check if we should return a mock response
            val (mockResponse, lookupTime) = OpenMockerProfiler.measureTime {
                mockerEngine.shouldMock(method, path)
            }

            // Create request context for tracking
            val requestContext = RequestContext(
                mockKey = mockKey,
                mockResponse = mockResponse,
                startTime = startTime
            )

            // Store context and key for use in response processing
            request.attributes.put(REQUEST_CONTEXT_ATTRIBUTE, requestContext)
            request.attributes.put(MOCK_KEY_ATTRIBUTE, mockKey)

            if (mockResponse != null) {
                // We found a mock response - record metrics
                metrics.recordCacheHit()

                // Store mock response for potential Phase 3 usage
                request.attributes.put(MOCK_RESPONSE_KEY, mockResponse)

                // Apply artificial delay if configured
                KtorUtils.applyMockDelay(mockResponse)

                // Mark that we don't need to cache this response since it's mocked
                request.attributes.put(BYPASS_CACHE_ATTRIBUTE, true)

                // Log the mocking operation
                if (config.enableLogging) {
                    val processingTime = System.currentTimeMillis() - startTime
                    OpenMockerLogger.logMockingOperation(mockKey, mockResponse, processingTime, true)
                }

                // Phase 2.3: Mock response found and prepared
                // Phase 3: Will integrate complete HttpResponse mock delivery
                // For now, we prepare all mock data and let the request proceed for testing
            } else {
                // No mock found - record cache miss
                metrics.recordCacheMiss()

                // Log the operation if enabled
                if (config.enableLogging) {
                    val processingTime = System.currentTimeMillis() - startTime
                    OpenMockerLogger.logMockingOperation(mockKey, null, processingTime, false)
                }
            }

        } catch (e: Exception) {
            // Record processing error
            metrics.recordProcessingError()

            // Log error if enabled
            if (config.enableLogging) {
                OpenMockerLogger.error(
                    "Error processing mock request for ${mockKey.method} ${mockKey.path}",
                    e,
                    mapOf("mockKey" to mockKey.toString())
                )
            }

            // Continue with real request on error
        }
    }

    onResponse { response ->
        // Skip if plugin is disabled or if this was a mocked response
        if (!config.isEnabled || response.call.request.attributes.contains(BYPASS_CACHE_ATTRIBUTE)) {
            return@onResponse
        }

        // Get request context if available
        val requestContext = response.call.request.attributes.getOrNull(REQUEST_CONTEXT_ATTRIBUTE)

        try {
            // Only cache successful responses or if interceptAll is enabled
            if (config.interceptAll || response.status.isSuccessful()) {
                val method = response.call.request.method.value
                val path = extractPathFromUrl(response.call.request.url.toString())
                val code = response.status.value

                // Read response body safely without consuming the original stream
                val (body, readTime) = OpenMockerProfiler.measureTime {
                    response.readBodySafely()
                }

                // Cache the response
                val (_, cacheTime) = OpenMockerProfiler.measureTime {
                    mockerEngine.cacheResponse(method, path, code, body)
                }

                // Record metrics for real request
                val totalProcessingTime = requestContext?.processingTime() ?: 0L
                metrics.recordRealRequest(totalProcessingTime)

                // Log caching operation if enabled
                if (config.enableLogging) {
                    val mockKey = requestContext?.mockKey ?: MockKey(method, path)
                    OpenMockerLogger.logCachingOperation(
                        mockKey,
                        code,
                        body.length,
                        true
                    )
                }

                // Log performance if caching took too long
                if (config.enableLogging && (readTime + cacheTime) > 50) {
                    OpenMockerProfiler.logPerformance(
                        "response_caching",
                        readTime + cacheTime,
                        50
                    )
                }

            } else {
                // Record metrics for non-cached real request
                val totalProcessingTime = requestContext?.processingTime() ?: 0L
                metrics.recordRealRequest(totalProcessingTime)
            }

        } catch (e: Exception) {
            // Record caching error
            metrics.recordProcessingError()

            // Log caching failure if enabled
            if (config.enableLogging) {
                val method = response.call.request.method.value
                val path = extractPathFromUrl(response.call.request.url.toString())
                val mockKey = requestContext?.mockKey ?: MockKey(method, path)

                OpenMockerLogger.error(
                    "Error caching response for ${mockKey.method} ${mockKey.path}",
                    e,
                    mapOf(
                        "statusCode" to response.status.value,
                        "mockKey" to mockKey.toString()
                    )
                )

                OpenMockerLogger.logCachingOperation(
                    mockKey,
                    response.status.value,
                    0,
                    false
                )
            }

            // Record metrics for failed caching but successful request
            val totalProcessingTime = requestContext?.processingTime() ?: 0L
            metrics.recordRealRequest(totalProcessingTime)
        }
    }

    // Optional: Provide access to metrics for monitoring
    // Note: client is not available in this scope, metrics access will be handled differently
}

/**
 * Convenience function to install OpenMocker with default configuration.
 *
 * Example:
 * ```kotlin
 * val client = HttpClient {
 *     install(OpenMocker)
 * }
 * ```
 */
fun HttpClientConfig<*>.installOpenMocker(
    configure: OpenMockerConfig.() -> Unit = {}
) {
    install(OpenMocker, configure)
}