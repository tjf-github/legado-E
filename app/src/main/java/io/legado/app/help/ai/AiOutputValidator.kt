package io.legado.app.help.ai

sealed class AiValidationResult {
    data class Valid(val protectedText: String) : AiValidationResult() {
        override fun toString(): String = "Valid(protectedText=[REDACTED])"
    }
    data class Invalid(val reason: String) : AiValidationResult()
}

object AiOutputValidator {
    private const val MAX_EDITS = 128
    private const val MAX_TEXT_EDIT_CORE = 12

    fun validateAndApply(
        chunk: AiTextChunk,
        output: AiChunkOutput,
        knownSentinels: Set<String>
    ): AiValidationResult {
        fun invalid(reason: String) = AiValidationResult.Invalid(reason)
        if (output.chunkId != chunk.id) return invalid("chunk_id mismatch")
        if (output.finishReason != AiFinishReason.stop) return invalid("incomplete finish_reason")
        if (output.edits.size > MAX_EDITS) return invalid("too many edits")

        var previousEnd = 0
        val charRanges = mutableListOf<Triple<Int, Int, AiEdit>>()
        output.edits.forEach { edit ->
            if (edit.start < previousEnd || edit.start < 0 || edit.end < edit.start) {
                return invalid("unordered or overlapping edit")
            }
            val codePoints = chunk.text.codePointCount(0, chunk.text.length)
            if (edit.end > codePoints) return invalid("edit out of bounds")
            val charStart = chunk.text.offsetByCodePoints(0, edit.start)
            val charEnd = chunk.text.offsetByCodePoints(0, edit.end)
            if (chunk.text.substring(charStart, charEnd) != edit.original) {
                return invalid("original anchor mismatch")
            }
            if (containsOrTouchesSentinel(chunk.text, charStart, charEnd, knownSentinels) ||
                containsSentinelSyntax(edit.replacement, knownSentinels)
            ) {
                return invalid("sentinel edited or constructed")
            }
            when (edit.kind) {
                AiEditKind.typo, AiEditKind.anti_theft -> {
                    val originalCore = coreCharacters(edit.original)
                    val replacementCore = coreCharacters(edit.replacement)
                    if (originalCore.isEmpty() || replacementCore.isEmpty() ||
                        originalCore.size != replacementCore.size ||
                        originalCore.size > MAX_TEXT_EDIT_CORE || replacementCore.size > MAX_TEXT_EDIT_CORE
                    ) return invalid("invalid text edit")
                }
                AiEditKind.punctuation, AiEditKind.whitespace -> {
                    if (hasLetterOrDigit(edit.original) || hasLetterOrDigit(edit.replacement)) {
                        return invalid("punctuation edit contains text")
                    }
                }
            }
            charRanges += Triple(charStart, charEnd, edit)
            previousEnd = edit.end
        }

        val result = StringBuilder(chunk.text)
        charRanges.asReversed().forEach { (start, end, edit) ->
            result.replace(start, end, edit.replacement)
        }
        return AiValidationResult.Valid(result.toString())
    }

    private fun coreCharacters(value: String): List<Int> {
        val result = mutableListOf<Int>()
        var i = 0
        while (i < value.length) {
            val cp = value.codePointAt(i)
            if (Character.isLetterOrDigit(cp)) result.add(cp)
            i += Character.charCount(cp)
        }
        return result
    }

    private fun hasLetterOrDigit(value: String): Boolean {
        var i = 0
        while (i < value.length) {
            val cp = value.codePointAt(i)
            if (Character.isLetterOrDigit(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    private fun containsSentinelSyntax(value: String, sentinels: Set<String>): Boolean =
        value.contains(AiTextChunker.SENTINEL_START) || sentinels.any(value::contains)

    private fun containsOrTouchesSentinel(
        text: String,
        start: Int,
        end: Int,
        sentinels: Set<String>
    ): Boolean = sentinels.any { sentinel ->
        var index = text.indexOf(sentinel)
        while (index >= 0) {
            val sentinelEnd = index + sentinel.length
            if (start < sentinelEnd && end > index) return true
            index = text.indexOf(sentinel, sentinelEnd)
        }
        false
    }
}
