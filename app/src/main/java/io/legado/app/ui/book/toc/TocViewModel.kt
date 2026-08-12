package io.legado.app.ui.book.toc


import android.app.Application
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.dao.BookmarkShift
import io.legado.app.data.dao.BookmarkDelete
import io.legado.app.data.dao.ChapterDelete
import io.legado.app.data.dao.ChapterShift
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ChapterNumberUtils
import io.legado.app.help.book.ChapterSplitter
import io.legado.app.help.book.ChapterSplitter.SplitUnit
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.FileDoc
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.writeText
import java.io.File

class TocViewModel(application: Application) : BaseViewModel(application) {

    companion object {
        /** 单次拆分章节单元数上限，超出提示分批处理 */
        private const val SPLIT_LIMIT = 500
    }

    var bookUrl: String = ""
    var bookData = MutableLiveData<Book>()
    var chapterListCallBack: ChapterListCallBack? = null
    var bookMarkCallBack: BookmarkCallBack? = null
    var searchKey: String? = null

    fun initBook(bookUrl: String) {
        this.bookUrl = bookUrl
        execute {
            appDb.bookDao.getBook(bookUrl)?.let {
                bookData.postValue(it)
            }
        }
    }

    fun upBookTocRule(book: Book, complete: (Throwable?) -> Unit) {
        execute {
            appDb.bookDao.update(book)
            LocalBook.getChapterList(book).let {
                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookChapterDao.insert(*it.toTypedArray())
                appDb.bookDao.update(book)
                ReadBook.onChapterListUpdated(book)
                bookData.postValue(book)
            }
        }.onSuccess {
            complete.invoke(null)
        }.onError {
            complete.invoke(it)
        }
    }

    fun reverseToc(success: (book: Book) -> Unit) {
        execute {
            bookData.value?.apply {
                setReverseToc(!getReverseToc())
                val toc = appDb.bookChapterDao.getChapterList(bookUrl)
                val newToc = toc.reversed()
                newToc.forEachIndexed { index, bookChapter ->
                    bookChapter.index = index
                }
                appDb.bookChapterDao.insert(*newToc.toTypedArray())
            }
        }.onSuccess {
            it?.let(success)
        }
    }

    /**
     * 在指定章节后新增章节，后续章节序号与标题数字自动 +1
     */
    fun insertChapter(book: Book, anchor: BookChapter, title: String, content: String) {
        execute {
            val toc = appDb.bookChapterDao.getChapterList(book.bookUrl)
            if (toc.isEmpty()) {
                throw NoStackTraceException(context.getString(R.string.chapter_list_empty))
            }
            val insertIndex = anchor.index + 1
            // 从后往前收集右移章节，避免 (bookUrl, index) 唯一索引中间态冲突
            val chapterShifts = mutableListOf<ChapterShift>()
            val bookmarkShifts = mutableListOf<BookmarkShift>()
            for (i in toc.size - 1 downTo 0) {
                val chapter = toc[i]
                if (chapter.index >= insertIndex) {
                    val newIndex = chapter.index + 1
                    val newTitle = ChapterNumberUtils.rewriteTitle(chapter.title, 1) ?: chapter.title
                    collectShift(book, chapter, newIndex, newTitle, chapterShifts, bookmarkShifts)
                }
            }
            // 构造新章节，先写正文覆盖文件再入库，避免“有章节无正文”的坏状态
            val newChapter = BookChapter(
                url = MD5Utils.md5Encode16(
                    "${book.originName}_edit_${anchor.index}_${System.currentTimeMillis()}"
                ),
                title = title.ifBlank {
                    context.getString(R.string.chapter_default_title, insertIndex + 1)
                },
                bookUrl = book.bookUrl,
                index = insertIndex
            )
            BookHelp.saveText(
                book,
                newChapter,
                content.ifBlank { context.getString(R.string.chapter_content_placeholder) }
            )
            // 更新书籍元数据与阅读位置（标题取重排结果，与事务内最终状态一致）
            book.totalChapterNum += 1
            if (book.durChapterIndex > anchor.index) {
                book.durChapterIndex += 1
                chapterShifts.firstOrNull { it.newIndex == book.durChapterIndex }?.let {
                    book.durChapterTitle = it.newTitle
                }
            }
            // 数据库变更整体事务化，失败自动回滚
            appDb.bookChapterDao.applyTocEdit(
                bookDao = appDb.bookDao,
                bookmarkDao = appDb.bookmarkDao,
                book = book,
                chapterDeletes = emptyList(),
                chapterInserts = listOf(newChapter),
                chapterShifts = chapterShifts,
                bookmarkDeletes = emptyList(),
                bookmarkShifts = bookmarkShifts
            )
            ReadBook.onChapterListUpdated(book)
            bookData.postValue(book)
        }.onSuccess {
            context.toastOnUi(context.getString(R.string.add_chapter_success))
            chapterListCallBack?.upChapterList(searchKey)
        }.onError {
            AppLog.put(context.getString(R.string.add_chapter_error), it, true)
        }
    }

