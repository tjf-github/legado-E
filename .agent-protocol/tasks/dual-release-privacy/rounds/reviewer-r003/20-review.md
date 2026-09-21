<!-- agent-protocol-binding
round_id: reviewer-r003
task_digest: 7aad1970fe8504e071bf9e4655f67736209fcf5093bb802b81eaa7e22f6d7216
snapshot_digest: 5222bf776001169185a113a5a0e5501663c40b521e7956f261261fbb9adf4f42
--># dual-release-privacy — C+D 阶段独立复审（reviewer=claude，route_revision 6，standard）

## 0. 结论

**通过（有条件）——未发现阻塞项。**

- 我在**只读**条件下逐字读完了 C+D 白名单内全部业务文件与协议证据，静态核对的结果是：冻结契约 1–8 在代码/CI 层面均已落地，未发现身份压平、身份来自设置、`.releaseS` 残留、越界改动或新增安全面。
- 但**本轮我无法执行任何命令**（本会话 Bash 工具被禁用，`git`/`gradlew.bat` 均不可调用），因此 `06-review-commands.json` 里冻结的 4 条检查（diff-check / business-diff / 定向测试 / status）在我这边全部是 `NOT RUN`。`10-execution.md` §4 的 D0–D7、§5 的脏缓存排除过程、D7 的包名与证书摘要，**全部仍是 executor 自述，未被我独立复现**。
- 由于没有 `git diff` 可用，本轮只能做“当前状态复审”，**不能做基线对比复审**：A/B 六个断言是否“逐字未改”、test.yml 除报告所述之外是否还有其它改动、`维护计划书` 与 AI 路由文件的改动是否属既有，我都无法核实。
- 因此建议：`30-acceptance.md` 必须把“Claude reviewer 未能执行任何冻结命令、未能做 diff 对比”作为保证降级如实披露，并由 acceptor 在冻结树上重跑关键门禁；F 阶段开始前先处置 F-01。

---

## 1. 我实际执行的核对动作与输出

只读动作（全部完成）：

1. 读了 `00-task.md`（15728 字节版本，顶部“2026-09-20 DSH + Claude 接管任务书”为唯一权威入口）、`10-execution.md`、`05-routing.md`、`06-review-commands.json`、`40-window-migration-plan.md`、`18-review-probes.md`、`13-reviewer-context.json`、`.review-snapshot.json`、`rounds/controller-ab-report-10-execution.md`。
2. 逐字读完业务文件：`ReleaseIdentity.kt`、`AppReleaseInfo.kt`、`AppUpdateGitHub.kt`、`AppUpdateGitee.kt`、`AppConst.kt`、`app/build.gradle`、`.github/workflows/test.yml`、`app/src/main/AndroidManifest.xml`、`values/strings.xml`、`values-zh/strings.xml`（前 40 行）、`AppReleaseInfoTest.kt`。
3. 受限搜索（只对单个白名单文件做 `rg`，未做目录遍历）：
   - `test.yml` 匹配 `releaseS|releaseA|applicationIdSuffix|app_name` → **No matches found**（`.releaseS`/`.releaseA` 依赖已清零，CI 不再写 `app_name` 占位符）。
   - `app/build.gradle` 匹配 `releaseS|releaseA|app_name|RELEASE_IDENTITY|applicationId` → 只有第 37/39/41/69/102/103/105/116/118 行，无 `releaseS/releaseA`。
   - `AppReleaseInfo.kt` 匹配 `releaseS|releaseA|OFFICIAL|BETA_RELEASES|isBeta` → `BETA_RELEASES` 已彻底不存在；`releaseS/releaseA` 只剩第 32 行注释。
4. 交叉一致性核算（我自己的算术，不是采信报告）：
   - `AppReleaseInfoTest.kt` 的 `@Test` 数量 = **9**，与 `10-execution.md` D1 的 `tests=9` 一致。
   - CI 版本口径：`VERSION=3.%y.%m%d%H`、`VERSIONL=3.%y.%m%d%H%M`，资产名 `legado_app_<VERSIONL>_<identity>.apk`；`name.split("_")[2].dropLast(2)` 恰得 `VERSION` ⇒ 客户端解析版本与 APK 的 `versionName` 自洽（`AppReleaseInfo.kt:16`）。
   - 两轴选择：GitHub/Gitee 的 `checkVariant`（`AppUpdateGitHub.kt:26-31`、`AppUpdateGitee.kt:27-32`）与资产解析（`AppReleaseInfo.kt:99,117`）同源；`AppVariant.of` 覆盖 8 个组合，不存在取不到值的分支（`AppReleaseInfo.kt:43-44`）。
   - 红测自洽性：`rounds/controller-ab-report-10-execution.md:43-50` 记 `6 tests completed, 5 failed`，唯一 PASS 的是 `packageIdentityToAssetIdentityContractIsFrozen` —— 与我独立得出的“该测试是自证空转”结论一致（见 F-07）。

