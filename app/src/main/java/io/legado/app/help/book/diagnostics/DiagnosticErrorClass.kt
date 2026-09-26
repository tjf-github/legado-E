package io.legado.app.help.book.diagnostics

/**
 * 统一错误分类。《书源与解析优化计划书》§6 B0 要求把网络、HTTP、编码、规则语法、零命中、
 * 正文为空、下一页循环、图片 URL 为空和替换超时区分开，不再用一个「失败」糊过去。
 *
 * [NONE] 表示该阶段正常产出，不是错误。
 */
enum class DiagnosticErrorClass(val label: String) {
    NONE("正常"),
    NETWORK("网络错误"),
    HTTP("HTTP错误"),
    ENCODING("编码错误"),
    RULE_SYNTAX("规则语法错误"),
    EMPTY_MATCH("规则零命中"),
    EMPTY_CONTENT("正文为空"),
    NEXT_PAGE_LOOP("下一页循环"),
    EMPTY_IMAGE_URL("图片URL为空"),
    REPLACE_TIMEOUT("替换超时"),
    UNKNOWN("未知错误");

    override fun toString(): String = label
}
