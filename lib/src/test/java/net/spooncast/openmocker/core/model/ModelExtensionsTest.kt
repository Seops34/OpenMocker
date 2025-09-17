package net.spooncast.openmocker.core.model

import net.spooncast.openmocker.lib.model.CachedKey
import net.spooncast.openmocker.lib.model.CachedResponse
import org.junit.Test
import org.junit.Assert.*

class ModelExtensionsTest {

    @Test
    fun `CachedKey toMockKey should convert correctly`() {
        val cachedKey = CachedKey("GET", "/api/users")
        val mockKey = cachedKey.toMockKey()

        assertEquals("GET", mockKey.method)
        assertEquals("/api/users", mockKey.path)
    }

    @Test
    fun `MockKey toCachedKey should convert correctly`() {
        val mockKey = MockKey("POST", "/api/posts")
        val cachedKey = mockKey.toCachedKey()

        assertEquals("POST", cachedKey.method)
        assertEquals("/api/posts", cachedKey.path)
    }

    @Test
    fun `CachedResponse toMockResponse should convert correctly`() {
        val cachedResponse = CachedResponse(200, "Hello World", 1000L)
        val mockResponse = cachedResponse.toMockResponse()

        assertEquals(200, mockResponse.code)
        assertEquals("Hello World", mockResponse.body)
        assertEquals(1000L, mockResponse.delay)
        assertEquals(emptyMap<String, String>(), mockResponse.headers)
    }

    @Test
    fun `MockResponse toCachedResponse should convert correctly`() {
        val headers = mapOf("Content-Type" to "application/json", "X-Custom" to "value")
        val mockResponse = MockResponse(404, "Not Found", 500L, headers)
        val cachedResponse = mockResponse.toCachedResponse()

        assertEquals(404, cachedResponse.code)
        assertEquals("Not Found", cachedResponse.body)
        assertEquals(500L, cachedResponse.duration)
        // headers는 CachedResponse에서 지원하지 않으므로 테스트하지 않음
    }

    @Test
    fun `bidirectional conversion should preserve data`() {
        // MockKey <-> CachedKey
        val originalMockKey = MockKey("PUT", "/api/items/123")
        val convertedBack = originalMockKey.toCachedKey().toMockKey()
        assertEquals(originalMockKey, convertedBack)

        // CachedKey <-> MockKey
        val originalCachedKey = CachedKey("DELETE", "/api/items/456")
        val convertedBackCached = originalCachedKey.toMockKey().toCachedKey()
        assertEquals(originalCachedKey, convertedBackCached)

        // MockResponse <-> CachedResponse (headers 제외)
        val originalMockResponse = MockResponse(201, "Created", 200L)
        val convertedBackMock = originalMockResponse.toCachedResponse().toMockResponse()
        assertEquals(originalMockResponse, convertedBackMock)

        // CachedResponse <-> MockResponse
        val originalCachedResponse = CachedResponse(500, "Internal Server Error", 0L)
        val convertedBackCachedResp = originalCachedResponse.toMockResponse().toCachedResponse()
        assertEquals(originalCachedResponse, convertedBackCachedResp)
    }

    @Test
    fun `conversion with default values should work correctly`() {
        val cachedResponseWithDefaults = CachedResponse(200, "OK")
        val mockResponse = cachedResponseWithDefaults.toMockResponse()

        assertEquals(200, mockResponse.code)
        assertEquals("OK", mockResponse.body)
        assertEquals(0L, mockResponse.delay)
        assertEquals(emptyMap<String, String>(), mockResponse.headers)
    }
}