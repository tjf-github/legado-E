package io.legado.app.help.ai

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.annotation.RequiresApi
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed class AiKeyState {
    data object Missing : AiKeyState()
    data object Unsupported : AiKeyState()
    data object NeedsReentry : AiKeyState()
    class Available(val key: AiApiKey) : AiKeyState() {
        override fun toString(): String = "Available([REDACTED])"
    }
}

interface AiEncryptedValueStore {
    fun read(): ByteArray?
    fun write(value: ByteArray)
    fun delete()
}

interface AiSecretCipher {
    val isSupported: Boolean
    fun encrypt(plainText: ByteArray): ByteArray
    fun decrypt(cipherText: ByteArray): ByteArray
    fun deleteKey()
}

class AiKeyStore(
    private val cipher: AiSecretCipher,
    private val store: AiEncryptedValueStore,
    private val onKeyUnavailable: () -> Unit
) {
    fun save(apiKey: AiApiKey): AiKeyState {
        if (!cipher.isSupported) {
            onKeyUnavailable()
            return AiKeyState.Unsupported
        }
        return try {
            val plain = apiKey.use { it.toByteArray(Charsets.UTF_8) }
            try {
                store.write(cipher.encrypt(plain))
            } finally {
                plain.fill(0)
            }
            load()
        } catch (_: Exception) {
            invalidate()
        }
    }

    fun load(): AiKeyState {
        if (!cipher.isSupported) {
            onKeyUnavailable()
            return AiKeyState.Unsupported
        }
        val encrypted = try {
            store.read()
        } catch (_: Exception) {
            return invalidate()
        } ?: run {
            onKeyUnavailable()
            return AiKeyState.Missing
        }
        return try {
            val plain = cipher.decrypt(encrypted)
            try {
                AiKeyState.Available(AiApiKey.from(plain.toString(Charsets.UTF_8)))
            } finally {
                plain.fill(0)
            }
        } catch (_: Exception) {
            invalidate()
        }
    }

    fun clear(): AiKeyState {
        runCatching { store.delete() }
        runCatching { cipher.deleteKey() }
        onKeyUnavailable()
        return if (cipher.isSupported) AiKeyState.Missing else AiKeyState.Unsupported
    }

    private fun invalidate(): AiKeyState {
        runCatching { store.delete() }
        runCatching { cipher.deleteKey() }
        onKeyUnavailable()
        return AiKeyState.NeedsReentry
    }
}

class NoBackupAiEncryptedValueStore(context: Context) : AiEncryptedValueStore {
    private val file = File(context.noBackupFilesDir, "ai_text/api_key.bin")
    private val atomicFile by lazy { AtomicFile(file) }

    override fun read(): ByteArray? = if (file.isFile) atomicFile.readFully() else null

    override fun write(value: ByteArray) {
        file.parentFile?.mkdirs()
        val stream = atomicFile.startWrite()
        try {
            stream.write(value)
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    override fun delete() {
        atomicFile.delete()
    }
}

@RequiresApi(Build.VERSION_CODES.M)
class AndroidKeystoreAesGcmCipher(
    private val alias: String = DEFAULT_ALIAS
) : AiSecretCipher {
    override val isSupported: Boolean = true

    override fun encrypt(plainText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(plainText)
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(FORMAT_VERSION)
                output.writeInt(cipher.iv.size)
                output.write(cipher.iv)
                output.writeInt(encrypted.size)
                output.write(encrypted)
            }
            bytes.toByteArray()
        }
    }

    override fun decrypt(cipherText: ByteArray): ByteArray {
        val (iv, encrypted) = decode(cipherText)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD)
        return cipher.doFinal(encrypted)
    }

    override fun deleteKey() {
        keyStore().deleteEntry(alias)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = keyStore()
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private fun getExistingKey(): SecretKey =
        keyStore().getKey(alias, null) as? SecretKey ?: throw IllegalStateException("Missing key")

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun decode(value: ByteArray): Pair<ByteArray, ByteArray> =
        DataInputStream(ByteArrayInputStream(value)).use { input ->
            require(input.readInt() == FORMAT_VERSION)
            val ivSize = input.readInt()
            require(ivSize in 12..32)
            val iv = ByteArray(ivSize).also(input::readFully)
            val encryptedSize = input.readInt()
            require(encryptedSize in 16..MAX_ENCRYPTED_BYTES)
            val encrypted = ByteArray(encryptedSize).also(input::readFully)
            require(input.read() == -1)
            iv to encrypted
        }

    companion object {
        private const val DEFAULT_ALIAS = "legado.ai_text.api_key.v1"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION = 1
        private const val MAX_ENCRYPTED_BYTES = 16 * 1024
        private val AAD = "legado-ai-key-v1".toByteArray(Charsets.UTF_8)
    }
}

object AndroidAiKeyStore {
    fun create(context: Context, onKeyUnavailable: () -> Unit): AiKeyStore {
        val cipher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AndroidKeystoreAesGcmCipher()
        } else {
            UnsupportedAiSecretCipher
        }
        return AiKeyStore(cipher, NoBackupAiEncryptedValueStore(context), onKeyUnavailable)
    }
}

private object UnsupportedAiSecretCipher : AiSecretCipher {
    override val isSupported: Boolean = false
    override fun encrypt(plainText: ByteArray): ByteArray = throw UnsupportedOperationException()
    override fun decrypt(cipherText: ByteArray): ByteArray = throw UnsupportedOperationException()
    override fun deleteKey() = Unit
}
