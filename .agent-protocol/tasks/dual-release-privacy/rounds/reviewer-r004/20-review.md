<!-- agent-protocol-binding
round_id: reviewer-r004
task_digest: 7aad1970fe8504e071bf9e4655f67736209fcf5093bb802b81eaa7e22f6d7216
snapshot_digest: 40f10aa52597fef3c6e53c6485da0a21ce784f3cbf61b42e9a963bccea4cdba0
--># dual-release-privacy — C+D 阶段独立复审（第 2 轮，reviewer=claude 只读，route_revision 6 / standard）

## 0. 结论

**通过（有条件）——未发现足以否决本次实现的业务阻塞项；但“C+D 已完成”在当前冻结快照上尚未成立，必须在 `30-acceptance.md` 闭合。**

三条要点：

1. **代码层**：我只读核对了白名单内全部业务文件与协议证据，冻结契约 1–8 在代码与 CI 层面均已落地，未发现身份压平、身份来自设置、`.releaseS` 残留、越界改动或新增安全面；第 1 轮 F-01（Gitee 通道依赖 `/releases/latest`）的修复我独立读代码确认成立。
2. **证据层（本次最主要的发现）**：`10-execution.md` §4 的 D1/D2/D4/D5/D7 结果与**当前冻结树不同源**。硬证据：当前 `AppReleaseInfoTest.kt` 有 **10** 个 `@Test`（第 70/77/92/104/129/149/178/214/234/262 行），而 §1 写“新增 3 个契约测试”（6+3=9）、§4 D1 记 `tests=9`。§9 F-01 新增的第 10 个测试（`giteeChannelSelectionFollowsReleaseMetadataNotListOrder`，第 262–298 行）是 **F-01 修复的产物**，而 D1/D2 的数字都早于它 ⇒ **所有门禁输出都产生于 F-01 功能改动之前**，修复后的树在可读证据里没有任何一方的执行结果。
3. **能力层**：本次调用我没有命令执行工具（只有 Read/Grep/Glob，与 `15-reviewer-raw.md` 记录的 r003 调用参数 `--allowedTools Read,Grep,Glob` 相同），因此 `06-review-commands.json` 冻结的 4 条命令在我这一侧**全部 NOT RUN**；第 1 轮同样如此。也就是说，本任务至今**没有任何一方在冻结树上留下过独立执行的测试输出**（`18-review-probes.md` 只有一条 `git diff --check -- .github/workflows/test.yml`，退出码 0、输出为空，且 diff 类命令看不到 untracked 文件）。`10-execution.md` §4 的 `tests=258 / failures=0`、lint、两个 APK 的包名/版本/证书摘要、D6 负例，**全部仍是 executor 自述，我一项都没有复现**。

因此若 acceptor 无法在冻结树上产出**修复后**的门禁原始输出，本任务不得标 `accepted`；这一限制必须按 standard 档的保证降级如实写进 `30-acceptance.md`。

---

## 1. 授权边界与我实际读到的东西

- 唯一权威入口取 `00-task.md` 顶部“2026-09-20 DSH + Claude 接管任务书”（route_revision 6，standard 档，DSH=controller+executor+acceptor，Claude=只读 reviewer）。我按该节“C+D 读写白名单”取读，未按下方已废止的旧节取读，也**没有**读 `PROJECT.md`/`CURRENT.md`（未列入 reviewer 可读白名单），因此构建/测试命令一律以 `06-review-commands.json` 冻结数组为准。
- 读到的协议证据：`00-task.md`、`05-routing.md`、`06-review-commands.json`、`10-execution.md`、`14-reviewer-prompt.md`、`15-reviewer-raw.md`、`18-review-probes.md`、`20-review.md`、`30-acceptance.md`、`40-window-migration-plan.md`、`rounds/controller-ab-report-10-execution.md`、`发布推送流程书.md`。
- 读到的业务文件（逐字全读）：`ReleaseIdentity.kt`、`AppReleaseInfo.kt`、`AppUpdateGitHub.kt`、`AppUpdateGitee.kt`、`AppConst.kt`、`app/build.gradle`、`.github/workflows/test.yml`、`app/src/main/AndroidManifest.xml`、`app/src/main/res/values/strings.xml`（定向）、`values-zh/strings.xml`（定向）、`AppReleaseInfoTest.kt`。
- **未**做目录遍历、未做跨目录 Grep；所有 Grep 都限定在单个白名单文件路径上。

