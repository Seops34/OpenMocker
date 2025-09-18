package net.spooncast.openmocker.ktor

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.util.AttributeKey
import net.spooncast.openmocker.core.MockKey
import net.spooncast.openmocker.core.MockResponse
import java.util.concurrent.atomic.AtomicLong

/**
 * Constants and internal types for OpenMocker Ktor plugin.
 *
 * This file defines all the AttributeKeys, internal data classes, and utility classes
 * needed for request/response processing in the OpenMocker plugin.
 *
 * The constants follow Ktor's conventions for plugin development and provide
 * type-safe data passing between onRequest and onResponse hooks.
 */

/**
 * AttributeKey for storing the mock response that should be returned for a request.
 *
 * When a mock response is found for a request in the onRequest hook, it's stored
 * using this key for later retrieval in the response processing pipeline.
 */
internal val MOCK_RESPONSE_KEY = AttributeKey<MockResponse>("OpenMocker.MockResponse")

/**
 * AttributeKey for storing the mock key (method + path) for the current request.
 *
 * This helps with debugging, logging, and maintaining consistency between
 * request and response processing phases.
 */
internal val MOCK_KEY_ATTRIBUTE = AttributeKey<MockKey>("OpenMocker.MockKey")

/**
 * AttributeKey for storing the original request data for processing.
 *
 * This preserves the original request information for use in response processing,
 * ensuring we have access to all request details even after modifications.
 */
internal val ORIGINAL_REQUEST_ATTRIBUTE = AttributeKey<HttpRequestBuilder>("OpenMocker.OriginalRequest")

/**
 * AttributeKey for storing the request processing context.
 *
 * This contains comprehensive information about the request processing state,
 * including timing, mocking status, and other metadata.
 */
internal val REQUEST_CONTEXT_ATTRIBUTE = AttributeKey<RequestContext>("OpenMocker.RequestContext")

/**
 * AttributeKey for tracking whether a request should bypass caching.
 *
 * Used to prevent caching of mocked responses since they don't represent
 * real server responses and shouldn't be stored for future mocking.
 */
internal val BYPASS_CACHE_ATTRIBUTE = AttributeKey<Boolean>("OpenMocker.BypassCache")

/**
 * Request processing context containing state and metadata for each request.
 *
 * This class maintains all the information needed to track a request through
 * the OpenMocker processing pipeline, from initial interception through
 * final response delivery.
 *
 * @property mockKey The MockKey identifying this request
 * @property mockResponse The mock response to return, if any
 * @property startTime Timestamp when request processing began
 * @property isMocked Whether this request will return a mock response
 * @property processingTime Time spent in mock processing (calculated lazily)
 */
internal data class RequestContext(
    val mockKey: MockKey,
    val mockResponse: MockResponse?,
    val startTime: Long = System.currentTimeMillis()
) {
    /**
     * Whether this request will be mocked (has a mock response).
     */
    val isMocked: Boolean get() = mockResponse != null

    /**
     * Calculate the processing time from start to now.
     *
     * @return Processing time in milliseconds
     */
    fun processingTime(): Long = System.currentTimeMillis() - startTime

    /**
     * Create a copy with updated mock response information.
     *
     * @param newMockResponse The new mock response to set
     * @return Updated RequestContext
     */
    fun withMockResponse(newMockResponse: MockResponse?): RequestContext {
        return copy(mockResponse = newMockResponse)
    }
}

/**
 * Metrics collection system for tracking mocking operations.
 *
 * This class provides thread-safe counters and metrics for monitoring
 * the behavior and performance of the OpenMocker plugin in production.
 *
 * All operations are atomic and lock-free for high-performance concurrent access.
 */
internal class MockingMetrics {

    private val mockedRequests = AtomicLong(0)
    private val realRequests = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val cacheHits = AtomicLong(0)
    private val processingErrors = AtomicLong(0)
    private val totalProcessingTime = AtomicLong(0)

    /**
     * Record that a request was mocked.
     *
     * @param processingTime Time spent processing this mock in milliseconds
     * @return New total count of mocked requests
     */
    fun recordMockedRequest(processingTime: Long = 0): Long {
        if (processingTime > 0) {
            totalProcessingTime.addAndGet(processingTime)
        }
        return mockedRequests.incrementAndGet()
    }

    /**
     * Record that a request proceeded with real HTTP execution.
     *
     * @param processingTime Time spent processing this request in milliseconds
     * @return New total count of real requests
     */
    fun recordRealRequest(processingTime: Long = 0): Long {
        if (processingTime > 0) {
            totalProcessingTime.addAndGet(processingTime)
        }
        return realRequests.incrementAndGet()
    }

