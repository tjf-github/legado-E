package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter

/** 待删除章节：bookUrl + url */
data class ChapterDelete(val bookUrl: String, val url: String)

/**
 * 章节重排：url 对应章节更新为 newIndex / newTitle。
 * 调用方须保证列表顺序满足唯一索引约束：
 * 右移（新增场景）从后往前，左移（删除场景）从前往后。
 */
data class ChapterShift(
    val bookUrl: String,
    val url: String,
    val newIndex: Int,
    val newTitle: String,
    /** 编辑后字数（拆分/合并等重写正文的章节），null 表示不更新 */
    val newWordCount: String? = null
)

/** 书签迁移：匹配 bookName + bookAuthor + oldIndex，更新为新序号与新标题 */
data class BookmarkShift(
    val bookName: String,
    val bookAuthor: String,
    val oldIndex: Int,
    val newIndex: Int,
    val newTitle: String
)

/** 删除书签：匹配 bookName + bookAuthor + chapterIndex */
data class BookmarkDelete(
    val bookName: String,
    val bookAuthor: String,
    val index: Int
)

@Dao
interface BookChapterDao {

    @Query("SELECT * FROM chapters where bookUrl = :bookUrl and title like '%'||:key||'%' order by `index`")
    fun search(bookUrl: String, key: String): List<BookChapter>

    @Query("SELECT * FROM chapters where bookUrl = :bookUrl and `index` >= :start and `index` <= :end and title like '%'||:key||'%' order by `index`")
    fun search(bookUrl: String, key: String, start: Int, end: Int): List<BookChapter>

    @Query("select * from chapters where bookUrl = :bookUrl order by `index`")
    fun getChapterList(bookUrl: String): List<BookChapter>

    @Query("select * from chapters where bookUrl = :bookUrl and `index` >= :start and `index` <= :end order by `index`")
    fun getChapterList(bookUrl: String, start: Int, end: Int): List<BookChapter>

    @Query("select * from chapters where bookUrl = :bookUrl and `index` = :index")
    fun getChapter(bookUrl: String, index: Int): BookChapter?

    @Query("select * from chapters where bookUrl = :bookUrl and `title` = :title")
    fun getChapter(bookUrl: String, title: String): BookChapter?

    @Query("select count(url) from chapters where bookUrl = :bookUrl")
    fun getChapterCount(bookUrl: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg bookChapter: BookChapter)

    @Update
    fun update(vararg bookChapter: BookChapter)

    @Query("delete from chapters where bookUrl = :bookUrl")
    fun delByBook(bookUrl: String)

    @Query("delete from chapters where bookUrl = :bookUrl and url = :url")
    fun delChapter(bookUrl: String, url: String)

    @Query("update chapters set `index` = :index, title = :title where bookUrl = :bookUrl and url = :url")
    fun upIndexTitle(bookUrl: String, url: String, index: Int, title: String)

    @Query("update chapters set wordCount = :wordCount where bookUrl = :bookUrl and url = :url")
    fun upWordCount(bookUrl: String, url: String, wordCount: String)

    /**
     * 目录批量编辑的原子操作（事务）：
     * 删除章节、章节重排（index/title）、插入章节、书签删除/迁移、书籍元数据更新一体完成。
     * 任一操作失败则整体回滚，不留半完成状态。
     * 正文覆盖文件的写入/改名由调用方在事务外完成（失败仅告警，不阻断数据库操作）。
     */
    @Transaction
    fun applyTocEdit(
        bookDao: BookDao,
        bookmarkDao: BookmarkDao,
        book: Book,
        chapterDeletes: List<ChapterDelete>,
        chapterInserts: List<BookChapter>,
        chapterShifts: List<ChapterShift>,
        bookmarkDeletes: List<BookmarkDelete>,
        bookmarkShifts: List<BookmarkShift>
    ) {
        chapterDeletes.forEach { delChapter(it.bookUrl, it.url) }
        chapterShifts.forEach {
            upIndexTitle(it.bookUrl, it.url, it.newIndex, it.newTitle)
            it.newWordCount?.let { wc -> upWordCount(it.bookUrl, it.url, wc) }
        }
        chapterInserts.forEach { insert(it) }
        bookmarkDeletes.forEach { bookmarkDao.delByChapterIndex(it.bookName, it.bookAuthor, it.index) }
        bookmarkShifts.forEach {
            bookmarkDao.shiftChapter(it.bookName, it.bookAuthor, it.oldIndex, it.newIndex, it.newTitle)
        }
        bookDao.update(book)
    }

}