未执行（见 §3）：`06-review-commands.json` 全部 4 条；`git status`、`git diff`、Gradle、aapt2、apksigner。

---

## 2. 发现

### F-01（中，阻塞 F 之前必须先决定）：Gitee 的 beta 通道取的是 `/releases/latest`，通道与 release 元数据没有绑定

- 位置：`app/src/main/java/io/legado/app/help/update/AppUpdateGitee.kt:35-39`（URL 选择）、`:50-58`（正式分支用 `first { !it.prerelease }`）、`AppReleaseInfo.kt:117`（通道来自 release 的 `prerelease` 字段）。
- 具体失败场景（两种读法都成立，代码不可能同时正确）：
  - 若 Gitee `/releases/latest` 返回“最新创建的 release（含 prerelease）”：每次 push 的 gitee job 只会创建**一个** release。当某次 updateLog 变化、创建的是正式 release（`prerelease=false`）时，beta 通道用户的 `/releases/latest` 拿到的就是正式 release，其资产被解析为 `*_OFFICIAL`（`AppReleaseInfo.kt:117`），与 `checkVariant = *_BETA` 不相等 ⇒ `filter` 全空 ⇒ 抛“已是最新版本”。**beta 通道用户在 Gitee 上静默拿不到更新**，而同一个 beta release 其实还在仓库里。
  - 若 Gitee `/releases/latest` 排除 prerelease：beta 分支**永远**拿不到标记为 prerelease 的 beta release，同样永远“已是最新版本”。
- 影响与定位：这是**发布通道（功能面）缺陷，不是安全/数据缺陷**；GitHub 侧没有这个问题（`AppUpdateGitHub.kt:34-38` 用 `/releases/tags/beta`，且 `isPreRelease` 与通道天然一致）。
- 该缺陷很可能**不是本轮引入**（URL 选择看起来是沿用的），但我在无 diff 的情况下不能确认；而 `00-task.md` 完成约束 4 要求“GitHub/Gitee 必须在 beta/正式元数据……精确选择自身资产，不得依赖列表顺序”，此处恰好依赖“/latest 恰好是 beta”。
- 建议：beta 分支改为与正式分支对称——取 `releases?page=1&per_page=3&direction=desc` 列表并选 `first { it.prerelease }`；或至少对 `/latest` 返回体的 `prerelease` 做断言，不匹配就报错而不是静默判“最新”。无论是否本轮修，**都必须在 F 之前显式接受或修复**，否则“beta 与正式发布均生成 normal/private 两个资产、两种身份都能更新”的验收判据在 Gitee 侧不成立。

### F-02（中低）：`update-storage` 上传到 link-E 的载荷身份很可能变了，但目标文件名没变

- 位置：`.github/workflows/test.yml:498`（`name: legado.app.private`）与 `:524`（`cp ./apk-private/*.apk link-E-repo/sigma/leagado_Sigma_正式版.apk`）。
- 具体失败场景：旧矩阵类型是 `release/releaseS`（A/B 报告第 17 行佐证），旧该 job 取的应是 `releaseS` 那一支（即 `.releaseS` 包名的 APK）；现在取 `legado.app.private`，即**包名 `.release` 的 APK**。目标文件名 `leagado_Sigma_正式版.apk` 被刻意保持不变（§7.2），于是外部消费方拿到的是**同一个文件名、不同 applicationId** 的包：通过该路径安装的用户会**新装一个应用而不是覆盖升级**（或反之）。这是对外部仓库的可见行为变化。
- 我无法确认旧 job 到底取哪一支（无 diff）——所以我把本条定为“需显式确认”，而不是“已确认缺陷”。
- 建议：在 F 阶段发布验证项里增加一条“link-E 目标文件的包名与预期消费方安装链是否一致”，或明确记录“该文件名下的包名已由 `.releaseS` 变为 `.release`，且这是有意的”，把它从隐性变化变成显性契约。

### F-03（中低，证据缺口）：`00-task.md` 完成约束 6 明确要求的 `:app:compileAppDebugKotlin` 没有独立结果记录

