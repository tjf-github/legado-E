package io.legado.app.ui.about

import io.legado.app.constant.AppLogEntry
import io.legado.app.constant.AppLogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppLogViewDataTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun parsesNewAndLegacyHeadersAndKeepsContinuationLines() {
        val entries = AppLogFileReader.parse(
            listOf(
                "26-09-07 12:00:00.000 [WARN] TTS retrying",
                "java.lang.IllegalStateException: broken",
                "\tat Reader.run(Reader.kt:10)",
                "26-09-07 12:00:01.000: [LiveEventBus] changed"
            )
        )

        assertEquals(2, entries.size)
        assertEquals(AppLogLevel.WARN, entries[0].level)
        assertEquals("TTS", entries[0].tag)
        assertTrue(entries[0].message.contains("IllegalStateException"))
        assertEquals(AppLogLevel.INFO, entries[1].level)
        assertEquals("LiveEventBus", entries[1].tag)
        assertEquals("changed", entries[1].message)
    }

    @Test
    fun filtersByLevelTagAndKeywordTogether() {
        val entries = listOf(
            AppLogEntry(1L, AppLogLevel.INFO, "Import", "scan complete"),
            AppLogEntry(2L, AppLogLevel.ERROR, "Import", "scan failed"),
            AppLogEntry(3L, AppLogLevel.ERROR, "TTS", "engine failed")
        )

        val result = filterLogEntries(
            entries,
            AppLogFilter(AppLogLevel.ERROR, "Import", "scan")
        )

        assertEquals(listOf(entries[1]), result)
    }

    @Test
    fun tailReaderDoesNotReturnOlderLinesPastLimit() {
        val file = tempFolder.newFile("appLog-test.txt")
        file.writeText((1..20).joinToString("\n") { "line-$it" })

        val lines = AppLogFileReader.readTailLines(file, maxLines = 3)

        assertEquals(listOf("line-18", "line-19", "line-20"), lines)
        assertFalse(lines.contains("line-17"))
    }

    @Test
    fun copiedTextIncludesLevelTagMessageAndThrowable() {
        val error = IllegalArgumentException("bad input")
        val text = formatLogEntries(
            listOf(AppLogEntry(0L, AppLogLevel.ERROR, "Import", "failed", error))
        )

        assertTrue(text.contains("[ERROR] Import failed"))
        assertTrue(text.contains("IllegalArgumentException: bad input"))
    }
}
