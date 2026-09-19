package io.legado.app.help.ai

import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stage C entry point; Stage D owns UI/navigation and supplies the per-chapter lifetime token.
 * This class never modifies BookContent or the original cache. Device consent must be verified
 * for the current service address by the caller; both permission flags default to false. */
class AiChapterService(provider: AiTextProvider, cache: AiChapterCache,
                       private val configs: AiConfigRepository) {
    private val processor = AiChapterProcessor(provider, cache)

    suspend fun process(book: Book, chapterUrl: String, chapterIndex: Int, content: BookContent,
                        apiKey: AiApiKey, token: AiTaskToken,
                        bookEnabled: Boolean = false, deviceConsentConfirmed: Boolean = false,
                        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
                        isCurrent: () -> Boolean = { true },
                        onRequest: (Int) -> Unit = {}): AiProcessResult =
        withContext(Dispatchers.IO) {
            val snapshot = configs.current()
            if (!snapshot.config.enabled || !bookEnabled || !deviceConsentConfirmed ||
                !AiPlainText.isAiPlainText(book, content)) return@withContext AiProcessResult.Original
            try {
                token.checkCurrent()
                val config = snapshot.config.toProviderConfig()
                val input = content.textList.joinToString("\n")
                val identity = AiChapterIdentity.build(book.bookUrl, chapterUrl, chapterIndex,
                    input, config, snapshot.generation, snapshot.fingerprint)
                val chunks = AiTextChunker.chunk(input, config.maxChunkChars)
                processor.process(
                    AiCacheKey.create(identity), chunks, config, apiKey, identity, token,
                    isCurrent = { configs.isCurrent(snapshot) && isCurrent() },
                    onProgress = onProgress,
                    onRequest = onRequest
                )
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { AiProcessResult.Failed(AiFailureCode.PREPARATION) }
        }
}
