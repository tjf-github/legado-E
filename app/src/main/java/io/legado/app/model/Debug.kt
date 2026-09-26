package io.legado.app.model

import android.annotation.SuppressLint
import android.util.Log
import io.legado.app.BuildConfig
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.*
import io.legado.app.help.book.diagnostics.BookSourceDiagnosticsCollector
import io.legado.app.help.book.diagnostics.DiagnosticStage
import io.legado.app.help.book.isWebFile
import io.legado.app.help.coroutine.CompositeCoroutine
import io.legado.app.help.source.sortUrls
import io.legado.app.model.rss.Rss
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import java.text.SimpleDateFormat
import java.util.*

object Debug {
    var callback: Callback? = null
    private var debugSource: String? = null
    private val tasks: CompositeCoroutine = CompositeCoroutine()
    val debugMessageMap = HashMap<String, String>()
    private val debugTimeMap = HashMap<String, Long>()
    var isChecking: Boolean = false

    /**
     * 正文四阶段诊断的接收端。只在书源调试会话期间非空；为空表示诊断关闭，
     * 正文管线不得因此产生额外内容处理或持久化。
     *
     * 主线程建立/清空、IO 线程读取，故用 @Volatile 保证可见性；外部只能经
     * [diagnosticsFor] 取匹配目标的接收端，不直接读本字段。
     */
    @Volatile
    private var diagnostics: BookSourceDiagnosticsCollector? = null

    @SuppressLint("ConstantLocale")
    private val debugTimeFormat = SimpleDateFormat("[mm:ss.SSS]", Locale.getDefault())
    private var startTime: Long = System.currentTimeMillis()

    @Synchronized
    fun log(
        sourceUrl: String?,
        msg: String = "",
        print: Boolean = true,
        isHtml: Boolean = false,
        showTime: Boolean = true,
        state: Int = 1
    ) {
        if (BuildConfig.DEBUG) {
            Log.d("sourceDebug", msg)
        }
        //调试信息始终要执行
        callback?.let {
            if ((debugSource != sourceUrl || !print)) return
            var printMsg = msg
            if (isHtml) {
                printMsg = HtmlFormatter.format(msg)
            }
            if (showTime) {
                val time = debugTimeFormat.format(Date(System.currentTimeMillis() - startTime))
                printMsg = "$time $printMsg"
            }
            it.printLog(state, printMsg)
        }
        if (isChecking && sourceUrl != null && (msg).length < 30) {
            var printMsg = msg
            if (isHtml) {
                printMsg = HtmlFormatter.format(msg)
            }
            if (showTime && debugTimeMap[sourceUrl] != null) {
                val time =
                    debugTimeFormat.format(Date(System.currentTimeMillis() - debugTimeMap[sourceUrl]!!))
                printMsg = printMsg.replace(AppPattern.debugMessageSymbolRegex, "")

                debugMessageMap[sourceUrl] = "$time $printMsg"
            }
        }
    }

    @Synchronized
    fun log(msg: String?) {
        log(debugSource, msg ?: "", true)
    }

    /**
     * 开启一次书源调试会话。
     *
     * 诊断接收端随会话建立，被正文管线的四个阶段共享；此时才允许产出阶段快照。
     * 会话身份由接收端自己持有：没有绑定正文目标之前，任何阶段写入都被拒绝。
     */
    fun startBookSourceDebugSession() {
        diagnostics = BookSourceDiagnosticsCollector()
    }

    /**
     * 把本次会话要调试的正文目标（书源 + 书 + 章节）绑给接收端。
     *
     * 只有匹配这个目标的正文调用才拿得到接收端，本地书/别的书/别的章节不会串进本次摘要。
     */
    fun bindDiagnosticsTarget(bookSourceUrl: String?, bookUrl: String?, chapterUrl: String?) {
        diagnostics?.bindTarget(bookSourceUrl, bookUrl, chapterUrl)
    }

    /**
     * 取当前调试会话里匹配该正文目标的接收端；未开启诊断、未绑定目标或不匹配时返回 null。
     */
    fun diagnosticsFor(
        bookSourceUrl: String?,
        bookUrl: String?,
        chapterUrl: String?
    ): BookSourceDiagnosticsCollector? {
        val collector = diagnostics ?: return null
        if (!collector.isEnabled) return null
        return collector.takeIf { it.accepts(bookSourceUrl, bookUrl, chapterUrl) }
    }

    /**
     * 输出本次会话收集到的四阶段诊断摘要。
     *
     * 缺哪个阶段就明确写「未执行」，不以其它阶段的快照冒充。
     */
    fun printBookSourceDiagnostics() {
        val collector = diagnostics ?: return
        collector.summaryLines().forEach { log(debugSource, it, showTime = false) }
    }

