package net.spooncast.openmocker.lib.client.okhttp

import net.spooncast.openmocker.lib.model.CachedResponse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.Assert.*

class OkHttpAdapterTest {

    private val adapter = OkHttpAdapter()

    @Test
    fun `clientType should return OkHttp`() {
        assertEquals("OkHttp", adapter.clientType)
    }

    @Test
    fun `extractRequestData should convert OkHttp Request correctly`() {
        // Given
        val request = Request.Builder()
            .url("https://api.example.com/api/v1/users?page=1")
            .method("GET", null)
            .addHeader("Authorization", "Bearer token123")
            .addHeader("Accept", "application/json")
            .build()

        // When
        val requestData = adapter.extractRequestData(request)

        // Then
        assertEquals("GET", requestData.method)
        assertEquals("/api/v1/users", requestData.path)
        assertEquals("https://api.example.com/api/v1/users?page=1", requestData.url)
        assertEquals("Bearer token123", requestData.getHeader("Authorization"))
        assertEquals("application/json", requestData.getHeader("Accept"))
    }

    @Test
    fun `extractRequestData should handle request with multiple headers`() {
        // Given
        val requestBody = "test body".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.example.com/api/v1/data")
            .method("POST", requestBody)
            .addHeader("Accept", "application/json")
            .addHeader("Accept", "text/plain")
            .addHeader("Custom-Header", "value1")
            .build()

        // When
        val requestData = adapter.extractRequestData(request)

        // Then
        assertEquals("POST", requestData.method)
        assertEquals("/api/v1/data", requestData.path)

        val acceptHeaders = requestData.getHeaders("Accept")
        assertEquals(2, acceptHeaders.size)
        assertTrue(acceptHeaders.contains("application/json"))
        assertTrue(acceptHeaders.contains("text/plain"))

        assertEquals("value1", requestData.getHeader("Custom-Header"))
    }

    @Test
    fun `createMockResponse should create proper OkHttp Response`() {
        // Given
        val originalRequest = Request.Builder()
            .url("https://api.example.com/api/v1/test")
            .build()

        val mockResponse = CachedResponse(
            code = 201,
            body = """{"id": 123, "name": "test"}""",
            duration = 1000L
        )

        // When
        val response = adapter.createMockResponse(originalRequest, mockResponse)

        // Then
        assertEquals(201, response.code)
        assertEquals("OpenMocker enabled", response.message)
        assertEquals(Protocol.HTTP_2, response.protocol)
        assertEquals(originalRequest, response.request)
        assertEquals("""{"id": 123, "name": "test"}""", response.body?.string())
    }

    @Test
    fun `extractResponseData should convert OkHttp Response correctly`() {
        // Given
        val request = Request.Builder()
            .url("https://api.example.com/test")
            .build()

        val responseBody = """{"message": "success"}""".toResponseBody("application/json".toMediaType())
        val headers = Headers.Builder()
            .add("Content-Type", "application/json; charset=utf-8")
            .add("Server", "nginx/1.18")
            .add("Custom-Header", "value1")
            .add("Custom-Header", "value2")
            .build()

        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .headers(headers)
            .body(responseBody)
            .build()

        // When
        val responseData = adapter.extractResponseData(response)

        // Then
        assertEquals(200, responseData.code)
        assertEquals("""{"message": "success"}""", responseData.body)
        assertTrue(responseData.isSuccessful)
        assertEquals("application/json; charset=utf-8", responseData.getHeader("Content-Type"))
        assertEquals("nginx/1.18", responseData.getHeader("Server"))

        val customHeaders = responseData.getHeaders("Custom-Header")
        assertEquals(2, customHeaders.size)
        assertTrue(customHeaders.contains("value1"))
        assertTrue(customHeaders.contains("value2"))
    }

    @Test
    fun `extractResponseData should handle response with no body`() {
        // Given
        val request = Request.Builder()
            .url("https://api.example.com/test")
            .build()

        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(204)
            .message("No Content")
            .body("".toResponseBody())
            .build()

        // When
        val responseData = adapter.extractResponseData(response)

        // Then
        assertEquals(204, responseData.code)
        assertEquals("", responseData.body)
        assertTrue(responseData.isSuccessful)
    }

    @Test
    fun `isSupported should validate OkHttp types correctly`() {
        // Given
        val request = Request.Builder().url("https://example.com").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .body("".toResponseBody())
            .build()

        // When & Then
        assertTrue(adapter.isSupported(request, response))
        assertFalse(adapter.isSupported(request, "not a response"))
        assertFalse(adapter.isSupported("not a request", response))
        assertFalse(adapter.isSupported(null, response))
        assertFalse(adapter.isSupported(request, null))
    }

    @Test
    fun `canHandleRequest should validate OkHttp Request type`() {
        // Given
        val request = Request.Builder().url("https://example.com").build()

        // When & Then
        assertTrue(adapter.canHandleRequest(request))
        assertFalse(adapter.canHandleRequest("not a request"))
        assertFalse(adapter.canHandleRequest(123))
    }

    @Test
    fun `canHandleResponse should validate OkHttp Response type`() {
        // Given
        val request = Request.Builder().url("https://example.com").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .body("".toResponseBody())
            .build()

        // When & Then
        assertTrue(adapter.canHandleResponse(response))
        assertFalse(adapter.canHandleResponse("not a response"))
        assertFalse(adapter.canHandleResponse(456))
    }
}