`20-review.md` 当前只有 3 行（“SUPERSEDED / 上一轮证据已归档到 `rounds/reviewer-r003`”）；上一轮的完整原文我从 `15-reviewer-raw.md` 的 `result` 字段读到（这正是 r003 的第 1 轮结论），F-01…F-07 的编号与处置在 `10-execution.md` §9 对得上。

---

## 2. 我实际执行的核对动作与输出

### 2.1 独立算术与交叉一致性（我自己的推导，不采信报告）

- **资产名 ↔ 版本自洽**：`AppReleaseInfo.kt:16` `name.split("_").getOrNull(2)?.dropLast(2)`。CI `VERSIONL=3.%y.%m%d%H%M`（`test.yml:50`）→ `legado_app_3.<yy>.<MMddHHMM>_<identity>.apk` → split[2]=`3.2609201623` → dropLast(2)=`3.26092016`=`VERSION`=`defaultConfig.versionName`（`build.gradle:21,73`）✓ 客户端比较基准等同安装包 versionName ✓。
- **历史命名兼容**：`发布推送流程书.md:110` 记载旧资产形如 `legado_测试版_3.YY.MMDDHHMM_release.apk`。按同一 split 规则，split[2] 仍是 VERSIONL ✓ 版本解析兼容；`fromAssetName` 对该名命中 `release` → PRIVATE ✓，旧 `_releaseS` 名先命中 `releaseS` → LEGACY_RELEASES ✓（判定顺序正确，无子串互吞）。
- **`AppConst.appInfo` 的可达性**：`build.gradle:112-122` debug 只加 `.debug`、`:101-106` release 只加 `.release`，不存在双后缀变体 ⇒ `ReleaseIdentity.kt:36` 的 `"$PRIVATE_PACKAGE.debug"` 恒不可达（与 §7 第 5 条 F-07 的披露一致），而 `"$NORMAL_PACKAGE.debug"` 可达且必要 ✓。
- **本地 versionCode/versionName 与 D7 自述吻合**：本地兜底 `yy*1000000+MM*10000+dd*100+HH` = 26*1e6+9*1e4+20*100+16 = **26092016**、`3.26.092016`，与 §4 D7 记录的 `versionCode=26092016 / versionName=3.26.092016` 一致 ✓（这只能说明 D7 的数值**自洽**，不能证明它被跑过）。
- **报告 §7 第 6 条的自我更正成立**：`%y.%m%d%H` 与 `yy.MMddHH` 字段、宽度、补零完全相同，渲染结果一致 ✓（原“本地与 CI 不同”确系事实错误，已更正）。
- **`AppVariant.of` 不会抛**：`AppReleaseInfo.kt:43-44` 的 `entries.first{}` 覆盖 8 个组合（`:27-37`），不存在取不到值的输入 ✓。
- **§1 文件计数自洽**：会话起始 git status 的业务改动正好 10 个文件（8 个已跟踪 + `ReleaseIdentity.kt` + 测试目录），与 §2 表格（9 行/10 文件）及 §1 的“10 个（新增 2 个）”一致 ✓。

### 2.2 单文件定向检索（含结果）

- `test.yml` 匹配 `releaseS|releaseA|sed |app_name|applicationIdSuffix|RELEASE_IDENTITY|legado\.app\.`：**`releaseS`/`releaseA` 零命中**；无 `app_name` 占位符写入、无 `sed` 改包名（唯一疑似命中 446 行是 “refused ” 的子串误报）；`RELEASE_IDENTITY` 只出现在 187 行的构建参数；`legado.app.` 静态字面量只出现在 498 行 update-storage 的 artifact 名 ✓。
- `AppReleaseInfo.kt` 匹配 `BETA_RELEASES|OFFICIAL|UNKNOWN|releaseS|releaseA`：`BETA_RELEASES` 已彻底不存在，只剩新枚举名与 32 行注释 ✓。
- `AppConst.kt` 匹配 `BETA_RELEASES|OFFICIAL|UNKNOWN|releaseS|releaseA|contains(`：只剩 `OFFICIAL_SIGNATURE`（30 行）、`isOfficial`（91 行）、`UNKNOWN_OFFICIAL`（105 行），**旧的 `packageName.contains("releaseA"/"releaseS")` 猜身份写法已不存在** ✓。
- `AppUpdateGitee.kt` 同上：无残留旧身份常量，`appVariant` 只出现在 28/30/66 行的新逻辑 ✓。
- `AppReleaseInfoTest.kt` 匹配 `@Test`：**10 处**（70/77/92/104/129/149/178/214/234/262）——这是 F-01 的核心硬证据。
- `strings.xml`：`app_name_private` 已同时存在于 `values/strings.xml:8`（`Legado·Privacy`）与 `values-zh/strings.xml:7`（`阅读·隐私`）✓；`app_name_a`（`values:5`、`zh:4`）与 `app_name_s`（`values:6`、`zh:5`）仍在 ✓（与 F-07 一致）；`beta_releaseS_version` 只作为**字符串资源**存在于 `values/strings.xml:1257`（`translatable="false"`，文案“正式版”），`values-zh` 1246–1249 行另有 `default_version=当前 / official_version=正式版 / beta_release_version=测试版 / beta_releaseA_version=共存版` ✓。

