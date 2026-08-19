package io.legado.app.ui.main.bookshelf.style2

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBookshelf2Binding
import io.legado.app.help.book.sortByBookshelf
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.group.GroupEditDialog
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.utils.dpToPx
import io.legado.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.legado.app.utils.gone
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 书架界面
 */
class BookshelfFragment2() : BaseBookshelfFragment(R.layout.fragment_bookshelf2),
    SearchView.OnQueryTextListener,
    BaseBooksAdapter.CallBack,
    SelectActionBar.CallBack,
    PopupMenu.OnMenuItemClickListener {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBookshelf2Binding::bind)
    private val bookshelfLayout by lazy { AppConfig.bookshelfLayout }
    private val booksAdapter: BaseBooksAdapter<*> by lazy {
        if (bookshelfLayout >= 2) {
            BooksAdapterGrid(requireContext(), this)
        } else {
            BooksAdapterList(requireContext(), this)
        }
    }
    private var bookGroups: List<BookGroup> = emptyList()
    private var booksFlowJob: Job? = null
    override var groupId = BookGroup.IdRoot
    override var books: List<Book> = emptyList()
    private var enableRefresh = true
    override var onlyUpdateRead = false
    private val bookshelfMargin by lazy { AppConfig.bookshelfMargin }
    private var itemCount = 0
    private var totalRows = 0
    private val addToGroupRequestCode = 34
    private val removeToGroupRequestCode = 42
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            exitSelectionMode()
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        initSelectActionBar()
        initRecyclerView()
        initBookGroupData()
        initBooksData()
    }

    /**
     * 多选模式：长按进入、底部操作栏全选/反选/删除/移组，back 或「完成」退出；
     * 仅在进入分组后（列表全为书籍）生效
     */
    private fun initSelectActionBar() {
        binding.selectActionBar.setMainActionText(R.string.delete)
        binding.selectActionBar.inflateMenu(R.menu.bookshelf_sel)
        binding.selectActionBar.setOnMenuItemClickListener(this)
        binding.selectActionBar.setCallBack(this)
        binding.selectActionBar.setOnCloseListener {
            exitSelectionMode()
        }
    }

    private fun enterSelectionMode(book: Book) {
        booksAdapter.enterSelectionMode(book)
        upSelectionBar()
    }

    private fun exitSelectionMode() {
        booksAdapter.exitSelectionMode()
        upSelectionBar()
    }

    private fun upSelectionBar() {
        val inSelection = booksAdapter.isSelectionMode
        binding.selectActionBar.run {
            if (inSelection) {
                visible()
            } else {
                gone()
            }
            setCloseIconVisible(inSelection)
        }
        binding.refreshLayout.isEnabled =
            enableRefresh && itemCount > 0 && !inSelection
        backCallback.isEnabled = inSelection
        upSelectCount()
    }

    private fun upSelectCount() {
        binding.selectActionBar.upCountView(booksAdapter.selection.size, booksAdapter.itemCount)
    }

    override fun onPause() {
        super.onPause()
        // 切换页面/退后台时退出多选模式，避免状态残留
        if (booksAdapter.isSelectionMode) {
            exitSelectionMode()
        }
    }

    private fun initRecyclerView() {
        binding.rvBookshelf.setEdgeEffectColor(primaryColor)
        binding.refreshLayout.setColorSchemeColors(accentColor)
        binding.refreshLayout.setOnRefreshListener {
            binding.refreshLayout.isRefreshing = false
            activityViewModel.upToc(books, onlyUpdateRead)
        }
        if (bookshelfLayout >= 2) {
            binding.rvBookshelf.layoutManager = GridLayoutManager(context, bookshelfLayout)
        } else {
            binding.rvBookshelf.layoutManager = LinearLayoutManager(context)
        }
        binding.rvBookshelf.adapter = booksAdapter
        /**
         * 采用 layoutManager?.onRestoreInstanceState(layoutState)
         * 恢复滚动位置
         * **/
        binding.rvBookshelf.itemAnimator =  null
        binding.rvBookshelf.addItemDecoration( object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val position = parent.getChildAdapterPosition(view)
                if (bookshelfLayout >= 2) {
                    val spanCount = bookshelfLayout
                    val rowIndex = position / spanCount
                    when (rowIndex) {
                        0 -> { //第一行加额外上边距
                            outRect.set(bookshelfMargin, bookshelfMargin + 24, bookshelfMargin, bookshelfMargin)
                        }
                        totalRows - 1 -> { //最后一行加额外下边距
                            outRect.set(bookshelfMargin, bookshelfMargin, bookshelfMargin, bookshelfMargin + 24)
                        }
                        else -> {
                            outRect.set(bookshelfMargin, bookshelfMargin, bookshelfMargin, bookshelfMargin)
                        }
                    }
                } else {
                    when (position) {
                        0 -> {
                            outRect.set(0, bookshelfMargin + 24, 0, bookshelfMargin)
                        }
                        itemCount - 1 -> {
                            outRect.set(0, bookshelfMargin, 0, bookshelfMargin + 24)
                        }
                        else -> {
                            outRect.set(0, bookshelfMargin, 0, bookshelfMargin)
                        }
                    }
                }
            }
        })
    }

    override fun upGroup(data: List<BookGroup>) {
        if (data != bookGroups) {
            bookGroups = data
            booksAdapter.updateItems(groupId)
            itemCount = getItemCount()
            val spanCount = bookshelfLayout
            if (spanCount >= 2) {
                totalRows = if (itemCount % spanCount == 0) itemCount / spanCount else itemCount / spanCount + 1
            }
            binding.tvEmptyMsg.isGone = itemCount > 0
            binding.refreshLayout.isEnabled = enableRefresh && itemCount > 0
        }
    }

    override fun upSort() {
        initBooksData()
    }

    private fun initBooksData() {
        if (groupId == BookGroup.IdRoot) {
            if (isAdded) {
                binding.titleBar.title = getString(R.string.bookshelf)
                binding.refreshLayout.isEnabled = true
                enableRefresh = true
            }
        } else {
            bookGroups.firstOrNull {
                groupId == it.groupId
            }?.let {
                binding.titleBar.title = "${getString(R.string.bookshelf)}(${it.groupName})"
                binding.refreshLayout.isEnabled = it.enableRefresh
                enableRefresh = it.enableRefresh
                onlyUpdateRead = it.onlyUpdateRead
            }
        }
        booksFlowJob?.cancel()
        booksFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowByGroup(groupId).map { list ->
                //排序：六种排序 + 逆序
                list.sortByBookshelf(
                    AppConfig.getBookSortByGroupId(groupId),
                    reverse = AppConfig.bookshelfSortReverse
                )
            }.flowWithLifecycleAndDatabaseChangeFirst(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.BOOK_TABLE_NAME
            ).catch {
                AppLog.put("书架更新出错", it)
            }.conflate().flowOn(Dispatchers.Default).collect { list ->
                books = list
                booksAdapter.updateItems(groupId)
                itemCount = getItemCount()
                val spanCount = bookshelfLayout
                if (spanCount >= 2) {
                    totalRows = if (itemCount % spanCount == 0) itemCount / spanCount else itemCount / spanCount + 1
                }
                binding.tvEmptyMsg.isGone = itemCount > 0
                binding.refreshLayout.isEnabled =
                    enableRefresh && itemCount > 0 && !booksAdapter.isSelectionMode
                delay(100)
            }
        }
    }

    fun back(): Boolean {
        if (groupId != BookGroup.IdRoot) {
            groupId = BookGroup.IdRoot
            initBooksData()
            return true
        }
        return false
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        SearchActivity.start(requireContext(), query)
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return false
    }

    override fun gotoTop() {
        if (AppConfig.isEInkMode) {
            binding.rvBookshelf.scrollToPosition(0)
        } else {
            binding.rvBookshelf.smoothScrollToPosition(0)
        }
    }

    override fun onItemClick(item: Any) {
        when (item) {
            is Book -> startActivityForBook(item)

            is BookGroup -> {
                groupId = item.groupId
                initBooksData()
            }
        }
    }

    override fun onItemLongClick(item: Any) {
        when (item) {
            is Book -> {
                // 根视图混排分组与书籍，长按保持原行为；进入分组后长按进入多选
                if (groupId == BookGroup.IdRoot) {
                    startActivity<BookInfoActivity> {
                        putExtra("name", item.name)
                        putExtra("author", item.author)
                    }
                } else {
                    enterSelectionMode(item)
                }
            }

            is BookGroup -> showDialogFragment(GroupEditDialog(item))
        }
    }

    override fun onSelectionChanged() {
        upSelectCount()
    }

    override fun selectAll(selectAll: Boolean) {
        booksAdapter.selectAll(selectAll)
        upSelectCount()
    }

    override fun revertSelection() {
        booksAdapter.revertSelection()
        upSelectCount()
    }

    override fun onClickSelectBarMainAction() {
        alertDelSelection()
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_del_selection -> alertDelSelection()
            R.id.menu_add_to_group -> selectGroup(true)
            R.id.menu_remove_to_group -> selectGroup(false)
            R.id.menu_exit_select -> exitSelectionMode()
        }
        return false
    }

    private fun alertDelSelection() {
        alert(titleResource = R.string.draw, messageResource = R.string.sure_del) {
            val checkBox = CheckBox(requireContext()).apply {
                setText(R.string.delete_book_file)
                isChecked = LocalConfig.deleteBookOriginal
            }
            val view = LinearLayout(requireContext()).apply {
                setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
                addView(checkBox)
            }
            customView { view }
            okButton {
                LocalConfig.deleteBookOriginal = checkBox.isChecked
                viewModel.deleteBook(booksAdapter.selection, checkBox.isChecked)
                exitSelectionMode()
            }
            noButton()
        }
    }

    /**
     * 内联分组多选：移至/移出分组（GroupSelectDialog 回调绑定在 Activity 上，分组页内无法直达，
     * 故仿章节多选弹窗自建）
     */
    private fun selectGroup(addTo: Boolean) {
        lifecycleScope.launch {
            val groups = withContext(Dispatchers.IO) {
                appDb.bookGroupDao.flowAll().first().filter { it.groupId > 0 }
            }
            if (groups.isEmpty()) return@launch
            val checked = MutableList(groups.size) { false }
            val context = requireContext()
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12.dpToPx(), 8.dpToPx(), 12.dpToPx(), 8.dpToPx())
            }
            groups.forEachIndexed { index, group ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                }
                val checkBox = CheckBox(context).apply {
                    isChecked = false
                    setOnCheckedChangeListener { _, isChecked -> checked[index] = isChecked }
                }
                row.addView(
                    checkBox,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
                row.addView(
                    TextView(context).apply {
                        text = group.groupName
                        textSize = 14f
                        setTextColor(primaryColor)
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
                container.addView(row)
            }
            val scroll = ScrollView(context).apply {
                addView(container)
            }
            alert(if (addTo) R.string.add_to_group else R.string.remove_group) {
                setCustomView(scroll)
                okButton {
                    val groupIds = groups.filterIndexed { index, _ -> checked[index] }
                        .map { it.groupId }
                    if (groupIds.isEmpty()) {
                        context.toastOnUi(R.string.select_group_first)
                    } else {
                        moveGroup(booksAdapter.selection, groupIds, addTo)
                        exitSelectionMode()
                    }
                }
                cancelButton()
            }
        }
    }

    private fun moveGroup(books: List<Book>, groupIds: List<Long>, addTo: Boolean) {
        val mask = groupIds.fold(0L) { acc, id -> acc or id }
        val array = Array(books.size) { index ->
            val book = books[index]
            if (addTo) {
                book.copy(group = book.group or mask)
            } else {
                book.copy(group = book.group and mask.inv())
            }
        }
        viewModel.updateBook(*array)
    }

    override fun isUpdate(bookUrl: String): Boolean {
        return activityViewModel.isUpdate(bookUrl)
    }

    fun getItemCount(): Int {
        return if (groupId == BookGroup.IdRoot) {
            bookGroups.size + books.size
        } else {
            books.size
        }
    }

    override fun getItems(): List<Any> {
        if (groupId != BookGroup.IdRoot) {
            return books
        }
        return bookGroups + books
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.UP_BOOKSHELF) {
            booksAdapter.notification(it)
        }
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) {
            booksAdapter.notifyDataSetChanged()
        }
    }
}
