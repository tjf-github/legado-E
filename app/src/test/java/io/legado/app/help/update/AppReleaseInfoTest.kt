package io.legado.app.help.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * dual-release-privacy A/B 阶段契约测试。
 *
 * 这里只使用现有生产 API，预期在当前实现上以断言失败变红；B 阶段不修改生产代码。
 * 冻结的产品契约是：
 *
 * - `io.legado.app.tjf.release` 是 PRIVATE（旧版延续，必须原地升级）；
 * - `io.legado.app.tjf` 是 NORMAL（新的独立安装链）；
 * - `.releaseS` 只属于历史临时测试身份，不是正式双版本候选；
 * - beta/正式是发布通道，normal/private 是资产身份，两个轴不能再压成一个 AppVariant。
 */
class AppReleaseInfoTest {

    private enum class ExpectedIdentity { PRIVATE, NORMAL }

    private companion object {
        const val PRIVATE_PACKAGE = "io.legado.app.tjf.release"
        const val NORMAL_PACKAGE = "io.legado.app.tjf"
        const val LEGACY_RELEASE_S_PACKAGE = "io.legado.app.tjf.releaseS"
        const val VERSION_L = "3.2609192100"
        const val PARSED_VERSION = "3.26091921"
    }

    private fun expectedIdentity(packageName: String): ExpectedIdentity? = when (packageName) {
        PRIVATE_PACKAGE -> ExpectedIdentity.PRIVATE
        NORMAL_PACKAGE -> ExpectedIdentity.NORMAL
        else -> null
    }

    private fun assetName(identity: ExpectedIdentity): String =
        "legado_app_${VERSION_L}_${identity.name.lowercase()}.apk"

    private fun asset(name: String) = Asset(
        apkUrl = "https://example.invalid/$name",
        contentType = "application/vnd.android.package-archive",
        createdAt = "2026-09-19T12:00:00Z",
        downloadCount = 0,
        id = name.hashCode(),
        name = name,
        state = "uploaded",
        url = "https://api.github.invalid/assets/${name.hashCode()}"
    )

    private fun githubRelease(preRelease: Boolean, vararg names: String): List<AppReleaseInfo> =
        GithubRelease(names.map(::asset), "note", preRelease).gitReleaseToAppReleaseInfo()

    private fun giteeRelease(preRelease: Boolean, vararg names: String): List<AppReleaseInfo> =
        GiteeRelease(
            names.map { GiteeAsset("https://example.invalid/$it", it) },
            "note",
            preRelease
        ).gitReleaseToAppReleaseInfo()

    /** 镜像当前消费端的关键规则：按 appVariant 精确过滤，再取第一个新版本。 */
    private fun pickLikeCurrentConsumer(
        assets: List<AppReleaseInfo>,
        checkVariant: AppVariant
    ): AppReleaseInfo? = assets
        .filter { it.appVariant == checkVariant }
        .firstOrNull { it.versionName > "3.0" }

    @Test
    fun packageIdentityToAssetIdentityContractIsFrozen() {
        assertEquals(ExpectedIdentity.PRIVATE, expectedIdentity(PRIVATE_PACKAGE))
        assertEquals(ExpectedIdentity.NORMAL, expectedIdentity(NORMAL_PACKAGE))
        assertNull(".releaseS 不属于正式双版本身份", expectedIdentity(LEGACY_RELEASE_S_PACKAGE))
    }

    @Test
    fun officialReleaseMustNotFlattenNormalAndPrivateIdentity() {
        val assets = githubRelease(
            false,
            assetName(ExpectedIdentity.PRIVATE),
            assetName(ExpectedIdentity.NORMAL)
        )

        assertNotEquals(
            "当前代码把正式 Release 的 normal/private 都压成 OFFICIAL，身份已丢失",
            assets[0].appVariant,
            assets[1].appVariant
        )
    }

    @Test
    fun betaReleaseMustKeepIdentityAndUseBetaChannel() {
        val assets = githubRelease(
            true,
            assetName(ExpectedIdentity.NORMAL),
            assetName(ExpectedIdentity.PRIVATE)
        )

        assertTrue("beta 元数据里的 normal/private 都必须保留 beta 通道", assets.all { it.appVariant.isBeta() })
        assertNotEquals("beta 通道内仍必须区分 normal/private", assets[0].appVariant, assets[1].appVariant)
    }