### 2.3 边界/安全抽查

- `AndroidManifest.xml:35` `android:label="${app_name}"` 确实使用占位符 ⇒ `build.gradle:103,105,116` 的 `manifestPlaceholders` 生效路径成立；`:538,546` 两个 provider 都用 `${applicationId}` ⇒ 双身份 authorities 天然唯一；`:27-29,541-542` 的固定权限 `io.legado.READ_WRITE`（protectionLevel normal）本轮未被触碰（会话起始 git status 无该文件），与 §7 第 3 条一致。
- 越界检查：参见 §4 的“未发现”。

---

## 3. 发现

### F-01（中｜证据缺口，收口前必须处理）：§4 门禁表与冻结树不同源，F-01 修复后没有任何执行证据

- 位置：`10-execution.md:30`（“新增 3 个契约测试”）、`:52`（D1 `tests=9`）、`:53`（D2 `tests=258`）、`:55-56`（D4/D5 构建）、`:58`（D7）；对照 `AppReleaseInfoTest.kt:262-298`（第 10 个 `@Test`）与 `10-execution.md:107`（F-01 已修 + 新增该测试）。
- 具体失败场景：acceptor 若原样继承 §4，会以“9 项定向测试全绿、258 项全量全绿、双 release 构建成功”为 C+D 完成判据；而这些数字对应的是 **F-01 修复之前**的树。冻结树上的真实状态是：10 项定向测试、全量应为 259 项、`AppUpdateGitee.getLatestRelease` 与 `AppReleaseInfo.firstReleaseOfChannel` 属于**修复后未被任何一方执行过**的生产改动（功能改动 ⇒ 按 `MODES.md` 必须重跑门禁，§9 也只做了“重新拉起 reviewer”）。
- 建议：`30-acceptance.md` 在冻结树上重跑并以原始输出落盘（定向 `AppReleaseInfoTest` 应见 **10** 项、全量单测、`:app:compileAppDebugKotlin`、`:app:lintAppDebug`、normal/private 两次 release 构建），并把 §4 的 D1/D2 数字明确改标为“F-01 修复前”或替换为新记录；不得两者混用。

### F-02（中｜证据缺口 + 表述不当）：`:app:compileAppDebugKotlin` 缺失，且 §9 用过去时把闭环指向一个尚无该内容的文件

- 位置：`10-execution.md:109`（F-03 处置：“**已补**：acceptor 在冻结树上显式重跑该门禁并记录原始输出（见 `30-acceptance.md`）”）；§4 表（49–57 行）**没有**该门禁行；§6 NOT RUN 清单（76–80 行）也**没有**把它列为 NOT RUN；`30-acceptance.md` 当前正文仍是旧映射的 SUPERSEDED 验收（`:1-3`、`:9`、`:11`），其中 11 行的 `BUILD SUCCESSFUL in 4m 54s` 与 `AppReleaseInfoTest 7/7` 是**废止映射的旧代码**上的运行，不能挪用到本树。
- 具体失败场景：`00-task.md:28`（约束 6）明确要求“运行：`git diff --check`、定向 JVM、`:app:compileAppDebugKotlin`、全量、lint、两次 release 构建”，并要求未运行项写 `NOT RUN`。该项现在是“既没结果、也没 NOT RUN、还宣称已闭环”，是典型的“该写而没写”。缓解事实：D2 的 `:app:testAppDebugUnitTest` 会编译 debug 变体主源码，故“白名单外是否残留旧 `AppVariant.OFFICIAL/BETA_RELEASES` 引用”这一风险在 D2 通过的前提下已被传递覆盖（但 D2 本身也是修复前的自述）。
- 建议：acceptor 在 `30-acceptance.md` 写原始命令、退出码与实际输出后，F-01/F-02 一并闭合；若无法运行，必须写 `NOT RUN` 并据此降级结论。

