package io.legado.app.help.book.diagnostics

import io.legado.app.exception.ContentEmptyException
import io.legado.app.exception.RegexTimeoutException
import java.io.EOFException
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.CharacterCodingException
import java.util.concurrent.TimeoutException
import java.util.regex.PatternSyntaxException

/**
 * 统一错误分类。默认脱敏：只返回类别与阶段，不复制原文。
 *
 * 判定顺序刻意「由具体到宽泛」，避免把替换超时误判成网络超时、把规则语法错误误判成零命中。
 */
object DiagnosticClassifier {

    /** 规则语法类标记（正则 / JSONPath / XPath / JS）。 */
    private val RULE_SYNTAX_MARKERS = listOf(
        "patternsyntaxexception",
        "malformedjson",
        "jsonparseexception",
        "jsonpathexception",
        "invalidpathexception",
        "invalid xpath",
        "xpathexception",
        "jsexception",
        "evaluatorexception",
        "unexpected token",
        "syntaxerror",
        "syntax error",
        "unknown hosting",
        "illegal pattern"
    )

    /** 网络类标记。 */
    private val NETWORK_MARKERS = listOf(
        "unknownhostexception",
        "connectexception",
        "sockettimeout",
        "no route to host",
        "failed to connect",
        "connection reset",
        "connection refused",
        "unable to resolve host",
        "network is unreachable",
        "timeout",
        "timed out"
    )

    /** 编码类标记。 */
    private val ENCODING_MARKERS = listOf(
        "malformedinput",
        "unmappablecharacter",
        "charactercodingexception",
        "unsupportedcharset",
        "unsupported encoding",
        "charset",
        "乱码"
    )

    /** 下一页循环类标记。 */
    private val NEXT_PAGE_LOOP_MARKERS = listOf(
        "next page",
        "nextpage",
        "下一页",
        "分页循环",
        "page loop"
    )

    /**
     * 由异常类型与消息判定错误类别。
     *
     * @param stage 出错阶段；同一异常在不同阶段含义不同（替换阶段超时才算替换超时）。
     */
    fun classify(error: Throwable?, stage: DiagnosticStage? = null): DiagnosticErrorClass {
        if (error == null) return DiagnosticErrorClass.NONE
        val typed = when (error) {
            is RegexTimeoutException -> DiagnosticErrorClass.REPLACE_TIMEOUT
            is ContentEmptyException -> DiagnosticErrorClass.EMPTY_CONTENT
            // 正则语法错误按类型判定，绝不解析异常消息里的规则片段（那正是要脱敏的内容）
            is PatternSyntaxException -> DiagnosticErrorClass.RULE_SYNTAX
            is UnknownHostException -> DiagnosticErrorClass.NETWORK
            is SocketTimeoutException -> DiagnosticErrorClass.NETWORK
            is SocketException -> DiagnosticErrorClass.NETWORK
            is InterruptedIOException -> DiagnosticErrorClass.NETWORK
            is TimeoutException -> if (stage == DiagnosticStage.REPLACE) {
                DiagnosticErrorClass.REPLACE_TIMEOUT
            } else {
                DiagnosticErrorClass.NETWORK
            }

            is CharacterCodingException -> DiagnosticErrorClass.ENCODING
            is EOFException -> DiagnosticErrorClass.ENCODING
            else -> DiagnosticErrorClass.NONE
        }
        if (typed != DiagnosticErrorClass.NONE) return typed
        return classifyByMessage(error.message, stage)
    }

    /**
     * 由消息文本判定错误类别；用于只有一句错误说明、没有异常对象的场合。
     */
    fun classifyByMessage(message: String?, stage: DiagnosticStage? = null): DiagnosticErrorClass {
        if (message.isNullOrBlank()) return DiagnosticErrorClass.NONE
        val lower = message.lowercase()
        if (RULE_SYNTAX_MARKERS.any { lower.contains(it) }) {
            return DiagnosticErrorClass.RULE_SYNTAX
        }
        if (stage == DiagnosticStage.REPLACE && (lower.contains("超时") || lower.contains("timeout"))) {
            return DiagnosticErrorClass.REPLACE_TIMEOUT
        }
        if (NEXT_PAGE_LOOP_MARKERS.any { lower.contains(it) }) {
            return DiagnosticErrorClass.NEXT_PAGE_LOOP
        }
        if (ENCODING_MARKERS.any { lower.contains(it) }) {
            return DiagnosticErrorClass.ENCODING
        }
        if (NETWORK_MARKERS.any { lower.contains(it) }) {
            return DiagnosticErrorClass.NETWORK
        }
        if (HTTP_STATUS_REGEX.containsMatchIn(lower)) {
            return DiagnosticErrorClass.HTTP
        }
        if (lower.contains("图片url") || (lower.contains("图片") && lower.contains("空"))) {
            return DiagnosticErrorClass.EMPTY_IMAGE_URL
        }
        if (lower.contains("内容为空") || lower.contains("正文为空") ||
            lower.contains("content is empty") || lower.contains("content empty")
        ) {
            return DiagnosticErrorClass.EMPTY_CONTENT
        }
        if (lower.contains("零命中") || lower.contains("no match")) {
            return DiagnosticErrorClass.EMPTY_MATCH
        }
        return DiagnosticErrorClass.UNKNOWN
    }

    /**
     * 由内容产出情况判定：只在没有异常时使用。
     *
     * 空产出归入零命中；阅读处理阶段空产出归入正文为空。
     */
    fun classifyOutput(text: String?, stage: DiagnosticStage): DiagnosticErrorClass {
        if (!text.isNullOrEmpty()) return DiagnosticErrorClass.NONE
        return when (stage) {
            DiagnosticStage.READ_PROCESS -> DiagnosticErrorClass.EMPTY_CONTENT
            else -> DiagnosticErrorClass.EMPTY_MATCH
        }
    }

    private val HTTP_STATUS_REGEX = Regex("""\b(4\d{2}|5\d{2})\b""")
}
