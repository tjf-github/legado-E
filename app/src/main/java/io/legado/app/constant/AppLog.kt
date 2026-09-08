package io.legado.app.constant

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.WebSettings
import io.legado.app.BuildConfig
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.LogUtils
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx
import java.util.logging.Level

enum class AppLogLevel {
    INFO,
    WARN,
    ERROR
}

data class AppLogEntry(
    val time: Long,
    val level: AppLogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
)

internal val AppLogLevel.javaLevel: Level
    get() = when (this) {
        AppLogLevel.INFO -> Level.INFO
        AppLogLevel.WARN -> Level.WARNING
        AppLogLevel.ERROR -> Level.SEVERE
    }

object AppLog {

    private const val MAX_LOGS = 100
    private val mLogs = arrayListOf<AppLogEntry>()

    val isEnabled: Boolean
        get() = AppConfig.recordLog

    @get:Synchronized
    val entries get() = mLogs.toList()

    /** Compatibility view used by the current log dialog. */
    @get:Synchronized
    val logs: List<Triple<Long, String, Throwable?>>
        get() = mLogs.map { Triple(it.time, it.message, it.throwable) }

    fun init(context: Context) {
        LogUtils.init(context, isEnabled)
    }

    fun onRecordLogChanged() {
        LogUtils.setEnabled(isEnabled)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        log(AppLogLevel.INFO, tag, message, throwable)
    }

    inline fun i(tag: String, lazyMessage: () -> String) {
        if (isEnabled) {
            i(tag, lazyMessage())
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(AppLogLevel.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(AppLogLevel.ERROR, tag, message, throwable)
    }

    fun toast(message: String?) {
        message?.let { appCtx.toastOnUi(it) }
    }

    @Deprecated("Use i/w/e and toast for new call sites")
    @Synchronized
    fun put(message: String?, throwable: Throwable? = null, toast: Boolean = false) {
        message ?: return
        if (toast) {
            toast(message)
        }
        append(
            AppLogEntry(
                System.currentTimeMillis(),
                AppLogLevel.INFO,
                LogTag.APP_LOG,
                message,
                throwable
            ),
            save = true,
            legacyDebug = true
        )
    }

    @Deprecated("Use i/w/e and toast for new call sites")
    @Synchronized
    fun putNotSave(message: String?, throwable: Throwable? = null, toast: Boolean = false) {
        message ?: return
        if (toast) {
            toast(message)
        }
        append(
            AppLogEntry(
                System.currentTimeMillis(),
                AppLogLevel.INFO,
                LogTag.APP_LOG,
                message,
                throwable
            ),
            save = false,
            legacyDebug = true
        )
    }

    @Deprecated("Use i with an explicit tag for new call sites")
    fun putDebug(message: String?, throwable: Throwable? = null) {
        if (isEnabled && message != null) {
            log(AppLogLevel.INFO, LogTag.APP_LOG, message, throwable)
        }
    }

    @Synchronized
    fun clear() {
        mLogs.clear()
    }

    fun logDeviceInfo() {
        i(LogTag.DEVICE) {
            buildString {
                kotlin.runCatching {
                    append("MANUFACTURER=").append(Build.MANUFACTURER).append("\n")
                    append("BRAND=").append(Build.BRAND).append("\n")
                    append("MODEL=").append(Build.MODEL).append("\n")
                    append("SDK_INT=").append(Build.VERSION.SDK_INT).append("\n")
                    append("RELEASE=").append(Build.VERSION.RELEASE).append("\n")
                    val userAgent = try {
                        WebSettings.getDefaultUserAgent(appCtx)
                    } catch (e: Throwable) {
                        e.toString()
                    }
                    append("WebViewUserAgent=").append(userAgent).append("\n")
                    append("packageName=").append(appCtx.packageName).append("\n")
                    append("heapSize=").append(Runtime.getRuntime().maxMemory()).append("\n")
                    AppConst.appInfo.let {
                        append("versionName=").append(it.versionName).append("\n")
                        append("versionCode=").append(it.versionCode).append("\n")
                    }
                }
            }
        }
    }

    private fun log(
        level: AppLogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null
    ) {
        if (!isEnabled) return
        append(
            AppLogEntry(System.currentTimeMillis(), level, tag, message, throwable),
            save = true,
            legacyDebug = false
        )
    }

    @Synchronized
    private fun append(entry: AppLogEntry, save: Boolean, legacyDebug: Boolean) {
        if (mLogs.size >= MAX_LOGS) {
            mLogs.removeLastOrNull()
        }
        if (save && isEnabled) {
            LogUtils.log(entry.level.javaLevel, entry.tag, entry.message, entry.throwable)
        }
        mLogs.add(0, entry)
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            val callerTag = resolveCallerTag(stackTrace)
            if (legacyDebug) {
                Log.e(callerTag, entry.message, entry.throwable)
            } else {
                when (entry.level) {
                    AppLogLevel.INFO -> Log.i(callerTag, entry.message, entry.throwable)
                    AppLogLevel.WARN -> Log.w(callerTag, entry.message, entry.throwable)
                    AppLogLevel.ERROR -> Log.e(callerTag, entry.message, entry.throwable)
                }
            }
        }
    }
}

internal fun resolveCallerTag(stackTrace: Array<StackTraceElement>): String {
    return stackTrace.getOrNull(3)?.className ?: AppLog::class.java.name
}
