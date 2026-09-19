package io.legado.app.help.ai

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class AiProcessResult {
    object Original : AiProcessResult()
    data class Completed(val text: String, val requestCount: Int = 0) : AiProcessResult() {
        override fun toString() = "Completed(text=[REDACTED], requestCount=$requestCount)"
    }

    /** @param failedChunkIndex 仅当失败可明确归因于某一块时携带 1-based 块序号（provider 调用失败、
     * 该块输出校验失败）；预备/身份/分块/缓存失效/整章结构恢复/缓存提交/整章 internal 等
     * 章级/提交级失败为 null，RequestCount 只是请求计数，绝不能当作块号。 */
    data class Failed(
        val code: AiFailureCode,
        val failedChunkIndex: Int? = null,
        val anchorDetail: AiAnchorFailureDetail? = null
    ) : AiProcessResult()
}

/** Shared serial requests and single flight. A failed chunk discards the entire candidate.
 * Production callers inject the independent cache; the no-cache overload is an offline test seam.
 * Each validated block is staged to disk and released. No earlier body is sent as history. */
class AiChapterProcessor(private val provider: AiTextProvider, private val cache: AiChapterCache? = null) {
    companion object {
        private val requests = Mutex()
        private val state = Any()
        private val inFlight = mutableMapOf<String, CompletableDeferred<AiProcessResult>>()
        private val activeTokens = mutableMapOf<AiTaskToken, Int>()
        suspend fun <T> withRequestLock(block: suspend () -> T): T = requests.withLock { block() }

        fun invalidateAll() {
            val tokens = synchronized(state) { activeTokens.keys.toList() }
            tokens.forEach { it.invalidate() }
        }
    }

    suspend fun process(cacheKey: String, chunking: AiChunkingResult, config: AiProviderConfig,
                        apiKey: AiApiKey, identity: AiCacheIdentity? = null,
                        token: AiTaskToken = AiTaskToken(),
                        isCurrent: () -> Boolean = { true },
                        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
                        onRequest: (Int) -> Unit = {}): AiProcessResult = coroutineScope {
        val context = currentCoroutineContext()
        val job = context.job
        token.attach(job)
        synchronized(state) { activeTokens[token] = (activeTokens[token] ?: 0) + 1 }
        var owner = false
        val dedupKey = "${cache?.scope ?: "offline"}:${identity?.let(AiCacheKey::create) ?: cacheKey}:" +
            AiCacheKey.sha256(chunking.protectedText.original) + ":${config.hashCode()}"
        val shared = synchronized(state) {
            inFlight[dedupKey] ?: CompletableDeferred<AiProcessResult>().also {
                owner = true; inFlight[dedupKey] = it
            }
        }
        try {
            if (!owner) {
                val result = shared.await()
                token.checkCurrent(); context.ensureActive()
                if (!isCurrent()) throw CancellationException("ai_task_invalidated")
                return@coroutineScope result
            }
            val generation = cache?.generation()
            val result = requests.withLock {
                execute(chunking, config, apiKey, identity, token, generation, isCurrent, onProgress, onRequest)
            }
            token.checkCurrent(); context.ensureActive()
            if (!isCurrent()) throw CancellationException("ai_task_invalidated")
            shared.complete(result)
            result
        } catch (cancel: CancellationException) {
            if (owner) shared.cancel(cancel)
            throw cancel
        } catch (_: Exception) {
            AiProcessResult.Failed(AiFailureCode.INTERNAL).also { if (owner) shared.complete(it) }
        } finally {
            // No cancellable mutex acquisition in cleanup.
            synchronized(state) {
                if (owner && inFlight[dedupKey] === shared) inFlight.remove(dedupKey)
                val count = (activeTokens[token] ?: 1) - 1
                if (count == 0) activeTokens.remove(token) else activeTokens[token] = count
            }
            token.detach(job)
        }
    }

