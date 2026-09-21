# dual-release-privacy — 发布同时生成普通版与隐私系统共存版

状态：`rework`

## 2026-09-21 Codex 发布级验收接管（当前唯一入口，route_revision 7）

- 用户明确要求对已完成 A-D 开展验收，并希望达到可发布程度；现役模式恢复为 `triad` / high：Codex=controller+acceptor、DSH=executor、Claude=只读 reviewer。旧 `dsh-claude` 的 C+D 实现、复审和自验收均保留为输入证据，但不直接继承其最终判定。
- 用户再次确认手机上有多个“阅读”，其中现有包 `io.legado.app.tjf.release` 才是要延续的隐私版。真机识别只按精确包名、版本和证书，不按显示名称/图标猜测；不得对其它“阅读”包执行安装、卸载、清数据或配置变更。新 normal 版只认 `io.legado.app.tjf`。
- 本轮授权 E 真机迁移验收与 F 发布前验证；允许只读设备盘点、在明确设备 `10CE5P1M1Z001P9` 上对已确认同包名同签名的 private 候选执行 `adb install -r`、安装 normal 候选、启动两包并核对非敏感可见行为。禁止卸载、清数据、读取/外发真实书源正文或凭据；签名不一致、覆盖失败或权限/provider 冲突立即停止。
- 允许重跑 `git diff --check`、定向/全量 JVM、compile、lint、normal/private release 构建、APK package/version/cert/authorities 静态检查，以及对 workflow 的本地静态/语法验证。允许读取本任务既有白名单、Manifest、更新设置的 `arrays.xml` / `pref_config_other.xml`，仅为闭合旧 reviewer 指出的实际键名缺口；本轮不改这些文件。
- 本轮不授权提交、推送、切分支、GitHub Actions 实跑、创建/更新 Release、Gitee/link-E/Telegram 外部写入。真实 CI 双 job 与线上双资产只能作为发布后门禁；在获得单独的提交/推送/发布授权前，最高结论是 `release candidate ready`，不得冒充已发布或线上验证完成。
- Codex 将把独立复跑、真机证据、未运行项和最终判定写入 `30-acceptance.md`；若业务代码需要修正，先退回 executor 并重新独立复审，不由 acceptor 静默修改生产代码。
- route_revision 7 为扩展后的 E/F 发布级验收重新开放 1 轮独立 reviewer；旧 route_revision 6 的两轮额度只约束已结束的 C+D 自验收，不阻止本轮对新增真机/线上风险面的新鲜复审。reviewer 只读，不得修改业务或协议文件。

## 2026-09-20 DSH + Claude 接管任务书（当前唯一执行入口）

- Codex 当前额度不可用，用户明确要求后续不依赖 Codex。现役模式为 `dsh-claude`、`route_revision=6`、风险档 `standard`：DSH=controller+executor+acceptor，Claude=独立只读 reviewer。
- 这是有意的保证降级：不再满足 high 的独立 acceptor 要求；最终状态仍可在 standard 双代理规则下由 DSH 验收，但必须完整披露 DSH 自执行自验收、Claude 只读复审以及所有 NOT RUN。
- A/B 已完成，权威证据在 `10-execution.md`：定向 JVM 测试可编译并真实运行，6 项中 5 项按预期失败，证明 normal/private 身份压平、beta 通道丢失、GitHub/Gitee 顺序错配和 `.releaseS` 误入候选。
- 当前只授权 C+D：最小实现 normal/private 双身份并完成本地自动化门禁。E 真机安装/迁移、F 发布验证、提交、推送、发布均未授权。
- DSH 新窗口开工顺序：读取根 `AGENTS.md`、协议 `README.md`/`PROJECT.md`/`CURRENT.md`、本任务 `00-task.md`/`05-routing.md`/`06-review-commands.json`/`10-execution.md`/`40-window-migration-plan.md`，再执行 `git status -sb`、`git log --oneline -3` 与授权路径 diff；旧 reviewer/acceptance 结论已 superseded，不得继承。
- DSH 完成实现和 `10-execution.md` 后，由当前 DSH controller 执行 `agent-protocol role reviewer -Entry dsh` 拉起 Claude；Claude 只写 `20-review.md`，DSH 处理 findings、重跑关键门禁并写 `30-acceptance.md`。

