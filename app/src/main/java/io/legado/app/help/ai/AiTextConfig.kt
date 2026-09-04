package io.legado.app.help.ai

import android.content.Context
import android.util.AtomicFile
import io.legado.app.utils.GSON
import java.io.File

/** Non-secret configuration. API keys and device consent cannot be represented here. */
data class AiTextConfig(
    val enabled: Boolean = false,
    val providerType: String = OpenAiCompatibleProvider.TYPE,
    val serviceUrl: String = "https://api.openai.com/v1",
    val model: String = "",
    val timeoutSeconds: Int = 45,
    val maxChunkChars: Int = 6000,
    val responseByteLimit: Int = 256 * 1024,
    val allowInsecureLocalHttp: Boolean = false
) {
    fun toProviderConfig(): AiProviderConfig = AiProviderConfig(
        providerType,
        serviceUrl,
        model,
        maxChunkChars,
        timeoutSeconds,
        responseByteLimit,
        allowInsecureLocalHttp
    )

    fun fingerprint(): String {
        val fields = listOf(
            CONFIG_VERSION,
            enabled.toString(),
            providerType,
            serviceUrl.trim(),
            model,
            timeoutSeconds.toString(),
            maxChunkChars.toString(),
            responseByteLimit.toString(),
            allowInsecureLocalHttp.toString()
        )
        return AiCacheKey.sha256(fields.joinToString("") { "${it.length}:$it" })
    }

    companion object {
        const val CONFIG_VERSION = "ai-config-v1"
    }
}

interface AiTextConfigStore {
    fun load(): AiTextConfig
    fun save(config: AiTextConfig)
}

/** Dedicated no-backup file; its API accepts no secret-bearing type. */
class AndroidAiTextConfigStore(context: Context) : AiTextConfigStore {
    private val file = File(context.noBackupFilesDir, "ai_text/config.json")
    private val atomicFile by lazy { AtomicFile(file) }

    override fun load(): AiTextConfig = runCatching {
        if (!file.isFile) AiTextConfig() else GSON.fromJson(file.readText(), AiTextConfig::class.java)
    }.getOrNull() ?: AiTextConfig()

    override fun save(config: AiTextConfig) {
        file.parentFile?.mkdirs()
        val stream = atomicFile.startWrite()
        try {
            stream.write(GSON.toJson(config).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            atomicFile.failWrite(stream)
            throw IllegalStateException("Unable to save AI configuration")
        }
    }
}

data class AiConfigSnapshot(
    val config: AiTextConfig,
    val generation: Long,
    val fingerprint: String = config.fingerprint()
)

/** Emits one invalidation only after a changed configuration was durably saved. */
class AiConfigRepository(
    private val store: AiTextConfigStore,
    private val onInvalidated: (AiConfigSnapshot) -> Unit = {}
) {
    private var snapshot = AiConfigSnapshot(store.load(), 0)

    @Synchronized
    fun current(): AiConfigSnapshot = snapshot

    @Synchronized
    fun update(config: AiTextConfig): AiConfigSnapshot {
        val fingerprint = config.fingerprint()
        if (fingerprint == snapshot.fingerprint) return snapshot
        store.save(config)
        return AiConfigSnapshot(config, snapshot.generation + 1, fingerprint).also {
            snapshot = it
            onInvalidated(it)
        }
    }

    @Synchronized
    fun disable(): AiConfigSnapshot = if (snapshot.config.enabled) {
        update(snapshot.config.copy(enabled = false))
    } else {
        snapshot
    }
}

object AiSecurityGate {
    fun canRun(config: AiTextConfig, keyState: AiKeyState): Boolean =
        config.enabled && keyState is AiKeyState.Available
}
