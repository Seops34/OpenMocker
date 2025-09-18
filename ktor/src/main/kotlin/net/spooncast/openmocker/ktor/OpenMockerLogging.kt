package net.spooncast.openmocker.ktor

import net.spooncast.openmocker.core.MockKey
import net.spooncast.openmocker.core.MockResponse

/**
 * Logging utilities for OpenMocker Ktor plugin.
 *
 * This object provides structured logging capabilities for the OpenMocker plugin,
 * including different log levels, structured data logging, and performance monitoring.
 *
 * The logging system is designed to be lightweight and production-safe, with
 * configurable verbosity levels and safe formatting of sensitive data.
 */
object OpenMockerLogger {

    /**
     * Log levels for controlling verbosity of OpenMocker operations.
     */
    enum class LogLevel(val priority: Int) {
        TRACE(0),
        DEBUG(1),
        INFO(2),
        WARN(3),
        ERROR(4),
        NONE(5)
    }

    /**
     * Current log level threshold.
     * Only messages at or above this level will be printed.
     */
    @Volatile
    private var currentLevel: LogLevel = LogLevel.INFO

    /**
     * Set the logging level for OpenMocker operations.
     *
     * @param level The minimum log level to output
     */
    fun setLogLevel(level: LogLevel) {
        currentLevel = level
    }

    /**
     * Log a trace message (most verbose).
     *
     * @param message The log message
     * @param context Optional context information
     */
    fun trace(message: String, context: Map<String, Any> = emptyMap()) {
        log(LogLevel.TRACE, message, context)
    }

    /**
     * Log a debug message.
     *
     * @param message The log message
     * @param context Optional context information
     */
    fun debug(message: String, context: Map<String, Any> = emptyMap()) {
        log(LogLevel.DEBUG, message, context)
    }

    /**
     * Log an info message.
     *
     * @param message The log message
     * @param context Optional context information
     */
    fun info(message: String, context: Map<String, Any> = emptyMap()) {
        log(LogLevel.INFO, message, context)
    }

    /**
     * Log a warning message.
     *
     * @param message The log message
     * @param context Optional context information
     */
    fun warn(message: String, context: Map<String, Any> = emptyMap()) {
        log(LogLevel.WARN, message, context)
    }

    /**
     * Log an error message.
     *
     * @param message The log message
     * @param throwable Optional exception
     * @param context Optional context information
     */
    fun error(message: String, throwable: Throwable? = null, context: Map<String, Any> = emptyMap()) {
        if (shouldLog(LogLevel.ERROR)) {
            val logMessage = formatMessage(LogLevel.ERROR, message, context)
            println(logMessage)
            throwable?.let {
                println("Exception: ${it.message}")
                if (currentLevel == LogLevel.TRACE) {
                    it.printStackTrace()
                }
            }
        }
    }

    /**
     * Log a mocking operation with structured data.
     *
     * @param mockKey The mock key being processed
     * @param mockResponse The mock response (if any)
     * @param processingTime Time taken to process the request
     * @param wasMocked Whether the request was ultimately mocked
     */
    fun logMockingOperation(
        mockKey: MockKey,
        mockResponse: MockResponse?,
        processingTime: Long,
        wasMocked: Boolean
    ) {
        if (shouldLog(LogLevel.DEBUG)) {
            val context = mapOf(
                "method" to mockKey.method,
                "path" to mockKey.path,
                "wasMocked" to wasMocked,
                "processingTime" to "${processingTime}ms",
                "statusCode" to (mockResponse?.code ?: "N/A"),
                "delay" to (mockResponse?.delay?.let { "${it}ms" } ?: "0ms")
            )

            val message = if (wasMocked) {
                "Mock response served for ${mockKey.method} ${mockKey.path}"
            } else {
                "Real request processed for ${mockKey.method} ${mockKey.path}"
            }

            debug(message, context)
        }
    }

    /**
     * Log caching operation with structured data.
     *
     * @param mockKey The mock key for the cached response
     * @param responseCode The HTTP response code that was cached
     * @param bodyLength Length of the response body
     * @param success Whether caching was successful
     */
    fun logCachingOperation(
        mockKey: MockKey,
        responseCode: Int,
        bodyLength: Int,
        success: Boolean
    ) {
        if (shouldLog(LogLevel.DEBUG)) {
            val context = mapOf(
                "method" to mockKey.method,
                "path" to mockKey.path,
                "responseCode" to responseCode,
                "bodyLength" to bodyLength,
                "success" to success
            )

            val message = if (success) {
                "Response cached for ${mockKey.method} ${mockKey.path}"
            } else {
                "Failed to cache response for ${mockKey.method} ${mockKey.path}"
            }

            debug(message, context)
        }
    }

