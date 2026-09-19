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
        // 校验保护项出现顺序 + 唯一性：不得重排、不得复制，也不得出现未闭合/未知哨兵。
        val expected = sentinels.keys.toList()
        val actual = mutableListOf<String>()
        var i = 0
        while (i < value.length) {
            val cp = value.codePointAt(i)
            if (cp == AiTextChunker.SENTINEL_START.code) {
                val close = value.indexOf(AiTextChunker.SENTINEL_END, i + 1)
                require(close > i) { "unterminated sentinel" }
                actual += value.substring(i, close + 1)
                i = close + 1
            } else {
                i += Character.charCount(cp)
            }
        }
        require(actual == expected) { "protected values reordered or duplicated" }

        var restored = value
        sentinels.forEach { (sentinel, originalValue) ->
            restored = restored.replace(sentinel, originalValue)
        }
        require(!restored.contains(AiTextChunker.SENTINEL_START) && !restored.contains(AiTextChunker.SENTINEL_END)) {
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

/**
 * 音节/组合记号是否应与其前一个基字符被视为同一书写单元。
 * 涵盖组合记号（Mn/Mc/Me）、变体选择符、零宽连接/非连接符，避免把它们与基字符分离。
 */
internal fun isCombiningCodePoint(codePoint: Int): Boolean {
    val type = Character.getType(codePoint)
    return type == Character.NON_SPACING_MARK.toInt() ||
        type == Character.COMBINING_SPACING_MARK.toInt() ||
        type == Character.ENCLOSING_MARK.toInt() ||
        isVariationSelector(codePoint) ||
        codePoint == 0x200C || codePoint == 0x200D // ZWNJ / ZWJ
}

private fun isVariationSelector(codePoint: Int): Boolean =
    codePoint in 0xFE00..0xFE0F || codePoint in 0xE0100..0xE01EF

object AiTextChunker {
    internal const val SENTINEL_START = '\uE000'
    internal const val SENTINEL_END = '\uE001'
    private val values = Regex("https?://[^\\s<>\\\"']+|[\\p{N}]+(?:[,._:/-][\\p{N}]+)*", RegexOption.IGNORE_CASE)
    private val sentenceEnds = setOf('。', '！', '？', '；', '.', '!', '?', ';')

    internal fun protectedValues(text: String): List<String> = values.findAll(text).map { it.value }.toList()

    fun protect(input: String, nonce: String = UUID.randomUUID().toString().replace("-", "")): AiProtectedText {
        require(nonce.matches(Regex("[A-Za-z0-9_-]+"))) { "invalid_nonce" }
        require(SENTINEL_START !in input && SENTINEL_END !in input) { "reserved_character" }
        val sentinels = linkedMapOf<String, String>()
        val protected = values.replace(input) {
            val marker = "$SENTINEL_START$nonce:${sentinels.size}$SENTINEL_END"
            sentinels[marker] = it.value
            marker
        }
        return AiProtectedText(input, protected, sentinels)
    }

    /** Linear atomic-boundary scan; bodies/context are generated lazily so previous request
     * bodies are not retained in a list. An indivisible unit over budget fails closed. */
    fun chunk(input: String, maxChunkCodePoints: Int = 6000, maxContextCodePoints: Int = 200,
              nonce: String = UUID.randomUUID().toString().replace("-", "")): AiChunkingResult {
        require(maxChunkCodePoints > 0 && maxContextCodePoints in 0..200)
        val protected = protect(input, nonce)
        val text = protected.protected
        val atoms = arrayListOf(0)
        val counts = arrayListOf(0)
        var pos = 0
        var total = 0
        while (pos < text.length) {
            val start = pos
            pos = if (text[pos] == SENTINEL_START) text.indexOf(SENTINEL_END, pos) + 1
                else pos + Character.charCount(text.codePointAt(pos))
            while (pos < text.length && (isCombiningCodePoint(text.codePointAt(pos)) ||
                    text.codePointBefore(pos) == 0x200D || text.codePointAt(pos) in 0x1F3FB..0x1F3FF)) {
                pos += Character.charCount(text.codePointAt(pos))
            }
            val count = text.codePointCount(start, pos)
            require(count <= maxChunkCodePoints) { "atomic_unit_exceeds_budget" }
            total += count
            atoms.add(pos); counts.add(total)
        }
        val ends = arrayListOf(0)
        var startAtom = 0
        while (startAtom < atoms.lastIndex) {
            var last = startAtom
            var paragraph = -1
            var line = -1
            var sentence = -1
            while (last < atoms.lastIndex && counts[last + 1] - counts[startAtom] <= maxChunkCodePoints) {
                last++
                val end = atoms[last]
                if (text[end - 1] == '\n') {
                    line = last
                    if (end >= 2 && text[end - 2] == '\n') paragraph = last
                } else if (text[end - 1] in sentenceEnds) sentence = last
            }
            val endAtom = if (last == atoms.lastIndex) last else when {
                paragraph > startAtom -> paragraph
                line > startAtom -> line
                sentence > startAtom -> sentence
                else -> last
            }
            check(endAtom > startAtom)
            ends.add(atoms[endAtom]); startAtom = endAtom
        }
        val chunks = object : AbstractList<AiTextChunk>() {
            override val size: Int get() = ends.size - 1
            override fun get(index: Int): AiTextChunk {
                require(index in 0 until size)
                val body = text.substring(ends[index], ends[index + 1])
                val context = if (index == 0 || maxContextCodePoints == 0) null else {
                    val previousStart = ends[index - 1]
                    val end = ends[index]
                    val length = text.codePointCount(previousStart, end)
                    val wanted = text.offsetByCodePoints(end, -minOf(length, maxContextCodePoints))
                    val found = atoms.binarySearch(wanted)
                    val safe = if (found >= 0) atoms[found] else atoms[-found - 1]
                    text.substring(safe, end).takeIf { it.isNotEmpty() }
                }
                return AiTextChunk("chunk-$index", body, context)
            }
        }
        return AiChunkingResult(protected, chunks)
    }
}
