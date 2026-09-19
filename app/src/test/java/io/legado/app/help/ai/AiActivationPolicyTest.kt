package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiActivationPolicyTest {

    @Test
    fun missingPreparedStateWithCachedLayoutReloadsInsteadOfSilentlyReturning() {
        assertEquals(
            AiActivationAction.RELOAD_CURRENT,
            decideAiActivation(
                preparedAvailable = false,
                currentLayoutAvailable = true,
                reloadIfMissing = true
            )
        )
    }

    @Test
    fun normalPageChangeDoesNotReloadWhileContentIsStillPreparing() {
        assertEquals(
            AiActivationAction.WAIT_FOR_CONTENT,
            decideAiActivation(
                preparedAvailable = false,
                currentLayoutAvailable = false,
                reloadIfMissing = false
            )
        )
    }
}
