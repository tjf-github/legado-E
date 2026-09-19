package io.legado.app.help.ai

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AiStageCConcurrencyTest {
    private val config = AiProviderConfig("fake", "https://example.invalid", "fake")
    private val key = AiApiKey.from("test-only")

    @Test fun cancellingFollowerDoesNotCancelOwnerOrCauseSecondRequest() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                calls.incrementAndGet()
                entered.complete(Unit)
                release.await()
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val processor = AiChapterProcessor(provider)
        val input = AiTextChunker.chunk("原始正文")
        val owner = async { processor.process("follower", input, config, key) }
        entered.await()
        val follower = async { processor.process("follower", input, config, key) }
        yield()
        follower.cancelAndJoin()
        release.complete(Unit)
        assertEquals("原始正文", (owner.await() as AiProcessResult.Completed).text)
        assertEquals(1, calls.get())
    }

    @Test fun differentProcessorInstancesStillSerializeRequests() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                calls.incrementAndGet()
                entered.complete(Unit)
                release.await()
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val a = async { AiChapterProcessor(provider).process("instance-a", AiTextChunker.chunk("甲"), config, key) }
        entered.await()
        val b = async { AiChapterProcessor(provider).process("instance-b", AiTextChunker.chunk("乙"), config, key) }
        yield()
        val beforeRelease = calls.get()
        release.complete(Unit)
        assertTrue(a.await() is AiProcessResult.Completed)
        assertTrue(b.await() is AiProcessResult.Completed)
        assertEquals(1, beforeRelease)
    }

    @Test fun providerIgnoringCancellationCannotReturnCompletedAndKeyCanBeReused() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var returnedCompleted = false
        val calls = AtomicInteger()
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                if (calls.incrementAndGet() == 1) withContext(NonCancellable) {
                    entered.complete(Unit)
                    release.await()
                }
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val processor = AiChapterProcessor(provider)
        val chunking = AiTextChunker.chunk("甲")
        val first = launch {
            returnedCompleted = processor.process("reuse-cancel", chunking, config, key) is AiProcessResult.Completed
        }
        entered.await()
        first.cancel()
        release.complete(Unit)
        first.join()
        assertFalse(returnedCompleted)
        assertTrue(withTimeout(2000) { processor.process("reuse-cancel", chunking, config, key) } is AiProcessResult.Completed)
        assertEquals(2, calls.get())
    }
}
