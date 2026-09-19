package io.legado.app.help.ai

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookContent
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Reader-facing service tests, independent of the DSH UI implementation. */
class AiStageDServiceIntegrationTest {
    @get:Rule val folder = TemporaryFolder()
    private val key = AiApiKey.from("synthetic-test-key")
    private val book = Book(bookUrl = "book-a", origin = "https://source.invalid", type = BookType.text)
    private val content = BookContent(true, listOf("正文甲", "", "正文乙"), emptyList())

    private fun repository() = AiConfigRepository(object : AiTextConfigStore {
        private var value = AiTextConfig(enabled = true, model = "fake")
        override fun load() = value
        override fun save(config: AiTextConfig) { value = config }
    })

    @Test fun readerInvalidationIsCheckedBeforeCachingAndProgressDoesNotHoldTokenLock() = runBlocking {
        val root = folder.newFolder()
        val token = AiTaskToken()
        var callbackCompleted = false
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey) =
                AiChunkOutput(request.chunkId, emptyList())
        }
        val service = AiChapterService(provider, AiChapterCache(root), repository())
        val outcome = runCatching {
            service.process(book, "chapter", 0, content, key, token, true, true,
                onProgress = { done, _ ->
                    if (done > 0) {
                        val thread = Thread { token.invalidate() }.apply { start() }
                        thread.join(1500)
                        callbackCompleted = !thread.isAlive
                    }
                })
        }
        assertTrue("UI cancellation must not deadlock with progress callback", callbackCompleted)
        assertTrue(outcome.exceptionOrNull() is CancellationException)
        assertFalse(root.walkTopDown().any { it.isFile })
    }

    @Test fun currentReaderGateAndRequestStatsReachTheRealProcessor() = runBlocking {
        var current = true
        var sent = 0
        var requests = 0
        val root = folder.newFolder()
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                current = false
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val service = AiChapterService(provider, AiChapterCache(root), repository())
        val outcome = runCatching {
            service.process(book, "chapter", 0, content, key, AiTaskToken(), true, true,
                isCurrent = { current }, onRequest = { requests++; sent += it })
        }
        assertTrue(outcome.exceptionOrNull() is CancellationException)
        assertEquals(1, requests)
        assertEquals(content.textList.joinToString("\n").length, sent)
        assertFalse(root.walkTopDown().any { it.isFile })
    }

    @Test fun revisitUsesCompleteCacheButChangedOriginalCannotReuseIt() = runBlocking {
        var calls = 0
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                calls++
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val service = AiChapterService(provider, AiChapterCache(folder.newFolder()), repository())
        suspend fun read(input: BookContent) = service.process(book, "chapter", 0, input, key, AiTaskToken(), true, true)
        val first = read(content) as AiProcessResult.Completed
        assertEquals("正文甲\n\n正文乙", first.text)
        assertEquals(1, first.requestCount)
        val cached = read(content) as AiProcessResult.Completed
        assertEquals(first.text, cached.text)
        assertEquals(0, cached.requestCount)
        assertEquals(1, calls)
        val changed = read(content.copy(textList = listOf("正文更新"))) as AiProcessResult.Completed
        assertEquals("正文更新", changed.text)
        assertEquals(2, calls)
        assertEquals(listOf("正文甲", "", "正文乙"), content.textList)
        assertTrue(content.sameTitleRemoved)
        assertEquals(emptyList<Any>(), content.effectiveReplaceRules)
    }

    @Test fun navigationToAnotherBookAtSameIndexRejectsLateOldResult() = runBlocking {
        withTimeout(5000) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val provider = object : AiTextProvider {
                override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                    if (++calls == 1) withContext(NonCancellable) { entered.complete(Unit); release.await() }
                    return AiChunkOutput(request.chunkId, emptyList())
                }
            }
            val root = folder.newFolder()
            val service = AiChapterService(provider, AiChapterCache(root), repository())
            val oldToken = AiTaskToken()
            val old = async { service.process(book, "chapter", 0, content, key, oldToken, true, true) }
            entered.await()
            oldToken.invalidate()
            val next = async {
                service.process(book.copy(bookUrl = "book-b"), "chapter", 0,
                    content.copy(textList = listOf("另一本正文")), key, AiTaskToken(), true, true)
            }
            release.complete(Unit)
            assertTrue(runCatching { old.await() }.exceptionOrNull() is CancellationException)
            assertEquals("另一本正文", (next.await() as AiProcessResult.Completed).text)
            assertEquals(1, root.walkTopDown().count { it.isFile })
            assertEquals(2, calls)
        }
    }

    @Test fun cachedResultStillRequiresCurrentBookAndDevicePermission() = runBlocking {
        var calls = 0
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                calls++
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val configs = repository()
        val service = AiChapterService(provider, AiChapterCache(folder.newFolder()), configs)
        assertTrue(service.process(book, "chapter", 0, content, key, AiTaskToken(), true, true) is AiProcessResult.Completed)
        assertEquals(AiProcessResult.Original, service.process(book, "chapter", 0, content, key, AiTaskToken(), true, false))
        assertEquals(AiProcessResult.Original, service.process(book, "chapter", 0, content, key, AiTaskToken(), false, true))
        configs.disable()
        assertEquals(AiProcessResult.Original, service.process(book, "chapter", 0, content, key, AiTaskToken(), true, true))
        assertEquals(1, calls)
    }
}
