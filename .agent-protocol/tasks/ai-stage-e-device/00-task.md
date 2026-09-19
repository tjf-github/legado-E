# ai-stage-e-device — 阶段E D1：ANCHOR 修复的真机受控复测

状态：`ready`

## 目标与非目标

- 目标：在当前候选（工作区含 `ai-stage-e-anchor` 已验收但**未提交**的 ANCHOR 上下文锚点修复）上做**受控真机复测**：
  1. 临时把 release 的 `applicationIdSuffix` 改为 `.releaseS`，构建共存包 `:app:assembleAppRelease`；
  2. 以 `adb install -r` **保留数据**安装到 `10CE5P1M1Z001P9`（包 `io.legado.app.tjf.releaseS`，禁止卸载或清数据）；
  3. 对 1–2 个**极短章**触发真实处理，只从固定日志字段读取结论，判断真机 `ANCHOR ambiguous + changed` 是否已被上下文锚点消解、能否取得"无需修改"缓存命中；
  4. 构建后**立即还原** `app/build.gradle` 并核对 `git diff -- app/build.gradle` 为空。
- 非目标：
  - **不做发布判定**（D2 发布矩阵另行立任务）；
  - 不修改任何业务源码、测试、Prompt 或缓存版本；
  - 不更换 App 内 AI 配置、目的地或模型；不新增目的地；
  - 不卸载、不清数据、不覆盖其它应用；不提交、不推送、不合并、不发布。

## 外发授权（用户 2026-09-19 明确授权，逐项）

- 目的地 / 模型：**沿用 App 内已配置的 AI 服务与其默认模型**（`AiKeyStore` 中现有配置），不得更换或新增。
- 正文范围：**1–2 个极短章**（优先选本身无错字的短章，便于同时观察"无需修改"路径）。
- 真实请求上限：**3 次**（每个明确假设只触发一次）。
- **分块上限的临时调整（controller 2026-09-19 补充授权，仍在上述信封之内）**：App 内现有 `maxChunkChars = 600`，而两部在架书单章约 5000+ 字，按 600 分块将产生约 10 次请求，**超出"≤3 次"上限**。为在不扩大正文范围的前提下把请求数降到 1–2 次，允许在设置对话框内**临时**把"分块上限"改为 App 自带默认值 `6000`（取值范围 256–6000，属受支持配置）；处理完成后**立即还原为 600** 并在证据中记录前后值。该调整只改变分块粒度，不改变目的地、模型、密钥或正文范围。
- 证据边界：只记录固定脱敏字段（如 `ai-fail code=… anchor=… chunk=N`、`ai-ok`、块号、耗时）；**正文、响应体、异常 message、书名不得进入任何证据文件**。

## 实时基线

- 任务风险档：standard
- Git 仓库根：D:/vsproject/legado-E
- 分支：codex/manga-import
- Git HEAD：17f8e29d84565101d6509be219f127340450faad
- 修前工作树摘要： M .agent-protocol/CURRENT.md |  M app/src/main/java/io/legado/app/help/ai/AiCacheKey.kt |  M app/src/main/java/io/legado/app/help/ai/AiOutputValidator.kt |  M app/src/main/java/io/legado/app/help/ai/AiProtocol.kt |  M app/src/main/java/io/legado/app/help/ai/OpenAiCompatibleProvider.kt |  M app/src/test/java/io/legado/app/help/ai/AiOutputValidatorTest.kt |  M app/src/test/java/io/legado/app/help/ai/OpenAiCompatibleProviderTest.kt
- 修前快照 SHA-256：c84cc3637dae64d8cfd196c7f83e68860cb0d71f88b7dc0795c3b2b2f9500731
- 当前 route_revision：1

## 复审冻结与轮次

- 最大复审轮次：2
- 复审冻结窗口：reviewer 启动至回执完成期间，授权文件、任务书与执行报告不得变化；runner 前后快照不一致时本轮报告失效。
- reviewer 可执行检查：只允许 `06-review-commands.json` 中冻结的参数数组；空数组表示 `NOT RUN`。
- 网络策略：本任务**需要**受控真实外发（见上「外发授权」），范围以该节为准；其它任何网络行为仍禁止。

