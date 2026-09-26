package io.legado.app.help.book.diagnostics

import io.legado.app.exception.ContentEmptyException
import io.legado.app.exception.RegexTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.PatternSyntaxException

/**
 * 四阶段诊断组件 JVM 契约测试（P1）：阶段语义、错误分类、默认脱敏、
 * 会话身份隔离与开关关闭时的零副作用。
 */
class BookSourceDiagnosticsContractTest {

    private val secretUrl =
        "https://user:p4ssw0rd@example.com/book/1?token=abc123&sign=deadbeef#frag"
    private val cookie = "Cookie: sessionid=abcdef123456; uid=42"

    private val bookSourceUrl = "https://source.example.com"
    private val bookUrl = "https://source.example.com/book/1"
    private val chapterUrl = "https://source.example.com/book/1/2.html"

    private fun boundCollector(): BookSourceDiagnosticsCollector =
        BookSourceDiagnosticsCollector().apply {
            bindTarget(bookSourceUrl, bookUrl, chapterUrl)
        }

    // ---------- 阶段 ----------

    @Test
    fun stageOrderMatchesRealCallChain() {
        assertEquals(listOf(1, 2, 3, 4), DiagnosticStage.values().map { it.order })
        assertEquals(
            listOf(
                DiagnosticStage.EXTRACT,
                DiagnosticStage.FORMAT,
                DiagnosticStage.REPLACE,
                DiagnosticStage.READ_PROCESS
            ),
            DiagnosticStage.values().toList()
        )
    }

    @Test
    fun stageLabelsAreStableForDebugOutput() {
        assertEquals("提取后", DiagnosticStage.EXTRACT.label)
        assertEquals("HTML格式化后", DiagnosticStage.FORMAT.label)
        assertEquals("书源替换后", DiagnosticStage.REPLACE.label)
        assertEquals("阅读处理后", DiagnosticStage.READ_PROCESS.label)
    }

    // ---------- 错误分类 ----------

    @Test
    fun regexTimeoutIsClassifiedAsReplaceTimeout() {
        assertEquals(
            DiagnosticErrorClass.REPLACE_TIMEOUT,
            DiagnosticClassifier.classify(RegexTimeoutException("替换超时"), DiagnosticStage.REPLACE)
        )
    }

    @Test
    fun contentEmptyIsClassifiedAsEmptyContent() {
        assertEquals(
            DiagnosticErrorClass.EMPTY_CONTENT,
            DiagnosticClassifier.classify(ContentEmptyException("内容为空"))
        )
    }

    @Test
    fun socketTimeoutIsClassifiedAsNetwork() {
        assertEquals(
            DiagnosticErrorClass.NETWORK,
            DiagnosticClassifier.classify(SocketTimeoutException("connect timed out"))
        )
    }

    @Test
    fun encodingErrorIsClassifiedAsEncoding() {
        assertEquals(
            DiagnosticErrorClass.ENCODING,
            DiagnosticClassifier.classifyByMessage("java.nio.charset.MalformedInputException")
        )
    }

    @Test
    fun ruleSyntaxErrorIsClassifiedAsRuleSyntax() {
        // 只按类型/标记判定，绝不解析异常消息里的正则片段（那正是需要脱敏的东西）
        val error = PatternSyntaxException("Unclosed group", "([unclosed", 0)
        assertEquals(
            "PatternSyntaxException 类型本身即为规则语法错误",
            DiagnosticErrorClass.RULE_SYNTAX,
            DiagnosticClassifier.classify(error)
        )
        assertEquals(
            DiagnosticErrorClass.RULE_SYNTAX,
            DiagnosticClassifier.classifyByMessage("Unexpected token in JSONPath: $.[")
        )
        assertEquals(
            DiagnosticErrorClass.RULE_SYNTAX,
            DiagnosticClassifier.classifyByMessage("Unknown hosting: /html/body/div")
        )
    }

    @Test
    fun jsRuleSyntaxMarkerIsNotDerivedFromUnrelatedText() {
        // 反例守卫：普通正文/错误说明里出现 "syntax" 之外的词不应被误判
        assertEquals(
            DiagnosticErrorClass.NONE,
            DiagnosticClassifier.classifyByMessage("")
        )
        assertEquals(
            DiagnosticErrorClass.UNKNOWN,
            DiagnosticClassifier.classifyByMessage("章节标题解析失败")
        )
    }

