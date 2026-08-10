package io.legado.app.help.book

/**
 * 章节标题数字编号重写工具
 *
 * 用于目录新增/删减章节时，对后续章节标题中的编号进行 +1/-1 重写。
 * 仅处理常见的编号模式，未命中的标题保持原样（只重排 index，不改标题）。
 */
object ChapterNumberUtils {

    private val patternCn = Regex("(第)(\\d+)([章回节])")
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
            val num = match.groupValues[2].toIntOrNull()
            if (num != null) {
                val newNum = num + delta
                if (newNum >= 1) {
                    result = result.replaceRange(
                        match.range,
                        "${match.groupValues[1]}$newNum${match.groupValues[3]}"
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
}
