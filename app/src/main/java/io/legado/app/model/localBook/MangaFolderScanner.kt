package io.legado.app.model.localBook

import io.legado.app.constant.AppLog
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.exception.TocEmptyException
import io.legado.app.utils.FileDoc
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.list
import splitties.init.appCtx

/**
 * 本地漫画目录扫描器
 *
 * 固定三层结构（见第二期计划书）：
 * - 第一层：导入入口（不生成实体）
 * - 第二层：系列/分类（系列导入时为书架分组，整本导入时为书本身）
 * - 第三层：直接含图片的文件夹（可读单元 = 书或章节）
 */
object MangaFolderScanner {

    enum class Mode { WHOLE, SERIES }

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    /** 图片文件名白名单判断 */
    fun isImageName(name: String): Boolean {
        return name.substringAfterLast('.', "").lowercase() in imageExtensions
    }

    /** 自然排序（001 < 002 < 010），按数字块数值比较，避免纯字符串序 */
    fun naturalSort(names: List<String>): List<String> {
        return names.sortedWith(naturalComparator)
    }

    /** 章节内图片生成 `<img>` 正文 */
    fun buildImgHtml(images: List<String>): String {
        return images.joinToString("\n") { "<img src=\"$it\">" }
    }

    /** 章节内图片列表存于章节 variable 的 key */
    const val IMG_KEY = "imgs"

    /** 从章节 variable 读取图片列表（换行分隔存储） */
    fun imagesOf(chapter: BookChapter): List<String> {
        return (chapter.variableMap[IMG_KEY] ?: "").split('\n').filter { it.isNotBlank() }
    }

    /**
     * 扫描导入根文件夹，返回书预览清单（IO 线程）
     */
    fun scan(root: FileDoc, mode: Mode): List<MangaBookPreview> {
        val rootChildren = root.list { !it.name.startsWith(".") }.orEmpty()
        val previews = when (mode) {
            Mode.WHOLE -> listOf(scanBook(root, rootChildren, series = null))
            Mode.SERIES -> rootChildren
                .filter { it.isDir }
                .sortedWith(compareBy(naturalComparator) { it.name })
                .map { seriesDir ->
                    val children = seriesDir.list { !it.name.startsWith(".") }.orEmpty()
                    scanBook(seriesDir, children, series = root.name)
                }
        }
        return previews.map { preview ->
            preview.isDuplicate = appDb.bookDao.has(preview.dir.toString())
            preview
        }
    }

    /**
     * 扫描一本书：直接子文件夹=章节；无子文件夹但直接含图时整本连看
     */
    private fun scanBook(
        bookDir: FileDoc,
        children: List<FileDoc>,
        series: String?
    ): MangaBookPreview {
        val dirs = children
            .filter { it.isDir }
            .sortedWith(compareBy(naturalComparator) { it.name })
        val directImages = children
            .filter { !it.isDir && isImageName(it.name) }
            .sortedWith(compareBy(naturalComparator) { it.name })
        val chapterDirs = dirs.mapNotNull { dir ->
            val images = imagesOf(dir)
            if (images.isEmpty()) null else MangaChapterPreview(dir = dir, name = dir.name, images = images)
        }
        return if (chapterDirs.isNotEmpty()) {
            MangaBookPreview(
                dir = bookDir,
                name = bookDir.name,
                group = series,
                isWhole = false,
                canToggleWhole = directImages.isNotEmpty(),
                chapterDirs = chapterDirs,
                wholeImages = directImages.map { it.toString() }
            )
        } else if (directImages.isNotEmpty()) {
            MangaBookPreview(
                dir = bookDir,
                name = bookDir.name,
                group = series,
                isWhole = true,
                canToggleWhole = false,
                wholeImages = directImages.map { it.toString() }
            )
        } else {
            MangaBookPreview(
                dir = bookDir,
                name = bookDir.name,
                group = series,
                enabled = false,
                isSkipped = true
            )
        }
    }

    /**
     * 重新解析本地漫画书的章节列表（阅读器打开/刷新目录时使用）
     */
    fun scanChapters(book: Book): ArrayList<BookChapter> {
        val bookDir = kotlin.runCatching {
            FileDoc.fromDir(book.bookUrl)
        }.getOrElse {
            AppLog.put("本地漫画目录解析失败\n${it.localizedMessage}", it)
            throw TocEmptyException(appCtx.getString(R.string.chapter_list_empty))
        }
        val children = bookDir.list { !it.name.startsWith(".") }.orEmpty()
        val directImages = children
            .filter { !it.isDir && isImageName(it.name) }
            .sortedWith(compareBy(naturalComparator) { it.name })
        val chapterDirs = children
            .filter { it.isDir && imagesOf(it).isNotEmpty() }
            .sortedWith(compareBy(naturalComparator) { it.name })
        if (chapterDirs.isEmpty() && directImages.isEmpty()) {
            throw TocEmptyException(appCtx.getString(R.string.chapter_list_empty))
        }
        val unitDirs = if (chapterDirs.isNotEmpty()) chapterDirs else listOf(bookDir)
        return ArrayList(unitDirs.mapIndexed { index, dir ->
            val images = if (dir == bookDir) {
                directImages
            } else {
                imagesOf(dir)
            }
            BookChapter(
                bookUrl = book.bookUrl,
                index = index,
                title = dir.name,
                url = MD5Utils.md5Encode16(book.bookUrl + index + dir.name),
                start = null,
                end = null,
                variable = GSON.toJson(mapOf(IMG_KEY to images.joinToString("\n")))
            )
        })
    }

    /** 文件夹直接含有的图片（自然排序后的 uri 列表） */
    private fun imagesOf(dir: FileDoc): List<String> {
        return dir.list { !it.name.startsWith(".") }.orEmpty()
            .filter { !it.isDir && isImageName(it.name) }
            .sortedWith(compareBy(naturalComparator) { it.name })
            .map { it.toString() }
    }

    private val naturalComparator = Comparator<String> { a, b ->
        compareNatural(a, b)
    }

    /** 数字块按数值比较，非数字块按字符比较 */
    private fun compareNatural(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val startI = i
                while (i < a.length && a[i].isDigit()) i++
                val startJ = j
                while (j < b.length && b[j].isDigit()) j++
                val numA = a.substring(startI, i).trimStart('0').ifEmpty { "0" }
                val numB = b.substring(startJ, j).trimStart('0').ifEmpty { "0" }
                val lengthCmp = numA.length.compareTo(numB.length)
                if (lengthCmp != 0) return lengthCmp
                val valueCmp = numA.compareTo(numB)
                if (valueCmp != 0) return valueCmp
                // 数值相同但补零位数不同：位数少者优先（1 < 001）
                val padCmp = (i - startI).compareTo(j - startJ)
                if (padCmp != 0) return padCmp
            } else {
                if (ca != cb) return ca.compareTo(cb)
                i++
                j++
            }
        }
        return a.length - b.length
    }
}
