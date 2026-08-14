package io.legado.app.ui.book.import.manga

import android.os.Bundle
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityImportMangaBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.model.localBook.MangaBookPreview
import io.legado.app.model.localBook.MangaFolderScanner
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.FileDoc
import io.legado.app.utils.gone
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 本地漫画导入：选择模式 → 选择文件夹 → 扫描预览 → 确认建书
 */
class ImportMangaActivity :
    VMBaseActivity<ActivityImportMangaBinding, ImportMangaViewModel>(),
    ImportMangaAdapter.CallBack {

    override val binding by viewBinding(ActivityImportMangaBinding::inflate)
    override val viewModel by viewModels<ImportMangaViewModel>()
    private val adapter by lazy { ImportMangaAdapter(this) }
    private var scanMode = MangaFolderScanner.Mode.WHOLE
    private var isAlbumMode = false

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
        binding.tvEmptyMsg.setText(R.string.import_manga_scanning)
        binding.tvConfirm.setOnClickListener {
            confirmImport()
        }
        binding.tvSelectAll.setOnClickListener {
            toggleSelectAll()
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
            adapter.setItems(list)
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
                        isAlbumMode = false
                        scanMode = MangaFolderScanner.Mode.WHOLE
                        selectFolder.launch {
                            title = getString(R.string.import_manga)
                        }
                    }
                    1 -> {
                        isAlbumMode = false
                        scanMode = MangaFolderScanner.Mode.SERIES
                        selectFolder.launch {
                            title = getString(R.string.import_manga)
                        }
                    }
                    else -> {
                        isAlbumMode = true
                        binding.llAlbumSeries.visible()
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
        if (isAlbumMode) {
            val seriesName = binding.etSeriesName.text?.toString()?.trim().orEmpty()
            if (seriesName.isNotEmpty()) {
                adapter.getItems().forEach { it.group = seriesName }
            }
        }
        viewModel.import(adapter.getItems()) {
            finish()
        }
    }

    private fun upSummary() {
        val books = adapter.getItems().filter { it.enabled && !it.isSkipped }
        val imageCount = books.sumOf { it.imageCount }
        binding.tvSummary.text = getString(R.string.import_manga_summary, books.size, imageCount)
        upSelectAllState()
    }

    private fun upSelectAllState() {
        val items = adapter.getItems().filter { !it.isSkipped }
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
        val items = adapter.getItems().filter { !it.isSkipped }
        val allChecked = items.isNotEmpty() && items.all { it.enabled }
        adapter.setAllEnabled(!allChecked)
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
