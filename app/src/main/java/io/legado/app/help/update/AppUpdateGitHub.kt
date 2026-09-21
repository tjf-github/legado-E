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
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.CoroutineScope

@Keep
@Suppress("unused")
object AppUpdateGitHub : AppUpdate.AppUpdateInterface {

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
        val lastReleaseUrl = if (checkVariant.isBeta()) {
            "https://api.github.com/repos/tjf-github/legado-E/releases/tags/beta"
        } else {
            "https://api.github.com/repos/tjf-github/legado-E/releases/latest"
        }
        val res = okHttpClient.newCallResponse {
            url(lastReleaseUrl)
        }
        if (!res.isSuccessful) {
            throw NoStackTraceException("获取新版本出错(${res.code})")
        }
        val body = res.body.text()
        if (body.isBlank()) {
            throw NoStackTraceException("获取新版本出错")
        }
        return GSON.fromJsonObject<GithubRelease>(body)
            .getOrElse {
                throw NoStackTraceException("获取新版本出错 " + it.localizedMessage)
            }
            .gitReleaseToAppReleaseInfo()
            .sortedByDescending { it.createdAt }
    }

    override fun check(
        scope: CoroutineScope,
    ): Coroutine<AppUpdate.UpdateInfo> {
        return Coroutine.async(scope) {
            getLatestRelease()
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
                ?: throw NoStackTraceException("已是最新版本")
        }.timeout(10000)
    }
}
