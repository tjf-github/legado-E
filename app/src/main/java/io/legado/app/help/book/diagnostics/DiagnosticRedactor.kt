package io.legado.app.help.book.diagnostics

/**
 * 诊断脱敏规则（《书源与解析优化计划书》§6 B0）。
 *
 * 硬性边界：
 * - 默认摘要只允许出现类型、长度、数量、耗时、阶段和错误分类；
 * - 不得输出真实正文、Cookie、令牌、URL 查询串等秘密；
 * - 本文件不依赖任何 Android API，纯字符串处理，可直接 JVM 契约测试。
 *
 * 只做「删除可证明的危险片段」，不做模糊化「美化」，避免把诊断信息本身弄丢。
 */
object DiagnosticRedactor {

    const val MASK = "***"

    /** 诊断消息（异常信息、规则名等）的最大保留长度，超长截断。 */
    const val MAX_MESSAGE_LENGTH = 240

    /** 阶段摘要里保留的「骨架」最大长度。 */
    const val MAX_SKELETON_LENGTH = 64

    /**
     * 生成正文的脱敏摘要：只保留长度与可数结构，不保留任何原文。
     *
     * 返回形如 `len=1234,lines=56,imgs=2`；空白/空正文返回零值摘要。
     */
    fun summarizeText(text: String?): String {
        if (text == null) return "len=0,lines=0,imgs=0"
        val lines = if (text.isEmpty()) 0 else countLines(text)
        return "len=${text.length},lines=$lines,imgs=${countImages(text)}"
    }

    /**
     * 只保留正文骨架：每个非空白字符折叠成一个 `.`、连续空白折叠成一个 `_`、不可见字符折叠成 `?`，
     * 输出长度由 [maxLength] 截断。
     *
     * 用途是让调试者能看出「这段是 HTML 还是纯文本、结构有没有被吃掉」而不写进真实正文，
     * 对应《书源与解析优化计划书》§6 B0「原始响应只显示必要片段」。
     *
     * **当前没有生产调用点**（P1 默认摘要只输出长度/数量/耗时，不输出任何正文片段），
     * 保留它是为了后续「原始响应预览」阶段直接可用；这一点在复审 L-3 里被明确指出，故在此如实标注。
     */
    fun skeleton(text: String?, maxLength: Int = MAX_SKELETON_LENGTH): String {
        if (text.isNullOrEmpty()) return ""
        val sb = StringBuilder()
        var pendingBlank = false
        var pendingOther = false
        for (ch in text) {
            when {
                ch.isWhitespace() -> pendingBlank = true
                ch.code < 0x20 || ch.code == 0x7F -> pendingOther = true
                else -> {
                    if (pendingOther) {
                        sb.append('?')
                        pendingOther = false
                        pendingBlank = false
                    }
                    if (pendingBlank) {
                        sb.append('_')
                        pendingBlank = false
                    }
                    sb.append('.')
                }
            }
            if (sb.length >= maxLength) break
        }
        return if (sb.length > maxLength) sb.substring(0, maxLength) else sb.toString()
    }

    /**
     * 对任意诊断消息脱敏：
     * - 删除 URL 的 userinfo、查询串与片段，只留 `scheme://host/path`；
     * - 删除常见凭据字段值（token/cookie/authorization/session/key 等）；
     * - 截断超长消息。
     */
    fun sanitizeMessage(message: String?): String {
        if (message.isNullOrEmpty()) return ""
        var str = message
        str = stripUrlSecrets(str)
        str = stripCredentialAssignments(str)
        str = stripBearerTokens(str)
        str = collapseWhitespace(str)
        return if (str.length > MAX_MESSAGE_LENGTH) {
            str.substring(0, MAX_MESSAGE_LENGTH) + "..."
        } else {
            str
        }
    }

