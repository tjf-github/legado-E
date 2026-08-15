@file:Suppress("unused")

package io.legado.app.utils

import android.annotation.SuppressLint
import android.icu.text.Collator
import android.icu.util.ULocale
import android.net.Uri
import android.text.Editable
import cn.hutool.core.net.URLEncodeUtil
import io.legado.app.constant.AppPattern
import io.legado.app.constant.AppPattern.dataUriRegex
import java.io.File
import java.lang.Character.codePointCount
import java.lang.Character.offsetByCodePoints
import java.net.InetAddress
import java.util.Locale
import java.util.regex.Pattern
import androidx.core.net.toUri

fun String?.safeTrim() = if (this.isNullOrBlank()) null else this.trim()

fun String?.isContentScheme(): Boolean = this?.startsWith("content://") == true

fun String.toEditable(): Editable = Editable.Factory.getInstance().newEditable(this)

fun String.parseToUri(): Uri {
    return if (isUri()) this.toUri() else {
        Uri.fromFile(File(this))
    }
}

fun String?.isUri(): Boolean {
    this ?: return false
    return this.startsWith("file://", true) || isContentScheme()
}

fun String?.isAbsUrl() =
    this?.let {
        it.startsWith("http://", true) || it.startsWith("https://", true)
    } ?: false

fun String?.isDataUrl() =
    this?.let {
        dataUriRegex.matches(it)
    } ?: false

fun String?.isJson(): Boolean =
    this?.run {
        val str = this.trim()
        when {
            str.startsWith("{") && str.endsWith("}") -> true
            str.startsWith("[") && str.endsWith("]") -> true
            else -> false
        }
    } ?: false

fun String?.isJsonObject(): Boolean =
    this?.run {
        val str = this.trim()
        str.startsWith("{") && str.endsWith("}")
    } ?: false

fun String?.isJsonArray(): Boolean =
    this?.run {
        val str = this.trim()
        str.startsWith("[") && str.endsWith("]")
    } ?: false

fun String?.isXml(): Boolean =
    this?.run {
        val str = this.trim()
        str.startsWith("<") && str.endsWith(">")
    } ?: false

fun String?.isTrue(nullIsTrue: Boolean = false): Boolean {
    if (this.isNullOrBlank() || this == "null") {
        return nullIsTrue
    }
    return !this.trim().matches("(?i)^(?:false|no|not|0|0.0)$".toRegex())
}

fun String.isHex(): Boolean {
    return all {c ->
        c in '0'..'9' || c in 'A'..'F' || c in 'a'..'f'
    }
}

fun String.splitNotBlank(vararg delimiter: String, limit: Int = 0): Array<String> = run {
    this.split(*delimiter, limit = limit).map { it.trim() }.filterNot { it.isBlank() }
        .toTypedArray()
}

fun String.splitNotBlank(regex: Regex, limit: Int = 0): Array<String> = run {
    this.split(regex, limit).map { it.trim() }.filterNot { it.isBlank() }.toTypedArray()
}

@SuppressLint("ObsoleteSdkInt")
fun String.cnCompare(other: String): Int {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        Collator.getInstance(ULocale.SIMPLIFIED_CHINESE).compare(this, other)
    } else {
        java.text.Collator.getInstance(Locale.CHINA).compare(this, other)
    }
}

/**
 * 自然排序比较：数字块按数值比较（1 < 2 < 10），非数字块按中文排序规则（拼音）。
 * 用于书架按书名/作者排序，避免纯字典序出现的 1,10,11,2,3 顺序。
 */
fun String.naturalCompare(other: String): Int {
    return naturalCompareWith(this, other) { a, b -> a.cnCompare(b) }
}

/**
 * 自然排序纯逻辑：数字块按数值比较，非数字块交给 textCompare 比较。
 * 抽成可注入文本比较器的纯函数，便于 JVM 单测（避免依赖 Android 的 ICU Collator）。
 */
internal fun naturalCompareWith(
    a: String,
    b: String,
    textCompare: (String, String) -> Int
): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ci = a[i]
        val cj = b[j]
        if (ci.isDigit() && cj.isDigit()) {
            val si = i
            while (i < a.length && a[i].isDigit()) i++
            val sj = j
            while (j < b.length && b[j].isDigit()) j++
            val ni = a.substring(si, i).trimStart('0').ifEmpty { "0" }
            val nj = b.substring(sj, j).trimStart('0').ifEmpty { "0" }
            val cmpLen = ni.length.compareTo(nj.length)
            if (cmpLen != 0) return cmpLen
            val cmpVal = ni.compareTo(nj)
            if (cmpVal != 0) return cmpVal
            // 数值相同但补零位数不同：位数少者优先（1 < 001）
            val cmpPad = (i - si).compareTo(j - sj)
            if (cmpPad != 0) return cmpPad
        } else {
            val si = i
            while (i < a.length && !a[i].isDigit()) i++
            val sj = j
            while (j < b.length && !b[j].isDigit()) j++
            val cmp = textCompare(a.substring(si, i), b.substring(sj, j))
            if (cmp != 0) return cmp
        }
    }
    return a.length - b.length
}

/**
 * 字符串所占内存大小
 */
fun String?.memorySize(): Int {
    this ?: return 0
    return 40 + 2 * length
}

/**
 * 是否中文
 */
fun String.isChinese(): Boolean {
    val p = Pattern.compile("[\u4e00-\u9fa5]")
    val m = p.matcher(this)
    return m.find()
}

/**
 * 将字符串拆分为单个字符,包含emoji
 */
fun CharSequence.toStringArray(): Array<String> {
    var codePointIndex = 0
    return try {
        Array(codePointCount(this, 0, length)) {
            val start = codePointIndex
            codePointIndex = offsetByCodePoints(this, start, 1)
            substring(start, codePointIndex)
        }
    } catch (e: Exception) {
        split("").toTypedArray()
    }
}

fun String.escapeRegex(): String {
    return replace(AppPattern.regexCharRegex, "\\\\$0")
}

fun String.encodeURI(): String = URLEncodeUtil.encodeQuery(this)

fun String.normalizeFileName(): String {
    return replace(AppPattern.fileNameRegex2, "_")
}

/**
 * 将ip字符串转为InetAddress
 */
fun String.parseIpsFromString(): List<InetAddress>? =
    split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { it.runCatching { InetAddress.getByName(this) }.getOrNull() }
        .takeIf { it.isNotEmpty() }


fun String.quoteReplacementJs(): String {
    if (!this.contains('\\')) {
        return this
    }
    val sb = StringBuilder()
    for (c in this) {
        if (c == '\\') {
            sb.append("\\\\")
        } else {
            sb.append(c)
        }
    }
    return sb.toString()
}