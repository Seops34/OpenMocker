package net.spooncast.openmocker.core

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MemoryMockRepositoryTest {

    private lateinit var repository: MemoryMockRepository

    @Before
    fun setUp() {
        repository = MemoryMockRepository()
    }

    @Test
    fun `saveMock and getMock should work correctly`() = runTest {
        val key = MockKey("GET", "/test")
        val response = MockResponse(200, "test response", 100L)

        repository.saveMock(key, response)
        val retrieved = repository.getMock(key)

        assertEquals(response, retrieved)
    }

    @Test
    fun `removeMock should return true when mock exists`() = runTest {
        val key = MockKey("POST", "/api/test")
        val response = MockResponse(201, "created", 0L)

        repository.saveMock(key, response)
        assertTrue(repository.removeMock(key))
        assertNull(repository.getMock(key))
    }

    @Test
    fun `removeMock should return false when mock does not exist`() = runTest {
        val key = MockKey("DELETE", "/nonexistent")
        assertFalse(repository.removeMock(key))
    }

    @Test
    fun `getAllMocks should return all saved mocks`() = runTest {
        val key1 = MockKey("GET", "/test1")
        val response1 = MockResponse(200, "response1")
        val key2 = MockKey("POST", "/test2")
        val response2 = MockResponse(201, "response2")

        repository.saveMock(key1, response1)
        repository.saveMock(key2, response2)

        val allMocks = repository.getAllMocks()
        assertEquals(2, allMocks.size)
        assertEquals(response1, allMocks[key1])
        assertEquals(response2, allMocks[key2])
    }

    @Test
    fun `cacheRealResponse and getCachedResponse should work correctly`() = runTest {
        val key = MockKey("GET", "/cache-test")
        val response = MockResponse(200, "cached response")

        repository.cacheRealResponse(key, response)
        val cached = repository.getCachedResponse(key)

        assertEquals(response, cached)
    }

    @Test
    fun `clearAll should remove all mocks and cached responses`() = runTest {
        val key = MockKey("GET", "/test")
        val response = MockResponse(200, "test")

        repository.saveMock(key, response)
        repository.cacheRealResponse(key, response)

        repository.clearAll()

        assertNull(repository.getMock(key))
        assertNull(repository.getCachedResponse(key))
        assertTrue(repository.getAllMocks().isEmpty())
        assertTrue(repository.getAllCachedResponses().isEmpty())
    }

    @Test
    fun `thread safety test for concurrent operations`() = runTest {
        val totalOperations = 1000
        val successCount = AtomicInteger(0)
        val key = MockKey("GET", "/concurrent-test")

        // Launch multiple coroutines to perform concurrent operations
        val jobs = List(totalOperations) { index ->
            launch {
                try {
                    val response = MockResponse(200, "response-$index", index.toLong())
                    repository.saveMock(key, response)

                    // Verify the save operation
                    val retrieved = repository.getMock(key)
                    if (retrieved != null) {
                        successCount.incrementAndGet()
                    }
                } catch (e: Exception) {
                    // Should not happen in a thread-safe implementation
                    fail("Thread safety violation: ${e.message}")
                }
            }
        }

        // Wait for all jobs to complete
        jobs.joinAll()

        // At least one operation should succeed (the last one)
        assertTrue("No successful operations detected", successCount.get() > 0)

        // The repository should still be in a consistent state
        assertNotNull(repository.getMock(key))
    }

    @Test
    fun `concurrent cache operations should be thread safe`() = runTest {
        val totalOperations = 500
        val keys = (1..totalOperations).map { MockKey("GET", "/cache-$it") }
        val responses = (1..totalOperations).map { MockResponse(200, "cached-$it") }

        // Launch concurrent cache operations
        val cacheJobs = keys.zip(responses).map { (key, response) ->
            launch {
                repository.cacheRealResponse(key, response)
            }
        }

        // Launch concurrent read operations
        val readJobs = keys.map { key ->
            launch {
                repository.getCachedResponse(key)
            }
        }

        // Wait for all operations to complete
        (cacheJobs + readJobs).joinAll()

        // Verify all responses were cached
        val allCached = repository.getAllCachedResponses()
        assertEquals(totalOperations, allCached.size)
    }

    @Test
    fun `memory management utility methods should work correctly`() = runTest {
        val key1 = MockKey("GET", "/test1")
        val key2 = MockKey("POST", "/test2")
        val response = MockResponse(200, "test")

        repository.saveMock(key1, response)
        repository.cacheRealResponse(key2, response)

        assertEquals(1, repository.getMocksCount())
        assertEquals(1, repository.getCachedResponsesCount())

        repository.clearMocks()
        assertEquals(0, repository.getMocksCount())
        assertEquals(1, repository.getCachedResponsesCount())

        repository.clearCache()
        assertEquals(0, repository.getCachedResponsesCount())
    }
}