# book-source-d2-match — 最终验收（acceptor: dsh）

状态：`本轮验收完成；协议 close 不可执行（见 §4.6），最终状态按 controller=executor=acceptor 同源的保证上限记录`

`provisional`

- product_state：`ready`
- assurance_state：`partial`
- acceptor 运行实例：dsh（mode=`dsh-claude`，route_revision=8，assurance=standard；controller=executor=dsh，reviewer=claude 由 runner 拉起，共三轮）
- 基线：分支 `codex/manga-import`，HEAD `25b289adb2060da421e437e9766727dac783a0c2`（未提交、未推送、未切分支、未发布）
- 判定依据：任务书完成条件、`10-execution.md`（第三轮冻结版）、三轮复审报告（`rounds/reviewer-r001/20-review.md`、`rounds/reviewer-r003/20-review.md`、`20-review.md`）、`18-review-probes.md`、`gate-artifacts/**`

---

## 1. 验收范围与依据

读取：本任务 `00-task.md`/`05-routing.md`/`06-review-commands.json`/`10-execution.md`/`11-implementation-notes.md`、`gate-artifacts/**`、`18-review-probes.md`、三份复审报告与回执、`rounds/**`、`superseded-rounds/reviewer-r002-aborted/**`、`docs/book-source-audit/D-2-补打匹配口径-裁定.md`；只读引用 `book-source-p1-device/findings.json` F-0009。

---

## 2. 独立复核（acceptor 自己跑的，不采信报告自述）

| # | 复核动作 | 结果 |
| --- | --- | --- |
| 2.1 | `agent-protocol role-verify book-source-d2-match` | **PASS（3 项）**：顶层 r004 与两个归档轮（r001、r003）的回执↔报告哈希全部一致 |
| 2.2 | 门禁用例数复算（解析 `app/build/test-results/testAppDebugUnitTest/*.xml`） | **suites=46, tests=424, failures=0, errors=0, skipped=0**；新增测试类 30 条。与 `10-execution.md` §1.2 自述一致 |
| 2.3 | 门禁原始件核对（`gate-artifacts/d2-gate-final.log`） | 实读到 `> Task :app:compileAppDebugKotlin`、`> Task :app:testAppDebugUnitTest` 均**执行**（非 UP-TO-DATE）与 `BUILD SUCCESSFUL in 1m 27s`；日志 sha256 `a6d29216…` 与 README 一致 |
| 2.4 | 冻结命令（probe 侧） | `18-review-probes.md`：三条命令由 runner 在一次性 worktree 里执行、退出码 0；`applied_bundle_hash=ec997bf2…`、`真实工作树前后状态：stable`；**并已注明该件只覆盖 args 里的 ASCII 路径**（r004 F-3） |
| 2.5 | repo 级越界对账（acceptor 自跑 `git status --short`） | 已跟踪改动 = 4 个业务文件 + 裁定书 + `双平台开发环境流程书.md`（§4.4 追认）+ `CURRENT.md`（§4.3）；未跟踪 = 任务目录 + `DiagnosticIdentity.kt` + `DiagnosticIdentityContractTest.kt`。**无其它已跟踪改动** |
| 2.6 | 三轮复审结论核对 | r001：**1 项阻塞 + 9 条非阻塞**（报告 sha256 `fb5b715f…`）；r003：**通过、无阻塞** + 4 条非阻塞（`94718490…`）；r004：**通过、无阻塞** + 3 条低（`aefc6b6a…`）。三份报告均由 runner 转录并绑定回执 |
| 2.7 | 裁定书与实现一致性 | 代码文案、归一语义、`sortUrls` 未做、`Debug` 无默认参数等主张逐条对照工作树，**与 `10-execution.md` §4/§5 相符**（r004 已由 reviewer 独立读到 `NetworkUtils`/`BookChapter`/`AnalyzeUrl`/`BookChapterList` 并确认 M1 口径成立） |

---

## 3. 三轮复审处置核对