### C+D 读写白名单

- 可读：本任务全部协议证据、`发布推送流程书.md`、`.github/workflows/test.yml`、`app/build.gradle`、`app/src/main/AndroidManifest.xml`、`app/src/main/res/values/strings.xml`、`app/src/main/res/values-zh/strings.xml`、`AppReleaseInfo.kt`、`AppUpdateGitHub.kt`、`AppUpdateGitee.kt`、`AppConst.kt`、`AppReleaseInfoTest.kt`，以及 Gradle/Android 工具产生的脱敏构建输出。
- 业务写白名单：`.github/workflows/test.yml`、`app/build.gradle`、`app/src/main/res/values/strings.xml`、`app/src/main/res/values-zh/strings.xml`、`app/src/main/java/io/legado/app/help/update/AppReleaseInfo.kt`、`app/src/main/java/io/legado/app/help/update/AppUpdateGitHub.kt`、`app/src/main/java/io/legado/app/help/update/AppUpdateGitee.kt`、`app/src/main/java/io/legado/app/constant/AppConst.kt`、`app/src/test/java/io/legado/app/help/update/AppReleaseInfoTest.kt`；如确需纯模型拆分，只额外允许新增 `app/src/main/java/io/legado/app/help/update/ReleaseIdentity.kt`。
- 证据写白名单：本任务 `10-execution.md`、`20-review.md`、`30-acceptance.md` 及 runner 自动生成的 prompt/raw/probe/rounds 文件；各角色只写自己的证据文件。
- `AndroidManifest.xml` 当前只读：先验证 `${applicationId}.readerProvider` / `${applicationId}.fileProvider` 随包名隔离；若固定权限 `io.legado.READ_WRITE` 必须修改，立即停止并留给 E 真机兼容审查。

### C+D 实现与验收约束

1. Gradle 使用显式、fail-closed 的 `RELEASE_IDENTITY=normal|private`；private 保持 `.release`，normal 不加后缀；未显式指定的本地 release 默认仍为 private，未知值立即失败。CI 不再用 `sed` 生成 `.releaseS`。
2. 显示名称明确区分 normal/private，但不得影响包名；两个版本共享版本号、功能源码和签名配置。
3. 发布通道与资产身份分轴；新资产使用 `normal`/`private` 标识，解析时只兼容历史 `release -> private`；`.releaseS` 不属于新双版本候选。
4. GitHub/Gitee 必须在 beta/正式元数据、资产顺序互换和额外 APK 场景下精确选择自身资产；不得依赖列表顺序或把所有正式资产压成 `OFFICIAL`。
5. CI matrix、artifact、Release 文件名及同文件内 update-storage/Gitee/Telegram 等下游全部迁移到 normal/private；不得残留正式发布 `releaseS` 依赖。
6. 先让 A/B 红测转绿，再运行：`git diff --check`、定向 JVM、`:app:compileAppDebugKotlin`、全量 `:app:testAppDebugUnitTest`、`:app:lintAppDebug`、normal/private 两次 release 构建；运行前设置 `GRADLE_USER_HOME=D:\gradle_home`。
7. 用 `apkanalyzer` 或 `aapt dump badging` 核对两个 APK 的 package/version，用 `apksigner verify --print-certs` 只记录证书摘要是否一致，不读取或输出密钥内容；工具不可用必须写 `NOT RUN`。
8. 保留用户既有/其它任务改动，不还原、不吸收 `.claude/`、`bookshelf.json`、AI 任务 EOF 差异或《维护计划书》内容；AI 功能继续封存。

### 当前停止条件