    /**
     * 删除指定章节，后续章节序号与标题数字自动 -1
     */
    fun deleteChapter(book: Book, chapter: BookChapter) {
        execute {
            val toc = appDb.bookChapterDao.getChapterList(book.bookUrl)
            if (toc.isEmpty()) {
                throw NoStackTraceException(context.getString(R.string.chapter_list_empty))
            }
            // 删除目标章节的正文覆盖文件（事务外，失败不阻断数据库操作）
            BookHelp.delContent(book, chapter)
            // 从前往后收集左移章节，避免 (bookUrl, index) 唯一索引中间态冲突
            val chapterShifts = mutableListOf<ChapterShift>()
            val bookmarkShifts = mutableListOf<BookmarkShift>()
            for (c in toc) {
                if (c.index > chapter.index) {
                    val newIndex = c.index - 1
                    val newTitle = ChapterNumberUtils.rewriteTitle(c.title, -1) ?: c.title
                    collectShift(book, c, newIndex, newTitle, chapterShifts, bookmarkShifts)
                }
            }
            // 更新书籍元数据与阅读位置
            book.totalChapterNum = (book.totalChapterNum - 1).coerceAtLeast(0)
            when {
                book.durChapterIndex > chapter.index -> {
                    book.durChapterIndex -= 1
                    chapterShifts.firstOrNull { it.newIndex == book.durChapterIndex }?.let {
                        book.durChapterTitle = it.newTitle
                    }
                }
                book.durChapterIndex == chapter.index -> {
                    book.durChapterIndex =
                        book.durChapterIndex.coerceAtMost((book.totalChapterNum - 1).coerceAtLeast(0))
                    book.durChapterPos = 0
                    val shiftedTitle =
                        chapterShifts.firstOrNull { it.newIndex == book.durChapterIndex }?.newTitle
                    if (shiftedTitle != null) {
                        book.durChapterTitle = shiftedTitle
                    } else if (book.durChapterIndex < chapter.index) {
                        toc.getOrNull(book.durChapterIndex)?.let {
                            book.durChapterTitle = it.title
                        }
                    }
                }
            }
            // 数据库变更整体事务化，失败自动回滚
            appDb.bookChapterDao.applyTocEdit(
                bookDao = appDb.bookDao,
                bookmarkDao = appDb.bookmarkDao,
                book = book,
                chapterDeletes = listOf(ChapterDelete(book.bookUrl, chapter.url)),
                chapterInserts = emptyList(),
                chapterShifts = chapterShifts,
                bookmarkDeletes = listOf(BookmarkDelete(book.name, book.author, chapter.index)),
                bookmarkShifts = bookmarkShifts
            )
            ReadBook.onChapterListUpdated(book)
            bookData.postValue(book)
        }.onSuccess {
            context.toastOnUi(context.getString(R.string.delete_chapter_success))
            chapterListCallBack?.upChapterList(searchKey)
        }.onError {
            AppLog.put(context.getString(R.string.delete_chapter_error), it, true)
        }
    }

