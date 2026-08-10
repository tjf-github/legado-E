package io.legado.app.help.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterNumberUtilsTest {

    @Test
    fun rewriteCnTitlePlusOne() {
        assertEquals("第101章 风雨", ChapterNumberUtils.rewriteTitle("第100章 风雨", 1))
        assertEquals("第101回 完结", ChapterNumberUtils.rewriteTitle("第100回 完结", 1))
        assertEquals("第101节", ChapterNumberUtils.rewriteTitle("第100节", 1))
    }

    @Test
    fun rewriteCnTitleMinusOne() {
        assertEquals("第99章 风雨", ChapterNumberUtils.rewriteTitle("第100章 风雨", -1))
    }

    @Test
    fun rewriteEnTitle() {
        assertEquals("Chapter 11", ChapterNumberUtils.rewriteTitle("Chapter 10", 1))
        assertEquals("chapter 1 test", ChapterNumberUtils.rewriteTitle("chapter 2 test", -1))
    }

    @Test
    fun noPatternReturnsNull() {
        assertNull(ChapterNumberUtils.rewriteTitle("序章", 1))
        assertNull(ChapterNumberUtils.rewriteTitle("番外", -1))
        assertNull(ChapterNumberUtils.rewriteTitle("", 1))
        assertNull(ChapterNumberUtils.rewriteTitle("第100章", 0))
    }

    @Test
    fun invalidResultReturnsNull() {
        assertNull(ChapterNumberUtils.rewriteTitle("第1章", -1))
        assertNull(ChapterNumberUtils.rewriteTitle("Chapter 1", -1))
    }

    @Test
    fun nextNumberTitleReturnsPrefixOnly() {
        assertEquals("第3章", ChapterNumberUtils.nextNumberTitle("第2章 山中怪影", 1))
        assertEquals("第1章", ChapterNumberUtils.nextNumberTitle("第2章 山中怪影", -1))
        assertEquals("Chapter 3", ChapterNumberUtils.nextNumberTitle("Chapter 2 abc", 1))
        assertNull(ChapterNumberUtils.nextNumberTitle("序章", 1))
        assertNull(ChapterNumberUtils.nextNumberTitle("第1章", -1))
    }
}
