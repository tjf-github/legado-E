# book-source-p1-rework-r3 — 验收（acceptor: dsh）

状态：本轮返工交付物在代码与证据层面通过独立复审（reviewer-r002 判定「源码层面未发现阻塞项」），
但真机与端到端验收未执行，按「不夸大」原则判定为

`provisional`

- product_state：`rework`
- assurance_state：`partial`
- 日期：2026-09-24（本地时区）
- acceptor 运行实例：dsh（mode=`dsh-claude`，route_revision=8，assurance=standard；controller=executor=acceptor=dsh，reviewer=claude 由 runner 拉起、两轮）
- 基线：分支 `codex/manga-import`，HEAD `b8ba92e194468f0d7e6103c1175ba9f2a8233d95`（未提交、未推送、未切分支、未发布）
- 判定依据：任务书 §完成条件 + `20-review.md`（reviewer-r002）+ 本验收自己复跑/复算的证据

---

## 一、验收依据与范围

读取：本任务 `00-task.md`、`10-execution.md`、`10-execution.summary.json`、`11-implementation-notes.md`、`12/13/14/15-*`（reviewer 轮次元数据）、`20-review.md`（r001 与 r002 轮次原件、`rounds/reviewer-r001/**`）、`32-probe-source.java`、`32-probe.log`、`.review-snapshot.json`；P1 任务 `book-source-p1-diagnostics/00-task.md`、`20-review.md`、`30-acceptance.md`（只读引用，未改一字）；实际工作树与 diff；`app/build/test-results/**`、`app/build/p1-r3-gate*.log`、`app/build/p1-r3-probe/**`。

本轮验收**未修改**任何生产代码、测试、任务书、执行报告或复审原文。唯一新增的验收侧文件是 `30-acceptance.md` 与本任务 `findings.json`（发现登记表）；`32-*` 探针证据由执行侧落盘、验收侧逐行复核。

---

## 二、独立复核（验收自己跑的，不采信报告自述）

### 2.1 版本与写入面（闭合 reviewer-r002 F-02-6）

- `git rev-parse HEAD` = `b8ba92e194468f0d7e6103c1175ba9f2a8233d95`，`git branch --show-current` = `codex/manga-import`：本轮没有提交、没有切分支。
- `git status --porcelain=v1 -uall -- . ":(exclude).agent-protocol/tasks/**"` 的改动集合与任务书白名单逐条对得上：tracked 侧 7 个业务文件（`ReplaceRule.kt`、`ContentProcessor.kt`、`Debug.kt`、`AnalyzeByXPath.kt`、`AnalyzeRule.kt`、`BookContent.kt`、`web/socket/BookSourceDebugWebSocket.kt`）+ `.agent-protocol/CURRENT.md`（controller 职责：换挡与指针）；untracked 侧 `help/book/diagnostics/**`（6）、`utils/SerialTaskQueue.kt`、7 个测试文件、`app/src/test/resources/bookSourceBaseline/**`、P0 的 `docs/book-source-audit/**` 与 `tools/book-source-audit/**`、`bookshelf.json`、仓库根 `build-dsh-p1-*.log`、`.gitignore`、`书源与解析优化计划书.md`。
- **「白名单外写入为零」现在有独立依据**：除以上集合外没有任何新增改动；`.gitignore`、`书源与解析优化计划书.md`、P0 文档与工具、`bookshelf.json`、仓库根历史日志全部是本轮**之前**就已脏的既有基线（未删除、未改写，本轮只读）。
- 其它任务证据目录（`dual-release-privacy/**`、`ai-stage-e-anchor-controlled/**`）最后修改时间停在 2026-09-21，不在本轮时间窗内，本轮未触碰。

### 2.2 门禁与探针的时序自证

验收不采信报告文字，而用文件时间戳把「跑的产物」与「冻结的源码」对齐：

```text
newest-source   2026-09-24T22:29:42   SerialTaskQueueTest.kt（最后一次源码/测试改动）
newest-class    2026-09-24T22:31:50   app_appDebug.kotlin_module（编译产物）
newest-testxml  2026-09-24T22:32:07   TEST-...DiagnosticsToggleParityTest.xml
probe-log       2026-09-24T22:32:14   32-probe.log
reviewer-r002   2026-09-24T22:33:46   15-reviewer-raw.md 的「开始时间」
```

即：**最后一次源码改动 → 编译 → 测试 → 探针 → 复审启动**严格递增，复审之后没有任何源码/文档改动（reviewer 只读、本验收未改树）。因此 `suites=45 tests=394 failures=0 errors=0 skipped=0`（XML 时间戳 22:32:07）与探针 `probe_failures=0/11`（22:32:14）对应的就是复审所看到的同一份源码。

