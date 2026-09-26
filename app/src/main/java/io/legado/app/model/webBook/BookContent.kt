package io.legado.app.model.webBook

import io.legado.app.R
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.exception.ContentEmptyException
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.diagnostics.BookSourceDiagnosticsCollector
import io.legado.app.help.book.diagnostics.DiagnosticStage
import io.legado.app.help.config.AppConfig
import io.legado.app.model.Debug
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setNextChapterUrl
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.mapAsync
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import org.apache.commons.text.StringEscapeUtils
import splitties.init.appCtx
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isOnLineTxt
import io.legado.app.help.book.isVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext

/**
 * 获取正文
 */
object BookContent {

    /**
     * 取本次正文调用对应的诊断接收端。
     *
     * 只有书源调试会话**绑定过**的那个正文目标（书源 + 书 + 章节）才拿得到接收端；
     * 本地 TXT/EPUB、同一书源下的别的书、同一本书的别的章节一律拿不到，
     * 因此这些路径对诊断是零额外处理，也不会把别的阅读写进调试摘要。
     */
    private fun diagnosticsFor(
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter
    ): BookSourceDiagnosticsCollector? =
        Debug.diagnosticsFor(bookSource.bookSourceUrl, book.bookUrl, bookChapter.url)

    @Throws(Exception::class)
    suspend fun analyzeContent(
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        baseUrl: String,
        redirectUrl: String,
        body: String?,
        nextChapterUrl: String?,
        needSave: Boolean = true
    ): String {
        body ?: throw NoStackTraceException(
            appCtx.getString(R.string.error_get_web_content, baseUrl)
        )
        Debug.log(bookSource.bookSourceUrl, "≡获取成功:${baseUrl}")
        Debug.log(bookSource.bookSourceUrl, body, state = 40)
        val mNextChapterUrl = if (nextChapterUrl.isNullOrEmpty()) {
            appDb.bookChapterDao.getChapter(book.bookUrl, bookChapter.index + 1)?.url
                ?: appDb.bookChapterDao.getChapter(book.bookUrl, 0)?.url
        } else {
            nextChapterUrl
        }
        val contentList = arrayListOf<String>()
        val nextUrlList = arrayListOf(redirectUrl)
        val contentRule = bookSource.getContentRule()
        val diagnostics = diagnosticsFor(bookSource, book, bookChapter)
        // 阶段一/二的采集权归诊断会话本身：本次调试的首个抓取页拿到，并发分页的其余页拿不到，
        // 不再借用 nextChapterUrl 这种与诊断无关的业务参数。
        val pageDiagnostics = if (diagnostics?.claimPrimaryStages() == true) diagnostics else null
        val analyzeRule = AnalyzeRule(book, bookSource)
        analyzeRule.setContent(body, baseUrl)
        analyzeRule.setRedirectUrl(redirectUrl)
        analyzeRule.setCoroutineContext(currentCoroutineContext())
        analyzeRule.setChapter(bookChapter)
        analyzeRule.setNextChapterUrl(mNextChapterUrl)
        currentCoroutineContext().ensureActive()
        var contentData = analyzeContent(
            book, baseUrl, redirectUrl, body, contentRule, bookChapter, bookSource, mNextChapterUrl,
            pageDiagnostics = pageDiagnostics
        )
        contentList.add(contentData.first)
        if (contentData.second.size == 1) {
            val webJs = contentRule.webJs
            var nextUrl = contentData.second[0]
            while (nextUrl.isNotEmpty() && !nextUrlList.contains(nextUrl)) {
                if (!mNextChapterUrl.isNullOrEmpty()
                    && NetworkUtils.getAbsoluteURL(redirectUrl, nextUrl)
                    == NetworkUtils.getAbsoluteURL(redirectUrl, mNextChapterUrl)
                ) break
                nextUrlList.add(nextUrl)
                currentCoroutineContext().ensureActive()
                val analyzeUrl = AnalyzeUrl(
                    mUrl = nextUrl,
                    source = bookSource,
                    ruleData = book,
                    coroutineContext = currentCoroutineContext()
                )
                val res = analyzeUrl.getStrResponseAwait(jsStr = webJs) //控制并发访问
                res.body?.let { nextBody ->
                    contentData = analyzeContent(
                        book, nextUrl, res.url, nextBody, contentRule,
                        bookChapter, bookSource, mNextChapterUrl,
                        printLog = false
                    )
                    nextUrl =
                        if (contentData.second.isNotEmpty()) contentData.second[0] else ""
                    contentList.add(contentData.first)
                    Debug.log(bookSource.bookSourceUrl, "第${contentList.size}页完成")
                }
            }
            Debug.log(bookSource.bookSourceUrl, "◇本章总页数:${nextUrlList.size}")
        } else if (contentData.second.size > 1) {
            Debug.log(bookSource.bookSourceUrl, "◇并发解析正文,总页数:${contentData.second.size}")
            flow {
                for (urlStr in contentData.second) {
                    emit(urlStr)
                }
            }.mapAsync(AppConfig.threadCount) { urlStr ->
                val analyzeUrl = AnalyzeUrl(
                    mUrl = urlStr,
                    source = bookSource,
                    ruleData = book,
                    coroutineContext = currentCoroutineContext()
                )
                val res = analyzeUrl.getStrResponseAwait() //控制并发访问
                analyzeContent(
                    book, urlStr, res.url, res.body!!, contentRule,
                    bookChapter, bookSource, mNextChapterUrl,
                    getNextPageUrl = false,
                    printLog = false
                ).first
            }.collect {
                currentCoroutineContext().ensureActive()
                contentList.add(it)
            }
        }
        val subContentRule = contentRule.subContent
        if (!subContentRule.isNullOrBlank()) { //副内容
            analyzeRule.getString(subContentRule).let { rawContent ->
                runCatching {
                    if (book.isOnLineTxt) {
                        contentList.add(rawContent)
                        return@let
                    }
                    val subContent = rawContent.trim().let {
                        if (it.startsWith("http", true)) {
                            AnalyzeUrl(
                                mUrl = it,
                                source = bookSource,
                                ruleData = book,
                                coroutineContext = currentCoroutineContext()
                            ).getStrResponseAwait().body
                        } else {
                            it
                        }
                    }
                    when {
                        book.isAudio -> {
                            bookChapter.putLyric(subContent)
                            Debug.log(bookSource.bookSourceUrl, "┌获取副文歌词")
                            Debug.log(bookSource.bookSourceUrl, "└\n$subContent")
                        }

                        book.isVideo -> {
                            bookChapter.putDanmaku(subContent)
                            Debug.log(bookSource.bookSourceUrl, "┌获取副文弹幕")
                            Debug.log(bookSource.bookSourceUrl, "└\n$subContent")
                        }
                    }
                }.onFailure { e ->
                    Debug.log(bookSource.bookSourceUrl, "获取副文出错, ${e.localizedMessage}")
                }
            }
        }
        var contentStr = contentList.joinToString("\n")
        //全文替换
        val replaceRegex = contentRule.replaceRegex
        if (!replaceRegex.isNullOrEmpty()) {
            val replaceStart = if (diagnostics != null) System.currentTimeMillis() else 0L
            contentStr = contentStr.split(AppPattern.LFRegex).joinToString("\n") { it.trim() }
            contentStr = try {
                analyzeRule.getString(replaceRegex, contentStr)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 四阶段诊断「书源替换后」：书源替换报错时如实记录错误类别（规则语法/超时等）
                diagnostics?.record(
                    stage = DiagnosticStage.REPLACE,
                    text = null,
                    elapsedMs = elapsedSince(replaceStart),
                    error = e
                )
                throw e
            }
            if (book.isOnLineTxt) {
                contentStr = contentStr.split(AppPattern.LFRegex).joinToString("\n") { "　　$it" }
            }
            // 四阶段诊断「书源替换后」：contentRule.replaceRegex 全文替换之后。
            diagnostics?.record(
                stage = DiagnosticStage.REPLACE,
                text = contentStr,
                elapsedMs = elapsedSince(replaceStart)
            )
        } else {
            // 没有书源替换规则时该阶段没有执行，不得用「提取后」的快照冒充。
            diagnostics?.markNotExecuted(
                DiagnosticStage.REPLACE,
                "书源未配置 replaceRegex，全文替换未执行"
            )
        }
        val titleRule = contentRule.title //先正文再章节名称
        if (!titleRule.isNullOrBlank()) {
            var title = analyzeRule.runCatching {
                getString(titleRule)
            }.onFailure {
                Debug.log(bookSource.bookSourceUrl, "获取标题出错, ${it.localizedMessage}")
            }.getOrNull()
            if (!title.isNullOrBlank()) {
                val matchResult = AppPattern.imgRegex.find(title)
                if (matchResult != null) {
                    matchResult.groupValues[1]
                    val (group1,group2) = matchResult.destructured
                    title = if (group1 != "") {
                        group1
                    } else {
                        bookChapter.title
                    }
                    bookChapter.imgUrl = group2
                }
                bookChapter.title = title
                bookChapter.titleMD5 = null
                bookChapter.update()
            }
        }
        Debug.log(bookSource.bookSourceUrl, "┌获取章节名称")
        Debug.log(bookSource.bookSourceUrl, "└${bookChapter.title}")
        Debug.log(bookSource.bookSourceUrl, "┌获取正文内容")
        Debug.log(bookSource.bookSourceUrl, "└\n$contentStr")
        if (!bookChapter.isVolume && contentStr.isBlank()) {
            throw ContentEmptyException("内容为空")
        }
        if (needSave) {
            BookHelp.saveContent(bookSource, book, bookChapter, contentStr)
        }
        return contentStr
    }

