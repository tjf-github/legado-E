package io.legado.app.help.book

/**
 * 中文数字与阿拉伯数字互转（支持 1~9999）
 *
 * 仅用于章节标题编号重写；超出范围返回 null，由调用方决定不重写。
 */
object ChineseNumberUtils {

    private val digits = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3,
        '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
    )
    private val unitValues = mapOf('十' to 10, '百' to 100, '千' to 1000, '万' to 10000)
    private const val digitChars = "零一二三四五六七八九"

    /**
     * 中文数字 → Int（1~9999）；非法、零或超范围返回 null。
     * 纯阿拉伯数字字符串也可直接转换（如 “100”）。
     */
    fun toInt(text: String): Int? {
        if (text.isEmpty()) return null
        text.toIntOrNull()?.let { return it.takeIf { n -> n in 1..9999 } }
        var total = 0
        var section = 0
        var number = 0
        var sawDigit = false
        for (ch in text) {
            val digit = digits[ch]
            if (digit != null) {
                number = digit
                sawDigit = true
            } else {
                val unit = unitValues[ch] ?: return null
                val base = if (number == 0) 1 else number
                if (unit == 10000) {
                    total += (section + base) * unit
                    section = 0
                } else {
                    section += base * unit
                }
                number = 0
            }
        }
        if (!sawDigit && section == 0 && total == 0) return null
        val result = total + section + number
        return result.takeIf { it in 1..9999 }
    }

    /**
     * Int（1~9999）→ 中文数字；超范围返回 null。
     * 十到十九输出不带前导“一”（十、十二…）；零位按中文习惯补“零”（一百零三、一千零一）。
     */
    fun toChinese(n: Int): String? {
        if (n !in 1..9999) return null
        val sb = StringBuilder()
        var pendingZero = false
        fun appendGroup(value: Int, unit: Char?) {
            if (value > 0) {
                if (pendingZero) {
                    sb.append('零')
                    pendingZero = false
                }
                sb.append(digitChars[value])
                unit?.let { sb.append(it) }
            } else if (sb.isNotEmpty()) {
                pendingZero = true
            }
        }
        appendGroup(n / 1000, '千')
        appendGroup(n % 1000 / 100, '百')
        appendGroup(n % 100 / 10, '十')
        appendGroup(n % 10, null)
        var result = sb.toString()
        if (n in 10..19) result = result.removePrefix("一")
        return result
    }
}
