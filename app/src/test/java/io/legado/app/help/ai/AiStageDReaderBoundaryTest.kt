package io.legado.app.help.ai

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookContent
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AiStageDReaderBoundaryTest {
    private val original = BookContent(true, listOf("合成原文"), emptyList())
    private fun input() = AiChapterScope(Book(bookUrl = "book", origin = "https://source.invalid", type = BookType.text),
        "chapter", 0, original, true, true, true, AiApiKey.from("test-key"),
        AiTextConfig(enabled = true, model = "fake").toProviderConfig())

    @Test fun revokedEligibilityInvalidatesAlreadyReadyCandidate() = runBlocking {
        for (reason in 0..3) {
            val coordinator = AiChapterCoordinator(AiChapterRunner { _, _, _, _ -> AiProcessResult.Completed("候选", 1) }, this)
            val scope = input()
            coordinator.resolve(original, scope, true)
            yield()
            coordinator.showAi()
            val denied = when (reason) {
                0 -> null
                1 -> scope.copy(bookEnabled = false)
                2 -> scope.copy(deviceConsentConfirmed = false)
                else -> scope.copy(allowNetwork = false)
            }
            assertFalse(coordinator.resolve(original, denied, true).isAi)
            assertFalse("revoked scope must discard candidate", coordinator.hasCompleted)
            assertEquals(AiDisplaySource.Original, coordinator.displaySource.value)
            coordinator.reset()
        }
    }

    @Test fun changedConfigurationCannotReuseOldCandidate() = runBlocking {
        val coordinator = AiChapterCoordinator(AiChapterRunner { scope, _, _, _ ->
            AiProcessResult.Completed(scope.config.model, 1)
        }, this)
        val old = input()
        coordinator.resolve(original, old, true)
        yield()
        coordinator.showAi()
        val changed = old.copy(config = old.config.copy(model = "new-model"))
        assertFalse(coordinator.resolve(original, changed, true).isAi)
        yield()
        coordinator.showAi()
        assertEquals("new-model", coordinator.resolve(original, changed, true).content.textList.single())
        coordinator.reset()
    }

    @Test fun nullCurrentScopeCancelsIgnoringCancellationRunner() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val coordinator = AiChapterCoordinator(AiChapterRunner { _, _, _, _ ->
            withContext(NonCancellable) { entered.complete(Unit); release.await() }
            AiProcessResult.Completed("迟到", 1)
        }, this)
        coordinator.resolve(original, input(), true)
        entered.await()
        coordinator.resolve(original, null, true)
        release.complete(Unit)
        yield()
        assertFalse(coordinator.hasCompleted)
        assertEquals(AiChapterUiState.Idle, coordinator.state.value)
        coordinator.reset()
    }

    @Test fun cancelAfterSelectionReturnsToOriginal() = runBlocking {
        val coordinator = AiChapterCoordinator(AiChapterRunner { _, _, _, _ -> AiProcessResult.Completed("候选", 1) }, this)
        coordinator.resolve(original, input(), true)
        yield()
        coordinator.showAi()
        coordinator.cancel()
        assertEquals(AiDisplaySource.Original, coordinator.displaySource.value)
        assertFalse(coordinator.hasCompleted)
        coordinator.reset()
    }

    @Test fun scopeDiagnosticsNeverExposeBookOrBody() {
        val scope = input()
        assertFalse(scope.toString().contains("合成原文"))
        assertFalse(scope.toString().contains("source.invalid"))
    }
}