    @Test
    fun nextPageLoopAndImageUrlAndHttpAreDistinguished() {
        assertEquals(
            DiagnosticErrorClass.NEXT_PAGE_LOOP,
            DiagnosticClassifier.classifyByMessage("检测到下一页循环")
        )
        assertEquals(
            DiagnosticErrorClass.EMPTY_IMAGE_URL,
            DiagnosticClassifier.classifyByMessage("图片URL为空")
        )
        assertEquals(
            DiagnosticErrorClass.HTTP,
            DiagnosticClassifier.classifyByMessage("HTTP 404 Not Found")
        )
        assertEquals(
            DiagnosticErrorClass.EMPTY_MATCH,
            DiagnosticClassifier.classifyByMessage("规则零命中")
        )
    }

    @Test
    fun unknownMessageFallsBackToUnknownNotToNone() {
        assertEquals(
            DiagnosticErrorClass.UNKNOWN,
            DiagnosticClassifier.classifyByMessage("某种没见过的失败")
        )
        assertEquals(DiagnosticErrorClass.NONE, DiagnosticClassifier.classify(null))
        assertEquals(DiagnosticErrorClass.NONE, DiagnosticClassifier.classifyByMessage(""))
    }

    @Test
    fun emptyOutputIsClassifiedByStage() {
        assertEquals(
            DiagnosticErrorClass.EMPTY_MATCH,
            DiagnosticClassifier.classifyOutput("", DiagnosticStage.EXTRACT)
        )
        assertEquals(
            DiagnosticErrorClass.EMPTY_CONTENT,
            DiagnosticClassifier.classifyOutput("", DiagnosticStage.READ_PROCESS)
        )
        assertEquals(
            DiagnosticErrorClass.NONE,
            DiagnosticClassifier.classifyOutput("有内容", DiagnosticStage.EXTRACT)
        )
    }

    // ---------- 脱敏 ----------

    @Test
    fun textSummaryExposesOnlyLengthLineAndImageCounts() {
        val summary = DiagnosticRedactor.summarizeText("第一行\n<img src=\"a.png\">\n第三行")
        assertEquals("len=25,lines=3,imgs=1", summary)
        assertFalse("摘要不得包含正文", summary.contains("第一行"))
    }

    @Test
    fun nullTextSummaryIsZeroed() {
        assertEquals("len=0,lines=0,imgs=0", DiagnosticRedactor.summarizeText(null))
        assertEquals("len=4,lines=1,imgs=0", DiagnosticRedactor.summarizeText("abcd"))
    }

    @Test
    fun messageSanitizeRemovesUrlCredentialsQueryAndFragment() {
        val sanitized = DiagnosticRedactor.sanitizeMessage("获取失败: $secretUrl")
        assertTrue(sanitized, sanitized.contains("https://example.com/book/1"))
        assertFalse("不得保留 userinfo", sanitized.contains("p4ssw0rd"))
        assertFalse("不得保留查询串", sanitized.contains("token=abc123"))
        assertFalse("不得保留查询串", sanitized.contains("abc123"))
        assertFalse("不得保留签名", sanitized.contains("deadbeef"))
        assertFalse("不得保留片段", sanitized.contains("#frag"))
    }

    @Test
    fun messageSanitizeRemovesQueryEvenWhenKeyIsNotACredentialName() {
        // 守卫：这条样本的键名（chapter/sid）不在 SECRET_KEYS 里，只有 stripUrlSecrets 真正丢弃
        // 查询串与片段才能通过；旧实现只靠凭据键遮蔽，所以这条用例必须独立存在。
        val sanitized = DiagnosticRedactor.sanitizeMessage(
            "https://example.invalid/page?chapter=SYNTHETIC_PRIVATE_VALUE#SYNTHETIC_FRAGMENT"
        )
        assertFalse(sanitized, sanitized.contains("SYNTHETIC_PRIVATE_VALUE"))
        assertFalse(sanitized, sanitized.contains("SYNTHETIC_FRAGMENT"))
        assertFalse(sanitized, sanitized.contains("?"))
        assertEquals("https://example.invalid/page", sanitized)
    }

