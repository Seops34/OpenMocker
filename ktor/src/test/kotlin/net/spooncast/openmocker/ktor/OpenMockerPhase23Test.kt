package net.spooncast.openmocker.ktor

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import net.spooncast.openmocker.core.MemoryMockRepository
import net.spooncast.openmocker.core.MockKey
import net.spooncast.openmocker.core.MockResponse
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assert.*

/**
 * Phase 2.3 functionality tests for OpenMocker onRequest hook.
 *
 * This test class validates the specific requirements from Phase 2.3:
 * - onRequest hook implementation
 * - AttributeKey data passing between hooks
 * - Request context management
 * - Logging and metrics system
 */
class OpenMockerPhase23Test {

    private lateinit var repository: MemoryMockRepository
    private lateinit var mockEngine: MockEngine

    @Before
    fun setup() {
        repository = MemoryMockRepository()
        mockEngine = MockEngine { request ->
            respond(
                content = """{"message": "real response"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    }

    @After
    fun cleanup() = runTest {
        repository.clearAll()
    }

    /**
     * Test that onRequest hook correctly processes requests without mocks
     * and sets appropriate AttributeKey values for tracking.
     */
    @Test
    fun `onRequest hook processes request without mock and sets tracking attributes`() = runTest {
        // Given
        val client = HttpClient(mockEngine) {
            install(OpenMocker) {
                this.repository = this@OpenMockerPhase23Test.repository
                isEnabled = true
                enableLogging = true
            }
        }

        // When
        val response = client.get("https://api.example.com/users")

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        // Verify tracking attributes were set
        val mockKey = response.call.request.attributes.getOrNull(MOCK_KEY_ATTRIBUTE)
        assertNotNull("Mock key should be set for tracking", mockKey)
        assertEquals("Method should match request", "GET", mockKey!!.method)
        assertEquals("Path should be extracted correctly", "/users", mockKey.path)

        // Verify request context was created
        val requestContext = response.call.request.attributes.getOrNull(REQUEST_CONTEXT_ATTRIBUTE)
        assertNotNull("Request context should be created", requestContext)
        assertEquals("Context should reference correct mock key", mockKey, requestContext!!.mockKey)
        assertNull("Context should not have mock response", requestContext.mockResponse)
        assertFalse("Context should not indicate mocked request", requestContext.isMocked)

        // Verify no mock response was stored (since none exists)
        val storedMockResponse = response.call.request.attributes.getOrNull(MOCK_RESPONSE_KEY)
        assertNull("No mock response should be stored when none configured", storedMockResponse)

        client.close()
    }

    /**
     * Test that onRequest hook finds configured mocks and prepares them
     * with proper AttributeKey data passing.
     */
    @Test
    fun `onRequest hook finds mock and prepares response with attributes`() = runTest {
        // Given
        val mockKey = MockKey("GET", "/api/data")
        val mockResponse = MockResponse(200, """{"data": "mocked"}""", 50L)
        repository.saveMock(mockKey, mockResponse)

        val client = HttpClient(mockEngine) {
            install(OpenMocker) {
                this.repository = this@OpenMockerPhase23Test.repository
                isEnabled = true
                enableLogging = true
            }
        }

        // When
        val response = client.get("https://api.example.com/api/data")

        // Then
        // Verify mock response was stored in attributes
        val storedMockResponse = response.call.request.attributes.getOrNull(MOCK_RESPONSE_KEY)
        assertNotNull("Mock response should be stored in attributes", storedMockResponse)
        assertEquals("Mock response code should match", 200, storedMockResponse!!.code)
        assertEquals("Mock response body should match", """{"data": "mocked"}""", storedMockResponse.body)
        assertEquals("Mock response delay should match", 50L, storedMockResponse.delay)

        // Verify bypass cache attribute was set
        val bypassCache = response.call.request.attributes.getOrNull(BYPASS_CACHE_ATTRIBUTE)
        assertNotNull("Bypass cache should be set for mocked requests", bypassCache)
        assertTrue("Bypass cache should be true", bypassCache!!)

        // Verify request context shows mocked status
        val requestContext = response.call.request.attributes.getOrNull(REQUEST_CONTEXT_ATTRIBUTE)
        assertNotNull("Request context should exist", requestContext)
        assertTrue("Request context should indicate mocked request", requestContext!!.isMocked)
        assertEquals("Context mock response should match stored response", mockResponse, requestContext.mockResponse)

        client.close()
    }

    /**
     * Test that plugin respects the disabled state and doesn't process requests.
     */
    @Test
    fun `disabled plugin skips request processing and attribute setting`() = runTest {
        // Given
        val mockKey = MockKey("GET", "/api/data")
        val mockResponse = MockResponse(200, """{"data": "mocked"}""", 0L)
        repository.saveMock(mockKey, mockResponse)

        val client = HttpClient(mockEngine) {
            install(OpenMocker) {
                this.repository = this@OpenMockerPhase23Test.repository
                isEnabled = false  // Plugin disabled
                enableLogging = false
            }
        }

        // When
        val response = client.get("https://api.example.com/api/data")

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        // Verify no OpenMocker attributes were set
        val mockKeyAttr = response.call.request.attributes.getOrNull(MOCK_KEY_ATTRIBUTE)
        val requestContext = response.call.request.attributes.getOrNull(REQUEST_CONTEXT_ATTRIBUTE)
        val storedMockResponse = response.call.request.attributes.getOrNull(MOCK_RESPONSE_KEY)
        val bypassCache = response.call.request.attributes.getOrNull(BYPASS_CACHE_ATTRIBUTE)

        assertNull("Mock key should not be set when plugin disabled", mockKeyAttr)
        assertNull("Request context should not be created when plugin disabled", requestContext)
        assertNull("Mock response should not be stored when plugin disabled", storedMockResponse)
        assertNull("Bypass cache should not be set when plugin disabled", bypassCache)

        client.close()
    }

    /**
     * Test that the onRequest hook correctly handles different HTTP methods
     * and creates method-specific mock keys.
     */
    @Test
    fun `onRequest hook creates correct mock keys for different HTTP methods`() = runTest {
        // Given
        val getMockKey = MockKey("GET", "/api/resource")
        val postMockKey = MockKey("POST", "/api/resource")
        repository.saveMock(getMockKey, MockResponse(200, """{"method": "GET"}"""))
        repository.saveMock(postMockKey, MockResponse(201, """{"method": "POST"}"""))

        val client = HttpClient(mockEngine) {
            install(OpenMocker) {
                this.repository = this@OpenMockerPhase23Test.repository
                isEnabled = true
            }
        }

        // When
        val getResponse = client.get("https://api.example.com/api/resource")
        val postResponse = client.post("https://api.example.com/api/resource")

        // Then
        // Verify GET request
        val getStoredMock = getResponse.call.request.attributes.getOrNull(MOCK_RESPONSE_KEY)
        assertNotNull("GET mock should be found", getStoredMock)
        assertEquals("GET mock body should match", """{"method": "GET"}""", getStoredMock!!.body)

        val getMockKeyAttr = getResponse.call.request.attributes.getOrNull(MOCK_KEY_ATTRIBUTE)
        assertNotNull("GET mock key should be set", getMockKeyAttr)
        assertEquals("GET method should be correct", "GET", getMockKeyAttr!!.method)

        // Verify POST request
        val postStoredMock = postResponse.call.request.attributes.getOrNull(MOCK_RESPONSE_KEY)
        assertNotNull("POST mock should be found", postStoredMock)
        assertEquals("POST mock body should match", """{"method": "POST"}""", postStoredMock!!.body)

        val postMockKeyAttr = postResponse.call.request.attributes.getOrNull(MOCK_KEY_ATTRIBUTE)
        assertNotNull("POST mock key should be set", postMockKeyAttr)
        assertEquals("POST method should be correct", "POST", postMockKeyAttr!!.method)

        client.close()
    }

    /**
     * Test that mock delay information is stored correctly in attributes.
     * Note: In runTest, actual timing is virtual, so we only verify the delay value is stored.
     */
    @Test
    fun `onRequest hook stores mock delay information correctly`() = runTest {
        // Given
        val mockKey = MockKey("GET", "/slow")
        val mockResponse = MockResponse(200, """{"slow": "response"}""", 100L) // 100ms delay
        repository.saveMock(mockKey, mockResponse)

        val client = HttpClient(mockEngine) {
            install(OpenMocker) {
                this.repository = this@OpenMockerPhase23Test.repository
                isEnabled = true
            }
        }

        // When
        val response = client.get("https://api.example.com/slow")

        // Then
        // Verify mock response was prepared with correct delay information
        val storedMockResponse = response.call.request.attributes.getOrNull(MOCK_RESPONSE_KEY)
        assertNotNull("Mock response should be stored", storedMockResponse)
        assertEquals("Mock delay should match configured value", 100L, storedMockResponse!!.delay)
        assertEquals("Mock response code should match", 200, storedMockResponse.code)
        assertEquals("Mock response body should match", """{"slow": "response"}""", storedMockResponse.body)

        // Verify request context indicates mocked request with delay
        val requestContext = response.call.request.attributes.getOrNull(REQUEST_CONTEXT_ATTRIBUTE)
        assertNotNull("Request context should exist", requestContext)
        assertTrue("Request should be marked as mocked", requestContext!!.isMocked)
        assertEquals("Context should reference the correct mock response", mockResponse, requestContext.mockResponse)

        client.close()
    }
}