    /**
     * `scheme://user:pass@host/path?query#frag` -> `scheme://host/path`
     *
     * 只按 `://` 定位，不猜协议名，避免把普通文本里的 `a:b@c` 误伤。
     *
     * 处理方式：URL 的范围仍然扫到空白/引号/括号等硬分隔符，但**只保留 authority + path**，
     * 第一个 `?` 或 `#` 之后的查询串与片段连同 URL 范围一起丢弃，不留在输出里。
     * 只把 `?`/`#` 当成「切点」而不是「URL 结束点」是关键：当成结束点会让查询串掉回普通文本。
     */
    internal fun stripUrlSecrets(text: String): String {
        val sb = StringBuilder(text.length)
        var index = 0
        while (true) {
            val schemeAt = text.indexOf("://", index)
            if (schemeAt < 0) {
                sb.append(text, index, text.length)
                break
            }
            // scheme 起点：向左取到最近的空白/引号/括号
            var start = schemeAt
            while (start > index && !text[start - 1].isUrlBoundary()) start--
            sb.append(text, index, start)
            // URL 范围结束：最近的硬分隔符（查询串与片段仍在这个范围之内）
            var end = schemeAt + 3
            while (end < text.length && !text[end].isUrlTerminator()) end++
            // 切点：authority + path 到这里为止，其余（?query / #frag）随 URL 范围一起丢弃
            var cut = schemeAt + 3
            while (cut < end && text[cut] != '?' && text[cut] != '#') cut++
            val authorityAndPath = text.substring(schemeAt + 3, cut)
            // userinfo 只在 authority 内：路径里出现的 '@'（如 /@user/profile）不是凭据，
            // 在整段 authority+path 上取最后一个 '@' 会把主机名一起切掉，摘要就指向了错误主机。
            // 切不出 authority（畸形 URL，如 https:///user:pw@host/x）时保守回退到旧行为：
            // 宁可多切掉主机名，也不把凭据留在摘要里。
            // 残余边界：userinfo 本身含 '/' 的畸形 URL 不再被识别为 userinfo（RFC 3986 不允许这种写法）。
            val authority = authorityAndPath.substringBefore('/')
            val visible = if (authority.isEmpty()) {
                val at = authorityAndPath.lastIndexOf('@')
                if (at >= 0) authorityAndPath.substring(at + 1) else authorityAndPath
            } else {
                val at = authority.lastIndexOf('@')
                val visibleHost = if (at >= 0) authority.substring(at + 1) else authority
                visibleHost + authorityAndPath.substring(authority.length)
            }
            sb.append(text, start, schemeAt + 3).append(visible)
            index = end
        }
        return sb.toString()
    }

    /** `token=abcd` / `Cookie: abcd` -> `token=***` */
    internal fun stripCredentialAssignments(text: String): String {
        var result = text
        for (key in SECRET_KEYS) {
            result = replaceAssignment(result, key, '=')
            result = replaceAssignment(result, key, ':')
        }
        return result
    }

    /** `Bearer abc.def` -> `Bearer ***` */
    internal fun stripBearerTokens(text: String): String {
        val sb = StringBuilder(text.length)
        var index = 0
        while (true) {
            val at = text.indexOf("bearer", index, ignoreCase = true)
            if (at < 0) {
                sb.append(text, index, text.length)
                break
            }
            val valueStart = skipSpaces(text, at + "bearer".length)
            if (valueStart == at + "bearer".length) {
                // 后面没有分隔空白，不是 bearer 值
                sb.append(text, index, valueStart)
                index = valueStart
                continue
            }
            var valueEnd = valueStart
            while (valueEnd < text.length && !text[valueEnd].isWhitespace()) valueEnd++
            sb.append(text, index, valueStart).append(MASK)
            index = valueEnd
        }
        return sb.toString()
    }