    private suspend fun execute(chunking: AiChunkingResult, config: AiProviderConfig, apiKey: AiApiKey,
                                identity: AiCacheIdentity?, token: AiTaskToken,
                                generation: Long?, isCurrent: () -> Boolean,
                                onProgress: (done: Int, total: Int) -> Unit,
                                onRequest: (Int) -> Unit): AiProcessResult {
        val context = currentCoroutineContext()
        fun checkCurrent() {
            context.ensureActive(); token.checkCurrent()
            if (!isCurrent()) throw CancellationException("ai_task_invalidated")
        }
        fun cacheCurrent() = cache == null || cache.generation() == generation
        checkCurrent()
        val original = chunking.protectedText.original
        if (!AiPlainText.isPlainTextString(original)) return AiProcessResult.Original
        if (identity != null && (AiCacheKey.sha256(original) != identity.inputTextSha256 ||
            identity.model != config.model || identity.providerType != config.type ||
            identity.serviceUrlHash != AiCacheKey.sha256(config.serviceUrl.trim()))) {
            return AiProcessResult.Failed(AiFailureCode.IDENTITY_MISMATCH)
        }
        if (cache != null && identity == null) return AiProcessResult.Failed(AiFailureCode.MISSING_IDENTITY)
        if (!cacheCurrent()) return AiProcessResult.Failed(AiFailureCode.CACHE_INVALIDATED)

        // Verify caller-supplied chunks before sending anything (also covers fake/test callers).
        var offset = 0
        var previous = ""
        chunking.chunks.forEachIndexed { index, chunk ->
            val tail = chunk.contextOnly ?: ""
            if (chunk.id != "chunk-$index" || chunk.text.isEmpty() ||
                chunk.text.codePointCount(0, chunk.text.length) > config.maxChunkChars ||
                tail.codePointCount(0, tail.length) > 200 || !previous.endsWith(tail) ||
                !chunking.protectedText.protected.startsWith(chunk.text, offset)) {
                return AiProcessResult.Failed(AiFailureCode.INVALID_CHUNKING)
            }
            offset += chunk.text.length; previous = chunk.text
        }
        if (offset != chunking.protectedText.protected.length ||
            chunking.protectedText.restore(chunking.protectedText.protected) != original) {
            return AiProcessResult.Failed(AiFailureCode.INVALID_CHUNKING)
        }
        if (cache != null && identity != null) cache.read(identity)?.let {
            checkCurrent()
            if (!cacheCurrent()) return AiProcessResult.Failed(AiFailureCode.CACHE_INVALIDATED)
            return AiProcessResult.Completed(it, 0)
        }

        val assembly = if (cache != null && identity != null) cache.begin(identity, generation!!) else null
        val offline = if (assembly == null) StringBuilder() else null
        var count = 0
        try {
            for (chunk in chunking.chunks) {
                checkCurrent()
                if (!cacheCurrent()) return AiProcessResult.Failed(AiFailureCode.CACHE_INVALIDATED)
                val output = try {
                    count++
                    onRequest(chunk.text.codePointCount(0, chunk.text.length) +
                        (chunk.contextOnly?.let { it.codePointCount(0, it.length) } ?: 0))
                    provider.processChunk(AiChunkRequest(chunk.id, chunk.text, chunk.contextOnly), config, apiKey)
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) {
                    return AiProcessResult.Failed(
                        (error as? AiProviderException)?.error?.toFailureCode() ?: AiFailureCode.INTERNAL,
                        count
                    )
                }
                checkCurrent()
                if (!cacheCurrent()) return AiProcessResult.Failed(AiFailureCode.CACHE_INVALIDATED)
                when (val result = AiOutputValidator.validateAndApply(chunk, output, chunking.protectedText.sentinels.keys)) {
                    is AiValidationResult.Invalid -> return AiProcessResult.Failed(
                        result.code,
                        count,
                        result.anchorDetail
                    )
                    is AiValidationResult.Valid -> token.withCurrent {
                        checkCurrent()
                        if (assembly != null) assembly.append(result.protectedText) else offline!!.append(result.protectedText)
                    }
                }
                // UI callbacks must not run while holding the task-token monitor.
                onProgress(count, chunking.chunks.size)
            }
            val text = chunking.protectedText.restore(assembly?.assembled() ?: offline.toString())
            if (!AiPlainText.isPlainTextString(text) ||
                AiTextChunker.protectedValues(text) != AiTextChunker.protectedValues(original)) {
                return AiProcessResult.Failed(AiFailureCode.STRUCTURE)
            }
            checkCurrent()
            if (!cacheCurrent()) return AiProcessResult.Failed(AiFailureCode.CACHE_INVALIDATED)
            if (assembly != null && !token.withCurrent { assembly.commit(text) { checkCurrent() } }) {
                return AiProcessResult.Failed(AiFailureCode.CACHE_COMMIT)
            }
            checkCurrent()
            return AiProcessResult.Completed(text, count)
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { return AiProcessResult.Failed(AiFailureCode.INTERNAL) }
        finally { assembly?.close() }
    }
}
