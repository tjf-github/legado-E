package io.legado.app.help.book.diagnostics

import java.net.URL

/**
 * 诊断会话的身份归一（D-2）。
 *
 * 背景：一次书源调试会话要判断「这次正文处理是不是我绑定的那一章」，原来直接比字符串。
 * 但章节 URL 按规则原文入库、**可以是相对路径**（`BookChapterList.kt` 的 `bookChapter.url = getString(urlRule)`），
 * 而调试页 `--<url>` 入口拿到的是用户粘的绝对地址；两侧逐字相等才接受，就等于这类会话永远匹配不上。
 * 实测（`book-source-p1-device` 的 D-2）失效不是「少一行」，而是第 4 行被写成「未进入阅读处理」——
 * 阅读明明执行了。所以这里把两侧都归一到同一口径再比。
 *
 * 归一只做**身份**用途，不参与请求、缓存或正文处理：
 * - 章节键：按该章节自己的 baseUrl 绝对化（与请求路径同源的 `NetworkUtils.getAbsoluteURL` 语义），再去掉 URL 选项（`,{...}`）；
 * - 书源键：去首尾空白、去片段与查询串、去尾斜杠（书源 URL 常见差异只有这几处）。
 *
 * 刻意**不**做：不做查询串排序归一、不按域名/同站放宽、不记录或返回任何 URL 到摘要里。
 * 纯 JVM 实现（只用 `java.net.URL`），可在单测里直接断言。
 */
object DiagnosticIdentity {

    /**
     * URL 选项分隔符，与 `AnalyzeUrl.paramPattern`（`\s*,\s*(?=\{)`）保持同一语义：
     * 只有「逗号 + 花括号」才是选项，查询串里的普通逗号不算。
     * 这里自带一份编译好的正则，避免把 Android 侧的 AnalyzeUrl 拉进纯 JVM 单测。
     */
    private val optionPattern = Regex("\\s*,\\s*(?=\\{)")

    /**
     * 章节身份键：绝对化 + 去 URL 选项。
     *
     * [baseUrl] 用章节自己的 `baseUrl`（`BookChapter.baseUrl`，入库时即为目录页的 redirectUrl）。
     * 无法解析（畸形 URL、`data:`/`javascript:` 等未知协议）时退回去掉选项后的原文——
     * 宁可两侧都退化成字符串比较，也不要在这里抛异常或把身份判成空。
     */
    fun chapterKey(baseUrl: String?, chapterUrl: String?): String? {
        if (chapterUrl.isNullOrBlank()) return chapterUrl
        val urlPart = stripOptions(chapterUrl.trim())
        val base = baseUrl?.trim()?.substringBefore(',')
        if (base.isNullOrEmpty()) return urlPart
        return try {
            URL(URL(base), urlPart).toString()
        } catch (e: Exception) {
            urlPart
        }
    }

    /**
     * 书源身份键：去空白、去片段、去查询串、去尾斜杠。空/空白返回 `null`（调用方据此判不匹配）。
     *
     * 只做这些——把「同域名即同源」放宽进来会推翻 P1 真机轮的会话隔离证据（S5）。
     * 去片段（`#`）比裁定书 M2 的字面多一项：片段不参与请求，指向同一书源，属有意放宽，已在执行报告登记。
     */
    fun sourceKey(sourceUrl: String?): String? {
        val raw = sourceUrl?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val withoutFragment = raw.substringBefore('#')
        val withoutQuery = withoutFragment.substringBefore('?')
        return withoutQuery.trimEnd('/').ifEmpty { null }
    }

    /** 去掉 URL 选项（AnalyzeUrl 的 `,{...}` 后缀），与 `BookChapter.getAbsoluteURL()` 用的是同一个切法。 */
    private fun stripOptions(url: String): String {
        val match = optionPattern.find(url) ?: return url
        return url.substring(0, match.range.first)
    }
}
