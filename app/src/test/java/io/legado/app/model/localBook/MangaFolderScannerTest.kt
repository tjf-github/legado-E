package io.legado.app.model.localBook

import io.legado.app.data.entities.BookChapter
import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaFolderScannerTest {

    @Test
    fun naturalSortOrdersPaddedNumbersNumerically() {
        val sorted = MangaFolderScanner.naturalSort(
            listOf("10.jpg", "2.jpg", "001.jpg", "010.jpg", "002.jpg", "1.jpg")
        )
        assertEquals(
            listOf("1.jpg", "001.jpg", "2.jpg", "002.jpg", "10.jpg", "010.jpg"),
            sorted
        )
    }

    @Test
    fun naturalSortKeepsChapterNumbersInOrder() {
        val sorted = MangaFolderScanner.naturalSort(
            listOf("第10话", "第2话", "第1话", "第1话(2)")
        )
        assertEquals(
            listOf("第1话", "第1话(2)", "第2话", "第10话"),
            sorted
        )
    }

    @Test
    fun imageNameWhitelist() {
        listOf("001.jpg", "002.JPEG", "a.png", "b.webp", "c.bmp", "d.gif").forEach {
            assertTrue("$it 应被识别为图片", MangaFolderScanner.isImageName(it))
        }
        listOf("note.txt", "cover.pdf", "archive.zip", "").forEach {
            assertTrue("$it 不应被识别为图片", !MangaFolderScanner.isImageName(it))
        }
    }

    @Test
    fun coverNameDetection() {
        listOf("cover.jpg", "Cover.png", "封面.png", "the_cover.webp", "COVER_01.jpg").forEach {
            assertTrue("$it 应被识别为封面", MangaFolderScanner.isCoverName(it))
        }
        listOf("001.jpg", "chapter1.jpg", "intro.png", "").forEach {
            assertTrue("$it 不应被识别为封面", !MangaFolderScanner.isCoverName(it))
        }
    }

    @Test
    fun suggestWholeRoleHeuristic() {
        // 直接图片不少于章节图片总数时建议整本连看
        assertTrue(MangaFolderScanner.suggestWholeRole(3, 3))
        assertTrue(MangaFolderScanner.suggestWholeRole(4, 3))
        // 无直接图片或直接图片更少时建议子文件夹作为章节
        assertTrue(!MangaFolderScanner.suggestWholeRole(0, 3))
        assertTrue(!MangaFolderScanner.suggestWholeRole(1, 3))
        assertTrue(!MangaFolderScanner.suggestWholeRole(0, 0))
    }

    @Test
    fun groupAlbumsByRelativePath() {
        val rows = listOf(
            MangaFolderScanner.AlbumRow(1, "content://media/1", "Pictures/TestAlbum/", "001.jpg"),
            MangaFolderScanner.AlbumRow(2, "content://media/2", "Pictures/TestAlbum/", "002.jpg"),
            MangaFolderScanner.AlbumRow(3, "content://media/3", "Pictures/Other/", "003.jpg")
        )
        val albums = MangaFolderScanner.groupAlbums(rows)
        assertEquals(2, albums.size)
        // 按名称排序：Other 在前
        assertEquals("Other", albums[0].name)
        assertEquals(listOf("content://media/3"), albums[0].wholeImages)
        assertEquals("album://Pictures/Other", albums[0].bookUrl)
        assertEquals("TestAlbum", albums[1].name)
        assertEquals(listOf("content://media/1", "content://media/2"), albums[1].wholeImages)
        assertEquals("album://Pictures/TestAlbum", albums[1].bookUrl)
        assertEquals("content://media/1", albums[1].coverImage)
    }

    @Test
    fun groupAlbumsNullRelativePathFallsBackPerImage() {
        val rows = listOf(
            MangaFolderScanner.AlbumRow(10, "content://media/10", null, "shot.png"),
            MangaFolderScanner.AlbumRow(11, "content://media/11", null, null)
        )
        val albums = MangaFolderScanner.groupAlbums(rows)
        assertEquals(2, albums.size)
        assertEquals("shot.png", albums[0].name)
        assertEquals("album://image-10", albums[0].bookUrl)
        assertEquals("未命名相册", albums[1].name)
        assertEquals("album://image-11", albums[1].bookUrl)
    }

    @Test
    fun groupAlbumsOrdersNumericPrefixNumerically() {
        // 真机反馈：相册名 "100 第195-197" 与 "86 第167-168" 导入后顺序颠倒，
        // 纯字符串序会让 "100" < "86"（'1' < '8'），期望数字前缀按数值排序。
        val rows = listOf(
            MangaFolderScanner.AlbumRow(1, "content://media/1", "Pictures/100 第195-197/", "001.jpg"),
            MangaFolderScanner.AlbumRow(2, "content://media/2", "Pictures/86 第167-168/", "001.jpg")
        )
        val albums = MangaFolderScanner.groupAlbums(rows)
        assertEquals(
            listOf("86 第167-168", "100 第195-197"),
            albums.map { it.name }
        )
    }

    @Test
    fun groupAlbumsSortsImagesByName() {
        // 真机反馈：相册内各页乱序 —— MediaStore 按 DATE_ADDED 返回，需按文件名自然排序
        val rows = listOf(
            MangaFolderScanner.AlbumRow(1, "content://media/1", "Pictures/Manga/", "010.jpg"),
            MangaFolderScanner.AlbumRow(2, "content://media/2", "Pictures/Manga/", "2.jpg"),
            MangaFolderScanner.AlbumRow(3, "content://media/3", "Pictures/Manga/", "001.jpg"),
            MangaFolderScanner.AlbumRow(4, "content://media/4", "Pictures/Manga/", "1.jpg")
        )
        val albums = MangaFolderScanner.groupAlbums(rows)
        assertEquals(1, albums.size)
        assertEquals(
            listOf("content://media/4", "content://media/3", "content://media/2", "content://media/1"),
            albums[0].wholeImages
        )
    }

    @Test
    fun groupAlbumsEmpty() {
        assertTrue(MangaFolderScanner.groupAlbums(emptyList()).isEmpty())
    }

    @Test
    fun buildImgHtmlJoinsImageTags() {
        val html = MangaFolderScanner.buildImgHtml(listOf("content://a/1.jpg", "content://a/2.jpg"))
        assertEquals(
            "<img src=\"content://a/1.jpg\">\n<img src=\"content://a/2.jpg\">",
            html
        )
    }

    @Test
    fun imagesOfReadsVariableRoundTrip() {
        val chapter = BookChapter(
            variable = GSON.toJson(
                mapOf(MangaFolderScanner.IMG_KEY to "content://a/1.jpg\ncontent://a/2.jpg")
            )
        )
        assertEquals(
            listOf("content://a/1.jpg", "content://a/2.jpg"),
            MangaFolderScanner.imagesOf(chapter)
        )
    }
}
