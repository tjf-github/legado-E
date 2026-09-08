package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTextChunkerTest {

    @Test
    fun chunksCoverProtectedInputAndRestoreOriginal() {
        val input = "第一段 https://example.com/a/12345?q=99\n\n第二段 12345678901234567890 结束。"
        val result = AiTextChunker.chunk(input, maxChunkCodePoints = 12, nonce = "fixed")

        assertEquals(result.protectedText.protected, result.chunks.joinToString("") { it.text })
        assertEquals(input, result.protectedText.restore(result.chunks.joinToString("") { it.text }))
        result.protectedText.sentinels.keys.forEach { sentinel ->
            assertEquals(1, result.chunks.count { sentinel in it.text })
        }
    }

    @Test
    fun hardSplitKeepsUnicodeCodePointsIntact() {
        val input = "甲😀乙🚀丙🌟丁"
        val result = AiTextChunker.chunk(input, maxChunkCodePoints = 2, nonce = "emoji")

        assertEquals(input, result.chunks.joinToString("") { it.text })
        result.chunks.forEach { chunk ->
            assertFalse(chunk.text.first().isLowSurrogate())
            assertFalse(chunk.text.last().isHighSurrogate())
        }
    }

    @Test
    fun contextIsBoundedAndNotPartOfChunkCoverage() {
        val input = "甲乙丙丁戊己庚辛壬癸"
        val result = AiTextChunker.chunk(
            input,
            maxChunkCodePoints = 3,
            maxContextCodePoints = 2,
            nonce = "context"
        )

        assertEquals(input, result.chunks.joinToString("") { it.text })
        assertEquals(null, result.chunks.first().contextOnly)
        result.chunks.drop(1).forEachIndexed { index, chunk ->
            assertEquals(result.chunks[index].text.takeLast(2), chunk.contextOnly)
            assertTrue(chunk.contextOnly!!.codePointCount(0, chunk.contextOnly.length) <= 2)
        }
    }

    @Test
    fun paragraphAndSentenceBoundariesArePreferred() {
        val input = "甲乙丙。丁戊己。\n\n庚辛壬癸"
        val result = AiTextChunker.chunk(input, maxChunkCodePoints = 10, nonce = "boundary")

        assertEquals("甲乙丙。丁戊己。\n\n", result.chunks.first().text)
        assertEquals(input, result.chunks.joinToString("") { it.text })
    }
}
