package io.legado.app.help.update

import androidx.annotation.Keep
import io.legado.app.constant.AppConst
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.CoroutineScope

@Keep
@Suppress("unused")
object AppUpdateGitee : AppUpdate.AppUpdateInterface {

    /**
     * 更新检查目标 = 本机安装身份（永远跟随包名）+ 发布通道（可由设置覆盖）。
     *
     * 身份绝不允许来自设置项：普通版拿到隐私版（或反向）会直接装错包，切断升级链。
     * 历史键 `beta_releaseS_version` 在设置里显示为“正式版”，按其可见语义映射到正式通道；
     * `beta_releaseA_version`（“共存版”）保持 beta 通道。两者都不再改变身份。
     */
    private val checkVariant: AppVariant
        get() = AppVariant.of(
            AppConst.appInfo.appVariant.identity,
            ReleaseIdentity.betaChannelOfSetting(AppConfig.updateToVariant)
                ?: AppConst.appInfo.appVariant.isBeta()
        )

    private suspend fun getLatestRelease(): List<AppReleaseInfo> {
        // beta 与正式 release 在同一条列表里，按 prerelease 元数据选通道：
        // 不再用 /releases/latest（它到底返不返回 prerelease 没有可验证保证，取错支会静默判“已是最新”）。
        val isBetaChannel = checkVariant.isBeta()
        val releasesUrl =
            "https://gitee.com/api/v5/repos/lyc486/legado/releases?page=1&per_page=5&direction=desc"
        val res = okHttpClient.newCallResponse {
            url(releasesUrl)
        }
        if (!res.isSuccessful) {
            throw NoStackTraceException("获取新版本出错(${res.code})")
        }
        val body = res.body.text()
        if (body.isBlank()) {
            throw NoStackTraceException("获取新版本出错")
        }
        return GSON.fromJsonArray<GiteeRelease>(body)
            .getOrElse {
                throw NoStackTraceException("获取新版本出错 " + it.localizedMessage)
            }
            .firstReleaseOfChannel(isBetaChannel)
            ?.gitReleaseToAppReleaseInfo()
            ?.sortedByDescending { it.createdAt }
            ?: throw NoStackTraceException("已是最新版本")
    }

    override fun check(
        scope: CoroutineScope,
    ): Coroutine<AppUpdate.UpdateInfo> {
        return Coroutine.async(scope) {
            getLatestRelease()
                // 身份已固定跟随安装包（见 checkVariant），不再需要为 `.release` 安装单独锁身份；
                // 原“不切版本”分支事实上只允许 beta 通道，会覆盖用户在设置里选的正式通道。
                .filter { it.appVariant == checkVariant }
                .firstOrNull { it.versionName > AppConst.appInfo.versionName }
                ?.let {
                    return@async AppUpdate.UpdateInfo(
                        it.versionName,
                        it.note,
                        it.downloadUrl,
                        it.name
                    )
                }
            throw NoStackTraceException("已是最新版本")
        }.timeout(10000)
    }
}
