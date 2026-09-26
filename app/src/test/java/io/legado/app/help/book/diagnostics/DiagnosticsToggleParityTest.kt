package io.legado.app.help.book.diagnostics

import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.utils.HtmlFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 诊断开关前后「正文产出等价」差分测试（P1 返工项 B-5）。
 *
 * 目的：证明打开诊断不会改变任何阶段的产出，关闭诊断也不会因为少记一条而改变产出。
 * 做法是把生产链路（[AnalyzeRule] 规则提取 → [HtmlFormatter.formatKeepImg] HTML 格式化 →
 * [ContentLineNormalizer] 段落规范化，后者正是 ContentProcessor 使用的那份实现）
 * 分别以「有诊断会话」和「无诊断会话」跑两遍，逐字节比较三段产出。
 *
 * 证据强度声明（不要读成比实际更强）：
 * - 三段都调用真实生产代码，**不是**复制实现；
 * - 但 [io.legado.app.help.book.ContentProcessor] 与 [io.legado.app.model.webBook.BookContent]
 *   本身依赖 appDb/appCtx/Build.VERSION，无 Robolectric 无法在 JVM 实例化，因此这里**没有**调用它们；
 *   二者与诊断的全部耦合就是「读一次 collector、写一条快照」，不参与返回值计算；
 * - 真机端到端一致性仍为 NOT RUN，见本轮执行报告。
 */
class DiagnosticsToggleParityTest {

    private val baseUrl = "https://example.com/book/1/index.html"
    private val indent = "　　"

    private val html = """
        <html><body>
          <div class="content">
            <p>第一段正文</p>
            <p>第二段 带 空格</p>
            <img src="../images/a.png">
            <usehtml><ruby>汉<rt>han</rt></ruby></usehtml>
          </div>
          <a href="/next/2.html">下一页</a>
        </body></html>
    """.trimIndent()

    private val json = """{"data":{"content":"JSON 正文第一段\n第二段","name":"书名"}}"""

    /** 三段产出：规则提取后 / HTML 格式化后 / 段落规范化后。 */
    private data class Output(
        val extracted: String,
        val formatted: String,
        val normalized: List<String>
    ) {
        fun typed(): String =
            extracted + "\u0000" + formatted + "\u0000" + normalized.joinToString("\n")
    }

    private fun runPipeline(
        collector: BookSourceDiagnosticsCollector?,
        content: String,
        rule: String
    ): Output {
        val analyzeRule = AnalyzeRule().setContent(content)
        analyzeRule.setRedirectUrl(baseUrl)
        val extracted = analyzeRule.getString(rule, unescape = false)
        collector?.record(DiagnosticStage.EXTRACT, extracted, 1L)
        val formatted = HtmlFormatter.formatKeepImg(extracted, java.net.URL(baseUrl))
        collector?.record(DiagnosticStage.FORMAT, formatted, 2L)
        val normalized = ContentLineNormalizer.normalize(
            content = formatted,
            includeTitle = true,
            paragraphIndent = indent
        )
        collector?.record(DiagnosticStage.READ_PROCESS, normalized.joinToString("\n"), 3L)
        return Output(extracted, formatted, normalized)
    }

    private fun assertParity(content: String, rule: String) {
        val off = runPipeline(null, content, rule)
        val collector = BookSourceDiagnosticsCollector().apply {
            bindTarget(baseUrl, baseUrl, baseUrl)
        }
        val on = runPipeline(collector, content, rule)
        assertEquals("诊断开启前后提取结果必须逐字节相同（规则：$rule）", off.extracted, on.extracted)
        assertEquals("诊断开启前后格式化结果必须逐字节相同（规则：$rule）", off.formatted, on.formatted)
        assertEquals("诊断开启前后段落结果必须逐字节相同（规则：$rule）", off.normalized, on.normalized)
        assertEquals(
            "诊断会话必须真的在工作，否则这条等价断言是空跑",
            3,
            collector.summaryLines().size
        )
        assertEquals("三段产出拼接后也必须逐字节相同（规则：$rule）", off.typed(), on.typed())
    }

    @Test
    fun cssExtractionIsIdenticalWithAndWithoutDiagnostics() {
        assertParity(html, "class.content@text")
    }

    @Test
    fun xpathExtractionIsIdenticalWithAndWithoutDiagnostics() {
        assertParity(html, "@XPath://div[@class='content']/p/text()")
    }

    @Test
    fun jsonPathExtractionIsIdenticalWithAndWithoutDiagnostics() {
        assertParity(json, "$.data.content")
    }

    @Test
    fun replaceRuleExtractionIsIdenticalWithAndWithoutDiagnostics() {
        assertParity(html, "class.content@text##第一段##[一]")
    }

    @Test
    fun offlineJsRuleExtractionIsIdenticalWithAndWithoutDiagnostics() {
        assertParity(html, "class.content@text@js:result.trim()")
    }

    @Test
    fun zeroHitRuleStaysEmptyWithAndWithoutDiagnostics() {
        assertParity(html, "class.not-exists@text")
    }

    @Test
    fun disabledCollectorDoesNotChangeOutputEither() {
        // 「显式传入关闭的 collector」与「完全不传」也必须等价
        val disabled = BookSourceDiagnosticsCollector(isEnabled = false).apply {
            bindTarget(baseUrl, baseUrl, baseUrl)
        }
        val off = runPipeline(null, html, "class.content@text")
        val withDisabled = runPipeline(disabled, html, "class.content@text")
        assertEquals(off.typed(), withDisabled.typed())
        assertTrue(disabled.summaryLines().isEmpty())
    }
}
