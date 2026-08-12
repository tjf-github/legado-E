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

    @Test
    fun firstTitlePrependedWhenContentStartsWithBody() {
        // 模拟解析器：章节正文从标题行之后开始
        val content = """
            江湖路远，少年背着一把旧剑。
            第2回 山中怪影
            那人影一闪而逝。
            第3回 夜宿山庙
            天黑之前。
        """.trimIndent()
        val units = ChapterSplitter.split(content, "第1章 初入江湖")
        assertEquals(3, units.size)
        assertEquals("第1章 初入江湖", units[0].title)
        assertEquals("江湖路远，少年背着一把旧剑。", units[0].content)
        assertEquals("第2回 山中怪影", units[1].title)
        assertEquals("那人影一闪而逝。", units[1].content)
        assertEquals("第3回 夜宿山庙", units[2].title)
    }

    @Test
    fun firstTitleNotDuplicatedWhenContentStartsWithIt() {
        val content = """
            第1章 初入江湖
            江湖路远，少年背着一把旧剑。
            第2回 山中怪影
            那人影一闪而逝。
        """.trimIndent()
        val units = ChapterSplitter.split(content, "第1章 初入江湖")
        assertEquals(2, units.size)
        assertEquals("第1章 初入江湖", units[0].title)
        assertEquals("江湖路远，少年背着一把旧剑。", units[0].content)
        assertEquals("第2回 山中怪影", units[1].title)
    }

    @Test
    fun firstTitleWithDifferentWhitespaceNotDuplicated() {
        val content = """
            　　第1章 初入江湖
            江湖路远。
            第2回 山中怪影
            那人影一闪而逝。
        """.trimIndent()
        val units = ChapterSplitter.split(content, "第1章 初入江湖")
        assertEquals(2, units.size)
        assertEquals("第1章 初入江湖", units[0].title)
        assertEquals("江湖路远。", units[0].content)
    }

    @Test
    fun internalSpacesInTitle() {
        val content = "第 1 章 风云\n正文一\n第2 章 山雨\n正文二"
        val units = ChapterSplitter.split(content)
        assertEquals(2, units.size)
        assertEquals("第 1 章 风云", units[0].title)
        assertEquals("第2 章 山雨", units[1].title)
    }

    @Test
    fun uppercaseChineseNumerals() {
        val content = "第壹章 风云\n正文一\n第贰章 山雨\n正文二"
        val units = ChapterSplitter.split(content)
        assertEquals(2, units.size)
        assertEquals("第壹章 风云", units[0].title)
        assertEquals("第贰章 山雨", units[1].title)
    }

    @Test
    fun fullWidthDigitsAndLetters() {
        val content = "第１章 风云\n正文一\nＣｈａｐｔｅｒ ２ Next\n正文二"
        val units = ChapterSplitter.split(content)
        assertEquals(2, units.size)
        assertEquals("第１章 风云", units[0].title)
        assertEquals("Ｃｈａｐｔｅｒ ２ Next", units[1].title)
    }

    @Test
    fun extraSuffixesJuanJiHua() {
        val content = "第1卷 上\n正文一\n第2话 下\n正文二\n第一集 番外\n正文三"
        val units = ChapterSplitter.split(content)
        assertEquals(3, units.size)
        assertEquals("第1卷 上", units[0].title)
        assertEquals("第2话 下", units[1].title)
        assertEquals("第一集 番外", units[2].title)
    }

    @Test
    fun wrappedTitles() {
        val content = "【第1章 风云】\n正文一\n『第2章 山雨』\n正文二"
        val units = ChapterSplitter.split(content)
        assertEquals(2, units.size)
        assertEquals("【第1章 风云】", units[0].title)
        assertEquals("『第2章 山雨』", units[1].title)
    }

    @Test
    fun compositeVolumeTitle() {
        val content = "第一卷 第一章 风云\n正文一\n第二卷 第一章 山雨\n正文二"
        val units = ChapterSplitter.split(content)
        assertEquals(2, units.size)
        assertEquals("第一卷 第一章 风云", units[0].title)
    }

    @Test
    fun pureNumberTitleNotMatchedByDefault() {
        val content = "1、风云\n正文一\n2、山雨\n正文二"
        assertTrue(ChapterSplitter.split(content).isEmpty())
    }

    @Test
    fun firstTitleNormalizedByNumberNotDuplicated() {
        // 中文数字 vs 阿拉伯数字、简繁差异：编号相同不重复补入
        val content = """
            第一章 风雨
            江湖路远。
            第二章 山雨
            那人影一闪而逝。
        """.trimIndent()
        val units = ChapterSplitter.split(content, "第1章 風雨")
        assertEquals(2, units.size)
        assertEquals("第一章 风雨", units[0].title)
        assertEquals("第二章 山雨", units[1].title)
    }
}