### F-03（中低｜未核对假设）：三个决定行为的白名单外输入无法核对

- 位置：`ReleaseIdentity.kt:49-53`（依赖设置项键名）、`AppUpdateGitHub.kt:29` / `AppUpdateGitee.kt:29`（依赖 `AppConfig.updateToVariant` 的类型与取值）、`AppConst.kt:69-72`（依赖包名集合）。
- 具体失败场景：若 `res/values/arrays.xml` 的 `default_app_variant_value` 的 entryValues **不是** `default_version/official_version/beta_release_version/beta_releaseA_version/beta_releaseS_version`（例如实际是索引或别的键），则 `betaChannelOfSetting` 全部返回 `null` ⇒ 用户在“检查更新查找版本”里选“测试版/正式版”**静默无效**，回退到安装包默认通道（本机 release 证书非官方/beta 签名 ⇒ 默认正式通道，见 §7 第 4 条）。本轮在无 diff、无网络、无命令的前提下**无法核实**；`res/values/arrays.xml`、`res/xml/pref_config_other.xml`、`AppConfig.kt` 均在白名单外，我按边界没有读。
- 严重度限定的理由（不是阻塞项）：最坏后果是“通道退回默认”，**永不换身份**（`fromPackageName` 为白名单 fail-closed；未命中返回 `null` 而非猜测），属 fail-safe。
- 建议：E 阶段真机复核一次设置项与实际通道的对应关系；或由 controller 单独授权后把这三个文件纳入一次性核对。

### F-04（低｜测试覆盖不对称）：Gitee 侧只断言“可区分”，不像 GitHub 侧那样断言“精确命中”

- 位置：`AppReleaseInfoTest.kt:130-147` vs `:105-127`。GitHub 侧有 `pickLikeCurrentConsumer`（`:63-68`）真跑“按 `checkVariant` 过滤”，Gitee 侧只断言 `assets[0].appVariant != assets[1].appVariant`。
- 具体失败场景：若将来 Gitee 的资产解析与 GitHub 分叉（例如给 `GiteeAsset.assetToAppReleaseInfo` 单独加分支），`giteeSelectionMustBeIdentityExactAndOrderIndependent` 仍会绿，而真实消费端可能取错资产。当前两处解析共用 `ReleaseIdentity.fromAssetName` + `AppVariant.of`，故实际风险低。
- 建议：把 Gitee 侧改成与 GitHub 同形（对每个 `checkVariant` 断言 `pickLikeCurrentConsumer` 命中的 `name`），一行级改动即可消除不对称。

### F-05（低｜健壮性）：Gitee 列表固定 `per_page=5` 且无分页

- 位置：`AppUpdateGitee.kt:37-38`、`:53`。F-01 修复后 beta 与正式通道都用这一页。
- 具体失败场景：Gitee 的正式 release（tag=版本号）只增不删，beta release 每次 beta 发布才重建。若某段时间连续出现 ≥5 次“改了 updateLog 的正式发布”而中间没有 beta 发布，beta release 落到第 6 条之后 ⇒ beta 通道用户拿到“已是最新版本”，尽管其 APK 仍在仓库里。概率低但机制真实。
- 建议：`per_page=30`（或页面翻到底）即可；或在 `30-acceptance.md` 记为“已知限制”。

### F-06（低｜简化/同步风险）：同一条契约规则在 GitHub/Gitee 两处复制

- 位置：`AppUpdateGitHub.kt:26-31` 与 `AppUpdateGitee.kt:26-31`（连注释都相同）。
- 风险场景：A/B 阶段暴露的正是“两个消费端漂移”（Gitee 独有身份锁）。现在两处逐字一致，但没有任何测试绑定它们；下次改动只改一处就会静默分叉。
- 建议：抽一个 `fun installedCheckVariant(): AppVariant`（放 `ReleaseIdentity.kt` 或更新模块内）供两处调用；属纯收敛，不新增抽象层。

