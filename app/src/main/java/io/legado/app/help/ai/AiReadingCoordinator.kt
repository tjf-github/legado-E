package io.legado.app.help.ai

import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 当前章阅读页可显示的简短状态。不阻塞基本阅读。 */
sealed interface AiChapterUiState {
    /** 未处理 / 已取消 / AI 不适用。 */
    data object Idle : AiChapterUiState

    /** 处理中：done/total 块。 */
    data class Processing(val done: Int, val total: Int) : AiChapterUiState

    /** “AI 正文已就绪”：完整候选已生成，等待用户主动切换。 */
    data object Ready : AiChapterUiState

    /** 失败：本章回退原文。code 为稳定脱敏失败分类，failedChunkIndex 仅当失败可明确归因于某一块时
     * 携带 1-based 块序号（provider 调用失败、该块输出校验失败）；章级/提交级失败为 null。
     * anchorDetail 仅在 ANCHOR 时携带固定的出现次数/是否改字枚举；三者都不包含正文、响应体或密钥。 */
    data class Failed(
        val code: AiFailureCode,
        val failedChunkIndex: Int? = null,
        val anchorDetail: AiAnchorFailureDetail? = null
    ) : AiChapterUiState

    val inProgress: Boolean get() = this is Processing
}

/**
 * 一次当前章 AI 处理所需的、与书/章/输入只读绑定的不变上下文。
 * 由调用方（Android）组装，协调器核心不触碰 Activity/KeyStore/ConnectivityManager。
 *
 * @param bookEnabled 本书 AI 开关已开（存在 Book.ReadConfig 可选字段）。
 * @param deviceConsentConfirmed 用户已对本设备 + 当前服务地址确认正文外发。
 * @param allowNetwork 网络策略允许（默认仅非计费网络）。
 */
data class AiChapterScope(
    val book: Book,
    val chapterUrl: String,
    val chapterIndex: Int,
    val original: BookContent,
    val bookEnabled: Boolean,
    val deviceConsentConfirmed: Boolean,
    val allowNetwork: Boolean,
    val apiKey: AiApiKey,
    val config: AiProviderConfig,
    val configurationVersion: String = ""
) {
    /** 用于“刷新进度/切换章”是否要作废旧任务的身份：书 + 章 + 输入哈希。
     * 输入变化（正文或规则变化）同样作废旧任务。 */
    val resetKey: String = AiCacheKey.sha256(
        listOf(book.bookUrl, chapterUrl, chapterIndex.toString(),
            AiCacheKey.sha256(original.textList.joinToString("\n")), config.toString(), configurationVersion,
            bookEnabled.toString(), deviceConsentConfirmed.toString(), allowNetwork.toString())
            .joinToString("") { "${it.length}:$it" }
    )
    override fun toString() = "AiChapterScope(identity=$resetKey)"
}

data class AiRequestStats(val requestCount: Int = 0, val sentChars: Long = 0)

/** 供协调器调用的一次处理入口：生产接 AiChapterService，测试用 Fake。
 * 必须先携带 [onProgress] 汇报块进度；[isCurrent] 每次落盘/提交/刷新前都要再核对。 */
fun interface AiChapterRunner {
    suspend fun process(
        scope: AiChapterScope,
        token: AiTaskToken,
        isCurrent: () -> Boolean,
        onProgress: (done: Int, total: Int) -> Unit
    ): AiProcessResult
}

/**
 * 当前章阅读协调器（只在 [jobScope] 内发一次串行处理，不直接拼 HTTP）。
 *
 * 语义（与功能书 §4.1 / §3.3 一致）：
 *  - [resolve] 对当前章立即返回原文用于排版，绝不阻塞在网络；仅当可用且未完成时在后台启动处理；
 *  - 处理期间始终显示原文，完成只切换到 [AiChapterUiState.Ready]，由用户主动 [showAi]；
 *  - 切章 / 离开 / 关闭功能 / 配置变化：调用 [reset] 使 token 失效并丢弃迟到结果；
 *  - 失败 / 取消不自动重试（[cancel] 置位后不自动重启），重新处理用 [retry]/[reprocess]；
 *  - 任何写回、提交、刷新 UI 前用 [isCurrent] 复核当前任务身份，避免串章。
 *
 * 不写回原章节缓存、不改变原文、导出、搜索、朗读读取路径。
 */
