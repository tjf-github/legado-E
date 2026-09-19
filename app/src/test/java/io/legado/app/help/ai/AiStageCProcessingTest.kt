package io.legado.app.help.ai

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiStageCProcessingTest {
    @get:Rule val folder = TemporaryFolder()
    private val config = AiProviderConfig("fake", "https://example.invalid", "fake")
    private val key = AiApiKey.from("test-only-secret")
    private fun identity(text: String) = AiChapterIdentity.build("book", "chapter", 0, text, config, 1, "fp")

    @Test fun validProcessingHitsCacheWithoutRepeatingProvider() = runBlocking {
        val cache = AiChapterCache(folder.newFolder())
        val input = "甲乙丙丁"
        val id = identity(input)
        var calls = 0
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                calls++
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val processor = AiChapterProcessor(provider, cache)
        repeat(2) {
            val result = processor.process(AiCacheKey.create(id), AiTextChunker.chunk(input, 2), config, key, id)
            assertEquals(input, (result as AiProcessResult.Completed).text)
        }
        assertEquals(2, calls)
    }

    @Test fun failureInSecondChunkNeverCachesPartialAndStopsCalling() = runBlocking {
        val root = folder.newFolder()
        val cache = AiChapterCache(root)
        val input = "甲乙丙丁戊己"
        val id = identity(input)
        var calls = 0
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                calls++
                return AiChunkOutput(if (calls == 2) "wrong" else request.chunkId, emptyList())
            }
        }
        assertTrue(AiChapterProcessor(provider, cache).process(AiCacheKey.create(id), AiTextChunker.chunk(input, 2), config, key, id) is AiProcessResult.Failed)
        assertEquals(2, calls)
        assertNull(AiChapterCache(root).read(id))
        assertFalse(root.walkTopDown().any { it.isFile && it.extension == "tmp" })
    }

    @Test fun clearDuringRequestRejectsLateCompletedAndStopsRemainingChunks() = runBlocking {
        val cache = AiChapterCache(folder.newFolder())
        val input = "甲乙丙丁"
        val id = identity(input)
        var calls = 0
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                calls++
                cache.deleteBook(id.bookUrlHash)
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val result = AiChapterProcessor(provider, cache).process(AiCacheKey.create(id), AiTextChunker.chunk(input, 2), config, key, id)
        assertFalse(result is AiProcessResult.Completed)
        assertEquals(1, calls)
        assertNull(cache.read(id))
    }

    @Test fun wrongInputIdentityNeverCallsProviderOrPoisonsCache() = runBlocking {
        val cache = AiChapterCache(folder.newFolder())
        val id = identity("另一份正文")
        var calls = 0
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                calls++
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        assertFalse(AiChapterProcessor(provider, cache).process(AiCacheKey.create(id), AiTextChunker.chunk("原文"), config, key, id) is AiProcessResult.Completed)
        assertEquals(0, calls)
        assertNull(cache.read(id))
    }

    @Test fun locallyCreatedMarkupCausesWholeChapterFallback() = runBlocking {
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey) =
                AiChunkOutput(request.chunkId, listOf(AiEdit(1, 2, " ", "", AiEditKind.whitespace)))
        }
        val result = AiChapterProcessor(provider).process("html-result", AiTextChunker.chunk("< p>"), config, key)
        assertFalse(result is AiProcessResult.Completed)
    }

    @Test fun explicitTokenInvalidationCancelsIgnoringProviderAndLeavesNoCache() = runBlocking {
        val root = folder.newFolder()
        val cache = AiChapterCache(root)
        val input = "原始正文"
        val id = identity(input)
        val token = AiTaskToken()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                withContext(NonCancellable) { entered.complete(Unit); release.await() }
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val task = async { AiChapterProcessor(provider, cache).process(AiCacheKey.create(id), AiTextChunker.chunk(input), config, key, id, token) }
        entered.await()
        token.invalidate()
        release.complete(Unit)
        assertTrue(runCatching { task.await() }.exceptionOrNull() is CancellationException)
        assertNull(cache.read(id))
        assertFalse(root.walkTopDown().any { it.isFile })
    }
}
