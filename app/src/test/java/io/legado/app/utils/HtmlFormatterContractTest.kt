package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HtmlFormatter JVM 契约测试（P1）。
 *
 * 冻结现有行为，不修正正文语义：本文件只断言**当前真实输出**（由探针从编译产物采集，
 * 见 10-execution.md 的命令证据），任何看起来奇怪的地方都写进执行报告，而不是顺手改实现。
 *
 * 特别记录一处既有行为（不是本任务修复目标）：
 * `indent1Regex`（`\s*\n+\s*` -> `\n　　`）之后紧接 `indent2Regex`（`^[\n\s]+` -> `　　`），
 * 因此**首行会得到 4 个全角空格，后续行 2 个**；HTML 源里的换行位置还会留下
 * 「换行 + 2 个全角空格」的段内尾巴。契约测试如实冻结该输出。
 */
class HtmlFormatterContractTest {

    private val base = java.net.URL("https://example.com/book/1/index.html")

    /** 首行 4 个全角空格、换行后 2 个全角空格（见类注释）。 */
    private val i4 = "\u3000\u3000\u3000\u3000"
    private val i2 = "\u3000\u3000"

    // ---------- format ----------

    @Test
    fun formatCollapsesWrapTagsIntoOneBoundaryPerTag() {
        val html = "<div>第一段</div><p>第二段</p><br>第三段"
        assertEquals("${i4}第一段\n${i2}第二段\n${i2}第三段", HtmlFormatter.format(html))
    }

    @Test
    fun formatStripsNonImageTagsAndComments() {
        val html = "<div><span>正文</span><!-- 广告 --><script>x()</script></div>"
        // script 内容属于文本节点，只清标签不清内容
        assertEquals("${i4}正文x()\n${i2}", HtmlFormatter.format(html))
    }

    @Test
    fun formatNormalizesNbspEnspEmspAndDropsZeroWidth() {
        val html = "a&nbsp;&nbsp;b&ensp;c&emsp;d&thinsp;e&zwnj;f\u2009g\u200C h\u200D"
        // &nbsp; / &ensp; / &emsp; 各自折成一个半角空格，零宽字符被删除
        assertEquals("a b c defg h", HtmlFormatter.format(html))
    }

    @Test
    fun formatProducesParagraphIndent() {
        val html = "\n\n   <div>只有一段</div>\n\n  "
        assertEquals("${i4}只有一段\n${i2}", HtmlFormatter.format(html))
    }

    @Test
    fun formatKeepsChineseAndEnglishPunctuationUntouched() {
        val text = "<div>他说：“你好，世界！”</div><p>a.b, c; d: e? f! g—h…i</p>"
        val formatted = HtmlFormatter.format(text)
        assertTrue(formatted, formatted.contains("“你好，世界！”"))
        assertTrue(formatted, formatted.contains("a.b, c; d: e? f! g—h…i"))
        assertFalse(formatted.contains("<div>"))
        assertFalse(formatted.contains("<p>"))
    }

    @Test
    fun formatDoesNotIntroduceBlankLinesBetweenPoetryLines() {
        val html = "<div>床前明月光</div><div><br></div><div>疑是地上霜</div>"
        val formatted = HtmlFormatter.format(html)
        assertTrue(formatted, formatted.contains("床前明月光"))
        assertTrue(formatted, formatted.contains("疑是地上霜"))
        // 每个块标签只产生一个边界，不允许出现空行
        assertFalse(formatted, formatted.contains("\n\n"))
    }

    @Test
    fun formatReturnsEmptyForNull() {
        assertEquals("", HtmlFormatter.format(null))
    }

    // ---------- formatKeepImg ----------

    @Test
    fun formatKeepImgKeepsImgAndResolvesRelativeUrl() {
        val html = "<div>文字</div><img src=\"../images/a.png\"><div>后续</div>"
        val result = HtmlFormatter.formatKeepImg(html, base)
        assertTrue(result, result.contains("<img src=\"https://example.com/book/images/a.png\">"))
        assertTrue(result.contains("文字"))
        assertTrue(result.contains("后续"))
    }

    @Test
    fun formatKeepImgResolvesDataSrcAndDataOriginal() {
        val html = "<div>x</div><img data-src=\"/lazy/1.jpg\"><img data-original=\"2.jpg\">"
        val result = HtmlFormatter.formatKeepImg(html, base)
        assertTrue(result, result.contains("<img src=\"https://example.com/lazy/1.jpg\">"))
        assertTrue(result, result.contains("<img src=\"https://example.com/book/1/2.jpg\">"))
    }

    @Test
    fun formatKeepImgKeepsCommaAndQueryInUrl() {
        // 图片 URL 自带逗号与查询串：不得被截断
        val html = "<div>x</div><img src=\"/img/a,b.png?w=100&amp;h=200\">"
        val result = HtmlFormatter.formatKeepImg(html, base)
        assertTrue(result, result.contains("/img/a,b.png?w=100&amp;h=200"))
    }

    @Test
    fun formatKeepImgKeepsAdjacentImages() {
        val html = "<img src=\"1.png\"><img src=\"2.png\"><img src=\"3.png\">"
        val result = HtmlFormatter.formatKeepImg(html, base)
        assertEquals(3, Regex("<img ").findAll(result).count())
    }

    @Test
    fun formatKeepImgKeepsAbsoluteAndDataUrlsUnchanged() {
        val absolute = "<img src=\"https://cdn.other.com/x.png\">"
        assertEquals(absolute, HtmlFormatter.formatKeepImg(absolute, base))
        val dataUrl = "<img src=\"data:image/png;base64,AAAA\">"
        assertEquals(dataUrl, HtmlFormatter.formatKeepImg(dataUrl, base))
    }

    @Test
    fun formatKeepImgWithoutBaseUrlKeepsRelativePath() {
        val html = "<img src=\"images/a.png\">"
        assertEquals(html, HtmlFormatter.formatKeepImg(html, null))
    }

    @Test
    fun formatKeepImgStripsUseHtmlTagsButKeepsInnerText() {
        // usehtml 片段本身不是 img，会被当普通标签清掉；保护职责在 BookContent 的占位符阶段
        val html = "<usehtml><ruby>汉<rt>han</rt></ruby></usehtml>"
        val result = HtmlFormatter.formatKeepImg(html, base)
        assertFalse(result, result.contains("<usehtml>"))
        assertTrue(result, result.contains("汉han"))
    }

    @Test
    fun formatKeepImgOnNullReturnsEmpty() {
        assertEquals("", HtmlFormatter.formatKeepImg(null, base))
    }

    @Test
    fun formatKeepImgKeepsPictureNewlineBoundary() {
        // 连续多图之间的换行边界必须保留，供阅读页识别「纯图片书」
        val html = "<div><img src=\"1.png\"></div><div><img src=\"2.png\"></div>"
        val result = HtmlFormatter.formatKeepImg(html, base)
        assertTrue(result, result.contains("https://example.com/book/1/1.png"))
        assertTrue(result, result.contains("https://example.com/book/1/2.png"))
        assertTrue(result, result.contains("\n"))
    }
}
