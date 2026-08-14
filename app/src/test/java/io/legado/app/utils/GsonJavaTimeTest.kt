package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class GsonJavaTimeTest {

    @Test
    fun localDateRoundTripStringFormat() {
        val date = LocalDate.of(2026, 8, 13)
        val json = GSON.toJson(date)
        assertEquals("\"2026-08-13\"", json)
        assertEquals(date, GSON.fromJsonObject<LocalDate>(json).getOrThrow())
    }

    @Test
    fun localDateLegacyObjectFormat() {
        // 旧数据：Gson 反射私有字段产生的对象格式
        val restored = GSON.fromJsonObject<LocalDate>(
            """{"year":2026,"month":8,"day":13}"""
        ).getOrThrow()
        assertEquals(LocalDate.of(2026, 8, 13), restored)
    }

    @Test
    fun localDateNull() {
        assertNull(GSON.fromJsonObject<LocalDate>("null").getOrNull())
    }

    @Test
    fun localDateTimeRoundTripAndLegacy() {
        val dateTime = LocalDateTime.of(2026, 8, 13, 10, 30, 5, 123)
        val json = GSON.toJson(dateTime)
        assertTrue(json.startsWith("\"2026-08-13T10:30:05"))
        assertEquals(dateTime, GSON.fromJsonObject<LocalDateTime>(json).getOrThrow())
        // 旧数据：嵌套对象格式
        val legacy = GSON.fromJsonObject<LocalDateTime>(
            """{"date":{"year":2026,"month":8,"day":13},"time":{"hour":10,"minute":30,"second":5,"nano":123}}"""
        ).getOrThrow()
        assertEquals(dateTime, legacy)
    }

    @Test
    fun localTimeRoundTripAndLegacy() {
        val time = LocalTime.of(10, 30, 5, 123)
        val json = GSON.toJson(time)
        assertEquals("\"10:30:05.000000123\"", json)
        assertEquals(time, GSON.fromJsonObject<LocalTime>(json).getOrThrow())
        // 旧数据：对象格式
        val legacy = GSON.fromJsonObject<LocalTime>(
            """{"hour":10,"minute":30,"second":5,"nano":123}"""
        ).getOrThrow()
        assertEquals(time, legacy)
    }
}