    fun cancelDebug(destroy: Boolean = false) {
        tasks.clear()

        if (destroy) {
            debugSource = null
            callback = null
            diagnostics = null
        }
    }

    fun startChecking(source: BookSource) {
        isChecking = true
        debugTimeMap[source.bookSourceUrl] = System.currentTimeMillis()
        debugMessageMap[source.bookSourceUrl] = "${debugTimeFormat.format(Date(0))} 开始校验"
    }

    fun finishChecking() {
        isChecking = false
    }

    fun getRespondTime(sourceUrl: String): Long {
        return debugTimeMap[sourceUrl] ?: CheckSource.timeout
    }

    fun updateFinalMessage(sourceUrl: String, state: String) {
        if (debugTimeMap[sourceUrl] != null && debugMessageMap[sourceUrl] != null) {
            val spendingTime = System.currentTimeMillis() - debugTimeMap[sourceUrl]!!
            debugTimeMap[sourceUrl] =
                if (state == "校验成功") spendingTime else CheckSource.timeout + spendingTime
            val printTime = debugTimeFormat.format(Date(spendingTime))
            debugMessageMap[sourceUrl] = "$printTime $state"
        }
    }

    suspend fun startDebug(scope: CoroutineScope, rssSource: RssSource) {
        cancelDebug()
        debugSource = rssSource.sourceUrl
        log(debugSource, "︾开始解析")
        val sort = rssSource.sortUrls().first()
        Rss.getArticles(scope, sort.first, sort.second, rssSource, 1)
            .onSuccess {
                if (it.first.isEmpty()) {
                    log(debugSource, "⇒列表页解析成功，为空")
                    log(debugSource, "︽解析完成", state = 1000)
                } else {
                    val ruleContent = rssSource.ruleContent
                    if (!rssSource.ruleArticles.isNullOrBlank() && rssSource.ruleDescription.isNullOrBlank()) {
                        log(debugSource, "︽列表页解析完成")
                        log(debugSource, showTime = false)
                        if (ruleContent.isNullOrEmpty()) {
                            log(debugSource, "⇒内容规则为空，默认获取整个网页", state = 1000)
                        } else {
                            rssContentDebug(scope, it.first[0], ruleContent, rssSource)
                        }
                    } else {
                        log(debugSource, "⇒存在描述规则，不解析内容页")
                        log(debugSource, "︽解析完成", state = 1000)
                    }
                }
            }
            .onError {
                log(debugSource, it.stackTraceStr, state = -1)
            }
    }

    fun startDebug(scope: CoroutineScope, rssSource: RssSource, key: String) {
        cancelDebug()
        debugSource = rssSource.sourceUrl
        startTime = System.currentTimeMillis()
        when {
            key.contains("::") -> {
                val name = key.substringBefore("::")
                val url = key.substringAfter("::")
                log(debugSource, "⇒开始访问分类页:$url")
                log(debugSource, "︾开始解析分类页")
                sortDebug(scope, rssSource, name, url)
            }

            key.isAbsUrl() -> {
                val ruleContent = rssSource.ruleContent
                if (!rssSource.ruleArticles.isNullOrBlank() && rssSource.ruleDescription.isNullOrBlank()) {
                    if (ruleContent.isNullOrEmpty()) {
                        log(debugSource, "⇒内容规则为空，默认获取整个网页", state = 1000)
                    } else {
                        val rssArticle = RssArticle()
                        rssArticle.origin = rssSource.sourceUrl
                        rssArticle.link = key
                        log(debugSource, "⇒开始访问内容页:$key")
                        rssContentDebug(scope, rssArticle, ruleContent, rssSource)
                    }
                } else {
                    log(debugSource, "⇒存在描述规则，不解析内容页")
                    log(debugSource, "︽解析完成", state = 1000)
                }
            }

            else -> {
                val searchUrl = rssSource.searchUrl
                if (searchUrl.isNullOrEmpty()) {
                    log(debugSource, "⇒搜索URL为空", state = -1)
                    return
                }
                log(debugSource, "⇒开始搜索关键字:$key")
                log(debugSource, "︾开始解析搜索页")
                sortDebug(scope, rssSource, "搜索", searchUrl, key)
            }
        }
    }