- 位置：`10-execution.md:49-57`（D 表列了 D0 `git diff --check`、D1 定向测试、D2 全量单测、D3 lint、D4/D5 双 release、D6 负例、D7 包名/证书，**没有 `:app:compileAppDebugKotlin` 这一行**）；`10-execution.md:69` 只在“过程中出现的缺陷”里提到它失败过一次，`10-execution.md:70` 提到 `--no-build-cache` 全量重编成功但未给出被重编的任务名与逐项结果。
- 具体风险场景：约束 6 的措辞是“运行：…、`:app:compileAppDebugKotlin`、全量…”，并要求未运行项写 `NOT RUN`。现在它既没有“结果”也没有“NOT RUN”，属于**该写而没写**；更实质的是，本轮把 `AppVariant` 的三个旧常量（`OFFICIAL/BETA_RELEASES/UNKNOWN`）整体删除，route_revision 3 的记录显示**白名单外曾有消费端依赖 `AppVariant.BETA_RELEASES`**（`05-routing.md:26-29`）。这类白名单外引用一旦漏改就是编译失败，而我无法编译验证。
- 建议：`30-acceptance.md` 在冻结树上独立重跑 `:app:compileAppDebugKotlin`（或至少 `:app:testAppDebugUnitTest` + `:app:assembleAppRelease`）并把命令与退出码写进验收；这是唯一能覆盖“白名单外旧常量引用是否已清零”的手段。

### F-04（低，方法论/证据边界）：冻结的 diff 类命令结构上看不到新增文件，而 D0 的“EXIT=0”只覆盖 8 个已跟踪文件

- 位置：`06-review-commands.json:8,15` 的 `git diff`/`git diff --check` 路径表虽然包含 `ReleaseIdentity.kt` 与 `AppReleaseInfoTest.kt`，但这两个文件在 HEAD 上是 **untracked**（会话起始 `git status` 记为 `?? app/src/main/java/io/legado/app/help/update/ReleaseIdentity.kt`、`?? app/src/test/java/io/legado/app/help/update/`），`git diff` 对 untracked 文件恒为空。
- 后果：`10-execution.md:50` 的 `git diff --check EXIT=0` 与冻结的 `phase-cd-business-diff` **永远不可能**显示这两个文件的内容或空白问题；本次改动的 10 个文件里有 2 个（含全部新逻辑的 `ReleaseIdentity.kt`）落在该证据覆盖之外。
- 补充：我在本会话**连这 4 条命令都无法执行**（Bash 禁用），所以这 4 条在我这一侧是 `NOT RUN`，我对其内容的了解完全来自直接读文件。
- 建议：把 `git diff --no-index`（或 `git add -N` 后的 `git diff`）纳入冻结命令，或在 30 里明确“新增文件由 reviewer 直接读取核对，diff 通道不覆盖”。

### F-05（低）：执行报告与事实有三处不一致，acceptor 不应原样继承

- `10-execution.md:16` “写入范围：业务文件 8 个（新增 1 个）”与 `:20-30` 的表不一致：表内 9 行、涉及 10 个文件，其中 `ReleaseIdentity.kt` 与 `AppReleaseInfoTest.kt` 相对 HEAD 都是新增（untracked）。
- `10-execution.md:88`（§7.6）称“本地兜底 `3.yy.MMddHH` 与 CI 注入 `3.yymmddHH` 不同（既有差异）”：`%y.%m%d%H` 与 `yy.MMddHH` 的字段含义、宽度、补零完全一致，**二者渲染结果相同**，不存在该差异（结论无害，但事实写错）。
- `10-execution.md:51` 的 D1 命令未带冻结数组里的 `-g D:\gradle_home`（`06-review-commands.json:22`），只在正文声明了环境变量；严格说报告引用的命令行与冻结命令不是同一条，无法据此判断是否按冻结参数执行。
- 建议：30 阶段修正这三处，避免把错误事实带进验收结论。

### F-06（低）：6 个 A/B 红测里有 1 个是空转测试，不应计入“有效信号”

- 位置：`AppReleaseInfoTest.kt:70-75`。该测试只对测试类内部的私有 `expectedIdentity()`（`:32-36`，纯 `when` + 测试常量）做断言，**不触碰任何生产代码**，因此在任何实现下都恒过。
- 已由归档证据佐证：`rounds/controller-ab-report-10-execution.md:43-44` 记红测阶段“6 tests completed, 5 failed”，唯一 PASS 就是它。所以“6 项中 5 项失败”应当读作“**5/6 有效**”，而不是“6 项红测”。它作为红测基线的保真度没问题（本轮要求逐字不改），但不能当作回归保护。
- 该契约已被 C 阶段新增的 `installIdentityIsDerivedFromPackageNameOnly`（`AppReleaseInfoTest.kt:179-212`）真正覆盖，所以**无需再改**；仅建议在 30 里把它标注为“无语义断言，红测保真用”。

