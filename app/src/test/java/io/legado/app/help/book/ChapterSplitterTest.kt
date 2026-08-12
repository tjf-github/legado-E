package io.legado.app.help.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterSplitterTest {

    @Test
    fun splitByArabicChapterTitles() {
        val content = """
            第一章 序
            正文一
            第二章 风云
            正文二
            第三章 山雨
            正文三
        """.trimIndent()
        val units = ChapterSplitter.split(content)
        assertEquals(3, units.size)
        assertEquals("第一章 序", units[0].title)
        assertEquals("正文一", units[0].content)
        assertEquals("第二章 风云", units[1].title)
        assertEquals("正文二", units[1].content)
        assertEquals("第三章 山雨", units[2].title)
        assertEquals("正文三", units[2].content)
    }

    @Test
    fun splitByChineseChapterTitles() {
        val content = """
            第一百章
            正文一
            第一百零一章
            正文二
        """.trimIndent()
        val units = ChapterSplitter.split(content)
        assertEquals(2, units.size)
        assertEquals("第一百章", units[0].title)
        assertEquals("第一百零一章", units[1].title)
    }

    @Test
    fun splitByEnglishChapterTitles() {
        val content = """
            Chapter 1 Start
            body one
            Chapter 2 Next
            body two
        """.trimIndent()
        val units = ChapterSplitter.split(content)
        assertEquals(2, units.size)
        assertEquals("Chapter 1 Start", units[0].title)
        assertEquals("Chapter 2 Next", units[1].title)
    }

    @Test
    fun prologueMergedIntoFirstChapter() {
        val content = """
            引子文字
            第一章 序
            正文一
        """.trimIndent()
        val units = ChapterSplitter.split(content)
        assertEquals(1, units.size)
        assertEquals("第一章 序", units[0].title)
        assertEquals("引子文字\n正文一", units[0].content)
    }

    @Test
    fun noTitleReturnsEmpty() {
        assertTrue(ChapterSplitter.split("只有正文，没有标题").isEmpty())
        assertTrue(ChapterSplitter.split("").isEmpty())
        assertTrue(ChapterSplitter.split(null).isEmpty())
        assertTrue(ChapterSplitter.split("   \n  ").isEmpty())
    }

    @Test
    fun leadingWhitespaceTitleStillMatched() {
        val content = """
            　第一章 序
            正文一
              第2章 二
            正文二
        """.trimIndent()
        val units = ChapterSplitter.split(content)
        assertEquals(2, units.size)
        assertEquals("第一章 序", units[0].title)
        assertEquals("第2章 二", units[1].title)
    }
}
