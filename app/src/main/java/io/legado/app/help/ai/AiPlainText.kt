package io.legado.app.help.ai

import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookContent
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal

/**
 * AI 纯文本判定（唯一实现，供启用判断、测试语料、处理器共同复用）。
 *
 * 判定为「允许 AI 处理」需同时满足：
 *  - 非本地书、非图片书；
 *  - 每个正文段落都既不命中 `<img>` 图片格式、`<usehtml>` 自定义 HTML、
 *    也不命中真实 HTML 标签模式。
 *
 * HTML 标签模式要求 `<` 后立即出现 `/` 或合法标签名，因此：
 *  - `a < b > c`、`1 < 2`、孤立 `<`/`>` 判为允许的普通文本；
 *  - `<p>`、`<font color="red">`、`<br/>`、`<b>` 判为拒绝的结构化文本。
 */
object AiPlainText {

    /**
     * 真实 HTML 标签模式：`<` 后立即 `/` 或 `[A-Za-z]`，可带属性，可自闭合。
     * 与功能书 4.2 一致，刻意用「立即」限定避免误拒 `a < b > c`。
     */
    val htmlTagRegex = Regex("</?[A-Za-z][A-Za-z0-9:-]*(?:\\s+[^<>]*?)?\\s*/?>")

    fun isAiPlainText(book: Book, content: BookContent): Boolean {
        if (book.isLocal || book.isImage) {
            return false
        }
        return content.textList.all { paragraph ->
            !AppPattern.imgPattern.matcher(paragraph).find() &&
                !AppPattern.useHtmlRegex.containsMatchIn(paragraph) &&
                !htmlTagRegex.containsMatchIn(paragraph)
        }
    }
}
