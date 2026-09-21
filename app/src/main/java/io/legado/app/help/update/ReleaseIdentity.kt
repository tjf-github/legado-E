package io.legado.app.help.update

/**
 * 发布/安装身份轴（与“发布通道 beta/正式”正交）。
 *
 * 冻结契约见 .agent-protocol/tasks/dual-release-privacy/40-window-migration-plan.md：
 * - [PRIVATE]：包名 `io.legado.app.tjf.release`，旧自用版延续，必须能原地覆盖升级并保留数据；
 * - [NORMAL]：包名 `io.legado.app.tjf`（含 `.debug`），全新的独立安装链；
 * - [LEGACY_RELEASES]：`releaseS`/`releaseA` 只保留历史语义，不属于正式双版本候选；
 * - [UNKNOWN]：不是本 fork 的安装包，或无法识别的资产，永远不参与精确匹配。
 *
 * 包名是升级身份，不按显示名称推断；改 `.release` 包名会切断覆盖升级与应用私有数据，禁止。
 */
enum class ReleaseIdentity(val assetTag: String) {
    NORMAL("normal"),
    PRIVATE("private"),
    LEGACY_RELEASES("releaseS"),
    UNKNOWN("");

    companion object {

        /** 基础 applicationId：普通版（新安装链）。 */
        const val NORMAL_PACKAGE = "io.legado.app.tjf"

        /** 旧自用版包名：隐私版必须延续它。 */
        const val PRIVATE_PACKAGE = "io.legado.app.tjf.release"

        /** 历史临时共存包名，不再发布。 */
        const val LEGACY_RELEASE_S_PACKAGE = "io.legado.app.tjf.releaseS"
        const val LEGACY_RELEASE_A_PACKAGE = "io.legado.app.tjf.releaseA"

        /**
         * 安装包名 -> 身份。只认本 fork 冻结的包名，未知包名不猜测、不回退到 normal/private。
         */
        fun fromPackageName(packageName: String): ReleaseIdentity = when (packageName) {
            PRIVATE_PACKAGE, "$PRIVATE_PACKAGE.debug" -> PRIVATE
            LEGACY_RELEASE_S_PACKAGE, LEGACY_RELEASE_A_PACKAGE -> LEGACY_RELEASES
            NORMAL_PACKAGE, "$NORMAL_PACKAGE.debug" -> NORMAL
            else -> UNKNOWN
        }

        /**
         * 更新设置项 -> 发布通道；返回 null 表示未显式指定，回退到安装包默认通道。
         *
         * 键名来自设置里的 `default_app_variant_value` 数组（历史命名，界面文案见 strings.xml）：
         * `beta_releaseS_version` 显示为“正式版”，按可见语义走正式通道；`beta_releaseA_version`（“共存版”）
         * 保持 beta 通道。两者都只影响通道——身份永远来自安装包名，设置项不得换身份。
         */
        fun betaChannelOfSetting(setting: String?): Boolean? = when (setting) {
            "beta_release_version", "beta_releaseA_version" -> true
            "official_version", "beta_releaseS_version" -> false
            else -> null
        }

        /**
         * 资产文件名 -> 身份。
         *
         * 新资产统一带 `normal`/`private` 标识；历史资产只把 `release` 兼容成 private
         * （旧 `.release` 安装的升级链），`.releaseS`/`.releaseA` 只保留历史语义。
         */
        fun fromAssetName(name: String): ReleaseIdentity = when {
            name.contains("releaseS") || name.contains("releaseA") -> LEGACY_RELEASES
            name.contains("private") -> PRIVATE
            name.contains("normal") -> NORMAL
            name.contains("release") -> PRIVATE
            else -> UNKNOWN
        }
    }
}
