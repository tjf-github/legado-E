package io.legado.app.help.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookContent
import io.legado.app.utils.LogUtils
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.legado.app.model.ReadBook
import splitties.init.appCtx

/** 网络策略：默认仅在非计费网络才允许外发。以 Android 计费能力判断为准，不用 Wi-Fi 名称代替。 */
object AiNetworkPolicy {
    /** “仅非计费网络”开关，默认开启。 */
    fun onlyNonMeteredEnabled(): Boolean = appCtx.getPrefBoolean(PreferKey.aiOnlyNonMeteredNetwork, true)
    fun setOnlyNonMetered(enabled: Boolean) = appCtx.putPrefBoolean(PreferKey.aiOnlyNonMeteredNetwork, enabled)

    /** 综合判断：关闭“仅非计费”则总是允许（但仅在线文字与已授权章）；否则要求当前活动网络确实非计费。 */
    fun isAllowed(): Boolean = !onlyNonMeteredEnabled() || isActiveNetworkNonMetered()

    private fun isActiveNetworkNonMetered(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // API 21/22 仍可尝试，但计费能力不完整；保守判定为不允许外发。
            return false
        }
        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}

/**
 * 阅读/设置侧 Android 接线：把纯核心（协调器、配置仓库、密钥、服务）粘合成应用级单例。
 * 不写入任何真实密钥/正文；密钥仅经 [AiKeyStore] 保存为 Keystore 密文。
 */
object AiReaderAccess {
    @Volatile var readerActive = false
        private set
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val coordinatorDelegate = lazy {
        val runner = AiChapterRunner { scope, token, isCurrent, onProgress ->
            val snapshot = configs.current()
            service.process(scope.book, scope.chapterUrl, scope.chapterIndex, scope.original,
                scope.apiKey, token, scope.bookEnabled, scope.deviceConsentConfirmed, onProgress,
                isCurrent = {
                    isCurrent() && readerActive && configs.isCurrent(snapshot) &&
                        scope.configurationVersion == version(snapshot) && scope.book.getAiEnabled() &&
                        deviceConsent.isConfirmed(snapshot.config.serviceUrl) && AiNetworkPolicy.isAllowed()
                }, onRequest = { coordinator.recordRequest(token, it) })
        }
        AiChapterCoordinator(runner, coordinatorScope, failureReporter)
    }

    /** 无内容失败诊断日志：固定前缀 + 稳定分类码 + 可选 ANCHOR 枚举细分 + 可选 1-based 块号。
     * 走 [LogUtils]（java.util.logging）：R8 的 -assumenosideeffects android.util.Log 不会剥离它，
     * 因此 release 包也能在真机日志里检索到。不记录正文、响应体、异常 message、书名、书源、Cookie 或 URL。 */
    private val failureReporter = AiFailureReporter { code, failedChunkIndex, anchorDetail ->
        val block = failedChunkIndex?.let { " chunk=$it" } ?: ""
        val anchor = anchorDetail?.let { " anchor=${it.kind.name} edit=${it.effect.name}" } ?: ""
        LogUtils.d("AiFailure", "ai-fail code=${code.name}$anchor$block")
    }

    /** 全局配置仓库；配置变化时使在途 AI 任务失效并重置协调器。 */
    val configs: AiConfigRepository by lazy {
        AiConfigRepository(AndroidAiTextConfigStore(appCtx)) { _ ->
            runCatching {
                if (coordinatorDelegate.isInitialized()) coordinator.reset()
                uiScope.launch { ReadBook.resetAiDisplay() }
            }
        }
    }

    /** Keystore 包装的密钥存储；失效/不可用时禁用全局开关并要求重录。 */
    val keyStore: AiKeyStore by lazy {
        AndroidAiKeyStore.create(appCtx) {
            runCatching { configs.disable() }
        }
    }

    /** 设备外发确认；恢复备份/迁移后应重新确认。 */
    val deviceConsent: AiDeviceConsent by lazy {
        AiDeviceConsent(AndroidAiDeviceConsentStore(appCtx))
    }

    private val provider: OpenAiCompatibleProvider by lazy { OpenAiCompatibleProvider() }

    val service: AiChapterService by lazy {
        AiChapterService(provider, AiAndroidAccess.cache, configs)
    }