### F-07（低，残留/死代码）：`ReleaseIdentity.PRIVATE_PACKAGE + ".debug"` 分支不可达；`app_name_a/app_name_s` 是否已无引用无法在我这一侧核实

- `ReleaseIdentity.kt:36` 的 `"$PRIVATE_PACKAGE.debug"`：`build.gradle` 只在 `release` 加 `.release`、在 `debug` 加 `.debug`（`:102`、`:118`），不存在同时带两个后缀的变体，故该分支恒不可达；`NORMAL` 侧的对应分支是**可达且必要**的（`io.legado.app.tjf.debug`）。
- `values/strings.xml:5-6`、`values-zh/strings.xml:4-5` 的 `app_name_a`/`app_name_s` 按 `10-execution.md:87` 称已无引用；**我无法核实**（它们在 `app_name` 占位符改由身份决定后失去引用，但确认“无引用”需要搜索白名单外源码，我按边界没有搜）。若确有白名单外代码动态引用它们，本轮改动会让“显示名随身份”与旧引用并存。
- 建议：不阻塞；白名单放开后统一清理死分支与死资源。

---

## 3. 我检查了但未发现问题的方面（含边界声明）

**已核对通过（当前状态下）**

- **约束 1（Gradle 身份 fail-closed）**：`app/build.gradle:39-43` —— 未显式指定默认 `private`、非 `normal|private` 立即抛 `GradleException`（第 41 行，与 D6 声称的行号一致）、CI 无 `sed`（`test.yml` 搜索无命中）。
- **约束 2（显示名与包名分离）**：`build.gradle:101-106` 只改 `applicationIdSuffix` 与 `app_name` 占位符；版本号来自 `defaultConfig`（`:72-73`），签名配置单一（`:54-67`）；`strings.xml:8` / `values-zh:7` 给出 `app_name_private`（`Legado·Privacy` / `阅读·隐私`），与迁移计划 §3.1 的建议一致。包名与显示名之间没有互相推断。
- **约束 3（两轴分离 + 历史兼容）**：`ReleaseIdentity.kt:61-67` 的判定顺序正确（`releaseS/releaseA` 先于 `release`，`private` 先于 `normal`，不存在子串互吞）；`.releaseS/.releaseA` 只映射为 `LEGACY_RELEASES`，与 `NORMAL/PRIVATE` 都不相等，不进入双版本候选；测试 `:215-232`、`:149-174` 覆盖。
- **约束 4（资产层精确选择）**：`AppReleaseInfo.kt:99,117` + `AppUpdateGitHub.kt:62` / `AppUpdateGitee.kt:74` 的 `it.appVariant == checkVariant` 是精确相等，不依赖顺序；`AppReleaseInfoTest.kt:105-147` 覆盖 beta/正式 × 两种资产顺序；`:150-174` 覆盖额外无关 APK 与 `.releaseS`。
- **约束 5（CI 与下游迁移）**：`test.yml:142` 矩阵恒为 `normal/private`；`:191-193` 资产名 `legado_app_<VERSIONL>_<identity>.apk`；`:230` artifact `legado.app.<type>`；`:261-266` 缺任一身份即失败；`:353-356` telegram 改用 `*_private.apk`；`:387-391` gitee 直传已定名资产；`:498` update-storage 取 `legado.app.private`。搜索确认 `test.yml` 内 `releaseS/releaseA` **零命中**，不存在正式发布残留的 `releaseS` 依赖。
- **Manifest 只读核对**：`AndroidManifest.xml:538,546` 的 `readerProvider`/`fileProvider` 都用 `${applicationId}`，两个身份 authorities 天然唯一；固定权限 `io.legado.READ_WRITE`（`:27-29,541-542`）仍是静态声明，我未改动也未发现本轮改动触及它 —— 与 `10-execution.md:85` 的“真机共存仍是 E 的门槛”一致。
- **`AppConst.kt` 身份/通道来源**：`:69-72` 身份只来自包名；`:91-96` 通道只来自签名/DEBUG 且 `isBetaChannel = isBeta && !isOfficial`；`:105` 默认值 `UNKNOWN_OFFICIAL` 是 fail-closed（未知包名不与任何双版本资产相等，不会拿错包）。
- **安全面**：本轮改动未新增命令注入面（CI 新增部分只用 `${{ env.type }}`/`${{ env.VERSIONL }}` 等受控值，`for identity in normal private` 为静态字面量，未把 `head_commit.message` 之类不可信输入内联进 `run:`）；未新增路径拼接（`fromAssetName`/`fromPackageName` 是纯字符串比较，无文件系统操作）；未触及 SQL/WebView/权限放大；未读取或输出密钥内容（`10-execution.md:64` 只记录证书 SHA-256，符合 `00-task.md` 第 7 条的“只记录摘要是否一致”）。
- **改动范围**：会话起始 `git status` 中的业务改动正好等于 C+D 写白名单（8 个已跟踪 + `ReleaseIdentity.kt` + 测试文件），未发现白名单外文件被改；`AndroidManifest.xml` 未被修改（`git status` 无该条目）。

