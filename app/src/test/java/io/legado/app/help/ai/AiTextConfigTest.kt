package io.legado.app.help.ai

import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTextConfigTest {
    @Test
    fun configCannotRepresentOrSerializeAnApiKey() {
        val fieldNames = AiTextConfig::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(fieldNames.any { "key" in it || "secret" in it || "token" in it })
        assertFalse(GSON.toJson(AiTextConfig()).contains("api_key", true))
        assertEquals("AiApiKey([REDACTED])", AiApiKey.from("stage-b-secret-47").toString())
    }

    @Test
    fun everyOperationalChangeInvalidatesOnce() {
        val store = MemoryConfigStore(AiTextConfig(model = "a"))
        val invalidations = mutableListOf<AiConfigSnapshot>()
        val repository = AiConfigRepository(store, invalidations::add)

        val unchanged = repository.update(AiTextConfig(model = "a"))
        assertEquals(0, unchanged.generation)
        assertEquals(0, invalidations.size)

        val changed = repository.update(AiTextConfig(model = "b"))
        assertEquals(1, changed.generation)
        assertEquals(1, invalidations.size)
        assertNotEquals(unchanged.fingerprint, changed.fingerprint)
    }

    @Test
    fun unavailableKeyDisablesTheEffectiveFeature() {
        val enabled = AiTextConfig(enabled = true, model = "model")
        assertFalse(AiSecurityGate.canRun(enabled, AiKeyState.Missing))
        assertTrue(AiSecurityGate.canRun(enabled, AiKeyState.Available(AiApiKey.from("test-only"))))

        val store = MemoryConfigStore(enabled)
        val repository = AiConfigRepository(store)
        repository.disable()
        assertFalse(repository.current().config.enabled)
    }

    private class MemoryConfigStore(initial: AiTextConfig) : AiTextConfigStore {
        private var value = initial
        override fun load(): AiTextConfig = value
        override fun save(config: AiTextConfig) {
            value = config
        }
    }
}
