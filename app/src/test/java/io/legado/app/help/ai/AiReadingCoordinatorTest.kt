package io.legado.app.help.ai

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookContent
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.After

/**
 * 当前章阅读协调器的纯状态/伪 Provider 单测：不需要网络，验证不串章、不自动重试、
 * 迟到结果丢弃、完成才可切 AI、切章即失效。
 */
class AiReadingCoordinatorTest {
    private val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @After fun closeScope() { jobScope.cancel() }
    private val book = Book(bookUrl = "book-a", origin = "https://source.invalid", type = BookType.text)
    private val content = BookContent(true, listOf("正文甲", "正文乙"), emptyList())
    private val key = AiApiKey.from("synthetic-test-key")
    private val config = AiProviderConfig("fake", "https://api.invalid", "fake")

    private fun scope(
        content: BookContent = this.content,
        bookEnabled: Boolean = true,
        consented: Boolean = true,
        network: Boolean = true,
        chapterIndex: Int = 0,
        bookUrl: String = "book-a"
    ) = AiChapterScope(
        book = if (bookUrl == "book-a") this.book else this.book.copy(bookUrl = bookUrl),
        chapterUrl = "chapter",
        chapterIndex = chapterIndex,
        original = content,
        bookEnabled = bookEnabled,
        deviceConsentConfirmed = consented,
        allowNetwork = network,
        apiKey = key,
        config = config
    )

    private suspend fun awaitState(c: AiChapterCoordinator, pred: (AiChapterUiState) -> Boolean) =
        withTimeout(4000) {
            while (!pred(c.state.value)) yield()
        }

    @Test fun unchangedCompletionIsReadyAndKeepsReadingSource() = runBlocking {
        val c = AiChapterCoordinator(AiChapterRunner { _, _, _, _ ->
            AiProcessResult.Completed(content.textList.joinToString("\n"))
        }, jobScope)
        assertFalse(c.completedWithoutChanges)
        c.resolve(content, scope(), true)
        awaitState(c) { it is AiChapterUiState.Ready }
        assertTrue(c.completedWithoutChanges)
        assertEquals(AiDisplaySource.Original, c.displaySource.value)
        c.reset()
        assertFalse(c.completedWithoutChanges)
    }

    @Test fun notCurrentOrNullScopeNeverRunsAndKeepsOriginal() = runBlocking {
        val calls = AtomicInt()
        val runner = AiChapterRunner { _, _, _, _ -> calls.increment(); AiProcessResult.Completed("不应出现") }
        val c = AiChapterCoordinator(runner, jobScope)
        val original = AiDisplayContent.original(content)
        assertEquals(original.content.textList, c.resolve(content, null, false).content.textList)
        assertEquals(original.content.textList, c.resolve(content, scope(), false).content.textList)
        assertEquals(0, calls.value)
    }