    @Test
    fun githubSelectionMustBeIdentityExactAndOrderIndependent() {
        val mismatches = mutableListOf<String>()
        for (preRelease in listOf(false, true)) {
            for (order in listOf(
                listOf(ExpectedIdentity.PRIVATE, ExpectedIdentity.NORMAL),
                listOf(ExpectedIdentity.NORMAL, ExpectedIdentity.PRIVATE)
            )) {
                val assets = githubRelease(preRelease, *order.map(::assetName).toTypedArray())
                for (wanted in ExpectedIdentity.entries) {
                    val wantedName = assetName(wanted)
                    val wantedVariant = assets.single { it.name == wantedName }.appVariant
                    val actualName = pickLikeCurrentConsumer(assets, wantedVariant)?.name
                    if (actualName != wantedName) {
                        mismatches += "preRelease=$preRelease order=$order wanted=$wanted actual=$actualName"
                    }
                }
            }
        }
        assertTrue(
            "GitHub normal/private 必须在 beta/正式及两个资产顺序下都只选自身；错配：$mismatches",
            mismatches.isEmpty()
        )
    }

    @Test
    fun giteeSelectionMustBeIdentityExactAndOrderIndependent() {
        val flattened = mutableListOf<String>()
        for (preRelease in listOf(false, true)) {
            for (order in listOf(
                listOf(ExpectedIdentity.PRIVATE, ExpectedIdentity.NORMAL),
                listOf(ExpectedIdentity.NORMAL, ExpectedIdentity.PRIVATE)
            )) {
                val assets = giteeRelease(preRelease, *order.map(::assetName).toTypedArray())
                if (assets[0].appVariant == assets[1].appVariant) {
                    flattened += "preRelease=$preRelease order=$order variant=${assets[0].appVariant}"
                }
            }
        }
        assertTrue(
            "Gitee normal/private 必须在 beta/正式及两个资产顺序下保持可区分；压平：$flattened",
            flattened.isEmpty()
        )
    }

    @Test
    fun releaseSAndUnrelatedApksMustNotEnterDualReleaseCandidates() {
        val legacyReleaseS = "legado_plus_${VERSION_L}_releaseS.apk"
        val unrelated = "unrelated_${VERSION_L}.apk"
        val assets = githubRelease(
            false,
            legacyReleaseS,
            unrelated,
            assetName(ExpectedIdentity.NORMAL),
            assetName(ExpectedIdentity.PRIVATE)
        )

        val normal = assets.single { it.name == assetName(ExpectedIdentity.NORMAL) }
        val private = assets.single { it.name == assetName(ExpectedIdentity.PRIVATE) }
        val legacy = assets.single { it.name == legacyReleaseS }
        val unrelatedInfo = assets.single { it.name == unrelated }

        assertTrue(
            "releaseS/无关 APK 不得与 normal/private 落入同一候选变体",
            setOf(normal.appVariant, private.appVariant)
                .intersect(setOf(legacy.appVariant, unrelatedInfo.appVariant))
                .isEmpty()
        )
        assertEquals(PARSED_VERSION, normal.versionName)
        assertEquals(PARSED_VERSION, private.versionName)
    }

    // ===== C 阶段补充契约（上方 6 个 A/B 红测断言原样保留，未因实现而改写） =====

    @Test
    fun installIdentityIsDerivedFromPackageNameOnly() {
        assertEquals(ReleaseIdentity.PRIVATE, ReleaseIdentity.fromPackageName(PRIVATE_PACKAGE))
        assertEquals(ReleaseIdentity.NORMAL, ReleaseIdentity.fromPackageName(NORMAL_PACKAGE))
        assertEquals(
            "debug 变体属于 normal 安装链（基础 applicationId + .debug）",
            ReleaseIdentity.NORMAL,
            ReleaseIdentity.fromPackageName("$NORMAL_PACKAGE.debug")
        )
        assertEquals(
            ReleaseIdentity.LEGACY_RELEASES,
            ReleaseIdentity.fromPackageName(LEGACY_RELEASE_S_PACKAGE)
        )
        assertEquals(
            "未知包名不得被猜成 normal/private",
            ReleaseIdentity.UNKNOWN,
            ReleaseIdentity.fromPackageName("io.legado.app")
        )
        assertEquals(
            ReleaseIdentity.UNKNOWN,
            ReleaseIdentity.fromPackageName("$LEGACY_RELEASE_S_PACKAGE.2")
        )

        // 身份 <-> 资产标识必须一致：CI 资产名 legado_app_<versionL>_<assetTag>.apk 靠它解析
        assertEquals("normal", ReleaseIdentity.NORMAL.assetTag)
        assertEquals("private", ReleaseIdentity.PRIVATE.assetTag)
        assertEquals(
            ReleaseIdentity.NORMAL,
            ReleaseIdentity.fromAssetName("legado_app_${VERSION_L}_${ReleaseIdentity.NORMAL.assetTag}.apk")
        )
        assertEquals(
            ReleaseIdentity.PRIVATE,
            ReleaseIdentity.fromAssetName("legado_app_${VERSION_L}_${ReleaseIdentity.PRIVATE.assetTag}.apk")
        )
    }

