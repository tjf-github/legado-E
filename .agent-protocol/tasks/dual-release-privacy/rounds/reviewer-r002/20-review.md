# dual-release-privacy — 独立复审报告（reviewer，第 2 轮）

> **SUPERSEDED（2026-09-20）**：本报告审查的是已废止的 `.release=普通版 / .releaseS=共存版` 映射，只保留作历史证据，不得用于当前 normal/private 迁移的验收或发布判断。新产品决策与 A/B 冻结范围以 `00-task.md` 顶部和 `40-window-migration-plan.md` 为准。

## 结论

**有阻塞项。**

- **本轮业务改动（两处）逐行看都是正确且克制的**：`test.yml` 的矩阵无条件化、`AppReleaseInfo.kt` 的 `isBeta()` 补入 `BETA_RELEASES`，两者我都做了白名单内的独立静态核对，**未发现会使它们出错的功能性缺陷**（见「反例检索」）。
- **阻塞项 A（证据与工作树直接矛盾，最高严重度）**：被冻结为「执行证据」的 `10-execution.md` 在 `:7` 声明「实际修改：仅 `.github/workflows/test.yml`」、在 `:8` 声明「未触碰：`AppReleaseInfo.kt`、任何 `app/src/**`」、在 `:32` 声明「没有触碰…任何 Kotlin 源码」。但工作树里 `AppReleaseInfo.kt` 已改（`isBeta()` 新增 `BETA_RELEASES` 分支），`app/src/test/java/io/legado/app/help/update/AppReleaseInfoTest.kt` 已新增。**即：报告声称的 diff 与实际 diff 不一致，且不一致的正是本任务的核心逻辑改动。**
- **阻塞项 B（改动缺执行证据 + 授权链断裂）**：本轮两轮 executor 回执都无法为此改动背书 —— `rounds/executor-r002/15-executor-raw.md:50` 是 executor 自己的断言「App 内更新识别逻辑…均未改动」，其 `:33` 的证据摘要 `882c5bda…` 与 `.review-snapshot.json:12` 记录的当前 `10-execution.md` 摘要一致，说明这就是为该改动定稿的那份报告；而 `rounds/executor-r001/15-executor-raw.md:17-18` 显示 r001 捕获 0 字符、退出码 1（EPERM 崩溃），同样没有产出。**没有任何一份执行记录承认或描述了这次 Kotlin 改动。** 任务书 `00-task.md:33` 把写白名单定为硬上限、扩权须「先由 controller 扩权并重新冻结」，而扩权只在 `05-routing.md:26-29` 被描述，`00-task.md:30/31/34` 的冻结白名单未更新。
- **结论的落点**：因阻塞项 A，`10-execution.md` 中**一切**关于 diff 范围与「未触碰」的断言在本轮都不可采信，包括它据此推出的完成条件 1、2、5 的达成判断；这些判断在本轮**无法判定**，直到 controller 用一份与工作树一致的执行记录替代它。

---

## 发现

### 发现 1 — 执行证据自相矛盾：报告声明未触碰的文件已被改，核心改动无执行记录（严重度：阻塞 / 证据与越界）

**位置**
- 主张：`10-execution.md:7`、`:8`、`:32`、`:69`（「本任务业务 diff 只含 `.github/workflows/test.yml`」）
- 实际：`app/src/main/java/io/legado/app/help/update/AppReleaseInfo.kt:26-31`（新增注释块 + `isBeta()` 增加 `BETA_RELEASES`）、新增 `app/src/test/java/io/legado/app/help/update/AppReleaseInfoTest.kt`
- 反向佐证：`rounds/reviewer-r001/20-review.md:20` 记录第 1 轮读到的是「`isBeta()` 只含 `BETA_RELEASE`、`BETA_RELEASEA`，**不含 `BETA_RELEASES`**」；`rounds/executor-r002/15-executor-raw.md:50` 记录 executor 声称「更新识别逻辑…均未改动」