- 门禁命令（任务书原文）：`GRADLE_USER_HOME=D:\gradle_home` 后 `gradlew.bat :app:compileAppDebugKotlin :app:testAppDebugUnitTest --console=plain`，最终一次 `BUILD SUCCESSFUL in 55s`、exit 0；日志 `app/build/p1-r3-gate-r2.log`。
- 用例分布（直接解析 XML）：`BookSourceDiagnosticsContractTest=51`、`SerialTaskQueueTest=3`、`DiagnosticsToggleParityTest=7`、`AnalyzeRuleContractTest=27`、`HtmlFormatterContractTest=16`、`ReplaceRuleContractTest=17`、`ContentProcessorContractTest=14`，其余为既有套件，合计 394。
- reviewer-r002 用源码 `@Test` 计数独立复算出「45 文件 / 394 例」，与本人解析的 XML 逐项相等（其报告 §四.1）。

### 2.3 角色回执

- `agent-protocol role-verify book-source-p1-rework-r3` → **PASS（15-reviewer-raw.md -> 20-review.md 一致）**；`15-reviewer-raw.md` 记录 `结果：ok`、`复审快照：stable`、`上一轮归档：rounds/reviewer-r001`（两轮复审均留痕）。
- **执行侧不存在 runner 封套**：dsh-claude 下 controller=executor=acceptor=dsh，执行由 controller 直接完成，`10-execution.summary.json` 已按事实写 `source=direct-controller-dispatch`。这是**结构缺口，不是代码失败**，按 P1 第二轮先例书面记录，不冒充已绑定（登记为 F-0006）。

### 2.4 设备与端到端

`adb devices -l` = `emulator-5554 offline`（无可用设备）。本轮**未安装任何 APK、未操作设备或用户数据**，真机与端到端全部 **NOT RUN**，因此本验收不宣称 P1 已最终完成（任务书 §完成条件第 6 条）。

---

## 三、P1 四项阻塞与 F-03 的闭合

| P1 验收阻塞 | 验收结论 | 依据 |
| --- | --- | --- |
| 1 默认摘要可泄漏任意正文（安全） | **已闭合**。`describeError` 只输出异常类名，阶段内异常与链路级兜底共用；生产代码里 `sanitizeMessage` 的唯一调用点是固定文案的未执行原因（reviewer-r002 用调用点枚举独立核实） | `BookSourceDiagnostics.kt:170-171`；守卫测试 3 条；探针 check 1—3 |
| 2 第四阶段被覆盖成「未执行」/ 不可见 | **已闭合**。`markNotExecutedIfMissing` 不再覆盖已执行快照；展示时机下沉为可测的 `shouldReportReadingStage()`；会话结束时无输出、不伪造 | 测试 5 条；探针 check 4—6、8；reviewer-r002 表 A |
| 3 Web 端摘要与终止信号投递竞态 | **已闭合（提交顺序层面）**。`printLog` 改为同一 `SerialTaskQueue` 串行投递，终止任务内含 `cancelDebug+close`，因而必然排在摘要之后 | 测试 3 条；reviewer-r002 表 A（并指出 NanoWSD `send` 库层语义仍属真机待验） |
| 4 M-3 `AnalyzeByXPath.kt` 越界（授权） | **已闭合**。controller 在任务书白名单里显式纳入并保留等价替换；本轮未再改该文件其它内容 | 任务书 §正式文件白名单；reviewer-r002 独立复核语义等价 |
| F-03（P1 复审低项）URL 主机名被路径 `@` 切掉 | **已闭合**。userinfo 只在 authority 内定位；空 authority 保守回退 | 测试 3 条；探针 check 9—11；reviewer-r002 §四.3 手算 5 例一致 |

---

## 四、reviewer-r001（第一轮复审）findings 处置

| Finding | 处置 | 验收核实 |
| --- | --- | --- |
| F-01 [中] 抓取失败贴到阅读路径的阶段四读数 | 已修：`recordFailure` 候选排除 `READ_PROCESS`，无候选归阶段一 | 守卫测试 `chainFailureNeverStampsTheReadingPathStage`；探针 check 7；reviewer-r002 表 B 复算确认 |
| F-02 [中] 展示时机缺守卫测试 | 已修：判定下沉为 `shouldReportReadingStage()` 三态 | 测试 2 条 + 探针 check 8；残余（`callback == null` 分支）登记为 F-0001 |
| F-03 [低] 畸形 URL 不再遮蔽 userinfo | 部分修：空 authority 回退；userinfo 含 `/` 记为残余边界（本轮之后该路径已无动态调用方） | 测试 + 探针 check 11；reviewer-r002 F-02-5 |
| F-04 [中] 执行侧回执表述与事实不符 | 已修表述：`source=direct-controller-dispatch` + `source_note` 明写无封套 | 本验收 §2.3 记录结构性缺口（F-0006） |
| F-05 [低] 补打权可能被导出/统计消费 | 登记为已知限制，未改行为 | 登记为 F-0005 |
| F-06 [低] 无生产调用点的 `close()`、UTF-16LE 探针日志 | 已修：删除 `close()` 及其测试；日志改 UTF-8 | reviewer-r002 表 B（并核实无残留引用） |

---

## 五、reviewer-r002（第二轮复审）findings 处置

