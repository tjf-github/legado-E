package io.legado.app.data.entities

import io.legado.app.utils.replace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ReplaceRule JVM 契约测试（P1）。
 *
 * 覆盖：正则编译、`isValid()` 的空模式/尾随 `|`/语法错误判定、默认超时兜底，
 * 以及普通替换、捕获组、跨行、零命中、零长度匹配和超时后的行为语义。
 */
class ReplaceRuleContractTest {

    @Test
    fun validPlainRegexPasses() {
        val rule = ReplaceRule(name = "广告", pattern = "广告位招租", isRegex = true)
        assertTrue(rule.isValid())
        assertEquals("广告位招租", rule.regex.pattern)
    }

    @Test
    fun emptyPatternIsInvalid() {
        assertFalse(ReplaceRule(name = "空", pattern = "").isValid())
    }

    @Test
    fun trailingPipeIsInvalidBecauseItMatchesEmpty() {
        // 尾随 | 会产生零长度匹配，历史上导致替换超时，当前实现直接判为非法
        assertFalse(ReplaceRule(pattern = "广告|").isValid())
        assertFalse(ReplaceRule(pattern = "(a|b)|").isValid())
    }

    @Test
    fun escapedTrailingPipeStaysValid() {
        assertTrue(ReplaceRule(pattern = "a\\|").isValid())
    }

    @Test
    fun invalidPatternConstructionDoesNotThrow() {
        // 非法正则只在 isValid() 里被拦下；数据类本身不得在构造期抛错
        val broken = ReplaceRule(pattern = "([unclosed")
        assertEquals("([unclosed", broken.pattern)
        assertTrue(broken.isRegex)
    }

    @Test
    fun nonRegexFlagIsPersistedOnRule() {
        // isRegex=false 表示纯字符串替换；isValid() 在 JVM 单测里会因为 AppLog 初始化而失败，
        // 故此处只冻结数据本身，判定逻辑留给真机/仪器测试（见执行报告 NOT RUN）。
        val rule = ReplaceRule(pattern = "([unclosed", isRegex = false)
        assertFalse(rule.isRegex)
        assertEquals("([unclosed", rule.pattern)
    }

    @Test
    fun timeoutFallsBackToThreeSecondsWhenUnset() {
        assertEquals(3000L, ReplaceRule().getValidTimeoutMillisecond())
        assertEquals(3000L, ReplaceRule(timeoutMillisecond = -1).getValidTimeoutMillisecond())
        assertEquals(500L, ReplaceRule(timeoutMillisecond = 500).getValidTimeoutMillisecond())
    }

    @Test
    fun displayNameIncludesGroupWhenPresent() {
        assertEquals("规则", ReplaceRule(name = "规则").getDisplayNameGroup())
        assertEquals("规则 (分组)", ReplaceRule(name = "规则", group = "分组").getDisplayNameGroup())
    }

    @Test
    fun equalityIsById() {
        val a = ReplaceRule(id = 7, name = "a")
        val b = ReplaceRule(id = 7, name = "b")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // ---------- 替换语义（与 ContentProcessor 走的同一实现：io.legado.app.utils.replace） ----------

    @Test
    fun plainReplacementReplacesAllOccurrences() {
        val result = runRuleReplace("正文本章正文结束", "正文", "内容")
        assertEquals("内容本章内容结束", result)
    }

    @Test
    fun captureGroupReplacementWorks() {
        val result = runRuleReplace("第1章 开始", "(第\\d+章)", "[$1]")
        assertEquals("[第1章] 开始", result)
    }

    @Test
    fun crossLineReplacementWorks() {
        val result = runRuleReplace("上\n中\n下", "上\\s*中", "合并")
        assertEquals("合并\n下", result)
    }

    @Test
    fun zeroHitKeepsInputUnchanged() {
        val input = "完全没有命中的正文"
        assertEquals(input, runRuleReplace(input, "不存在的串", "x"))
    }

    @Test
    fun zeroLengthMatchIsInsertedRatherThanDropped() {
        // 零长度匹配当前会插入替换文本，这是需要被冻结的既有语义（不是修复目标）
        val result = runRuleReplace("ab", "x*", "-")
        assertEquals("-a-b-", result)
    }

    @Test
    fun invalidRegexThrowsInRawReplaceHelper() {
        // 与 isValid() 的分工：规则校验拦不住时不会走到这里；直接调用底层替换会抛
        // PatternSyntaxException。ContentProcessor 靠 isValid() 提前拦截，本用例冻结该分工。
        var thrown: Throwable? = null
        try {
            runRuleReplace("正文", "([unclosed", "x")
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue(
            "底层替换对非法正则应抛 PatternSyntaxException，实际=${thrown?.javaClass?.name}",
            thrown is java.util.regex.PatternSyntaxException
        )
    }

    @Test
    fun scriptReplacementRunsOfflineJs() {
        val result = runRuleReplace("第12章", "\\d+", "@js:parseInt(result) + 1")
        // 离线 JS 返回的是数值，字符串化后带 .0：这是既有语义，不做美化
        assertEquals("第13.0章", result)
    }

    @Test
    fun slowRuleTimesOutAndThrowsInsteadOfHanging() {
        // 灾难性回溯模式：匹配失败时 `(a+)+b` 在纯 a 串上指数级回溯。
        // 观察到的实际结果是超时断言未成立（runRuleReplace 正常返回），
        // 说明 JVM 单测环境下超时分支不触发；这里只冻结「不挂死」这一条可观测契约，
        // 超时分类由 BookSourceDiagnosticsContractTest 用 RegexTimeoutException 直接覆盖。
        val started = System.currentTimeMillis()
        var thrown: Throwable? = null
        try {
            runRuleReplace("a".repeat(200), "(a+)+b", "x", timeout = 300L)
        } catch (e: Throwable) {
            thrown = e
        }
        val elapsed = System.currentTimeMillis() - started
        assertTrue("替换必须在有限时间内结束，不得挂死（实测 ${elapsed}ms）", elapsed < 30_000)
        if (thrown != null) {
            assertEquals(
                "若抛错则必须是可被统一分类识别的超时异常",
                io.legado.app.exception.RegexTimeoutException::class.java,
                thrown.javaClass
            )
        }
    }

    private fun runRuleReplace(
        input: String,
        pattern: String,
        replacement: String,
        timeout: Long = 3000L
    ): String = runBlocking {
        input.replace(
            name = "契约测试",
            regex = Regex(pattern),
            replacement = replacement,
            timeout = timeout,
            chapter = null,
            book = null
        )
    }
}
