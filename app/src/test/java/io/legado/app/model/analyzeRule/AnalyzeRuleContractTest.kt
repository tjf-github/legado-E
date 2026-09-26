package io.legado.app.model.analyzeRule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AnalyzeRule JVM 契约测试（P1，§8.1 五种解析方式）。
 *
 * 纯 JVM 运行：不依赖 Robolectric、不 mock、不联网。
 * `AnalyzeRule(ruleData = null, source = null)` 即可覆盖 CSS/JSoup、XPath、JSONPath、正则与离线 JS。
 *
 * 已记录的既有语义（本次只冻结、不修正，详见 10-execution.md「既有行为缺口」）：
 * - `getString` 没有 `Mode.Regex` 分支：裸正则规则会落到 JSoup 选择器，命中不到返回空串；
 *   `getStringList` 同理，且正则模式需要 `:` 前缀才生效，而带前缀时它把整条规则当字符串返回。
 *   因此正则断言只覆盖确实生效的 `getElement`（`:` 前缀）与 `##match##replacement` 替换形态。
 * - `@webjs:` 需要真实 WebView，不在本文件覆盖范围（NOT RUN）。
 */
class AnalyzeRuleContractTest {

    private val html = """
        <html><body>
          <div id="content" class="content">
            <p>第一段</p>
            <p>第二段</p>
            <a href="/next/2.html">下一页</a>
          </div>
          <div class="meta"><span>作者</span><i>某人</i></div>
        </body></html>
    """.trimIndent()

    private val json = """
        {"code":0,"data":{"name":"书名","chapters":[{"title":"第1章"},{"title":"第2章"}]}}
    """.trimIndent()

    // ---------- CSS / JSoup ----------

    @Test
    fun cssRuleReturnsTextListInDocumentOrder() {
        val rule = AnalyzeRule().setContent(html)
        // `@text` 取整个元素的 text()，会把子元素（含链接）文本一起并入，属既有语义
        assertEquals(listOf("第一段 第二段 下一页"), rule.getStringList("class.content@text"))
    }

    @Test
    fun cssRuleJoinsMultipleMatchesWithNewline() {
        val rule = AnalyzeRule().setContent(html)
        assertEquals("第一段 第二段 下一页", rule.getString("class.content@text"))
    }

    @Test
    fun cssRuleSupportsTagChainAndAttributeExtraction() {
        val rule = AnalyzeRule().setContent(html)
        assertEquals("/next/2.html", rule.getString("tag.a@href"))
    }

    @Test
    fun cssRuleSupportsExplicitCssHeader() {
        val rule = AnalyzeRule().setContent(html)
        assertEquals("作者", rule.getString("@CSS:.meta span@text"))
    }

    @Test
    fun cssRuleTextNodesSeesOnlyDirectTextChildren() {
        // `@textNodes` 走的是 jsoup `element.textNodes()`，即**直系**子文本节点。
        // 这里的夹具文本全在 <p> 子元素里，直系文本节点只有空白，所以返回空列表是实现的正确行为，
        // 不是产品缺口（首轮报告把它写成「既有缺口」，已在返工轮按 L-2 更正）。
        val nested = AnalyzeRule().setContent(
            "<div class=\"content\"><p>第一段</p><p>第二段</p></div>"
        )
        assertEquals(emptyList<String>(), nested.getStringList("class.content@textNodes"))
        assertEquals("第一段 第二段", nested.getString("class.content@text"))

        // 直系文本节点的正例：文本直接挂在 .content 下时 @textNodes 取得到，
        // 且同一元素的多个文本节点用 \n 拼接为一条结果（不是拆成多条）。
        val direct = AnalyzeRule().setContent(
            "<div class=\"content\">第一段<br>第二段</div>"
        )
        assertEquals(listOf("第一段\n第二段"), direct.getStringList("class.content@textNodes"))
        assertEquals("第一段\n第二段", direct.getString("class.content@textNodes"))
    }

    @Test
    fun zeroHitCssRuleReturnsEmptyListAndEmptyStringNotOriginalContent() {
        val rule = AnalyzeRule().setContent(html)
        // 零命中返回空列表（不是 null），文本形式为空串——不得回退成整页原文
        assertEquals(emptyList<String>(), rule.getStringList("class.not-exists@text"))
        assertEquals("", rule.getString("class.not-exists@text"))
    }

    @Test
    fun cssRuleWithReplaceSupport() {
        val rule = AnalyzeRule().setContent(html)
        // `##match##replacement`：先取文本，再把换行替换成 `|`
        assertEquals("第一段 第二段 下一页", rule.getString("class.content@text##\\n##|"))
    }

    @Test
    fun cssRuleReplaceCanInsertPrefix() {
        val rule = AnalyzeRule().setContent("<div class=\"content\"><p>第一段 第二段</p></div>")
        assertEquals("[一] 第二段", rule.getString("class.content@text##第一段##[一]"))
    }

    // ---------- XPath ----------

    @Test
    fun xpathRuleExtractsText() {
        val rule = AnalyzeRule().setContent(html)
        assertEquals("第一段", rule.getString("@XPath://div[@id='content']/p[1]/text()"))
    }

