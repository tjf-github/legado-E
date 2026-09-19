package io.legado.app.help.ai

import android.content.Context
import io.legado.app.data.entities.Book
import splitties.init.appCtx
import java.io.File

/**
 * Android 入口：独立 AI 正文缓存，位于应用 `cacheDir/ai_text`。
 *
 * 与可 JVM 单测的 [AiChapterCache] 解耦——Android 上下文只在入口处提供缓存目录，
 * 不把 Android 依赖注入到代码核心。`BookHelp` 清理与删除书籍通过本类联动删除该书 AI 缓存。
 */
object AiAndroidAccess {
    private const val DIR_NAME = "ai_text"

    /** 应用级单例缓存。惰性初始化；应用运行时首次访问会发生在这里。 */
    val cache: AiChapterCache by lazy { AiChapterCache(File(appCtx.cacheDir, DIR_NAME)) }

    /** 显式构造（便于测试或注入不同 Context）。 */
    fun create(context: Context): AiChapterCache = AiChapterCache(File(context.cacheDir, DIR_NAME))

    /** 删除某本书的全部 AI 缓存。 */
    fun clearBook(book: Book) {
        cache.deleteBook(AiChapterIdentity.bookUrlHash(book.bookUrl))
    }

    /** 清除全部 AI 缓存。 */
    fun clearAll() {
        cache.deleteAll()
    }

    /** 清除不在 [validBookUrlHashes] 中的书籍 AI 缓存（已删除书籍的孤立缓存）。 */
    fun clearBooksNotIn(validBookUrlHashes: Set<String>) {
        cache.deleteBooksExcept(validBookUrlHashes)
    }
}
