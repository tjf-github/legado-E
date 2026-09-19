package io.legado.app.help.ai

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookContent
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiChapterServiceTest {
    @get:Rule val folder = TemporaryFolder()
    private val book = Book(bookUrl = "book", origin = "https://source", type = BookType.text)
    private val content = BookContent(false, listOf("原始正文"), null)
    private val key = AiApiKey.from("test-only-key")
    private fun repository(enabled: Boolean = true) = AiConfigRepository(object : AiTextConfigStore {
        private var value = AiTextConfig(enabled = enabled, model = "fake")
        override fun load() = value
        override fun save(config: AiTextConfig) { value = config }
    })

    @Test fun offUnconfirmedLocalAndStructuredInputsNeverCallProvider() = runBlocking {
        var calls = 0
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                calls++
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val service = AiChapterService(provider, AiChapterCache(folder.newFolder()), repository())
        assertEquals(AiProcessResult.Original, service.process(book, "chapter", 0, content, key, AiTaskToken()))
        assertEquals(AiProcessResult.Original, service.process(book, "chapter", 0, content, key, AiTaskToken(), true, false))
        listOf(book.copy(type = BookType.image), book.copy(origin = BookType.localTag, type = BookType.text or BookType.local)).forEach {
            assertEquals(AiProcessResult.Original, service.process(it, "chapter", 0, content, key, AiTaskToken(), true, true))
        }
        assertEquals(AiProcessResult.Original, service.process(book, "chapter", 0, content.copy(textList = listOf("<p>正文</p>")), key, AiTaskToken(), true, true))
        val disabled = AiChapterService(provider, AiChapterCache(folder.newFolder()), repository(false))
        assertEquals(AiProcessResult.Original, disabled.process(book, "chapter", 0, content, key, AiTaskToken(), true, true))
        assertEquals(0, calls)
    }

    @Test fun configurationChangeCancelsOldRequestAndDoesNotCacheLateResult() = runBlocking {
        val root = folder.newFolder()
        val configs = repository()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                withContext(NonCancellable) { entered.complete(Unit); release.await() }
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val service = AiChapterService(provider, AiChapterCache(root), configs)
        val task = async { service.process(book, "chapter", 0, content, key, AiTaskToken(), true, true) }
        entered.await()
        configs.update(configs.current().config.copy(model = "changed"))
        release.complete(Unit)
        assertTrue(runCatching { task.await() }.exceptionOrNull() is CancellationException)
        assertFalse(root.walkTopDown().any { it.isFile })
        assertEquals(listOf("原始正文"), content.textList)
    }
}