    /**
     * Record a cache miss (no mock found for request).
     *
     * @return New total count of cache misses
     */
    fun recordCacheMiss(): Long = cacheMisses.incrementAndGet()

    /**
     * Record a cache hit (mock found for request).
     *
     * @return New total count of cache hits
     */
    fun recordCacheHit(): Long = cacheHits.incrementAndGet()

    /**
     * Record a processing error during mock operations.
     *
     * @return New total count of processing errors
     */
    fun recordProcessingError(): Long = processingErrors.incrementAndGet()

    /**
     * Calculate the mocking ratio (percentage of requests that were mocked).
     *
     * @return Ratio between 0.0 and 1.0, or 0.0 if no requests processed
     */
    fun getMockingRatio(): Double {
        val total = mockedRequests.get() + realRequests.get()
        return if (total > 0) mockedRequests.get().toDouble() / total else 0.0
    }

    /**
     * Calculate the cache hit ratio (percentage of mock lookups that found results).
     *
     * @return Ratio between 0.0 and 1.0, or 0.0 if no lookups performed
     */
    fun getCacheHitRatio(): Double {
        val total = cacheHits.get() + cacheMisses.get()
        return if (total > 0) cacheHits.get().toDouble() / total else 0.0
    }

    /**
     * Get the average processing time per request.
     *
     * @return Average processing time in milliseconds, or 0.0 if no requests processed
     */
    fun getAverageProcessingTime(): Double {
        val total = mockedRequests.get() + realRequests.get()
        return if (total > 0) totalProcessingTime.get().toDouble() / total else 0.0
    }

    /**
     * Get current metrics snapshot.
     *
     * @return MetricsSnapshot containing all current metrics
     */
    fun getSnapshot(): MetricsSnapshot {
        return MetricsSnapshot(
            mockedRequests = mockedRequests.get(),
            realRequests = realRequests.get(),
            cacheMisses = cacheMisses.get(),
            cacheHits = cacheHits.get(),
            processingErrors = processingErrors.get(),
            totalProcessingTime = totalProcessingTime.get(),
            mockingRatio = getMockingRatio(),
            cacheHitRatio = getCacheHitRatio(),
            averageProcessingTime = getAverageProcessingTime()
        )
    }

    /**
     * Reset all metrics to zero.
     *
     * Useful for testing and periodic metric resets in production.
     */
    fun reset() {
        mockedRequests.set(0)
        realRequests.set(0)
        cacheMisses.set(0)
        cacheHits.set(0)
        processingErrors.set(0)
        totalProcessingTime.set(0)
    }
}

/**
 * Immutable snapshot of metrics at a point in time.
 *
 * This data class provides a consistent view of all metrics values,
 * useful for reporting and monitoring purposes.
 *
 * @property mockedRequests Total number of mocked requests
 * @property realRequests Total number of real HTTP requests
 * @property cacheMisses Total number of cache misses
 * @property cacheHits Total number of cache hits
 * @property processingErrors Total number of processing errors
 * @property totalProcessingTime Total processing time across all requests
 * @property mockingRatio Ratio of mocked to total requests
 * @property cacheHitRatio Ratio of cache hits to total lookups
 * @property averageProcessingTime Average processing time per request
 */
data class MetricsSnapshot(
    val mockedRequests: Long,
    val realRequests: Long,
    val cacheMisses: Long,
    val cacheHits: Long,
    val processingErrors: Long,
    val totalProcessingTime: Long,
    val mockingRatio: Double,
    val cacheHitRatio: Double,
    val averageProcessingTime: Double
) {
    /**
     * Total number of requests processed.
     */
    val totalRequests: Long get() = mockedRequests + realRequests

    /**
     * Total number of cache operations performed.
     */
    val totalCacheOperations: Long get() = cacheHits + cacheMisses

    /**
     * Format metrics as a human-readable string.
     *
     * @return Formatted metrics string
     */
    override fun toString(): String {
        return "OpenMocker Metrics: " +
            "requests=${totalRequests} " +
            "(${String.format("%.1f", mockingRatio * 100)}% mocked), " +
            "cache=${String.format("%.1f", cacheHitRatio * 100)}% hits, " +
            "avg_time=${String.format("%.2f", averageProcessingTime)}ms, " +
            "errors=${processingErrors}"
    }
}