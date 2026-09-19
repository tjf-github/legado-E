package io.legado.app.help.ai

import android.content.Context
import android.util.AtomicFile
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.utils.GSON
import java.io.File
import java.net.URI

/** 服务地址规范化（纯函数，可 JVM 单测）。
 * 只把 scheme 与 authority 归一（统一小写、去掉路径尾斜杠），用于“本设备 + 当前服务地址”绑定，
 * 不参与缓存/日志字段。 */
object AiConsentPolicy {
    fun normalize(url: String): String {
        return runCatching {
            val endpoint = URI(AiEndpointPolicy.chatCompletionsUrl(
                AiTextConfig(serviceUrl = url, allowInsecureLocalHttp = true).toProviderConfig()))
            require(endpoint.port == -1 || endpoint.port in 1..65535)
            val scheme = endpoint.scheme.lowercase()
            val port = if ((scheme == "https" && endpoint.port == 443) ||
                (scheme == "http" && endpoint.port == 80)) -1 else endpoint.port
            URI(scheme, null, endpoint.host.lowercase(), port, endpoint.path, null, null)
                .normalize().toASCIIString()
        }.getOrDefault("")
    }

    fun isConfirmed(stored: String?, currentServiceUrl: String): Boolean =
        stored != null && stored.isNotEmpty() && normalize(currentServiceUrl) == stored
}

/** 设备外发同意的持久化接口。明文只保存“规范化后的服务地址”，不含任何密钥或正文。 */
interface AiDeviceConsentStore {
    fun read(): String?
    fun write(normalizedServiceUrl: String)
    fun clear()
}

/** 应用私有、禁止备份的存储：`noBackupFilesDir/ai_text/device_consent.json`。
 * 只解决跨设备/恢复备份后不沿用授权的问题；同设备恢复书架后由调用方主动清空，见
 * [AiReaderAccess.revokeDeviceConsent]。 */
class AndroidAiDeviceConsentStore(context: Context) : AiDeviceConsentStore {
    private val file = File(context.noBackupFilesDir, "ai_text/device_consent.json")
    private val atomicFile by lazy { AtomicFile(file) }

    override fun read(): String? = if (file.isFile) runCatching {
        JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
            .get("url")?.asString
    }.getOrNull() else null

    override fun write(normalizedServiceUrl: String) {
        file.parentFile?.mkdirs()
        val stream = atomicFile.startWrite()
        try {
            val json = JsonObject().apply { addProperty("url", normalizedServiceUrl) }
            stream.write(GSON.toJson(json).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    override fun clear() {
        atomicFile.delete()
    }
}

/** 设备级外发授权：绑定规范化后的当前服务地址。默认未确认；确认、变更地址、恢复/迁移后需重确认。 */
class AiDeviceConsent(private val store: AiDeviceConsentStore) {
    fun isConfirmed(serviceUrl: String): Boolean = AiConsentPolicy.isConfirmed(store.read(), serviceUrl)
    fun confirm(serviceUrl: String) {
        val normalized = AiConsentPolicy.normalize(serviceUrl)
        require(normalized.isNotEmpty()) { "invalid_service_address" }
        store.write(normalized)
    }
    fun clear() = store.clear()
}