    @Throws(Exception::class)
    private suspend fun analyzeContent(
        book: Book,
        baseUrl: String,
        redirectUrl: String,
        body: String,
        contentRule: ContentRule,
        chapter: BookChapter,
        bookSource: BookSource,
        nextChapterUrl: String?,
        getNextPageUrl: Boolean = true,
        printLog: Boolean = true,
        pageDiagnostics: BookSourceDiagnosticsCollector? = null
    ): Pair<String, List<String>> {
        val analyzeRule = AnalyzeRule(book, bookSource)
        analyzeRule.setContent(body, baseUrl)
        analyzeRule.setCoroutineContext(currentCoroutineContext())
        val rUrl = analyzeRule.setRedirectUrl(redirectUrl)
        analyzeRule.setNextChapterUrl(nextChapterUrl)
        val nextUrlList = arrayListOf<String>()
        analyzeRule.setChapter(chapter)
        //获取正文
        val extractStart = if (pageDiagnostics != null) System.currentTimeMillis() else 0L
        var content = try {
            analyzeRule.getString(contentRule.content, unescape = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 四阶段诊断「提取后」：规则解析抛错时记录错误类别与「产出为空」，异常照常上抛
            pageDiagnostics?.record(
                stage = DiagnosticStage.EXTRACT,
                text = null,
                elapsedMs = elapsedSince(extractStart),
                error = e
            )
            throw e
        }
        // 四阶段诊断「提取后」：这里才是 contentRule.content 的真实规则产出（选多/选少看这一行），
        // 耗时也只计提取本身，不再用原始响应的读取耗时冒充。
        pageDiagnostics?.record(
            stage = DiagnosticStage.EXTRACT,
            text = content,
            elapsedMs = elapsedSince(extractStart)
        )
        if (!book.isAudio && !book.isVideo) { //音频和视频获取的是链接，不需要html格式化
            val formatStart = if (pageDiagnostics != null) System.currentTimeMillis() else 0L
            val useHtmlMap = mutableMapOf<String, String>()
            if (AppConfig.adaptSpecialStyle) {
                content = AppPattern.useHtmlRegex.replace(content) { matchResult ->
                    val placeholder = "{usehtml_${useHtmlMap.size}}"
                    useHtmlMap[placeholder] = matchResult.value
                    placeholder
                }
            }
            content = try {
                HtmlFormatter.formatKeepImg(content, rUrl) //内置净化格式化
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pageDiagnostics?.record(
                    stage = DiagnosticStage.FORMAT,
                    text = content,
                    elapsedMs = elapsedSince(formatStart),
                    error = e
                )
                throw e
            }
            if (content.indexOf('&') > -1) {
                content = StringEscapeUtils.unescapeHtml4(content)
            }
            useHtmlMap.forEach { (placeholder, originalContent) ->
                content = content.replace(placeholder, originalContent)
            }
            // 四阶段诊断「HTML 格式化后」：内置净化格式化 + 实体解码 + usehtml 还原之后。
            pageDiagnostics?.record(
                stage = DiagnosticStage.FORMAT,
                text = content,
                elapsedMs = elapsedSince(formatStart)
            )
        } else {
            // 音频/视频规则取的是链接，本条链路不执行 HTML 格式化，不得伪造成已完成。
            pageDiagnostics?.markNotExecuted(
                DiagnosticStage.FORMAT,
                "音频/视频书源取链接，跳过 HTML 格式化"
            )
        }
        //获取下一页链接
        if (getNextPageUrl) {
            val nextUrlRule = contentRule.nextContentUrl
            if (!nextUrlRule.isNullOrEmpty()) {
                Debug.log(bookSource.bookSourceUrl, "┌获取正文下一页链接", printLog)
                analyzeRule.getStringList(nextUrlRule, isUrl = true)?.let {
                    nextUrlList.addAll(it)
                }
                Debug.log(bookSource.bookSourceUrl, "└" + nextUrlList.joinToString("，"), printLog)
            }
        }
        return Pair(content, nextUrlList)
    }

    /** 诊断计时：只有会话开启时才读时钟（start 为 0 表示没有计时）。 */
    private fun elapsedSince(start: Long): Long =
        if (start > 0) System.currentTimeMillis() - start else 0L
}