    /**
     * 读取章节有效正文并生成拆分预览单元（IO 线程）。
     * 未命中标题 / 仅一章 / 超上限时抛出带提示的异常，由 onError toast。
     */
    fun previewSplit(book: Book, chapter: BookChapter, callback: (List<SplitUnit>) -> Unit) {
        execute {
            val content = BookHelp.getContent(book, chapter).orEmpty()
            // 章节正文可能从标题行之后开始，补上本章标题以保证原章节作为第一单元保留
            val units = ChapterSplitter.split(content, chapter.title)
            if (units.isEmpty()) {
                throw NoStackTraceException(context.getString(R.string.split_chapter_no_title))
            }
            if (units.size < 2) {
                throw NoStackTraceException(context.getString(R.string.split_chapter_single))
            }
            if (units.size > SPLIT_LIMIT) {
                throw NoStackTraceException(
                    context.getString(R.string.split_chapter_too_many, SPLIT_LIMIT)
                )
            }
            units
        }.onSuccess {
            callback.invoke(it)
        }.onError {
            AppLog.put(context.getString(R.string.split_chapter_error), it, true)
        }
    }

    /**
     * 按标题规则拆分粘连章节：
     * 原章节改写为第一个拆分单元，其余单元作为新章插入，后续章节序号右移。
     * 注意：拆分出的单元标题来自原文，后续章节标题**保留原文不改写**
     * （与“手动新增章节”不同，后者需要级联 +1 重编号）。
     * @param units 拆分单元（可由 previewSplit 生成，并经预览勾选/改名调整）
     */
    fun splitChapter(book: Book, chapter: BookChapter, units: List<SplitUnit>) {
        execute {
            if (units.size < 2) {
                throw NoStackTraceException(context.getString(R.string.split_chapter_single))
            }
            if (units.size > SPLIT_LIMIT) {
                throw NoStackTraceException(
                    context.getString(R.string.split_chapter_too_many, SPLIT_LIMIT)
                )
            }
            val toc = appDb.bookChapterDao.getChapterList(book.bookUrl)
            if (toc.isEmpty()) {
                throw NoStackTraceException(context.getString(R.string.chapter_list_empty))
            }
            val insertCount = units.size - 1
            val insertIndex = chapter.index + 1
            val first = units.first()

            // 原章节改写为第一个单元：正文覆盖文件重命名/重写 + 标题更新（index 不变）
            val chapterShifts = mutableListOf<ChapterShift>()
            val bookmarkShifts = mutableListOf<BookmarkShift>()
            val updatedChapter = chapter.copy(title = first.title)
            collectShift(book, chapter, chapter.index, first.title, chapterShifts, bookmarkShifts)
            BookHelp.saveText(book, updatedChapter, first.content)

            // 其余单元作为新章节：先写正文覆盖文件，避免“有章节无正文”的坏状态
            val stamp = System.currentTimeMillis()
            val newChapters = units.drop(1).mapIndexed { k, unit ->
                BookChapter(
                    url = MD5Utils.md5Encode16(
                        "${book.originName}_split_${chapter.index}_${stamp}_$k"
                    ),
                    title = unit.title,
                    bookUrl = book.bookUrl,
                    index = insertIndex + k
                ).also { newChapter ->
                    BookHelp.saveText(book, newChapter, unit.content)
                }
            }

            // 后续章节从后往前右移 insertCount 位（仅 index，标题保留原文），
            // 避免唯一索引中间态冲突
            for (i in toc.size - 1 downTo 0) {
                val c = toc[i]
                if (c.index > chapter.index) {
                    val newIndex = c.index + insertCount
                    collectShift(book, c, newIndex, c.title, chapterShifts, bookmarkShifts)
                }
            }

            // 更新书籍元数据与阅读位置（标题取重排结果，与事务内最终状态一致）
            book.totalChapterNum += insertCount
            when {
                book.durChapterIndex > chapter.index -> {
                    book.durChapterIndex += insertCount
                    chapterShifts.firstOrNull { it.newIndex == book.durChapterIndex }?.let {
                        book.durChapterTitle = it.newTitle
                    }
                }
                book.durChapterIndex == chapter.index -> {
                    book.durChapterTitle = first.title
                }
            }

            // 数据库变更整体事务化，失败自动回滚
            appDb.bookChapterDao.applyTocEdit(
                bookDao = appDb.bookDao,
                bookmarkDao = appDb.bookmarkDao,
                book = book,
                chapterDeletes = emptyList(),
                chapterInserts = newChapters,
                chapterShifts = chapterShifts,
                bookmarkDeletes = emptyList(),
                bookmarkShifts = bookmarkShifts
            )
            ReadBook.onChapterListUpdated(book)
            bookData.postValue(book)
        }.onSuccess {
            context.toastOnUi(context.getString(R.string.split_chapter_success))
            chapterListCallBack?.upChapterList(searchKey)
        }.onError {
            AppLog.put(context.getString(R.string.split_chapter_error), it, true)
        }
    }

