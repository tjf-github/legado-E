# dual-release-privacy — C+D 阶段执行证据（DSH executor，route_revision 6）

状态：`cd-implemented / pending-review`

> A/B 阶段报告（由 controller=Codex 撰写）已**原样归档**到 `rounds/controller-ab-report-10-execution.md`
> （6506 字节，SHA-256 `A82704CB66E53DCBA07CB106CDBA8DCB1A1C734DA02C87C60EBFBDE0F73E1D5D`），未改写一个字节。
> 本文件是 C+D 当前轮的 executor 证据，只描述本次真实执行。

## 1. 授权与角色披露

- 本轮只执行 `00-task.md` 顶部「2026-09-20 DSH + Claude 接管任务书」授权的 **C（最小实现）+ D（本地自动化门禁）**。
- mode=`dsh-claude`、assurance=`standard`、route_revision=6：**DSH 同时是 controller、executor、acceptor，Claude 只是只读 reviewer**。
  这不是 high 档要求的「独立 executor + 独立 acceptor」，本报告与后续验收都必须带这一限制阅读。
- 未执行：E 真机安装/迁移、F 发布验证、提交、推送、发布、切分支、安装/卸载 APK、清数据。
- 未读取、输出或改动任何密钥内容；`apksigner --print-certs` 只用于比对两个 APK 的**证书摘要是否一致**。
- 写入范围：业务文件 10 个（相对 HEAD 新增 2 个：`ReleaseIdentity.kt`、`AppReleaseInfoTest.kt`），其余为任务目录内的协议证据文件。
  （§2 表格 9 行覆盖这 10 个文件，其中 `values/strings.xml` 与 `values-zh/strings.xml` 同一行。）

## 2. 实际业务 diff（与当前工作树一致）

| 文件 | 变更 | 为什么必须改 |
| --- | --- | --- |
| `app/src/main/java/io/legado/app/help/update/ReleaseIdentity.kt`（新增） | 新增身份轴枚举 + `fromPackageName` + `fromAssetName` + `betaChannelOfSetting` | 身份轴原先不存在，只能由「单枚举猜」；这是纯模型文件，白名单允许新增 |
| `AppReleaseInfo.kt` | `AppVariant` 改成 `(identity, betaChannel)` 两轴 8 变体；GitHub/Gitee 资产解析改为 `AppVariant.of(ReleaseIdentity.fromAssetName(name), preRelease)` | 原实现把正式发布的所有 APK 压成 `OFFICIAL`、把 beta 资产按名字猜身份，身份与通道混在一个轴上（A/B 红测 5 项失败） |
| `AppUpdateGitHub.kt` | `checkVariant` = 安装身份 + 设置通道 | 端点选择与资产过滤必须同源，否则会取到别人身份的 APK |
| `AppUpdateGitee.kt` | 同上；删除「`appVariant == BETA_RELEASE` 就不切版本」的身份锁；过滤统一为 `it.appVariant == checkVariant` | 身份已固定跟随安装包，旧锁只会额外把用户在设置里选的正式通道覆盖掉 |
| `AppConst.kt` | 安装身份改由包名派生（`ReleaseIdentity.fromPackageName`）；通道仍按签名/DEBUG（`isBetaChannel = isBeta && !isOfficial`）；`AppInfo.appVariant` 默认值改为 `UNKNOWN_OFFICIAL` | 原实现用 `packageName.contains("releaseA"/"releaseS")` 猜身份、用签名猜通道，normal/private 无法区分 |
| `app/build.gradle` | 新增 `-PRELEASE_IDENTITY=normal或private`（未指定默认 private，未知值立即失败）；release 的 `applicationIdSuffix` 与 `app_name` 占位符随身份 | 冻结契约要求身份由显式、fail-closed 的参数决定，不再由 CI 用 `sed` 改 build.gradle |
| `app/src/main/res/values/strings.xml`、`values-zh/strings.xml` | 新增 `app_name_private`（`Legado·Privacy` / `阅读·隐私`） | 双版本显示名必须可区分，但不得影响包名 |
| `app/src/test/java/io/legado/app/help/update/AppReleaseInfoTest.kt` | **A/B 的 6 个断言逐字未改**，新增 3 个契约测试 | 让红测转绿只能靠生产代码；新增测试覆盖包名→身份、历史资产兼容、设置项只改通道 |
| `.github/workflows/test.yml` | 矩阵 `normal/private`（不再按 updateLog 收敛）、删除 `sed`、资产名 `legado_app_<versionL>_<identity>.apk`、artifact `legado.app.*`、双版本完整性断言、下游四段（prerelease/telegram/gitee/update-storage）全部迁移 | 冻结契约要求两个身份每次发布都产出，且下游不得再依赖 `原包名/共存/releaseS` |