### F-07（低｜死代码/空操作，F-07 原项已披露，我独立确认并补一条）

- `AppReleaseInfo.kt:127`：`GiteeAsset` 生成的 `AppReleaseInfo.createdAt` 恒为 `0`，因此 `AppUpdateGitee.kt:55` 的 `sortedByDescending { it.createdAt }` 对 Gitee **是空操作**，实际顺序 = API 返回顺序。当前每个 Gitee release 只会上传同一批次的新命名资产，没有可错选的混合资产，故不构成缺陷；但“已按创建时间排序”这个隐含保证不成立，建议删掉该行或改注释说明。
- `ReleaseIdentity.kt:36` `"$PRIVATE_PACKAGE.debug"` 不可达（我按 `build.gradle:101-122` 独立确认）；`values:5-6` / `zh:4-5` 的 `app_name_a`/`app_name_s` 仍在；`-PRELEASE_IDENTITY=private` 对 **debug 变体无效果**（debug 恒为 `app_name` + `.tjf.debug`，即 NORMAL 身份）——若有人指望用 debug 包验证 private 身份，会得到错误的身份预期。
- 建议：白名单放开后统一清理；本条不阻塞。

### F-08（信息｜已披露残留，我确认为事实但不能核对“旧值”）

- `test.yml:498`（artifact `legado.app.private`）+ `:524`（`cp ./apk-private/*.apk link-E-repo/sigma/leagado_Sigma_正式版.apk`）：我可以确认**现在**上传到 link-E 的是 `.release` 包名的 private APK，而目标文件名不变。**旧 job 取的是哪一支我无法核实**（无 diff），因此“该文件的包名身份是否已从 `.releaseS` 变为 `.release`”只能按 §9 F-02 的“用户已裁决、F 阶段显性核对”处理 —— 我同意这一处置，并建议把它写成 F 阶段的显式契约检查项，而不是隐性变化。
- `10-execution.md:87`（§7 第 4 条）：本机 release 证书 `bea423a4…` 既不等于 `OFFICIAL_SIGNATURE`（`AppConst.kt:30-31`）也不等于 `BETA_SIGNATURE`（`:32-33`）⇒ 本机/自签 release 默认真走**正式通道**（`AppConst.kt:91-96`）。结合 `发布推送流程书.md:12-13` 记载的日常发布路径是 **beta 预发布**：若用户的设置是“当前”，应用内检查更新会读 `/releases/latest`，在两次正式发布之间只会得到“已是最新版本”。我无法用 diff 证明“判定逻辑与改动前一致”（§7 第 4 条的主张），故列为 **E 阶段必核项**：先记录真机已装 `.release` 的证书摘要与设置项，再判断默认通道与 beta 标签的关系。

### F-09（信息｜探针覆盖有限）

- `18-review-probes.md:11-19` 只记录了一条 `git diff --check -- .github/workflows/test.yml`（退出码 0、耗时 0.14s、正文为空），且 `06-review-commands.json:13-18` 的 `phase-cd-business-diff`、`:20-25` 的 `phase-cd-targeted-test`、`:27-32` 的 `phase-cd-status` 都没有任何探针输出。唯一含“测试是否转绿”信息的通道因此完全依赖 executor 自述。
- 另：该探针与 F-04（旧编号）指出的问题同源 —— `git diff` 看不到 untracked 的 `ReleaseIdentity.kt` 与整个测试目录，而这两个文件正是本次改动的主体与唯一回归保护。

---

## 4. 已核对但未发现问题的方面（按任务书约束逐条）

