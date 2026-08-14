package io.legado.app.ui.book.import.manga

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.databinding.ItemImportMangaBinding
import io.legado.app.model.localBook.MangaBookPreview
import io.legado.app.utils.gone
import io.legado.app.utils.visible

class ImportMangaAdapter(
    private val callBack: CallBack
) : RecyclerView.Adapter<ImportMangaAdapter.ViewHolder>() {

    interface CallBack {
        fun onCheckedChanged(preview: MangaBookPreview, checked: Boolean)

        fun onRename(preview: MangaBookPreview)

        fun onToggleRole(preview: MangaBookPreview)
    }

    private val items = arrayListOf<MangaBookPreview>()

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(list: List<MangaBookPreview>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun notifyListChanged() {
        notifyDataSetChanged()
    }

    fun getItems(): List<MangaBookPreview> = items

    @SuppressLint("NotifyDataSetChanged")
    fun setAllEnabled(enabled: Boolean) {
        items.forEach {
            if (!it.isSkipped) {
                it.enabled = enabled
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemImportMangaBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemImportMangaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(preview: MangaBookPreview) {
            val context = binding.root.context
            binding.cbEnabled.isChecked = preview.enabled
            binding.cbEnabled.isEnabled = !preview.isSkipped
            binding.tvName.text = preview.name
            binding.tvInfo.text = if (preview.isSkipped) {
                context.getString(R.string.import_manga_skipped)
            } else {
                context.getString(R.string.import_manga_chapters, preview.chapters.size) +
                    " · " +
                    context.getString(R.string.import_manga_images, preview.imageCount)
            }
            binding.tvDuplicate.apply {
                if (preview.isDuplicate) visible() else gone()
            }
            binding.tvRole.apply {
                if (preview.canToggleWhole) {
                    visible()
                    text = context.getString(
                        if (preview.isWhole) {
                            R.string.import_manga_role_whole
                        } else {
                            R.string.import_manga_role_chapters
                        }
                    )
                } else {
                    gone()
                }
            }
            binding.cbEnabled.setOnClickListener {
                callBack.onCheckedChanged(preview, binding.cbEnabled.isChecked)
            }
            binding.tvRole.setOnClickListener {
                callBack.onToggleRole(preview)
            }
            binding.ivRename.setOnClickListener {
                callBack.onRename(preview)
            }
        }
    }
}