未触碰（白名单外或明确禁止）：`AndroidManifest.xml`、`res/values/arrays.xml`、`res/xml/pref_config_other.xml`、数据库、签名与 Secrets、AI 源码与设置、发布目标、`bookshelf.json`、`.claude/`、《维护计划书》与 AI 任务文件。

## 3. 冻结契约如何落地

1. **Gradle 身份 fail-closed**：`def releaseIdentity = project.findProperty("RELEASE_IDENTITY") ?: "private"`；非 `normal|private` 直接抛 `GradleException`（配置期失败）。本地不传参 = private，保持旧 `.release` 升级链。
2. **显示名与包名分离**：private=`io.legado.app.tjf.release` + `@string/app_name_private`；normal=`io.legado.app.tjf`（无后缀）+ `@string/app_name`；两者共用版本号、源码与签名配置。
3. **两轴分离**：`AppVariant = 身份 × 通道`。资产身份来自文件名（新资产 `normal`/`private`；历史资产只把 `release` 兼容成 private；`releaseS`/`releaseA` 归 `LEGACY_RELEASES`，与 normal/private 不同变体），通道来自 `prerelease`。
4. **身份永不来自设置**：`checkVariant = AppVariant.of(AppConst.appInfo.appVariant.identity, ReleaseIdentity.betaChannelOfSetting(pref) ?: 安装包默认通道)`；GitHub 与 Gitee 同一规则。
5. **CI 双身份**：matrix 恒为 `["normal","private"]`；构建命令新增 `-PRELEASE_IDENTITY`（取自 matrix 的 type）；artifact=`legado.app.normal` / `legado.app.private`；Release 资产名 `legado_app_<versionL>_<identity>.apk`；prerelease job 在发布前断言两个身份都存在，缺一即失败。
6. **下游迁移**：telegram 用 `*_private.apk` 判定 plus 标签；gitee 直接上传已定名的资产；update-storage 改取 `legado.app.private`。
7. **不变量**：未改数据库结构、业务功能、AI 开关、签名配置、用户数据格式与版本号口径。

## 4. D 阶段门禁与真实结果

运行环境：Windows + `GRADLE_USER_HOME=D:/gradle_home`，JDK 17（Temurin 17.0.20），Android SDK `D:/Android/Sdk`（build-tools 35.0.0）。

> **证据来源标注（2026-09-20 第 2 轮复审后更正，属收窄性修正）**：本节 D0–D7 **全部产生于 F-01（Gitee 通道）修复之前**的代码状态，
> 因此 D1/D2 的计数是 9 / 258 项，而当前冻结树上应为 10 / 259 项。修复后的门禁原始输出由 acceptor 在 `30-acceptance.md` §2 给出；
> 本节数字只作历史记录，**不得作为当前完成判据**，两者不得混用。

