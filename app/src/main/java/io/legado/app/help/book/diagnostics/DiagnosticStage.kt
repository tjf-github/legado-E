package io.legado.app.help.book.diagnostics

/**
 * 正文四阶段诊断：阶段标识。
 *
 * 顺序即真实调用链顺序，不得重排：
 * 1. [EXTRACT]       `AnalyzeRule.getString(contentRule.content)` 之后（提取后）
 * 2. [FORMAT]        `HtmlFormatter.formatKeepImg` 之后（HTML 格式化后）
 * 3. [REPLACE]       `ContentRule.replaceRegex` 全文替换之后（书源替换后）
 * 4. [READ_PROCESS]  `ContentProcessor.getContent` 之后（阅读处理后）
 *
 * 调试摘要与错误分类只依赖本枚举，不依赖任何 Android API，便于 JVM 契约测试。
 */
enum class DiagnosticStage(val label: String, val order: Int) {
    EXTRACT("提取后", 1),
    FORMAT("HTML格式化后", 2),
    REPLACE("书源替换后", 3),
    READ_PROCESS("阅读处理后", 4);

    override fun toString(): String = "$order.$label"
}