- **约束 1（Gradle fail-closed）**：`build.gradle:39-43` —— 未指定默认 `private`、非 `normal|private` 立即抛 `GradleException`（第 41 行）；`test.yml` 内无 `sed` 改包名；187 行用 `-PRELEASE_IDENTITY="${{ env.type }}"` 注入。**通过**。
- **约束 2（显示名与包名分离、共享版本/源码/签名）**：`build.gradle:101-106` 只改 `applicationIdSuffix` 与 `app_name` 占位符；`defaultConfig.applicationId=io.legado.app.tjf`（`:69`）、版本（`:72-73`）、单一 `signingConfigs.myConfig`（`:54-67`）均未按身份分叉；`AndroidManifest.xml:35` 使用占位符；`app_name_private` 两份 strings 都有。**通过**。
- **约束 3（两轴分离 + 历史兼容）**：`ReleaseIdentity.kt:14-18,61-67`、`AppReleaseInfo.kt:25-45`；`.releaseS/.releaseA` 只映射 `LEGACY_RELEASES`，与 NORMAL/PRIVATE 均不相等；`firstReleaseOfChannel`（`:82-83`）按 release 元数据选通道而非列表顺序。**通过**（F-01 修复我逐行读过：`AppUpdateGitee.kt:34-56` 两条通道统一取列表、选不到即抛“已是最新版本”，不再可能拿另一支静默判最新）。
- **约束 4（资产层精确选择）**：`AppUpdateGitHub.kt:62` / `AppUpdateGitee.kt:66` 均为 `it.appVariant == checkVariant` 精确相等；`AppReleaseInfo.kt:99,117` 与 `AppUpdate*.checkVariant`（`:26-31`）同源。**通过**（覆盖不对称见 F-04）。
- **约束 5（CI 与下游迁移）**：`test.yml:142` 矩阵恒为 `normal/private`；`:187` 注入身份；`:191-193` 资产名 `legado_app_<VERSIONL>_<identity>.apk`；`:230` artifact `legado.${{ env.product }}.${{ env.type }}`（product 为静态 `app`，与 `:498` 的 `legado.app.private` 一致）；`:261-266` 缺任一身份即失败（该守卫在正式与 beta 两条路径上都先于发布执行）；`:353-356` telegram 改用 `*_private.apk`；`:387-391` gitee 直传已定名资产；`:498` update-storage 取 private。`releaseS`/`releaseA` 在 `test.yml` 内**零命中**。**通过**。
- **约束 6（A/B 红测转绿 + 门禁）**：转绿在**文件层**可确认（10 个测试 + 新实现），但门禁证据不同源 —— 见 **F-01/F-02**。
- **约束 7（包名/版本/证书摘要）**：§4 D7 的记录口径正确（只记证书 SHA-256、不输出密钥内容），数值自洽（见 §2.1），但**未被我复现**。口径**通过**，数值**未验证**。
- **约束 8（保留用户既有改动、AI 封存）**：会话起始 git status 的业务改动恰好等于 C+D 业务写白名单（8 个已跟踪 + 2 个新增），业务 diff 不含 AI 源码/设置；`AndroidManifest.xml` 未在改动列表内。`维护计划书`、AI 任务路由、`bookshelf.json`、`.claude/` 属用户既有/他任务（AI 路由差异为 EOF 换行，A/B 报告第 20 行已说明），本任务未吸收。**通过（就 diff 路径列表而言；逐字 diff 我无法生成）**。
- **安全面（未发现新增问题）**：`test.yml` 新增/改动片段只内联 `${{ env.type }}`/`${{ env.VERSIONL }}`（来自 `:142` 的静态字面量矩阵）与固定字面量 `normal private`，未把 `github.event.*` 之类不可信输入带进 `run:`；无新增路径拼接或文件系统操作（`fromAssetName`/`fromPackageName` 是纯字符串比较）；无 SQL/WebView/权限放大；权限 `io.legado.READ_WRITE` 未被本轮触碰（真机共存仍是 E 的门槛，与 §7 第 3 条一致）；未读取/输出密钥内容。
- **越界（未发现）**：业务改动全部落在 `00-task.md:17` 的 C+D 业务写白名单内；`ReleaseIdentity.kt` 是纯模型文件（无 Android 依赖），符合“如确需纯模型拆分，只额外允许新增”的条件；未发现白名单外文件被改；未发现 `.claude/`、`bookshelf.json`、AI 任务或《维护计划书》内容被本任务吸收。

---

## 5. 我未检查到的范围（NOT RUN，请勿当成已覆盖）