| 步骤 | 命令 | 结果 |
| --- | --- | --- |
| D0 | `git diff --check` | `EXIT=0`，只有 Git 的 LF→CRLF 提示 |
| D1 | `$env:GRADLE_USER_HOME='D:/gradle_home'; gradlew.bat :app:testAppDebugUnitTest --tests io.legado.app.help.update.AppReleaseInfoTest --console=plain`（等价于冻结命令的 `-g D:/gradle_home`，reviewer F-05 指出报告应写明这一等价关系） | `BUILD SUCCESSFUL in 44s`；XML：tests=9 / failures=0 / errors=0 |
| D2 | `gradlew.bat :app:testAppDebugUnitTest --console=plain` | `BUILD SUCCESSFUL in 11s`；38 个 XML 汇总 tests=258 / failures=0 / errors=0 / skipped=0（全部时间戳 2026/9/20 16:17:29） |
| D3 | `gradlew.bat :app:lintAppDebug --console=plain` | `BUILD SUCCESSFUL in 11m 41s`，报告 `app/build/reports/lint-results-appDebug.html` |
| D4 | `gradlew.bat :app:assembleAppRelease -PRELEASE_IDENTITY=normal --console=plain` | `BUILD SUCCESSFUL in 16m 22s`，APK 19,964,341 字节 |
| D5 | `gradlew.bat :app:assembleAppRelease -PRELEASE_IDENTITY=private --console=plain` | 成功，APK 19,964,366 字节（16:53:09）；随后复跑 `BUILD SUCCESSFUL in 22s`（UP-TO-DATE）、`EXIT=0` |
| D6 | `gradlew.bat :app:assembleAppRelease -PRELEASE_IDENTITY=garbage`（**预期失败**） | `BUILD FAILED in 3s`，`build.gradle` 第 41 行：`RELEASE_IDENTITY 只接受 normal 或 private，当前为 garbage`，`EXIT=1` |
| D7 | `aapt2 dump badging` + `apksigner verify --print-certs` | 见下 |

D7 逐项事实：

- normal：`package: name=io.legado.app.tjf versionCode=26092016 versionName=3.26.092016`；`application-label: Legado`、`application-label-zh: 阅读`。
- private：`package: name=io.legado.app.tjf.release versionCode=26092016 versionName=3.26.092016`；`application-label: Legado·Privacy`、`application-label-zh: 阅读·隐私`。
- 两者版本号一致；包名一个是基础 applicationId、一个是旧自用版身份；显示名可区分。
- 证书 SHA-256 摘要：两包均为 `bea423a492567c23903896aa41fac03f31fbd89c98eb6a32f7a7b7b2fe55db01`，**一致**（未输出任何密钥内容）。
- 补充：CI 里「缺任一身份即失败」的守卫逻辑用 git-bash 本地模拟过：两包都在 → 通过；删掉 private → 报「缺少 private APK，双版本发布不完整」并判失败；telegram 的 `*_private.apk` 标签判定随之翻转。这只证明 shell 判定逻辑，**不代表 CI 已跑**。

## 5. 过程中出现的真实缺陷与一次环境误导

1. **真实缺陷（已修）**：`AppConst.kt` 的 `AppInfo.appVariant` 默认值仍写着 `AppVariant.UNKNOWN`，而该常量随旧枚举一起被删除 → `compileAppDebugKotlin` 直接失败（`Unresolved reference UNKNOWN`）。改为 `AppVariant.UNKNOWN_OFFICIAL` 后消失。这是本轮唯一一次由改动本身引起的编译失败。
2. **环境误导（非代码缺陷，已排除并留证）**：修好上一条后，`AppUpdateGitHub.kt` / `AppUpdateGitee.kt` 仍报 26 条「`okHttpClient` / `GSON` / `newCallResponse` 未解析」，且连续复跑不变。逐条排除后确认是 Kotlin 增量编译与构建缓存的脏状态（声明这些符号的文件并未改动，错误却指向它们的 import）。处置：删除 `app/build/kotlin`、`app/build/tmp/kotlin-classes` 并以 `--no-build-cache` 全量重编，`BUILD SUCCESSFUL in 3m 26s`；此后 D1–D7 全部在干净状态下通过。
   **后续窗口请注意**：本项目改完 Kotlin 若出现成片「import 未解析」而声明文件并未改动，优先怀疑增量状态，不要当成产品缺陷去改代码。

