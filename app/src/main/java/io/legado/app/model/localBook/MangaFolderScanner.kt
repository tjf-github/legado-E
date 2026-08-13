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
import android.content.ContentUris
import android.provider.MediaStore

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

    /** 相册导入书的 bookUrl 前缀（无实体目录，目录以导入时固化的章节为准） */
    const val ALBUM_URL_PREFIX = "album://"

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    /** 图片文件名白名单判断 */
    fun isImageName(name: String): Boolean {
        return name.substringAfterLast('.', "").lowercase() in imageExtensions
    }

    /** 封面命名判断：文件名含 cover 或 封面（大小写不敏感） */
    fun isCoverName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("cover") || lower.contains("封面")
    }

    /**
     * 混合结构建议角色：书文件夹直接图片数不少于各章节图片总数时，建议整本连看；
     * 行级切换仍可覆盖该建议
     */
    fun suggestWholeRole(directImageCount: Int, chapterImageCount: Int): Boolean {
        return directImageCount > 0 && directImageCount >= chapterImageCount
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
    fun scan(
        root: FileDoc,
        mode: Mode,
        onProgress: ((scanned: Int, total: Int) -> Unit)? = null
    ): List<MangaBookPreview> {
        val rootChildren = root.list { !it.name.startsWith(".") }.orEmpty()
        val previews = when (mode) {
            Mode.WHOLE -> listOf(scanBook(root, rootChildren, series = null))
            Mode.SERIES -> {
                val seriesDirs = rootChildren
                    .filter { it.isDir }
                    .sortedWith(compareBy(naturalComparator) { it.name })
                seriesDirs.mapIndexed { index, seriesDir ->
                    onProgress?.invoke(index + 1, seriesDirs.size)
                    val children = seriesDir.list { !it.name.startsWith(".") }.orEmpty()
                    scanBook(seriesDir, children, series = root.name)
                }
            }
        }
        return previews.map { preview ->
            preview.isDuplicate = appDb.bookDao.has(preview.bookKey)
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
        // 封面：命名含 cover/封面 的图片优先，其次书文件夹首图/首章节首图
        val coverImage = directImages.firstOrNull { isCoverName(it.name) }?.toString()
            ?: directImages.firstOrNull()?.toString()
            ?: chapterDirs.firstOrNull()?.images?.firstOrNull()
        return if (chapterDirs.isNotEmpty()) {
            val suggestWhole = suggestWholeRole(
                directImageCount = directImages.size,
                chapterImageCount = chapterDirs.sumOf { it.images.size }
            )
            MangaBookPreview(
                dir = bookDir,
                name = bookDir.name,
                group = series,
                isWhole = suggestWhole,
                canToggleWhole = directImages.isNotEmpty(),
                chapterDirs = chapterDirs,
                wholeImages = directImages.map { it.toString() },
                coverImage = coverImage
            )
        } else if (directImages.isNotEmpty()) {
            MangaBookPreview(
                dir = bookDir,
                name = bookDir.name,
                group = series,
                isWhole = true,
                canToggleWhole = false,
                wholeImages = directImages.map { it.toString() },
                coverImage = coverImage
            )
        } else {
            MangaBookPreview(
                dir = bookDir,
                name = bookDir.name,
                group = series,
                enabled = false,
                isSkipped = true,
                coverImage = coverImage
            )
        }
    }

    /**
     * 重新解析本地漫画书的章节列表（阅读器打开/刷新目录时使用）
     */
    fun scanChapters(book: Book): ArrayList<BookChapter> {
        if (book.bookUrl.startsWith(ALBUM_URL_PREFIX)) {
            // 相册导入的书没有实体目录：直接使用导入时固化的章节
            val dbChapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
            if (dbChapters.isNotEmpty()) {
                return ArrayList(dbChapters)
            }
            throw TocEmptyException(appCtx.getString(R.string.chapter_list_empty))
        }
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

    /**
     * 相册导入：扫描 MediaStore 图片相册，每个相册 = 一本书（整本连看单章节）
     */
    fun scanAlbums(): List<MangaBookPreview> {
        data class AlbumImages(val id: String, val name: String, val images: MutableList<String>)
        val albums = linkedMapOf<String, AlbumImages>()
        appCtx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DISPLAY_NAME
            ),
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            // Android 15+ 隐私限制：BUCKET_ID/BUCKET_DISPLAY_NAME 对他人媒体返回 null，
            // 改用 RELATIVE_PATH（如 Pictures/TestAlbum/）作为相册分组依据
            val pathCol = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val imageUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(idCol)
                ).toString()
                val relativePath = if (pathCol >= 0) cursor.getString(pathCol)?.trim('/') ?: "" else ""
                val albumId = relativePath.ifBlank {
                    // 无目录信息的图片（旧系统/异常媒体）：每张图独立成相册兜底
                    "album-${cursor.getLong(idCol)}"
                }
                val albumName = relativePath.substringAfterLast('/').ifBlank {
                    if (nameCol >= 0) cursor.getString(nameCol) ?: "未命名相册" else "未命名相册"
                }
                albums.getOrPut(albumId) { AlbumImages(albumId, albumName, arrayListOf()) }
                    .images.add(imageUri)
            }
        }
        return albums.values.sortedBy { it.name }.map { album ->
            MangaBookPreview(
                dir = null,
                bookUrl = ALBUM_URL_PREFIX + album.id,
                name = album.name,
                isWhole = true,
                canToggleWhole = false,
                wholeImages = album.images,
                coverImage = album.images.firstOrNull()
            )
        }.map { preview ->
            preview.isDuplicate = appDb.bookDao.has(preview.bookKey)
            preview
        }
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
