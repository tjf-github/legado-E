package io.legado.app.data.entities

import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReadConfigTest {

    @Test
    fun readConfigRoundTripAllFields() {
        val config = Book.ReadConfig(
            reverseToc = true,
            pageAnim = 3,
            reSegment = true,
            imageStyle = "cover",
            imageNoGap = false,
            imageGap = 2,
            useReplaceRule = true,
            delTag = 123L,
            ttsEngine = "test",
            splitLongChapter = false,
            readSimulating = true,
            startDate = LocalDate.of(2026, 8, 13)
        )
        val restored = GSON.fromJsonObject<Book.ReadConfig>(GSON.toJson(config)).getOrThrow()
        assertEquals(config, restored)
    }

    @Test
    fun readConfigDefaultImageNoGapIsNull() {
        // 旧数据/默认值：imageNoGap 为 null（表示跟随全局默认，阅读时按 true 处理）
        val config = Book.ReadConfig()
        assertNull(config.imageNoGap)
        val restored = GSON.fromJsonObject<Book.ReadConfig>(GSON.toJson(config)).getOrThrow()
        assertEquals(config, restored)
    }

    @Test
    fun readConfigImageNoGapCompatibility() {
        // 老数据没有 imageNoGap 字段：反序列化不崩溃且为 null
        val restored = GSON.fromJsonObject<Book.ReadConfig>("{}").getOrThrow()
        assertNull(restored.imageNoGap)
        // imageNoGap=false 显式存储时保持 false
        val noGap = GSON.fromJsonObject<Book.ReadConfig>(
            GSON.toJson(Book.ReadConfig(imageNoGap = false))
        ).getOrThrow()
        assertEquals(false, noGap.imageNoGap)
    }

    @Test
    fun readConfigMissingFieldsFallbackToDefaults() {
        val restored = GSON.fromJsonObject<Book.ReadConfig>(
            """{"reverseToc":true}"""
        ).getOrThrow()
        assertTrue(restored.reverseToc)
        assertNull(restored.pageAnim)
        assertEquals(true, restored.splitLongChapter)
    }

    @Test
    fun imageGapCompatibilityMapping() {
        val book = Book(bookUrl = "test://book")
        // 默认：无间隙（0）
        assertEquals(0, book.getImageGap())
        // 旧数据 imageNoGap=false（间距开启）→ 映射为中档 2
        book.setImageNoGap(false)
        assertEquals(2, book.getImageGap())
        // 新档位写入时同步 imageNoGap 兼容字段
        book.setImageGap(3)
        assertEquals(3, book.getImageGap())
        assertEquals(false, book.config.imageNoGap)
        book.setImageGap(0)
        assertEquals(0, book.getImageGap())
        assertEquals(true, book.config.imageNoGap)
    }

    @Test
    fun readConfigLegacyStartDateFormat() {
        // 旧数据：Gson 反射对象格式 {year,month,day}
        val restored = GSON.fromJsonObject<Book.ReadConfig>(
            """{"startDate":{"year":2026,"month":8,"day":13}}"""
        ).getOrThrow()
        assertEquals(LocalDate.of(2026, 8, 13), restored.startDate)
    }

    @Test
    fun readConfigStartDateRoundTripStringFormat() {
        // 新数据：ISO 字符串格式
        val config = Book.ReadConfig(startDate = LocalDate.of(2026, 8, 13))
        val json = GSON.toJson(config)
        assertTrue(json.contains("2026-08-13"))
        assertTrue(!json.contains("\"year\""))
        assertEquals(config, GSON.fromJsonObject<Book.ReadConfig>(json).getOrThrow())
    }
}
