package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiCacheKeyTest {
    private val base = AiCacheIdentity(
        bookUrlHash = "book",
        chapterUrlHash = "chapter",
        chapterIndex = 2,
        inputTextSha256 = "input",
        promptVersion = "prompt-v1",
        providerType = "chat-completions",
        serviceUrlHash = "service",
        model = "model-a",
        chunkingVersion = "chunk-v1",
        validationVersion = "validator-v1",
        configGeneration = 3,
        configFingerprint = "config-a"
    )

    @Test
    fun keyIsDeterministicAndEveryIdentityFieldMatters() {
        assertEquals(AiCacheKey.create(base), AiCacheKey.create(base))
        assertNotEquals(AiCacheKey.create(base), AiCacheKey.create(base.copy(model = "model-b")))
        assertNotEquals(AiCacheKey.create(base), AiCacheKey.create(base.copy(inputTextSha256 = "changed")))
        assertNotEquals(AiCacheKey.create(base), AiCacheKey.create(base.copy(configGeneration = 4)))
        assertNotEquals(AiCacheKey.create(base), AiCacheKey.create(base.copy(configFingerprint = "config-b")))
    }

    @Test
    fun identityHasNoApiKeyFieldAndDigestDoesNotExposeValues() {
        val names = AiCacheIdentity::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(names.any { "apikey" in it || "api_key" in it })
        assertFalse(AiCacheKey.create(base).contains(base.model))
    }
}
