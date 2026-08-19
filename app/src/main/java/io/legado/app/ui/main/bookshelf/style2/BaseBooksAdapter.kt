package io.legado.app.ui.main.bookshelf.style2

import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcelable
import android.view.LayoutInflater
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup

abstract class BaseBooksAdapter<VH : RecyclerView.ViewHolder>(
    val context: Context,
    val callBack: CallBack
) : RecyclerView.Adapter<VH>() {
    private val layoutStates = mutableMapOf<Long, Parcelable?>()
    private var currentGroupId: Long? = null
    private var layoutManager: RecyclerView.LayoutManager? = null
    protected val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        layoutManager = recyclerView.layoutManager
    }

    private val diffItemCallback = object : DiffUtil.ItemCallback<Any>() {

        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when {
                oldItem is Book && newItem is Book -> {
                    oldItem.name == newItem.name
                            && oldItem.author == newItem.author
                }

                oldItem is BookGroup && newItem is BookGroup -> {
                    oldItem.groupId == newItem.groupId
                }

                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when {
                oldItem is Book && newItem is Book -> {
                    oldItem.durChapterTime == newItem.durChapterTime &&
                            oldItem.name == newItem.name &&
                            oldItem.author == newItem.author &&
                            oldItem.durChapterTitle == newItem.durChapterTitle &&
                            oldItem.latestChapterTitle == newItem.latestChapterTitle &&
                            oldItem.lastCheckCount == newItem.lastCheckCount &&
                            oldItem.getDisplayCover() == newItem.getDisplayCover() &&
                            oldItem.getUnreadChapterNum() == newItem.getUnreadChapterNum()
                }

                oldItem is BookGroup && newItem is BookGroup -> {
                    oldItem.groupName == newItem.groupName &&
                            oldItem.cover == newItem.cover &&
                            oldItem.enableRefresh == newItem.enableRefresh &&
                            oldItem.onlyUpdateRead == newItem.onlyUpdateRead
                }

                else -> false
            }
        }

        override fun getChangePayload(oldItem: Any, newItem: Any): Any? {
            val bundle = bundleOf()
            when {
                oldItem is Book && newItem is Book -> {
                    if (oldItem.name != newItem.name) {
                        bundle.putString("name", newItem.name)
                    }
                    if (oldItem.author != newItem.author) {
                        bundle.putString("author", newItem.author)
                    }
                    if (oldItem.durChapterTitle != newItem.durChapterTitle) {
                        bundle.putString("dur", newItem.durChapterTitle)
                    }
                    if (oldItem.latestChapterTitle != newItem.latestChapterTitle) {
                        bundle.putString("last", newItem.latestChapterTitle)
                    }
                    if (oldItem.getDisplayCover() != newItem.getDisplayCover()) {
                        bundle.putString("cover", newItem.getDisplayCover())
                    }
                    if (oldItem.lastCheckCount != newItem.lastCheckCount
                        || oldItem.durChapterTime != newItem.durChapterTime
                        || oldItem.getUnreadChapterNum() != newItem.getUnreadChapterNum()
                    ) {
                        bundle.putBoolean("refresh", true)
                    }
                }

                oldItem is BookGroup && newItem is BookGroup -> {
                    if (oldItem.groupName != newItem.groupName) {
                        bundle.putString("groupName", newItem.groupName)
                    }
                    if (oldItem.cover != newItem.cover) {
                        bundle.putString("cover", newItem.cover)
                    }
                    if (oldItem.enableRefresh != newItem.enableRefresh || oldItem.onlyUpdateRead != newItem.onlyUpdateRead) {
                        bundle.putBoolean("unviewable", true)
                    }
                }
            }
            if (bundle.isEmpty) return null
            return bundle
        }
    }

    private val asyncListDiffer by lazy {
        AsyncListDiffer(this, diffItemCallback).apply {
            addListListener { _, _ ->
                currentGroupId?.let {
                    layoutManager?.onRestoreInstanceState(layoutStates[it])
                    layoutStates[it] = null
                }
            }
        }
    }

    /**
     * 多选模式：按 bookUrl 记录选中项（书架列表经 DB flow 每次发出新对象，不能用对象相等性）
     * 仅在进入分组后（列表全为 Book）生效
     */
    private val selectedBooks = hashSetOf<String>()

    var isSelectionMode = false
        private set

    val selection: List<Book>
        get() = getItems().filterIsInstance<Book>().filter { selectedBooks.contains(it.bookUrl) }

    fun isSelected(book: Book): Boolean = selectedBooks.contains(book.bookUrl)

    /**
     * 长按进入多选模式并默认选中该书
     */
    fun enterSelectionMode(book: Book) {
        isSelectionMode = true
        selectedBooks.add(book.bookUrl)
        notifyDataSetChanged()
    }

    fun exitSelectionMode() {
        if (!isSelectionMode && selectedBooks.isEmpty()) return
        isSelectionMode = false
        selectedBooks.clear()
        notifyDataSetChanged()
    }

    fun toggle(book: Book) {
        if (selectedBooks.contains(book.bookUrl)) {
            selectedBooks.remove(book.bookUrl)
        } else {
            selectedBooks.add(book.bookUrl)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectAll(selectAll: Boolean) {
        if (selectAll) {
            getItems().filterIsInstance<Book>().forEach {
                selectedBooks.add(it.bookUrl)
            }
        } else {
            selectedBooks.clear()
        }
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun revertSelection() {
        getItems().filterIsInstance<Book>().forEach {
            if (selectedBooks.contains(it.bookUrl)) {
                selectedBooks.remove(it.bookUrl)
            } else {
                selectedBooks.add(it.bookUrl)
            }
        }
        notifyDataSetChanged()
    }

    fun updateItems(groupId: Long) {
        currentGroupId?.let {
            layoutStates[it] = layoutManager?.onSaveInstanceState()
        }
        currentGroupId = groupId
        asyncListDiffer.submitList(callBack.getItems())
    }

    fun notification(bookUrl: String) {
        for (i in 0 until itemCount) {
            getItem(i).let {
                if (it is Book && it.bookUrl == bookUrl) {
                    notifyItemChanged(i, bundleOf(Pair("refresh", null)))
                    return
                }
            }
        }
    }

    fun getItems() = asyncListDiffer.currentList

    fun getItem(position: Int) = getItems().getOrNull(position)

    override fun getItemCount(): Int {
        return getItems().size
    }

    override fun getItemViewType(position: Int): Int {
        if (getItem(position) is BookGroup) {
            return 1
        }
        return 0
    }

    final override fun onBindViewHolder(holder: VH, position: Int) {}


    interface CallBack {
        fun onItemClick(item: Any)
        fun onItemLongClick(item: Any)
        fun isUpdate(bookUrl: String): Boolean
        fun getItems(): List<Any>

        /**
         * 多选数量变化（单项切换/全选/反选）后回调
         */
        fun onSelectionChanged()
    }
}