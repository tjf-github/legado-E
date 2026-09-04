package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiOutputValidatorTest {

    @Test
    fun appliesValidatedEditsFromEndToStart() {
        val chunk = AiTextChunk("chunk-0", "他走的很快！！", null)
        val output = AiChunkOutput(
            chunkId = chunk.id,
            edits = listOf(
                AiEdit(2, 3, "的", "得", AiEditKind.typo),
                AiEdit(5, 7, "！！", "！", AiEditKind.punctuation)
            )
        )

        val result = AiOutputValidator.validateAndApply(chunk, output, emptySet())
        assertEquals(AiValidationResult.Valid("他走得很快！"), result)
    }

    @Test
    fun rejectsOverlapWrongAnchorAndWholeSentenceDeletion() {
        val chunk = AiTextChunk("chunk-0", "甲乙丙丁", null)
        val overlap = AiChunkOutput(
            chunk.id,
            listOf(
                AiEdit(0, 2, "甲乙", "戊己", AiEditKind.typo),
                AiEdit(1, 2, "乙", "庚", AiEditKind.typo)
            )
        )
        val wrongAnchor = AiChunkOutput(
            chunk.id,
            listOf(AiEdit(0, 1, "错", "戊", AiEditKind.typo))
        )
        val deletion = AiChunkOutput(
            chunk.id,
            listOf(AiEdit(0, 4, "甲乙丙丁", "", AiEditKind.typo))
        )

        assertTrue(AiOutputValidator.validateAndApply(chunk, overlap, emptySet()) is AiValidationResult.Invalid)
        assertTrue(AiOutputValidator.validateAndApply(chunk, wrongAnchor, emptySet()) is AiValidationResult.Invalid)
        assertTrue(AiOutputValidator.validateAndApply(chunk, deletion, emptySet()) is AiValidationResult.Invalid)
    }

    @Test
    fun rejectsTextInsidePunctuationEditAndSentinelMutation() {
        val protected = AiTextChunker.protect("编号 123456 结束", "validator")
        val sentinel = protected.sentinels.keys.single()
        val chunk = AiTextChunk("chunk-0", protected.protected, null)
        val textAsPunctuation = AiChunkOutput(
            chunk.id,
            listOf(AiEdit(0, 1, "编", "号", AiEditKind.punctuation))
        )
        val sentinelStart = chunk.text.codePointCount(0, chunk.text.indexOf(sentinel))
        val sentinelMutation = AiChunkOutput(
            chunk.id,
            listOf(
                AiEdit(
                    sentinelStart,
                    sentinelStart + sentinel.codePointCount(0, sentinel.length),
                    sentinel,
                    "123456",
                    AiEditKind.typo
                )
            )
        )

        assertTrue(
            AiOutputValidator.validateAndApply(chunk, textAsPunctuation, setOf(sentinel)) is AiValidationResult.Invalid
        )
        assertTrue(
            AiOutputValidator.validateAndApply(chunk, sentinelMutation, setOf(sentinel)) is AiValidationResult.Invalid
        )
    }

    @Test
    fun rejectsTruncationWrongChunkAndTooManyEdits() {
        val chunk = AiTextChunk("chunk-0", "甲", null)
        assertTrue(
            AiOutputValidator.validateAndApply(
                chunk,
                AiChunkOutput(chunk.id, emptyList(), finishReason = AiFinishReason.length),
                emptySet()
            ) is AiValidationResult.Invalid
        )
        assertTrue(
            AiOutputValidator.validateAndApply(
                chunk,
                AiChunkOutput("other", emptyList()),
                emptySet()
            ) is AiValidationResult.Invalid
        )
        val excessive = List(129) { AiEdit(0, 0, "", "", AiEditKind.whitespace) }
        assertTrue(
            AiOutputValidator.validateAndApply(
                chunk,
                AiChunkOutput(chunk.id, excessive),
                emptySet()
            ) is AiValidationResult.Invalid
        )
    }
}
