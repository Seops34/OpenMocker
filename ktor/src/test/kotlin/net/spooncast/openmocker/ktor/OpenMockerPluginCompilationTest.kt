package net.spooncast.openmocker.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import net.spooncast.openmocker.core.MemoryMockRepository
import org.junit.Assert.*
import org.junit.Test

/**
 * Basic compilation and instantiation tests for OpenMocker Ktor plugin.
 *
 * These tests verify that Phase 2.1 requirements are met:
 * - Plugin can be compiled without errors
 * - Basic classes can be instantiated
 * - Configuration validation works
 * - Utility functions work correctly
 *
 * Note: Full plugin integration tests will be added in Phase 2.2 when
 * the actual mock response creation is implemented.
 */
class OpenMockerPluginCompilationTest {

    @Test
    fun `plugin configuration can be instantiated with default values`() {
        // Verify that configuration class can be created with defaults
        val config = OpenMockerConfig()

        // Check default values
        assertNotNull(config.repository)
        assertTrue("Plugin should be enabled by default", config.isEnabled)
        assertTrue("Intercept all should be true by default", config.interceptAll)
        assertEquals("Max cache size should default to -1", -1, config.maxCacheSize)
        assertFalse("Auto enable in debug should default to false", config.autoEnableInDebug)
    }

    @Test
    fun `plugin configuration can be customized`() {
        // Verify that configuration can be customized
        val customRepo = MemoryMockRepository()
        val config = OpenMockerConfig()

        // Customize configuration
        config.repository = customRepo
        config.isEnabled = false
        config.interceptAll = false
        config.maxCacheSize = 100
        config.autoEnableInDebug = true

        // Verify customization worked
        assertSame("Custom repository should be set", customRepo, config.repository)
        assertFalse("Plugin should be disabled", config.isEnabled)
        assertFalse("Intercept all should be false", config.interceptAll)
        assertEquals("Max cache size should be 100", 100, config.maxCacheSize)
        assertTrue("Auto enable in debug should be true", config.autoEnableInDebug)
    }

    @Test
    fun `mocker engine can be created with default repository`() {
        // Verify that KtorMockerEngine can be instantiated
        val engine = createKtorMockerEngine()
        assertNotNull(engine)
    }

    @Test
    fun `mocker engine can be created with custom repository`() {
        // Verify that KtorMockerEngine can be created with custom repository
        val customRepo = MemoryMockRepository()
        val engine = KtorMockerEngine(customRepo)
        assertNotNull(engine)
    }

    @Test
    fun `configuration validation works correctly`() {
        // Verify that configuration validation catches invalid settings
        val config = OpenMockerConfig()

        // Valid configurations should pass
        config.maxCacheSize = 100
        config.validate() // Should not throw

        config.maxCacheSize = -1
        config.validate() // Should not throw (unlimited)

        // Invalid configuration should fail
        var exceptionThrown = false
        try {
            config.maxCacheSize = 0
            config.validate()
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true
        }

        assertTrue("Expected validation to fail for maxCacheSize = 0", exceptionThrown)
    }

    @Test
    fun `adapter functions compile correctly`() {
        // Verify that adapter functions can be called without compilation errors
        val url = "https://api.example.com/test/path?param=value#fragment"
        val extractedPath = extractPathFromUrl(url)

        // Should extract path correctly
        assertEquals("Expected correct path extraction", "/test/path", extractedPath)
    }

    @Test
    fun `url path extraction handles edge cases`() {
        // Test various URL formats to ensure robust path extraction
        val testCases = mapOf(
            "https://api.example.com/" to "/",
            "https://api.example.com" to "/",
            "https://api.example.com/path" to "/path",
            "https://api.example.com/path/" to "/path/",
            "https://api.example.com/path?query=value" to "/path",
            "https://api.example.com/path#fragment" to "/path",
            "https://api.example.com/path?query=value#fragment" to "/path",
            "https://api.example.com/complex/nested/path" to "/complex/nested/path"
        )

        testCases.forEach { (url, expectedPath) ->
            val actualPath = extractPathFromUrl(url)
            assertEquals("For URL '$url'", expectedPath, actualPath)
        }
    }
}