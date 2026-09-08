package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonNull
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException

data class AiConnectionTestResult(val reachable: Boolean, val model: String)

class OpenAiCompatibleProvider internal constructor(
    private val transport: AiHttpTransport = OkHttpAiTransport()
) : AiTextProvider {

    override suspend fun processChunk(
        request: AiChunkRequest,
        config: AiProviderConfig,
        apiKey: AiApiKey
    ): AiChunkOutput {
        validateConfig(config)
        if (request.text.codePointCount(0, request.text.length) > config.maxChunkChars) {
            throw AiProviderException(AiProviderError.protocol)
        }
        return execute(request, config, apiKey)
    }

    /** The payload is constant by construction and cannot accept chapter text. */
    suspend fun testConnection(config: AiProviderConfig, apiKey: AiApiKey): AiConnectionTestResult {
        validateConfig(config)
        val output = execute(CONNECTION_TEST_REQUEST, config, apiKey)
        if (output.chunkId != CONNECTION_TEST_REQUEST.chunkId || output.finishReason != AiFinishReason.stop) {
            throw AiProviderException(AiProviderError.protocol)
        }
        return AiConnectionTestResult(true, config.model)
    }

    private suspend fun execute(
        request: AiChunkRequest,
        config: AiProviderConfig,
        apiKey: AiApiKey
    ): AiChunkOutput {
        val initialUrl = AiEndpointPolicy.chatCompletionsUrl(config)
        val body = requestBody(request, config.model)
        var url = initialUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val response = try {
                transport.execute(
                    AiHttpRequest(
                        url = url,
                        headers = mapOf(
                            "Accept" to "application/json",
                            "Authorization" to apiKey.use { "Bearer $it" }
                        ),
                        body = body,
                        timeoutSeconds = config.timeoutSeconds,
                        responseByteLimit = config.responseByteLimit
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: AiProviderException) {
                throw error
            } catch (_: Exception) {
                throw AiProviderException(AiProviderError.network)
            }

            if (response.code in REDIRECT_CODES) {
                if (response.code !in BODY_PRESERVING_REDIRECT_CODES || redirectCount == MAX_REDIRECTS) {
                    throw AiProviderException(AiProviderError.unsafe_redirect)
                }
                val location = response.header("Location")
                    ?: throw AiProviderException(AiProviderError.unsafe_redirect)
                val next = AiEndpointPolicy.resolveRedirect(
                    url,
                    location,
                    config.allowInsecureLocalHttp
                )
                // A cross-origin redirect would disclose both the chapter body and credentials.
                if (!AiEndpointPolicy.sameOrigin(initialUrl, next)) {
                    throw AiProviderException(AiProviderError.unsafe_redirect)
                }
                url = next
            } else {
                ensureSuccessful(response.code)
                return parseResponse(response.body)
            }
        }
        throw AiProviderException(AiProviderError.unsafe_redirect)
    }

    private fun requestBody(request: AiChunkRequest, model: String): String {
        val userContent = JsonObject().apply {
            addProperty("chunk_id", request.chunkId)
            addProperty("text", request.text)
            if (request.contextOnly == null) {
                add("context_only", JsonNull.INSTANCE)
            } else {
                addProperty("context_only", request.contextOnly)
            }
        }
        val root = JsonObject().apply {
            addProperty("model", model)
            addProperty("temperature", 0)
            addProperty("max_tokens", MAX_OUTPUT_TOKENS)
            add("messages", GSON.toJsonTree(listOf(
                mapOf("role" to "system", "content" to SYSTEM_PROMPT),
                mapOf("role" to "user", "content" to GSON.toJson(userContent))
            )))
        }
        return GSON.toJson(root)
    }

    private fun parseResponse(body: String): AiChunkOutput {
        try {
            val root = JsonParser.parseString(body).asJsonObject
            val choices = root.getAsJsonArray("choices")
            if (choices == null || choices.size() != 1) throw IllegalArgumentException()
            val choice = choices[0].asJsonObject
            val finishReason = normalizeFinishReason(choice.get("finish_reason")?.asString)
            val contentElement = choice.getAsJsonObject("message")?.get("content")
                ?: throw IllegalArgumentException()
            if (!contentElement.isJsonPrimitive || !contentElement.asJsonPrimitive.isString) {
                throw IllegalArgumentException()
            }
            val content = contentElement.asString
            val payload = JsonParser.parseString(content).asJsonObject
            return parseOutputPayload(payload, finishReason)
        } catch (_: Exception) {
            throw AiProviderException(AiProviderError.protocol)
        }
    }

    private fun parseOutputPayload(payload: JsonObject, finishReason: AiFinishReason): AiChunkOutput {
        val chunkId = requiredString(payload, "chunk_id")
        val editsElement = payload.get("edits")
        if (editsElement == null || !editsElement.isJsonArray) throw IllegalArgumentException()
        val edits = editsElement.asJsonArray.map { element ->
            if (!element.isJsonObject) throw IllegalArgumentException()
            val edit = element.asJsonObject
            val kindName = requiredString(edit, "kind")
            AiEdit(
                requiredInt(edit, "start"),
                requiredInt(edit, "end"),
                requiredString(edit, "original"),
                requiredString(edit, "replacement"),
                AiEditKind.entries.firstOrNull { it.name == kindName } ?: throw IllegalArgumentException()
            )
        }
        val warnings = payload.get("warnings")?.let { element ->
            if (!element.isJsonArray) throw IllegalArgumentException()
            element.asJsonArray.map {
                if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString) throw IllegalArgumentException()
                it.asString
            }
        } ?: emptyList()
        return AiChunkOutput(chunkId, edits, warnings, finishReason)
    }

    private fun requiredString(value: JsonObject, name: String): String {
        val element = value.get(name)
        if (element == null || !element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            throw IllegalArgumentException()
        }
        return element.asString
    }

    private fun requiredInt(value: JsonObject, name: String): Int {
        val element = value.get(name)
        if (element == null || !element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
            throw IllegalArgumentException()
        }
        val raw = element.asString
        if (!raw.matches(Regex("-?(?:0|[1-9]\\d*)"))) throw IllegalArgumentException()
        return raw.toIntOrNull() ?: throw IllegalArgumentException()
    }

    private fun ensureSuccessful(code: Int) {
        when (code) {
            in 200..299 -> Unit
            401, 403 -> throw AiProviderException(AiProviderError.authentication, code)
            429 -> throw AiProviderException(AiProviderError.rate_limited, code)
            in 500..599 -> throw AiProviderException(AiProviderError.server, code)
            else -> throw AiProviderException(AiProviderError.http, code)
        }
    }

    private fun validateConfig(config: AiProviderConfig) {
        if (config.type != TYPE || config.model.isBlank() ||
            config.maxChunkChars !in 256..20_000 ||
            config.timeoutSeconds !in 5..120 ||
            config.responseByteLimit !in 4_096..1_048_576
        ) {
            throw AiProviderException(AiProviderError.protocol)
        }
    }

    private fun normalizeFinishReason(reason: String?): AiFinishReason = when (reason) {
        "stop" -> AiFinishReason.stop
        "length" -> AiFinishReason.length
        "content_filter" -> AiFinishReason.content_filter
        "tool_calls", "function_call" -> AiFinishReason.tool_calls
        else -> AiFinishReason.other
    }

    companion object {
        const val TYPE = "chat-completions"
        const val CONNECTION_TEST_CHUNK_ID = "connection-test"
        const val CONNECTION_TEST_TEXT = "这是一段固定连接测试文本。"
        private const val MAX_REDIRECTS = 3
        private const val MAX_OUTPUT_TOKENS = 8192
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val BODY_PRESERVING_REDIRECT_CODES = setOf(307, 308)
        private val CONNECTION_TEST_REQUEST = AiChunkRequest(
            CONNECTION_TEST_CHUNK_ID,
            CONNECTION_TEST_TEXT,
            null
        )
        private const val SYSTEM_PROMPT = """
You repair only local typos, anti-theft substitutions, punctuation, and whitespace in the supplied text.
Return one JSON object only: {"chunk_id":"...","edits":[{"start":0,"end":1,"original":"...","replacement":"...","kind":"typo|anti_theft|punctuation|whitespace"}],"warnings":[]}.
Offsets are Unicode code points in text. context_only is read-only context and must never be edited or repeated. Do not rewrite, summarize, continue, translate, or alter sentinels, URLs, numbers, names, facts, plot, or style.
"""
    }
}
