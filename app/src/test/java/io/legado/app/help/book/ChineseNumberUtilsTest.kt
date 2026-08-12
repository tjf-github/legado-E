package io.legado.app.help.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChineseNumberUtilsTest {

    @Test
    fun toIntBasicNumbers() {
        assertEquals(10, ChineseNumberUtils.toInt("十"))
        assertEquals(12, ChineseNumberUtils.toInt("十二"))
        assertEquals(20, ChineseNumberUtils.toInt("二十"))
        assertEquals(100, ChineseNumberUtils.toInt("一百"))
        assertEquals(103, ChineseNumberUtils.toInt("一百零三"))
        assertEquals(110, ChineseNumberUtils.toInt("一百一十"))
        assertEquals(200, ChineseNumberUtils.toInt("两百"))
        assertEquals(2300, ChineseNumberUtils.toInt("两千三百"))
        assertEquals(1001, ChineseNumberUtils.toInt("一千零一"))
        assertEquals(9999, ChineseNumberUtils.toInt("九千九百九十九"))
    }

    @Test
    fun toIntArabicAndZeroVariants() {
        assertEquals(100, ChineseNumberUtils.toInt("100"))
        assertEquals(1001, ChineseNumberUtils.toInt("一千〇一"))
        assertEquals(103, ChineseNumberUtils.toInt("一百零三"))
    }

    @Test
    fun toIntInvalidReturnsNull() {
        assertNull(ChineseNumberUtils.toInt(""))
        assertNull(ChineseNumberUtils.toInt("零"))
        assertNull(ChineseNumberUtils.toInt("一万"))
        assertNull(ChineseNumberUtils.toInt("10000"))
        assertNull(ChineseNumberUtils.toInt("abc"))
    }

    @Test
    fun toChineseBasicNumbers() {
        assertEquals("十", ChineseNumberUtils.toChinese(10))
        assertEquals("十二", ChineseNumberUtils.toChinese(12))
        assertEquals("二十", ChineseNumberUtils.toChinese(20))
        assertEquals("一百", ChineseNumberUtils.toChinese(100))
        assertEquals("一百零三", ChineseNumberUtils.toChinese(103))
        assertEquals("一百一十", ChineseNumberUtils.toChinese(110))
        assertEquals("二百", ChineseNumberUtils.toChinese(200))
        assertEquals("二千三百", ChineseNumberUtils.toChinese(2300))
        assertEquals("一千零一", ChineseNumberUtils.toChinese(1001))
        assertEquals("九千九百九十九", ChineseNumberUtils.toChinese(9999))
    }

    @Test
    fun toChineseInvalidReturnsNull() {
        assertNull(ChineseNumberUtils.toChinese(0))
        assertNull(ChineseNumberUtils.toChinese(10000))
    }

    @Test
    fun roundTripWithinRange() {
        listOf(1, 2, 9, 10, 11, 19, 20, 99, 100, 101, 109, 110, 200, 999, 1000, 1001, 2300, 9999)
            .forEach { n ->
                assertEquals(n, ChineseNumberUtils.toInt(ChineseNumberUtils.toChinese(n)!!))
            }
    }
}
