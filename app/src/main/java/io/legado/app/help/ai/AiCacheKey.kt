package io.legado.app.help.ai

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class AiCacheIdentity(
    val bookUrlHash: String,
    val chapterUrlHash: String,
    val chapterIndex: Int,
    val inputTextSha256: String,
    val promptVersion: String,
    val providerType: String,
    val serviceUrlHash: String,
    val model: String,
    val chunkingVersion: String,
    val validationVersion: String,
    val configGeneration: Long,
    val configFingerprint: String = ""
)

object AiCacheKey {
    fun create(identity: AiCacheIdentity): String {
        val fields = listOf(
            identity.bookUrlHash,
            identity.chapterUrlHash,
            identity.chapterIndex.toString(),
            identity.inputTextSha256,
            identity.promptVersion,
            identity.providerType,
            identity.serviceUrlHash,
            identity.model,
            identity.chunkingVersion,
            identity.validationVersion,
            identity.configGeneration.toString(),
            identity.configFingerprint
        )
        val canonical = fields.joinToString("") { "${it.length}:$it" }
        return sha256(canonical)
    }

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

/** 参与缓存身份的版本：Prompt / 分块 / 校验任一变化都会产生新的缓存键。 */
object AiVersions {
    const val PROMPT = "ai-prompt-v4"
    const val CHUNKING = "ai-chunk-v2"
    const val VALIDATION = "ai-validate-v6"
}

/**
 * 统一构造缓存身份。API Key 不进入身份；正文、配置、分块与校验版本变化会使键自然失效。
 * [config] 的 serviceUrl 仅存 SHA-256（与调用方配置脱敏），[configFingerprint] 透传配置指纹。
 */
object AiChapterIdentity {
    fun bookUrlHash(bookUrl: String): String = AiCacheKey.sha256(bookUrl)
    fun chapterUrlHash(chapterUrl: String): String = AiCacheKey.sha256(chapterUrl)

    fun build(
        bookUrl: String,
        chapterUrl: String,
        chapterIndex: Int,
        inputText: String,
        config: AiProviderConfig,
        configGeneration: Long,
        configFingerprint: String,
        promptVersion: String = AiVersions.PROMPT,
        chunkingVersion: String = AiVersions.CHUNKING,
        validationVersion: String = AiVersions.VALIDATION
    ): AiCacheIdentity = AiCacheIdentity(
        bookUrlHash = bookUrlHash(bookUrl),
        chapterUrlHash = chapterUrlHash(chapterUrl),
        chapterIndex = chapterIndex,
        inputTextSha256 = AiCacheKey.sha256(inputText),
        promptVersion = promptVersion,
        providerType = config.type,
        serviceUrlHash = AiCacheKey.sha256(config.serviceUrl.trim()),
        model = config.model,
        chunkingVersion = "$chunkingVersion:max=${config.maxChunkChars}:context=200",
        validationVersion = validationVersion,
        configGeneration = configGeneration,
        configFingerprint = configFingerprint
    )
}
