package io.legado.app.help.ai

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 失败 reporter 的端到端行为单测：证明
 *  - 状态首次落到 Failed 时只上报一次（记录器计数）；
 *  - 上报载荷只含稳定分类码 + 可选块号 + 可选 ANCHOR 枚举细分，绝不携带正文/密钥/异常 message；
 *  - 章级/提交级失败仍然上报但块号为 null；
 *  - 重复设置同一失败状态不会重复上报（避免 Activity 重组重复日志）。
 */
class AiFailureReporterTest {
    private val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @After fun closeScope() { jobScope.cancel() }
    private val book = Book(bookUrl = "book-a", origin = "https://source.invalid", type = BookType.text)
    private val content = BookContent(true, listOf("正文甲", "正文乙"), emptyList())
    private val key = AiApiKey.from("synthetic-test-key")
    private val config = AiProviderConfig("fake", "https://api.invalid", "fake")

    private fun scope() = AiChapterScope(
        book = book,
        chapterUrl = "chapter",
        chapterIndex = 0,
        original = content,
        bookEnabled = true,
        deviceConsentConfirmed = true,
        allowNetwork = true,
        apiKey = key,
        config = config
    )

    private suspend fun awaitFailed(c: AiChapterCoordinator) =
        withTimeout(4000) { while (!(c.state.value is AiChapterUiState.Failed)) yield() }

    private class RecordingReporter : AiFailureReporter {
        data class Call(
            val code: AiFailureCode,
            val failedChunkIndex: Int?,
            val anchorDetail: AiAnchorFailureDetail?
        )
        val calls = mutableListOf<Call>()
        override fun report(
            code: AiFailureCode,
            failedChunkIndex: Int?,
            anchorDetail: AiAnchorFailureDetail?
        ) {
            calls += Call(code, failedChunkIndex, anchorDetail)
        }
    }

    @Test fun reportsExactlyOnceWhenStateSettlesToFailed() = runBlocking {
        val reporter = RecordingReporter()
        val c = AiChapterCoordinator(
            AiChapterRunner { _, _, _, _ -> AiProcessResult.Failed(AiFailureCode.PROTOCOL, 2) },
            jobScope, reporter
        )
        c.resolve(content, scope(), true)
        awaitFailed(c)
        assertEquals(listOf(RecordingReporter.Call(AiFailureCode.PROTOCOL, 2, null)), reporter.calls)
    }

    /** 反例：章级/提交级失败无块号，但仍必须上报一次。 */
    @Test fun reportsChapterLevelFailureWithNullChunk() = runBlocking {
        val chapterLevel = listOf(
            AiFailureCode.STRUCTURE, AiFailureCode.CACHE_COMMIT, AiFailureCode.CACHE_INVALIDATED,
            AiFailureCode.INTERNAL, AiFailureCode.IDENTITY_MISMATCH, AiFailureCode.INVALID_CHUNKING,
            AiFailureCode.PREPARATION, AiFailureCode.MISSING_IDENTITY
        )
        chapterLevel.forEach { code ->
            val reporter = RecordingReporter()
            val c = AiChapterCoordinator(
                AiChapterRunner { _, _, _, _ -> AiProcessResult.Failed(code) },
                jobScope, reporter
            )
            c.resolve(content, scope(), true)
            awaitFailed(c)
            assertEquals(listOf(RecordingReporter.Call(code, null, null)), reporter.calls)
        }
    }

    /** 反例：重复设置同一失败状态不得重复上报（StateFlow 收集/Activity 重组不会刷新日志）。 */
    @Test fun duplicateFailedSettlementDoesNotReReport() = runBlocking {
        val reporter = RecordingReporter()
        var calls = 0
        val c = AiChapterCoordinator(
            AiChapterRunner { _, _, _, _ -> calls++; AiProcessResult.Failed(AiFailureCode.NETWORK, 1) },
            jobScope, reporter
        )
        // resolve 两次：第一次启动处理，第二次命中同一已完成/失败态；仅应上报一次。
        c.resolve(content, scope(), true)
        awaitFailed(c)
        val display = c.resolve(content, scope(), true)
        assertTrue(display.content.textList.isNotEmpty())
        assertEquals(1, calls)
        assertEquals(1, reporter.calls.size)
        assertEquals(RecordingReporter.Call(AiFailureCode.NETWORK, 1, null), reporter.calls.single())
    }

    /** 反例：载荷里绝不能出现正文/密钥字面量（reporter 只拿到分类码与块号）。 */
    @Test fun reporterPayloadContainsOnlySafeEnumsAndChunkIndex() = runBlocking {
        val reporter = RecordingReporter()
        val c = AiChapterCoordinator(
            AiChapterRunner { _, _, _, _ -> AiProcessResult.Failed(
                AiFailureCode.ANCHOR,
                5,
                AiAnchorFailureDetail(AiAnchorFailureKind.ambiguous, AiEditEffect.changed)
            ) },
            jobScope, reporter
        )
        c.resolve(content, scope(), true)
        awaitFailed(c)
        // 断言 reporter 只见到 (code, chunkIndex)，没有机会携带任何文本。
        val call = reporter.calls.single()
        assertEquals(AiFailureCode.ANCHOR, call.code)
        assertEquals(5, call.failedChunkIndex)
        assertEquals(
            AiAnchorFailureDetail(AiAnchorFailureKind.ambiguous, AiEditEffect.changed),
            call.anchorDetail
        )
        // 正文/密钥字面量绝不出现在上报载荷中：录制器只有枚举与数字字段。
        assertTrue(reporter.calls.toString().let {
            it.contains("ANCHOR") && it.contains("ambiguous") && it.contains("changed") &&
                !it.contains("正文") && !it.contains("synthetic")
        })
    }
}