- 需要修改白名单外文件、Manifest 固定权限、数据库/用户数据格式、签名/Secrets、AI 功能或发布目标；
- normal/private 任一包名、版本或签名无法满足迁移计划，或自动门禁无法稳定复现；
- 需要安装 APK、卸载/清数据、读取真实书源/正文/凭据、提交、推送或发布；
- Claude reviewer 不可用，或 reviewer 冻结窗口发生漂移。

## 2026-09-20 A/B 阶段重新冻结（历史范围，已完成）

- 当前只执行 `40-window-migration-plan.md` 的 A、B 阶段：恢复/分类旧 diff，并用纯 JVM 测试留下可复现红测；C–F 均未授权。
- A/B 当时为 `route_revision=4`：controller=codex、executor=dsh、reviewer=claude、acceptor=codex；当前已由上方 route_revision 6 接管段覆盖。
- 本节覆盖下方旧任务书中的目标、完成条件、白名单和轮次描述；旧内容仅作迁移历史，不再授权 `.release/releaseS` 方案继续前进。
- 本阶段产品契约：`io.legado.app.tjf.release -> private`，`io.legado.app.tjf -> normal`；`.releaseS` 只保留历史测试语义，不属于正式双版本选择集。
- 旧未提交 diff 分类：`.github/workflows/test.yml` 必须在 C 阶段重写；`AppReleaseInfo.kt` 的 `BETA_RELEASES.isBeta()` 补丁必须在 C 阶段按“身份/通道分离”重写；旧 `AppReleaseInfoTest.kt` 仅资产构造/解析辅助可复用，旧身份断言必须在 B 阶段替换。
- executor 本阶段可读：根 `AGENTS.md`、协议 `README.md`/`PROJECT.md`/`CURRENT.md`、本任务 `00-task.md`/`05-routing.md`/`06-review-commands.json`/`10-execution.md`/`20-review.md`/`30-acceptance.md`/`40-window-migration-plan.md`，以及 `.github/workflows/test.yml`、`app/build.gradle`、`app/src/main/AndroidManifest.xml`、`AppReleaseInfo.kt`、`AppUpdateGitHub.kt`、`AppUpdateGitee.kt`、`AppConst.kt`、现有 `AppReleaseInfoTest.kt`。
- executor 本阶段只可改：`app/src/test/java/io/legado/app/help/update/AppReleaseInfoTest.kt` 与本任务 `10-execution.md`。生产源码、Gradle、Manifest、CI、资源文件均只读。
- B 阶段测试必须：冻结上述 package identity → asset identity 映射；证明当前正式 Release 会把 normal/private 资产身份压平；覆盖 normal/private 在 beta 与正式元数据中只能选择自身 APK；覆盖资产顺序互换、额外无关 APK；覆盖 `.releaseS` 不进入正式双版本候选集。
- 红测必须实际运行并记录精确命令、失败测试名和关键断言；允许预期断言失败，不接受只写测试不运行。不得为了转绿修改生产代码。
- 旧 `10-execution.md`、`20-review.md`、`30-acceptance.md` 及其结论均标记为 superseded；历史文件/rounds 不删除。A/B 完成后状态仍为 `rework`，由 controller 核对红测与白名单后才能另行授权 C。
- 明确禁止：提交、推送、发布、切分支、安装 APK、卸载/清数据、修改签名/Secrets、读取真实用户数据，以及进入 C 阶段实现。

## 2026-09-20 controller 决策（覆盖下方旧目标中的身份映射）

- 用户确认：当前尚未发布双版本；既有自用版本包名为 `io.legado.app.tjf.release`，今后必须作为“隐私版”延续并支持原地覆盖、保留数据。
- 新普通版使用基础包名 `io.legado.app.tjf`，作为全新的安装链；不得占用 `.releaseS`。
- `.releaseS` 只保留为既有真机/AI 阶段的临时共存测试身份，不进入本次正式双版本发布，也不得被描述为旧版本的升级目标。
- 下方原目标中“普通版 `.release`、隐私系统共存版 `.releaseS`”的映射已被本节废止；此前实现、执行报告、复审和验收仅作为历史证据，不得直接提交或发布。
- 权威迁移与新窗口交接计划见同目录 `40-window-migration-plan.md`。在按新计划完成实现、独立复审、真机保留数据升级与双资产 CI 证据前，任务保持 `rework`，禁止提交、推送和发布。
- 用户另行授权仅修改《维护计划书-后续修复与增强.md》，把“书源优化”登记进待办池；该文档登记不改变本任务的发布实现范围、复审结论或运行行为。