    private fun sortDebug(scope: CoroutineScope, rssSource: RssSource, name: String, url: String, key: String? = null) {
        Rss.getArticles(scope, name, url, rssSource, 1, key)
            .onSuccess {
                if (it.first.isEmpty()) {
                    log(debugSource, "⇒列表页解析成功，为空")
                    log(debugSource, "︽解析完成", state = 1000)
                } else {
                    val ruleContent = rssSource.ruleContent
                    if (!rssSource.ruleArticles.isNullOrBlank() && rssSource.ruleDescription.isNullOrBlank()) {
                        log(debugSource, "︽列表页解析完成")
                        log(debugSource, showTime = false)
                        if (ruleContent.isNullOrEmpty()) {
                            log(debugSource, "⇒内容规则为空，默认获取整个网页", state = 1000)
                        } else {
                            rssContentDebug(scope, it.first[0], ruleContent, rssSource)
                        }
                    } else {
                        log(debugSource, "⇒存在描述规则，不解析内容页")
                        log(debugSource, "︽解析完成", state = 1000)
                    }
                }
            }
            .onError {
                log(debugSource, it.stackTraceStr, state = -1)
            }
    }

    private fun rssContentDebug(
        scope: CoroutineScope,
        rssArticle: RssArticle,
        ruleContent: String,
        rssSource: RssSource
    ) {
        log(debugSource, "︾开始解析内容页")
        Rss.getContent(scope, rssArticle, ruleContent, rssSource)
            .onSuccess {
                log(debugSource, it)
                log(debugSource, "︽内容页解析完成", state = 1000)
            }
            .onError {
                log(debugSource, it.stackTraceStr, state = -1)
            }
    }

    fun startDebug(scope: CoroutineScope, bookSource: BookSource, key: String) {
        cancelDebug()
        debugSource = bookSource.bookSourceUrl
        startTime = System.currentTimeMillis()
        startBookSourceDebugSession()
        when {
            key.isAbsUrl() -> {
                val book = Book()
                book.origin = bookSource.bookSourceUrl
                book.bookUrl = key
                log(debugSource, "⇒开始访问详情页:$key")
                infoDebug(scope, bookSource, book)
            }

            key.contains("::") -> {
                val url = key.substringAfter("::")
                log(debugSource, "⇒开始访问发现页:$url")
                exploreDebug(scope, bookSource, url)
            }

            key.startsWith("++") -> {
                val url = key.substring(2)
                val book = Book()
                book.origin = bookSource.bookSourceUrl
                book.tocUrl = url
                log(debugSource, "⇒开始访目录页:$url")
                tocDebug(scope, bookSource, book)
            }

            key.startsWith("--") -> {
                val url = key.substring(2)
                val book = Book()
                book.origin = bookSource.bookSourceUrl
                log(debugSource, "⇒开始访正文页:$url")
                val chapter = BookChapter()
                chapter.title = "调试"
                chapter.url = url
                contentDebug(scope, bookSource, book, chapter, null)
            }

            else -> {
                log(debugSource, "⇒开始搜索关键字:$key")
                searchDebug(scope, bookSource, key)
            }
        }
    }

    private fun exploreDebug(scope: CoroutineScope, bookSource: BookSource, url: String) {
        log(debugSource, "︾开始解析发现页")
        val explore = WebBook.exploreBook(scope, bookSource, url, 1)
            .onSuccess { exploreBooks ->
                if (exploreBooks.isNotEmpty()) {
                    log(debugSource, "︽发现页解析完成")
                    log(debugSource, showTime = false)
                    infoDebug(scope, bookSource, exploreBooks[0].toBook())
                } else {
                    log(debugSource, "︽未获取到书籍", state = -1)
                }
            }
            .onError {
                log(debugSource, it.stackTraceStr, state = -1)
            }
        tasks.add(explore)
    }

    private fun searchDebug(scope: CoroutineScope, bookSource: BookSource, key: String) {
        log(debugSource, "︾开始解析搜索页")
        val search = WebBook.searchBook(scope, bookSource, key, 1)
            .onSuccess { searchBooks ->
                if (searchBooks.isNotEmpty()) {
                    log(debugSource, "︽搜索页解析完成")
                    log(debugSource, showTime = false)
                    infoDebug(scope, bookSource, searchBooks[0].toBook())
                } else {
                    log(debugSource, "︽未获取到书籍", state = -1)
                }
            }
            .onError {
                log(debugSource, it.stackTraceStr, state = -1)
            }
        tasks.add(search)
    }

