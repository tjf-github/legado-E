package io.legado.app.help.book

import io.legado.app.data.entities.ReplaceRule
import io.legado.app.help.book.diagnostics.ContentLineNormalizer
import io.legado.app.model.webBook.BookContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ContentProcessor / BookContent JVM 契约测试（P1）。
 *
 * 证据强度声明（重要，不要读成比实际更强）：
 * - [ContentProcessor] 本身依赖 `appDb`（Room/SQLite）、`Build.VERSION`、`appCtx`（Android Context），
 *   本仓库没有 Robolectric 也没有 mock 框架，JVM 单测无法实例化它，因此
 *   **没有** 直接调用 `ContentProcessor.getContent`；
 * - 这里冻结的是它尾部段落规范化的**同一份**逻辑（已抽成
 *   [ContentLineNormalizer]，实现逐行等价），以及它依赖的 [BookContent] 数据结构；
 * - 全链路一致性只能靠真机抽查确认，未执行项在执行报告里写 NOT RUN。
 */
class ContentProcessorContractTest {

    private val indent = "　　"

    // ---------- 段落规范化（ContentProcessor.getContent 尾部逻辑） ----------

    @Test
    fun normalizeDropsBlankLinesAndIndentsBody() {
        val content = "标题\n\n　第一段　\n\n\n第二段\n   \n"
        assertEquals(
            listOf("标题", "${indent}第一段", "${indent}第二段"),
            ContentLineNormalizer.normalize(content, includeTitle = true, paragraphIndent = indent)
        )
    }

    @Test
    fun normalizeWithoutTitleIndentsFirstParagraphToo() {
        assertEquals(
            listOf("${indent}标题", "${indent}正文"),
            ContentLineNormalizer.normalize("标题\n正文", includeTitle = false, paragraphIndent = indent)
        )
    }

    @Test
    fun normalizeTrimsOnlyControlAndIdeographicSpace() {
        // 只去掉 code <= 0x20 与全角空格 U+3000，不动普通空格之外的可见标点
        val content = "\u0001\u001f　首行　\n\t第二行\t"
        assertEquals(
            listOf("首行", "${indent}第二行"),
            ContentLineNormalizer.normalize(content, includeTitle = true, paragraphIndent = indent)
        )
    }

    @Test
    fun normalizeKeepsPunctuationAndInnerSpaces() {
        val content = "他说：“你好，世界！”\n a  b "
        assertEquals(
            listOf("他说：“你好，世界！”", "${indent}a  b"),
            ContentLineNormalizer.normalize(content, includeTitle = true, paragraphIndent = indent)
        )
    }

    @Test
    fun normalizeDropsPoetryBlankLinesButKeepsEveryNonBlankLine() {
        val poetry = "床前明月光\n\n疑是地上霜\n\n举头望明月\n\n低头思故乡"
        val result = ContentLineNormalizer.normalize(poetry, includeTitle = true, paragraphIndent = indent)
        assertEquals(4, result.size)
        assertEquals("床前明月光", result[0])
        assertEquals("${indent}低头思故乡", result[3])
    }

    @Test
    fun normalizeKeepsImageLinesIntact() {
        val content = "<img src=\"a.png\">\n<img src=\"b.png\">"
        val result = ContentLineNormalizer.normalize(content, includeTitle = true, paragraphIndent = indent)
        assertEquals(listOf("<img src=\"a.png\">", "${indent}<img src=\"b.png\">"), result)
    }

    @Test
    fun normalizeEmptyContentProducesEmptyList() {
        assertTrue(ContentLineNormalizer.normalize("", includeTitle = true, paragraphIndent = indent).isEmpty())
        assertTrue(ContentLineNormalizer.normalize(" \n\t\n　", includeTitle = true, paragraphIndent = indent).isEmpty())
    }

    @Test
    fun normalizeNeverInventsParagraphs() {
        // 反例守卫：规范化只能删空行、加缩进，不得把一行拆成两行或合并两行
        val content = "第一行\n第二行"
        val result = ContentLineNormalizer.normalize(content, includeTitle = true, paragraphIndent = indent)
        assertEquals(2, result.size)
        result.forEachIndexed { index, line ->
            assertTrue("第 $index 行被改写: $line", line.endsWith(content.split("\n")[index]))
        }
    }

    // ---------- 替换阶段的「逐行 trim 后还原」 ----------

    @Test
    fun replaceStageLineTrimNormalizationIsStable() {
        // BookContent 替换阶段：split("\n").joinToString("\n") { it.trim() }
        val lines = listOf("  第一行  ", "\t第二行", "第三行 ")
        val normalized = lines.joinToString("\n") { it.trim() }
        assertEquals("第一行\n第二行\n第三行", normalized)
        // 幂等：再跑一次结果不变
        assertEquals(normalized, normalized.split("\n").joinToString("\n") { it.trim() })
    }

    @Test
    fun replaceStageTrimDoesNotTouchInnerText() {
        val original = "  中间 有 空格  和, 标点。  "
        assertEquals("中间 有 空格  和, 标点。", original.trim())
    }

    // ---------- BookContent 数据结构 ----------

    @Test
    fun bookContentCarriesSameTitleFlagTextListAndEffectiveRules() {
        val rule = ReplaceRule(id = 1, name = "广告", pattern = "广告")
        val content = BookContent(true, listOf("标题", "${indent}正文"), arrayListOf(rule))
        assertTrue(content.sameTitleRemoved)
        assertEquals(listOf("标题", "${indent}正文"), content.textList)
        assertEquals(1, content.effectiveReplaceRules?.size)
        assertSame(rule, content.effectiveReplaceRules?.first())
    }

    @Test
    fun bookContentAllowsNullEffectiveRulesWhenReplaceDisabled() {
        val content = BookContent(false, listOf("正文"), null)
        assertFalse(content.sameTitleRemoved)
        assertNull(content.effectiveReplaceRules)
    }

    @Test
    fun bookContentCopyKeepsContentsEquivalent() {
        // data class copy 是阅读页分页前的常见操作，必须保持内容等价
        val content = BookContent(false, listOf("a", "b"), null)
        val copy = content.copy(sameTitleRemoved = true)
        assertEquals(content.textList, copy.textList)
        assertTrue(copy.sameTitleRemoved)
    }

    @Test
    fun bookContentToStringJoinsTextListWithNewline() {
        // toString() 被导出/调试链路直接用作文本，冻结其拼接语义
        assertEquals("a\nb", BookContent(false, listOf("a", "b"), null).toString())
    }
}