## 目标与非目标

- 目标：让 `push main` 触发的 beta 与正式发布都固定构建并上传两个 APK：普通版（包名后缀 `.release`）和隐私系统共存版（包名后缀 `.releaseS`）。保留现有 `release` / `releaseS` 文件名标识，并修正 `BETA_RELEASES` 未被当作 beta 的既有更新检查缺口，确保共存版能从 beta 标签获取并精确选择自身 APK。
- 非目标：不发布、不提交、不推送、不切分支；不改变签名、版本号、其它应用功能、AI 功能状态或更新源；不修改 `app/build.gradle` 与 Manifest 的产品定义；不处理已冻结的 AI Stage E 任务。

## 实时基线

- 任务风险档：standard
- Git 仓库根：D:/vsproject/legado-E
- 分支：codex/manga-import
- Git HEAD：fd045f69b2bdadcba86d30b525fe1fd244edb9ce
- 修前工作树摘要：`?? .claude/`、`?? bookshelf.json`，以及协议指针变更；均为用户既有或本任务协议文件，不得纳入业务改动。
- 修前快照 SHA-256：bd55b5bda9fa013cb623e383aad13fd238b0fc2fa9e380227bc87a1c763c77d1
- 当前 route_revision：7

## 复审冻结与轮次

- 最大复审轮次：2
- 复审冻结窗口：reviewer 启动至回执完成期间，授权文件、任务书与执行报告不得变化；runner 前后快照不一致时本轮报告失效。
- reviewer 可执行检查：只允许 `06-review-commands.json` 中冻结的参数数组。
- 网络策略：默认禁止；Gradle 仅允许使用本机已有缓存，不授权下载依赖。协议只记录策略，不能把未实现的网络隔离宣称为强制证据。

## 角色与授权

- controller：codex
- executor 可读 / 可改：可读 `.agent-protocol/README.md`、`CURRENT.md`、`PROJECT.md`、本任务 `00-task.md`、`05-routing.md`、`06-review-commands.json`、`10-execution.md`、`20-review.md`、`14-executor-prompt.md`、`rounds/executor-r001/15-executor-raw.md`、`发布推送流程书.md`、`.github/workflows/test.yml`、`app/build.gradle`、`app/src/main/AndroidManifest.xml`、`AppReleaseInfo.kt`、`AppUpdateGitHub.kt`、`AppUpdateGitee.kt`、`AppConst.kt`；只可改 `.github/workflows/test.yml`、`AppReleaseInfo.kt`、新增 `app/src/test/java/io/legado/app/help/update/AppReleaseInfoTest.kt` 与本任务 `10-execution.md`。
- reviewer 可读：本任务 `00-task.md`、`05-routing.md`、`06-review-commands.json`、`10-execution.md`、`14-reviewer-prompt.md`、`18-review-probes.md`、`.github/workflows/test.yml`、`app/build.gradle`、`AppReleaseInfo.kt`、`AppUpdateGitHub.kt`、`AppUpdateGitee.kt`、`AppConst.kt`、`app/src/test/java/io/legado/app/help/update/AppReleaseInfoTest.kt`；只读，不可改项目文件。
- 沙箱提权：Gradle 若因 `D:\gradle_home` 写权限被拒，可对明确的 Gradle 门禁命令逐条申请；不得扩大到提交、推送或发布。
- 写白名单是**硬上限**：白名单内才是授权范围；白名单外即使只改一个注释也属越界，必须先由 controller 扩权并重新冻结。
- 正式文件白名单：`.github/workflows/test.yml`、`app/src/main/java/io/legado/app/help/update/AppReleaseInfo.kt`、`app/src/test/java/io/legado/app/help/update/AppReleaseInfoTest.kt`、`维护计划书-后续修复与增强.md`（仅限登记“书源优化”待办）、`.agent-protocol/tasks/dual-release-privacy/10-execution.md`、`.agent-protocol/tasks/dual-release-privacy/20-review.md`、`.agent-protocol/tasks/dual-release-privacy/30-acceptance.md` 及 runner 生成的本任务回执/探针文件。
- 允许产生的临时目录：系统临时目录、现有 Gradle 构建目录；不得把临时产物混入正式 diff。
- 收尾前必须清理：本任务额外产生且不属于 Gradle 标准构建输出的临时文件。
- acceptor：codex
- 外部目的地与模型：DSH 作为 executor、Claude 作为只读 reviewer；仅允许读取以上白名单中的项目配置/源码，不得读取或外发凭据、签名材料、真实用户内容和日志。
- 明确禁止的数据和操作：`keystore*`、Token/Secrets、真实书源与正文、用户缓存、未脱敏日志；提交、推送、发布、切分支、删除数据、安装 APK、改设备或 App 配置。

