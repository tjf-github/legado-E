package io.legado.app.ui.book.import.manga

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.ActivityImportMangaBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.model.localBook.MangaBookPreview
import io.legado.app.model.localBook.MangaFolderScanner
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.FileDoc
import io.legado.app.utils.applyTint
import io.legado.app.utils.gone
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 本地漫画导入：选择模式 → 选择文件夹 → 扫描预览 → 确认建书
 */
class ImportMangaActivity :
    VMBaseActivity<ActivityImportMangaBinding, ImportMangaViewModel>(),
    ImportMangaAdapter.CallBack {

    companion object {
        private const val NEW_GROUP_SENTINEL = "\u0000new_group"
    }

    override val binding by viewBinding(ActivityImportMangaBinding::inflate)
    override val viewModel by viewModels<ImportMangaViewModel>()
    private val adapter by lazy { ImportMangaAdapter(this) }
    private var scanMode = MangaFolderScanner.Mode.WHOLE
    private var allPreviews: List<MangaBookPreview> = emptyList()
    private var filterKey: String = ""
    // 显式选择的分组：null=未选（沿用自动推导），""=未分组，其它=分组名
    private var selectedGroupName: String? = null

    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }

    private val selectFolder = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            kotlin.runCatching {
                FileDoc.fromDir(uri)
            }.onSuccess { rootDoc ->
                startScan(rootDoc)
            }.onFailure {
                toastOnUi(R.string.import_manga_scan_failed)
                finish()
            }
        } ?: finish()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        initSearchView()
        binding.tvEmptyMsg.setText(R.string.import_manga_scanning)
        binding.tvConfirm.setOnClickListener {
            confirmImport()
        }
        binding.tvSelectAll.setOnClickListener {
            toggleSelectAll()
        }
        binding.tvGroupName.setOnClickListener {
            chooseGroup()
        }
        viewModel.scanningLiveData.observe(this) { scanning ->
            binding.refreshProgressBar.isAutoLoading = scanning
            if (scanning) {
                binding.tvEmptyMsg.visible()
                binding.tvEmptyMsg.setText(R.string.import_manga_scanning)
            }
        }
        viewModel.scanProgressLiveData.observe(this) { (scanned, total) ->
            binding.tvScanProgress.apply {
                if (total > 0 && scanned < total) {
                    text = getString(R.string.import_manga_progress, scanned, total)
                    visible()
                } else {
                    gone()
                }
            }
        }
        viewModel.previewLiveData.observe(this) { list ->
            allPreviews = list
            updateGroupName()
            applyFilter()
            if (list.isEmpty()) {
                binding.tvEmptyMsg.visible()
                binding.tvEmptyMsg.setText(R.string.import_manga_empty)
            } else {
                binding.tvEmptyMsg.gone()
            }
            upSummary()
        }
        chooseMode()
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.queryHint = getString(R.string.search)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterKey = newText.orEmpty()
                applyFilter()
                return false
            }
        })
    }

    private fun applyFilter() {
        val key = filterKey.trim()
        val filtered = if (key.isEmpty()) {
            allPreviews
        } else {
            allPreviews.filter { it.name.contains(key, ignoreCase = true) }
        }
        adapter.setItems(filtered)
    }

    private fun chooseMode() {
        val modes = arrayListOf(
            SelectItem(getString(R.string.import_manga_mode_whole), 0),
            SelectItem(getString(R.string.import_manga_mode_series), 1),
            SelectItem(getString(R.string.import_manga_mode_album), 2)
        )
        alert(getString(R.string.import_manga)) {
            items(modes) { _, item, _ ->
                when (item.value) {
                    0 -> {
                        scanMode = MangaFolderScanner.Mode.WHOLE
                        selectFolder.launch {
                            title = getString(R.string.import_manga)
                        }
                    }
                    1 -> {
                        scanMode = MangaFolderScanner.Mode.SERIES
                        selectFolder.launch {
                            title = getString(R.string.import_manga)
                        }
                    }
                    else -> {
                        scanAlbums()
                    }
                }
            }
            onCancelled {
                finish()
            }
        }
    }

    /** 相册导入：先申请读取权限，再扫描 MediaStore 相册 */
    private fun scanAlbums() {
        PermissionsCompat.Builder()
            .addPermissions(*Permissions.Group.IMAGES)
            .rationale(R.string.import_manga_album_permission)
            .onGranted {
                viewModel.scanAlbums()
            }
            .onDenied {
                binding.tvEmptyMsg.visible()
                binding.tvEmptyMsg.setText(R.string.import_manga_album_permission_denied)
            }
            .request()
    }

    private fun startScan(rootDoc: FileDoc) {
        viewModel.scan(rootDoc, scanMode)
    }

    private fun confirmImport() {
        // 用户显式选了分组/未分组时，覆盖自动推导的分组；未选则保留原值（重导时保留旧分组）
        selectedGroupName?.let { name ->
            allPreviews.forEach { it.group = name.ifBlank { null } }
        }
        viewModel.import(allPreviews) {
            finish()
        }
    }

    /** 刷新分组显示：优先显式选择，否则显示自动推导的分组名（无则为未分组） */
    private fun updateGroupName() {
        val name = selectedGroupName
            ?: allPreviews.firstOrNull()?.group
        binding.tvGroupName.text = name?.takeIf { it.isNotBlank() }
            ?: getString(R.string.no_group)
    }

    private fun chooseGroup() {
        lifecycleScope.launch {
            val groups = withContext(IO) {
                appDb.bookGroupDao.all.filter { it.groupId >= 0 }
            }
            val items = arrayListOf<SelectItem<String>>(
                SelectItem(getString(R.string.no_group), "")
            )
            groups.forEach { items.add(SelectItem(it.groupName, it.groupName)) }
            items.add(SelectItem(getString(R.string.add_group), NEW_GROUP_SENTINEL))
            alert(getString(R.string.group_select)) {
                items(items) { _, item, _ ->
                    if (item.value == NEW_GROUP_SENTINEL) {
                        showNewGroupDialog()
                    } else {
                        selectedGroupName = item.value
                        updateGroupName()
                    }
                }
            }
        }
    }

    private fun showNewGroupDialog() {
        val editBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = getString(R.string.add_group)
        }
        alert(getString(R.string.add_group)) {
            customView { editBinding.root }
            okButton {
                val name = editBinding.editView.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    selectedGroupName = name
                    updateGroupName()
                }
            }
            cancelButton()
        }
    }

    private fun upSummary() {
        val books = allPreviews.filter { it.enabled && !it.isSkipped }
        val imageCount = books.sumOf { it.imageCount }
        binding.tvSummary.text = getString(R.string.import_manga_summary, books.size, imageCount)
        upSelectAllState()
    }

    private fun upSelectAllState() {
        val items = allPreviews.filter { !it.isSkipped }
        val allChecked = items.isNotEmpty() && items.all { it.enabled }
        binding.tvSelectAll.setText(
            if (allChecked) {
                R.string.import_manga_deselect_all
            } else {
                R.string.import_manga_select_all
            }
        )
    }

    private fun toggleSelectAll() {
        val items = allPreviews.filter { !it.isSkipped }
        val allChecked = items.isNotEmpty() && items.all { it.enabled }
        allPreviews.filter { !it.isSkipped }.forEach { it.enabled = !allChecked }
        adapter.notifyListChanged()
        upSummary()
    }

    override fun onCheckedChanged(preview: MangaBookPreview, checked: Boolean) {
        preview.enabled = checked
        upSummary()
    }

    override fun onRename(preview: MangaBookPreview) {
        val editBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = getString(R.string.import_manga_rename)
            editView.setText(preview.name)
        }
        alert(getString(R.string.import_manga_rename)) {
            customView { editBinding.root }
            okButton {
                val newName = editBinding.editView.text?.toString()?.trim()
                if (!newName.isNullOrEmpty()) {
                    preview.name = newName
                    adapter.notifyListChanged()
                    upSummary()
                }
            }
            cancelButton()
        }
    }

    override fun onToggleRole(preview: MangaBookPreview) {
        preview.toggleRole()
        adapter.notifyListChanged()
        upSummary()
    }
}