    @Test
    fun legacyReleaseAssetsStayPrivateAndNewAssetsCarryBothAxes() {
        // 历史 .release 资产 = 隐私版延续：旧 .release 安装仍要能在 beta 标签里找到自己的升级包
        val legacyBeta = githubRelease(true, "legado_beta_${VERSION_L}_release.apk").single()
        assertEquals(ReleaseIdentity.PRIVATE, legacyBeta.appVariant.identity)
        assertTrue("历史 .release 资产属于 beta 通道", legacyBeta.appVariant.isBeta())

        // CI 固定命名 legado_app_<versionL>_<identity>.apk，必须解析成 (身份, 通道) 两个轴
        val privateBeta = githubRelease(true, assetName(ExpectedIdentity.PRIVATE)).single()
        assertEquals(AppVariant.PRIVATE_BETA, privateBeta.appVariant)
        val normalOfficial = githubRelease(false, assetName(ExpectedIdentity.NORMAL)).single()
        assertEquals(AppVariant.NORMAL_OFFICIAL, normalOfficial.appVariant)

        // .releaseS 只保留历史语义：既不等于 normal，也不等于 private
        val legacyS = giteeRelease(false, "legado_plus_${VERSION_L}_releaseS.apk").single()
        assertEquals(ReleaseIdentity.LEGACY_RELEASES, legacyS.appVariant.identity)
        assertNotEquals(AppVariant.NORMAL_OFFICIAL, legacyS.appVariant)
        assertNotEquals(AppVariant.PRIVATE_OFFICIAL, legacyS.appVariant)
    }

    @Test
    fun updateSettingOnlyPicksChannelAndNeverIdentity() {
        assertEquals(true, ReleaseIdentity.betaChannelOfSetting("beta_release_version"))
        assertEquals(true, ReleaseIdentity.betaChannelOfSetting("beta_releaseA_version"))
        assertEquals(false, ReleaseIdentity.betaChannelOfSetting("official_version"))
        assertEquals(
            "设置里显示为“正式版”的历史键走正式通道",
            false,
            ReleaseIdentity.betaChannelOfSetting("beta_releaseS_version")
        )
        assertNull(
            "未显式指定时不得猜通道，回退到安装包默认通道",
            ReleaseIdentity.betaChannelOfSetting("default_version")
        )
        assertNull(ReleaseIdentity.betaChannelOfSetting(""))

        val installIdentities = listOf(ReleaseIdentity.NORMAL, ReleaseIdentity.PRIVATE)
        for (identity in installIdentities) {
            for (beta in listOf(true, false)) {
                val target = AppVariant.of(identity, beta)
                assertEquals("身份只能来自安装包名", identity, target.identity)
                val other = installIdentities.single { it != identity }
                assertNotEquals(target, AppVariant.of(other, beta))
                assertNotEquals(target, AppVariant.of(other, !beta))
            }
        }
    }

    @Test
    fun giteeChannelSelectionFollowsReleaseMetadataNotListOrder() {
        val betaName = assetName(ExpectedIdentity.PRIVATE)
        val officialName = assetName(ExpectedIdentity.NORMAL)
        val betaRelease = GiteeRelease(
            listOf(GiteeAsset("https://example.invalid/$betaName", betaName)),
            "beta note",
            true
        )
        val officialRelease = GiteeRelease(
            listOf(GiteeAsset("https://example.invalid/$officialName", officialName)),
            "official note",
            false
        )

        for (releases in listOf(
            listOf(officialRelease, betaRelease),
            listOf(betaRelease, officialRelease)
        )) {
            assertEquals(betaRelease, releases.firstReleaseOfChannel(true))
            assertEquals(officialRelease, releases.firstReleaseOfChannel(false))
            assertEquals(
                "beta 通道必须拿到 prerelease 那一支的资产",
                AppVariant.PRIVATE_BETA,
                releases.firstReleaseOfChannel(true)!!.gitReleaseToAppReleaseInfo().single().appVariant
            )
            assertEquals(
                AppVariant.NORMAL_OFFICIAL,
                releases.firstReleaseOfChannel(false)!!.gitReleaseToAppReleaseInfo().single().appVariant
            )
        }

        assertNull(
            "列表里没有匹配通道的 release 时必须判“已是最新”，不得退回另一支",
            listOf(officialRelease).firstReleaseOfChannel(true)
        )
    }
}