**具体失败场景**
本次实质要修的是「共存版 beta 取不到更新」，而唯一的修复动作就是 `AppReleaseInfo.kt` 的 `isBeta()`，它没有任何执行者记录。复核者若按报告验收，会认为业务 diff 只有一行 YAML，从而**不去检查真正改动产品行为的那个文件**；第 1 轮的「发现 2（消费侧不可读）」也就不可能被正确闭合 —— 第 1 轮明说该文件不可读且判定「不需改动」，现在它却被无声地改了。此外，若采纳者按 `10-execution.md:8` 的清单做「未触碰」核对，会得出与实际相反的结论。

**建议**
1. 不要以当前 `10-execution.md` 作为本轮执行证据；由 controller 用一份与实际工作树一致的报告替代（至少如实列出 `AppReleaseInfo.kt` 与新增测试文件，并写明各自作者与依据）。
2. 同步把 `AppReleaseInfo.kt` 及新测试补入 `00-task.md:30/31/34` 的白名单后再重新冻结；仅凭 `05-routing.md` 里的一段叙述不足以满足任务书自己定的扩权程序。
3. 明确记录该 `isBeta()` 改动由谁做出（r002 回执已否认是 executor）——high 风险任务要求可归属的执行链。

### 发现 2 — 测试无法检出端点选择回归（严重度：中 / 测试覆盖缺口）

**位置**：`app/src/test/java/io/legado/app/help/update/AppReleaseInfoTest.kt:116-132`（`officialReleaseApkIsNotOfferedAsBetaUpdate`）

**具体失败场景**
该用例断言的是**解析器**：`preRelease = false` 时 `Asset.assetToAppReleaseInfo` 把资产映射成 `OFFICIAL`（`AppReleaseInfo.kt:86-91`）。它**从未调用 `isBeta()`**，因此与「beta 端点选择」无关。若有人把 `isBeta()` 再改回不含 `BETA_RELEASES`，这个用例仍然通过 —— 它对本任务的核心修复零鉴别力。注释「正式发布的资产不能出现在 beta 端点结果里」描述的语义该用例并未验证。

**建议**：把端点选择从 `AppUpdateGitHub.getLatestRelease()`（`AppUpdateGitHub.kt:28-33`，依赖 `AppConfig`/`AppConst`/网络，JVM 不可测）抽成一个纯函数（如 `isBetaEndpoint(checkVariant)`），并由测试直接断言；否则请改写注释，明确该用例只断言解析器。

### 发现 3 — 测试重写了消费端逻辑并硬编码版本比较（严重度：低 / 测试保真度）

**位置**：`AppReleaseInfoTest.kt:64-69`（`pickedAsset`，`firstOrNull { it.versionName > "3.0" }`）对比 `AppUpdateGitHub.kt:57-58`（`filter { it.appVariant == checkVariant }.firstOrNull { it.versionName > AppConst.appInfo.versionName }`）

**具体失败场景**：`"3.0"` 是替身阈值。若消费端的版本比较改成 `>=`、或改比 `createdAt`/`versionCode`，本测试依旧通过 —— 「消费端按 `appVariant == checkVariant` 精确选择」这句完成条件在测试里只是被复述，没有被钉住。

**建议**：接受它是镜像测试，但在注释或测试名里写明「镜像，非绑定」；或把选择逻辑同样抽成可测的纯函数后由测试绑定。

### 发现 4 — `isBeta()` 的修复依赖既有 `versionName` 解析，而该解析被测试「钉成事实」（严重度：信息）

**位置**：`AppReleaseInfo.kt:16`（`name.split("_").getOrNull(2)?.dropLast(2)`）、`AppReleaseInfoTest.kt:173-183`

**我核对的结果**：该解析在此仓库内是**正确**的 —— CI 注入 `versionL = 3.%y.%m%d%H%M`（`test.yml:50`）、`version = 3.%y.%m%d%H`（`:49`），构建期 `versionName` 取 `VERSION`（`app/build.gradle:21,65`），故资产名第三段形如 `3.2609192130`，`dropLast(2)` 得 `3.26091921` = `VERSION`，与 `AppConst.appInfo.versionName` 同口径。测试注释与断言也与此一致。

**提示**：任务说明该测试只「钉事实」、不改既有取值方式，这一点成立；但请注意它同时也把该解析固定下来，日后若有人要改版本比较口径，会被这个测试挡住 —— 属有意为之，仅作记录，不构成阻塞。

