package io.legado.app.help.ai

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookContent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPlainTextTest {
    private val onlineText = Book(bookUrl = "book", origin = "https://source", type = BookType.text)

    @Test
    fun acceptsPlainTextAndMathematicalAngleBrackets() {
        listOf("plain", "a < b > c", "1 < 2", "isolated < and >").forEach { value ->
            assertTrue(AiPlainText.isAiPlainText(onlineText, content(value)))
        }
    }

    @Test
    fun rejectsStructuredMarkupImageAndLocalBooks() {
        listOf("<img src=\"x\">", "<usehtml>x</usehtml>", "<p>x</p>", "<font color=\"red\">x</font>", "<br/>", "<b>x</b>").forEach { value ->
            assertFalse(AiPlainText.isAiPlainText(onlineText, content(value)))
        }
        assertFalse(
            AiPlainText.isAiPlainText(
                onlineText.copy(type = BookType.image),
                content("plain")
            )
        )
        assertFalse(
            AiPlainText.isAiPlainText(
                onlineText.copy(origin = BookType.localTag, type = BookType.text or BookType.local),
                content("plain")
            )
        )
    }

    private fun content(value: String) = BookContent(false, listOf(value), null)
}
