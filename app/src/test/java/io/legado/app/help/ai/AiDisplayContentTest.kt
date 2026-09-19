package io.legado.app.help.ai

import io.legado.app.data.entities.ReplaceRule
import io.legado.app.help.book.BookContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDisplayContentTest {
    private val original = BookContent(true, listOf("正文甲", "正文乙"), emptyList<ReplaceRule>())

    @Test fun originalDefaultNeverExposesCandidate() {
        val display = AiDisplayContent.original(original)
        assertSame(original, display.content)
        assertSame(original, display.original)
        assertNull(display.candidate)
        assertFalse(display.isAi)
        assertEquals(AiDisplaySource.Original, display.source)
    }

    @Test fun aiCandidateOnlyReplacesTextListAndKeepsMetadata() {
        val candidate = original.withAiText("净化甲\n正文乙")
        val display = AiDisplayContent.ai(original, candidate)
        assertTrue(display.isAi)
        assertEquals(AiDisplaySource.Ai, display.source)
        assertSame(candidate, display.content)
        // 元数据保留
        assertTrue(display.content.sameTitleRemoved)
        assertEquals(emptyList<Any>(), display.content.effectiveReplaceRules)
        // 只改 textList
        assertEquals(listOf("净化甲", "正文乙"), display.content.textList)
        assertEquals(listOf("正文甲", "正文乙"), original.textList)
    }

    @Test fun switchingSourceChangesExposedContent() {
        val candidate = original.withAiText("净化")
        val display = AiDisplayContent.ai(original, candidate)
        assertSame(candidate, display.content)
        val back = AiDisplayContent(display.original, display.candidate, AiDisplaySource.Original)
        assertSame(original, back.content)
    }

    @Test fun emptyParagraphsSurviveRoundTrip() {
        val content = BookContent(false, listOf("正文甲", "", "正文乙"), null)
        val candidate = content.withAiText("正文甲\n\n正文乙")
        assertEquals(listOf("正文甲", "", "正文乙"), candidate.textList)
    }
}
