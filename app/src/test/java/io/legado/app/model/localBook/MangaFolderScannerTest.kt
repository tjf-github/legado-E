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
