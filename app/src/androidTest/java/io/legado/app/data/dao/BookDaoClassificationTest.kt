package io.legado.app.data.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 书架分类查询（书源书籍 / 本地小说 / 本地漫画）回归验证 */
@RunWith(AndroidJUnit4::class)
class BookDaoClassificationTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        db.bookDao.insert(
            Book(bookUrl = "net1", name = "在线书", type = BookType.text),
            Book(bookUrl = "local_novel", name = "本地小说", type = BookType.local or BookType.text),
            Book(bookUrl = "local_manga", name = "本地漫画", type = BookType.local or BookType.image),
            Book(bookUrl = "audio1", name = "有声书", type = BookType.local or BookType.audio)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun classificationQueries() = runBlocking {
        // 书源书籍：非本地、非音频/视频
        val netBooks = db.bookDao.flowNetBook().first()
        assertTrue(netBooks.any { it.bookUrl == "net1" })
        assertTrue(netBooks.none { it.bookUrl == "local_novel" })
        assertTrue(netBooks.none { it.bookUrl == "audio1" })

        // 本地小说：本地且非图片
        val localNovels = db.bookDao.flowLocal().first()
        assertTrue(localNovels.any { it.bookUrl == "local_novel" })
        assertTrue(localNovels.none { it.bookUrl == "local_manga" })

        // 本地漫画：本地且图片
        val localMangas = db.bookDao.flowLocalManga().first()
        assertTrue(localMangas.any { it.bookUrl == "local_manga" })
        assertTrue(localMangas.none { it.bookUrl == "local_novel" })
    }
}
