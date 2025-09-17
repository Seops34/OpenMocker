package net.spooncast.openmocker.core.model

import org.junit.Test
import org.junit.Assert.*

class MockResponseTest {

    @Test
    fun `MockResponse creation with valid inputs should succeed`() {
        val response = MockResponse(200, "OK", 100L, mapOf("Content-Type" to "application/json"))

        assertEquals(200, response.code)
        assertEquals("OK", response.body)
        assertEquals(100L, response.delay)
        assertEquals(mapOf("Content-Type" to "application/json"), response.headers)
    }

    @Test
    fun `MockResponse with default values should work`() {
        val response = MockResponse(404, "Not Found")

        assertEquals(404, response.code)
        assertEquals("Not Found", response.body)
        assertEquals(0L, response.delay)
        assertTrue(response.headers.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `MockResponse with invalid low status code should throw IllegalArgumentException`() {
        MockResponse(99, "Invalid")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `MockResponse with invalid high status code should throw IllegalArgumentException`() {
        MockResponse(600, "Invalid")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `MockResponse with negative delay should throw IllegalArgumentException`() {
        MockResponse(200, "OK", -1L)
    }

    @Test
    fun `isSuccess should return correct values`() {
        assertTrue(MockResponse(200, "OK").isSuccess)
        assertTrue(MockResponse(201, "Created").isSuccess)
        assertTrue(MockResponse(299, "Custom Success").isSuccess)

        assertFalse(MockResponse(199, "Informational").isSuccess)
        assertFalse(MockResponse(300, "Redirection").isSuccess)
        assertFalse(MockResponse(400, "Bad Request").isSuccess)
        assertFalse(MockResponse(500, "Server Error").isSuccess)
    }

    @Test
    fun `isClientError should return correct values`() {
        assertTrue(MockResponse(400, "Bad Request").isClientError)
        assertTrue(MockResponse(404, "Not Found").isClientError)
        assertTrue(MockResponse(499, "Custom Client Error").isClientError)

        assertFalse(MockResponse(200, "OK").isClientError)
        assertFalse(MockResponse(300, "Redirection").isClientError)
        assertFalse(MockResponse(500, "Server Error").isClientError)
    }

    @Test
    fun `isServerError should return correct values`() {
        assertTrue(MockResponse(500, "Internal Server Error").isServerError)
        assertTrue(MockResponse(503, "Service Unavailable").isServerError)
        assertTrue(MockResponse(599, "Custom Server Error").isServerError)

        assertFalse(MockResponse(200, "OK").isServerError)
        assertFalse(MockResponse(400, "Bad Request").isServerError)
        assertFalse(MockResponse(499, "Custom Client Error").isServerError)
    }

    @Test
    fun `hasDelay should return correct values`() {
        assertFalse(MockResponse(200, "OK", 0L).hasDelay)
        assertTrue(MockResponse(200, "OK", 100L).hasDelay)
        assertTrue(MockResponse(200, "OK", 1L).hasDelay)
    }

    @Test
    fun `hasHeaders should return correct values`() {
        assertFalse(MockResponse(200, "OK").hasHeaders)
        assertTrue(MockResponse(200, "OK", headers = mapOf("Content-Type" to "application/json")).hasHeaders)
    }

    @Test
    fun `toString should return formatted string`() {
        val response1 = MockResponse(200, "Hello World")
        assertEquals("HTTP 200 (body: 11 chars)", response1.toString())

        val response2 = MockResponse(404, "Not Found", 1000L)
        assertEquals("HTTP 404 (body: 9 chars) delay: 1000ms", response2.toString())

        val response3 = MockResponse(200, "OK", headers = mapOf("Content-Type" to "application/json"))
        assertEquals("HTTP 200 (body: 2 chars) headers: 1", response3.toString())

        val response4 = MockResponse(500, "Error", 500L, mapOf("X-Error" to "true", "Retry-After" to "60"))
        assertEquals("HTTP 500 (body: 5 chars) delay: 500ms headers: 2", response4.toString())
    }

    @Test
    fun `MockResponse equality should work correctly`() {
        val response1 = MockResponse(200, "OK", 100L, mapOf("Content-Type" to "application/json"))
        val response2 = MockResponse(200, "OK", 100L, mapOf("Content-Type" to "application/json"))
        val response3 = MockResponse(201, "Created", 100L, mapOf("Content-Type" to "application/json"))

        assertEquals(response1, response2)
        assertNotEquals(response1, response3)
    }

    @Test
    fun `MockResponse hashCode should be consistent`() {
        val response1 = MockResponse(200, "OK", 100L, mapOf("Content-Type" to "application/json"))
        val response2 = MockResponse(200, "OK", 100L, mapOf("Content-Type" to "application/json"))

        assertEquals(response1.hashCode(), response2.hashCode())
    }
}