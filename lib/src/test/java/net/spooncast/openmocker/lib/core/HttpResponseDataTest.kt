package net.spooncast.openmocker.lib.core

import net.spooncast.openmocker.lib.model.CachedResponse
import org.junit.Test
import org.junit.Assert.*

class HttpResponseDataTest {

    @Test
    fun `toCachedResponse should create correct CachedResponse`() {
        // Given
        val responseData = HttpResponseData(
            code = 200,
            body = """{"message": "success"}"""
        )
        val duration = 500L

        // When
        val cachedResponse = responseData.toCachedResponse(duration)

        // Then
        assertEquals(200, cachedResponse.code)
        assertEquals("""{"message": "success"}""", cachedResponse.body)
        assertEquals(500L, cachedResponse.duration)
    }

    @Test
    fun `toCachedResponse with default duration should use zero`() {
        // Given
        val responseData = HttpResponseData(
            code = 404,
            body = """{"error": "not found"}"""
        )

        // When
        val cachedResponse = responseData.toCachedResponse()

        // Then
        assertEquals(404, cachedResponse.code)
        assertEquals("""{"error": "not found"}""", cachedResponse.body)
        assertEquals(0L, cachedResponse.duration)
    }

    @Test
    fun `isSuccessful should return correct status for 2xx codes`() {
        // Given & When & Then
        assertTrue(HttpResponseData(200, "").isSuccessful)
        assertTrue(HttpResponseData(201, "").isSuccessful)
        assertTrue(HttpResponseData(204, "").isSuccessful)
        assertTrue(HttpResponseData(299, "").isSuccessful)

        assertFalse(HttpResponseData(199, "").isSuccessful)
        assertFalse(HttpResponseData(300, "").isSuccessful)
        assertFalse(HttpResponseData(404, "").isSuccessful)
        assertFalse(HttpResponseData(500, "").isSuccessful)
    }

    @Test
    fun `getHeader should return first value case-insensitive`() {
        // Given
        val headers = mapOf(
            "Content-Type" to listOf("application/json", "charset=utf-8"),
            "Server" to listOf("nginx/1.18")
        )
        val responseData = HttpResponseData(
            code = 200,
            body = "{}",
            headers = headers
        )

        // When & Then
        assertEquals("application/json", responseData.getHeader("Content-Type"))
        assertEquals("application/json", responseData.getHeader("content-type"))
        assertEquals("nginx/1.18", responseData.getHeader("SERVER"))
        assertNull(responseData.getHeader("Non-Existent"))
    }

    @Test
    fun `getContentType should return content type header`() {
        // Given
        val headers = mapOf(
            "Content-Type" to listOf("application/json; charset=utf-8")
        )
        val responseData = HttpResponseData(
            code = 200,
            body = "{}",
            headers = headers
        )

        // When & Then
        assertEquals("application/json; charset=utf-8", responseData.getContentType())
    }

    @Test
    fun `isJsonResponse should detect JSON content type`() {
        // Given
        val jsonResponse = HttpResponseData(
            code = 200,
            body = "{}",
            headers = mapOf("Content-Type" to listOf("application/json"))
        )

        val jsonWithCharsetResponse = HttpResponseData(
            code = 200,
            body = "{}",
            headers = mapOf("Content-Type" to listOf("application/json; charset=utf-8"))
        )

        val textResponse = HttpResponseData(
            code = 200,
            body = "Hello",
            headers = mapOf("Content-Type" to listOf("text/plain"))
        )

        val noContentTypeResponse = HttpResponseData(
            code = 200,
            body = "{}"
        )

        // When & Then
        assertTrue(jsonResponse.isJsonResponse())
        assertTrue(jsonWithCharsetResponse.isJsonResponse())
        assertFalse(textResponse.isJsonResponse())
        assertFalse(noContentTypeResponse.isJsonResponse())
    }

    @Test
    fun `constructor should set isSuccessful based on code`() {
        // Given & When
        val successResponse = HttpResponseData(200, "success")
        val notFoundResponse = HttpResponseData(404, "not found")
        val serverErrorResponse = HttpResponseData(500, "server error")

        // Then
        assertTrue(successResponse.isSuccessful)
        assertFalse(notFoundResponse.isSuccessful)
        assertFalse(serverErrorResponse.isSuccessful)
    }

    @Test
    fun `constructor should allow overriding isSuccessful`() {
        // Given & When
        val response = HttpResponseData(
            code = 404,
            body = "not found",
            isSuccessful = true // Override default behavior
        )

        // Then
        assertEquals(404, response.code)
        assertTrue(response.isSuccessful) // Should use provided value
    }
}