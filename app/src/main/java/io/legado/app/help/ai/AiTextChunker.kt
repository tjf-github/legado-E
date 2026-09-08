package io.legado.app.help.ai

import java.util.UUID

data class AiProtectedText(
    val original: String,
    val protected: String,
    val sentinels: Map<String, String>
) {
    override fun toString(): String =
        "AiProtectedText(original=[REDACTED], protected=[REDACTED], sentinelCount=${sentinels.size})"

    fun restore(value: String): String {
        var restored = value
        sentinels.forEach { (sentinel, originalValue) ->
            require(restored.windowed(sentinel.length, 1).count { it == sentinel } == 1) {
                "protected value was changed"
            }
            restored = restored.replace(sentinel, originalValue)
        }
        require(!restored.contains(AiTextChunker.SENTINEL_START)) {
            "unknown sentinel in result"
        }
        return restored
    }
}

data class AiTextChunk(
    val id: String,
    val text: String,
    val contextOnly: String?
) {
    override fun toString(): String =
        "AiTextChunk(id=$id, text=[REDACTED], contextOnly=[REDACTED])"
}

data class AiChunkingResult(
    val protectedText: AiProtectedText,
    val chunks: List<AiTextChunk>
) {
    override fun toString(): String =
        "AiChunkingResult(protectedText=[REDACTED], chunkCount=${chunks.size})"
}

object AiTextChunker {
    internal const val SENTINEL_START = '\uE000'
    private const val SENTINEL_END = '\uE001'
    private val protectedValueRegex = Regex(
        "https?://[^\\s<>\\\"']+|(?<![\\p{L}\\p{N}_])(?:\\d[\\d,._:/-]*\\d|\\d)(?![\\p{L}\\p{N}_])",
        RegexOption.IGNORE_CASE
    )
    private val preferredSentenceEnds = setOf('\u3002', '\uff01', '\uff1f', '\uff1b', '.', '!', '?', ';')

    fun protect(input: String, nonce: String = UUID.randomUUID().toString().replace("-", "")): AiProtectedText {
        require(nonce.isNotBlank())
        val sentinels = linkedMapOf<String, String>()
        var index = 0
        val protected = protectedValueRegex.replace(input) { match ->
            val sentinel = "$SENTINEL_START$nonce:${index++}$SENTINEL_END"
            sentinels[sentinel] = match.value
            sentinel
        }
        return AiProtectedText(input, protected, sentinels)
    }

    fun chunk(
        input: String,
        maxChunkCodePoints: Int = 6000,
        maxContextCodePoints: Int = 200,
        nonce: String = UUID.randomUUID().toString().replace("-", "")
    ): AiChunkingResult {
        require(maxChunkCodePoints > 0)
        require(maxContextCodePoints >= 0)
        val protectedText = protect(input, nonce)
        val text = protectedText.protected
        if (text.isEmpty()) return AiChunkingResult(protectedText, emptyList())

        val chunks = mutableListOf<AiTextChunk>()
        var start = 0
        while (start < text.length) {
            val hardEnd = offsetByCodePoints(text, start, maxChunkCodePoints)
            val end = if (hardEnd == text.length) {
                hardEnd
            } else {
                chooseBoundary(text, start, avoidSentinelSplit(text, hardEnd))
            }
            val body = text.substring(start, end)
            val context = chunks.lastOrNull()?.text?.let {
                safeTail(it, maxContextCodePoints)
            }?.takeIf(String::isNotEmpty)
            chunks += AiTextChunk("chunk-${chunks.size}", body, context)
            start = end
        }

        check(chunks.joinToString("") { it.text } == protectedText.protected)
        check(protectedText.restore(chunks.joinToString("") { it.text }) == input)
        return AiChunkingResult(protectedText, chunks)
    }

    private fun chooseBoundary(text: String, start: Int, hardEnd: Int): Int {
        if (hardEnd <= start) return nextAtomicEnd(text, start)
        var index = hardEnd
        while (index > start) {
            val previous = text.codePointBefore(index)
            val previousStart = index - Character.charCount(previous)
            if (previous == '\n'.code && previousStart > start && text.codePointBefore(previousStart) == '\n'.code) {
                return index
            }
            index = previousStart
        }
        index = hardEnd
        while (index > start) {
            val previous = text.codePointBefore(index)
            if (previous == '\n'.code || preferredSentenceEnds.contains(previous.toChar())) return index
            index -= Character.charCount(previous)
        }
        return hardEnd
    }

    private fun avoidSentinelSplit(text: String, proposedEnd: Int): Int {
        val open = text.lastIndexOf(SENTINEL_START, proposedEnd - 1)
        if (open < 0) return proposedEnd
        val close = text.indexOf(SENTINEL_END, open)
        return if (close >= proposedEnd) close + 1 else proposedEnd
    }

    private fun nextAtomicEnd(text: String, start: Int): Int {
        if (text[start] == SENTINEL_START) {
            val close = text.indexOf(SENTINEL_END, start)
            require(close >= 0) { "unterminated sentinel" }
            return close + 1
        }
        return start + Character.charCount(text.codePointAt(start))
    }

    private fun offsetByCodePoints(text: String, start: Int, count: Int): Int {
        val available = text.codePointCount(start, text.length)
        return text.offsetByCodePoints(start, minOf(count, available))
    }

    private fun safeTail(text: String, maxCodePoints: Int): String {
        if (maxCodePoints == 0) return ""
        val total = text.codePointCount(0, text.length)
        var start = text.offsetByCodePoints(0, maxOf(0, total - maxCodePoints))
        val open = text.lastIndexOf(SENTINEL_START, start)
        if (open >= 0) {
            val close = text.indexOf(SENTINEL_END, open)
            if (close >= start) start = close + 1
        }
        return text.substring(start)
    }
}
