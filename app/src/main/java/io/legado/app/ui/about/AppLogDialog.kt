package io.legado.app.ui.about

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.addTextChangedListener
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppLogEntry
import io.legado.app.constant.AppLogLevel
import io.legado.app.constant.LogTag
import io.legado.app.databinding.DialogAppLogBinding
import io.legado.app.databinding.ItemAppLogBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.FileDoc
import io.legado.app.utils.gone
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import splitties.views.onClick
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppLogDialog : BaseDialogFragment(R.layout.dialog_app_log),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogAppLogBinding::bind)
    private val adapter by lazy { LogAdapter(requireContext()) }
    private val dateFormatter = SimpleDateFormat("yy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var sourceEntries: List<AppLogEntry> = emptyList()
    private var filteredEntries: List<AppLogEntry> = emptyList()
    private var tagOptions: List<String?> = listOf(null)
    private var updatingTagSpinner = false
    private var loadGeneration = 0

    override fun onStart() {
        super.onStart()
        setLayout(0.95f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.run {
            toolBar.setBackgroundColor(primaryColor)
            toolBar.setTitle(R.string.log)
            toolBar.inflateMenu(R.menu.app_log)
            toolBar.setOnMenuItemClickListener(this@AppLogDialog)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
        }
        setupFilters()
        loadSelectedSource()
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_refresh -> loadSelectedSource()
            R.id.menu_copy_log -> copyFilteredLogs()
            R.id.menu_export_log -> exportAllLogs()
            R.id.menu_clear -> clearMemoryLogs()
            else -> return false
        }
        return true
    }

    private fun setupFilters() {
        setSpinnerItems(
            binding.spSource,
            listOf(getString(R.string.log_source_memory), getString(R.string.log_source_file))
        )
        setSpinnerItems(
            binding.spLevel,
            listOf(getString(R.string.log_level_all)) + AppLogLevel.entries.map { it.name }
        )
        updateTagOptions()
        binding.spSource.onSelected { loadSelectedSource() }
        binding.spLevel.onSelected { applyFilter() }
        binding.spTag.onSelected {
            if (!updatingTagSpinner) applyFilter()
        }
        binding.etKeyword.addTextChangedListener { applyFilter() }
    }

    private fun loadSelectedSource() {
        val generation = ++loadGeneration
        binding.rotateLoading.gone()
        when (selectedSource()) {
            AppLogSource.MEMORY -> {
                sourceEntries = AppLog.entries
                updateTagOptions()
                applyFilter()
            }

            AppLogSource.FILE -> loadFileEntries(generation)
        }
    }

    private fun loadFileEntries(generation: Int) {
        if (!AppLog.isEnabled) {
            requireContext().toastOnUi(R.string.log_recording_disabled)
        }
        val logFolder = requireContext().externalCacheDir?.let { File(it, "logs") }
        binding.rotateLoading.visible()
        binding.tvMsg.gone()
        viewLifecycleOwner.lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                logFolder?.let(AppLogFileReader::read).orEmpty()
            }
            if (generation != loadGeneration || selectedSource() != AppLogSource.FILE) {
                return@launch
            }
            sourceEntries = entries
            binding.rotateLoading.gone()
            updateTagOptions()
            applyFilter()
        }
    }

    private fun updateTagOptions() {
        val selectedTag = tagOptions.getOrNull(binding.spTag.selectedItemPosition)
        tagOptions = listOf(null) + sourceEntries.map(AppLogEntry::tag).distinct().sorted()
        val labels = tagOptions.map { it ?: getString(R.string.log_tag_all) }
        updatingTagSpinner = true
        setSpinnerItems(binding.spTag, labels)
        binding.spTag.setSelection(tagOptions.indexOf(selectedTag).coerceAtLeast(0))
        updatingTagSpinner = false
    }

    private fun applyFilter() {
        val level = binding.spLevel.selectedItemPosition
            .takeIf { it > 0 }
            ?.let { AppLogLevel.entries[it - 1] }
        val tag = tagOptions.getOrNull(binding.spTag.selectedItemPosition)
        filteredEntries = filterLogEntries(
            sourceEntries,
            AppLogFilter(level, tag, binding.etKeyword.text?.toString().orEmpty())
        )
        adapter.setItems(filteredEntries)
        binding.tvMsg.text = getString(R.string.log_no_results)
        binding.tvMsg.visible(filteredEntries.isEmpty())
    }

    private fun copyFilteredLogs() {
        if (filteredEntries.isEmpty()) {
            requireContext().toastOnUi(R.string.log_no_results)
            return
        }
        requireContext().sendToClip(formatLogEntries(filteredEntries))
    }

    private fun clearMemoryLogs() {
        AppLog.clear()
        if (selectedSource() == AppLogSource.MEMORY) {
            loadSelectedSource()
        }
        requireContext().toastOnUi(R.string.log_memory_cleared)
    }

    private fun exportAllLogs() {
        Coroutine.async {
            val backupPath = AppConfig.backupPath ?: let {
                appCtx.toastOnUi(R.string.log_backup_not_set)
                return@async
            }
            LogExport.copyLogs(FileDoc.fromUri(backupPath.toUri(), true))
            appCtx.toastOnUi(R.string.log_exported)
        }.onError { error ->
            AppLog.e(LogTag.APP_LOG, "导出全部日志失败", error)
            val detail = error.localizedMessage ?: error::class.java.simpleName
            appCtx.toastOnUi(appCtx.getString(R.string.log_export_failed, detail))
        }
    }

    private fun selectedSource(): AppLogSource {
        return AppLogSource.entries[binding.spSource.selectedItemPosition.coerceAtLeast(0)]
    }

    private fun setSpinnerItems(spinner: AppCompatSpinner, items: List<String>) {
        spinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            items
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun AppCompatSpinner.onSelected(block: () -> Unit) {
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) = block()

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    inner class LogAdapter(context: Context) :
        RecyclerAdapter<AppLogEntry, ItemAppLogBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemAppLogBinding {
            return ItemAppLogBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemAppLogBinding,
            item: AppLogEntry,
            payloads: MutableList<Any>
        ) {
            binding.textTime.text = buildString {
                append(dateFormatter.format(Date(item.time)))
                append(" [").append(item.level.name).append("] ")
                append(item.tag)
            }
            binding.textMessage.text = item.message
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemAppLogBinding) {
            binding.root.onClick {
                getItem(holder.layoutPosition)?.let { item ->
                    showDialogFragment(
                        TextDialog(getString(R.string.log), formatLogEntries(listOf(item)))
                    )
                }
            }
        }
    }
}
