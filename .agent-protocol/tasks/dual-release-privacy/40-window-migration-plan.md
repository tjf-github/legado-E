# dual-release-privacy — 双版本身份迁移与新窗口交接计划

状态：`A-B-complete / handed-off-to-dsh-claude / C-D-authorized`

## 0. 当前交接（route_revision 6）

- Codex 额度不可用，后续不依赖 Codex；当前模式为 `dsh-claude`、风险档 `standard`。
- DSH 负责 controller+executor+acceptor，Claude 负责独立只读 reviewer；该模式不具备 high 的独立 acceptor 保证，验收必须披露这一限制。
- A/B 已完成，红测证据见 `10-execution.md`；当前只执行 C+D。E 真机迁移、F 发布验证、提交、推送和发布继续禁止，等待用户分别授权。
- 当前读写白名单、停止条件和实际角色映射以 `00-task.md` 顶部“DSH + Claude 接管任务书”为唯一权威入口。

## 1. 已冻结的产品决策

| 版本身份 | 最终包名 | 升级关系 | 建议 APK 标识 |
| --- | --- | --- | --- |
| 隐私版（旧版延续） | `io.legado.app.tjf.release` | 必须可覆盖当前旧版并保留全部数据 | `private`（解析时兼容历史 `release`） |
| 普通版（新安装链） | `io.legado.app.tjf` | 全新安装；不自动继承旧版数据 | `normal` |
| 临时共存测试包 | `io.legado.app.tjf.releaseS` | 不属于正式双版本；不得发布为迁移目标 | `releaseS` 仅保留历史测试语义 |

不可反转的原则：包名是升级身份，不按显示名称推断。旧 `.release` 改包名会切断覆盖升级和应用私有数据，因此禁止。新普通版采用仓库已有的基础 applicationId，不新增含义模糊的 `.releaseA` / `.releaseS` / `.releaseN` 后缀。

## 2. 迁移目标

1. 每次 beta 与正式发布都生成并上传普通版和隐私版两个 APK。
2. 隐私版候选的包名、签名和版本号满足对当前 `io.legado.app.tjf.release` 的 `adb install -r` 升级条件，并用非敏感数据标记证明升级后数据仍在。
3. 普通版包名固定为 `io.legado.app.tjf`，可与隐私版同时安装，Provider authorities 不冲突。
4. 两个版本共享同一功能源码和同一发布版本号，仅应用身份、显示名称、APK 标识和更新资产匹配不同。
5. 应用内更新按“安装身份 + 发布通道”精确选择自己的 APK；禁止普通版拿到隐私版，或隐私版被引导安装普通版。
6. `.releaseS` 不出现在正式双版本构建矩阵、发布资产或用户可选更新版本中。

## 3. 推荐实现方案

### 3.1 Gradle 身份选择

- 保持 `defaultConfig.applicationId = io.legado.app.tjf`。
- 保持本地/未显式指定时的 release 默认身份为旧隐私版 `.release`，避免开发者误打一个会切断旧升级链的包。
- 增加一个明确、可校验的 Gradle 发布身份参数，例如 `-PRELEASE_IDENTITY=normal|private`：
  - `private`：应用 `.release` 后缀；
  - `normal`：不添加 applicationIdSuffix；
  - 其它值立即失败，不静默回退。
- 不再由 CI 使用 `sed` 把 `.release` 临时替换成 `.releaseS`。身份逻辑应由 Gradle 自己校验，CI 只传枚举参数。
- 显示名称分别使用明确资源；建议普通版“阅读”，隐私版“阅读·隐私”。显示名称最终可调整，但不得影响包名决策。

### 3.2 CI 双构建

- matrix 从 `release/releaseS` 改成 `normal/private`，无论 `updateLog.md` 是否变化都构建两个身份。
- 两个 job 均运行现有 `testAppDebugUnitTest lintAppDebug assembleAppRelease` 门禁，并分别传入冻结的身份参数。
- artifact 名固定为不同且稳定的值，例如：
  - `legado.app.normal`
  - `legado.app.private`
- Release 中最终文件名必须同时包含版本号和身份标识，不再用“测试版/正式版”表示包身份。beta/正式是发布通道，normal/private 是应用身份，两者不得混用。
- `update-storage`、Gitee、Telegram 等下游消费方改为显式选择 `normal` 或 `private` artifact；禁止继续依赖 `releaseS` 名称。

### 3.3 更新识别

- 先写回归测试，再改实现。
- 将“安装包身份”和“beta/正式发布通道”分开判断，资产解析不能再把所有正式 Release APK 都压成同一个 `OFFICIAL` 后失去包身份。
- 当前 `.release` 安装默认匹配 `private` 资产；无后缀 `io.legado.app.tjf` 默认匹配 `normal` 资产。
- 解析时可兼容历史 `.release` 文件名作为 private，但新资产统一使用 `private`；`.releaseS` 不再作为本次双版本候选。
- GitHub 与 Gitee 都必须按同一身份规则精确过滤，测试覆盖两个资产顺序互换、额外无关 APK、beta/正式两种 release 元数据。

### 3.4 权限与组件隔离

- 核对 Manifest 中 `${applicationId}.readerProvider` 与 `${applicationId}.fileProvider`，确认双包安装后 authorities 唯一。
- 固定权限 `io.legado.READ_WRITE` 由两个同签名应用共同声明的兼容性必须在目标 Android 版本上验证；若出现安装冲突，只允许改成基于 `${applicationId}` 的权限名并补兼容审查，不得现场卸载旧包规避。
- 两个 APK 必须使用同一获授权 CI keystore；不得读取、输出或改动密钥内容。

