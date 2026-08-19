package io.legado.app.ui.main.bookshelf.style1.books

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewConfiguration
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isGone
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBooksBinding
import io.legado.app.help.book.sortByBookshelf
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.main.bookshelf.BookshelfViewModel
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.utils.dpToPx
import io.legado.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.legado.app.utils.gone
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setEdgeEffectColor
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 书架界面
 */
class BooksFragment() : BaseFragment(R.layout.fragment_books),
    BaseBooksAdapter.CallBack,
    SelectActionBar.CallBack,
    PopupMenu.OnMenuItemClickListener {

    constructor(position: Int, group: BookGroup) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        bundle.putLong("groupId", group.groupId)
        bundle.putInt("bookSort", group.getRealBookSort())
        bundle.putBoolean("enableRefresh", group.enableRefresh)
        bundle.putBoolean("onlyUpdateRead", group.onlyUpdateRead)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBooksBinding::bind)
    private val activityViewModel by activityViewModels<MainViewModel>()
    private val viewModel by viewModels<BookshelfViewModel>()
    private val addToGroupRequestCode = 34
    private val removeToGroupRequestCode = 42
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            exitSelectionMode()
        }
    }
    private val bookshelfLayout by lazy { AppConfig.bookshelfLayout }
    private val booksAdapter: BaseBooksAdapter<*> by lazy {
        when (bookshelfLayout) {
            0 -> {
                BooksAdapterList(requireContext(), this, this, viewLifecycleOwner.lifecycle)
            }
            1 -> {
                BooksAdapterList2(requireContext(), this, this, viewLifecycleOwner.lifecycle)
            }
            else -> {
                BooksAdapterGrid(requireContext(), this)
            }
        }
    }
    private var booksFlowJob: Job? = null
    var position = 0
        private set
    var groupId = -1L
        private set
    var bookSort = 0
        private set
    private var upLastUpdateTimeJob: Job? = null
    private var enableRefresh = true
    private var onlyUpdateRead = false
    private val bookshelfMargin by lazy { AppConfig.bookshelfMargin }
    private var itemCount = 0
    private var totalRows = 0

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        arguments?.let {
            position = it.getInt("position", 0)
            groupId = it.getLong("groupId", -1)
            bookSort = it.getInt("bookSort", 0)
            enableRefresh = it.getBoolean("enableRefresh", true)
            onlyUpdateRead = it.getBoolean("onlyUpdateRead", false)
            binding.refreshLayout.isEnabled = enableRefresh
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        initSelectActionBar()
        initRecyclerView()
        upRecyclerData()
    }

    /**
     * 多选模式：长按进入、底部操作栏全选/反选/删除/移组，back 或「完成」退出
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
            enableRefresh && booksAdapter.itemCount > 0 && !inSelection
        backCallback.isEnabled = inSelection
        upSelectCount()
    }

    private fun upSelectCount() {
        binding.selectActionBar.upCountView(booksAdapter.selection.size, booksAdapter.itemCount)
    }

    override fun onPause() {
        super.onPause()
        // 切 tab / 退后台时退出多选模式，避免状态残留
        if (booksAdapter.isSelectionMode) {
            exitSelectionMode()
        }
    }

    private fun initRecyclerView() {
        binding.rvBookshelf.setEdgeEffectColor(primaryColor)
        upFastScrollerBar()
        binding.refreshLayout.setColorSchemeColors(accentColor)
        binding.refreshLayout.setOnRefreshListener {
            binding.refreshLayout.isRefreshing = false
            activityViewModel.upToc(booksAdapter.getItems(), onlyUpdateRead)
        }
        if (bookshelfLayout >= 2) {
            binding.rvBookshelf.layoutManager = GridLayoutManager(context, bookshelfLayout)
            binding.rvBookshelf.setRecycledViewPool(activityViewModel.booksGridRecycledViewPool)
        } else {
            binding.rvBookshelf.layoutManager = LinearLayoutManager(context)
            binding.rvBookshelf.setRecycledViewPool(activityViewModel.booksListRecycledViewPool)
        }
        booksAdapter.stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        binding.rvBookshelf.adapter = booksAdapter
        /**
         * 应该是当初没有使用override val keepScrollPosition = true 加的代码
         * 最近阅读插入顶部时会造成滚动
         * 但是采用keepScrollPosition = true复原滚动后,代码就多余了
         * 采用下面代码反而会向上多滚动一个行
         * 再加上2025/12/19代码,因为下面的代码会出现很奇怪的自动滚动到顶部现象,没理出原因,注释掉下面代码
         * **/
