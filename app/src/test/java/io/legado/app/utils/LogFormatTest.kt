package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.logging.Level
import java.util.logging.LogRecord

class LogFormatTest {

    @Test
    fun formatsStableDisplayLevelsForFileFiltering() {
        assertEquals(
            "26-09-07 12:00:00.000 [INFO] App started\n",
            formatLogLine(
                "26-09-07 12:00:00.000",
                LogRecord(Level.INFO, "App started")
            )
        )
        assertEquals(
            "26-09-07 12:00:00.000 [WARN] TTS retry\n",
            formatLogLine(
                "26-09-07 12:00:00.000",
                LogRecord(Level.WARNING, "TTS retry")
            )
        )
        assertEquals(
            "26-09-07 12:00:00.000 [ERROR] Import failed\n",
            formatLogLine(
                "26-09-07 12:00:00.000",
                LogRecord(Level.SEVERE, "Import failed")
            )
        )
    }
}
