package io.legado.app.data.entities

import org.junit.Assert.assertEquals
import org.junit.Test

class BookGroupTest {

    @Test
    fun textBookGroupsComeBeforeAllAndMangaComesAfter() {
        val orderedGroupIds = listOf(
            BookGroup.IdAll to BookGroup.OrderAll,
            BookGroup.IdLocalManga to BookGroup.OrderLocalManga,
            BookGroup.IdNetBook to BookGroup.OrderNetBook,
            BookGroup.IdLocal to BookGroup.OrderLocal,
        ).sortedBy { it.second }.map { it.first }

        assertEquals(
            listOf(
                BookGroup.IdNetBook,
                BookGroup.IdLocal,
                BookGroup.IdAll,
                BookGroup.IdLocalManga,
            ),
            orderedGroupIds,
        )
    }
}