class AiChapterCoordinator(
    private val runner: AiChapterRunner,
    private val jobScope: CoroutineScope,
    /**
     * 失败诊断入口：仅在状态首次落到 Failed 时调用一次。
     * 不携带正文、响应体或密钥；ANCHOR 可附固定枚举细分。生产用日志实现，JVM 测试用记录器证明。
     */
    private val failureReporter: AiFailureReporter = AiFailureReporter { _, _, _ -> }
) {
    private val _state = MutableStateFlow<AiChapterUiState>(AiChapterUiState.Idle)
    val state: StateFlow<AiChapterUiState> = _state.asStateFlow()

    private val _displaySource = MutableStateFlow(AiDisplaySource.Original)
    val displaySource: StateFlow<AiDisplaySource> = _displaySource.asStateFlow()
    private val _stats = MutableStateFlow(AiRequestStats())
    val stats: StateFlow<AiRequestStats> = _stats.asStateFlow()

    fun recordRequest(token: AiTaskToken, chars: Int) = synchronized(lock) {
        if (currentToken === token) {
            _stats.value = AiRequestStats(_stats.value.requestCount + 1, _stats.value.sentChars + chars)
        }
    }

    @Volatile private var currentToken: AiTaskToken = AiTaskToken()
    /** 当前章的 token；切章/重置后换新，旧的交给在途任务失效。 */
    val token: AiTaskToken get() = currentToken

    @Volatile private var scope: AiChapterScope? = null
    @Volatile private var completed: String? = null
    @Volatile private var started = false
    private var job: Job? = null
    private val lock = Any()

    /** 是否已有可展示的完整候选。 */
    val hasCompleted: Boolean get() = synchronized(lock) { completed != null }

    /** 完整检查后的候选与原文相同（也适用于缓存命中），不意味着处理失败。 */
    val completedWithoutChanges: Boolean get() = synchronized(lock) {
        completed != null && completed == scope?.original?.textList?.joinToString("\n")
    }

    /** 是否已对当前章发起处理（用于 UI 判断是否可取消）。 */
    val isProcessing: Boolean get() = synchronized(lock) { job?.isActive == true }

    /**
     * 决定本次排版用什么正文。只处理当前章（[isCurrent]）；相邻章返回原文不触发请求。
     * @param original 现成的原文章节正文（ContentProcessor 输出）。
     * @param scope 当前章 AI 上下文；为 null 表示 AI 不适用（本地/结构化/全局关/未授权/无密钥）。
     * @param isCurrent 该章是否为当前阅读章（offset == 0）。
     */
    suspend fun resolve(original: BookContent, scope: AiChapterScope?, isCurrent: Boolean): AiDisplayContent {
        if (!isCurrent) {
            return AiDisplayContent.original(original)
        }
        if (scope == null) {
            reset()
            return AiDisplayContent.original(original)
        }
        val ready = scope.bookEnabled && scope.deviceConsentConfirmed && scope.allowNetwork &&
            AiPlainText.isAiPlainText(scope.book, scope.original)
        if (!ready) {
            reset()
            return AiDisplayContent.original(original)
        }
        return synchronized(lock) {
        bind(scope)
        val aiSelectedText = synchronized(lock) {
            if (completed != null && _displaySource.value == AiDisplaySource.Ai) completed else null
        }
        if (aiSelectedText != null) {
            val candidate = scope.original.withAiText(aiSelectedText)
            return@synchronized AiDisplayContent(scope.original, candidate, AiDisplaySource.Ai, currentToken)
        }
        startIfNeeded(scope)
        AiDisplayContent.original(original)
        }
    }

    /** 用户取消当前章处理：立即失效并恢复原文；不自动重启。 */
    fun cancel() {
        synchronized(lock) {
            currentToken.invalidate()
            currentToken = AiTaskToken()
            job?.cancel()
            job = null
            started = true // 阻止 resolve 自动重启
            completed = null
            _displaySource.value = AiDisplaySource.Original
            _state.value = AiChapterUiState.Idle
        }
    }

    /** 失败 / 取消后由用户显式重试同一本章。 */
    fun retry() {
        restart()
    }

    /** 重新处理本章：先删该章 AI 缓存再重跑（缓存删除由 Android 侧的 runner 负责）。 */
    fun reprocess() {
        restart()
    }

    /** 用户主动切回原文。 */
    fun showOriginal() {
        synchronized(lock) { _displaySource.value = AiDisplaySource.Original }
    }

    /** 用户主动切换到完整 AI 结果；仅当候选存在时才生效。 */
    fun showAi() {
        synchronized(lock) {
            if (completed != null) _displaySource.value = AiDisplaySource.Ai
        }
    }

    /** 切章 / 离开本书 / 关闭全局或本书开关：作废旧任务并恢复原文。 */
    fun reset() {
        synchronized(lock) {
            currentToken.invalidate()
            currentToken = AiTaskToken()
            job?.cancel()
            job = null
            scope = null
            completed = null
            started = false
            _stats.value = AiRequestStats()
            _displaySource.value = AiDisplaySource.Original
            _state.value = AiChapterUiState.Idle
        }
    }

    private fun bind(scope: AiChapterScope) {
        val changed = synchronized(lock) { this.scope?.resetKey != scope.resetKey }
        if (changed) {
            synchronized(lock) {
                currentToken.invalidate()
                currentToken = AiTaskToken()
                job?.cancel()
                job = null
                this.scope = scope
                completed = null
                started = false
                _stats.value = AiRequestStats()
                _displaySource.value = AiDisplaySource.Original
                _state.value = AiChapterUiState.Idle
            }
        } else {
            synchronized(lock) { this.scope = scope }
        }
    }

    private fun startIfNeeded(scope: AiChapterScope) = synchronized(lock) {
        val shouldStart = synchronized(lock) {
            if (started || job?.isActive == true || !scope.bookEnabled || !scope.deviceConsentConfirmed || !scope.allowNetwork) {
                false
            } else {
                started = true
                _state.value = AiChapterUiState.Processing(0, 0)
                true
            }
        }
        if (!shouldStart) return@synchronized
        val taskToken = currentToken
        job = jobScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = runner.process(
                    scope,
                    taskToken,
                    isCurrent = { isStillCurrent(scope) && currentToken === taskToken },
                    onProgress = { done, total ->
                        synchronized(lock) {
                            if (isStillCurrentUnsafe(scope) && exactToken(taskToken)) {
                                _state.value = AiChapterUiState.Processing(done, total)
                            }
                        }
                    }
                )
                currentCoroutineContext().ensureActive()
                synchronized(lock) {
                    if (!isStillCurrentUnsafe(scope) || !exactToken(taskToken)) return@launch
                    when (result) {
                        is AiProcessResult.Completed -> {
                            completed = result.text
                            _state.value = AiChapterUiState.Ready
                        }
                        is AiProcessResult.Failed -> settleFailed(
                            result.code,
                            result.failedChunkIndex,
                            result.anchorDetail
                        )
                        is AiProcessResult.Original -> _state.value = AiChapterUiState.Idle
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                synchronized(lock) {
                    if (isStillCurrentUnsafe(scope) && exactToken(taskToken)) {
                        settleFailed(AiFailureCode.INTERNAL, null, null)
                    }
                }
            }
        }.also { it.start() }
    }

    private fun restart() {
        synchronized(lock) {
            val current = scope ?: return
            currentToken.invalidate()
            currentToken = AiTaskToken()
            job?.cancel()
            job = null
            completed = null
            started = false
            _stats.value = AiRequestStats()
            _displaySource.value = AiDisplaySource.Original
        startIfNeeded(current)
        }
    }

    private fun exactToken(taskToken: AiTaskToken): Boolean = currentToken === taskToken

    /**
     * 失败状态“首次落定”：只有从非 Failed 进入 Failed（或失败可归因信息变化）时才上报一次，
     * 避免重复设置同一失败状态导致的重复日志。上报仅携带分类码、可选块号与 ANCHOR 枚举细分，
     * 不含正文/响应体/密钥。
     */
    private fun settleFailed(
        code: AiFailureCode,
        failedChunkIndex: Int?,
        anchorDetail: AiAnchorFailureDetail?
    ) {
        val previous = _state.value
        if (previous is AiChapterUiState.Failed && previous.code == code &&
            previous.failedChunkIndex == failedChunkIndex && previous.anchorDetail == anchorDetail
        ) {
            return
        }
        _state.value = AiChapterUiState.Failed(code, failedChunkIndex, anchorDetail)
        failureReporter.report(code, failedChunkIndex, anchorDetail)
    }

    private fun isStillCurrentUnsafe(scope: AiChapterScope): Boolean =
        this.scope?.resetKey == scope.resetKey

    private fun isStillCurrent(scope: AiChapterScope): Boolean =
        isStillCurrentUnsafe(scope)
}
