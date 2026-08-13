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
}