---

## 我在白名单内实际执行过的核对动作与输出

工具面：**我的运行时只有 Read / Grep / Glob，没有 shell**（与第 1 轮 reviewer 相同）。因此 `06-review-commands.json` 冻结的 `workflow-diff-check`、`git diff`、`git status`、`git rev-parse` **我本人均未执行**（`NOT RUN`），只能引用 runner 探针 `18-review-probes.md:11-19` 的 PASS 记录，并标注为「来自探针，非我执行」。我未使用 Bash、未做目录遍历，未读取白名单外的项目文件。

1. **`test.yml` 全文 545 行**：`:139-141` 为无条件 `echo 'build_matrix={"product":["app"],"type":["release","releaseS"]}' >> $GITHUB_OUTPUT`；`:42` 仍声明该 output；`:146` 仍 `fromJson(needs.prepare.outputs.build_matrix)`；`:145-147` 含 `fail-fast: false`。与 r002 报告主张一致。
2. **包名后缀链路**：`build.gradle:91` `applicationIdSuffix '.release'`，`:92-98` 有 `'.releaseA'`/`'.releaseS'` 分支；全文件 `'.release'` 字面量仅此 1 处 ⇒ `test.yml:188` 的 `sed "s/'.release'/'.releaseS'/"` 只改第 91 行，不误伤。`test.yml:185/187` 的 `typeName` 分支与 `:269-272`、`:361-363`、`:402-405` 的改名链未变，产物名保留 `release`/`releaseS` 标识。
3. **`isBeta()` 影响面（我独立重查，而非采信报告）**：`AppVariant` 在 `app/src/main` 内只被 5 个文件引用（`AppReleaseInfo.kt`、`AppUpdateGitHub.kt`、`AppUpdateGitee.kt`、`AppConst.kt` 及新测试，Grep 全 `app/src/main/**/*.kt`）；`AppUpdateGitHub.kt:28-33` 与 `AppUpdateGitee.kt:29-34` 中 `isBeta()` **只**用于在三元里选 URL。⇒ 该改动的爆炸半径限于端点选择，无第三个消费者。结合 `AppUpdateGitHub.kt:57`（`filter { it.appVariant == checkVariant }`）与 `:59` 的 `versionName > AppConst.appInfo.versionName`，改动方向正确：不修则 `BETA_RELEASES` 走 `/releases/latest`，而正式发布资产在 `AppReleaseInfo.kt:86-91` 一律解析为 `OFFICIAL`，精确匹配必空 ⇒ 恒报「已是最新版本」。Gitee 侧 `:30-34` 两个分支都是同一 URL、且 `GiteeAsset.assetToAppReleaseInfo` 忽略 `preRelease`（`AppReleaseInfo.kt:107-113`），故行为不变，无回归。
4. **新测试可编译性（静态）**：`AppVariant` 引用一致；`Asset`/`GiteeAsset`/`GithubRelease` 构造参数与位置参数调用与 `AppReleaseInfo.kt:36-41,50-55,64-78,98-103` 匹配；Kotlin `2.3.10`（`gradle/libs.versions.toml:3`）支持 `AppVariant.entries`；`junit 4.13.2` 已由 `app/build.gradle:179` 的 `testImplementation(libs.junit)` 提供。**注意：这只证明「看起来能编译」，不等于跑过**（见 NOT RUN）。
5. **版本串口径**：`test.yml:49-50` 与 `app/build.gradle:21,65` 与测试 `AppReleaseInfoTest.kt:34,37` 三者一致（推导见发现 4）。
6. **越界面**：未发现改动触及 `keystore*`、Secrets、真实书源/正文、用户缓存、日志；工作流未改 `permissions:`、未新增 secret 引用、未 echo 任何 secret。
7. **简化面**：两处改动均为「删分支 / 加一个枚举分支」，未引入新抽象、兼容垫片或死代码；`updateLog_updated` 仍被 `test.yml:277,290,299,307,417,509` 消费，未变死输出。

## 反例检索：我检查过、未发现问题的面

