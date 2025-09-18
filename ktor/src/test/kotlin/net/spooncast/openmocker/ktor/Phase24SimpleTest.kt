package net.spooncast.openmocker.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import net.spooncast.openmocker.core.MemoryMockRepository
import net.spooncast.openmocker.core.MockKey
import net.spooncast.openmocker.core.MockResponse
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase 2.4 simple test suite for response processing and caching logic.
 *
 * This test validates the core functionality implemented in Phase 2.4:
 * - Enhanced response caching logic
 * - Content type detection
 * - Configuration options (cacheFailures)
 * - Plugin enable/disable functionality
 */
class Phase24SimpleTest {

    private lateinit var repository: MemoryMockRepository
    private lateinit var httpClient: HttpClient

    @Before
    fun setup() {
        repository = MemoryMockRepository()

        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/users" -> respond(
                    content = """{"users": [{"id": 1, "name": "John"}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/api/error" -> respond(
                    content = """{"error": "Server Error"}""",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> respond(
                    content = "Not Found",
                    status = HttpStatusCode.NotFound
                )
            }
        }

        httpClient = HttpClient(mockEngine) {
            install(OpenMocker) {
                repository = this@Phase24SimpleTest.repository
                enableLogging = true
                logLevel = OpenMockerConfig.LogLevel.DEBUG
                metricsEnabled = true
                cacheFailures = true
                interceptAll = true
            }
        }
    }

    @Test
    fun testRealResponseCaching() = runBlocking {
        // When: Make request (no mock configured, should cache real response)
        val response = httpClient.get("/api/users")
        val body = response.bodyAsText()

        // Then: Verify real response
        assertEquals(200, response.status.value)
        assertTrue(body.contains("John"))

        // Verify response was cached
        val cachedResponse = repository.getCachedResponse(MockKey("GET", "/api/users"))
        assertNotNull("Response should be cached", cachedResponse)
        assertEquals(200, cachedResponse!!.code)
        assertTrue("Cached response should contain 'John'", cachedResponse.body.contains("John"))
    }

    @Test
    fun testErrorResponseCaching() = runBlocking {
        // When: Make request that returns error (cacheFailures = true)
        val response = httpClient.get("/api/error")
        val body = response.bodyAsText()

        // Then: Verify error response
        assertEquals(500, response.status.value)
        assertTrue(body.contains("Server Error"))

        // Verify error response was cached due to cacheFailures = true
        val cachedResponse = repository.getCachedResponse(MockKey("GET", "/api/error"))
        assertNotNull("Error response should be cached when cacheFailures = true", cachedResponse)
        assertEquals(500, cachedResponse!!.code)
        assertTrue("Cached error should contain error message", cachedResponse.body.contains("Server Error"))
    }

    @Test
    fun testContentTypeDetection() {
        // Test content type detection functionality
        val jsonBody = """{"key": "value"}"""
        val xmlBody = """<?xml version="1.0"?><root><item>value</item></root>"""
        val htmlBody = """<html><body><h1>Hello</h1></body></html>"""
        val plainBody = """Plain text content"""

        // Verify content type detection works correctly
        assertEquals(ContentType.Application.Json, MockHttpCall.detectContentType(jsonBody))
        assertEquals(ContentType.Application.Xml, MockHttpCall.detectContentType(xmlBody))
        assertEquals(ContentType.Text.Html, MockHttpCall.detectContentType(htmlBody))
        assertEquals(ContentType.Text.Plain, MockHttpCall.detectContentType(plainBody))
    }

    @Test
    fun testSelectiveCaching() = runBlocking {
        // Create client with interceptAll = false and cacheFailures = false
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/success" -> respond(
                    content = """{"success": true}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/api/failure" -> respond(
                    content = """{"error": true}""",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> respond("Not Found", HttpStatusCode.NotFound)
            }
        }

        val selectiveClient = HttpClient(mockEngine) {
            install(OpenMocker) {
                repository = this@Phase24SimpleTest.repository
                interceptAll = false  // Only cache when explicitly configured
                cacheFailures = false  // Don't cache error responses
            }
        }

        // Make successful request - should be cached
        val successResponse = selectiveClient.get("/api/success")
        assertEquals(200, successResponse.status.value)

        // Check if success response was cached
        val cachedSuccess = repository.getCachedResponse(MockKey("GET", "/api/success"))
        assertNotNull("Success response should be cached even with interceptAll = false", cachedSuccess)

        // Make error request - should NOT be cached due to cacheFailures = false
        val errorResponse = selectiveClient.get("/api/failure")
        assertEquals(500, errorResponse.status.value)

        // Check that error response was NOT cached
        val cachedError = repository.getCachedResponse(MockKey("GET", "/api/failure"))
        assertNull("Error response should not be cached when cacheFailures = false", cachedError)
    }

    @Test
    fun testDisabledPlugin() = runBlocking {
        // Create client with plugin disabled
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"real": true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val disabledClient = HttpClient(mockEngine) {
            install(OpenMocker) {
                repository = this@Phase24SimpleTest.repository
                isEnabled = false  // Plugin disabled
            }
        }

        // Configure a mock (should be ignored since plugin is disabled)
        repository.saveMock(
            MockKey("GET", "/api/test"),
            MockResponse(code = 999, body = "This should not appear")
        )

        // Make request - should get real response, not mock
        val response = disabledClient.get("/api/test")
        assertEquals(200, response.status.value)
        val body = response.bodyAsText()
        assertTrue("Should get real response", body.contains("real"))
        assertFalse("Should not get mock response", body.contains("This should not appear"))
    }

    @Test
    fun testMockingWithDelay() = runBlocking {
        // Configure mock response with delay
        val mockResponse = MockResponse(
            code = 200,
            body = """{"mocked": true}""",
            delay = 10L  // Small delay to avoid test timing issues
        )
        repository.saveMock(MockKey("GET", "/api/users"), mockResponse)

        // Make request with configured mock
        val response = httpClient.get("/api/users")
        assertEquals(200, response.status.value)
        val body = response.bodyAsText()
        assertTrue("Should get mock response", body.contains("mocked"))

        // Note: We don't test exact delay timing as it's system-dependent
        // The delay implementation is tested through the plugin's internal mechanisms
    }

    @Test
    fun testMockResponseValidation() {
        // Test that MockResponse can be created with valid parameters
        try {
            val validResponse1 = MockResponse(code = 200, body = "OK", delay = 0L)
            assertEquals(200, validResponse1.code)
            assertEquals("OK", validResponse1.body)
            assertEquals(0L, validResponse1.delay)

            val validResponse2 = MockResponse(code = 404, body = "Not Found", delay = 100L)
            assertEquals(404, validResponse2.code)
            assertEquals("Not Found", validResponse2.body)
            assertEquals(100L, validResponse2.delay)
        } catch (e: Exception) {
            fail("Creating MockResponse with valid parameters should not throw exception: ${e.message}")
        }

        // Test content type detection with mock response
        val jsonMockResponse = MockResponse(code = 200, body = """{"test": true}""")
        val detectedContentType = MockHttpCall.detectContentType(jsonMockResponse.body)
        assertEquals("JSON content should be detected correctly", ContentType.Application.Json, detectedContentType)
    }
}