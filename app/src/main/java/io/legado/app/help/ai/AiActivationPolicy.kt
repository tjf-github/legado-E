package io.legado.app.help.ai

internal enum class AiActivationAction {
    ACTIVATE_PREPARED,
    RELOAD_CURRENT,
    WAIT_FOR_CONTENT
}

/** Decides how the reader recovers AI state after its activity was left and recreated. */
internal fun decideAiActivation(
    preparedAvailable: Boolean,
    currentLayoutAvailable: Boolean,
    reloadIfMissing: Boolean
): AiActivationAction = when {
    preparedAvailable -> AiActivationAction.ACTIVATE_PREPARED
    reloadIfMissing && currentLayoutAvailable -> AiActivationAction.RELOAD_CURRENT
    else -> AiActivationAction.WAIT_FOR_CONTENT
}