reviewer-r002 结论为**通过（源码层面无阻塞项）**，其余 7 条为低/信息级。验收侧**未在复审后改动工作树**：MODES.md 规定「最后一轮复审后只允许删除或收窄已有表述的文档修正，任何改变功能或证据含义的修正都要重新复审」，而本任务 standard 档的两轮复审已用尽，因此以下条目一律**登记为未关闭发现**（`findings.json` F-0001…F-0007），不在此处静默「顺手修」：

| 复审发现 | 处置 | 登记 |
| --- | --- | --- |
| F-02-1 `Debug` 侧 `callback == null` 分支缺守卫测试 | 未改树（改测试会破坏冻结快照）；该分支行为已静态核对正确 | F-0001（low，open） |
| F-02-2 执行报告 §2 第 1 行行号偏差（应为 `BookSourceDiagnostics.kt:170-171`） | 未改报告（同上）；**行号更正在本验收单内给出** | F-0002（info，open） |
| F-02-3 真机步骤 2 缺少可复现窗口（Web 端收尾即 `cancelDebug(true)`；Android 调试页 finish 即 `onCleared()`） | 未改报告；真机轮次必须改用「抓取仍在飞行中」或「不经 finish 的入口」，否则会把「会话已结束→无输出」误判为功能失效 | F-0003（low，open） |
| F-02-4 `RssSourceDebugWebSocket.printLog` 同形竞态（未纳入 P1） | 未处理（超 P1 范围）；根因相同，可直接复用 `SerialTaskQueue` | F-0004（low，open） |
| F-02-5 F-03 残余边界已无动态调用方 | 记录（不构成风险） | 并入 F-0002/F-0005 说明 |
| F-02-6 「白名单外写入为零」无法由复审独立证实 | **由本验收闭合**：见 §2.1 的独立 git 核对 | 已闭合 |
| F-02-7 执行侧无 runner 封套 | 记录为结构缺口，不冒充 | F-0006（info，open） |

另：reviewer-r002 自述读取了 `.agent-protocol/PROJECT.md`（其口径判断不影响结论），并顺带发现该文件 `allowed_external_providers` 仍写 `mode=triad`、与实际 `dsh-claude` 不一致。**确认属实**，属协议配置的陈旧描述；本轮不改协议文件（改协议须另开任务并重新冻结），登记在案。

---

## 六、NOT RUN（不要读作已验收）

1. 真机 / UI 四阶段可见性（含阶段四补打的真实读数）——无可用设备（`emulator-5554 offline`）。
2. Web 端四行摘要的**实际到达顺序**（只证明了投递/执行顺序；NanoWSD 库层 `send`/`close` 语义未核实）。
3. 端到端（真机）诊断开关前后正文逐字一致。
4. 真机对照 reviewer-r001 F-01 的「断网 + 同章缓存阅读」场景（代码与探针已覆盖，真机未做）。
5. `:app:lintAppDebug`（非发布轮）。
6. `@webjs:`、真实书源与真实站点（未读取任何真实语料/书源/`bookshelf.json`）。
7. 其它 `Debug.Callback` 实现（`ui/book/source/debug/BookSourceDebugModel`、`ui/rss/source/debug/RssSourceDebugModel`、`RssSourceDebugWebSocket`）未改动（reviewer-r002 已独立读源码确认 RSS Web 端仍是旧形态，登记为 F-0004）。

---

## 七、最终判定与下一步

**判定：`provisional`（product_state=rework，assurance_state=partial）。**

理由：P1 的四项阻塞与 F-03 已全部闭合；两轮独立复审（第二轮无源码级阻塞）与本验收的独立复核（门禁 45 套件/394 用例全绿、探针 11/11、时序自证与 git 核对、role-verify PASS）都支持「代码就绪」；但**真机与端到端未验**，任务书 §完成条件第 6 条明确要求此时不得宣称 P1 最终完成。故本任务收口为 provisional，而不是 accepted。

- 唯一下一步：开一轮**真机合成链路抽查任务**（按本验收 §五 F-0003 修正后的窗口设计执行步骤 1—7，需要 controller/用户授权向真实设备安装隔离 debug 包）。
- 后续修复项：F-0001（补一条 `Debug` 侧守卫测试）、F-0002（报告行号）、F-0004（RSS Web 端同形竞态）、F-0005（阶段四是否限定阅读路径专用的口径决定）。
- 提交 / 推送 / 发布状态：**均未授权、均未执行**；本轮全部改动仍在 `codex/manga-import` 的未提交工作树中，提交边界由用户决定。

---

## 八、收口说明

- 本验收之后按 `agent-protocol close book-source-p1-rework-r3` 收口：写入 `40-archive.json`、`40-archive.md` 与 `.agent-protocol/ARCHIVE.md` 索引，`CURRENT.md` 指针置 `task_id: none`、`state: provisional`、`last_closed_task: book-source-p1-rework-r3`。
- P1 原任务 `book-source-p1-diagnostics` 的证据保持冻结、只读引用；两者关系（standard 档两轮复审用尽后按收敛规则转后续任务）已写入本任务任务书 §交接说明。
- 未关闭发现共 7 条（`findings.json`），随归档进入索引，供真机轮次与后续修复任务直接引用。
