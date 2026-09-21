package io.legado.app.help.update

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.exception.NoStackTraceException
import java.time.Instant

data class AppReleaseInfo(
    val appVariant: AppVariant,
    val createdAt: Long,
    val note: String,
    val name: String,
    val downloadUrl: String,
    val assetUrl: String
) {
    val versionName: String = name.split("_").getOrNull(2)?.dropLast(2) ?: ""
}

/**
 * 更新检查目标 = 安装/资产身份（[ReleaseIdentity]）× 发布通道（beta / 正式）。
 *
 * 两个轴必须保持正交：身份决定“装的是哪一个包”，通道决定“从 beta 标签还是正式 Release 取”。
 * 压成一个枚举会让同一次发布里的 normal/private 资产失去区分度，导致任一方拿到对方的 APK。
 */
enum class AppVariant(val identity: ReleaseIdentity, private val betaChannel: Boolean) {

    NORMAL_BETA(ReleaseIdentity.NORMAL, true),
    NORMAL_OFFICIAL(ReleaseIdentity.NORMAL, false),
    PRIVATE_BETA(ReleaseIdentity.PRIVATE, true),
    PRIVATE_OFFICIAL(ReleaseIdentity.PRIVATE, false),

    /** 历史 releaseS/releaseA 资产，保留可识别性但不再发布，也不与 normal/private 混同。 */
    LEGACY_RELEASES_BETA(ReleaseIdentity.LEGACY_RELEASES, true),
    LEGACY_RELEASES_OFFICIAL(ReleaseIdentity.LEGACY_RELEASES, false),

    UNKNOWN_BETA(ReleaseIdentity.UNKNOWN, true),
    UNKNOWN_OFFICIAL(ReleaseIdentity.UNKNOWN, false);

    fun isBeta(): Boolean = betaChannel

    companion object {
        /** 两个轴全覆盖，取不到就是编程错误，不做静默回退。 */
        fun of(identity: ReleaseIdentity, isBetaChannel: Boolean): AppVariant =
            entries.first { it.identity == identity && it.betaChannel == isBetaChannel }
    }
}

@Keep
data class GithubRelease(
    val assets: List<Asset>?,
    val body: String,
    @SerializedName("prerelease")
    val isPreRelease: Boolean,
) {
    fun gitReleaseToAppReleaseInfo(): List<AppReleaseInfo> {
        assets ?: throw NoStackTraceException("获取新版本出错")
        return assets
            .filter { it.isValid }
            .map { it.assetToAppReleaseInfo(isPreRelease, body) }
    }
}
@Keep
data class GiteeRelease(
    val assets: List<GiteeAsset>?,
    val body: String,
    @SerializedName("prerelease")
    val prerelease: Boolean,
) {
    fun gitReleaseToAppReleaseInfo(): List<AppReleaseInfo> {
        assets ?: throw NoStackTraceException("获取新版本出错")
        return assets
            .filter { it.isValid }
            .map { it.assetToAppReleaseInfo(prerelease, body) }
    }
}

/**
 * 按发布通道从 Gitee release 列表里挑目标 release：beta 通道取第一个 prerelease，正式通道取第一个非 prerelease。
 *
 * 通道必须由 release 元数据决定，不能依赖 `/releases/latest` 恰好返回哪一支——否则另一支存在时会被静默判成“已是最新”。
 */
fun List<GiteeRelease>.firstReleaseOfChannel(isBetaChannel: Boolean): GiteeRelease? =
    firstOrNull { it.prerelease == isBetaChannel }

@Keep
data class Asset(
    @SerializedName("browser_download_url")
    val apkUrl: String,
    @SerializedName("content_type")
    val contentType: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("download_count")
    val downloadCount: Int,
    val id: Int,
    val name: String,
    val state: String,
    val url: String
) {
    val isValid: Boolean
        get() = (contentType == "application/vnd.android.package-archive") && (state == "uploaded")

    fun assetToAppReleaseInfo(preRelease: Boolean, note: String): AppReleaseInfo {
        val instant = Instant.parse(createdAt)
        val timestamp: Long = instant.toEpochMilli()

        val appVariant = AppVariant.of(ReleaseIdentity.fromAssetName(name), preRelease)

        return AppReleaseInfo(appVariant, timestamp, note, name, apkUrl, url)
    }
}

@Keep
data class GiteeAsset(
    @SerializedName("browser_download_url")
    val apkUrl: String,
    @SerializedName("name")
    val name: String
) {
    val isValid: Boolean
        get() = apkUrl.contains(".apk")

    fun assetToAppReleaseInfo(preRelease: Boolean, note: String): AppReleaseInfo {

        val appVariant = AppVariant.of(ReleaseIdentity.fromAssetName(name), preRelease)

        return AppReleaseInfo(appVariant, 0, note, name, apkUrl, "")
    }
}


