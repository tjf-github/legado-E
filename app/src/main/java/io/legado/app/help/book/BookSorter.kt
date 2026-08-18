package io.legado.app.help.book

import io.legado.app.data.entities.Book
import io.legado.app.utils.cnCompare
import io.legado.app.utils.naturalCompareWith
import kotlin.math.max

/**
 * 书架排序：六种排序方式 + 逆序开关。
 *
 * @param bookSort 排序方式：0=按阅读时间（默认），1=按更新时间，2=按书名，
 *                 3=手动排序，4=综合排序，5=按作者
 * @param reverse  是否逆序（对六种排序统一生效）
 * @param mangaStable 默认排序下漫画书是否按 order 稳定排序（读完不跳动）
 * @param textCompare 文本块比较器（默认中文拼音 Collator）；数字块始终按数值自然排序，
 *                    单测可注入纯 JVM 实现
 */
fun List<Book>.sortByBookshelf(
    bookSort: Int,
    reverse: Boolean = false,
    mangaStable: Boolean = true,
    textCompare: (String, String) -> Int = String::cnCompare
): List<Book> {
    val sorted = when (bookSort) {
        // 按更新时间
        1 -> sortedByDescending { it.latestChapterTime }
        // 按书名（自然排序）
        2 -> sortedWith { o1, o2 -> naturalCompareWith(o1.name, o2.name, textCompare) }
        // 手动排序
        3 -> sortedBy { it.order }
        // 综合排序
        4 -> sortedByDescending { max(it.latestChapterTime, it.durChapterTime) }
        // 按作者（自然排序）
        5 -> sortedWith { o1, o2 -> naturalCompareWith(o1.author, o2.author, textCompare) }
        // 默认：按阅读时间；漫画有阅读顺序，按 order 稳定排序（读完不跳动）
        else -> if (mangaStable) {
            val (manga, others) = partition { it.isImage }
            manga.sortedBy { it.order } + others.sortedByDescending { it.durChapterTime }
        } else {
            sortedByDescending { it.durChapterTime }
        }
    }
    return if (reverse) sorted.reversed() else sorted
}