    @Test
    fun messageSanitizeDropsQueryButKeepsFollowingProse() {
        // 丢弃查询串不得吃掉后面的普通文本（URL 范围仍以空白等硬分隔符为界）
        val sanitized = DiagnosticRedactor.sanitizeMessage(
            "请求 https://a.example.com/p?sid=SECRET 失败，重试"
        )
        assertFalse(sanitized, sanitized.contains("SECRET"))
        assertTrue(sanitized, sanitized.contains("https://a.example.com/p"))
        assertTrue(sanitized, sanitized.contains("失败，重试"))
    }

    @Test
    fun messageSanitizeKeepsHostWhenPathContainsAtSign() {
        // F-03 守卫：userinfo 只在 authority 内；路径里的 '@' 不是凭据，
        // 不能因此把主机名切掉（旧实现取整段 authority+path 的最后一个 '@'）
        val sanitized = DiagnosticRedactor.sanitizeMessage("请求 https://api.example.com/@user/profile 失败")
        assertEquals("请求 https://api.example.com/@user/profile 失败", sanitized)
    }

    @Test
    fun messageSanitizeStillDropsCredentialOnMalformedUrlWithoutAuthority() {
        // 畸形 URL 切不出 authority（F-03 的保守回退）：宁可多切主机名，也不把凭据留在摘要里
        val sanitized = DiagnosticRedactor.sanitizeMessage("请求 https:///user:pw@api.example.com/x 失败")
        assertFalse(sanitized, sanitized.contains("pw"))
        assertTrue(sanitized, sanitized.contains("api.example.com/x"))
    }

    @Test
    fun messageSanitizeStillDropsRealUserInfoBeforeHost() {
        val sanitized = DiagnosticRedactor.sanitizeMessage(
            "请求 https://user:p4ssw0rd@api.example.com/@user/profile 失败"
        )
        assertFalse("不得保留 userinfo", sanitized.contains("p4ssw0rd"))
        assertFalse("不得保留用户名", sanitized.contains("user:"))
        assertTrue(sanitized, sanitized.contains("https://api.example.com/@user/profile"))
    }

    @Test
    fun messageSanitizeRemovesValueAfterColonAndSpace() {
        // 冒号后有空格时，值同样必须整体遮蔽，不能只掩掉冒号；
        // 两个 Header 分行给出，掩码范围按行计，互不吞并
        val sanitized = DiagnosticRedactor.sanitizeMessage(
            "Cookie: SYNTHETIC_COOKIE_VALUE\nAuthorization: SYNTHETIC_AUTH_VALUE"
        )
        assertFalse(sanitized, sanitized.contains("SYNTHETIC_COOKIE_VALUE"))
        assertFalse(sanitized, sanitized.contains("SYNTHETIC_AUTH_VALUE"))
        assertTrue(sanitized, sanitized.contains("Cookie:"))
        assertTrue(sanitized, sanitized.contains("Authorization:"))
    }

    @Test
    fun messageSanitizeRemovesCookieAndBearerValues() {
        val sanitized = DiagnosticRedactor.sanitizeMessage("$cookie Authorization: Bearer eyJhbGciOi.xx.yy")
        assertFalse(sanitized, sanitized.contains("abcdef123456"))
        assertFalse(sanitized, sanitized.contains("eyJhbGciOi"))
        assertTrue(sanitized, sanitized.contains("Cookie:"))
    }

    @Test
    fun messageSanitizeMasksWholeAuthorizationHeaderLine() {
        // Header 语义：冒号后的整行都是凭据范围，不能只掩掉第一个词
        // （只掩 Bearer 会把后面的 token 暴露出来）
        val sanitized = DiagnosticRedactor.sanitizeMessage("Authorization: Bearer SYNTHETIC_TOKEN_VALUE")
        assertFalse(sanitized, sanitized.contains("SYNTHETIC_TOKEN_VALUE"))
        assertFalse(sanitized, sanitized.contains("Bearer"))
        assertEquals("Authorization: ***", sanitized)
    }

    @Test
    fun messageSanitizeMasksWholeCookieHeaderLine() {
        val sanitized = DiagnosticRedactor.sanitizeMessage("Cookie: sid=SYNTHETIC_SID_VALUE; uid=42")
        assertFalse(sanitized, sanitized.contains("SYNTHETIC_SID_VALUE"))
        assertFalse(sanitized, sanitized.contains("uid=42"))
        assertEquals("Cookie: ***", sanitized)
    }