    private val coordinatorScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    val coordinator: AiChapterCoordinator get() = coordinatorDelegate.value
    private fun version(snapshot: AiConfigSnapshot) = "${snapshot.generation}:${snapshot.fingerprint}"

    /** 恢复备份后必须撤销本设备的外发确认；noBackup 仅解决跨设备，不自动覆盖同设备恢复。 */
    fun revokeDeviceConsent() {
        if (coordinatorDelegate.isInitialized()) coordinator.reset()
        deviceConsent.clear()
        uiScope.launch { ReadBook.resetAiDisplay() }
    }

    /** 组装当前章 AI 上下文；AI 不适用或未开启/未授权/网络不允许时返回 null（保持原正文）。 */
    fun buildScope(book: Book, chapterUrl: String, chapterIndex: Int, content: BookContent): AiChapterScope? {
        val snapshot = configs.current()
        if (!readerActive || !snapshot.config.enabled) return null
        val plainText = AiPlainText.isAiPlainText(book, content)
        if (!plainText) return null
        val keyState = keyStore.load()
        val apiKey = (keyState as? AiKeyState.Available)?.key ?: return null
        return AiChapterScope(
            book = book,
            chapterUrl = chapterUrl,
            chapterIndex = chapterIndex,
            original = content,
            bookEnabled = book.getAiEnabled(),
            deviceConsentConfirmed = deviceConsent.isConfirmed(snapshot.config.serviceUrl),
            allowNetwork = AiNetworkPolicy.isAllowed(),
            apiKey = apiKey,
            config = snapshot.config.toProviderConfig(),
            configurationVersion = version(snapshot)
        )
    }

    /** 全局配置开关是否可用（默认为 key 可用 + enabled）。 */
    fun isGlobalUsable(): Boolean = configs.current().config.enabled

    fun setBookEnabled(book: Book, enabled: Boolean) {
        if (!enabled) coordinator.reset()
        book.setAiEnabled(enabled)
    }

    fun getBookEnabled(book: Book): Boolean = book.getAiEnabled()

    fun setOnlyNonMetered(enabled: Boolean) = AiNetworkPolicy.setOnlyNonMetered(enabled)
    fun onlyNonMetered(): Boolean = AiNetworkPolicy.onlyNonMeteredEnabled()

    /** 固定测试连接：只发送固定测试文本，不发送书籍内容。 */
    suspend fun testConnection(): AiConnectionTestResult {
        val snapshot = configs.current()
        val keyState = keyStore.load()
        val apiKey = (keyState as? AiKeyState.Available)?.key ?: throw IllegalStateException("missing key")
        return testConnection(snapshot.config, apiKey)
    }

    suspend fun testConnection(config: AiTextConfig, apiKey: AiApiKey): AiConnectionTestResult =
        AiChapterProcessor.withRequestLock { provider.testConnection(config.toProviderConfig(), apiKey) }

    fun settingsChanged() {
        coordinator.reset()
        AiChapterProcessor.invalidateAll()
        uiScope.launch { ReadBook.resetAiDisplay() }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = checkNetwork()
        override fun onLost(network: Network) = checkNetwork()
    }
    private var monitoring = false
    private fun checkNetwork() {
        if (!AiNetworkPolicy.isAllowed()) {
            coordinator.cancel()
            uiScope.launch { ReadBook.resetAiDisplay() }
        }
    }
    fun setReaderActive(active: Boolean) {
        readerActive = active
        if (!active) coordinator.cancel()
        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching {
            if (active && !monitoring) {
                cm.registerNetworkCallback(NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), networkCallback)
                monitoring = true
            } else if (!active && monitoring) {
                cm.unregisterNetworkCallback(networkCallback)
                monitoring = false
            }
        }
    }

    suspend fun deleteChapter(book: Book, chapterUrl: String, index: Int, original: BookContent) {
        val snapshot = configs.current()
        val identity = AiChapterIdentity.build(book.bookUrl, chapterUrl, index,
            original.textList.joinToString("\n"), snapshot.config.toProviderConfig(),
            snapshot.generation, snapshot.fingerprint)
        kotlinx.coroutines.withContext(Dispatchers.IO) { AiAndroidAccess.cache.delete(identity) }
    }
}
