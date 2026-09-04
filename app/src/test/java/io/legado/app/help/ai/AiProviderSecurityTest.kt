package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderSecurityTest {
    @Test
    fun httpsIsDefaultAndHttpRequiresExplicitPrivateLiteral() {
        val https = config("https://example.com/api/v1")
        assertEquals(
            "https://example.com/api/v1/chat/completions",
            AiEndpointPolicy.chatCompletionsUrl(https)
        )

        assertError(AiProviderError.unsafe_endpoint) {
            AiEndpointPolicy.chatCompletionsUrl(config("http://example.com"))
        }
        assertError(AiProviderError.unsafe_endpoint) {
            AiEndpointPolicy.chatCompletionsUrl(
                config("http://internal.example", allowHttp = true)
            )
        }
        assertEquals(
            "http://127.0.0.1:8080/v1/chat/completions",
            AiEndpointPolicy.chatCompletionsUrl(
                config("http://127.0.0.1:8080", allowHttp = true)
            )
        )
    }

    @Test
    fun credentialsQueryAndCrossOriginAreRejected() {
        assertError(AiProviderError.unsafe_endpoint) {
            AiEndpointPolicy.chatCompletionsUrl(config("https://user:pass@example.com/v1"))
        }
        assertError(AiProviderError.unsafe_endpoint) {
            AiEndpointPolicy.chatCompletionsUrl(config("https://example.com/v1?token=x"))
        }
        assertTrue(AiEndpointPolicy.sameOrigin("https://EXAMPLE.com/a", "https://example.com:443/b"))
        assertFalse(AiEndpointPolicy.sameOrigin("https://example.com/a", "https://other.example/b"))
        assertFalse(AiEndpointPolicy.sameOrigin("https://example.com/a", "http://example.com/a"))
    }

    private fun config(url: String, allowHttp: Boolean = false) = AiProviderConfig(
        OpenAiCompatibleProvider.TYPE,
        url,
        "model",
        allowInsecureLocalHttp = allowHttp
    )

    private fun assertError(expected: AiProviderError, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull() as AiProviderException
        assertEquals(expected, error.error)
    }
}
