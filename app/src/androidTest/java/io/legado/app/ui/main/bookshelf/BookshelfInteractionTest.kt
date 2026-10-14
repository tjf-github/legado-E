package io.legado.app.ui.main.bookshelf

import android.view.ContextThemeWrapper
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.ui.main.bookshelf.style1.books.BaseBooksAdapter
import io.legado.app.ui.main.bookshelf.style1.books.BooksAdapterGrid
import io.legado.app.ui.main.bookshelf.style1.books.BooksAdapterList
import io.legado.app.ui.main.bookshelf.style1.books.BooksAdapterList2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Existing adapter callbacks are the seam: no database records, navigation or network fixtures. */
@RunWith(AndroidJUnit4::class)
class BookshelfInteractionTest {
    private val book = Book(bookUrl = "bookshelf-interaction-fixture", name = "Fixture", type = BookType.local)

    private class RecordingCallbacks : BaseBooksAdapter.CallBack {
        var opened: Book? = null
        var info: Book? = null
        override fun open(book: Book) { opened = book }
        override fun openBookInfo(book: Book) { info = book }
        override fun isUpdate(bookUrl: String) = false
        override fun onSelectionChanged() = Unit
    }

    private fun withAdapters(check: (BaseBooksAdapter<*>, View, RecordingCallbacks) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.AppTheme_Light)
            val owner = object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.STARTED }
            }
            val factories: List<(RecordingCallbacks) -> BaseBooksAdapter<*>> = listOf(
                { BooksAdapterList(context, Fragment(), it, owner.lifecycle) },
                { BooksAdapterList2(context, Fragment(), it, owner.lifecycle) },
                { BooksAdapterGrid(context, it) }
            )
            factories.forEach { create ->
                val callbacks = RecordingCallbacks()
                val adapter = create(callbacks)
                val recycler = RecyclerView(context).apply {
                    layoutManager = LinearLayoutManager(context)
                    this.adapter = adapter
                }
                adapter.setItems(listOf(book))
                recycler.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
                recycler.layout(0, 0, 1080, 1600)
                val row = requireNotNull(recycler.findViewHolderForAdapterPosition(0)).itemView
                try { check(adapter, row, callbacks) } finally { recycler.adapter = null }
            }
        }
    }

    @Test fun normalLongPressOpensBookInfoInEveryLayout() = withAdapters { adapter, row, callbacks ->
        assertTrue(row.performLongClick())
        assertEquals("Normal long press must request book information", book, callbacks.info)
        assertEquals(null, callbacks.opened)
        assertFalse(adapter.isSelectionMode)
    }

    @Test fun menuSelectionStartsEmptyAndCanSelectInEveryLayout() = withAdapters { adapter, row, callbacks ->
        adapter.enterSelectionMode()
        assertTrue(adapter.isSelectionMode)
        assertTrue(adapter.selection.isEmpty())
        row.performClick()
        assertEquals(listOf(book), adapter.selection)
        assertEquals(null, callbacks.opened)
        assertEquals(null, callbacks.info)
        adapter.exitSelectionMode()
    }

    @Test fun normalClickRequestsReadingInEveryLayout() = withAdapters { _, row, callbacks ->
        row.performClick()
        assertEquals(book, callbacks.opened)
        assertEquals(null, callbacks.info)
    }

    @Test fun selectionClickAndLongPressToggleWithoutNavigationInEveryLayout() = withAdapters { adapter, row, callbacks ->
        adapter.enterSelectionMode(book)
        assertEquals(listOf(book), adapter.selection)
        row.performClick()
        assertTrue(adapter.selection.isEmpty())
        row.performLongClick()
        assertEquals(listOf(book), adapter.selection)
        assertEquals(null, callbacks.opened)
        assertEquals(null, callbacks.info)
        adapter.exitSelectionMode()
        assertFalse(adapter.isSelectionMode)
        assertTrue(adapter.selection.isEmpty())
    }
}