**本轮未检查到的范围（诚实列出，请勿当成已覆盖）**

1. **所有命令类证据**：Bash 在本会话不可用 ⇒ `phase-cd-diff-check`、`phase-cd-business-diff`、`phase-cd-targeted-test`、`phase-cd-status` 全为 `NOT RUN`。`10-execution.md` 的 D0–D7（含 `tests=258/failures=0`、lint 成功、两个 APK 的包名/版本/证书摘要、D6 负例）**我一项都没有复现**，它们是未经验证的主张。lint 的 issue 计数、全量测试的 38 个 XML 我也没有看到原始输出。
2. **基线对比**：无 diff ⇒ 我无法核实“A/B 6 个断言逐字未改”、`test.yml` 是否只有报告所述改动、`维护计划书-后续修复与增强.md` 与 `ai-stage-e-anchor-controlled/05-routing.md` 的改动是否属既有（A/B 报告第 20 行称属既有，我无法独立确认）。
3. **冻结窗口**：`.review-snapshot.json` 的 `after` 为 `null`、`state=running`；我无法自行计算快照哈希，`00-task.md` 要求的“前后快照一致”只能由 runner 判定。
4. **白名单外但与本改动强相关的文件**：`res/values/arrays.xml`、`res/xml/pref_config_other.xml`（设置项 `entryValues` 是否真的是 `default_version`/`beta_release_version`/`beta_releaseS_version`）、`AppConfig.kt`（`updateToVariant` 的默认值）均不在我的可读白名单内 ⇒ `ReleaseIdentity.betaChannelOfSetting`（`ReleaseIdentity.kt:49-53`）的键名**未与真实设置值核对**。缓解：未命中时返回 `null` 并回退到安装包自身默认通道，最坏情况是“通道退回默认”，不会换身份，属 fail-safe。
5. **测试目录内容**：我只读了 `AppReleaseInfoTest.kt`；`app/src/test/java/io/legado/app/help/update/` 下是否有其它残留文件，我没有枚举（枚举会越过白名单）。
6. **`PROJECT.md`/`CURRENT.md`**：按我的理解未被 `00-task.md` 列入 reviewer 可读白名单，我**没有读**，因此没有使用项目自述的构建/测试命令，只用 `06-review-commands.json` 的冻结参数。
7. **E/F 范围**：真机安装/覆盖升级/数据保留、双包共存、`*.releaseS` 旧安装的实际更新行为、GitHub Actions 实跑、Gitee 真实上传、link-E 外部消费方，全部 `NOT RUN`（未授权）。
8. 网络行为（Gitee `/releases/latest` 的真实语义）无法验证 —— F-01 的两种读法我都无法实测，只能指出代码在其中一种下必然失败。

---

## 4. 给 acceptor 的建议动作（按优先级）

1. 在冻结树上重跑 `:app:compileAppDebugKotlin` + `:app:testAppDebugUnitTest`（覆盖 F-03，同时排除白名单外旧 `AppVariant` 常量残留的编译风险），并把原始输出写进 `30-acceptance.md`。
2. 对 F-01 作出显式决定：修（beta 分支改为按 `prerelease` 选 release）或在 30 里登记为“已知限制、Gitee beta 通道可能静默无更新”，二者都不能沉默通过。
3. 对 F-02 明确 link-E 目标文件名的载荷身份变化是否有意，纳入 F 阶段验证。
4. 修正 F-05 的三处事实，并把“reviewer 未能执行任何冻结命令、未能 diff 对比、F-01/F-02 未实测”写进验收的保证降级披露。