    @Test
    fun messageSanitizeTruncatesLongText() {
        val long = "x".repeat(1000)
        val sanitized = DiagnosticRedactor.sanitizeMessage(long)
        assertEquals(DiagnosticRedactor.MAX_MESSAGE_LENGTH + 3, sanitized.length)
    }

    @Test
    fun messageSanitizeKeepsOrdinaryDiagnosticText() {
        val sanitized = DiagnosticRedactor.sanitizeMessage("替换净化: 规则 广告替换出错")
        assertEquals("替换净化: 规则 广告替换出错", sanitized)
    }

    @Test
    fun skeletonKeepsStructureButNotWords() {
        val skeleton = DiagnosticRedactor.skeleton("<div>abc</div>\n<p>中文</p>")
        assertFalse(skeleton, skeleton.contains("abc"))
        assertFalse(skeleton, skeleton.contains("中文"))
        assertTrue(skeleton, skeleton.contains("_"))
        assertEquals("", DiagnosticRedactor.skeleton(null))
    }

    // ---------- 会话身份 ----------

    @Test
    fun collectorWithoutBoundTargetAcceptsNothing() {
        val collector = BookSourceDiagnosticsCollector()
        assertFalse(
            "未绑定正文目标的会话不得接受任何快照",
            collector.accepts(bookSourceUrl, bookUrl, chapterUrl)
        )
    }

    @Test
    fun collectorAcceptsOnlyTheBoundBookAndChapter() {
        val collector = boundCollector()
        assertTrue(collector.accepts(bookSourceUrl, bookUrl, chapterUrl))
        assertFalse("本地书（origin 不是书源）不得写入", collector.accepts("loc_book", bookUrl, chapterUrl))
        assertFalse("别的书不得写入", collector.accepts(bookSourceUrl, "https://source.example.com/book/2", chapterUrl))
        assertFalse("别的章节不得写入", collector.accepts(bookSourceUrl, bookUrl, "https://source.example.com/book/1/3.html"))
        assertFalse("别的书源不得写入", collector.accepts("https://other.example.com", bookUrl, chapterUrl))
    }

    @Test
    fun collectorAcceptsChapterOnlyTargetWhenBookUrlIsEmpty() {
        // `--<url>` 正文调试入口只给得出章节链接，此时书号不参与匹配
        val collector = BookSourceDiagnosticsCollector().apply {
            bindTarget(bookSourceUrl, null, chapterUrl)
        }
        assertTrue(collector.accepts(bookSourceUrl, "", chapterUrl))
        assertFalse(collector.accepts(bookSourceUrl, "", "https://source.example.com/book/1/9.html"))
    }

    @Test
    fun primaryStagesClaimIsGrantedExactlyOnceUnderConcurrency() {
        val collector = boundCollector()
        val granted = AtomicInteger()
        val start = CountDownLatch(1)
        val done = CountDownLatch(32)
        val pool = Executors.newFixedThreadPool(8)
        repeat(32) {
            pool.execute {
                start.await()
                if (collector.claimPrimaryStages()) granted.incrementAndGet()
                done.countDown()
            }
        }
        start.countDown()
        assertTrue("并发采集权测试超时", done.await(10, TimeUnit.SECONDS))
        pool.shutdown()
        assertEquals("并发分页只能有一个调用拿到阶段一/二的采集权", 1, granted.get())
    }

    // ---------- 快照与开关 ----------

    @Test
    fun disabledCollectorRecordsNothing() {
        val collector = BookSourceDiagnosticsCollector(isEnabled = false)
        collector.bindTarget(bookSourceUrl, bookUrl, chapterUrl)
        collector.record(DiagnosticStage.EXTRACT, "正文", 1L)
        collector.markNotExecuted(DiagnosticStage.REPLACE, "没有规则")
        collector.markMissingStagesNotExecuted("未执行")
        collector.recordFailure(RuntimeException("失败"))
        assertTrue(collector.snapshots().isEmpty())
        assertTrue(collector.summaryLines().isEmpty())
    }

    @Test
    fun enabledCollectorRecordsSummaryWithoutContent() {
        val collector = boundCollector()
        collector.record(DiagnosticStage.EXTRACT, "真实正文内容", 12L)
        val snapshot = collector.snapshotOf(DiagnosticStage.EXTRACT)!!
        assertEquals("len=6,lines=1,imgs=0", snapshot.summary)
        assertEquals(DiagnosticErrorClass.NONE, snapshot.errorClass)
        assertEquals(12L, snapshot.elapsedMs)
        assertTrue(snapshot.executed)
        assertFalse(snapshot.format(), snapshot.format().contains("真实正文内容"))
    }

