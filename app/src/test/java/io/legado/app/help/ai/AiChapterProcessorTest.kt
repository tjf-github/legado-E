package io.legado.app.help.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AiChapterProcessorTest {
    private val config = AiProviderConfig("fake", "https://example.invalid", "fake-model")

    @Test
    fun providerCallsAreSerialAcrossDifferentKeys() = runBlocking {
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val releaseFirst = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig): AiChunkOutput {
                val now = active.incrementAndGet()
                maxActive.updateAndGet { maxOf(it, now) }
                entered.complete(Unit)
                releaseFirst.await()
                active.decrementAndGet()
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val processor = AiChapterProcessor(provider)
        val first = async { processor.process("a", AiTextChunker.chunk("甲", nonce = "a"), config) }
        entered.await()
        val second = async { processor.process("b", AiTextChunker.chunk("乙", nonce = "b"), config) }
        releaseFirst.complete(Unit)

        assertTrue(first.await() is AiProcessResult.Completed)
        assertTrue(second.await() is AiProcessResult.Completed)
        assertEquals(1, maxActive.get())
    }

    @Test
    fun sameKeyIsSingleFlight() = runBlocking {
        val calls = AtomicInteger()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig): AiChunkOutput {
                calls.incrementAndGet()
                entered.complete(Unit)
                release.await()
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val processor = AiChapterProcessor(provider)
        val chunking = AiTextChunker.chunk("甲乙", nonce = "single")
        val first = async { processor.process("same", chunking, config) }
        entered.await()
        val second = async { processor.process("same", chunking, config) }
        yield()
        release.complete(Unit)

        assertEquals(first.await(), second.await())
        assertEquals(1, calls.get())
    }

    @Test
    fun invalidChunkStopsLaterCallsAndReturnsNoPartialText() = runBlocking {
        val calls = AtomicInteger()
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig): AiChunkOutput {
                val call = calls.incrementAndGet()
                return if (call == 2) {
                    AiChunkOutput("wrong", emptyList())
                } else {
                    AiChunkOutput(request.chunkId, emptyList())
                }
            }
        }
        val processor = AiChapterProcessor(provider)
        val chunking = AiTextChunker.chunk("甲乙丙丁戊己", maxChunkCodePoints = 2, nonce = "failure")

        val result = processor.process("failure", chunking, config)
        assertTrue(result is AiProcessResult.Failed)
        assertEquals(2, calls.get())
    }

    @Test
    fun cancellationDoesNotProduceCompletedResult() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig): AiChunkOutput {
                entered.complete(Unit)
                never.await()
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val processor = AiChapterProcessor(provider)
        val job = async { processor.process("cancel", AiTextChunker.chunk("甲", nonce = "cancel"), config) }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
}
