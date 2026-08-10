package io.legado.app.model.localBook

import io.legado.app.utils.FileDoc

/**
 * 本地漫画导入：预览清单数据
 *
 * 一本书 = 一个直接含图片的文件夹；一个章节 = 书文件夹的直接子文件夹，
 * 若书文件夹直接含图片且没有可读子文件夹，则整本连看（单章节）。
 */
data class MangaBookPreview(
    val dir: FileDoc,
    var name: String,
    var group: String? = null,
    // true = 整本连看（直接图片作为一章）；false = 子文件夹作为章节
    var isWhole: Boolean = false,
    // 文件夹同时有直接图片与可读子文件夹时才允许切换角色
    var canToggleWhole: Boolean = false,
    var enabled: Boolean = true,
    var isDuplicate: Boolean = false,
    var isSkipped: Boolean = false,
    // 子文件夹章节（isWhole = false 时使用）
    val chapterDirs: List<MangaChapterPreview> = emptyList(),
    // 书文件夹直接图片（isWhole = true 时使用）
    val wholeImages: List<String> = emptyList(),
) {

    /** 当前生效的章节列表 */
    val chapters: List<MangaChapterPreview>
        get() = if (isWhole) {
            listOf(MangaChapterPreview(dir = dir, name = name, images = wholeImages))
        } else {
            chapterDirs
        }

    val imageCount: Int
        get() = chapters.sumOf { it.images.size }

    fun toggleRole() {
        if (canToggleWhole) {
            isWhole = !isWhole
        }
    }
}

data class MangaChapterPreview(
    val dir: FileDoc,
    val name: String,
    val images: List<String>,
)
