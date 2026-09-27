package io.legado.app.help.book.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-2 契约测试：诊断会话身份归一（M1/M2）与不匹配可见性（M3）。
 *
 * 对应裁定书 `docs/book-source-audit/D-2-补打匹配口径-裁定.md`。这里守的是两条真实场景：
 * 1. 调试页 `--<绝对URL>` 绑定绝对地址，而书里章节存的是**相对路径**（规则原文入库）；
 * 2. `book.origin` 与 `bookSource.bookSourceUrl` 只差尾斜杠/查询串。
 * 两种情况原来都逐字比较 ⇒ 永不匹配，且摘要会把原因写成「未进入阅读处理」。
 */
class DiagnosticIdentityContractTest {

    private val source = "https://source.example.com"
    private val base = "https://source.example.com/book/1/"
    private val relativeChapter = "chapter/2.html"
    private val absoluteChapter = "https://source.example.com/book/1/chapter/2.html"
    private val options = ",{\"headers\":{\"X-Test\":\"1\"}}"

    // ---------- M1：章节身份归一 ----------

    @Test
    fun relativeChapterResolvesAgainstItsOwnBase() {
        assertEquals(absoluteChapter, DiagnosticIdentity.chapterKey(base, relativeChapter))
    }

    @Test
    fun absoluteChapterIgnoresBaseAndStaysUnchanged() {
        assertEquals(absoluteChapter, DiagnosticIdentity.chapterKey(base, absoluteChapter))
        assertEquals(absoluteChapter, DiagnosticIdentity.chapterKey(null, absoluteChapter))
        assertEquals(absoluteChapter, DiagnosticIdentity.chapterKey("", absoluteChapter))
    }

    @Test
    fun urlOptionsAreStrippedBeforeComparing() {
        assertEquals(
            absoluteChapter,
            DiagnosticIdentity.chapterKey(base, absoluteChapter + options)
        )
        // 基地址自身带选项时同样要切掉（NetworkUtils 对 base 也是 substringBefore(",")）
        assertEquals(
            absoluteChapter,
            DiagnosticIdentity.chapterKey(base + options, relativeChapter)
        )
    }

    @Test
    fun commaInsideQueryIsNotTreatedAsOptions() {
        // 选项分隔符是「逗号 + 花括号」，查询串里的普通逗号不能被切掉
        val withComma = "https://source.example.com/p?a=1,2"
        assertEquals(withComma, DiagnosticIdentity.chapterKey(null, withComma))
    }

    @Test
    fun unresolvableUrlFallsBackToRawTextWithoutThrowing() {
        assertEquals("javascript:void(0)", DiagnosticIdentity.chapterKey(base, "javascript:void(0)"))
        assertEquals("data:text/plain,x", DiagnosticIdentity.chapterKey(base, "data:text/plain,x"))
    }

    @Test
    fun protocolRelativeChapterTakesTheBaseProtocol() {
        // 协议相对地址（//host/x）按 base 的协议解析；绑/读两侧同源，不会误接受
        assertEquals(
            "https://cdn.example.com/x.html",
            DiagnosticIdentity.chapterKey("https://source.example.com/book/1/", "//cdn.example.com/x.html")
        )
    }

    @Test
    fun basePathContainingCommaIsTruncatedLikeTheRequestPath() {
        // 已知边界（复审 F-6）：base 用 `substringBefore(',')` 切——与 NetworkUtils.getAbsoluteURL 一致；
        // 章节串用「逗号 + 花括号」正则切。两者口径不同源，base 路径里出现合法逗号时键会失真。
        // 该不对称在绑/读两侧一致，只会让键不等于真实请求 URL，**不会**造成误接受，故按要求记录而不改口径。
        assertEquals(
            "https://a.example.com/2.html",
            DiagnosticIdentity.chapterKey("https://a.example.com/b,c/", "2.html")
        )
    }

    @Test
    fun blankChapterUrlStaysBlank() {
        assertEquals("", DiagnosticIdentity.chapterKey(base, ""))
        assertEquals(null, DiagnosticIdentity.chapterKey(base, null))
    }

    // ---------- M2：书源身份归一 ----------

    @Test
    fun sourceKeyIgnoresTrailingSlashQueryAndFragment() {
        assertEquals(source, DiagnosticIdentity.sourceKey("$source/"))
        assertEquals(source, DiagnosticIdentity.sourceKey("$source?t=1"))
        assertEquals(source, DiagnosticIdentity.sourceKey("$source/#top"))
        assertEquals(source, DiagnosticIdentity.sourceKey("  $source  "))
    }

