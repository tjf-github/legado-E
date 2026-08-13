package io.legado.app.ui.book.toc

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.PorterDuff
import android.text.TextUtils
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.databinding.FragmentChapterListBinding
import io.legado.app.databinding.DialogChapterInsertBinding
import io.legado.app.help.book.ChapterNumberUtils
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ChapterSplitter
import io.legado.app.help.book.ChapterSplitter.RuleType
import io.legado.app.help.book.ChapterSplitter.SplitUnit
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.isVideo
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.ui.widget.recycler.UpLinearLayoutManager
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.observeEvent
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChapterListFragment : VMBaseFragment<TocViewModel>(R.layout.fragment_chapter_list),
    ChapterListAdapter.Callback,
    TocViewModel.ChapterListCallBack {
    override val viewModel by activityViewModels<TocViewModel>()
    private val binding by viewBinding(FragmentChapterListBinding::bind)
    private val mLayoutManager by lazy { UpLinearLayoutManager(requireContext()) }
    private val adapter by lazy { ChapterListAdapter(requireContext(), this) }
    private var durChapterIndex = 0

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        viewModel.chapterListCallBack = this@ChapterListFragment
        val bbg = bottomBackground
        val btc = requireContext().getPrimaryTextColor(ColorUtils.isColorLight(bbg))
        llChapterBaseInfo.setBackgroundColor(bbg)
        tvCurrentChapterInfo.setTextColor(btc)
        ivChapterTop.setColorFilter(btc, PorterDuff.Mode.SRC_IN)
        ivChapterBottom.setColorFilter(btc, PorterDuff.Mode.SRC_IN)
        initRecyclerView()
        initView()
        viewModel.bookData.observe(this@ChapterListFragment) {
            initBook(it)
        }
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = mLayoutManager
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
    }

    private fun initView() = binding.run {
        ivChapterTop.setOnClickListener {
            mLayoutManager.scrollToPositionWithOffset(0, 0)
        }
        ivChapterBottom.setOnClickListener {
            if (adapter.itemCount > 0) {
                mLayoutManager.scrollToPositionWithOffset(adapter.itemCount - 1, 0)
            }
        }
        tvCurrentChapterInfo.setOnClickListener {
            mLayoutManager.scrollToPositionWithOffset(durChapterIndex, 0)
        }
        binding.llChapterBaseInfo.applyNavigationBarPadding()
    }

    @SuppressLint("SetTextI18n")
    private fun initBook(book: Book) {
        lifecycleScope.launch {
            upChapterList(null)
            durChapterIndex = book.durChapterIndex
            binding.tvCurrentChapterInfo.text =
                "${book.durChapterTitle}(${book.durChapterIndex + 1}/${book.simulatedTotalChapterNum()})"
            initCacheFileNames(book)
        }
    }

    private fun initCacheFileNames(book: Book) {
        lifecycleScope.launch(IO) {
            adapter.cacheFileNames.addAll(BookHelp.getChapterFiles(book))
            withContext(Main) {
                adapter.notifyItemRangeChanged(0, adapter.itemCount, true)
            }
        }
    }

    override fun observeLiveBus() {
        observeEvent<Pair<Book, BookChapter>>(EventBus.SAVE_CONTENT) { (book, chapter) ->
            viewModel.bookData.value?.bookUrl?.let { bookUrl ->
                if (book.bookUrl == bookUrl) {
                    adapter.cacheFileNames.add(chapter.getFileName())
                    if (viewModel.searchKey.isNullOrEmpty()) {
                        adapter.notifyItemChanged(chapter.index, true)
                    } else {
                        adapter.getItems().forEachIndexed { index, bookChapter ->
                            if (bookChapter.index == chapter.index) {
                                adapter.notifyItemChanged(index, true)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun upChapterList(searchKey: String?) {
        lifecycleScope.launch {
            withContext(IO) {
                val end = (book?.simulatedTotalChapterNum() ?: Int.MAX_VALUE) - 1
                when {
                    searchKey.isNullOrBlank() ->
                        appDb.bookChapterDao.getChapterList(viewModel.bookUrl, 0, end).also {
                            chapterList = it
                        }

                    else -> appDb.bookChapterDao.search(viewModel.bookUrl, searchKey, 0, end)
                }
            }.let {
                adapter.setItems(it)
            }
        }
    }

    override fun onListChanged() {
        lifecycleScope.launch {
            var scrollPos = 0
            withContext(Default) {
                adapter.getItems().forEachIndexed { index, bookChapter ->
                    if (bookChapter.index >= durChapterIndex) {
                        return@withContext
                    }
                    scrollPos = index
                }
            }
            mLayoutManager.scrollToPositionWithOffset(scrollPos, 0)
            adapter.upDisplayTitles(scrollPos)
        }
    }

    override fun clearDisplayTitle() {
        adapter.clearDisplayTitle()
        adapter.upDisplayTitles(mLayoutManager.findFirstVisibleItemPosition())
    }

    override fun upAdapter() {
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    override val scope: CoroutineScope
        get() = lifecycleScope

    override val book: Book?
        get() = viewModel.bookData.value

    override val isLocalBook: Boolean
        get() = viewModel.bookData.value?.isLocal == true

    override fun durChapterIndex(): Int {
        return durChapterIndex
    }
    private var chapterList: List<BookChapter>? = null

    override fun openChapter(bookChapter: BookChapter) {
        activity?.run {
            if (book?.isVideo == true) {
                val volumes = arrayListOf<BookChapter>()
                chapterList?.forEach { chapter ->
                    if (chapter.isVolume) {
                        volumes.add(chapter)
                    }
                }
                var chapterInVolumeIndex = 0
                var durVolumeIndex = 0
                if (volumes.isNotEmpty()) {
                    for ((index, volume) in volumes.reversed().withIndex()) {
                        val first = bookChapter.index
                        if (volume.index < first) {
                            chapterInVolumeIndex = first - volume.index - 1
                            durVolumeIndex = volumes.size - index - 1
                            break
                        } else if (volume.index == first) {
                            chapterInVolumeIndex = 0
                            durVolumeIndex = volumes.size - index - 1
                            break
                        }
                    }
                } else {
                    chapterInVolumeIndex = bookChapter.index
                }
                setResult(
                    RESULT_OK, Intent()
                        .putExtra("index", bookChapter.index)
                        .putExtra("chapterChanged", bookChapter.index != durChapterIndex)
                        .putExtra("durVolumeIndex", durVolumeIndex)
                        .putExtra("chapterInVolumeIndex", chapterInVolumeIndex)
                )
                finish()
                return@run
            }
            setResult(
                RESULT_OK, Intent()
                    .putExtra("index", bookChapter.index)
                    .putExtra("chapterChanged", bookChapter.index != durChapterIndex)
            )
            finish()
        }
    }

    override fun onChapterMenu(bookChapter: BookChapter) {
        val book = viewModel.bookData.value ?: return
        if (!book.isLocalTxt) return
        val menuItems = mutableListOf(
            getString(R.string.add_chapter_after),
            getString(R.string.delete_chapter),
            getString(R.string.split_chapter),
            getString(R.string.merge_chapter)
        )
        if (viewModel.hasLastSplitUndo(book.bookUrl)) {
            menuItems.add(getString(R.string.undo_split_chapter))
        }
        requireContext().selector(
            R.string.chapter_menu_title,
            menuItems
        ) { _, index ->
            when (index) {
                0 -> showInsertChapterDialog(book, bookChapter)
                1 -> showDeleteChapterDialog(book, bookChapter)
                2 -> showSplitPreviewDialog(book, bookChapter)
                3 -> showMergeChaptersDialog(book, bookChapter)
                4 -> viewModel.undoLastSplit(book)
            }
        }
    }

    private fun showInsertChapterDialog(book: Book, anchor: BookChapter) {
        val defaultTitle = ChapterNumberUtils.nextNumberTitle(anchor.title, 1)
            ?: getString(R.string.chapter_default_title, anchor.index + 1)
        alert {
            setTitle(R.string.add_chapter_title)
            val alertBinding = DialogChapterInsertBinding.inflate(layoutInflater)
            alertBinding.editTitle.setText(defaultTitle)
            setCustomView(alertBinding.root)
            okButton {
                val title = alertBinding.editTitle.text?.toString() ?: ""
                val content = alertBinding.editContent.text?.toString() ?: ""
                viewModel.insertChapter(book, anchor, title, content)
            }
            cancelButton()
        }
    }

    private fun showDeleteChapterDialog(book: Book, chapter: BookChapter) {
        alert {
            setTitle(R.string.delete_chapter)
            setMessage(getString(R.string.delete_chapter_confirm, chapter.title))
            okButton {
                viewModel.deleteChapter(book, chapter)
            }
            cancelButton()
        }
    }

    private fun showSplitPreviewDialog(book: Book, chapter: BookChapter) {
        viewModel.previewSplit(book, chapter) { units ->
            showSplitUnitsDialog(book, chapter, units)
        }
    }

    private fun showMergeChaptersDialog(book: Book, anchor: BookChapter) {
        lifecycleScope.launch {
            val toc = withContext(IO) {
                appDb.bookChapterDao.getChapterList(book.bookUrl)
            }
            val context = requireContext()
            val checked = MutableList(toc.size) { it == anchor.index }
            val dark = ColorUtils.isColorLight(bottomBackground)
            val primaryColor = context.getPrimaryTextColor(dark)
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12.dpToPx(), 8.dpToPx(), 12.dpToPx(), 8.dpToPx())
                addView(
                    EditText(context).apply {
                        hint = getString(R.string.merge_chapter_title_hint)
                        setText(anchor.title)
                        selectAll()
                    }
                )
            }
            toc.forEachIndexed { index, chapter ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                }
                val checkBox = CheckBox(context).apply {
                    isChecked = index == anchor.index
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
                        text = chapter.title
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
            alert {
                setTitle(R.string.merge_chapter_select)
                setCustomView(scroll)
                positiveButton(R.string.merge_chapter_confirm) {
                    val mergedTitle =
                        (container.getChildAt(0) as EditText).text?.toString()?.trim().orEmpty()
                    val selected = toc.filterIndexed { index, _ -> checked[index] }
                    if (selected.size < 2) {
                        context.toastOnUi(R.string.merge_chapter_need_two)
                    } else {
                        viewModel.mergeChapters(book, selected, mergedTitle.ifBlank { anchor.title })
                    }
                }
                cancelButton()
            }
        }
    }

    private fun showSplitUnitsDialog(book: Book, chapter: BookChapter, units: List<SplitUnit>) {
        val context = requireContext()
        val titles = units.map { it.title }.toMutableList()
        val checked = MutableList(units.size) { true }
        val dark = ColorUtils.isColorLight(bottomBackground)
        val primaryColor = context.getPrimaryTextColor(dark)
        val secondaryColor = context.getSecondaryTextColor(dark)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dpToPx(), 8.dpToPx(), 12.dpToPx(), 8.dpToPx())
            addView(
                TextView(context).apply {
                    text = getString(R.string.split_chapter_rule_hint) + "\n" +
                        getString(R.string.split_chapter_preview_count, units.size)
                    textSize = 13f
                    setTextColor(secondaryColor)
                    setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 8.dpToPx())
                }
            )
        }
        units.forEachIndexed { index, unit ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4.dpToPx(), 6.dpToPx(), 4.dpToPx(), 6.dpToPx())
            }
            val checkBox = CheckBox(context).apply {
                isChecked = true
                isEnabled = index > 0
                setOnCheckedChangeListener { _, isChecked -> checked[index] = isChecked }
            }
            row.addView(
                checkBox,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            val textColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            val titleView = TextView(context).apply {
                text = unit.title
                textSize = 15f
                setTextColor(primaryColor)
            }
            titleView.setOnClickListener {
                alert {
                    setTitle(R.string.split_chapter_rename)
                    val editText = EditText(context).apply {
                        setText(titles[index])
                        selectAll()
                    }
                    setCustomView(editText)
                    okButton {
                        val newTitle = editText.text?.toString()?.trim().orEmpty()
                        if (newTitle.isNotEmpty()) {
                            titles[index] = newTitle
                            titleView.text = newTitle
                        }
                    }
                    cancelButton()
                }
            }
            val detailView = TextView(context).apply {
                val firstLine = unit.content.lineSequence().firstOrNull().orEmpty()
                val ruleText = when (unit.ruleType) {
                    RuleType.CN_NUMBER -> getString(R.string.split_chapter_rule_cn)
                    RuleType.CHAPTER_EN -> getString(R.string.split_chapter_rule_en)
                }
                val ruleLabel = if (unit.wrapped) {
                    "$ruleText · ${getString(R.string.split_chapter_rule_wrapped)}"
                } else {
                    ruleText
                }
                text = "$ruleLabel · " +
                    getString(R.string.split_chapter_first_line, firstLine) +
                    " · " + getString(R.string.split_chapter_chars, unit.content.length)
                textSize = 12f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(secondaryColor)
            }
            textColumn.addView(titleView)
            textColumn.addView(detailView)
            row.addView(
                textColumn,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            container.addView(row)
        }
        val scroll = ScrollView(context).apply {
            addView(container)
        }
        alert {
            setTitle(R.string.split_chapter_preview)
            setCustomView(scroll)
            okButton {
                val finalUnits = units.mapIndexedNotNull { index, unit ->
                    if (checked[index]) {
                        SplitUnit(titles[index], unit.content, unit.ruleType, unit.wrapped)
                    } else null
                }
                if (finalUnits.size < 2) {
                    context.toastOnUi(R.string.split_chapter_single)
                } else {
                    viewModel.splitChapter(book, chapter, finalUnits)
                }
            }
            cancelButton()
        }
    }

}
