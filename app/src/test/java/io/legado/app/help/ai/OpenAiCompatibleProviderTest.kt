package io.legado.app.help.ai

import io.legado.app.utils.GSON
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.ArrayDeque

class OpenAiCompatibleProviderTest {
    private val config = AiProviderConfig(
        OpenAiCompatibleProvider.TYPE,
        "https://api.example/v1",
        "model-a"
    )
    private val apiKey = AiApiKey.from("stage-b-secret-47")

    @Test
    fun testConnectionUsesOnlyTheFixedPayload() = runBlocking {
        val transport = FakeTransport(validResponse(OpenAiCompatibleProvider.CONNECTION_TEST_CHUNK_ID))
        val provider = OpenAiCompatibleProvider(transport)

        assertTrue(provider.testConnection(config, apiKey).reachable)
        val sent = transport.requests.single()
        assertTrue(sent.body.contains(OpenAiCompatibleProvider.CONNECTION_TEST_TEXT))
        assertFalse(sent.body.contains("private chapter text"))
        assertEquals("Bearer stage-b-secret-47", sent.header("Authorization"))
        assertFalse(sent.toString().contains("stage-b-secret-47"))
        assertFalse(AiChunkRequest("c", "private chapter text", null).toString().contains("private chapter text"))
    }

    @Test
    fun crossOriginRedirectStopsBeforeSecretOrBodyCanBeForwarded() = runBlocking {
        val transport = FakeTransport(
            AiHttpResponse(307, mapOf("Location" to "https://other.example/v1/chat/completions"), "")
        )
        val provider = OpenAiCompatibleProvider(transport)

        val error = runCatching {
            provider.processChunk(AiChunkRequest("c1", "private chapter text", null), config, apiKey)
        }.exceptionOrNull() as AiProviderException

        assertEquals(AiProviderError.unsafe_redirect, error.error)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun sameOriginRedirectAndFinishReasonAreNormalized() = runBlocking {
        val transport = FakeTransport(
            AiHttpResponse(307, mapOf("Location" to "/moved"), ""),
            validResponse("c1", "length")
        )
        val output = OpenAiCompatibleProvider(transport).processChunk(
            AiChunkRequest("c1", "正文", null),
            config,
            apiKey
        )

        assertEquals(AiFinishReason.length, output.finishReason)
        assertEquals(2, transport.requests.size)
        assertTrue(transport.requests[1].url.endsWith("/moved"))
    }

    @Test
    fun redirectThatWouldChangePostSemanticsIsRejected() = runBlocking {
        val transport = FakeTransport(
            AiHttpResponse(302, mapOf("Location" to "/moved"), "")
        )
        val error = runCatching {
            OpenAiCompatibleProvider(transport).processChunk(
                AiChunkRequest("c1", "正文", null), config, apiKey
            )
        }.exceptionOrNull() as AiProviderException

        assertEquals(AiProviderError.unsafe_redirect, error.error)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun errorsNeverExposeResponseBodyOrKey() = runBlocking {
        val transport = FakeTransport(
            AiHttpResponse(401, emptyMap(), "server echoed stage-b-secret-47")
        )
        val error = runCatching {
            OpenAiCompatibleProvider(transport).processChunk(
                AiChunkRequest("c1", "正文", null), config, apiKey
            )
        }.exceptionOrNull() as AiProviderException

        assertEquals(AiProviderError.authentication, error.error)
        assertFalse(error.toString().contains("stage-b-secret-47"))
        assertFalse(error.stackTraceToString().contains("server echoed"))
    }

    @Test
    fun responseReaderStopsAtHardByteLimit() {
        assertEquals(
            "1234",
            AiResponseReader.readLimited(ByteArrayInputStream("1234".toByteArray()), 4).toString(Charsets.UTF_8)
        )
        val error = runCatching {
            AiResponseReader.readLimited(ByteArrayInputStream("12345".toByteArray()), 4)
        }.exceptionOrNull() as AiProviderException
        assertEquals(AiProviderError.response_too_large, error.error)
    }

    @Test
    fun missingFieldsUnknownKindsAndNonStringContentFailClosed() = runBlocking {
        val invalidBodies = listOf(
            chatResponse("{\"chunk_id\":\"c1\"}"),
            chatResponse("{\"chunk_id\":\"c1\",\"edits\":[{\"start\":0,\"end\":1,\"original\":\"甲\",\"replacement\":\"乙\",\"kind\":\"rewrite\"}]}"),
            GSON.toJson(mapOf("choices" to listOf(mapOf(
                "finish_reason" to "stop",
                "message" to mapOf("content" to listOf("not", "text"))
            ))))
        )
        invalidBodies.forEach { body ->
            val error = runCatching {
                OpenAiCompatibleProvider(FakeTransport(AiHttpResponse(200, emptyMap(), body)))
                    .processChunk(AiChunkRequest("c1", "正文", null), config, apiKey)
            }.exceptionOrNull() as AiProviderException
            assertEquals(AiProviderError.protocol, error.error)
        }
    }

    private fun validResponse(chunkId: String, finishReason: String = "stop"): AiHttpResponse {
        val content = GSON.toJson(mapOf("chunk_id" to chunkId, "edits" to emptyList<Any>(), "warnings" to emptyList<String>()))
        return AiHttpResponse(
            200,
            emptyMap(),
            chatResponse(content, finishReason)
        )
    }

    private fun chatResponse(content: Any, finishReason: String = "stop"): String = GSON.toJson(mapOf(
        "choices" to listOf(mapOf(
            "finish_reason" to finishReason,
            "message" to mapOf("content" to content)
        ))
    ))

    private class FakeTransport(vararg responses: AiHttpResponse) : AiHttpTransport {
        val requests = mutableListOf<AiHttpRequest>()
        private val responses = ArrayDeque(responses.toList())

        override suspend fun execute(request: AiHttpRequest): AiHttpResponse {
            requests += request
            return responses.removeFirst()
        }
    }
}