//        booksAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
//            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
//                val layoutManager = binding.rvBookshelf.layoutManager
//                if (positionStart == 0 && itemCount == 1 && layoutManager is LinearLayoutManager) {
//                    val scrollTo = layoutManager.findFirstVisibleItemPosition() - itemCount
//                    binding.rvBookshelf.scrollToPosition(max(0, scrollTo))
//                }
//            }
//
//            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
//                val layoutManager = binding.rvBookshelf.layoutManager
//                if (toPosition == 0 && itemCount == 1 && layoutManager is LinearLayoutManager) {
//                    val scrollTo = layoutManager.findFirstVisibleItemPosition() - itemCount
//                    binding.rvBookshelf.scrollToPosition(max(0, scrollTo))
//                }
//            }
//        })
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
        startLastUpdateTimeJob()
    }

    private fun upFastScrollerBar() {
        val showBookshelfFastScroller = AppConfig.showBookshelfFastScroller
        binding.rvBookshelf.setFastScrollEnabled(showBookshelfFastScroller)
        if (showBookshelfFastScroller) {
            binding.rvBookshelf.scrollBarSize = 0
        } else {
            binding.rvBookshelf.scrollBarSize =
                ViewConfiguration.get(requireContext()).scaledScrollBarSize
        }
    }

    fun upBookSort(sort: Int) {
        binding.root.post {
            arguments?.putInt("bookSort", sort)
            bookSort = sort
            upRecyclerData()
        }
    }

    fun setEnableRefresh(enable: Boolean) {
        enableRefresh = enable
        binding.refreshLayout.isEnabled = enable
    }

    /**
     * 更新书籍列表信息
     */
    private fun upRecyclerData() {
        booksFlowJob?.cancel()
        booksFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowByGroup(groupId).map { list ->
                //排序：六种排序 + 逆序
                list.sortByBookshelf(
                    bookSort,
                    reverse = AppConfig.bookshelfSortReverse
                )
            }.flowWithLifecycleAndDatabaseChangeFirst(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.BOOK_TABLE_NAME
            ).catch {
                AppLog.put("书架更新出错", it)
            }.conflate().flowOn(Dispatchers.Default).collect { list ->
                itemCount = list.size
                val spanCount = bookshelfLayout
                if (spanCount >= 2) {
                    totalRows = if (itemCount % spanCount == 0) itemCount / spanCount else itemCount / spanCount + 1
                }
                binding.tvEmptyMsg.isGone = itemCount > 0
                binding.refreshLayout.isEnabled =
                    enableRefresh && itemCount > 0 && !booksAdapter.isSelectionMode
                booksAdapter.setItems(list)
                delay(100)
            }
        }
    }

    private fun startLastUpdateTimeJob() {
        upLastUpdateTimeJob?.cancel()
        if (!AppConfig.showLastUpdateTime || bookshelfLayout >= 2) {
            return
        }
        upLastUpdateTimeJob = viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    booksAdapter.upLastUpdateTime()
                    delay(30 * 1000)
                }
            }
        }
    }

    fun getBooks(): List<Book> {
        return booksAdapter.getItems()
    }

    fun gotoTop() {
        if (AppConfig.isEInkMode) {
            binding.rvBookshelf.scrollToPosition(0)
        } else {
            binding.rvBookshelf.smoothScrollToPosition(0)
        }
    }

    fun getBooksCount(): Int {
        return booksAdapter.itemCount
    }

    override fun onDestroyView() {
        super.onDestroyView()
        /**
         * 将 RecyclerView 中的视图全部回收到 RecycledViewPool 中
         */
        binding.rvBookshelf.setItemViewCacheSize(0)
        binding.rvBookshelf.adapter = null
    }

    override fun open(book: Book) {
        startActivityForBook(book)
    }

    override fun openBookInfo(book: Book) {
        startActivity<BookInfoActivity> {
            putExtra("name", book.name)
            putExtra("author", book.author)
        }
    }

    override fun onSelectionChanged() {
        upSelectCount()
    }

    override fun onLongPressBook(book: Book) {
        enterSelectionMode(book)
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

    @SuppressLint("NotifyDataSetChanged")
    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.UP_BOOKSHELF) {
            booksAdapter.notification(it)
        }
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) {
            booksAdapter.notifyDataSetChanged()
            startLastUpdateTimeJob()
            upFastScrollerBar()
        }
    }
}