    private fun infoDebug(scope: CoroutineScope, bookSource: BookSource, book: Book) {
        if (book.tocUrl.isNotBlank()) {
            log(debugSource, "≡已获取目录链接,跳过详情页")
            log(debugSource, showTime = false)
            tocDebug(scope, bookSource, book)
            return
        }
        log(debugSource, "︾开始解析详情页")
        val info = WebBook.getBookInfo(scope, bookSource, book)
            .onSuccess {
                log(debugSource, "︽详情页解析完成")
                log(debugSource, showTime = false)
                if (!book.isWebFile) {
                    tocDebug(scope, bookSource, book)
                } else {
                    log(debugSource, "≡文件类书源跳过解析目录", state = 1000)
                }
            }
            .onError {
                log(debugSource, it.stackTraceStr, state = -1)
            }
        tasks.add(info)
    }

    private fun tocDebug(scope: CoroutineScope, bookSource: BookSource, book: Book) {
        log(debugSource, "︾开始解析目录页")
        val chapterList = WebBook.getChapterList(scope, bookSource, book)
            .onSuccess { chapters ->
                log(debugSource, "︽目录页解析完成")
                log(debugSource, showTime = false)
                val toc = chapters.filter { !(it.isVolume && it.url.startsWith(it.title)) }
                if (toc.isEmpty()) {
                    log(debugSource, "≡没有正文章节")
                    return@onSuccess
                }
                val nextChapterUrl = toc.getOrNull(1)?.url ?: toc.first().url
                contentDebug(scope, bookSource, book, toc.first(), nextChapterUrl)
            }
            .onError {
                log(debugSource, it.stackTraceStr, state = -1)
            }
        tasks.add(chapterList)
    }

    private fun contentDebug(
        scope: CoroutineScope,
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        nextChapterUrl: String?
    ) {
        log(debugSource, "︾开始解析正文页")
        // 先绑定本次会话要调试的正文目标，正文管线才可能把阶段快照写进这个会话
        bindDiagnosticsTarget(bookSource.bookSourceUrl, book.bookUrl, bookChapter.url)
        val content = WebBook.getContent(
            scope = scope,
            bookSource = bookSource,
            book = book,
            bookChapter = bookChapter,
            nextChapterUrl = nextChapterUrl,
            needSave = false
        ).onSuccess {
            // 顺序不能反：Web 端 printLog 收到 state=1000 会异步 cancelDebug(true)，
            // 先发终止信号会连带清掉 collector 与 callback，摘要一行都打不出来。
            finishContentDiagnostics()
            log(debugSource, "︽正文页解析完成", state = 1000)
        }.onError {
            // 链路级异常兜底：把网络/HTTP/编码等只有异常没有阶段读数的失败归到停住的那一层
            if (it !is CancellationException) {
                diagnostics?.recordFailure(it)
            }
            finishContentDiagnostics()
            log(debugSource, it.stackTraceStr, state = -1)
        }
        tasks.add(content)
    }

    /**
     * 正文调试收尾：先补「未执行」的标注，再输出四个阶段的诊断摘要。
     *
     * 书源调试链路在 BookContent 之后即结束；阶段四只有在阅读路径**没有**真实执行过时才标未执行。
     * 无条件标注会 upsert 覆盖掉已经发生的真实读数，把「执行过」写成「未执行」，属反向伪造。
     * 其余阶段若因链路提前失败而没有快照，也一律显式标注，不让摘要看起来「四段齐全」。
     */
    private fun finishContentDiagnostics() {
        val collector = diagnostics ?: return
        collector.markNotExecutedIfMissing(
            DiagnosticStage.READ_PROCESS,
            "书源调试链路止于 BookContent，未进入阅读处理"
        )
        collector.markMissingStagesNotExecuted("本次调试链路未执行该阶段")
        printBookSourceDiagnostics()
    }

    /**
     * 阅读路径真正执行完阶段四时调用（由 ContentProcessor 记录快照后触发）。
     *
     * 阶段四只可能发生在阅读处理里，调试链路本身止于 BookContent，所以摘要不能在收尾处
     * 一律写成「未执行」：会话仍存活且本次会话尚未补打过时，补一次含真实阶段四读数的摘要。
     * 会话已结束（callback/diagnostics 被清空）时不输出、也不伪造；每个会话只补打一次，
     * 避免同一章被阅读、导出统计等路径反复处理时刷屏——该判定由
     * [BookSourceDiagnosticsCollector.shouldReportReadingStage] 承担，可在 JVM 里直接契约测试。
     */
    fun onReadingStageRecorded() {
        val collector = diagnostics ?: return
        if (callback == null) return
        if (!collector.shouldReportReadingStage()) return
        collector.markMissingStagesNotExecuted("本次调试链路未执行该阶段")
        printBookSourceDiagnostics()
    }

    interface Callback {
        fun printLog(state: Int, msg: String)
    }
}