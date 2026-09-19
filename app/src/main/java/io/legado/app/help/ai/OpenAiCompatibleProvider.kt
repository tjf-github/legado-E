package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonNull
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import kotlinx.coroutines.CancellationException
import java.io.StringReader

data class AiConnectionTestResult(val reachable: Boolean, val model: String)

/** Provider 响应解析的固定脱敏子类；不得包含响应文本、字段值或异常 message。 */
internal enum class AiProtocolFailureDetail {
    blank_content,
    outer_json_syntax,
    outer_not_object,
    choices_not_array,
    choices_count,
    choice_not_object,
    finish_reason_not_string,
    message_not_object,
    no_content,
    content_not_string,
    payload_markdown_fence,
    payload_json_syntax,
    payload_json_syntax_non_json_prefix,
    payload_json_syntax_unterminated_string,
    payload_json_syntax_invalid_escape,
    payload_json_syntax_raw_control,
    payload_json_syntax_mismatched_closer,
    payload_json_syntax_unclosed_container,
    payload_json_syntax_trailing_data,
    payload_not_object,
    chunk_id_not_string,
    no_edits_array,
    edit_not_object,
    kind_not_string,
    start_not_int,
    end_not_int,
    original_not_string,
    replacement_not_string,
    bad_kind,
    warnings_not_array,
    warnings_not_string,
    unknown
}