    /**
     * Log metrics snapshot with formatted output.
     *
     * @param metrics The metrics snapshot to log
     */
    fun logMetrics(metrics: MetricsSnapshot) {
        if (shouldLog(LogLevel.INFO)) {
            info(
                "OpenMocker Metrics Report",
                mapOf<String, Any>(
                    "totalRequests" to metrics.totalRequests,
                    "mockedRequests" to metrics.mockedRequests,
                    "realRequests" to metrics.realRequests,
                    "mockingRatio" to String.format("%.1f%%", metrics.mockingRatio * 100),
                    "cacheHitRatio" to String.format("%.1f%%", metrics.cacheHitRatio * 100),
                    "averageProcessingTime" to String.format("%.2fms", metrics.averageProcessingTime),
                    "errors" to metrics.processingErrors
                )
            )
        }
    }

    /**
     * Log plugin configuration at startup.
     *
     * @param config The OpenMockerConfig being used
     */
    fun logConfiguration(config: OpenMockerConfig) {
        if (shouldLog(LogLevel.INFO)) {
            info(
                "OpenMocker plugin initialized",
                mapOf<String, Any>(
                    "isEnabled" to config.isEnabled,
                    "interceptAll" to config.interceptAll,
                    "maxCacheSize" to config.maxCacheSize,
                    "enableLogging" to config.enableLogging,
                    "repository" to (config.repository::class.simpleName ?: "Unknown")
                )
            )
        }
    }

    /**
     * Internal logging method that handles message formatting and output.
     */
    private fun log(level: LogLevel, message: String, context: Map<String, Any>) {
        if (shouldLog(level)) {
            val logMessage = formatMessage(level, message, context)
            println(logMessage)
        }
    }

    /**
     * Check if a message at the given level should be logged.
     */
    private fun shouldLog(level: LogLevel): Boolean {
        return level.priority >= currentLevel.priority
    }

    /**
     * Format a log message with level, timestamp, and context.
     */
    private fun formatMessage(level: LogLevel, message: String, context: Map<String, Any>): String {
        val timestamp = System.currentTimeMillis()
        val contextString = if (context.isNotEmpty()) {
            " | " + context.entries.joinToString(", ") { "${it.key}=${it.value}" }
        } else {
            ""
        }

        return "[OpenMocker] ${level.name} $timestamp: $message$contextString"
    }
}

/**
 * Performance monitoring utilities for measuring operation times.
 */
object OpenMockerProfiler {

    /**
     * Measure the execution time of a suspend operation.
     *
     * @param operation The suspend operation to measure
     * @return Pair of (result, execution time in milliseconds)
     */
    suspend inline fun <T> measureTime(crossinline operation: suspend () -> T): Pair<T, Long> {
        val startTime = System.currentTimeMillis()
        val result = operation()
        val endTime = System.currentTimeMillis()
        return result to (endTime - startTime)
    }

    /**
     * Measure the execution time of a regular operation.
     *
     * @param operation The operation to measure
     * @return Pair of (result, execution time in milliseconds)
     */
    inline fun <T> measureTimeSync(crossinline operation: () -> T): Pair<T, Long> {
        val startTime = System.currentTimeMillis()
        val result = operation()
        val endTime = System.currentTimeMillis()
        return result to (endTime - startTime)
    }

    /**
     * Log a performance measurement.
     *
     * @param operationName Name of the operation being measured
     * @param executionTime Execution time in milliseconds
     * @param threshold Log level threshold - operations above this time log as warnings
     */
    fun logPerformance(operationName: String, executionTime: Long, threshold: Long = 100) {
        val context = mapOf(
            "operation" to operationName,
            "executionTime" to "${executionTime}ms",
            "threshold" to "${threshold}ms"
        )

        if (executionTime > threshold) {
            OpenMockerLogger.warn("Slow operation detected: $operationName", context)
        } else {
            OpenMockerLogger.trace("Operation completed: $operationName", context)
        }
    }
}