| 轮次 | 结论 | 处置 |
| --- | --- | --- |
| r001 | 1 项阻塞（M3 文案在默认时序不出现）+ 9 条非阻塞 | 执行报告 §2 逐条处置：新增刷新点 + 一会话一次打印 + 时序契约测试；文案/证据/白名单/`isSameSource` 删除等 |
| r003 | **通过、无阻塞** + 4 条非阻塞 + 3 项待澄清 | 执行报告 §2b 逐条处置：**F-2 并发覆盖真实读数的真缺陷已修**（刷新整体持锁 + 120 轮并发压力测试）、F-3 文案去掉「其它」、F-4 空白章节不绑定、§3.2 流程书追认并纳白、白名单追加四个核对源、生产入口去默认参数 |
| r004 | **通过、无阻塞** + 3 条低 | **F-1（文档未同步）：已修**——裁定书 M3 行与 `11-implementation-notes.md` §1.6 的措辞改为与代码一致（**仅文档；代码未动**，故「被复审的代码修订」仍是 r004 看过的那一版）；**F-2（阶段四原因行可被与「为何未执行」无关的会话级不匹配改写）：记为 open**，不在本轮改代码（理由见 §6 第 2 条）；**F-3（冻结证据集在构造上覆盖不到非 ASCII 路径）：已在 `gate-artifacts/README.md` 注明定义域**，并保留「无越界」结论以 repo 级对账为准；**§5.7**：把「编译器已确认所有生产调用点都显式传 base」收紧为「编译期已排除省略实参；显式传 `null` 的调用点无法由编译期排除」 |

---

## 4. 披露（必须与结论一起读）

1. **执行侧没有 runner 封套**：本任务由 controller（同一 DSH 实例）直接指派执行，因此**没有** `15-executor-raw.md`/`10-execution.summary.json`；`role-verify` 对本执行侧不构成绑定，只对 reviewer 侧构成。controller=executor=acceptor 同源 ⇒ 本轮上限为 `provisional`，**不主张 `accepted`**。
2. **协议文件行尾修复与指纹漂移**：建任务前发现 `.agent-protocol/**` 非任务文件在工作区是 CRLF、索引里是 LF，导致 `-Activate` 的状态行校验失败；按索引恢复 21 个文件为 LF（`tasks/**` 一字节未动），并钉 `.agent-protocol/** -text`。副作用：协议指纹由 `3a239c56…` 变为 `9c5c705c…`，`CURRENT.md` 的 `channel_test` 证明不再与当前字节对应（详见 `05-routing.md` 披露 1）。
3. **`CURRENT.md` 的改动归属**：`task_id`/`evidence_dir`/`state` 三行由 `agent-protocol task … -Activate` 写入，属协议工具链动作，非执行侧越界（r001 F-4、r004 §1 均已核对）。
4. **`双平台开发环境流程书.md` 执行期被追加**：controller（同一实例）在执行期间追加了两条环境坑登记（协议快照哈希成本、`review-probe` 复制成本），属执行白名单外写入——**controller 追认**，并已把该文件（§8 两条）纳入任务书白名单（r003 §3.2）。
5. **复审过程的边界事件（如实登记）**：
   - r001 的 reviewer 为跑冻结命令委派了子代理，其中一个子代理又派生下级代理，该下级代理**越界读取了 `13-reviewer-context.json` 并对任务目录做过一次 grep**，还两度回灌自称本任务复审的伪报告块；另有一批回报提到「以作废指令开头的消息」，r001 声明未收到/未遵循。**只采纳 runner 转录的报告正文块**，伪报告未进入任何证据链。
   - r003 披露自己做过一次越出白名单的 `Glob`（只返回文件名，未读内容）。
   - 三轮 reviewer 均**没有 shell**，冻结命令一律由它们标 `NOT RUN`，命令类证据只来自 `review-probe`（§2.4）。
