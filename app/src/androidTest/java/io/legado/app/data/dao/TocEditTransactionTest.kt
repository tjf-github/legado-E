package io.legado.app.data.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.Bookmark
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 目录批量编辑事务（applyTocEdit）的原子性验证：
 * 成功时重排/插入/书签/书籍元数据一体生效；中途失败时整体回滚，不留半完成状态。
 */
@RunWith(AndroidJUnit4::class)
class TocEditTransactionTest {

    private lateinit var db: AppDatabase
    private val bookUrl = "test_book"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        db.bookDao.insert(Book(bookUrl = bookUrl, name = "测试书"))
        db.bookChapterDao.insert(
            BookChapter(url = "c1", title = "第一章", bookUrl = bookUrl, index = 0),
            BookChapter(url = "c2", title = "第二章", bookUrl = bookUrl, index = 1),
            BookChapter(url = "c3", title = "第三章", bookUrl = bookUrl, index = 2)
        )
        db.bookmarkDao.insert(
            Bookmark(
                bookName = "测试书",
                chapterIndex = 1,
                chapterName = "第二章"
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertWithShiftCommitsAtomically() {
        val book = db.bookDao.getBook(bookUrl)!!
        db.bookChapterDao.applyTocEdit(
            bookDao = db.bookDao,
            bookmarkDao = db.bookmarkDao,
            book = book.apply { totalChapterNum = 4 },
            chapterDeletes = emptyList(),
            chapterInserts = listOf(
                BookChapter(url = "c_new", title = "新章", bookUrl = bookUrl, index = 1)
            ),
            chapterShifts = listOf(
                ChapterShift(bookUrl, "c3", 3, "第四章"),
                ChapterShift(bookUrl, "c2", 2, "第三章")
            ),
            bookmarkDeletes = emptyList(),
            bookmarkShifts = listOf(
                BookmarkShift("测试书", "", 1, 2, "第三章")
            )
        )
        val toc = db.bookChapterDao.getChapterList(bookUrl)
        assertEquals(listOf("第一章", "新章", "第三章", "第四章"), toc.map { it.title })
        assertEquals(listOf(0, 1, 2, 3), toc.map { it.index })
        assertEquals(4, db.bookDao.getBook(bookUrl)!!.totalChapterNum)
        val shifted = db.bookmarkDao.getByBook("测试书", "")
        assertEquals(1, shifted.size)
        assertEquals(2, shifted[0].chapterIndex)
        assertEquals("第三章", shifted[0].chapterName)
    }

    @Test
    fun failureRollsBackAllChanges() {
        val book = db.bookDao.getBook(bookUrl)!!
        var thrown: Throwable? = null
        try {
            db.bookChapterDao.applyTocEdit(
                bookDao = db.bookDao,
                bookmarkDao = db.bookmarkDao,
                book = book.apply { totalChapterNum = 99 },
                chapterDeletes = emptyList(),
                chapterInserts = emptyList(),
                // 第一条右移成功（c3 -> 3），第二条与新的 index 3 冲突，触发整体回滚
                chapterShifts = listOf(
                    ChapterShift(bookUrl, "c3", 3, "第四章"),
                    ChapterShift(bookUrl, "c2", 3, "冲突章")
                ),
                bookmarkDeletes = emptyList(),
                bookmarkShifts = listOf(
                    BookmarkShift("测试书", "", 1, 2, "第三章")
                )
            )
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue("事务应抛出唯一索引冲突异常", thrown != null)
        val toc = db.bookChapterDao.getChapterList(bookUrl)
        assertEquals(listOf("第一章", "第二章", "第三章"), toc.map { it.title })
        assertEquals(listOf(0, 1, 2), toc.map { it.index })
        assertEquals(0, db.bookDao.getBook(bookUrl)!!.totalChapterNum)
        val bookmark = db.bookmarkDao.getByBook("测试书", "")
        assertEquals(1, bookmark.size)
        assertEquals(1, bookmark[0].chapterIndex)
        assertEquals("第二章", bookmark[0].chapterName)
    }
}
