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
