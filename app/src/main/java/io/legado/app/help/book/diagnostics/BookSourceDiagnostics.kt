package io.legado.app.help.book.diagnostics

import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 单个阶段的诊断快照。
 *
 * 只允许承载脱敏信息：阶段、错误分类、长度/数量/耗时摘要、以及已脱敏的说明。
 * 真实正文、Cookie、令牌和 URL 查询串一律不得进入本对象。
 */
data class StageSnapshot(
    val stage: DiagnosticStage,
    val errorClass: DiagnosticErrorClass,
    /** 由 [DiagnosticRedactor.summarizeText] 生成的 `len=..,lines=..,imgs=..`。 */
    val summary: String,
    val elapsedMs: Long,
    /** 该阶段是否真的在真实调用链里执行过；false 表示未执行/缺失。 */
    val executed: Boolean = true,
    /**
     * 未执行原因或补充说明。未执行原因来自调用方的固定文案，写入前经
     * [DiagnosticRedactor.sanitizeMessage] 过滤；异常说明只写异常类名，不引用异常消息。
     */
    val note: String? = null
) {

    /** 稳定、可断言的诊断行文本。 */
    fun format(): String {
        val builder = StringBuilder("【诊断】")
        builder.append(stage.order).append('.').append(stage.label).append(' ')
        if (!executed) {
            builder.append("未执行")
        } else {
            builder.append(errorClass.label)
        }
        if (summary.isNotEmpty()) {
            builder.append(' ').append(summary)
        }
        // 链路级异常兜底快照没有测得耗时，不写 0ms 冒充读数
        if (executed && elapsedMs > 0) {
            builder.append(" 耗时").append(elapsedMs).append("ms")
        }
        if (!note.isNullOrBlank()) {
            builder.append(" (").append(note).append(')')
        }
        return builder.toString()
    }
}

/**
 * 诊断接收端：一次书源调试会话一个实例。
 *
 * 边界（P1 返工后）：
 * - **会话身份**：只接受 [bindTarget] 绑定过的那个正文目标（书源 + 书 + 章节）写入，
 *   所以本地 TXT/EPUB、别的书、别的章节即使共用同一条正文管线也不会串进来；
 * - **阶段唯一**：同一阶段重复写入时后者覆盖前者，摘要里不出现同一阶段两行自相矛盾；
 *   但「未执行」标注必须走 [markNotExecutedIfMissing]，不得把已执行的真实读数覆盖掉；
 * - 全内存、不落盘、不联网；诊断关闭时任何方法都是空操作。
 */
class BookSourceDiagnosticsCollector(val isEnabled: Boolean = true) {

    private val snapshots = Collections.synchronizedList(ArrayList<StageSnapshot>())

    /** 阶段一/二的采集权：一个会话只由首个抓取页采集，并发分页只有一个调用能拿到。 */
    private val primaryStagesClaimed = AtomicBoolean(false)

    /** 阶段四真实读数补打摘要的采集权：一个会话只补打一次。 */
    private val readingStageReported = AtomicBoolean(false)

    /** 本次会话要调试的正文目标；未绑定前不接受任何阶段快照。 */
    @Volatile
    private var target: Target? = null

    /** 正文目标：书源、书、章节三级身份。[bookUrl] 允许为空（`--<url>` 正文调试入口）。 */
    data class Target(
        val bookSourceUrl: String?,
        val bookUrl: String?,
        val chapterUrl: String?
    )

    /** 绑定本次会话要调试的正文目标，须在正文抓取之前调用。 */
    fun bindTarget(bookSourceUrl: String?, bookUrl: String?, chapterUrl: String?) {
        target = Target(bookSourceUrl, bookUrl, chapterUrl)
    }

    /**
     * 判断一次正文调用是否属于本次会话。
     *
     * 书源与章节必须一致；会话目标带书号时书号也必须一致。因此本地书（origin 是 `loc_book`）、
     * 同一书源下的别的书、同一本书的别的章节都拿不到接收端。
     */
    fun accepts(bookSourceUrl: String?, bookUrl: String?, chapterUrl: String?): Boolean {
        val bound = target ?: return false
        val boundSource = bound.bookSourceUrl
        if (boundSource.isNullOrEmpty() || boundSource != bookSourceUrl) return false
        if (bound.chapterUrl.isNullOrEmpty() || bound.chapterUrl != chapterUrl) return false
        if (!bound.bookUrl.isNullOrEmpty() && bound.bookUrl != bookUrl) return false
        return true
    }

    /** 首个抓取页一次性拿到阶段一/二的采集权；重复调用返回 false。 */
    fun claimPrimaryStages(): Boolean = primaryStagesClaimed.compareAndSet(false, true)

    /**
     * 阶段四「展示时机」判定：现在是否应当补打一次含真实第四阶段读数的摘要。
     *
     * 三个条件同时成立才为 true，且一旦返回 true 就占用本次会话的补打权（重复调用返回 false）：
     * 1. 诊断开着；
     * 2. 阶段四有**真实执行过**的快照（未执行标注不算读数）；
     * 3. 本会话还没补打过——阅读处理与书源调试链路互不同步，同一章可能被多次处理
     *    （阅读、同章导出统计等都满足 [accepts]），补打必须收敛到一次。
     *
     * 会话是否还活着由调用方判断（回调在 Debug 手里），本方法不依赖任何 Android API，便于 JVM 契约测试。
     */
    fun shouldReportReadingStage(): Boolean {
        if (!isEnabled) return false
        if (snapshotOf(DiagnosticStage.READ_PROCESS)?.executed != true) return false
        return readingStageReported.compareAndSet(false, true)
    }