## 完成条件

- [ ] CI 的构建矩阵无论更新日志是否变化，均含 `release` 与 `releaseS` 两个类型。
- [ ] 普通版继续使用 `.release`，共存版仅在独立 CI job 中临时替换为 `.releaseS`；产物名继续包含 `release` / `releaseS`，资产解析与消费端仍按变体精确匹配。
- [ ] 先用 JVM 回归测试证明 `BETA_RELEASES` 必须被视为 beta，再做最小修正；GitHub/Gitee 消费端按 `appVariant == checkVariant` 精确选择对应 APK。
- [ ] `git diff --check`、`:app:compileAppDebugKotlin`、`:app:testAppDebugUnitTest`、`:app:lintAppDebug` 通过；未运行项必须写 `NOT RUN`。
- [ ] 独立 reviewer 检查矩阵、文件名、包名和 beta/正式发布上传路径；acceptor 处理全部 findings。
- [ ] AI 功能与其协议任务保持封存，本任务业务 diff 不含任何 AI 源码或设置变更。

## 第三轮 executor 限定动作（复审后证据对账）

- 只允许读取当前工作树、现有测试结果 XML、两轮 reviewer 报告与既有执行回执，并重写 `10-execution.md`，使实际 diff、测试先红后绿证据、越界/超时事实与当前状态一致。
- 不再修改任何业务文件，不再运行 Gradle，不创建项目内临时目录或日志；controller 已在最终代码上独立运行 `:app:compileAppDebugKotlin :app:testAppDebugUnitTest :app:lintAppDebug`，结果 `BUILD SUCCESSFUL in 4m 54s`，该项必须标注为“引用 controller 证据，非 executor 亲跑”。
- 第二轮 DSH 在超时前留下的项目内 `.gradle-home` 与 `build-exec-r1..r4` 临时日志已由 controller 在确认目标路径位于工作区后清理；报告须如实写明第二轮 timeout 与清理责任归属。

## 停止条件

- 需要改变签名、Secrets、应用功能、AI 状态或发布目标；
- 发现 `.releaseS` 需要额外产品代码/权限修正才能安全构建或识别；
- 需要提交、推送或实际创建 Release；
- 当前模式不满足 high 风险任务的独立执行、复审与验收要求。

## 风险档规则

- `light`：只允许文档、注释与不改变运行行为的说明性文件；一轮独立静态复审。触及代码、脚本、依赖、构建、CI、权限、安全、路由、发布或数据格式时必须升级。
- `standard`：普通代码、启发式和配置行为；独立 reviewer、冻结快照、最多两轮。
- `high`：安全、权限、迁移、协议脚本、发布与高影响数据变更；独立 reviewer 与独立 acceptor，关键门禁必须重跑。
