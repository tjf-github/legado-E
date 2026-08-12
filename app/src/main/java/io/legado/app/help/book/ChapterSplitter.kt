package io.legado.app.help.book

/**
 * 粘连章节拆分纯逻辑（不依赖 Android / 数据库，可 JVM 单测）
 *
 * 按标题规则定位正文中的章节起始行，把一整章正文拆成多个单元。
 * 首个标题之前的内容（前言/引子）默认并入第一章，不另立单元。
 */
object ChapterSplitter {

    /** 标题起始行规则：第N章/回/节（阿拉伯或中文数字）或 Chapter N（忽略大小写） */
    private val titleRegex = Regex(
        "^[\\s\u3000]*(第(?:\\d+|[一二三四五六七八九十百千万零两〇]+)[章回节]|Chapter\\s*\\d+).*$",
        RegexOption.IGNORE_CASE
    )

    /** 拆分结果单元 */
    data class SplitUnit(val title: String, val content: String)

    private data class MutableSplitUnit(val title: String) {
        val bodyLines = mutableListOf<String>()
    }

    /**
     * @param content 整章正文（调用方传入有效正文：覆盖文件优先）
     * @param firstTitle 本章节标题。部分解析器的章节正文从标题行之后开始，
     *   此时把标题补到正文开头，保证原章节作为第一个拆分单元保留；
     *   若正文首行已是该标题则不重复添加。
     * @return 拆分单元列表；内容为空或未命中任何标题规则时返回空列表
     */
    fun split(content: String?, firstTitle: String? = null): List<SplitUnit> {
        if (content.isNullOrBlank()) return emptyList()
        val text = if (!firstTitle.isNullOrBlank() &&
            content.lineSequence().firstOrNull()?.trim() != firstTitle.trim()
        ) {
            "$firstTitle\n$content"
        } else {
            content
        }
        val lines = text.split('\n', '\r').filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val units = mutableListOf<MutableSplitUnit>()
        val prologue = mutableListOf<String>()
        for (line in lines) {
            if (titleRegex.matches(line)) {
                units.add(MutableSplitUnit(line.trim()))
            } else if (units.isNotEmpty()) {
                units.last().bodyLines.add(line)
            } else {
                prologue.add(line)
            }
        }
        if (units.isEmpty()) return emptyList()

        // 前言并入第一章
        units.first().bodyLines.addAll(0, prologue)
        return units.map { unit ->
            SplitUnit(unit.title, unit.bodyLines.joinToString("\n").trim())
        }
    }
}
