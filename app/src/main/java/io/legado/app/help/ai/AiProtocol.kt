package io.legado.app.help.ai

import com.google.gson.annotations.SerializedName

/**
 * AI 正文净化契约类型。
 *
 * 均为无 Android / 无数据库依赖的纯数据类型，可 JVM 单测。
 * API Key 由调用方以 [AiApiKey] 显式传入 Provider，不属于普通配置、缓存身份或日志字段。
 */

/** 单条局部编辑的类型。模型只能返回这些类别，禁止整句/整段改写。 */
enum class AiEditKind {
    /** 错别字 */
    typo,

    /** 防盗替换（同音/形近故意替换） */
    anti_theft,

    /** 标点问题 */
    punctuation,

    /** 空白/断行/粘连问题 */
    whitespace,

    /**
     * 网文“反和谐”：仅删除插入到词语内部的、白名单内的噪声字符（标点/符号/空格/零宽），
     * 要求噪声前后仍保留字母核心、字母序列与替换结果逐字一致，且噪声数量有界。
     * 不允许任意插入、改写、搬迁；模糊或重复锚点整章失败关闭。
     */
    denoise
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

/**
 * 稳定、可脱敏的失败分类：给每个校验/提供方/章节级失败一个固定枚举码。
 * UI/测试/日志只暴露此枚举（+块序号），绝不暴露正文、响应体或密钥。
 */
enum class AiFailureCode {
    /* 提供方层（来自 AiProviderException / 网络与认证） */
    AUTHENTICATION,
    RATE_LIMITED,
    NETWORK,
    TIMEOUT,
    UNSAFE_ENDPOINT,
    UNSAFE_REDIRECT,
    SERVER,
    HTTP,
    RESPONSE_TOO_LARGE,
    PROTOCOL,

    /* 校验层（局部编辑不符合契约） */
    CHUNK_ID,
    FINISH_REASON,
    TOO_MANY_EDITS,
    ANCHOR,
    RANGE,
    OVERLAP,
    SENTINEL,
    EDIT_KIND,
    CORE_LENGTH,

    /* 章节级（应用/恢复后的整体结构问题） */
    STRUCTURE,
    IDENTITY_MISMATCH,
    MISSING_IDENTITY,
    INVALID_CHUNKING,
    CACHE_INVALIDATED,
    CACHE_COMMIT,
    PREPARATION,
    INTERNAL
}

/** ANCHOR 失败的安全子类；只描述精确锚点的出现次数，不携带锚点文本。 */
enum class AiAnchorFailureKind {
    ambiguous,
    missing
}

/** 失败编辑是否会改变正文；只比较 original/replacement，不携带二者内容。 */
enum class AiEditEffect {
    noop,
    changed
}

/** ANCHOR 专用脱敏诊断。其它失败类型必须保持为 null。 */
data class AiAnchorFailureDetail(
    val kind: AiAnchorFailureKind,
    val effect: AiEditEffect
)

/**
 * 无内容失败上报入口：只在失败状态“首次落定”时携带稳定分类码、可选 1-based 块号，
 * 以及可选的 ANCHOR 脱敏子类。
 * 绝不携带异常 message、请求/响应体、正文片段或对象 toString()。
 * 由 [AiChapterCoordinator] 在状态落到 Failed 时调用一次（而非在 UI 收集里调用），
 * 避免 Activity 重组导致重复日志。生产实现写日志；JVM 测试注入记录器证明。
 */
fun interface AiFailureReporter {
    fun report(code: AiFailureCode, failedChunkIndex: Int?, anchorDetail: AiAnchorFailureDetail?)
}

/** 把提供方错误映射到统一失败分类码。 */
fun AiProviderError.toFailureCode(): AiFailureCode = when (this) {
    AiProviderError.unsafe_endpoint -> AiFailureCode.UNSAFE_ENDPOINT
    AiProviderError.unsafe_redirect -> AiFailureCode.UNSAFE_REDIRECT
    AiProviderError.authentication -> AiFailureCode.AUTHENTICATION
    AiProviderError.rate_limited -> AiFailureCode.RATE_LIMITED
    AiProviderError.server -> AiFailureCode.SERVER
    AiProviderError.http -> AiFailureCode.HTTP
    AiProviderError.timeout -> AiFailureCode.TIMEOUT
    AiProviderError.network -> AiFailureCode.NETWORK
    AiProviderError.response_too_large -> AiFailureCode.RESPONSE_TOO_LARGE
    AiProviderError.protocol -> AiFailureCode.PROTOCOL
}
