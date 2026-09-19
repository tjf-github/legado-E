# ai-stage-e-anchor — 阶段E：ANCHOR 上下文锚点兼容与本地门禁

状态：`ready`

## 目标与非目标

- 目标：按《AI正文净化-锚点兼容与发布验收计划书》§8 完成本地步骤 B1→B3 与 C：
  1. **B1**：只用合成正文为 `ambiguous + changed` 冻结上下文锚点测试契约（正例可定位 / 反例必须失败），**先让测试在旧实现上真实失败**，不先改生产代码；
  2. **B2**：最小扩展编辑协议——给 `AiEdit` 增加只读短前/后文锚点，以 `前文 + original + 后文` 的**组合唯一性**重建范围；本机必须验证三者都是当前块的精确连续子串；模型 `start/end` 仍只作交叉校验，不得单独消歧；组合仍不唯一则整章失败关闭；
  3. **B3**：同步 Prompt/schema、Provider 严格字段解析、Validator 与反例；递增 `AiVersions.PROMPT` / `VALIDATION` 使旧缓存自然失效；日志只记固定结构化分类，不记上下文字段的值；
  4. **C**：集中自动化门禁 `:app:compileAppDebugKotlin :app:testAppDebugUnitTest :app:lintAppDebug` + `git diff --check`，记录套件/项目数。
- 非目标：
  - **不触发任何真实外部模型请求**（D1 需另行取得目的地、模型与正文范围授权）；
  - 不执行发布矩阵 D2、不构建/安装共存包、不提交/推送/发布（提交由 controller 在 close 之后另行授权）；
  - 不改 UI 文案与交互语义、不碰 Android 侧缓存清理/朗读守卫等既有功能；
  - 不放宽 `changed` 校验换取 Completed，也不以「忽略 ambiguous changed」或信任模型下标作为退路。

## 实时基线

- 任务风险档：standard
- Git 仓库根：D:/vsproject/legado-E
- 分支：codex/manga-import
- Git HEAD：17f8e29d84565101d6509be219f127340450faad
- 修前工作树摘要：clean
- 修前快照 SHA-256：0ece961465e4aeca26c47c4599e93d61f2d90cced254df3661e3b55f0965736c
- 当前 route_revision：1

## 复审冻结与轮次

- 最大复审轮次：2
- 复审冻结窗口：reviewer 启动至回执完成期间，授权文件、任务书与执行报告不得变化；runner 前后快照不一致时本轮报告失效。
- reviewer 可执行检查：只允许 `06-review-commands.json` 中冻结的参数数组；空数组表示 `NOT RUN`。
- 网络策略：默认禁止；协议只记录策略，不能把未实现的网络隔离宣称为强制证据。

## 角色与授权

