package io.legado.app.help.ai

import com.google.gson.annotations.SerializedName

/**
 * AI 正文净化契约类型。
 *
 * 均为无 Android / 无数据库依赖的纯数据类型，可 JVM 单测。
 * API Key 由调用方以 [AiApiKey] 显式传入 Provider，不属于普通配置、缓存身份或日志字段。
 */

/** 单条局部编辑的类型。模型只能返回这四类，禁止整句/整段改写。 */
enum class AiEditKind {
    /** 错别字 */
    typo,

    /** 防盗替换（同音/形近故意替换） */
    anti_theft,

    /** 标点问题 */
    punctuation,

    /** 空白/断行/粘连问题 */
    whitespace
}

/**
 * 模型返回的某一块局部编辑。
 * @param start 当前块文本内按 Unicode code point 计的起始偏移（含）。
 * @param end 结束偏移（不含）。
 * @param original 必须与 [start, end) 区间的原文逐值相等（本机锚定校验）。
 * @param replacement 替换文本。
 * @param kind 编辑类型。
 */
data class AiEdit(
    val start: Int,
    val end: Int,
    val original: String,
    val replacement: String,
    val kind: AiEditKind
) {
    override fun toString(): String = "AiEdit(start=$start, end=$end, kind=$kind, text=[REDACTED])"
}

/** 最终原因。长度截断/异常结束会导致整块失败。 */
enum class AiFinishReason {
    stop,
    length,
    content_filter,
    tool_calls,
    other
}

/** Provider 归一化后的结构化输出。 */
data class AiChunkOutput(
    @SerializedName("chunk_id")
    val chunkId: String,
    val edits: List<AiEdit>,
    val warnings: List<String> = emptyList(),
    val finishReason: AiFinishReason = AiFinishReason.stop
) {
    override fun toString(): String =
        "AiChunkOutput(chunkId=$chunkId, editCount=${edits.size}, warningCount=${warnings.size}, finishReason=$finishReason)"
}

/** 发送给 Provider 的单块请求。text 为哨兵化后的块正文，context_only 只读、不参与编辑。 */
data class AiChunkRequest(
    val chunkId: String,
    val text: String,
    val contextOnly: String?
) {
    override fun toString(): String =
        "AiChunkRequest(chunkId=$chunkId, textCodePoints=${text.codePointCount(0, text.length)}, " +
            "contextCodePoints=${contextOnly?.codePointCount(0, contextOnly.length) ?: 0})"
}

/** Provider 运行所需的最小配置（阶段 A 仅做契约，不含真实网络）。 */
data class AiProviderConfig(
    val type: String,
    val serviceUrl: String,
    val model: String,
    val maxChunkChars: Int = 6000,
    val timeoutSeconds: Int = 45,
    val responseByteLimit: Int = 256 * 1024,
    val allowInsecureLocalHttp: Boolean = false
)

/**
 * 避免密钥被 data class/toString/异常插值意外打印的最小包装。
 * Provider 只能在构造认证头的最短作用域内读取明文。
 */
class AiApiKey private constructor(private val value: String) {
    internal fun <T> use(block: (String) -> T): T = block(value)

    override fun toString(): String = "AiApiKey([REDACTED])"

    companion object {
        fun from(value: String): AiApiKey {
            require(value.isNotBlank()) { "API key must not be blank" }
            return AiApiKey(value)
        }
    }
}

/** 供应商无关的挂起接口。阶段 A 用 Fake Provider 实现。 */
interface AiTextProvider {
    /** 处理单块，返回归一化编辑列表。失败由异常或校验层表达。 */
    suspend fun processChunk(
        request: AiChunkRequest,
        config: AiProviderConfig,
        apiKey: AiApiKey
    ): AiChunkOutput
}