    /**
     * `key=value` / `key: value` 的值替换为掩码；键名保持原样，便于定位。
     *
     * 两种分隔符的掩码范围不同：
     * - `key=value` 只掩到下一个分隔符（`&`/`;`/`,`/空白），避免吃掉同句里的其它诊断信息；
     * - `key: value` 按 **Header 语义**掩到行尾。只掩一个词会漏掉后面的部分
     *   （`Authorization: Bearer <token>` 掩掉 `Bearer` 反而把 token 暴露出来），
     *   而带冒号的凭据说明本来就是整行敏感。
     */
    private fun replaceAssignment(text: String, key: String, separator: Char): String {
        val toEndOfLine = separator == ':'
        val sb = StringBuilder(text.length)
        var index = 0
        while (true) {
            val at = text.indexOf(key, index, ignoreCase = true)
            if (at < 0) {
                sb.append(text, index, text.length)
                break
            }
            val before = if (at == 0) null else text[at - 1]
            val afterKey = at + key.length
            var cursor = skipSpaces(text, afterKey)
            val separatorOk = cursor < text.length && text[cursor] == separator
            if (before != null && (before.isLetterOrDigit() || before == '_') || !separatorOk) {
                sb.append(text, index, afterKey)
                index = afterKey
                continue
            }
            cursor++
            // 分隔符与值之间可能有空白（`Cookie: value` / `Authorization: value`），
            // 不跳过就会把空白当成值的结尾，原值原样留在输出里
            cursor = skipSpaces(text, cursor)
            sb.append(text, index, cursor)
            var valueEnd = cursor
            if (toEndOfLine) {
                while (valueEnd < text.length && text[valueEnd] != '\n' && text[valueEnd] != '\r') {
                    valueEnd++
                }
            } else {
                while (valueEnd < text.length && text[valueEnd] != ' ' && text[valueEnd] != '\n' &&
                    text[valueEnd] != ';' && text[valueEnd] != ',' && text[valueEnd] != '&'
                ) {
                    valueEnd++
                }
            }
            sb.append(MASK)
            index = valueEnd
        }
        return sb.toString()
    }

    private fun skipSpaces(text: String, from: Int): Int {
        var cursor = from
        while (cursor < text.length && (text[cursor] == ' ' || text[cursor] == '\t')) cursor++
        return cursor
    }

    private fun collapseWhitespace(text: String): String {
        val sb = StringBuilder(text.length)
        var pendingSpace = false
        for (ch in text) {
            if (ch == '\n' || ch == '\r' || ch == '\t' || ch == ' ') {
                pendingSpace = true
            } else {
                if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
                pendingSpace = false
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun countLines(text: String): Int {
        var count = 1
        for (ch in text) if (ch == '\n') count++
        return count
    }

    /** 与 `HtmlFormatter.formatImagePattern` 的 `<img` 前缀一致，只数不改。 */
    private fun countImages(text: String): Int {
        var count = 0
        var index = 0
        while (true) {
            val at = text.indexOf("<img", index, ignoreCase = true)
            if (at < 0) return count
            count++
            index = at + 4
        }
    }

    private fun Char.isUrlBoundary(): Boolean =
        isWhitespace() || this == '"' || this == '\'' || this == '(' || this == '[' ||
            this == '{' || this == '<' || this == '（' || this == '【' || this == '：' || this == '='

    private fun Char.isUrlTerminator(): Boolean =
        isWhitespace() || this == '"' || this == '\'' || this == ')' || this == ']' ||
            this == '}' || this == '>' || this == '（' || this == '【' || this == '，' ||
            this == '。' || this == '<'

    private val SECRET_KEYS = listOf(
        "token",
        "access_token",
        "refresh_token",
        "cookie",
        "authorization",
        "password",
        "passwd",
        "secret",
        "api_key",
        "apikey",
        "session",
        "sessionid",
        "sign",
        "signature",
        "set-cookie",
        // 常见短键名（查询串本身已被 stripUrlSecrets 丢弃，这里是消息里以 `key=value` 形式出现时的守卫）
        "sid",
        "auth",
        "key",
        "tk",
        "csrf"
    )
}