- controller：dsh
- executor 可读 / 可改：读 `00-task.md`、`05-routing.md`、`14-executor-prompt.md`、`.agent-protocol/README.md`、`CURRENT.md`、`PROJECT.md` 与本任务证据目录；其余可改文件见下方写白名单。
- reviewer 可读：`00-task.md`、`05-routing.md`、`06-review-commands.json`、`10-execution.md`、`14-reviewer-prompt.md`，**以及写白名单内的全部源码与测试文件**（`app/src/main/java/io/legado/app/help/ai/**`、`app/src/test/java/io/legado/app/help/ai/**`）与 `rounds/executor-r001/**` 与 `18-review-probes.md`（冻结命令在一次性 worktree 的真实运行结果）—— 复审必须能自己读 diff 与测试、自己找反例，不得只凭执行报告下结论。禁止读取：凭据与 Token、`keystore*`、`look.ps1`、真实书源/正文/用户缓存/未脱敏日志、`.channel-test.json`，以及白名单之外的其它源码与文档。
- 沙箱提权：否（默认）。需要提权时任务书必须写明哪一条命令、为什么、提到哪一档；执行侧一次一申请，不得批量提权。
- **分工（round 1 实测结论，2026-09-19）**：经 `agent-protocol role executor` 拉起的 dsh 子代理运行在 workspace-write 策略下，对 `D:\gradle_home` 只读，`gradlew.bat` 启动即在 `gradle-8.14.4-bin.zip.lck` 处被拒，且该子进程没有可用审批通道——**它无法运行任何 Gradle 门禁**（证据见 `rounds/executor-r001/10-execution.md`）。因此本任务分工固定为：executor 负责白名单内的代码与测试；**门禁命令由 controller 在拥有完整权限的 shell 中执行并如实留证**。executor 侧的门禁项一律写 `NOT RUN`，不得据此验收，也不得为跑通门禁而改 `GRADLE_USER_HOME`、下载依赖或绕路提权。
- 写白名单是**硬上限**：白名单内才是授权范围；白名单外即使只改一个注释也属越界，必须先由 controller 扩权并重新冻结。
- 正式文件白名单（仅限本任务所需的最小改动）：
  - `app/src/main/java/io/legado/app/help/ai/AiProtocol.kt`（`AiEdit` 增加只读上下文锚点字段与常量）
  - `app/src/main/java/io/legado/app/help/ai/AiOutputValidator.kt`（组合锚点唯一性定位与校验）
  - `app/src/main/java/io/legado/app/help/ai/OpenAiCompatibleProvider.kt`（严格字段解析与系统提示同步）
  - `app/src/main/java/io/legado/app/help/ai/AiCacheKey.kt`（仅 `AiVersions` 版本常量递增）
  - `app/src/main/java/io/legado/app/help/ai/AiChapterProcessor.kt`（仅当需要透传失败分类时）
  - `app/src/test/java/io/legado/app/help/ai/AiOutputValidatorTest.kt`、`AiNoChangeTest.kt`、`OpenAiCompatibleProviderTest.kt`（B1 正反例与解析用例）
  - 证据：`.agent-protocol/tasks/ai-stage-e-anchor/10-execution.md`
- 允许产生的临时目录：系统临时目录，或本字段明确列出的项目内目录；不得把临时产物混入正式 diff。
- 收尾前必须清理：本任务产生且不属于正式交付物的临时文件。
- acceptor：dsh
- 外部目的地与模型：**本任务零外发**——不调用任何外部模型，不发送任何项目代码或正文；仅本地 Gradle 与 JVM 单测。D1 的真实外发另需授权。
- 明确禁止的数据和操作：凭据/Token/Cookie、`keystore*`、`look.ps1`、真实书源与正文、用户缓存与未脱敏日志；禁止网络请求、提交、推送、切分支、发布、删除数据、安装软件与越出白名单的写入。

## 完成条件

- [ ] B1：合成正文正反例齐全，且在**旧实现上真实失败**（保留失败输出，不允许只写结论）
- [ ] B2：组合锚点（前文+original+后文）唯一时可按组合重建范围；三者任一不是当前块精确连续子串、组合仍不唯一、越界/重叠、触碰哨兵保护项或 code point 边界错误时**必须失败关闭**，失败分类稳定且不含正文
- [ ] B3：Prompt/schema/Provider/Validator 同步，`AiVersions` 递增使旧缓存失效，新增字段的值不进日志
- [ ] C：`:app:compileAppDebugKotlin :app:testAppDebugUnitTest :app:lintAppDebug` 全通过并记录套件/项目数，`git diff --check` 无新增问题
- [ ] 文档与交接：`10-execution.md` 如实记录改动、命令与真实输出；未运行项一律写 `NOT RUN`，不确定处写明缺什么才能判断

## 停止条件

- 权限或数据范围不明确；
- 需要改变产品语义、费用、发布范围或安全边界；
- 当前模式不满足任务要求的保证等级。

## 风险档规则

- `light`：只允许文档、注释与不改变运行行为的说明性文件；一轮独立静态复审。触及代码、脚本、依赖、构建、CI、权限、安全、路由、发布或数据格式时必须升级。
- `standard`：普通代码、启发式和配置行为；独立 reviewer、冻结快照、最多两轮。
- `high`：安全、权限、迁移、协议脚本、发布与高影响数据变更；独立 reviewer 与独立 acceptor，关键门禁必须重跑。