1. **`06-review-commands.json` 全部 4 条命令**：`phase-cd-diff-check`、`phase-cd-business-diff`、`phase-cd-targeted-test`、`phase-cd-status` —— 本次调用没有命令执行工具（只有 Read/Grep/Glob），**全部 NOT RUN**。
2. **`10-execution.md` §4 的全部结果**（D0–D7）：`git diff --check` 退出码、定向 9/10 项、全量 38 XML/258 项、lint 成功、两次 release 构建、D6 负例、两 APK 的 29 位证书摘要 —— **一项都没有被我复现**。lint 的 issue 计数与原始报告文本我没有看到。
3. **基线对比能力**：无 diff ⇒ 我无法核实 §9 F-05/F-06 声称的“A/B 6 个断言逐字未改”（我只能确认当前文件**恰好含**这 6 个测试名 + 3 个 C 阶段测试 + 1 个 F-01 测试）、无法核实 `test.yml`/`AppConst.kt`/`AppUpdateGitee.kt` 除报告所述之外是否还有其它改动、无法核实 §7 第 4 条“判定逻辑与改动前一致”。
4. **白名单外但强相关**：`res/values/arrays.xml`、`res/xml/pref_config_other.xml`（设置项 entryValues）、`AppConfig.kt`（`updateToVariant` 的类型/默认值）、`AppUpdate.kt`（两个实现之外的消费端与更新源选择）、其它任何可能引用旧 `AppVariant` 常量的源码 —— **未读、未搜**（见 F-03；后者只能由编译覆盖）。
5. **测试目录完整性**：我只读了 `AppReleaseInfoTest.kt`，未枚举 `app/src/test/java/io/legado/app/help/update/` 下是否还有其它文件。
6. **`PROJECT.md` / `CURRENT.md`**：未按白名单读取；构建/测试命令只来自 `06-review-commands.json`。
7. **冻结窗口与快照**：`18-review-probes.md:1-9` 的“HEAD/快照 SHA-256/命令清单 SHA-256/工作树 stable/总结 PASS”都是 runner 侧记录，我没有哈希工具，无法自算或复核；我只能声明我没有写任何文件（含未执行任何会改工作树的命令）。
8. **真机与远端**：E/F 全部范围（安装覆盖升级、数据保留、双包共存、应用内检查更新、link-E 消费方、GitHub Actions 实跑、Gitee 真实上传）——未授权、**NOT RUN**。
9. **Gitee API 真实语义**：`/releases?per_page=5&direction=desc` 是否真返回 `prerelease` 与 `assets`、排序字段是否为 `created_at` —— 无网络、无命令，**未能验证**（F-01 修复的两条读法我都无法实测，只能确认纯函数选支逻辑正确；若列表不返回 `assets`，`AppReleaseInfo.kt:70` 会抛“获取新版本出错”，属 E/F 必核项）。
10. **`git status` 的当前值**：我用的是会话起始快照（harness 提供），不是实时值。

**轮次提示**：按 `05-routing.md` route_revision 4 的“新一轮复审从未来 C 阶段重新计数”，r003 为 C 阶段第 1 轮、本轮为第 2 轮，已达 `00-task.md` 的“最大复审轮次 2”。若本轮之后还需修改生产代码（含为 F-01/F-02 补跑后发现的任何问题），需要 controller 显式重冻结并提高轮次上限，不能在本轮额度内继续。

---

## 6. 给 acceptor 的收口清单（按优先级）

1. 在冻结树上重跑并原样落盘：`:app:compileAppDebugKotlin`、定向 `AppReleaseInfoTest`（**应见 10 项**）、全量 `:app:testAppDebugUnitTest`（**应见 259 项**）、`:app:lintAppDebug`、`assembleAppRelease -PRELEASE_IDENTITY=normal|private`、以及 `aapt2/apksigner` 摘要比对；命令、退出码、原始输出缺一不可（闭合 F-01/F-02，并顺带覆盖“白名单外旧常量引用是否清零”）。
2. `10-execution.md` §4 的 D1/D2 数字必须改标为“F-01 修复前”或替换为新记录，避免验收继承过期数字。
3. 明确写出保证降级：mode `dsh-claude` 下 DSH 自执行自验收、reviewer 两轮均无命令执行能力、冻结命令四次全部 NOT RUN、F-01 修复后的树在本次复审前无任何执行证据。
4. 把 F-08 两条（link-E 载荷包名身份、真机默认通道 vs beta 标签）写进 F/E 的显式验证项，不要作为隐性变化带过。
5. 对 F-03/F-05/F-06/F-07 作出“接受 / 延后 / 修”的显式裁决并记录，不要沉默通过。