    @Test
    fun sourceKeyKeepsDifferentHostsApart() {
        assertNotEquals(
            DiagnosticIdentity.sourceKey(source),
            DiagnosticIdentity.sourceKey("https://other.example.com")
        )
        assertNotEquals(
            DiagnosticIdentity.sourceKey(source),
            DiagnosticIdentity.sourceKey("https://source.example.com.evil.test")
        )
    }

    @Test
    fun blankSourceIsNeverEqualToAnything() {
        assertEquals(null, DiagnosticIdentity.sourceKey("   "))
        assertEquals(null, DiagnosticIdentity.sourceKey(null))
        assertEquals(null, DiagnosticIdentity.sourceKey(""))
    }

    // ---------- 会话：D-2 两种方向 ----------

    @Test
    fun absoluteDebugTargetMatchesRelativeStoredChapter() {
        // D-2 原始形态：调试页粘绝对 URL，书里章节是相对路径
        val collector = BookSourceDiagnosticsCollector().apply {
            bindTarget(source, null, absoluteChapter)
        }
        assertTrue(
            "相对存储的章节必须能与绝对调试目标对上",
            collector.accepts(source, null, relativeChapter, base)
        )
        assertEquals(0, collector.mismatchCount())
    }

    @Test
    fun relativeDebugTargetMatchesAbsoluteStoredChapter() {
        // 反方向：调试入口给的是相对/带选项的地址，阅读侧给绝对地址
        val collector = BookSourceDiagnosticsCollector().apply {
            bindTarget(source, null, relativeChapter + options, base)
        }
        assertTrue(collector.accepts(source, null, absoluteChapter, null))
        assertEquals(0, collector.mismatchCount())
    }

    @Test
    fun rawLiteralEqualityStillAcceptsWhenOnlyOneSideHasABase() {
        // 回归守卫：归一比较必须是**新增**匹配途径，不是替换。
        // 存量书若 baseUrl 为空，两侧会归一到不同形态，但它们在 P1 真机轮里本来就能对上（++<tocUrl>）。
        val collector = BookSourceDiagnosticsCollector().apply {
            bindTarget(source, null, relativeChapter, base)
        }
        assertTrue(
            "原始串逐字相等必须仍然接受",
            collector.accepts(source, null, relativeChapter, null)
        )
        assertEquals(0, collector.mismatchCount())
    }

    @Test
    fun rawEqualityDoesNotLeakAcrossDifferentChapters() {
        // 回退途径不得变成「随便谁都收」：原始串不同、归一后也不同时仍必须拒绝
        val collector = BookSourceDiagnosticsCollector().apply {
            bindTarget(source, null, relativeChapter, base)
        }
        assertFalse(collector.accepts(source, null, "chapter/9.html", null))
        assertFalse(collector.accepts(source, null, "chapter/9.html", base))
        assertEquals(2, collector.mismatchCount())
    }

    @Test
    fun sourceVariantStillMatchesSameSource() {
        // M2：origin 与当前书源只差尾斜杠/查询串时仍算同一书源
        val collector = BookSourceDiagnosticsCollector().apply {
            bindTarget("$source?from=debug", null, absoluteChapter)
        }
        assertTrue(collector.accepts("$source/", null, absoluteChapter))
        // 反例守卫：同主机不同**路径**是不同书源，不得因为「同域名」被放宽
        assertFalse(collector.accepts("$source/book", null, absoluteChapter))
    }

    // ---------- 会话：隔离负例（不得因归一而放宽） ----------

    @Test
    fun otherBookOtherChapterAndLocalBookAreStillRejected() {
        val collector = BookSourceDiagnosticsCollector().apply {
            bindTarget(source, "https://source.example.com/book/1", absoluteChapter)
        }
        assertFalse(
            "别的书不得写入",
            collector.accepts(source, "https://source.example.com/book/2", absoluteChapter)
        )
        assertFalse(
            "别的章节不得写入（哪怕同目录）",
            collector.accepts(source, "https://source.example.com/book/1", "chapter/3.html", base)
        )
        assertFalse(
            "本地书不得写入",
            collector.accepts("loc_book", "https://source.example.com/book/1", absoluteChapter)
        )
        assertFalse(
            "别的书源不得写入",
            collector.accepts("https://other.example.com", "https://source.example.com/book/1", absoluteChapter)
        )
        assertEquals(4, collector.mismatchCount())
    }

    @Test
    fun differentHostWithSamePathIsNotAccepted() {
        val collector = BookSourceDiagnosticsCollector().apply {
            bindTarget(source, null, absoluteChapter)
        }
        assertFalse(collector.accepts("https://other.example.com", null, absoluteChapter))
    }

    // ---------- 计数与 M3 文案 ----------

