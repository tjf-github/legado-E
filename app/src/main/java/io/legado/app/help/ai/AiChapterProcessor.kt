package io.legado.app.help.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class AiProcessResult {
    data class Completed(val text: String, val requestCount: Int) : AiProcessResult() {
        override fun toString(): String = "Completed(text=[REDACTED], requestCount=$requestCount)"
    }
    data class Failed(val reason: String, val requestCount: Int) : AiProcessResult()
}

/**
 * 阶段 A 离线编排器：全局串行、同键单飞，任一块失败即停止。
 * 真实文件缓存、任务令牌和 UI 状态在后续阶段接入。
 */
class AiChapterProcessor(
    private val provider: AiTextProvider
) {
    private val requestMutex = Mutex()
    private val stateMutex = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<AiProcessResult>>()

    suspend fun process(
        cacheKey: String,
        chunking: AiChunkingResult,
        config: AiProviderConfig,
        apiKey: AiApiKey
    ): AiProcessResult {
        var owner = false
        val shared = stateMutex.withLock {
            inFlight[cacheKey] ?: CompletableDeferred<AiProcessResult>().also {
                inFlight[cacheKey] = it
                owner = true
            }
        }
        if (!owner) return shared.await()

        try {
            val result = requestMutex.withLock { execute(chunking, config, apiKey) }
            shared.complete(result)
            return result
        } catch (error: CancellationException) {
            shared.cancel(error)
            throw error
        } catch (_: Throwable) {
            val result = AiProcessResult.Failed("processor_failure", 0)
            shared.complete(result)
            return result
        } finally {
            stateMutex.withLock {
                if (inFlight[cacheKey] === shared) inFlight.remove(cacheKey)
            }
        }
    }

    private suspend fun execute(
        chunking: AiChunkingResult,
        config: AiProviderConfig,
        apiKey: AiApiKey
    ): AiProcessResult {
        val processed = ArrayList<String>(chunking.chunks.size)
        var requestCount = 0
        for (chunk in chunking.chunks) {
            val output = try {
                requestCount++
                provider.processChunk(
                    AiChunkRequest(chunk.id, chunk.text, chunk.contextOnly),
                    config,
                    apiKey
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                return AiProcessResult.Failed(
                    (error as? AiProviderException)?.error?.name ?: "provider_failure",
                    requestCount
                )
            }
            when (
                val validation = AiOutputValidator.validateAndApply(
                    chunk,
                    output,
                    chunking.protectedText.sentinels.keys
                )
            ) {
                is AiValidationResult.Invalid -> return AiProcessResult.Failed(
                    validation.reason,
                    requestCount
                )
                is AiValidationResult.Valid -> processed += validation.protectedText
            }
        }

        return try {
            AiProcessResult.Completed(
                chunking.protectedText.restore(processed.joinToString("")),
                requestCount
            )
        } catch (error: IllegalArgumentException) {
            AiProcessResult.Failed(error.message ?: "protected value changed", requestCount)
        }
    }
}
