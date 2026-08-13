package io.legado.app.ui.book.import.manga

import android.app.Application
import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.book.BookHelp
import io.legado.app.model.localBook.MangaBookPreview
import io.legado.app.model.localBook.MangaFolderScanner
import io.legado.app.utils.FileDoc
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.toastOnUi

class ImportMangaViewModel(application: Application) : BaseViewModel(application) {

    val previewLiveData = MutableLiveData<List<MangaBookPreview>>()

    val scanningLiveData = MutableLiveData<Boolean>()

    /** 大目录扫描进度（已扫描 / 总数），仅系列模式有总数 */
    val scanProgressLiveData = MutableLiveData<Pair<Int, Int>>()

    fun scan(root: FileDoc, mode: MangaFolderScanner.Mode) {
        scanningLiveData.postValue(true)
        execute {
            MangaFolderScanner.scan(root, mode) { scanned, total ->
                scanProgressLiveData.postValue(scanned to total)
            }
        }.onSuccess {
            previewLiveData.postValue(it)
        }.onError {
            previewLiveData.postValue(emptyList())
            context.toastOnUi(
                "${context.getString(R.string.import_manga_scan_failed)}\n${it.localizedMessage}"
            )
            AppLog.put("导入本地漫画扫描失败\n${it.localizedMessage}", it)
        }.onFinally {
            scanningLiveData.postValue(false)
        }
    }

    fun scanAlbums() {
        scanningLiveData.postValue(true)
        execute {
            MangaFolderScanner.scanAlbums()
        }.onSuccess {
            previewLiveData.postValue(it)
        }.onError {
            previewLiveData.postValue(emptyList())
            context.toastOnUi(
                "${context.getString(R.string.import_manga_scan_failed)}\n${it.localizedMessage}"
            )
            AppLog.put("导入相册扫描失败\n${it.localizedMessage}", it)
        }.onFinally {
            scanningLiveData.postValue(false)
        }
    }

    fun import(bookList: List<MangaBookPreview>, finally: () -> Unit) {
        execute {
            val list = bookList.filter { it.enabled && !it.isSkipped }
            // 本批整体置顶，系列内按阅读顺序正排（第1话 order 最小、最上），
            // 书架默认对漫画按 order 稳定排序，读完位置不变
            val baseOrder = appDb.bookDao.minOrder - list.size
            list.forEachIndexed { index, preview ->
                importBook(preview, baseOrder + index)
            }
        }.onSuccess {
            context.toastOnUi(R.string.import_manga_import_success)
        }.onError {
            context.toastOnUi(
                "${context.getString(R.string.import_manga_import_failed)}\n${it.localizedMessage}"
            )
            AppLog.put("导入本地漫画失败\n${it.localizedMessage}", it)
        }.onFinally {
            finally.invoke()
        }
    }

    private fun importBook(preview: MangaBookPreview, order: Int) {
        val bookUrl = preview.bookKey
        appDb.bookDao.getBook(bookUrl)?.let { oldBook ->
            // 重新导入：清理旧章节与正文缓存，阅读进度重置
            BookHelp.clearCache(oldBook)
            appDb.bookChapterDao.delByBook(bookUrl)
        }
        val book = Book(
            bookUrl = bookUrl,
            type = BookType.image or BookType.local,
            origin = BookType.localTag,
            originName = preview.name,
            name = preview.name,
            group = getGroupId(preview.group),
            coverUrl = preview.coverImage,
            totalChapterNum = preview.chapters.size,
            latestChapterTime = System.currentTimeMillis(),
            order = order
        )
        appDb.bookDao.insert(book)
        val chapters = preview.chapters.mapIndexed { index, chapter ->
            BookChapter(
                bookUrl = bookUrl,
                index = index,
                title = chapter.name,
                url = MD5Utils.md5Encode16(bookUrl + index + chapter.name),
                start = null,
                end = null,
                variable = GSON.toJson(
                    mapOf(MangaFolderScanner.IMG_KEY to chapter.images.joinToString("\n"))
                )
            )
        }
        appDb.bookChapterDao.insert(*chapters.toTypedArray())
    }

    /** 按系列名查找/创建书架分组 */
    private fun getGroupId(groupName: String?): Long {
        val name = groupName?.trim().orEmpty()
        if (name.isEmpty()) {
            return 0
        }
        appDb.bookGroupDao.getByName(name)?.let {
            return it.groupId
        }
        val group = BookGroup(
            groupId = appDb.bookGroupDao.getUnusedId(),
            groupName = name,
            order = appDb.bookGroupDao.maxOrder + 1
        )
        appDb.bookGroupDao.insert(group)
        return group.groupId
    }
}
