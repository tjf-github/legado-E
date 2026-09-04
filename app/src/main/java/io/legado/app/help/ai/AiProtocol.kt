package io.legado.app.help.ai

/**
 * AI 正文净化——阶段 A 契约类型。
 *
 * 均为无 Android / 无数据库依赖的纯数据类型，可 JVM 单测。
 * 阶段 A 只固化「局部编辑列表」协议与 Provider 边界；真实 HTTP/Keystore/翻译在阶段 B。
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
)

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
    val chunkId: String,
    val edits: List<AiEdit>,
    val warnings: List<String> = emptyList(),
    val finishReason: AiFinishReason = AiFinishReason.stop
)

/** 发送给 Provider 的单块请求。text 为哨兵化后的块正文，context_only 只读、不参与编辑。 */
data class AiChunkRequest(
    val chunkId: String,
    val text: String,
    val contextOnly: String?
)

/** Provider 运行所需的最小配置（阶段 A 仅做契约，不含真实网络）。 */
data class AiProviderConfig(
    val type: String,
    val serviceUrl: String,
    val model: String,
    val maxChunkChars: Int = 6000,
    val timeoutSeconds: Int = 45
)

/** 供应商无关的挂起接口。阶段 A 用 Fake Provider 实现。 */
interface AiTextProvider {
    /** 处理单块，返回归一化编辑列表。失败由异常或校验层表达。 */
    suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig): AiChunkOutput
}