    /**
     * 记录阶段：内容摘要由本方法统一生成，调用方只传正文本身，不得传摘要字符串。
     *
     * @param text 真实产出，仅在方法内计算长度/数量，不进入 [StageSnapshot]。
     * @param error 该阶段抛出的异常（若有）。有异常时错误类别以异常为准。
     * @param timeoutMs 该阶段自身的超时阈值（替换类阶段才有）；0 表示不适用。
     */
    fun record(
        stage: DiagnosticStage,
        text: String?,
        elapsedMs: Long,
        error: Throwable? = null,
        timeoutMs: Long = 0L
    ) {
        if (!isEnabled) return
        var errorClass = if (error != null) {
            DiagnosticClassifier.classify(error, stage)
        } else {
            DiagnosticClassifier.classifyOutput(text, stage)
        }
        if (error == null && timeoutMs > 0 && elapsedMs > timeoutMs) {
            errorClass = DiagnosticErrorClass.REPLACE_TIMEOUT
        }
        val note = when {
            error != null -> describeError(error)
            errorClass == DiagnosticErrorClass.REPLACE_TIMEOUT ->
                "超出规则超时阈值 ${timeoutMs}ms"
            else -> null
        }
        record(
            StageSnapshot(
                stage = stage,
                errorClass = errorClass,
                summary = DiagnosticRedactor.summarizeText(text),
                elapsedMs = elapsedMs,
                executed = true,
                note = note
            )
        )
    }

    /**
     * 异常说明：只写稳定的异常类名，**绝不引用 message**。
     *
     * 异常消息是无界输入：RegexTimeoutException 的消息里带规则名、正则和整段待替换正文
     * （见 RegexExtensions.replace），其它异常也可能内嵌响应体、规则片段或正文。
     * [DiagnosticRedactor.sanitizeMessage] 只能删除「可证明的秘密形态」（URL 查询串、凭据赋值、Bearer），
     * 认不出普通正文，所以默认摘要一律不把 message 写进去；错误语义由错误类别承担。
     */
    private fun describeError(error: Throwable): String =
        "异常类型 " + error.javaClass.simpleName + "，消息默认不输出"

    /**
     * 只在该阶段**还没有任何快照**时才标「未执行」。
     *
     * 收尾兜底必须用本方法而不是 [markNotExecuted]：阅读路径可能已经真实执行过阶段四
     * （ContentProcessor.getContent），无条件标注会把它覆盖成「未执行」，等于反向伪造。
     */
    fun markNotExecutedIfMissing(stage: DiagnosticStage, reason: String) {
        if (!isEnabled) return
        if (snapshotOf(stage) != null) return
        markNotExecuted(stage, reason)
    }

    /** 显式记录「该阶段没有在真实调用链里执行」。 */
    fun markNotExecuted(stage: DiagnosticStage, reason: String) {
        if (!isEnabled) return
        record(
            StageSnapshot(
                stage = stage,
                errorClass = DiagnosticErrorClass.NONE,
                summary = "",
                elapsedMs = 0,
                executed = false,
                note = DiagnosticRedactor.sanitizeMessage(reason)
            )
        )
    }

    /**
     * 收尾兜底：本次会话里还没有任何快照的阶段一律标「未执行」。
     *
     * 用于书源调试链路没能走到某个阶段（含在提取前就失败）的情形，避免摘要看起来「四段齐全」。
     */
    fun markMissingStagesNotExecuted(reason: String) {
        if (!isEnabled) return
        DiagnosticStage.values().forEach { stage ->
            if (snapshotOf(stage) == null) {
                markNotExecuted(stage, reason)
            }
        }
    }

    /**
     * 链路级异常兜底：把异常归到链路实际停住的那一层。
     *
     * 抓取链路里已执行阶段中最靠后的一个就是「产出到这里为止」，因此把错误类别与说明补到它上面，
     * 并保留该阶段原有的摘要与耗时；一个阶段都没执行时归到阶段一。
     *
     * **阶段四不在候选里**：它由阅读路径（ContentProcessor）产生，与本次抓取链路的成败无关。
     * 把它算进来会把「抓取失败」贴到一个实际成功执行的阅读读数上（例如断网时同章的缓存阅读），
     * 摘要因此写出假读数。
     */
    fun recordFailure(error: Throwable) {
        if (!isEnabled) return
        val lastExecuted = DiagnosticStage.values().lastOrNull {
            it != DiagnosticStage.READ_PROCESS && snapshotOf(it)?.executed == true
        }
        if (lastExecuted == null) {
            record(DiagnosticStage.EXTRACT, text = null, elapsedMs = 0L, error = error)
            return
        }
        val snapshot = snapshotOf(lastExecuted) ?: return
        record(
            snapshot.copy(
                errorClass = DiagnosticClassifier.classify(error, lastExecuted),
                note = describeError(error)
            )
        )
    }

    /** 同一阶段只保留最后一条快照，摘要里不会出现同一阶段两行。 */
    fun record(snapshot: StageSnapshot) {
        if (!isEnabled) return
        synchronized(snapshots) {
            snapshots.removeAll { it.stage == snapshot.stage }
            snapshots.add(snapshot)
        }
    }

    fun snapshots(): List<StageSnapshot> = synchronized(snapshots) { ArrayList(snapshots) }

    fun snapshotOf(stage: DiagnosticStage): StageSnapshot? =
        synchronized(snapshots) { snapshots.firstOrNull { it.stage == stage } }

    fun clear() {
        synchronized(snapshots) { snapshots.clear() }
    }

    /** 按阶段顺序输出的诊断摘要行；缺的阶段不补造，由调用方显式 markNotExecuted。 */
    fun summaryLines(): List<String> =
        synchronized(snapshots) { snapshots.sortedBy { it.stage.order }.map { it.format() } }
}
