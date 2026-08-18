package io.legado.app.help.book

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import org.junit.Assert.assertEquals
import org.junit.Test

class BookSorterTest {

    private val textCompare: (String, String) -> Int = { a, b -> a.compareTo(b) }

    private fun book(
        name: String,
        author: String = "",
        type: Int = BookType.text,
        latestChapterTime: Long = 0,
        durChapterTime: Long = 0,
        order: Int = 0
    ) = Book(
        bookUrl = name,
        name = name,
        author = author,
        type = type,
        latestChapterTime = latestChapterTime,
        durChapterTime = durChapterTime,
        order = order
    )

    @Test
    fun defaultSortsMangaByOrderThenOthersByReadTime() {
        val list = listOf(
            book("小说A", durChapterTime = 100),
            book("漫画1", type = BookType.image, order = 2, durChapterTime = 200),
            book("漫画2", type = BookType.image, order = 1, durChapterTime = 300),
            book("小说B", durChapterTime = 50),
        )
        val sorted = list.sortByBookshelf(0, textCompare = textCompare)
        assertEquals(listOf("漫画2", "漫画1", "小说A", "小说B"), sorted.map { it.name })
    }

    @Test
    fun reverseAppliesToDefaultSort() {
        val list = listOf(
            book("小说A", durChapterTime = 100),
            book("漫画1", type = BookType.image, order = 2),
            book("漫画2", type = BookType.image, order = 1),
            book("小说B", durChapterTime = 50),
        )
        val sorted = list.sortByBookshelf(0, reverse = true, textCompare = textCompare)
        assertEquals(listOf("小说B", "小说A", "漫画1", "漫画2"), sorted.map { it.name })
    }

    @Test
    fun defaultSortWithoutMangaStableUsesReadTimeOnly() {
        val list = listOf(
            book("漫画1", type = BookType.image, order = 1, durChapterTime = 100),
            book("漫画2", type = BookType.image, order = 2, durChapterTime = 200),
            book("小说A", durChapterTime = 150),
        )
        val sorted = list.sortByBookshelf(0, mangaStable = false, textCompare = textCompare)
        assertEquals(listOf("漫画2", "小说A", "漫画1"), sorted.map { it.name })
    }

    @Test
    fun sortsByUpdateTimeDescending() {
        val list = listOf(
            book("A", latestChapterTime = 10),
            book("B", latestChapterTime = 30),
            book("C", latestChapterTime = 20),
        )
        val sorted = list.sortByBookshelf(1, textCompare = textCompare)
        assertEquals(listOf("B", "C", "A"), sorted.map { it.name })
    }

    @Test
    fun reverseSortsByUpdateTimeAscending() {
        val list = listOf(
            book("A", latestChapterTime = 10),
            book("B", latestChapterTime = 30),
            book("C", latestChapterTime = 20),
        )
        val sorted = list.sortByBookshelf(1, reverse = true, textCompare = textCompare)
        assertEquals(listOf("A", "C", "B"), sorted.map { it.name })
    }

    @Test
    fun sortsByNameNaturally() {
        // 自然排序：a1 < a2 < a10，而不是 a1,a10,a2
        val list = listOf(
            book("a10"),
            book("a2"),
            book("a1"),
        )
        val sorted = list.sortByBookshelf(2, textCompare = textCompare)
        assertEquals(listOf("a1", "a2", "a10"), sorted.map { it.name })
    }

    @Test
    fun reverseSortsByNameDescending() {
        val list = listOf(
            book("a10"),
            book("a2"),
            book("a1"),
        )
        val sorted = list.sortByBookshelf(2, reverse = true, textCompare = textCompare)
        assertEquals(listOf("a10", "a2", "a1"), sorted.map { it.name })
    }

    @Test
    fun sortsByManualOrderAscending() {
        val list = listOf(
            book("A", order = 3),
            book("B", order = 1),
            book("C", order = 2),
        )
        val sorted = list.sortByBookshelf(3, textCompare = textCompare)
        assertEquals(listOf("B", "C", "A"), sorted.map { it.name })
    }

    @Test
    fun reverseSortsByManualOrderDescending() {
        val list = listOf(
            book("A", order = 3),
            book("B", order = 1),
            book("C", order = 2),
        )
        val sorted = list.sortByBookshelf(3, reverse = true, textCompare = textCompare)
        assertEquals(listOf("A", "C", "B"), sorted.map { it.name })
    }

    @Test
    fun sortsByComprehensiveTimeDescending() {
        val list = listOf(
            book("A", latestChapterTime = 100),
            book("B", durChapterTime = 80),
            book("C", latestChapterTime = 50, durChapterTime = 50),
        )
        val sorted = list.sortByBookshelf(4, textCompare = textCompare)
        assertEquals(listOf("A", "B", "C"), sorted.map { it.name })
    }

    @Test
    fun reverseSortsByComprehensiveTimeAscending() {
        val list = listOf(
            book("A", latestChapterTime = 100),
            book("B", durChapterTime = 80),
            book("C", latestChapterTime = 50, durChapterTime = 50),
        )
        val sorted = list.sortByBookshelf(4, reverse = true, textCompare = textCompare)
        assertEquals(listOf("C", "B", "A"), sorted.map { it.name })
    }

    @Test
    fun sortsByAuthorNaturally() {
        val list = listOf(
            book("A", author = "作者B"),
            book("B", author = "作者A"),
            book("C", author = "作者C"),
        )
        val sorted = list.sortByBookshelf(5, textCompare = textCompare)
        assertEquals(listOf("B", "A", "C"), sorted.map { it.name })
    }

    @Test
    fun reverseSortsByAuthorDescending() {
        val list = listOf(
            book("A", author = "作者B"),
            book("B", author = "作者A"),
            book("C", author = "作者C"),
        )
        val sorted = list.sortByBookshelf(5, reverse = true, textCompare = textCompare)
        assertEquals(listOf("C", "A", "B"), sorted.map { it.name })
    }
}
