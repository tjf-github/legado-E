package io.legado.app.ui.about

import io.legado.app.constant.AppLog
import io.legado.app.constant.LogTag
import io.legado.app.utils.FileDoc
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.delete
import io.legado.app.utils.externalCache
import io.legado.app.utils.find
import io.legado.app.utils.openOutputStream
import splitties.init.appCtx
import java.io.File

internal object LogExport {

    fun copyLogs(destination: FileDoc) {
        val cacheDir = appCtx.externalCache
        val logFiles = File(cacheDir, "logs")
        val crashFiles = File(cacheDir, "crash")
        val logcatFile = File(cacheDir, "logcat.txt")
        dumpLogcat(logcatFile)

        val zipFile = File(cacheDir, "logs.zip")
        ZipUtils.zipFiles(arrayListOf(logFiles, crashFiles, logcatFile), zipFile)
        try {
            destination.find("logs.zip")?.delete()
            zipFile.inputStream().use { input ->
                destination.createFileIfNotExist("logs.zip")
                    .openOutputStream()
                    .getOrThrow()
                    .use(input::copyTo)
            }
        } finally {
            zipFile.delete()
        }
    }

    private fun dumpLogcat(file: File) {
        try {
            val process = Runtime.getRuntime().exec("logcat -d")
            file.outputStream().use(process.inputStream::copyTo)
        } catch (error: Exception) {
            AppLog.w(LogTag.APP_LOG, "保存 Logcat 失败", error)
        }
    }
}