    @Test
    fun mismatchCountOnlyCountsRejectedIdentities() {
        val collector = BookSourceDiagnosticsCollector().apply {
            bindTarget(source, null, absoluteChapter)
        }
        assertTrue(collector.accepts(source, null, absoluteChapter))
        assertTrue(collector.accepts(source, null, relativeChapter, base))
        assertEquals(0, collector.mismatchCount())
        assertFalse(collector.accepts(source, null, "chapter/9.html", base))
        assertFalse(collector.accepts("https://other.example.com", null, absoluteChapter))
        assertEquals(2, collector.mismatchCount())
    }

    @Test
    fun unboundCollectorRejectsWithoutCounting() {
        // 没有绑定目标 = 没有会话身份，不该被算成「身份不匹配」
        val collector = BookSourceDiagnosticsCollector()
        assertFalse(collector.accepts(source, null, absoluteChapter))
        assertEquals(0, collector.mismatchCount())
    }

    @Test
    fun disabledCollectorNeverAcceptsAndNeverCounts() {
        val collector = BookSourceDiagnosticsCollector(isEnabled = false).apply {
            bindTarget(source, null, absoluteChapter)
        }
        assertFalse(collector.accepts(source, null, "chapter/9.html", base))
        assertEquals(0, collector.mismatchCount())
    }

    @Test
    fun readingStageReasonDistinguishesChainStopFromIdentityMismatch() {
        // 没有排除记录：保持原口径「未进入阅读处理」
        val plain = BookSourceDiagnosticsCollector().apply {
            bindTarget(source, null, absoluteChapter)
        }
        assertEquals(
            "书源调试链路止于 BookContent，未进入阅读处理",
            plain.readingStageMissingReason()
        )

        // 有排除记录：写明「阅读处理发生过、只是身份不属于本次会话」，且不含任何 URL
        val mismatched = BookSourceDiagnosticsCollector().apply {
            bindTarget(source, null, absoluteChapter)
            accepts(source, null, "chapter/9.html", base)
            accepts(source, null, "chapter/10.html", base)
        }
        val reason = mismatched.readingStageMissingReason()
        assertTrue(reason, reason.contains("2 次"))
        assertFalse("说明不得回显 URL", reason.contains("http"))
        assertFalse("说明不得回显主机名", reason.contains("example.com"))
        assertFalse("说明不得再声称未进入阅读处理", reason.contains("未进入阅读处理"))
    }

    @Test
    fun readingStageReasonIsSafeForTheSummarySanitizer() {
        // 该文案会经过 DiagnosticRedactor.sanitizeMessage 落进摘要，长度与形态必须稳定
        val collector = BookSourceDiagnosticsCollector().apply {
            bindTarget(source, null, absoluteChapter)
            accepts(source, null, "chapter/9.html", base)
        }
        val sanitized = DiagnosticRedactor.sanitizeMessage(collector.readingStageMissingReason())
        assertEquals(collector.readingStageMissingReason(), sanitized)
        assertTrue(sanitized.length < DiagnosticRedactor.MAX_MESSAGE_LENGTH)
    }

    // ---------- F-1：收尾之后才发生不匹配，原因必须能刷新 ----------

    @Test
    fun mismatchAfterChainFinishRefreshesTheReadingStageReason() {
        // 复审 F-1 的时序：调试链路先收尾（此时计数为 0），用户随后读该章才发生身份不匹配。
        // 旧实现把原因在写入时固化成字符串，第 4 行会永远留着「未进入阅读处理」这句错误因果。
        val collector = BookSourceDiagnosticsCollector().apply {
            bindTarget(source, null, absoluteChapter)
        }
        collector.markNotExecutedIfMissing(
            DiagnosticStage.READ_PROCESS,
            collector.readingStageMissingReason()
        )
        assertTrue(
            "收尾时刻（计数 0）先写下旧文案",
            collector.summaryLines().last().contains("未进入阅读处理")
        )

        // 之后才发生一次身份不匹配（例如存量书 chapter.baseUrl 为空，归一对不上）
        assertFalse(collector.accepts(source, null, "chapter/2.html", null))
        assertTrue("计数变化必须能刷新原因", collector.refreshReadingStageMissingReasonIfMissing())

        val line = collector.summaryLines().last()
        assertTrue(line, line.contains("1 次"))
        assertFalse("不得再声称未进入阅读处理", line.contains("未进入阅读处理"))
        assertFalse("不得回显 URL", line.contains("http"))
        assertFalse("刷新不得把阶段四标成已执行", collector.snapshotOf(DiagnosticStage.READ_PROCESS)!!.executed)
    }

