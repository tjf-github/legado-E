package io.legado.app.help.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AiChapterProcessorTest {
    private val config = AiProviderConfig("fake", "https://example.invalid", "fake-model")
    private val apiKey = AiApiKey.from("test-only-key")

    @Test
    fun providerCallsAreSerialAcrossDifferentKeys() = runBlocking {
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val releaseFirst = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                val now = active.incrementAndGet()
                maxActive.updateAndGet { maxOf(it, now) }
                entered.complete(Unit)
                releaseFirst.await()
                active.decrementAndGet()
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val processor = AiChapterProcessor(provider)
        val first = async { processor.process("a", AiTextChunker.chunk("甲", nonce = "a"), config, apiKey) }
        entered.await()
        val second = async { processor.process("b", AiTextChunker.chunk("乙", nonce = "b"), config, apiKey) }
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
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                calls.incrementAndGet()
                entered.complete(Unit)
                release.await()
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val processor = AiChapterProcessor(provider)
        val chunking = AiTextChunker.chunk("甲乙", nonce = "single")
        val first = async { processor.process("same", chunking, config, apiKey) }
        entered.await()
        val second = async { processor.process("same", chunking, config, apiKey) }
        yield()
        release.complete(Unit)

        assertEquals(first.await(), second.await())
        assertEquals(1, calls.get())
    }

    @Test
    fun invalidChunkStopsLaterCallsAndReturnsNoPartialText() = runBlocking {
        val calls = AtomicInteger()
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
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

        val result = processor.process("failure", chunking, config, apiKey)
        assertTrue(result is AiProcessResult.Failed)
        assertEquals(2, calls.get())
    }

    @Test
    fun cancellationDoesNotProduceCompletedResult() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                entered.complete(Unit)
                never.await()
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val processor = AiChapterProcessor(provider)
        val job = async { processor.process("cancel", AiTextChunker.chunk("甲", nonce = "cancel"), config, apiKey) }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test
    fun arbitraryProviderMessageIsNotExposed() = runBlocking {
        val provider = object : AiTextProvider {
            override suspend fun processChunk(
                request: AiChunkRequest,
                config: AiProviderConfig,
                apiKey: AiApiKey
            ): AiChunkOutput = error("stage-b-secret-47 private chapter text")
        }
        val result = AiChapterProcessor(provider).process(
            "safe-error",
            AiTextChunker.chunk("正文", nonce = "safe"),
            config,
            apiKey
        ) as AiProcessResult.Failed

        // 任意提供方异常只暴露稳定分类码，绝不泄露其 message。
        assertEquals(AiFailureCode.INTERNAL, result.code)
    }

    @Test
    fun providerFailureCarriesOneBasedChunkIndex() = runBlocking {
        var calls = 0
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                calls++
                if (calls == 2) throw AiProviderException(AiProviderError.network)
                return AiChunkOutput(request.chunkId, emptyList())
            }
        }
        val result = AiChapterProcessor(provider).process(
            "chunk-idx", AiTextChunker.chunk("甲乙丙丁戊己", maxChunkCodePoints = 2, nonce = "cidx"), config, apiKey
        ) as AiProcessResult.Failed
        // 第 2 块请求失败，失败可明确归因于该块，应携带 1-based 块序号。
        assertEquals(AiFailureCode.NETWORK, result.code)
        assertEquals(2, result.failedChunkIndex)
    }

    @Test
    fun chunkOutputValidationFailureCarriesOneBasedChunkIndex() = runBlocking {
        var calls = 0
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput {
                calls++
                return AiChunkOutput(if (calls == 2) "wrong" else request.chunkId, emptyList())
            }
        }
        val result = AiChapterProcessor(provider).process(
            "chunk-val", AiTextChunker.chunk("甲乙丙丁戊己", maxChunkCodePoints = 2, nonce = "cval"), config, apiKey
        ) as AiProcessResult.Failed
        // 第 2 块输出校验失败（chunk_id 不符），应携带该块序号。
        assertEquals(AiFailureCode.CHUNK_ID, result.code)
        assertEquals(2, result.failedChunkIndex)
    }

    @Test
    fun anchorFailureDetailSurvivesProcessorBoundary() = runBlocking {
        val provider = object : AiTextProvider {
            override suspend fun processChunk(
                request: AiChunkRequest,
                config: AiProviderConfig,
                apiKey: AiApiKey
            ) = AiChunkOutput(
                request.chunkId,
                listOf(AiEdit(0, 1, "丙", "丙", AiEditKind.typo))
            )
        }
        val result = AiChapterProcessor(provider).process(
            "anchor-detail",
            AiTextChunker.chunk("甲乙", nonce = "anchor-detail"),
            config,
            apiKey
        ) as AiProcessResult.Failed

        assertEquals(AiFailureCode.ANCHOR, result.code)
        assertEquals(1, result.failedChunkIndex)
        assertEquals(
            AiAnchorFailureDetail(AiAnchorFailureKind.missing, AiEditEffect.noop),
            result.anchorDetail
        )
    }

    @Test
    fun wholeChapterStructureFailureCarriesNoChunkIndex() = runBlocking {
        // 应用编辑后整章不再是纯文本（<p> 标签），属章节级 STRUCTURE 失败，不得携带块号。
        val provider = object : AiTextProvider {
            override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig, apiKey: AiApiKey): AiChunkOutput =
                AiChunkOutput(request.chunkId, listOf(AiEdit(1, 2, " ", "", AiEditKind.whitespace)))
        }
        val result = AiChapterProcessor(provider).process(
            "structure", AiTextChunker.chunk("< p>", nonce = "structure"), config, apiKey
        ) as AiProcessResult.Failed
        assertEquals(AiFailureCode.STRUCTURE, result.code)
        assertNull(result.failedChunkIndex)
    }
}
