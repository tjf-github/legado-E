package io.legado.app.help.book

/**
 * 粘连章节拆分纯逻辑（不依赖 Android / 数据库，可 JVM 单测）
 *
 * 按标题规则定位正文中的章节起始行，把一整章正文拆成多个单元。
 * 首个标题之前的内容（前言/引子）默认并入第一章，不另立单元。
 */
object ChapterSplitter {

    /**
     * 标题起始行规则（匹配前先做全角转半角规范化）：
     * - 第N章/回/节/卷/集/部/篇/话：允许内部空白，数字支持阿拉伯/中文大小写/全角；
     * - Chapter N：允许空白与全角变体；
     * - 行首允许【〔「『［《（ 等包裹符号。
     * 纯数字标题（如 “1、xxx”）默认不识别，避免正文列举行误命中。
     */
    private val titleRegex = Regex(
        "^[\\s\u3000]*(?:[【〔「『［《（(]+[\\s\u3000]*)?" +
            "(第\\s{0,4}[0-9〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+\\s{0,4}[章回节卷集部篇话]|" +
            "Chapter\\s{0,4}[0-9]{1,4}).*$",
        RegexOption.IGNORE_CASE
    )

    /** 编号片段（用于首标题规范化比较）：第N章… 或 Chapter N */
    private val prefixRegex = Regex(
        "第\\s{0,4}[0-9〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+\\s{0,4}[章回节卷集部篇话]|" +
            "Chapter\\s{0,4}[0-9]{1,4}",
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
            !sameTitle(content.lineSequence().firstOrNull().orEmpty(), firstTitle)
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
            if (titleRegex.matches(normalizeFullWidth(line))) {
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

    /** 全角字符转半角（数字/字母/空格），用于规则匹配与编号比较 */
    private fun normalizeFullWidth(line: String): String {
        val sb = StringBuilder(line.length)
        for (ch in line) {
            when {
                ch in '０'..'９' -> sb.append('0' + (ch - '０'))
                ch in 'Ａ'..'Ｚ' -> sb.append('A' + (ch - 'Ａ'))
                ch in 'ａ'..'ｚ' -> sb.append('a' + (ch - 'ａ'))
                ch == '　' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * 编号片段规范化比较：抽行首“第N章/回…”“Chapter N”的编号值，
     * 阿拉伯与中文数字归一（第1章 == 第一章），避免格式差异导致重复单元；
     * 无法抽取时回退整行 trim 比较。
     */
    private fun sameTitle(a: String, b: String): Boolean {
        val keyA = titlePrefixKey(a)
        val keyB = titlePrefixKey(b)
        if (keyA != null && keyB != null) return keyA == keyB
        return a.trim() == b.trim()
    }

    /** 返回 (类型, 编号)：cn=第N章…、en=Chapter N；无法识别返回 null */
    private fun titlePrefixKey(line: String): Pair<String, Int>? {
        val match = prefixRegex.find(normalizeFullWidth(line)) ?: return null
        val text = match.value.replace(" ", "")
        val num = if (text.startsWith("第", ignoreCase = true)) {
            val digits = text.substring(1, text.length - 1)
            digits.toIntOrNull() ?: ChineseNumberUtils.toInt(digits)
        } else {
            text.drop(CHAPTER_WORD_LENGTH).toIntOrNull()
        } ?: return null
        return if (text.startsWith("第", ignoreCase = true)) Pair("cn", num) else Pair("en", num)
    }

    private const val CHAPTER_WORD_LENGTH = "Chapter".length
}
