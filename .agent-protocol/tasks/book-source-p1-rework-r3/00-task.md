# book-source-p1-rework-r3 — P1 第三轮返工（脱敏/阶段四/Web 保序）

状态：`ready`

## 目标与非目标

本任务承接 `book-source-p1-diagnostics`（P1 首轮 + 第二轮返工，两轮独立复审与两轮验收均判 `rework`）。
该任务档位是 standard，独立复审上限两轮，已用尽；按其 `30-acceptance.md` §「下一步」的要求，剩余返工项转成本任务重新走独立复审与验收。

交付（全部来自 `book-source-p1-diagnostics/30-acceptance.md` 的阻塞发现 1—4 与 `20-review.md` F-03）：

1. **默认诊断摘要不得泄漏任意正文（安全阻塞）**：`BookSourceDiagnosticsCollector.describeError` 目前把 `error.message` 交给 `DiagnosticRedactor.sanitizeMessage`，后者只认 URL、凭据赋值与 Bearer，普通文本原样输出。异常消息是无界输入，必须改为只输出稳定的错误类别与异常类名（固定说明），并补「任意消息正文不得进入摘要」的守卫测试。
2. **第四阶段（阅读处理后）的真实性与展示时机（功能阻塞）**：`Debug.finishContentDiagnostics` 无条件把 `READ_PROCESS` 标成「未执行」，且 `record` 是同阶段 upsert，会把已经执行过的真实快照覆盖掉；摘要又只在该收尾处输出一次。必须做到：无快照时才写专属「未执行」原因；会话存活期间同章真实阅读完成后能再输出一次含真实第四阶段读数的摘要；阅读发生在调试会话结束之后的情形如实表示（无输出，不伪造）。
3. **Web 端投递竞态（阻塞）**：`BookSourceDebugWebSocket.printLog` 把四行摘要与终止信号拆成多个并发 IO 任务投递，`Debug.kt` 只保证入队顺序；终止任务可先关 socket 导致摘要丢失。必须让同一 socket 的日志按提交顺序串行发送，并给出可复核的保序证据。
4. **`AnalyzeByXPath.kt` 越界改动的裁定（授权阻塞，M-3）**：本任务由 controller 显式把该文件纳入白名单并**保留**等价替换（`TextUtils.join("\n", it)` → `it.joinToString("\n")`）。理由：该文件首轮修改语义等价（`getResult` 返回 `List<JXNode>?`），且回退会让 XPath 契约测试在严格 JVM 单测下调用未实现的 android.jar 桩；本轮不再新增该文件的其它改动。
5. **F-03（低）**：`DiagnosticRedactor.stripUrlSecrets` 在「authority + path」上取最后一个 `@`，路径里含 `@` 时会把主机名切掉（不泄漏，但摘要指向错误主机）。改为只在第一个 `/` 之前（authority 内）寻找 userinfo。

非目标：不做 P2—P5；不修真实书源；不恢复封存 AI 功能；不改数据库结构；本地 TXT/EPUB 导入与阅读路径零侵入；不改变最终正文、异常回退、分页或替换语义；不做发版动作、不跑 lint（非发布轮）；不修改 `book-source-p1-diagnostics` 任务目录里的任何角色原文（只读引用）。

## 实时基线

- 任务风险档：standard
- Git 仓库根：D:/vsproject/legado-E
- 分支：codex/manga-import
- Git HEAD：b8ba92e194468f0d7e6103c1175ba9f2a8233d95
- 修前工作树摘要：见 `.agent-protocol/tasks/book-source-p1-rework-r3/00-task.md` 生成时的 `Get-AgentProtocolGitSnapshot` 记录（P1 全部未提交改动 + P0/工具链既有脏基线；本任务不清理、不修改这些既有改动）。
- 修前快照 SHA-256：93d9bd10c6528f37c76e97435343dd1709d299f2bf17ff16af58099fe06b6e04
- 当前 route_revision：8

## 复审冻结与轮次

- 最大复审轮次：2
- 复审冻结窗口：reviewer 启动至回执完成期间，授权文件、任务书与执行报告不得变化；runner 前后快照不一致时本轮报告失效。
- reviewer 可执行检查：只允许 `06-review-commands.json` 中冻结的参数数组；空数组表示 `NOT RUN`（本任务沿用只读静态复审 + acceptor 独立复跑门禁）。
- 网络策略：默认禁止；本轮不访问真实站点。

## 角色与授权