## 4. 分阶段执行顺序

### A. 新窗口恢复与基线

1. 读取根 `AGENTS.md`、`.agent-protocol/README.md`、`PROJECT.md`、`CURRENT.md`、本任务 `00-task.md` 与本计划。
2. 执行 `git status -sb`、`git log --oneline -3`，保存所有用户既有改动；不得把 `.claude/`、`bookshelf.json`、AI 任务 EOF 差异混入本任务。
3. 将当前尚未提交的旧映射 diff（workflow、`AppReleaseInfo.kt`、旧 `AppReleaseInfoTest.kt`）分类为“可复用逻辑”或“需重写”，不得直接提交。
4. controller 按新范围更新任务白名单、冻结命令和执行报告起点；旧 reviewer/acceptance 结论标为 superseded，不删除历史。

### B. 测试先行

1. 增加纯 JVM 测试，冻结 package identity → asset identity 的映射。
2. 先证明当前代码会把旧 `.release` 错配为 normal 或把正式资产身份压平，记录红测。
3. 覆盖 normal/private 在 beta 和正式 Release 中各自只选自身 APK。
4. 覆盖 `.releaseS` 不属于正式双版本选择集。

### C. 最小实现

1. 修改 Gradle 身份参数与显示名称资源。
2. 修改 CI matrix、构建参数、artifact/Release 命名及下游消费方。
3. 修改应用身份识别、资产解析和更新过滤。
4. 不修改数据库结构、业务功能、AI 功能开关、签名配置或用户数据格式。

### D. 本地自动化门禁

运行前设置 `$env:GRADLE_USER_HOME = "D:\gradle_home"`：

1. `git diff --check`
2. 更新模块定向 JVM 测试
3. `./gradlew.bat :app:compileAppDebugKotlin`
4. `./gradlew.bat :app:testAppDebugUnitTest`
5. `./gradlew.bat :app:lintAppDebug`
6. 分别构建 normal/private release APK；用 `apkanalyzer` 或 `aapt dump badging` 核对包名、versionName、versionCode，用 `apksigner verify --print-certs` 核对两包证书摘要一致。

### E. 真机迁移验收（发布前硬门槛）

1. 明确指定设备序列号；开始前记录已安装旧 `.release` 的包名、版本号、证书摘要和非敏感数据标记。
2. 对 private 候选执行 `adb install -r`，禁止卸载、清数据；安装后确认包名未变、版本前进、数据标记仍存在。
3. 安装 normal 候选，确认 `io.legado.app.tjf` 与 `.release` 同时存在、图标/名称可区分、各自数据库和设置独立。
4. 分别触发检查更新，证明两个身份选择各自 URL；不得仅凭枚举测试代替真实安装行为。
5. 若签名不一致、权限冲突或覆盖安装失败，立即停止；不得通过卸载旧版解决。

### F. 独立复审与发布验证

1. executor 写与实际 diff 一致的执行报告；reviewer 独立核对包名、签名、矩阵、资产名和更新选择；acceptor 重跑关键门禁并检查真机证据。
2. 只有任务达到 `accepted` 后，才分别请求“提交”“推送”“发布”授权；三者不合并推定。
3. 首次发布以 GitHub Actions 实际出现两个 build job、两个不同包名 APK、两个正确签名资产为最终证据。
4. 发布后在旧 `.release` 上再做一次在线更新检查；normal 做一次新装更新检查。失败则停止宣传双版本，保留旧隐私版可回滚路径。

## 5. 完成判据

- [ ] `io.legado.app.tjf.release` 可从发布前旧版保留数据覆盖到新 private APK。
- [ ] `io.legado.app.tjf` 可独立安装并与旧包共存。
- [ ] 两包签名一致、版本号一致、authorities 唯一。
- [ ] beta 与正式发布均生成 normal/private 两个资产。
- [ ] 两种身份的应用内更新都精确选择自身 APK。
- [ ] 正式矩阵、Release 资产和下游上传均不再使用 `.releaseS`。
- [ ] compile、全量 JVM、lint、双 APK 检查、真机迁移、独立复审全部通过。
- [ ] AI 实验功能保持封存，业务 diff 不包含 AI 源码或设置变化。

## 6. 停止条件

- 旧 `.release` 候选无法 `install -r` 或签名不一致；
- 双包出现权限/provider 冲突；
- 需要卸载、清数据、读取真实用户正文/凭据才能继续；
- 需要改变数据库结构或自动跨包复制私有数据；
- 更新端无法可靠区分 normal/private；
- 未取得提交、推送或发布的对应独立授权。

## 7. 新窗口唯一开工指令

DSH 新窗口从 `CURRENT.md`、`00-task.md`、`10-execution.md` 和本计划恢复上下文，先核对工作树/HEAD，再只执行 C+D：让 A/B 红测转绿，完成 normal/private 的 Gradle、更新识别、CI 和本地门禁。实现报告完成后拉起 Claude 只读复审，再由 DSH 处理 findings 并验收。不要安装 APK、进入 E/F、提交、推送或发布；任何旧报告里“`.release` 是普通版、`.releaseS` 是隐私版”的结论均已失效。