    @Test fun eligibleStartsOnceAndOnlyPublishesReadyAfterComplete() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInt()
        val runner = AiChapterRunner { _, _, _, onProgress ->
            calls.increment()
            started.complete(Unit)
            onProgress(1, 1)
            release.await()
            AiProcessResult.Completed("净化结果")
        }
        val c = AiChapterCoordinator(runner, jobScope)
        val display = c.resolve(content, scope(), true)
        // 处理期间显示原文，不阻塞
        assertEquals(listOf("正文甲", "正文乙"), display.content.textList)
        assertFalse(display.isAi)
        started.await()
        awaitState(c) { it is AiChapterUiState.Processing }
        assertTrue(c.isProcessing)
        release.complete(Unit)
        awaitState(c) { it is AiChapterUiState.Ready }
        assertTrue(c.hasCompleted)
        assertFalse(c.completedWithoutChanges)
        assertEquals(1, calls.value)
    }

    @Test fun readyButNotSelectedStillShowsOriginalUntilShowAi() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val runner = AiChapterRunner { _, _, _, _ -> release.await(); AiProcessResult.Completed("净化结果") }
        val c = AiChapterCoordinator(runner, jobScope)
        c.resolve(content, scope(), true)
        release.complete(Unit)
        awaitState(c) { it is AiChapterUiState.Ready }
        val display = c.resolve(content, scope(), true)
        assertFalse(display.isAi)
        assertEquals(listOf("正文甲", "正文乙"), display.content.textList)
    }

    @Test fun showAiShowsCandidateAndShowOriginalReverts() = runBlocking {
        val runner = AiChapterRunner { _, _, _, _ -> AiProcessResult.Completed("净化结果") }
        val c = AiChapterCoordinator(runner, jobScope)
        c.resolve(content, scope(), true)
        awaitState(c) { it is AiChapterUiState.Ready }
        c.showAi()
        val display = c.resolve(content, scope(), true)
        assertTrue(display.isAi)
        assertEquals("净化结果", display.content.textList.single())
        c.showOriginal()
        assertFalse(c.resolve(content, scope(), true).isAi)
    }

    @Test fun cancelDiscardsLateResultAndDoesNotAutoRestart() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInt()
        val runner = AiChapterRunner { _, _, _, _ ->
            calls.increment()
            withContext(NonCancellable) { started.complete(Unit); release.await() }
            AiProcessResult.Completed("迟到结果")
        }
        val c = AiChapterCoordinator(runner, jobScope)
        c.resolve(content, scope(), true)
        started.await()
        c.cancel()
        awaitState(c) { it is AiChapterUiState.Idle }
        release.complete(Unit)
        yield()
        // 迟到 Completed 不得变成 Ready
        assertFalse(c.hasCompleted)
        assertFalse(c.state.value is AiChapterUiState.Ready)
        // 不自动重启
        assertEquals(AiDisplaySource.Original, c.displaySource.value)
        assertEquals(1, calls.value)
    }

    @Test fun failureKeepsOriginalAndNeedsExplicitRetry() = runBlocking {
        val calls = AtomicInt()
        val runner = AiChapterRunner { _, _, _, _ -> calls.increment(); AiProcessResult.Failed(AiFailureCode.NETWORK, 1) }
        val c = AiChapterCoordinator(runner, jobScope)
        val display = c.resolve(content, scope(), true)
        awaitState(c) { it is AiChapterUiState.Failed }
        assertFalse(display.isAi)
        assertEquals(listOf("正文甲", "正文乙"), display.content.textList)
        // 失败不自动重试
        assertEquals(1, calls.value)
        assertEquals(AiDisplaySource.Original, c.displaySource.value)
        // 安全失败分类码与失败块序号必须贯穿到 UI 状态。
        val failed = c.state.value as AiChapterUiState.Failed
        assertEquals(AiFailureCode.NETWORK, failed.code)
        assertEquals(1, failed.failedChunkIndex)
    }

    @Test fun failedStateCarriesSafeBlockIndex() = runBlocking {
        val runner = AiChapterRunner { _, _, _, _ -> AiProcessResult.Failed(AiFailureCode.PROTOCOL, 3) }
        val c = AiChapterCoordinator(runner, jobScope)
        c.resolve(content, scope(), true)
        awaitState(c) { it is AiChapterUiState.Failed }
        val failed = c.state.value as AiChapterUiState.Failed
        assertEquals(AiFailureCode.PROTOCOL, failed.code)
        assertEquals(3, failed.failedChunkIndex)
    }

    /** 反例：章节级/提交级失败（请求数不作为“块号”）。即便处理器返回过多次请求，
     * 只要失败不可归因于某一块，failedChunkIndex 就必须为 null；UI 据此不显示“第 N 块”。 */
    @Test fun chapterLevelFailuresCarryNoChunkIndex() = runBlocking {
        val chapterLevel = listOf(
            AiFailureCode.STRUCTURE, AiFailureCode.CACHE_COMMIT, AiFailureCode.CACHE_INVALIDATED,
            AiFailureCode.INTERNAL, AiFailureCode.IDENTITY_MISMATCH, AiFailureCode.INVALID_CHUNKING,
            AiFailureCode.PREPARATION, AiFailureCode.MISSING_IDENTITY
        )
        chapterLevel.forEach { code ->
            val runner = AiChapterRunner { _, _, _, _ -> AiProcessResult.Failed(code) }
            val c = AiChapterCoordinator(runner, jobScope)
            c.resolve(content, scope(), true)
            awaitState(c) { it is AiChapterUiState.Failed }
            val failed = c.state.value as AiChapterUiState.Failed
            assertEquals(code, failed.code)
            assertNull("章级/提交级失败不得携带块号：$code", failed.failedChunkIndex)
        }
    }

    @Test fun differentChapterInvalidatesOldTokenAndRejectsLateResult() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInt()
        val runner = AiChapterRunner { _, _, _, _ ->
            val n = calls.increment()
            if (n == 1) withContext(NonCancellable) { started.complete(Unit); release.await() }
            AiProcessResult.Completed(if (n == 1) "旧章结果" else "新章结果")
        }
        val c = AiChapterCoordinator(runner, jobScope)
        val old = scope(chapterIndex = 0)
        c.resolve(content, old, true)
        started.await()
        // 切到另一章（不同 resetKey）
        val other = scope(content = BookContent(true, listOf("新正文"), emptyList()), chapterIndex = 1)
        val display2 = c.resolve(other.original, other, true)
        // 立即用新章原始正文展示
        assertEquals(listOf("新正文"), display2.content.textList)
        release.complete(Unit)
        awaitState(c) { it is AiChapterUiState.Ready }
        c.showAi()
        // 旧章迟到结果被丢弃，新章自己的候选生效
        assertEquals("新章结果", c.resolve(other.original, other, true).content.textList.single())
        assertEquals(2, calls.value)
    }

    @Test fun retryAfterFailureRunsAgain() = runBlocking {
        var first = true
        val calls = AtomicInt()
        val runner = AiChapterRunner { _, _, _, _ ->
            calls.increment()
            if (first) { first = false; AiProcessResult.Failed(AiFailureCode.NETWORK, 1) }
            else AiProcessResult.Completed("重试成功")
        }
        val c = AiChapterCoordinator(runner, jobScope)
        c.resolve(content, scope(), true)
        awaitState(c) { it is AiChapterUiState.Failed }
        c.retry()
        awaitState(c) { it is AiChapterUiState.Ready }
        assertEquals(2, calls.value)
        c.showAi()
        assertEquals("重试成功", c.resolve(content, scope(), true).content.textList.single())
    }

    private class AtomicInt {
        private val counter = java.util.concurrent.atomic.AtomicInteger()
        val value get() = counter.get()
        fun increment(): Int = counter.incrementAndGet()
    }
}