## 6. 未运行项（NOT RUN）

- GitHub Actions `Test Build` 实跑、真实 Release/asset 上传、beta 标签更新：NOT RUN（属 F）。
- 真机安装、`.release` 覆盖升级与数据保留、双包共存、应用内检查更新：NOT RUN（属 E）。
- 设备侧证据（adb）、Gitee 真实上传：NOT RUN。
- 提交、推送、发布、切分支：NOT RUN（未授权）。
- 独立 executor 回执：NOT RUN —— 本轮 executor 就是人类启动的 DSH 实例本身（mode `dsh-claude` 的既定映射），顶层 `14-executor-prompt.md` / `15-executor-raw.md` 仍是 r006 子进程超时留下的历史回执；`role-verify` 对 executor 角色预计仍 FAIL，acceptor 必须在 `30-acceptance.md` 里如实披露，不得当作通过。

## 7. 判断、偏离与残留（请 reviewer 重点核对）

1. **设置项键名不在白名单**：`res/values/arrays.xml` 与 `res/xml/pref_config_other.xml` 属 C+D 白名单外文件，用户可见的三个选项键名仍是历史值 `default_version` / `beta_release_version` / `beta_releaseS_version`。本轮只把「键名 → 通道」的映射收敛进 `ReleaseIdentity.betaChannelOfSetting`：`beta_releaseS_version`（界面文案为「正式版」）→ 正式通道，`beta_release_version`（测试版）→ beta 通道，`beta_releaseA_version`（共存版，界面未列出）→ beta 通道，其它值 → 回退安装包默认通道。键名/文案清理留给白名单放开后的独立改动。
2. **update-storage 只换数据源**：改为取 `legado.app.private`，但 link-E 仓库里的目标路径与文件名保持 `sigma/leagado_Sigma_正式版.apk` 不变，以免改到白名单外的外部消费契约；若用户要顺带改这个文件名，需要另行授权。
3. **Manifest 只读核对**：`readerProvider` / `fileProvider` 均用 applicationId 占位符，双包 authorities 唯一；固定权限 `io.legado.READ_WRITE` 由同一证书签名的两个包声明，静态未见冲突，但**真机共存仍是 E 的门槛**。
4. **默认通道取决于真实签名**：本机 release 证书摘要 `bea423a4…` 既不等于 `AppConst.OFFICIAL_SIGNATURE` 也不等于 `BETA_SIGNATURE`，因此本机打出的 release 默认走**正式通道**（判定逻辑与改动前一致）。E 阶段必须在真机上先记录已安装 `.release` 的证书摘要，再判断默认通道与 beta 标签的关系。
5. **`app_name_a` / `app_name_s` 资源保留但不再被引用**：Manifest 占位符改由身份决定后这两个字符串失去引用；为控制 diff 未删除资源。
6. **版本名口径（本项已按 reviewer F-05 更正）**：本地兜底 `yy.MMddHH` 与 CI 注入 `3.%y.%m%d%H` 的字段、宽度、补零一致，渲染结果相同（实测本机 APK `versionName=3.26.092016`），原文称“本地与 CI 不同”是**事实错误**；真正需要钉住的口径是 CI 资产名 `legado_app_<VERSIONL>_<identity>.apk` 与 `AppReleaseInfo.versionName = name.split("_")[2].dropLast(2)` 自洽（`VERSIONL` = `VERSION` + 分钟两位），由冻结测试的 `VERSION_L` / `PARSED_VERSION` 覆盖。
7. **UNKNOWN 身份是 fail-closed 兜底**：非本 fork 包名 → `UNKNOWN_*`，与 normal/private/legacy 变体都不相等，不会拿到双版本资产；代价是这类自定义包名安装包不再从本项目取任何更新（旧实现会把它们当 `OFFICIAL`）。
8. **未做（且不应在本轮做）**：数据库/用户数据迁移、跨包数据继承、真机验证、发布通道实跑。

