package io.legado.app.help.ai

import io.legado.app.utils.GSON
import com.google.gson.JsonParser
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
    fun requestRequiresJsonModeAndDescribesEveryValidatorConstraint() = runBlocking {
        val transport = FakeTransport(validResponse("c1"))
        OpenAiCompatibleProvider(transport).processChunk(
            AiChunkRequest("c1", "正文", null), config, apiKey
        )

        val body = JsonParser.parseString(transport.requests.single().body).asJsonObject
        assertEquals(
            "json_object",
            body.getAsJsonObject("response_format").get("type").asString
        )
        val prompt = body.getAsJsonArray("messages")[0].asJsonObject.get("content").asString
        listOf(
            "Unicode code points",
            "sorted",
            "non-overlapping",
            "exact substring",
            "Unicode letters only",
            "equal code-point counts",
            "1..12",
            "at most 128",
            "json example",
            "no-op edit",
            "original equals replacement"
        ).forEach { assertTrue("missing prompt rule: $it", prompt.contains(it)) }
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
    fun blankContentMustNotBecomeAValidNoEditPayload() = runBlocking {
        // 空白 message.content（空串 / 纯空白）表示模型未产出合法 JSON 对象，
        // 必须失败关闭为 PROTOCOL，绝不能由客户端替服务端补造“无编辑”成功载荷。
        listOf("", "   ", "\t\n").forEach { blank ->
            val transport = FakeTransport(AiHttpResponse(200, emptyMap(), chatResponse(blank, "stop")))
            val error = runCatching {
                OpenAiCompatibleProvider(transport).processChunk(
                    AiChunkRequest("c1", "正文", null), config, apiKey
                )
            }.exceptionOrNull() as AiProviderException
            assertEquals(AiProviderError.protocol, error.error)
        }
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

    @Test
    fun protocolDiagnosticsAreFixedEnumsAndNeverContainPayload() = runBlocking {
        suspend fun detailFor(content: Any): AiProtocolFailureDetail {
            val details = mutableListOf<AiProtocolFailureDetail>()
            val transport = FakeTransport(AiHttpResponse(200, emptyMap(), chatResponse(content)))
            val error = runCatching {
                OpenAiCompatibleProvider(transport) { details += it }.processChunk(
                    AiChunkRequest("c1", "private chapter text", null),
                    config,
                    apiKey
                )
            }.exceptionOrNull() as AiProviderException
            assertEquals(AiProviderError.protocol, error.error)
            assertEquals(1, details.size)
            assertFalse(details.toString().contains("private chapter text"))
            assertFalse(details.toString().contains("stage-b-secret-47"))
            return details.single()
        }

        assertEquals(AiProtocolFailureDetail.payload_markdown_fence, detailFor("```json\n{}\n```"))
        assertEquals(
            AiProtocolFailureDetail.payload_json_syntax_unclosed_container,
            detailFor("{not-json")
        )
        assertEquals(AiProtocolFailureDetail.payload_not_object, detailFor("[]"))
        assertEquals(
            AiProtocolFailureDetail.start_not_int,
            detailFor("""{"chunk_id":"c1","edits":[{"start":"0","end":1,"original":"甲","replacement":"乙","kind":"typo"}]}""")
        )
        assertEquals(
            AiProtocolFailureDetail.original_not_string,
            detailFor("""{"chunk_id":"c1","edits":[{"start":0,"end":1,"replacement":"乙","kind":"typo"}]}""")
        )
    }

    @Test
    fun payloadJsonSyntaxDiagnosticsAreReproducibleAndContentFree() = runBlocking {
        val cases = listOf(
            "answer: {\"chunk_id\":\"c1\"}" to
                AiProtocolFailureDetail.payload_json_syntax_non_json_prefix,
            "{\"chunk_id\":\"c1}" to
                AiProtocolFailureDetail.payload_json_syntax_unterminated_string,
            """{"chunk_id":"\q"}""" to
                AiProtocolFailureDetail.payload_json_syntax_invalid_escape,
            "{\"chunk_id\":\"line\nbreak\"}" to
                AiProtocolFailureDetail.payload_json_syntax_raw_control,
            "{\"chunk_id\":\"c1\"]" to
                AiProtocolFailureDetail.payload_json_syntax_mismatched_closer,
            "{\"chunk_id\":\"c1\"" to
                AiProtocolFailureDetail.payload_json_syntax_unclosed_container,
            "{\"chunk_id\":\"c1\"} trailing-private-text" to
                AiProtocolFailureDetail.payload_json_syntax_trailing_data
        )

        cases.forEach { (content, expected) ->
            repeat(2) {
                val details = mutableListOf<AiProtocolFailureDetail>()
                val response = AiHttpResponse(200, emptyMap(), chatResponse(content))
                val error = runCatching {
                    OpenAiCompatibleProvider(FakeTransport(response)) { details += it }.processChunk(
                        AiChunkRequest("c1", "private chapter text", null),
                        config,
                        apiKey
                    )
                }.exceptionOrNull() as AiProviderException

                assertEquals(AiProviderError.protocol, error.error)
                assertEquals(listOf(expected), details)
                assertFalse(details.toString().contains(content))
                assertFalse(details.toString().contains("private chapter text"))
                assertFalse(details.toString().contains("stage-b-secret-47"))
                assertFalse(details.toString().contains("trailing-private-text"))
            }
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