    /**
     * 章节重排后重命名正文覆盖文件，失败仅告警，不阻断数据库操作
     */
    private fun renameChapterFile(
        book: Book,
        chapter: BookChapter,
        newIndex: Int,
        newTitle: String
    ) {
        kotlin.runCatching {
            val oldName = String.format(
                "%05d-%s.nb",
                chapter.index,
                MD5Utils.md5Encode16(chapter.title)
            )
            val newName = String.format(
                "%05d-%s.nb",
                newIndex,
                MD5Utils.md5Encode16(newTitle)
            )
            if (oldName == newName) return@runCatching
            val dir = context.externalFiles.getFile("book_cache", book.getFolderName())
            val oldFile = File(dir, oldName)
            if (oldFile.exists()) {
                oldFile.renameTo(File(dir, newName))
            }
        }.onFailure {
            AppLog.put("重命名章节正文文件失败\n${it.localizedMessage}", it)
        }
    }

    /**
     * 收集单章重排操作：文件改名（best-effort）＋章节 shift ＋书签 shift。
     * 调用方须保证传入顺序满足唯一索引约束（右移从后往前、左移从前往后）。
     */
    private fun collectShift(
        book: Book,
        chapter: BookChapter,
        newIndex: Int,
        newTitle: String,
        chapterShifts: MutableList<ChapterShift>,
        bookmarkShifts: MutableList<BookmarkShift>
    ) {
        renameChapterFile(book, chapter, newIndex, newTitle)
        chapterShifts.add(ChapterShift(book.bookUrl, chapter.url, newIndex, newTitle))
        bookmarkShifts.add(BookmarkShift(book.name, book.author, chapter.index, newIndex, newTitle))
    }

    fun startChapterListSearch(newText: String?) {
        chapterListCallBack?.upChapterList(newText)
    }

    fun startBookmarkSearch(newText: String?) {
        bookMarkCallBack?.upBookmark(newText)
    }

    fun upChapterListAdapter() {
        chapterListCallBack?.upAdapter()
    }

    fun saveBookmark(treeUri: Uri) {
        execute {
            val book = bookData.value
                ?: throw NoStackTraceException(context.getString(R.string.no_book))
            val fileName = "bookmark-${book.name} ${book.author}.json"
            val doc = FileDoc.fromUri(treeUri, true)
            doc.createFileIfNotExist(fileName).writeText(
                GSON.toJson(
                    appDb.bookmarkDao.getByBook(book.name, book.author)
                )
            )
        }.onError {
            AppLog.put("导出失败\n${it.localizedMessage}", it, true)
        }.onSuccess {
            context.toastOnUi("导出成功")
        }
    }

    fun saveBookmarkMd(treeUri: Uri) {
        execute {
            val book = bookData.value
                ?: throw NoStackTraceException(context.getString(R.string.no_book))
            val fileName = "bookmark-${book.name} ${book.author}.md"
            val treeDoc = FileDoc.fromUri(treeUri, true)
            val fileDoc = treeDoc.createFileIfNotExist(fileName)
                .openOutputStream()
                .getOrThrow()
            fileDoc.use { outputStream ->
                outputStream.write("## ${book.name} ${book.author}\n\n".toByteArray())
                appDb.bookmarkDao.getByBook(book.name, book.author).forEach {
                    outputStream.write("#### ${it.chapterName}\n\n".toByteArray())
                    outputStream.write("###### 原文\n ${it.bookText}\n\n".toByteArray())
                    outputStream.write("###### 摘要\n ${it.content}\n\n".toByteArray())
                }
            }
        }.onError {
            AppLog.put("导出失败\n${it.localizedMessage}", it, true)
        }.onSuccess {
            context.toastOnUi("导出成功")
        }
    }

    interface ChapterListCallBack {
        fun upChapterList(searchKey: String?)

        fun clearDisplayTitle()

        fun upAdapter()
    }

    interface BookmarkCallBack {
        fun upBookmark(searchKey: String?)
    }
}