- **命令注入 / 插值面**：新 `run` 块（`test.yml:141`）是**纯字面量 echo，不含任何 `${{ }}` 插值**，相比被删除的 `if [ "${{ steps.updateLog.outputs.updated }}" == "yes" ]` 反而**减少**一处表达式→shell 插值点。未发现命令注入、路径穿越、XSS、SQL、凭据外泄或权限放大。
- **YAML 合法性**：单行 echo，引号配对正确，`>> $GITHUB_OUTPUT` 在。r002 用 PyYAML 6.0.3 解析通过（`10-execution.md:38`）；我无解析器，**未独立复现**。
- **矩阵语义**：`prepare.outputs.build_matrix`（`:42`）→ `build.strategy.matrix`（`:146`），`set-matrix` 无条件产出，两分片为 `(app,release)`/`(app,releaseS)`，artifact 名 `legado.app.release`/`legado.app.releaseS` 与 `:514` 按名下载一致。`set-matrix` 无 `if:`，无「被跳过导致 output 为空」的新风险。
- **双资产承载**：`prerelease`(`:268-274`)、`telegram`(`:359-366`，含 `has_plus` 分支)、`gitee`(`:401-407`)、`lanzou`(`:337`) 都是「遍历 `*/*.apk` + 按 `原包名`/`共存` 改名」，无「只取一个 APK」假设。`fail_on_unmatched_files: true`(`:287,317`) 在双资产下仍能匹配。
- **正式发布路径回归**：`update-storage`(`:509` 需 `updated == 'yes'`，`:514` 下载 `legado.app.releaseS`) 在改动前后都运行于曾构建 releaseS 的场景 ⇒ 无回归。

## 我没检查到的范围（诚实清单）

1. **一切命令执行 —— NOT RUN**：无 shell。冻结的 `workflow-diff-check`、`git diff`、`git status`、`git rev-parse`、任何 hash **我本人均未执行**。因此「恰好 1 个 hunk」「相对 HEAD 只差哪些文件」「HEAD 等于 `fd045f69b…`」这些主张我**无法独立证实**。发现 1 的成立依据是**两份白名单内文本的相互矛盾**（`10-execution.md:7-8,32` 与 `AppReleaseInfo.kt:26-31` 的实际内容，加 `rounds/reviewer-r001/20-review.md:20` 对第 1 轮所见内容的记录）—— 这足以证明「报告与工作树不一致」，但**不等于**我能出具 diff 本身。
2. **Gradle 门禁 —— NOT RUN**：`:app:compileAppDebugKotlin`、`:app:testAppDebugUnitTest`、`:app:lintAppDebug` 我无法运行（r002 的报告 `:41-42,53-56` 记载因沙箱提权无审批通道而失败）；新测试**从未被真实执行过**。发现 4 中「`versionName` 与 `AppConst.appInfo.versionName` 同口径」是静态推导，不是运行结果。
3. **GitHub Actions 真实语义 —— NOT RUN**：无网络、不推送、不干跑。「矩阵被 GH 接受、两分片都成功、prerelease 真上传两个文件」全为静态推导。真正有鉴别力的 actionlint / `workflow_dispatch` 干跑未做。
4. **仓库级一致性与冻结窗口**：`18-review-probes.md:4` 的冻结快照摘要 `1434f7b9…` 与 `.review-snapshot.json` 中我这次运行的 `"before"` 记录不同（后者 `"after"` 仍为 `null`，`state: running`）—— 我无法解释该差异，也无法自证「复审窗口内授权文件/任务书/执行报告未变化」（无快照能力）。
5. **白名单外未读**：`app/src/main/res/**`（`@string/app_name_s` 是否存在）、`AndroidManifest.xml`、`发布推送流程书.md`、`.github/scripts/lzy_web.py`、Secrets 取值、第三方镜像/Telegram/Gitee/link-E 消费方行为 —— 均未读。故「beta 发布由 1 个 APK 变 2 个」对下发渠道的影响我无法判定。
6. **发现 1 的归因**：我能证明「该改动无执行记录且与报告矛盾」，**不能**证明它由谁做出（r002 回执只证明 executor 声称未改 Kotlin 源码，不能排除其它写入者）。
