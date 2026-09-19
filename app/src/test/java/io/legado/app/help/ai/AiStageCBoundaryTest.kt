package io.legado.app.help.ai

import org.junit.Assert.*
import org.junit.Test

/** Independent public-contract counterexamples for Stage C. */
class AiStageCBoundaryTest {
    @Test fun sentinelNearLimitNeverExpandsChunkBudget() {
        val input = "甲".repeat(55) + " https://example.invalid/123456789 " + "乙".repeat(100)
        val result = AiTextChunker.chunk(input, 64, 20)
        assertEquals(input, result.protectedText.restore(result.chunks.joinToString("") { it.text }))
        assertTrue(result.chunks.all { it.text.codePointCount(0, it.text.length) <= 64 })
        assertTrue(result.chunks.all { (it.contextOnly ?: "").codePointCount(0, (it.contextOnly ?: "").length) <= 20 })
    }

    @Test fun combiningMarksStayWithBaseAndContextDoesNotStartWithMark() {
        val result = AiTextChunker.chunk("甲e\u0301乙e\u0301丙", 2, 1, "fixed")
        assertEquals("甲e\u0301乙e\u0301丙", result.chunks.joinToString("") { it.text })
        result.chunks.forEach {
            assertFalse(it.text.startsWith("\u0301"))
            assertFalse(it.contextOnly?.startsWith("\u0301") == true)
            assertTrue((it.contextOnly ?: "").codePointCount(0, (it.contextOnly ?: "").length) <= 1)
            assertTrue(it.text.codePointCount(0, it.text.length) <= 2)
        }
    }

    @Test fun numbersAdjacentToChineseAreProtected() {
        val value = AiTextChunker.protect("第123章花了12.50元还有９９人", "fixed")
        assertEquals(listOf("123", "12.50", "９９"), value.sentinels.values.toList())
        assertEquals(value.original, value.restore(value.protected))
    }

    @Test fun symbolAndControlInsertionAreNotPunctuation() {
        listOf("😀", "\u0000", "\uE001", "$").forEach { replacement ->
            val chunk = AiTextChunk("chunk-0", "甲。", null)
            val output = AiChunkOutput(chunk.id, listOf(AiEdit(1, 2, "。", replacement, AiEditKind.punctuation)))
            assertTrue("accepted invalid replacement", AiOutputValidator.validateAndApply(chunk, output, emptySet()) is AiValidationResult.Invalid)
        }
    }

    @Test fun insertInsideSentinelMustFailEvenForEmptyRange() {
        val value = AiTextChunker.protect("123", "fixed")
        val chunk = AiTextChunk("chunk-0", value.protected, null)
        val output = AiChunkOutput(chunk.id, listOf(AiEdit(2, 2, "", " ", AiEditKind.whitespace)))
        assertTrue(AiOutputValidator.validateAndApply(chunk, output, value.sentinels.keys) is AiValidationResult.Invalid)
    }

    @Test fun textEditCannotIntroduceNumber() {
        val chunk = AiTextChunk("chunk-0", "甲乙", null)
        val output = AiChunkOutput(chunk.id, listOf(AiEdit(0, 1, "甲", "9", AiEditKind.typo)))
        assertTrue(AiOutputValidator.validateAndApply(chunk, output, emptySet()) is AiValidationResult.Invalid)
    }

    @Test fun restorationRejectsReorderedOrDuplicatedProtectedValues() {
        val value = AiTextChunker.protect("12 与 34", "fixed")
        val tokens = value.sentinels.keys.toList()
        listOf(tokens[1] + " 与 " + tokens[0], value.protected + tokens[0]).forEach {
            assertTrue(runCatching { value.restore(it) }.isFailure)
        }
    }

    @Test fun tooLargeAtomicUnitFailsInsteadOfSendingOverBudget() {
        assertTrue(runCatching { AiTextChunker.chunk("e" + "\u0301".repeat(10), 4) }.isFailure)
    }

    @Test fun longChapterHasBoundedFreshRequestsAndExactCoverage() {
        val input = ("这一段保留原有文字。\n\n".repeat(6000))
        val result = AiTextChunker.chunk(input)
        assertTrue(result.chunks.size > 10)
        assertEquals(input, result.chunks.joinToString("") { it.text })
        result.chunks.forEachIndexed { index, chunk ->
            assertTrue(chunk.text.codePointCount(0, chunk.text.length) <= 6000)
            val context = chunk.contextOnly ?: ""
            assertTrue(context.codePointCount(0, context.length) <= 200)
            if (index > 0) assertTrue(result.chunks[index - 1].text.endsWith(context))
        }
    }

    @Test fun paragraphBoundaryWinsOverLaterSentenceBoundary() {
        val result = AiTextChunker.chunk("甲乙\n\n丙丁。戊己庚辛", 8)
        assertEquals("甲乙\n\n", result.chunks.first().text)
    }

    @Test fun typoCannotSmuggleControlSymbolsOrMarkupAroundEqualLetterCount() {
        val chunk = AiTextChunk("chunk-0", "甲乙丙", null)
        listOf("丁😀戊己", "丁\u0000戊己", "<p>戊己").forEach { replacement ->
            val output = AiChunkOutput(chunk.id, listOf(AiEdit(0, 3, "甲乙丙", replacement, AiEditKind.typo)))
            assertTrue(AiOutputValidator.validateAndApply(chunk, output, emptySet()) is AiValidationResult.Invalid)
        }
    }
}
