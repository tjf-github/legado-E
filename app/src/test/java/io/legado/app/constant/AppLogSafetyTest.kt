package io.legado.app.constant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class AppLogSafetyTest {

    @Test
    fun logsSnapshotGetterIsSynchronized() {
        val getter = AppLog::class.java.getMethod("getLogs")

        assertTrue(Modifier.isSynchronized(getter.modifiers))
    }

    @Test
    fun callerTagUsesExpectedFrameWhenAvailable() {
        val stackTrace = Array(4) { index ->
            StackTraceElement("Caller$index", "method", "Caller.kt", index)
        }

        assertEquals("Caller3", resolveCallerTag(stackTrace))
    }

    @Test
    fun callerTagFallsBackForShallowStack() {
        assertEquals(AppLog::class.java.name, resolveCallerTag(emptyArray()))
    }
}