6. **协议账面事故与 `close` 不可执行（本轮无法收口的直接原因）**：
   - 第一次拉第二轮复审（21:31）在前置快照后**挂死**（进程无子进程、claude 未启动），controller 中断了它。中断时该次已把顶层 r001 证据归档到 `rounds/reviewer-r001/`、把顶层 `20-review.md` 换成 SUPERSEDED 占位，但**没有写出自己的回执**。
   - 归档逻辑按「**第一个空闲归档位 + 1**」编号并原样复制**顶层现存文件**（`Invoke-AgentProtocolRole.ps1:233-260`），于是重跑（22:45）把同一份 r001 回执误标着复制进 `rounds/reviewer-r002/`。
   - 处置（经用户授权）：r004 正确归档出 `rounds/reviewer-r003/` 后，把 `rounds/reviewer-r002/` 里**唯一不重复的两件**（那次中止尝试的 checkpoint/context）移到 `superseded-rounds/reviewer-r002-aborted/` 保留，删除与 r001 逐字节相同的回执/summary 重复件与 107 字节占位；随后 `role-verify` 回到 **PASS（3 项）**（§2.1）。
   - **但 `close` 仍不可执行**：`Close-AgentProtocolTask.ps1:142-146` 以「`rounds/` 里 reviewer 归档目录数 + 顶层回执」计轮次，本次为 **2 + 1 = 3**，超过 `standard` 档上限 2；该计数把被中止的 r002 也算作一轮。协议给出的出口是「升级任务档或按收敛规则转后续任务」，而升级到 `high`（上限 3）会同时要求独立 executor/acceptor 回执，本模式不满足。**故本任务不调用 `close`**，`CURRENT.md` 保持指向本任务，交用户/controller 决定（§6 第 1 条）。
7. **提交边界**：全部改动（4 个业务文件 + 1 个新生产文件 + 1 个新测试文件 + 裁定书 + 流程书 + 协议证据 + 任务证据）**未提交、未推送、未发布**；提交/推送/发布按协议各需独立授权。

---

## 5. NOT RUN（不得读作已覆盖）

1. 真机安装与实际阅读补打同屏证据（不装包、不联网，需 §13 授权后另起）；
2. `:app:lintAppDebug`（非发布轮）；
3. 浏览器端 Web 调试页与真机 WebSocket 端到端；
4. 真实书源、真实站点、`docs/book-source-audit/语料/**`、`bookshelf.json`；
5. 音频/视频/文件类书源、多页章（`nextContentUrl`）、`@webjs:`；
6. `sortUrls()` 兜底（源 URL 被真正改写）与**书号归一**（`accepts` 对 `bookUrl` 仍逐字比较）；
7. 存量书 `baseUrl` 的实际填充率（无法在本机验证用户数据）；
8. Web 调试页流里刷新链的可达性（r003 §3.1）；
9. 冻结命令的退出码与 XML 计数的独立复算（reviewer 无 shell；命令由 runner probe 执行、计数由 acceptor 复算）。

---

## 6. 后续项

1. **协议脚本健壮性（建议按协议项目自己的批次修）**：①归档目录按**被归档回执的轮次 id** 命名，而不是「第一个空闲位」；②被中止/超时的轮次不应永久占用轮次编号与上限配额（或在 manifest 留痕并允许重跑不计轮次）；③`reviewer-r*` 计数应只算**含有效回执**的归档。修完可对已收敛的任务补 `close`。
2. **r004 F-2（open）**：阶段四「未执行」原因行会被与本阶段无关的会话级不匹配改写（链路在阶段一/二/三失败 + 会话存活 + 用户读非目标章时可达）。建议：文案改非因果式，或仅当阶段一/二/三均无错误类别时才刷新；并补一条 `recordFailure` 之后的时序契约测试。
3. **r004 §5.7**：生产侧 `Debug.*` 已无默认参数（编译期排除「省略实参」），但无法排除白名单外显式传 `null` 的调用点；将来新增调用点应同时传 `chapter.baseUrl`。
4. **书号身份（r001 F-10）与存量 `baseUrl`**：与 D-2 同类的残留，见 §5 第 6/7 条，建议随 P2/P3 一并处理。
5. **证据层成本**：本仓库 `review-probe` 每次复制含 ignored 文件的工作树（25,088 文件 / 1.1 GB，约 20 分钟）；协议快照每次哈希 2.26 万 ignored 文件（约 3.5 分钟）。已登记进《双平台开发环境流程书》§8；如需进一步提速，可考虑给 `D:\vsproject\legado-E` 加 Defender 目录排除（需管理员）。
6. **本轮代码修订 = r004 看过的那一版**；r004 之后的改动仅限文档（裁定书 M3 行、实施说明 §1.6、`gate-artifacts/README.md` 定义域注明），**代码一字节未动**——这是为了让「被复审的修订」保持等于最终修订。
