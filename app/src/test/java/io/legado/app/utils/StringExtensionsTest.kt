package io.legado.app.utils

import org.junit.Assert.assertTrue
import org.junit.Test

class StringExtensionsTest {

    private val textCompare: (String, String) -> Int = { a, b -> a.compareTo(b) }

    @Test
    fun naturalCompareOrdersNumbersNumerically() {
        // 8 < 10，而不是 1,10,11,2,3 的字典序
        assertTrue(naturalCompareWith("2", "10", textCompare) < 0)
        assertTrue(naturalCompareWith("10", "2", textCompare) > 0)
        assertTrue(naturalCompareWith("001", "010", textCompare) < 0)
    }

    @Test
    fun naturalCompareKeepsChapterNumbersInOrder() {
        assertTrue(naturalCompareWith("第1话", "第2话", textCompare) < 0)
        assertTrue(naturalCompareWith("第2话", "第10话", textCompare) < 0)
        assertTrue(naturalCompareWith("第10话", "第2话", textCompare) > 0)
        assertTrue(naturalCompareWith("第1话", "第1话(2)", textCompare) < 0)
    }

    @Test
    fun naturalCompareHandlesPaddedNumbers() {
        // 数值相同：补零位数少者优先（1 < 001）
        assertTrue(naturalCompareWith("1.jpg", "001.jpg", textCompare) < 0)
        assertTrue(naturalCompareWith("001.jpg", "002.jpg", textCompare) < 0)
    }

    @Test
    fun naturalCompareHandlesMixedTextAndNumbers() {
        assertTrue(naturalCompareWith("world1", "world2", textCompare) < 0)
        assertTrue(naturalCompareWith("world10", "world2", textCompare) > 0)
        assertTrue(naturalCompareWith("a2", "a10", textCompare) < 0)
    }

    @Test
    fun naturalCompareExtensionSolvesDigitPrefix() {
        // 纯数字/数字前缀路径不依赖 Android Collator，可直接测扩展函数
        assertTrue("10.jpg".naturalCompare("2.jpg") > 0)
        assertTrue("001.jpg".naturalCompare("010.jpg") < 0)
    }
}
