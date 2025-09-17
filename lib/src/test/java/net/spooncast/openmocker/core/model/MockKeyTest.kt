package net.spooncast.openmocker.core.model

import org.junit.Test
import org.junit.Assert.*

class MockKeyTest {

    @Test
    fun `MockKey creation with valid inputs should succeed`() {
        val mockKey = MockKey("GET", "/api/users")
        assertEquals("GET", mockKey.method)
        assertEquals("/api/users", mockKey.path)
    }

    @Test
    fun `MockKey toString should return formatted string`() {
        val mockKey = MockKey("POST", "/api/posts")
        assertEquals("POST /api/posts", mockKey.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `MockKey with blank method should throw IllegalArgumentException`() {
        MockKey("", "/api/users")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `MockKey with blank path should throw IllegalArgumentException`() {
        MockKey("GET", "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `MockKey with non-uppercase method should throw IllegalArgumentException`() {
        MockKey("get", "/api/users")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `MockKey with path not starting with slash should throw IllegalArgumentException`() {
        MockKey("GET", "api/users")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `MockKey with method containing non-letters should throw IllegalArgumentException`() {
        MockKey("GET123", "/api/users")
    }

    @Test
    fun `MockKey equality should work correctly`() {
        val key1 = MockKey("GET", "/api/users")
        val key2 = MockKey("GET", "/api/users")
        val key3 = MockKey("POST", "/api/users")

        assertEquals(key1, key2)
        assertNotEquals(key1, key3)
    }

    @Test
    fun `MockKey hashCode should be consistent`() {
        val key1 = MockKey("GET", "/api/users")
        val key2 = MockKey("GET", "/api/users")

        assertEquals(key1.hashCode(), key2.hashCode())
    }
}