    @Test
    fun mismatchRefreshNeverOverwritesARealReadingSnapshot() {
        val collector = BookSourceDiagnosticsCollector().apply {
            bindTarget(source, null, absoluteChapter)
            record(DiagnosticStage.READ_PROCESS, "阅读处理后的正文", 5L)
            accepts(source, null, "chapter/9.html", base)
        }
        assertFalse(
            "已有真实读数时不得刷新（那会反向伪造）",
            collector.refreshReadingStageMissingReasonIfMissing()
        )
        val snapshot = collector.snapshotOf(DiagnosticStage.READ_PROCESS)!!
        assertTrue(snapshot.executed)
        assertEquals(5L, snapshot.elapsedMs)
    }

    @Test
    fun mismatchRefreshAndPrintAreLatchedToOncePerSession() {
        val collector = BookSourceDiagnosticsCollector().apply {
            bindTarget(source, null, absoluteChapter)
            markNotExecutedIfMissing(DiagnosticStage.READ_PROCESS, "收尾占位")
        }
        assertTrue(collector.claimMismatchRefreshReport())
        assertFalse("一个会话只允许打印一次刷新", collector.claimMismatchRefreshReport())
        // 计数继续更新（不只停在 1），但打印权不再发放
        collector.accepts(source, null, "chapter/9.html", base)
        collector.accepts(source, null, "chapter/10.html", base)
        assertTrue(collector.refreshReadingStageMissingReasonIfMissing())
        assertTrue(collector.summaryLines().last().contains("2 次"))
        assertFalse(collector.claimMismatchRefreshReport())
    }

    @Test
    fun disabledCollectorNeverRefreshesOrClaims() {
        val collector = BookSourceDiagnosticsCollector(isEnabled = false).apply {
            bindTarget(source, null, absoluteChapter)
            markNotExecutedIfMissing(DiagnosticStage.READ_PROCESS, "占位")
        }
        assertFalse(collector.refreshReadingStageMissingReasonIfMissing())
        assertFalse(collector.claimMismatchRefreshReport())
        assertTrue(collector.snapshots().isEmpty())
    }

    @Test
    fun blankChapterUrlBindsNothing() {
        // 复审 r003 F-4：`--` 加纯空白会让归一键成为一串空格，绑上一个永远匹配不到的「幽灵目标」，
        // 于是任何真实正文处理都被计成「身份不匹配」。空白必须等于没绑定。
        val collector = BookSourceDiagnosticsCollector().apply {
            bindTarget(source, null, "   ", base)
        }
        assertFalse(collector.accepts(source, null, absoluteChapter, base))
        assertEquals("未绑定目标时不得计数", 0, collector.mismatchCount())
        assertFalse(collector.refreshReadingStageMissingReasonIfMissing())
    }

    @Test
    fun concurrentRefreshNeverOverwritesARealReadingSnapshot() {
        // 复审 r003 F-2：refresh 的「取快照 → 判断 → 写回」必须原子。
        // 旧实现跨锁读改写，会在「同章阅读写真实读数」与「别的章被拒触发刷新」并发时把真实读数覆盖成「未执行」。
        repeat(120) {
            val collector = BookSourceDiagnosticsCollector().apply {
                bindTarget(source, null, absoluteChapter)
                markNotExecutedIfMissing(DiagnosticStage.READ_PROCESS, "收尾占位")
            }
            val reader = java.lang.Thread {
                collector.record(DiagnosticStage.READ_PROCESS, "阅读处理后的正文", 7L)
            }
            val mismatcher = java.lang.Thread {
                collector.accepts(source, null, "chapter/9.html", base)
                collector.refreshReadingStageMissingReasonIfMissing()
            }
            reader.start(); mismatcher.start(); reader.join(); mismatcher.join()
            val snapshot = collector.snapshotOf(DiagnosticStage.READ_PROCESS)!!
            assertTrue("真实读数不得被身份刷新覆盖成未执行", snapshot.executed)
            assertEquals(7L, snapshot.elapsedMs)
            assertNull("真实读数不得被挂上「身份隔离」说明", snapshot.note)
        }
    }

    @Test
    fun targetToStringNeverLeaksUrls() {
        // 复审 F-2：Target 驻留原始章节串，默认 data class 输出会在有人 log(target) 时把 URL 带出去。
        // 书源键允许可见（调试日志本来就打书源 URL，且它已去掉查询串/片段）；章节串与书号必须遮蔽。
        val target = BookSourceDiagnosticsCollector.Target(
            sourceKey = DiagnosticIdentity.sourceKey("$source?token=SECRET")!!,
            bookUrl = "https://source.example.com/book/1?sig=SECRET2",
            chapterKey = absoluteChapter,
            rawChapterUrl = absoluteChapter
        )
        val text = target.toString()
        assertFalse("不得回显章节 URL", text.contains(absoluteChapter))
        assertFalse("不得回显书号查询串", text.contains("SECRET"))
        assertTrue(text, text.contains("rawChapterUrl=set"))
        assertTrue(text, text.contains("chapterKey=<set>"))
    }
}
