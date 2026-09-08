package io.legado.app.constant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.logging.Level

class AppLogFacadeTest {

    @Test
    fun structuredLevelsMapToFileLevels() {
        assertEquals(Level.INFO, AppLogLevel.INFO.javaLevel)
        assertEquals(Level.WARNING, AppLogLevel.WARN.javaLevel)
        assertEquals(Level.SEVERE, AppLogLevel.ERROR.javaLevel)
    }

    @Test
    fun structuredEntryRetainsFilterFields() {
        val error = IllegalStateException("broken")
        val entry = AppLogEntry(123L, AppLogLevel.ERROR, LogTag.IMPORT, "failed", error)

        assertEquals(123L, entry.time)
        assertEquals(AppLogLevel.ERROR, entry.level)
        assertEquals(LogTag.IMPORT, entry.tag)
        assertEquals("failed", entry.message)
        assertEquals(error, entry.throwable)
    }

    @Test
    fun centralTagsAreNonBlankAndUnique() {
        val tags = listOf(
            LogTag.APP,
            LogTag.APP_LOG,
            LogTag.CRASH,
            LogTag.DEVICE,
            LogTag.LIVE_EVENT_BUS,
            LogTag.MONITOR,
            LogTag.NETWORK,
            LogTag.JS,
            LogTag.TTS,
            LogTag.WEBDAV,
            LogTag.IMPORT,
            LogTag.MANGA,
            LogTag.BOOKSHELF,
            LogTag.TOC,
            LogTag.AI
        )

        assertTrue(tags.all(String::isNotBlank))
        assertEquals(tags.size, tags.toSet().size)
    }
}
