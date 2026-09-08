@file:Suppress("unused")

package io.legado.app.utils

import android.annotation.SuppressLint
import android.content.Context
import io.legado.app.BuildConfig
import io.legado.app.constant.AppLog
import io.legado.app.help.globalExecutor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.time.Duration.Companion.days

@SuppressLint("SimpleDateFormat")
@Suppress("unused")
internal object LogUtils {
    const val TIME_PATTERN = "yy-MM-dd HH:mm:ss.SSS"
    val logTimeFormat by lazy { SimpleDateFormat(TIME_PATTERN) }

    internal fun init(context: Context, enabled: Boolean) {
        fileHandler = createFileHandler(context, enabled)?.also {
            logger.addHandler(it)
        }
    }

    @JvmStatic
    fun d(tag: String, msg: String) {
        log(Level.INFO, tag, msg)
    }

    inline fun d(tag: String, lazyMsg: () -> String) {
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "$tag ${lazyMsg()}")
        }
    }

    @JvmStatic
    fun e(tag: String, msg: String) {
        log(Level.SEVERE, tag, msg)
    }

    internal fun log(level: Level, tag: String, msg: String, throwable: Throwable? = null) {
        val text = if (throwable == null) {
            "$tag $msg"
        } else {
            "$tag $msg\n${throwable.stackTraceToString()}"
        }
        logger.log(level, text)
    }

    val logger: Logger by lazy {
        Logger.getLogger("Legado")
    }

    private var fileHandler: FileHandler? = null

    private fun createFileHandler(context: Context, enabled: Boolean): FileHandler? {
        try {
            val root = context.externalCacheDir ?: return null
            val logFolder = FileUtils.createFolderIfNotExist(root, "logs")
            globalExecutor.execute {
                val expiredTime = System.currentTimeMillis() - 7.days.inWholeMilliseconds
                logFolder.listFiles()?.forEach {
                    if (it.lastModified() < expiredTime || it.name.endsWith(".lck")) {
                        it.delete()
                    }
                }
            }
            val date = getCurrentDateStr(TIME_PATTERN).replace(" ", "_").replace(":", "-")
            val logPath = FileUtils.getPath(root = logFolder, "appLog-$date.txt")
            return AsyncFileHandler(logPath).apply {
                formatter = object : java.util.logging.Formatter() {
                    override fun format(record: LogRecord): String {
                        return formatLogLine(getCurrentDateStr(TIME_PATTERN), record)
                    }
                }
                level = if (enabled) {
                    Level.INFO
                } else {
                    Level.OFF
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            AppLog.putNotSave("创建fileHandler出错\n$e", e)
            return null
        }
    }

    internal fun setEnabled(enabled: Boolean) {
        val level = if (enabled) {
            Level.INFO
        } else {
            Level.OFF
        }
        fileHandler?.level = level
    }

    /**
     * 获取当前时间
     */
    @SuppressLint("SimpleDateFormat")
    fun getCurrentDateStr(pattern: String): String {
        val date = Date()
        val sdf = SimpleDateFormat(pattern)
        return sdf.format(date)
    }

}

internal fun formatLogLine(timestamp: String, record: LogRecord): String {
    val level = when {
        record.level.intValue() >= Level.SEVERE.intValue() -> "ERROR"
        record.level.intValue() >= Level.WARNING.intValue() -> "WARN"
        else -> "INFO"
    }
    return "$timestamp [$level] ${record.message}\n"
}

fun Throwable.printOnDebug() {
    if (BuildConfig.DEBUG) {
        printStackTrace()
    }
}