    @Test
    fun xpathRuleReturnsElementOuterHtmlForElementNodes() {
        val rule = AnalyzeRule().setContent(html)
        // 选中元素节点时返回节点外层 HTML（既有语义，不是纯文本）
        assertEquals(
            listOf("<p>第一段</p>", "<p>第二段</p>"),
            rule.getStringList("@XPath://div[@id='content']/p")
        )
    }

    @Test
    fun xpathTextNodesReturnPlainText() {
        val rule = AnalyzeRule().setContent(html)
        assertEquals(
            listOf("第一段", "第二段"),
            rule.getStringList("@XPath://div[@id='content']/p/text()")
        )
    }

    @Test
    fun leadingSlashIsTreatedAsXPathWithoutHeader() {
        val rule = AnalyzeRule().setContent(html)
        assertEquals("第二段", rule.getString("//div[@id='content']/p[2]/text()"))
    }

    // ---------- JSONPath ----------

    @Test
    fun jsonRuleReadsNestedString() {
        val rule = AnalyzeRule().setContent(json)
        assertEquals("书名", rule.getString("$.data.name"))
    }

    @Test
    fun jsonRuleReadsArrayItems() {
        val rule = AnalyzeRule().setContent(json)
        assertEquals(
            listOf("第1章", "第2章"),
            rule.getStringList("$.data.chapters[*].title")
        )
    }

    @Test
    fun jsonRuleWithExplicitHeader() {
        val rule = AnalyzeRule().setContent(json)
        assertEquals("书名", rule.getString("@Json:$.data.name"))
    }

    @Test
    fun jsonRuleZeroHitReturnsEmpty() {
        val rule = AnalyzeRule().setContent(json)
        assertEquals("", rule.getString("$.data.missing"))
    }

    // ---------- 正则 ----------

    @Test
    fun regexElementWithColonPrefixReturnsFullMatchAndGroups() {
        val rule = AnalyzeRule().setContent("正文开始<123>正文结束")
        // `:` 前缀是正则模式开关；返回 [整段匹配, 捕获组...]
        assertEquals(listOf("123", "123"), rule.getElement(":(\\d+)"))
    }

    @Test
    fun regexElementKeepsFullMatchWhenGroupDiffers() {
        val rule = AnalyzeRule().setContent("正文开始<123>abc")
        assertEquals(listOf("<123>", "123"), rule.getElement(":<(\\d+)>"))
    }

    @Test
    fun regexElementZeroHitReturnsNull() {
        val rule = AnalyzeRule().setContent("无数字")
        assertNull(rule.getElement(":(\\d+)"))
    }

    @Test
    fun getStringWithoutColonPrefixDoesNotRunRegex() {
        // 已记录缺口：`getString` 没有 Mode.Regex 分支，裸正则会落到 CSS 选择器并返回空串，
        // 因此纯正则取值必须带 `:` 前缀并走 getElement。
        val rule = AnalyzeRule().setContent("正文开始<123>正文结束")
        assertEquals("", rule.getString("<(\\d+)>"))
        assertEquals("", rule.getString("<\\d+>"))
    }

    // ---------- 离线 JS ----------

    @Test
    fun jsRuleRunsOfflineOnResult() {
        val rule = AnalyzeRule().setContent("abcd")
        assertEquals("4", rule.getString("@js:String(result.length)"))
    }

    @Test
    fun jsRuleCanTransformHtmlResult() {
        val rule = AnalyzeRule().setContent("<div class=\"content\"><p>ab</p></div>")
        assertEquals("ab", rule.getString("class.content@text@js:result"))
    }

    @Test
    fun jsRuleReceivesBaseUrlAndContentBindings() {
        val rule = AnalyzeRule().setContent("<div>abc</div>", "https://example.com/book")
        assertEquals(
            "https://example.com/book|14",
            rule.getString("@js:baseUrl + '|' + String(src.length)")
        )
    }

    // ---------- 边界 ----------

    @Test
    fun emptyRuleReturnsEmptyString() {
        val rule = AnalyzeRule().setContent(html)
        assertEquals("", rule.getString(""))
        assertNull(rule.getStringList(null))
    }

    @Test
    fun nullContentThrowsAssertionError() {
        var thrown: Throwable? = null
        try {
            AnalyzeRule().setContent(null)
        } catch (e: AssertionError) {
            thrown = e
        }
        assertTrue("setContent(null) 必须显式报错而不是静默", thrown is AssertionError)
    }

    @Test
    fun isUrlResolvesRelativeLinkAgainstRedirectUrl() {
        val rule = AnalyzeRule().setContent(html)
        rule.setRedirectUrl("https://example.com/book/1/index.html")
        assertEquals(
            listOf("https://example.com/next/2.html"),
            rule.getStringList("tag.a@href", isUrl = true)
        )
    }

    @Test
    fun setRedirectUrlRejectsDataUrl() {
        // data: URL 不参与相对地址补全，setRedirectUrl 返回原值（null）
        val rule = AnalyzeRule().setContent(html)
        assertNull(rule.setRedirectUrl("data:text/plain;base64,AAAA"))
    }
}
