package io.legado.app.help.ai

import io.legado.app.help.book.BookContent

/** 当前章用户实际选择的正文显示源。首版只有原文与完整 AI 结果两档，不提供混合结果。 */
enum class AiDisplaySource { Original, Ai }

/**
 * 传给 [io.legado.app.ui.book.read.page.provider.ChapterProvider] 的唯一显示源。
 *
 * 默认把 [original] 交给排版；仅当用户已选择 AI（[source] == [AiDisplaySource.Ai]）且存在完整候选
 * （[candidate] 非空）时才返回候选。候选只替换 `textList`，必须保留原对象的
 * `sameTitleRemoved` 与 `effectiveReplaceRules`（见 [BookContent.withAiText]）。
 *
 * 本类绝不写回原始章节缓存，也不改变 WebBook、导出、搜索、朗读读取的原文。
 */
class AiDisplayContent(
    val original: BookContent,
    val candidate: BookContent?,
    val source: AiDisplaySource,
    val taskToken: AiTaskToken? = null
) {
    /** 真正交给排版器的正文。 */
    val content: BookContent
        get() = if (source == AiDisplaySource.Ai && candidate != null) candidate else original

    val isAi: Boolean get() = source == AiDisplaySource.Ai && candidate != null

    override fun toString(): String =
        "AiDisplayContent(source=$source, candidate=${if (candidate != null) "present" else "null"})"

    companion object {
        /** AI 不适用 / 未开启 / 失败回退时使用：始终排版原文。 */
        fun original(content: BookContent): AiDisplayContent =
            AiDisplayContent(content, null, AiDisplaySource.Original)

        /** 以完整候选结果构造：候选只替换 textList，保留原对象元数据。 */
        fun ai(original: BookContent, candidate: BookContent): AiDisplayContent =
            AiDisplayContent(original, candidate, AiDisplaySource.Ai)
    }
}

/**
 * 用整章净化后的文本（由 [io.legado.app.help.ai.AiChapterService] 完成“恢复哨兵”后的结果）
 * 构造候选。原文段落以 `\n` 连接成单个输入串，处理完成后再按 `\n` 拆回段落；
 * 段落数与原始 `textList` 可能因空白类局部编辑合并/拆行而变化，这属于预期行为。
 * `sameTitleRemoved` 与 `effectiveReplaceRules` 原样保留。
 */
fun BookContent.withAiText(completed: String): BookContent =
    copy(textList = completed.split("\n"))