- controller：dsh（本窗口；route_revision 8，mode=dsh-claude，人类在 DSH 窗口直接指派）
- executor：dsh（仅执行；完成后交 runner 拉起的 claude reviewer，不得自验收）
- reviewer：claude（只读 tool-policy，由 controller 用 `agent-protocol role reviewer -Entry dsh` 拉起）
- acceptor：dsh（在复审之后独立核对工作树、复跑门禁与探针，并逐项处理 findings）
- 外部目的地与模型：当前用户级 providers.json 配置的 claude reviewer 与 dsh 执行；准确路由以运行回执为准，不猜测、不输出凭据。
- executor 可读 / reviewer 可读：`AGENTS.md`、`.agent-protocol` 入口、本任务目录（`15-*-raw.md` 除外）、`book-source-p1-diagnostics` 的任务书/复审/验收（只读）、`书源与解析优化计划书.md`、`维护计划书-后续修复与增强.md`、`app/src/main` 下非敏感源码、`app/src/test` 下合成测试与夹具、构建脚本。禁止读取真实书源 JSON/ZIP、`docs/book-source-audit/语料/**`、`语料归因.md`、`bookshelf.json`、签名材料与凭据。
- 正式文件白名单（硬上限，本轮由 controller 显式扩展）：
  - `app/src/main/java/io/legado/app/help/book/diagnostics/**`
  - `app/src/main/java/io/legado/app/model/Debug.kt`
  - `app/src/main/java/io/legado/app/help/book/ContentProcessor.kt`
  - `app/src/main/java/io/legado/app/model/webBook/BookContent.kt`
  - `app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeRule.kt`
  - `app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeByXPath.kt`（显式纳入：保留首轮等价替换，不再新增改动）
  - `app/src/main/java/io/legado/app/utils/SerialTaskQueue.kt`（新增：串行投递队列）
  - `app/src/main/java/io/legado/app/web/socket/BookSourceDebugWebSocket.kt`
  - `app/src/test/java/io/legado/app/**`（仅本任务相关契约/守卫测试）
  - `app/src/test/resources/book-source-p1/**`（新增合成夹具，如需要）
  - 本任务目录的证据文件：`10-execution.md`、`10-execution.summary.json`、`11-implementation-notes.md`、`16-device-checks.md`、`31-*.log`、`32-*`（合成探针源码/输出）
- 白名单外确有必要时：停止该项，写明必要性交 controller 扩权，不自行扩权。
- 允许产生的临时目录：系统临时目录、`build/`、`app/build/`、`D:/gradle_home`、项目内 `build/drp-gradle-home`；禁止清理用户既有文件与 `book-source-p1-diagnostics` 的证据。
- 沙箱提权：Gradle 门禁需要工作区外写入（`D:\gradle_home` 的 wrapper 锁、Kotlin 守护进程 `%LOCALAPPDATA%\kotlin\daemon`），执行侧按一条命令一次申请 `danger-full-access`；被拒时记录命令与失败证据，改用任务书授权的等价通道并在报告中披露差异，不静默改写结论。
- 明确禁止的数据和操作：真实书源/语料/归因表、`bookshelf.json`、签名材料、凭据、用户设备数据；不提交、不推送、不切分支、不发布、不安装软件、不安装 APK（除非 controller 另行授权真机抽查）、不修改协议目录与其它任务证据、不改数据库结构。

## 完成条件

- [ ] 上述 1—5 项全部落地，且每一项都有对应的守卫/契约测试（异常消息守卫、阶段四覆盖与展示时机、串行投递保序、URL 主机名保留）。
- [ ] 诊断关闭时正文链路零额外处理；四阶段只新增「取接收端 + 写快照」，不改变任何返回值。
- [ ] 设置 `GRADLE_USER_HOME=D:/gradle_home` 后运行 `.`gradlew.bat` :app:compileAppDebugKotlin :app:testAppDebugUnitTest --console=plain`，记录实际退出码与直接解析测试 XML 得到的 `suites/tests/failures/errors/skipped`。
- [ ] 在 `10-execution.md` 给出改动清单、白名单声明、相对既有脏基线的说明、命令证据（含失败与处置）、限制与 NOT RUN、真机抽查步骤。
- [ ] 独立复审（claude，runner 封套）与验收（dsh）分别产出证据；实现完成本身不构成 P1 通过。
- [ ] 真机/UI 未验时如实写 NOT RUN，不宣称 P1 最终完成。

## 停止条件

- 需要改变正文语义、依赖、数据库、本地导入路径、真实用户数据或白名单之外文件时，停止该项并交 controller；
- 需要改变发布范围、安全边界或费用时停止；
- 发现既有 bug 只记录合成复现，不混入修复。

## 风险档规则

- `light`：只允许文档、注释与不改变运行行为的说明性文件；一轮独立静态复审。
- `standard`：普通代码、启发式和配置行为；独立 reviewer、冻结快照、最多两轮。
- `high`：安全、权限、迁移、协议脚本、发布与高影响数据变更；独立 reviewer 与独立 acceptor，关键门禁必须重跑。

## 交接说明

- 任务来源：`book-source-p1-diagnostics`（P1）标准档两轮复审已用尽（`rounds/reviewer-r001` + 当前 `15-reviewer-raw.md`），第三轮复审会超出 `standard.max_review_rounds=2`；按 `Close-AgentProtocolTask.ps1` 的收敛要求「升级任务档或按收敛规则转后续任务」，且 `high` 档要求 executor/reviewer/acceptor 三个不同身份（当前 dsh-claude 下 executor=acceptor=dsh，结构上不可满足），故新开本任务。
- 原任务证据目录保持只读：不覆盖、不删除、不改写任何角色原文；本任务报告引用其 finding 编号（B-1…B-5、M-1…M-3、L-1…L-5、F-01…F-07）作为来源。
- 生成器 `-Activate` 仍因模板行的 CRLF 与 `^状态：`draft`$` 正则不匹配而回滚（与 P1 同一事实），本任务按 CLI 支持的 draft + controller 手工激活流程补全。
- 真实基线：HEAD `b8ba92e19`，分支 `codex/manga-import`，工作树含 P1 未提交改动与 P0 既有脏基线；本任务不提交、不推送、不发布。
