package net.spooncast.openmocker.lib.core

import io.mockk.*
import kotlinx.coroutines.test.runTest
import net.spooncast.openmocker.lib.model.CachedKey
import net.spooncast.openmocker.lib.model.CachedResponse
import net.spooncast.openmocker.lib.model.CachedValue
import net.spooncast.openmocker.lib.repo.CacheRepo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class MockingEngineTest {

    private val mockCacheRepo: CacheRepo = mockk()
    private val mockAdapter: HttpClientAdapter<String, String> = mockk()
    private lateinit var mockingEngine: MockingEngine<String, String>

    @Before
    fun setUp() {
        mockingEngine = MockingEngine(mockCacheRepo, mockAdapter)
        clearAllMocks()
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `checkForMock should return null when no mock exists`() = runTest {
        // Given
        val request = "test-request"
        val requestData = HttpRequestData("GET", "/api/test", "https://api.example.com/api/test")
        val cachedKey = CachedKey("GET", "/api/test")

        every { mockAdapter.extractRequestData(request) } returns requestData
        every { mockCacheRepo.cachedMap } returns mapOf<CachedKey, CachedValue>()

        // When
        val result = mockingEngine.checkForMock(request)

        // Then
        assertNull(result)
        verify { mockAdapter.extractRequestData(request) }
    }

    @Test
    fun `checkForMock should return mock response when mock exists`() = runTest {
        // Given
        val request = "test-request"
        val requestData = HttpRequestData("GET", "/api/test", "https://api.example.com/api/test")
        val cachedKey = CachedKey("GET", "/api/test")
        val originalResponse = CachedResponse(200, """{"original": true}""")
        val mockResponse = CachedResponse(201, """{"mocked": true}""", 0L)
        val cachedValue = CachedValue(response = originalResponse, mock = mockResponse)
        val expectedMockResult = "mocked-response"

        every { mockAdapter.extractRequestData(request) } returns requestData
        every { mockCacheRepo.cachedMap } returns mapOf(cachedKey to cachedValue)
        every { mockAdapter.createMockResponse(request, mockResponse) } returns expectedMockResult

        // When
        val result = mockingEngine.checkForMock(request)

        // Then
        assertEquals(expectedMockResult, result)
        verify { mockAdapter.extractRequestData(request) }
        verify { mockAdapter.createMockResponse(request, mockResponse) }
    }

    @Test
    fun `checkForMock should return mock response when mock has duration`() = runTest {
        // Given
        val request = "test-request"
        val requestData = HttpRequestData("GET", "/api/test", "https://api.example.com/api/test")
        val cachedKey = CachedKey("GET", "/api/test")
        val originalResponse = CachedResponse(200, "original")
        val mockResponse = CachedResponse(200, "delayed response", 100L) // 100ms delay
        val cachedValue = CachedValue(response = originalResponse, mock = mockResponse)
        val expectedMockResult = "delayed-mock-response"

        every { mockAdapter.extractRequestData(request) } returns requestData
        every { mockCacheRepo.cachedMap } returns mapOf(cachedKey to cachedValue)
        every { mockAdapter.createMockResponse(request, mockResponse) } returns expectedMockResult

        // When
        val result = mockingEngine.checkForMock(request)

        // Then
        assertEquals(expectedMockResult, result)
        verify { mockAdapter.createMockResponse(request, mockResponse) }
    }

    @Test
    fun `checkForMockSync should return mock response without delay`() {
        // Given
        val request = "test-request"
        val requestData = HttpRequestData("GET", "/api/test", "https://api.example.com/api/test")
        val mockResponse = CachedResponse(200, "sync response", 1000L) // Long delay
        val expectedMockResult = "sync-mock-response"

        every { mockAdapter.extractRequestData(request) } returns requestData
        every { mockCacheRepo.getMock("GET", "/api/test") } returns mockResponse
        every { mockAdapter.createMockResponse(request, mockResponse) } returns expectedMockResult

        val startTime = System.currentTimeMillis()

        // When
        val result = mockingEngine.checkForMockSync(request)

        // Then
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        assertEquals(expectedMockResult, result)
        assertTrue("Should not apply delay in sync version", duration < 50)
        verify { mockAdapter.createMockResponse(request, mockResponse) }
    }

    @Test
    fun `checkForMockSync should return null when no mock exists`() {
        // Given
        val request = "test-request"
        val requestData = HttpRequestData("GET", "/api/test", "https://api.example.com/api/test")

        every { mockAdapter.extractRequestData(request) } returns requestData
        every { mockCacheRepo.getMock("GET", "/api/test") } returns null

        // When
        val result = mockingEngine.checkForMockSync(request)

        // Then
        assertNull(result)
        verify { mockAdapter.extractRequestData(request) }
        verify { mockCacheRepo.getMock("GET", "/api/test") }
        verify(exactly = 0) { mockAdapter.createMockResponse(any(), any()) }
    }

    @Test
    fun `cacheResponse should store response in repository`() {
        // Given
        val request = "test-request"
        val response = "test-response"
        val requestData = HttpRequestData("POST", "/api/create", "https://api.example.com/api/create")
        val responseData = HttpResponseData(201, """{"id": 123}""")

        every { mockAdapter.extractRequestData(request) } returns requestData
        every { mockAdapter.extractResponseData(response) } returns responseData
        every { mockCacheRepo.cache(any(), any(), any(), any()) } just Runs

        // When
        mockingEngine.cacheResponse(request, response)

        // Then
        verify { mockAdapter.extractRequestData(request) }
        verify { mockAdapter.extractResponseData(response) }
        verify {
            mockCacheRepo.cache(
                method = "POST",
                urlPath = "/api/create",
                responseCode = 201,
                responseBody = """{"id": 123}"""
            )
        }
    }

    @Test
    fun `getClientType should return adapter client type`() {
        // Given
        every { mockAdapter.clientType } returns "TestClient"

        // When
        val clientType = mockingEngine.getClientType()

        // Then
        assertEquals("TestClient", clientType)
        verify { mockAdapter.clientType }
    }

    @Test
    fun `canHandle should delegate to adapter isSupported method`() {
        // Given
        val request = "test-request"
        val response = "test-response"

        every { mockAdapter.isSupported(request, response) } returns true

        // When
        val canHandle = mockingEngine.canHandle(request, response)

        // Then
        assertTrue(canHandle)
        verify { mockAdapter.isSupported(request, response) }
    }

    @Test
    fun `applyMockDelay should complete when duration is greater than zero`() = runTest {
        // Given
        val mockResponse = CachedResponse(200, "test", 100L)

        // When & Then
        // Should complete without throwing exception
        mockingEngine.applyMockDelay(mockResponse)

        // Test passes if no exception is thrown
        assertTrue("applyMockDelay should complete successfully", true)
    }

    @Test
    fun `applyMockDelay should complete when duration is zero`() = runTest {
        // Given
        val mockResponse = CachedResponse(200, "test", 0L)

        // When & Then
        // Should complete without throwing exception
        mockingEngine.applyMockDelay(mockResponse)

        // Test passes if no exception is thrown
        assertTrue("applyMockDelay should complete successfully", true)
    }
}