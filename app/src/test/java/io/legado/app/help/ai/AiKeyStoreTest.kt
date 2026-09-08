package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiKeyStoreTest {
    @Test
    fun encryptedRoundTripNeverStoresPlainText() {
        val values = MemoryEncryptedStore()
        val keyStore = AiKeyStore(ReversingCipher(), values) {}

        val saved = keyStore.save(AiApiKey.from("stage-b-secret-47"))

        assertTrue(saved is AiKeyState.Available)
        assertFalse(values.value!!.toString(Charsets.UTF_8).contains("stage-b-secret-47"))
        val loaded = keyStore.load() as AiKeyState.Available
        assertEquals("stage-b-secret-47", loaded.key.use { it })
        assertFalse(loaded.toString().contains("stage-b-secret-47"))
    }

    @Test
    fun corruptionClearsCiphertextAndRequiresReentry() {
        val values = MemoryEncryptedStore(byteArrayOf(1, 2, 3))
        val cipher = ReversingCipher(failDecrypt = true)
        var disabled = false
        val state = AiKeyStore(cipher, values) { disabled = true }.load()

        assertEquals(AiKeyState.NeedsReentry, state)
        assertEquals(null, values.value)
        assertTrue(cipher.deleted)
        assertTrue(disabled)
    }

    @Test
    fun unsupportedPlatformCannotPersistAKey() {
        val values = MemoryEncryptedStore()
        var disabled = false
        val state = AiKeyStore(UnsupportedCipher(), values) { disabled = true }
            .save(AiApiKey.from("stage-b-secret-47"))

        assertEquals(AiKeyState.Unsupported, state)
        assertEquals(null, values.value)
        assertTrue(disabled)
    }

    private class MemoryEncryptedStore(var value: ByteArray? = null) : AiEncryptedValueStore {
        override fun read(): ByteArray? = value
        override fun write(value: ByteArray) {
            this.value = value.copyOf()
        }
        override fun delete() {
            value = null
        }
    }

    private class ReversingCipher(private val failDecrypt: Boolean = false) : AiSecretCipher {
        var deleted = false
        override val isSupported = true
        override fun encrypt(plainText: ByteArray): ByteArray = plainText.reversedArray().map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun decrypt(cipherText: ByteArray): ByteArray {
            if (failDecrypt) error("corrupt")
            return cipherText.map { (it.toInt() xor 0x5a).toByte() }.toByteArray().reversedArray()
        }
        override fun deleteKey() {
            deleted = true
        }
    }

    private class UnsupportedCipher : AiSecretCipher {
        override val isSupported = false
        override fun encrypt(plainText: ByteArray): ByteArray = error("unsupported")
        override fun decrypt(cipherText: ByteArray): ByteArray = error("unsupported")
        override fun deleteKey() = Unit
    }
}
