package net.spooncast.openmocker.lib.core

import net.spooncast.openmocker.lib.model.CachedKey
import org.junit.Test
import org.junit.Assert.*

class HttpRequestDataTest {

    @Test
    fun `toCachedKey should create correct CachedKey`() {
        // Given
        val requestData = HttpRequestData(
            method = "GET",
            path = "/api/v1/users",
            url = "https://api.example.com/api/v1/users"
        )

        // When
        val cachedKey = requestData.toCachedKey()

        // Then
        assertEquals("GET", cachedKey.method)
        assertEquals("/api/v1/users", cachedKey.path)
    }

    @Test
    fun `getHeader should return first value case-insensitive`() {
        // Given
        val headers = mapOf(
            "Content-Type" to listOf("application/json", "charset=utf-8"),
            "Authorization" to listOf("Bearer token123")
        )
        val requestData = HttpRequestData(
            method = "POST",
            path = "/api/v1/data",
            url = "https://api.example.com/api/v1/data",
            headers = headers
        )

        // When & Then
        assertEquals("application/json", requestData.getHeader("Content-Type"))
        assertEquals("application/json", requestData.getHeader("content-type"))
        assertEquals("Bearer token123", requestData.getHeader("AUTHORIZATION"))
        assertNull(requestData.getHeader("Non-Existent"))
    }

    @Test
    fun `getHeaders should return all values case-insensitive`() {
        // Given
        val headers = mapOf(
            "Accept" to listOf("application/json", "text/plain"),
            "Custom-Header" to listOf("value1")
        )
        val requestData = HttpRequestData(
            method = "GET",
            path = "/api/test",
            url = "https://api.example.com/api/test",
            headers = headers
        )

        // When & Then
        val acceptHeaders = requestData.getHeaders("accept")
        assertEquals(2, acceptHeaders.size)
        assertTrue(acceptHeaders.contains("application/json"))
        assertTrue(acceptHeaders.contains("text/plain"))

        val customHeaders = requestData.getHeaders("CUSTOM-HEADER")
        assertEquals(1, customHeaders.size)
        assertEquals("value1", customHeaders[0])

        val nonExistentHeaders = requestData.getHeaders("Non-Existent")
        assertTrue(nonExistentHeaders.isEmpty())
    }

    @Test
    fun `should handle empty headers map`() {
        // Given
        val requestData = HttpRequestData(
            method = "GET",
            path = "/api/test",
            url = "https://api.example.com/api/test",
            headers = emptyMap()
        )

        // When & Then
        assertNull(requestData.getHeader("Any-Header"))
        assertTrue(requestData.getHeaders("Any-Header").isEmpty())
    }
}