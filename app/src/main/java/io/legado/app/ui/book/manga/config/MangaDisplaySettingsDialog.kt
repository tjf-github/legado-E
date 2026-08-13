package io.legado.app.ui.book.manga.config

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogMangaDisplaySettingsBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadManga
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 统一漫画阅读显示设置面板：阅读方向/图片间距/动画/灰度/墨水屏/标题/缩放等，
 * 替代菜单中的分散开关项
 */
class MangaDisplaySettingsDialog : BaseDialogFragment(R.layout.dialog_manga_display_settings) {
    private val binding by viewBinding(DialogMangaDisplaySettingsBinding::bind)
    private val callback get() = activity as? Callback

    override fun onStart() {
        super.onStart()
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initData()
        initView()
    }

    private fun initData() {
        binding.rgDirection.check(
            if (AppConfig.enableMangaHorizontalScroll) R.id.rb_horizontal else R.id.rb_vertical
        )
        binding.cbSnap.isChecked = AppConfig.disableHorizontalPageSnap
        binding.cbSnap.isVisible = AppConfig.enableMangaHorizontalScroll
        binding.rgGap.check(
            when (ReadManga.book?.getImageGap() ?: 0) {
                1 -> R.id.rb_gap_small
                2 -> R.id.rb_gap_medium
                3 -> R.id.rb_gap_large
                else -> R.id.rb_gap_none
            }
        )
        binding.cbPageAnim.isChecked = !AppConfig.disableMangaPageAnim
        binding.cbGray.isChecked = AppConfig.enableMangaGray
        binding.cbEpaper.isChecked = AppConfig.enableMangaEInk
        binding.dsbEpaper.progress = AppConfig.mangaEInkThreshold
        binding.dsbEpaper.isVisible = AppConfig.enableMangaEInk
        binding.cbHideTitle.isChecked = AppConfig.hideMangaTitle
        binding.cbDisableScale.isChecked = AppConfig.disableMangaScale
        binding.cbDisableClickScroll.isChecked = AppConfig.disableClickScroll
    }

    private fun initView() {
        binding.rgDirection.setOnCheckedChangeListener { _, checkedId ->
            AppConfig.enableMangaHorizontalScroll = checkedId == R.id.rb_horizontal
            binding.cbSnap.isVisible = AppConfig.enableMangaHorizontalScroll
            callback?.updateDisplaySettings()
        }
        binding.cbSnap.setOnCheckedChangeListener { _, checked ->
            AppConfig.disableHorizontalPageSnap = checked
            callback?.updateDisplaySettings()
        }
        binding.rgGap.setOnCheckedChangeListener { _, checkedId ->
            ReadManga.book?.let { book ->
                book.setImageGap(
                    when (checkedId) {
                        R.id.rb_gap_small -> 1
                        R.id.rb_gap_medium -> 2
                        R.id.rb_gap_large -> 3
                        else -> 0
                    }
                )
                book.save()
            }
            callback?.updateDisplaySettings()
        }
        binding.cbPageAnim.setOnCheckedChangeListener { _, checked ->
            AppConfig.disableMangaPageAnim = !checked
            callback?.updateDisplaySettings()
        }
        binding.cbGray.setOnCheckedChangeListener { _, checked ->
            AppConfig.enableMangaGray = checked
            if (checked) {
                AppConfig.enableMangaEInk = false
                binding.cbEpaper.isChecked = false
                binding.dsbEpaper.isVisible = false
            }
            callback?.updateDisplaySettings()
        }
        binding.cbEpaper.setOnCheckedChangeListener { _, checked ->
            AppConfig.enableMangaEInk = checked
            binding.dsbEpaper.isVisible = checked
            if (checked) {
                AppConfig.enableMangaGray = false
                binding.cbGray.isChecked = false
            }
            callback?.updateDisplaySettings()
        }
        binding.dsbEpaper.onChanged = {
            AppConfig.mangaEInkThreshold = it
            callback?.updateDisplaySettings()
        }
        binding.cbHideTitle.setOnCheckedChangeListener { _, checked ->
            AppConfig.hideMangaTitle = checked
            callback?.updateDisplaySettings()
        }
        binding.cbDisableScale.setOnCheckedChangeListener { _, checked ->
            AppConfig.disableMangaScale = checked
            callback?.updateDisplaySettings()
        }
        binding.cbDisableClickScroll.setOnCheckedChangeListener { _, checked ->
            AppConfig.disableClickScroll = checked
            callback?.updateDisplaySettings()
        }
    }

    interface Callback {
        fun updateDisplaySettings()
    }
}
