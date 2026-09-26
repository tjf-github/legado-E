package io.legado.app.help.book.diagnostics

/**
 * 正文段落规范化的**纯函数**部分。
 *
 * 从 [io.legado.app.help.book.ContentProcessor.getContent] 尾部原样抽出，语义一字未改：
 * 按 `\n` 切行、逐行 trim（去掉 `code <= 0x20` 的字符与全角空格 U+3000）、丢弃空行、
 * 首段保留原样、其余段加 `paragraphIndent` 前缀。
 *
 * 抽出的目的是让 P1 契约测试能在纯 JVM 下冻结这段行为：`ContentProcessor` 本身依赖
 * `appDb` / `Build.VERSION` / `appCtx`，无法在无 Robolectric、无 mock 框架的 JVM 单测里实例化。
 * 逻辑仍是「同一份代码」，但**这不是调用生产类**，属于转写级证据，已在执行报告里如实标注。
 */
object ContentLineNormalizer {

    /**
     * @param content 已完成的正文（含标题行）
     * @param includeTitle 首段是否作为标题处理（不缩进）
     * @param paragraphIndent 正文段落缩进前缀，通常为两个全角空格
     */
    fun normalize(
        content: String,
        includeTitle: Boolean,
        paragraphIndent: String
    ): List<String> {
        val contents = ArrayList<String>()
        content.split("\n").forEach { str ->
            val paragraph = str.trim {
                it.code <= 0x20 || it == '　'
            }
            if (paragraph.isNotEmpty()) {
                if (contents.isEmpty() && includeTitle) {
                    contents.add(paragraph)
                } else {
                    contents.add("$paragraphIndent$paragraph")
                }
            }
        }
        return contents
    }
}