class OpenAiCompatibleProvider internal constructor(
    private val transport: AiHttpTransport = OkHttpAiTransport(),
    private val protocolReporter: (AiProtocolFailureDetail) -> Unit = { detail ->
        LogUtils.d("AiFailure", "ai-protocol detail=${detail.name}")
    }
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
            add("response_format", JsonObject().apply { addProperty("type", "json_object") })
            add("messages", GSON.toJsonTree(listOf(
                mapOf("role" to "system", "content" to SYSTEM_PROMPT),
                mapOf("role" to "user", "content" to GSON.toJson(userContent))
            )))
        }
        return GSON.toJson(root)
    }

    private fun parseResponse(body: String): AiChunkOutput {
        try {
            val outer = try {
                JsonParser.parseString(body)
            } catch (_: Exception) {
                throw ProtocolDetail(AiProtocolFailureDetail.outer_json_syntax)
            }
            if (!outer.isJsonObject) throw ProtocolDetail(AiProtocolFailureDetail.outer_not_object)
            val root = outer.asJsonObject
            val choicesElement = root.get("choices")
            if (choicesElement == null || !choicesElement.isJsonArray) {
                throw ProtocolDetail(AiProtocolFailureDetail.choices_not_array)
            }
            val choices = choicesElement.asJsonArray
            if (choices.size() != 1) throw ProtocolDetail(AiProtocolFailureDetail.choices_count)
            if (!choices[0].isJsonObject) throw ProtocolDetail(AiProtocolFailureDetail.choice_not_object)
            val choice = choices[0].asJsonObject
            val finishElement = choice.get("finish_reason")
            if (finishElement != null && (!finishElement.isJsonPrimitive || !finishElement.asJsonPrimitive.isString)) {
                throw ProtocolDetail(AiProtocolFailureDetail.finish_reason_not_string)
            }
            val finishReason = normalizeFinishReason(finishElement?.asString)
            val messageElement = choice.get("message")
            if (messageElement == null || !messageElement.isJsonObject) {
                throw ProtocolDetail(AiProtocolFailureDetail.message_not_object)
            }
            val contentElement = messageElement.asJsonObject.get("content")
                ?: throw ProtocolDetail(AiProtocolFailureDetail.no_content)
            if (!contentElement.isJsonPrimitive || !contentElement.asJsonPrimitive.isString) {
                throw ProtocolDetail(AiProtocolFailureDetail.content_not_string)
            }
            val content = contentElement.asString
            // 空白 message.content 表示模型未产出合法 JSON 对象，属协议不兼容。
            // 必须失败关闭为 PROTOCOL：严禁由客户端替服务端补造“无编辑”成功载荷，否则
            // 一整章空白响应也会被当作 Completed 缓存，掩盖真实的协议不兼容。
            if (content.isBlank()) throw ProtocolDetail(AiProtocolFailureDetail.blank_content)
            if (content.trimStart().startsWith("```")) {
                throw ProtocolDetail(AiProtocolFailureDetail.payload_markdown_fence)
            }
            val parsedPayload = try {
                parseStrictJson(content)
            } catch (_: Exception) {
                throw ProtocolDetail(classifyPayloadJsonSyntax(content))
            }
            if (!parsedPayload.isJsonObject) throw ProtocolDetail(AiProtocolFailureDetail.payload_not_object)
            return parseOutputPayload(parsedPayload.asJsonObject, finishReason)
        } catch (e: Exception) {
            val detail = (e as? ProtocolDetail)?.detail ?: AiProtocolFailureDetail.unknown
            protocolReporter(detail)
            throw AiProviderException(AiProviderError.protocol)
        }
    }

    private class ProtocolDetail(val detail: AiProtocolFailureDetail) : IllegalArgumentException()

    private fun parseStrictJson(content: String) = JsonReader(StringReader(content)).use { reader ->
        reader.setStrictness(Strictness.STRICT)
        val parsed = JsonParser.parseReader(reader)
        if (reader.peek() != JsonToken.END_DOCUMENT) throw IllegalArgumentException()
        parsed
    }

    /**
     * Classifies only JSON grammar shape. The result is a fixed enum: it never retains or reports
     * payload text, offsets, lengths, hashes, parser messages, or field values.
     */
    private fun classifyPayloadJsonSyntax(content: String): AiProtocolFailureDetail {
        val start = content.indexOfFirst { !it.isWhitespace() }
        if (start < 0 || (content[start] != '{' && content[start] != '[')) {
            return AiProtocolFailureDetail.payload_json_syntax_non_json_prefix
        }

        val containers = ArrayDeque<Char>()
        var inString = false
        var escaping = false
        var unicodeDigitsRemaining = 0
        var rootClosed = false
        var index = start
        while (index < content.length) {
            val char = content[index]
            if (rootClosed) {
                if (!char.isWhitespace()) {
                    return AiProtocolFailureDetail.payload_json_syntax_trailing_data
                }
                index++
                continue
            }
            if (inString) {
                if (unicodeDigitsRemaining > 0) {
                    if (!char.isHexDigit()) {
                        return AiProtocolFailureDetail.payload_json_syntax_invalid_escape
                    }
                    unicodeDigitsRemaining--
                } else if (escaping) {
                    when (char) {
                        '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> escaping = false
                        'u' -> {
                            escaping = false
                            unicodeDigitsRemaining = 4
                        }
                        else -> return AiProtocolFailureDetail.payload_json_syntax_invalid_escape
                    }
                } else {
                    when {
                        char == '\\' -> escaping = true
                        char == '"' -> inString = false
                        char.code < 0x20 -> {
                            return AiProtocolFailureDetail.payload_json_syntax_raw_control
                        }
                    }
                }
                index++
                continue
            }

            when (char) {
                '"' -> inString = true
                '{', '[' -> containers.addLast(char)
                '}', ']' -> {
                    val expected = if (char == '}') '{' else '['
                    if (containers.isEmpty() || containers.removeLast() != expected) {
                        return AiProtocolFailureDetail.payload_json_syntax_mismatched_closer
                    }
                    if (containers.isEmpty()) rootClosed = true
                }
            }
            index++
        }
        return when {
            unicodeDigitsRemaining > 0 ->
                AiProtocolFailureDetail.payload_json_syntax_invalid_escape
            escaping || inString ->
                AiProtocolFailureDetail.payload_json_syntax_unterminated_string
            containers.isNotEmpty() ->
                AiProtocolFailureDetail.payload_json_syntax_unclosed_container
            else -> AiProtocolFailureDetail.payload_json_syntax
        }
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun parseOutputPayload(payload: JsonObject, finishReason: AiFinishReason): AiChunkOutput {
        val chunkId = requiredString(payload, "chunk_id", AiProtocolFailureDetail.chunk_id_not_string)
        val editsElement = payload.get("edits")
        if (editsElement == null || !editsElement.isJsonArray) {
            throw ProtocolDetail(AiProtocolFailureDetail.no_edits_array)
        }
        val edits = editsElement.asJsonArray.map { element ->
            if (!element.isJsonObject) throw ProtocolDetail(AiProtocolFailureDetail.edit_not_object)
            val edit = element.asJsonObject
            val kindName = requiredString(edit, "kind", AiProtocolFailureDetail.kind_not_string)
            AiEdit(
                requiredInt(edit, "start", AiProtocolFailureDetail.start_not_int),
                requiredInt(edit, "end", AiProtocolFailureDetail.end_not_int),
                requiredString(edit, "original", AiProtocolFailureDetail.original_not_string),
                requiredString(edit, "replacement", AiProtocolFailureDetail.replacement_not_string),
                AiEditKind.entries.firstOrNull { it.name == kindName }
                    ?: throw ProtocolDetail(AiProtocolFailureDetail.bad_kind)
            )
        }
        val warnings = payload.get("warnings")?.let { element ->
            if (!element.isJsonArray) throw ProtocolDetail(AiProtocolFailureDetail.warnings_not_array)
            element.asJsonArray.map {
                if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString) {
                    throw ProtocolDetail(AiProtocolFailureDetail.warnings_not_string)
                }
                it.asString
            }
        } ?: emptyList()
        return AiChunkOutput(chunkId, edits, warnings, finishReason)
    }

    private fun requiredString(
        value: JsonObject,
        name: String,
        detail: AiProtocolFailureDetail
    ): String {
        val element = value.get(name)
        if (element == null || !element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            throw ProtocolDetail(detail)
        }
        return element.asString
    }

    private fun requiredInt(
        value: JsonObject,
        name: String,
        detail: AiProtocolFailureDetail
    ): Int {
        val element = value.get(name)
        if (element == null || !element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
            throw ProtocolDetail(detail)
        }
        val raw = element.asString
        if (!raw.matches(Regex("-?(?:0|[1-9]\\d*)"))) throw ProtocolDetail(detail)
        return raw.toIntOrNull() ?: throw ProtocolDetail(detail)
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
You repair only local typos, anti-theft substitutions, de-noise of inserted decoration, punctuation, and whitespace in the supplied text.
Return exactly one json object and no markdown or commentary.
Rules for edits:
- start/end are zero-based Unicode code points in text, with end exclusive.
- edits must be sorted by start, non-overlapping, and contain the exact substring from text in original.
- typo and anti_theft original/replacement must contain Unicode letters only, have equal code-point counts, and each contain 1..12 code points.
- punctuation and whitespace original/replacement may be empty but may contain only Unicode punctuation or whitespace.
- denoise removes a tiny amount (1..2) of inserted decoration inside a word: allowed noise is only ascii punctuation like * _ - ~ ., middle dots, plain/nbsp/ideographic space, and zero-width chars. The letters kept in original must be identical to replacement (only noise removed, never a letter changed); the first and last code point of original must both be letters.
- return at most 128 edits. If any edit is uncertain, omit it instead of guessing.
- NEVER emit a no-op edit where original equals replacement; if a span needs no change, do not include it in edits at all.
- A correct chapter needs no changes: return an empty edits array when no repair is needed. Never invent errors to produce edits.
- Each non-empty original must occur exactly once in text. If it repeats, omit the edit instead of guessing its position.
- context_only is read-only context and must never be edited or repeated.
Do not rewrite, summarize, continue, translate, add or change letters, or alter sentinels, URLs, numbers, names, facts, plot, or style.
json example: {"chunk_id":"chunk-0","edits":[{"start":0,"end":1,"original":"甲","replacement":"乙","kind":"typo"}],"warnings":[]}.
"""
    }
}