## 角色与授权

- controller：dsh
- executor 可读 / 可改：读 `00-task.md`、`05-routing.md`、`14-executor-prompt.md`、`.agent-protocol/README.md`、`CURRENT.md`、`PROJECT.md`、`rounds/**` 与上一任务 `../ai-stage-e-anchor/30-acceptance.md`。
- reviewer 可读：`00-task.md`、`05-routing.md`、`06-review-commands.json`、`10-execution.md`、`14-reviewer-prompt.md`、`18-review-probes.md`，以及 `app/build.gradle`（核对临时 suffix 是否已还原）。禁止读取凭据、`keystore*`、`look.ps1`、真实书源/正文、用户缓存、未脱敏日志。
- 沙箱提权：**需要**——`gradlew` 与 `adb` 均需写入会话工作区之外。本任务书预先一次授权：`$env:GRADLE_USER_HOME='D:\gradle_home'` 下的 `gradlew.bat` 调用与 `adb` 对 `10CE5P1M1Z001P9` 的读写，档位 `danger-full-access`；一次一申请，不得批量扩大。
- 正式文件白名单：
  - `app/build.gradle` —— **仅第 91 行** `applicationIdSuffix` 在构建期临时改为 `'.releaseS'`，构建与安装完成后**必须还原**并核对 `git diff` 为空；
  - `app/src/main/AndroidManifest.xml` —— 构建期临时把自定义权限 `io.legado.READ_WRITE` 改为 `io.legado.app.tjf.releaseS.READ_WRITE`（**仅第 28、541、542 行三处**）。原因：设备上已存在同族包，重复声明同名自定义权限会被 `INSTALL_FAILED_DUPLICATE_PERMISSION` 拒绝；已装 releaseS（3.26.090809）声明的正是 `io.legado.app.tjf.releaseS.READ_WRITE`，沿用同一方案。构建后**必须还原**并核对 `git diff -- app/src/main/AndroidManifest.xml` 为空；
  - 证据：`.agent-protocol/tasks/ai-stage-e-device/**`。
- 允许产生的临时目录：系统临时目录、`app/build/**`（构建产物，已被忽略）、`D:\dsh-temp`。
- 收尾前必须清理：临时 suffix 改动与临时权限改名（均还原并核对 diff 为空）、logcat 原始转储、不属于证据的临时文件。
- acceptor：dsh
- 外部目的地与模型：App 内已配置的 AI 服务（见「外发授权」）；**不得**把目的地或凭据写入证据。
- 明确禁止的数据和操作：凭据/Token、`keystore*`、`look.ps1`、正文与响应体进证据、卸载、清数据、覆盖其它应用、提交、推送、切分支、发布、安装其它软件。

## 完成条件

- [ ] `:app:assembleAppRelease` 成功，记录 APK 路径、大小与 SHA-256
- [ ] `adb -s 10CE5P1M1Z001P9 install -r` 成功，且**未卸载、未清数据**（安装前后 `firstInstallTime` 不变）
- [ ] ≥1 次真实处理取得**明确结论**：真机 `Ready`，或"无需修改"缓存命中，或固定失败分类（`code=… anchor=… chunk=N`）；≤3 次请求
- [ ] 证据只含固定脱敏字段，无正文/响应体/书名
- [ ] `git diff -- app/build.gradle` 为空，`git status` 无新增业务改动
- [ ] 未运行项一律写 `NOT RUN` 并说明缺什么才能判断

## 停止条件

- 需要扩大外发范围、更换目的地/模型，或超过 3 次请求；
- 需要卸载、清数据或覆盖其它应用；
- 出现"不明确但可以继续"的判断压力时停止；
- 当前模式不满足任务要求的保证等级。

## 风险档规则

- `light`：只允许文档与说明性文件；`standard`：普通代码、启发式和配置行为，独立 reviewer、冻结快照、最多两轮；`high`：安全、权限、迁移、协议脚本、发布与高影响数据变更，需独立 acceptor。
