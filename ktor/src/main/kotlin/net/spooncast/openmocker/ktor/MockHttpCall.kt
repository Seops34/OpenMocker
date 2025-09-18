package net.spooncast.openmocker.ktor

import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import net.spooncast.openmocker.core.MockResponse

/**
 * Mock HTTP call utilities for Phase 2.4 implementation.
 *
 * This implementation provides mock response creation capabilities for the OpenMocker
 * Ktor plugin. Due to Ktor's internal API constraints, we use the plugin's built-in
 * mechanisms for response transformation rather than creating custom HttpClientCall objects.
 *
 * The approach focuses on:
 * - Mock response information preparation
 * - Content transformation through plugin pipeline
 * - Memory-efficient streaming support
 * - Integration with existing AttributeKey system
 *
 * This design is more compatible with Ktor's plugin architecture and avoids
 * issues with final classes and internal APIs.
 */
internal object MockHttpCall {

    /**
     * Creates mock response content for the plugin pipeline.
     *
     * This method prepares the mock response for integration with Ktor's
     * transformRequestBody hook. Instead of creating a complete HttpClientCall,
     * we prepare the response content that can be handled by the plugin pipeline.
     *
     * @param client The HttpClient instance
     * @param requestBuilder The original request builder
     * @param mockResponse The mock response configuration
     * @return OutgoingContent that represents the mock response
     */
    suspend fun create(
        client: HttpClient,
        requestBuilder: HttpRequestBuilder,
        mockResponse: MockResponse
    ): HttpClientCall {
        // Since we cannot directly create HttpClientCall due to final members,
        // we'll throw an exception here to indicate this approach needs revision.
        // The actual mock response will be handled through the response pipeline
        // using the MockResponseInfo approach from KtorAdapters.
        throw UnsupportedOperationException(
            "Direct HttpClientCall creation is not supported due to Ktor's internal API constraints. " +
            "Mock responses are handled through the plugin pipeline using MockResponseInfo."
        )
    }

    /**
     * Prepares mock response content for the transformation pipeline.
     *
     * This method creates the appropriate OutgoingContent for mock responses
     * that will be processed by Ktor's plugin pipeline.
     *
     * @param mockResponse The mock response configuration
     * @return OutgoingContent containing the mock response body
     */
    suspend fun createMockContent(mockResponse: MockResponse): OutgoingContent {
        return TextContent(
            text = mockResponse.body,
            contentType = detectContentType(mockResponse.body)
        )
    }

    /**
     * Detects the appropriate content type based on response body content.
     */
    fun detectContentType(body: String): io.ktor.http.ContentType {
        val trimmed = body.trim()
        return when {
            trimmed.startsWith("{") || trimmed.startsWith("[") -> io.ktor.http.ContentType.Application.Json
            body.contains("html", ignoreCase = true) -> io.ktor.http.ContentType.Text.Html
            trimmed.startsWith("<?xml") || trimmed.startsWith("<") -> io.ktor.http.ContentType.Application.Xml
            else -> io.ktor.http.ContentType.Text.Plain
        }
    }
}