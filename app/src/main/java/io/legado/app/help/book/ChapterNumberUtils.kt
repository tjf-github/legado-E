package io.legado.app.help.book

/**
 * 章节标题数字编号重写工具
 *
 * 用于目录新增/删减章节时，对后续章节标题中的编号进行 +1/-1 重写。
 * 仅处理常见的编号模式，未命中的标题保持原样（只重排 index，不改标题）。
 */
object ChapterNumberUtils {

    private val patternCn = Regex("(第)(\\d+|[一二三四五六七八九十百千万零两〇]+)([章回节])")
    private val patternEn = Regex("(Chapter\\s*)(\\d+)", RegexOption.IGNORE_CASE)

    /**
     * @param delta 增量：新增章节传 1，删除章节传 -1
     * @return 重写后的标题；未命中任何编号模式或结果非法（编号小于 1）时返回 null
     */
    fun rewriteTitle(title: String, delta: Int): String? {
        if (delta == 0 || title.isBlank()) return null
        var result = title
        var rewritten = false
        patternCn.find(result)?.let { match ->
            val num = parseNumber(match.groupValues[2])
            if (num != null) {
                val newNum = num + delta
                if (newNum in 1..9999) {
                    result = result.replaceRange(
                        match.range,
                        "${match.groupValues[1]}${formatNumber(newNum, match.groupValues[2])}${match.groupValues[3]}"
                    )
                    rewritten = true
                }
            }
        }
        patternEn.find(result)?.let { match ->
            val num = match.groupValues[2].toIntOrNull()
            if (num != null) {
                val newNum = num + delta
                if (newNum >= 1) {
                    result = result.replaceRange(
                        match.range,
                        "${match.groupValues[1]}$newNum"
                    )
                    rewritten = true
                }
            }
        }
        return if (rewritten) result else null
    }

    /**
     * 仅重写标题中的编号前缀，返回如“第3章”“Chapter 3”这类纯编号标题。
     * 用于新增章节时预填默认标题，避免复制上一章的副标题。
     *
     * @return 纯编号标题；未命中任何编号模式时返回 null
     */
    fun nextNumberTitle(title: String, delta: Int): String? {
        if (delta == 0 || title.isBlank()) return null
        patternCn.find(title)?.let { match ->
            val num = parseNumber(match.groupValues[2])
            if (num != null) {
                val newNum = num + delta
                if (newNum in 1..9999) {
                    return "${match.groupValues[1]}${formatNumber(newNum, match.groupValues[2])}${match.groupValues[3]}"
                }
            }
        }
        patternEn.find(title)?.let { match ->
            val num = match.groupValues[2].toIntOrNull()
            if (num != null) {
                val newNum = num + delta
                if (newNum >= 1) {
                    return "${match.groupValues[1]}$newNum"
                }
            }
        }
        return null
    }

    private fun parseNumber(raw: String): Int? = raw.toIntOrNull() ?: ChineseNumberUtils.toInt(raw)

    private fun formatNumber(value: Int, raw: String): String =
        if (raw.all { it.isDigit() }) value.toString() else ChineseNumberUtils.toChinese(value).orEmpty()
}