    @Test
    fun sameStageIsRecordedOnlyOnceWithLatestValue() {
        val collector = boundCollector()
        collector.record(DiagnosticStage.EXTRACT, "第一次产出", 5L)
        collector.record(DiagnosticStage.EXTRACT, "第二次产出更长", 9L)
        assertEquals(1, collector.snapshots().count { it.stage == DiagnosticStage.EXTRACT })
        assertEquals("len=7,lines=1,imgs=0", collector.snapshotOf(DiagnosticStage.EXTRACT)!!.summary)
    }

    @Test
    fun notExecutedStageIsMarkedExplicitly() {
        val collector = boundCollector()
        collector.record(DiagnosticStage.EXTRACT, "正文", 1L)
        collector.markNotExecuted(DiagnosticStage.REPLACE, "书源未配置 replaceRegex，全文替换未执行")
        val snapshot = collector.snapshotOf(DiagnosticStage.REPLACE)!!
        assertFalse(snapshot.executed)
        assertNull(collector.snapshotOf(DiagnosticStage.FORMAT))
        val line = snapshot.format()
        assertTrue(line, line.contains("未执行"))
        assertFalse("未执行阶段不得声称有耗时", line.contains("耗时"))
        assertTrue(line, line.contains("replaceRegex"))
    }

    @Test
    fun missingStagesAreAllFilledAtSessionEnd() {
        val collector = boundCollector()
        collector.record(DiagnosticStage.REPLACE, "正文", 3L)
        collector.markMissingStagesNotExecuted("本次调试链路未执行该阶段")
        assertEquals(4, collector.summaryLines().size)
        assertTrue(collector.snapshotOf(DiagnosticStage.REPLACE)!!.executed)
        DiagnosticStage.values()
            .filter { it != DiagnosticStage.REPLACE }
            .forEach { stage ->
                val snapshot = collector.snapshotOf(stage)!!
                assertFalse("$stage 应标未执行", snapshot.executed)
                assertTrue(snapshot.format(), snapshot.format().contains("未执行"))
            }
    }