## 8. 证据位置

- 定向测试 XML：`app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.update.AppReleaseInfoTest.xml`
- 全量测试 XML 目录：`app/build/test-results/testAppDebugUnitTest/`（38 个文件）
- lint 报告：`app/build/reports/lint-results-appDebug.html`
- 两个 APK 副本：系统临时目录 `%TEMP%/dual-release-privacy/{normal,private}.apk`（收尾时删除）
- A/B 归档：`rounds/controller-ab-report-10-execution.md`
- 复审冻结命令：`06-review-commands.json`（diff-check / business-diff / 定向测试 / status）
## 9. 复审后处置（reviewer-r003 findings）

复审原文：`20-review.md`（runner 冻结 sha256 `08339df6cd7beff1be3edd6a74a40d883e092288cefe088144a0c53a3a951be7`，结论「通过（有条件）、无阻塞项」，但 reviewer 侧 Bash 被禁用、4 条冻结命令全部 NOT RUN）。

| finding | 处置 |
| --- | --- |
| F-01 Gitee beta 通道依赖 `/releases/latest` | **已修**：`AppUpdateGitee.getLatestRelease` 两条通道统一取列表（`per_page=5&direction=desc`），新增纯函数 `List<GiteeRelease>.firstReleaseOfChannel(isBetaChannel)`（`AppReleaseInfo.kt`）按 `prerelease` 元数据选支，选不到直接抛「已是最新版本」，不再可能拿另一支静默判最新；新增 JVM 测试 `giteeChannelSelectionFollowsReleaseMetadataNotListOrder`（两种列表顺序 + 只有正式 release 时必须为 null） |
| F-02 update-storage 载荷身份变化 | **用户已裁决（2026-09-20，「按任务书吧」）**：`00-task.md` 约束 5 与迁移计划 §3.2 只要求下游「显式选择 normal 或 private、不得再依赖 releaseS」，未要求改目标路径；旧 `.releaseS` 正是「隐私系统共存版」，其语义延续者即 private，故保留 `legado.app.private`，link-E 目标路径与文件名不变。**残留**：`.releaseS` 旧下载用户无法被任一新身份覆盖升级（废止 releaseS 的必然结果），F 阶段须把「link-E 目标文件的包名已由 `.releaseS` 变为 `.release`」作为显性契约核对 |
| F-03 `:app:compileAppDebugKotlin` 未单列 | **已补**：acceptor 在冻结树上显式重跑该门禁并记录原始输出（见 `30-acceptance.md`），同时覆盖「白名单外是否残留旧 `AppVariant` 常量引用」 |
| F-04 diff 通道看不到 untracked 新文件 | **已披露接受**：`git diff` 对 untracked 恒空，本轮不引入 `git add -N`（避免改索引影响其它窗口）；改为在 `30-acceptance.md` 记录两个新文件的路径、行数与 SHA-256 作为替代证据 |
| F-05 报告三处事实不符 | **已更正**：本节上方已改（文件计数、版本名口径、D1 命令与冻结命令的等价关系） |
| F-06 A/B 第 6 个测试是空转测试 | **接受不再改**：`packageIdentityToAssetIdentityContractIsFrozen` 只断言测试类内部常量，红测保真用；真实契约由 C 阶段新增的 `installIdentityIsDerivedFromPackageNameOnly` 覆盖，`30-acceptance.md` 会标注它的性质 |
| F-07 死分支与死资源 | **接受，暂不清理**：`ReleaseIdentity.PRIVATE_PACKAGE + ".debug"` 当前不可达（保留为防御性映射，不改变任何可达行为）；`app_name_a`/`app_name_s` 失去引用但未删除。两者都属白名单放开后的独立清理，不构成本轮阻塞 |

F-01 的改动落在 C 阶段授权的「更新识别/更新过滤」范围内，属于**功能改动**，按 `MODES.md`「任何改变功能的修正都要重新复审」，本轮已重新拉起 reviewer（第 2 轮）。