    @Test
    fun summaryLinesAreOrderedByStage() {
        val collector = boundCollector()
        collector.record(DiagnosticStage.READ_PROCESS, "d", 4L)
        collector.record(DiagnosticStage.EXTRACT, "a", 1L)
        collector.record(DiagnosticStage.FORMAT, "b", 2L)
        val lines = collector.summaryLines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("【诊断】1.提取后"))
        assertTrue(lines[1].startsWith("【诊断】2.HTML格式化后"))
        assertTrue(lines[2].startsWith("【诊断】4.阅读处理后"))
    }

    // ---------- 错误接线 ----------

    @Test
    fun chainFailureWithoutAnyStageIsClassifiedOnFirstStage() {
        // 抓取层就失败（没有任何阶段产出）时，错误类别必须出现在摘要里，而不是静默无诊断
        val collector = boundCollector()
        collector.recordFailure(java.net.UnknownHostException("source.example.com"))
        val snapshot = collector.snapshotOf(DiagnosticStage.EXTRACT)!!
        assertEquals(DiagnosticErrorClass.NETWORK, snapshot.errorClass)
        assertTrue(snapshot.format(), snapshot.format().contains("网络错误"))
    }

    @Test
    fun chainFailureKeepsTheLastStageSummaryAndAddsErrorClass() {
        val collector = boundCollector()
        collector.record(DiagnosticStage.EXTRACT, "提取出来的正文", 7L)
        collector.record(DiagnosticStage.FORMAT, "格式化后的正文", 8L)
        collector.markNotExecuted(DiagnosticStage.REPLACE, "书源未配置 replaceRegex，全文替换未执行")
        collector.recordFailure(ContentEmptyException("内容为空"))
        val format = collector.snapshotOf(DiagnosticStage.FORMAT)!!
        assertEquals(DiagnosticErrorClass.EMPTY_CONTENT, format.errorClass)
        assertEquals("len=7,lines=1,imgs=0", format.summary)
        assertEquals(8L, format.elapsedMs)
        assertTrue(collector.snapshotOf(DiagnosticStage.REPLACE)!!.format().contains("未执行"))
    }

    @Test
    fun replaceTimeoutIsDetectedByElapsedOverThreshold() {
        val collector = boundCollector()
        collector.record(DiagnosticStage.REPLACE, "正文", elapsedMs = 5000L, timeoutMs = 3000L)
        assertEquals(
            DiagnosticErrorClass.REPLACE_TIMEOUT,
            collector.snapshotOf(DiagnosticStage.REPLACE)!!.errorClass
        )
    }

    @Test
    fun errorSnapshotNeverCarriesRawContent() {
        val collector = boundCollector()
        collector.record(
            DiagnosticStage.READ_PROCESS,
            "章节真实正文",
            elapsedMs = 3L,
            error = RegexTimeoutException("替换超时 https://a.com/x?token=zzz")
        )
        val snapshot = collector.snapshotOf(DiagnosticStage.READ_PROCESS)!!
        val note = snapshot.note ?: ""
        assertNotEquals("章节真实正文", note)
        assertFalse(note, note.contains("zzz"))
        assertEquals(DiagnosticErrorClass.REPLACE_TIMEOUT, snapshot.errorClass)
    }

    @Test
    fun replaceTimeoutNoteNeverCopiesTheExceptionMessage() {
        // RegexTimeoutException 的消息里带规则名、正则与整段待替换正文，一律不进摘要
        val collector = boundCollector()
        collector.record(
            DiagnosticStage.READ_PROCESS,
            "正文",
            elapsedMs = 4000L,
            error = RegexTimeoutException("替换超时\n规则名称:广告\n替换内容:真实章节正文")
        )
        val note = collector.snapshotOf(DiagnosticStage.READ_PROCESS)!!.note ?: ""
        assertFalse(note, note.contains("真实章节正文"))
        assertFalse(note, note.contains("广告"))
        assertEquals(DiagnosticErrorClass.REPLACE_TIMEOUT, collector.snapshotOf(DiagnosticStage.READ_PROCESS)!!.errorClass)
    }

    @Test
    fun stageErrorIsClassifiedByItsOwnException() {
        val collector = boundCollector()
        collector.record(
            DiagnosticStage.EXTRACT,
            text = null,
            elapsedMs = 6L,
            error = PatternSyntaxException("Unclosed group", "([unclosed", 0)
        )
        val snapshot = collector.snapshotOf(DiagnosticStage.EXTRACT)!!
        assertEquals(DiagnosticErrorClass.RULE_SYNTAX, snapshot.errorClass)
        assertEquals("len=0,lines=0,imgs=0", snapshot.summary)
    }

    // ---------- 异常说明不得引用 message / 阶段四真实性 ----------

    @Test
    fun arbitraryExceptionMessageNeverReachesTheSummary() {
        // 验收阻塞 1 守卫：异常消息是无界输入，可能内嵌正文/规则片段，
        // 默认摘要只允许出现稳定的异常类名与错误类别
        val collector = boundCollector()
        collector.record(
            DiagnosticStage.EXTRACT,
            text = null,
            elapsedMs = 0,
            error = IllegalArgumentException("parse failed near SYNTHETIC_PRIVATE_BODY_SENTINEL")
        )
        val lines = collector.summaryLines().joinToString("\n")
        assertFalse(lines, lines.contains("SYNTHETIC_PRIVATE_BODY_SENTINEL"))
        assertFalse(lines, lines.contains("parse failed"))
        assertTrue(lines, lines.contains("IllegalArgumentException"))
    }

    @Test
    fun exceptionMessageIsNeverUsedEvenWhenItLooksHarmless() {
        // 反面守卫：看似无害的中文说明同样不进摘要（脱敏器无法判断它是不是正文）
        val collector = boundCollector()
        collector.record(
            DiagnosticStage.FORMAT,
            text = null,
            elapsedMs = 0,
            error = RuntimeException("第三章 风起 云天宗内门弟子")
        )
        val line = collector.snapshotOf(DiagnosticStage.FORMAT)!!.format()
        assertFalse(line, line.contains("云天宗"))
        assertTrue(line, line.contains("RuntimeException"))
    }

    @Test
    fun chainFailureNoteNeverCopiesTheExceptionMessage() {
        val collector = boundCollector()
        collector.record(DiagnosticStage.EXTRACT, "正文", 1L)
        collector.recordFailure(
            IllegalStateException("bad rule /html/body/@textNodes near SYNTHETIC_PRIVATE_BODY_SENTINEL")
        )
        val lines = collector.summaryLines().joinToString("\n")
        assertFalse(lines, lines.contains("SYNTHETIC_PRIVATE_BODY_SENTINEL"))
        assertFalse(lines, lines.contains("bad rule"))
        assertTrue(lines, lines.contains("IllegalStateException"))
    }

    @Test
    fun notExecutedMarkNeverOverwritesAnExecutedSnapshot() {
        // 验收阻塞 2 守卫：收尾兜底不得把已经执行过的阶段四读数覆盖成「未执行」
        val collector = boundCollector()
        collector.record(DiagnosticStage.READ_PROCESS, "阅读处理后的正文", 42L)
        collector.markNotExecutedIfMissing(
            DiagnosticStage.READ_PROCESS,
            "书源调试链路止于 BookContent，未进入阅读处理"
        )
        val snapshot = collector.snapshotOf(DiagnosticStage.READ_PROCESS)!!
        assertTrue("已执行的真实读数不得被覆盖成未执行", snapshot.executed)
        assertEquals(42L, snapshot.elapsedMs)
        assertEquals("len=8,lines=1,imgs=0", snapshot.summary)
        assertNull("被跳过的标注不应把原因挂到快照上", snapshot.note)
    }

    @Test
    fun notExecutedMarkStillFillsStagesWithoutSnapshots() {
        val collector = boundCollector()
        collector.markNotExecutedIfMissing(DiagnosticStage.READ_PROCESS, "未进入阅读处理")
        val snapshot = collector.snapshotOf(DiagnosticStage.READ_PROCESS)!!
        assertFalse(snapshot.executed)
        assertEquals("未进入阅读处理", snapshot.note)
    }

    @Test
    fun readingStageReportNeedsARealReadingSnapshotAndHappensOnlyOnce() {
        // 展示时机三态守卫（F-02）：没有真实读数不补打；只有「未执行」标注也不补打；
        // 真实读数第一次补打、之后不再重复（避免阅读/导出统计反复处理时刷屏）
        val collector = boundCollector()
        assertFalse("没有阶段四快照时不得补打", collector.shouldReportReadingStage())
        collector.markNotExecutedIfMissing(DiagnosticStage.READ_PROCESS, "未进入阅读处理")
        assertFalse("未执行标注不算真实读数", collector.shouldReportReadingStage())
        collector.record(DiagnosticStage.READ_PROCESS, "阅读处理后的正文", 3L)
        assertTrue("首次真实读数应补打", collector.shouldReportReadingStage())
        assertFalse("同一会话只补打一次", collector.shouldReportReadingStage())
    }

    @Test
    fun disabledCollectorNeverReportsReadingStage() {
        val collector = BookSourceDiagnosticsCollector(isEnabled = false)
        assertFalse("关闭的诊断不得占用补打权", collector.shouldReportReadingStage())
        collector.markNotExecutedIfMissing(DiagnosticStage.EXTRACT, "未执行")
        assertTrue(collector.snapshots().isEmpty())
    }

    @Test
    fun chainFailureNeverStampsTheReadingPathStage() {
        // F-01 守卫：阶段四由阅读路径产生，抓取链路的失败不得贴到它上面。
        // 反例场景：调试会话存活时断网，用户同时打开同一章（走缓存阅读）→ 真实阶段四读数存在。
        val collector = boundCollector()
        collector.record(DiagnosticStage.READ_PROCESS, "阅读处理后的正文", 3L)
        collector.recordFailure(java.net.UnknownHostException("source.example.com"))
        val readProcess = collector.snapshotOf(DiagnosticStage.READ_PROCESS)!!
        assertTrue(readProcess.executed)
        assertEquals("阅读读数不得被标成网络错误", DiagnosticErrorClass.NONE, readProcess.errorClass)
        assertNull(readProcess.note)
        assertEquals(
            "链路失败在没有任何抓取阶段产出时应归到阶段一",
            DiagnosticErrorClass.NETWORK,
            collector.snapshotOf(DiagnosticStage.EXTRACT)!!.errorClass
        )
    }

    @Test
    fun clearRemovesCollectedSnapshots() {
        val collector = boundCollector()
        collector.record(DiagnosticStage.EXTRACT, "a", 1L)
        collector.clear()
        assertTrue(collector.snapshots().isEmpty())
    }
}
