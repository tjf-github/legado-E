<!-- agent-protocol-binding
round_id: reviewer-r001
task_digest: 695f0681c170817674ab0e4e4e368ff0743ed238a9848e85008f4ebfae42256b
snapshot_digest: ed3e6bb148266742ca782c573e732caf79612262eddacea144099ed2fa0c8113
--># book-source-p1-rework-r3 — 第三轮独立复审报告（reviewer: claude，只读静态复审）

- 任务：`.agent-protocol/tasks/book-source-p1-rework-r3`，mode=`dsh-claude`，route_revision=8，assurance=standard，分支 `codex/manga-import`，HEAD `b8ba92e194468f0d7e6103c1175ba9f2a8233d95`（按任务书与执行报告声明，我无法独立核实）。
- 复审方式：**只读静态复审**。`06-review-commands.json` 的 `commands` 为空数组 → 按任务书 §复审冻结与轮次，reviewer 侧一切构建/测试/探针/`git`/真机检查一律 **NOT RUN**。本会话的 `Grep`/`Glob` 也不可用（`rg` 进程启动被拒：`EPERM: operation not permitted, uv_spawn 'rg'`），改为按白名单显式路径逐个 `Read`，因此**任何"全仓库搜索"类结论都不成立**，报告中涉及"无调用点/未改动"的判断都限定在我实际读过的文件范围内。
- 读取范围（23 个文件，全部在任务书白名单内）：本任务 `00-task.md`、`10-execution.md`、`10-execution.summary.json`、`11-implementation-notes.md`、`05-routing.md`、`06-review-commands.json`、`32-probe-source.java`、`32-probe.log`；P1 任务 `30-acceptance.md`、`20-review.md`（只读）；生产源码 `help/book/diagnostics/{BookSourceDiagnostics,DiagnosticRedactor,DiagnosticStage,DiagnosticClassifier}.kt`、`model/Debug.kt`、`help/book/ContentProcessor.kt`、`model/webBook/BookContent.kt`、`model/analyzeRule/AnalyzeByXPath.kt`、`utils/SerialTaskQueue.kt`、`web/socket/BookSourceDebugWebSocket.kt`；测试 `BookSourceDiagnosticsContractTest.kt`、`SerialTaskQueueTest.kt`；构建脚本 `app/build.gradle`。
- 未读：`15-reviewer-raw.md`（禁读）、`PROJECT.md`（任务书未列入 reviewer 白名单）、`app/build/**`（门禁日志与测试 XML，白名单外）、`rounds/**`、`docs/book-source-audit/**`、`语料归因.md`、`bookshelf.json`、真实书源/语料、签名材料与凭据、两份计划书（本轮不需要）。

---

## 一、结论

**有阻塞项。** 结论分三层，请勿混读：

1. **本任务点名的 5 项交付（P1 验收阻塞 1—4 与 F-03）在源码层面我逐项核对为「已落地」**，未发现伪造快照、正文语义改变、越权写入白名单外文件或凭据外泄的证据（见第三节）。这一点上执行报告与工作树**一致**。
2. **但本轮仍不足以判 P1 通过**：三项阻塞（F-01 中、F-02 中、F-04 中）+ 三项低项（F-03/F-05/F-06）。其中 F-01 是**代码级真实性缺陷**，会让新功能在特定时序下把「网络错误」贴到一个实际执行成功的阶段四读数上，与执行报告 §7 真机步骤 5 的预期直接冲突；F-02 是**完成条件明文要求（任务书 §完成条件第 1 条）的守卫测试缺失**；F-04 是**执行侧回执/runner 封套未闭合**，而报告 §2 第 6 行把它写成已处置。
3. **证据侧**：门禁（exit 0 / 45 suites / 393 tests）与真机项在我的授权下**无法独立复核（NOT RUN）**，我只能核实「产物侧自洽」（第四节 5—7 条）。因此即便 F-01/F-02/F-04 被修掉，本轮仍须 acceptor 在最终工作树上复跑门禁并做真机抽查。

---

## 二、发现清单

### F-01 [中 · 功能/真实性] `recordFailure` 会把链路级网络失败贴到「阅读路径产生的阶段四快照」上，摘要因此写出假读数

- 位置：`app/src/main/java/io/legado/app/model/Debug.kt:435-442`（`contentDebug.onError` → `diagnostics?.recordFailure(it)`，第 438 行）；`app/src/main/java/io/legado/app/help/book/diagnostics/BookSourceDiagnostics.kt:214-228`（第 216 行 `DiagnosticStage.values().lastOrNull { snapshotOf(it)?.executed == true }`，第 222-227 行 `record(snapshot.copy(errorClass=…, note=…))` 保留原摘要与耗时）；`app/src/main/java/io/legado/app/help/book/ContentProcessor.kt:234-245`（本轮新增的阶段四真实快照写入点）。
- 为什么是本轮引入的：旧实现在收尾无条件 `markNotExecuted(READ_PROCESS, …)` 覆盖阶段四，这个错误归属被覆盖动作**掩盖**了；本轮按验收阻塞 2 的要求「不得覆盖已执行快照」后，被掩盖的错误归属**变成可见输出**。`recordFailure` 的注释「已执行阶段里最靠后的一个就是产出到这里为止」在阶段四可能来自**另一次调用（阅读路径）**之后不再成立。
- 具体失败场景（断网 + 同章缓存阅读，正是本任务要支持的「会话存活期间同章阅读」场景）：
  1. 在应用内调试页对书源 S 的书 B 第 C 章发起调试；会话 S1 存活（UI 调试链路不发送 `state=1000`，故 `Debug.diagnostics`/`callback` 未被清空）。
  2. 抓取因断网变慢/超时；其间用户切到阅读页打开同一章 C（该章已有下载缓存，阅读路径**不抓网**）→ `ContentProcessor.getContent` 命中 `Debug.diagnosticsFor` → 写入真实阶段四快照（`executed=true`）→ `Debug.onReadingStageRecorded()` 补打含真实读数摘要（本轮新功能，行为正确）。
  3. 调试链路的抓取随后失败（`UnknownHostException`）→ `recordFailure` 取 `lastOrNull { executed }` = `READ_PROCESS`（`values()` 顺序里 order=4 最大）→ 把该快照改写成 `errorClass=网络错误`，并**保留**阅读阶段的 `summary=len=17,lines=1,imgs=0`、`elapsedMs=3`。
  4. `finishContentDiagnostics` → `markNotExecutedIfMissing(READ_PROCESS)` 因已有快照而跳过 → `markMissingStagesNotExecuted` 把 1/2/3 标未执行 → 打印。
- 实际输出：`1.提取后 未执行 / 2.HTML格式化后 未执行 / 3.书源替换后 未执行 / 4.阅读处理后 网络错误 len=17,lines=1,imgs=0 耗时3ms`。
- 双重冲突：(a) 第 4 行声称一个**实际成功执行**的阅读处理发生了网络错误（反向误导，与阻塞 2 要消除的「未执行覆盖真实读数」同类）；(b) 与执行报告 §7 真机步骤 5 的预期「断网…预期第 1 行显示 `网络错误`，其余阶段显式「未执行」」（`10-execution.md:163`）**不符**——本场景下第 1 行是「未执行」、第 4 行才是「网络错误」。
- 建议（最小改法）：阶段四不在书源调试链路的产出范围内，把 `recordFailure` 的候选集合排除它，例如 `DiagnosticStage.values().lastOrNull { it != DiagnosticStage.READ_PROCESS && snapshotOf(it)?.executed == true }`，落点为空时沿用现有 §第 217-219 行的「归到阶段一」分支；或给 `recordFailure` 增加一个「本次链路自己写入过的阶段」参数。同时建议在报告里把 §7 步骤 5 的预期按改法写清。

### F-02 [中 · 证据缺口] 任务书明文要求的「阶段四**展示时机**」守卫测试不存在；报告 §2 把探针 check 4—6 记为该项目的证据属过度映射

- 位置/依据：任务书 `00-task.md:64`「上述 1—5 项全部落地，且每一项都有对应的守卫/契约测试（异常消息守卫、**阶段四覆盖与展示时机**、串行投递保序、URL 主机名保留）」；判定逻辑在 `Debug.kt:471-478`（`diagnostics?/callback?/executed?/claimReadingStageReport()` 四个条件 + 补打）；生产触发点在 `ContentProcessor.kt:243-245`。
- 我核对了两条可能的证据链，都不成立：
  - 测试侧：`BookSourceDiagnosticsContractTest.kt:585-590`（`readingStageReportIsClaimedOnlyOncePerSession`）与 `:592-598`（`disabledCollectorNeitherClaimsNorReportsReadingStage`）只调 `BookSourceDiagnosticsCollector.claimReadingStageReport()`，**从不调用** `Debug.onReadingStageRecorded()`，也断言不到「何时输出摘要」。
  - 探针侧：`32-probe-source.java:46-59` 的 check 4—6 全部是 `BookSourceDiagnosticsCollector` 直调（`markNotExecutedIfMissing` + `snapshotOf` + `summaryLines`），**没有一条触及 `Debug`/`ContentProcessor`**。报告 `10-execution.md:32` 与 `:123-128` 把「④展示时机的证据」列为「探针 check 4—6」与 `Debug.kt（… onReadingStageRecorded）`，是位置引用而非证据。
- 为什么不是「随手补一条单测」：`Debug.onReadingStageRecorded()` 的正面分支必然走到 `printBookSourceDiagnostics` → `Debug.log` → `if (BuildConfig.DEBUG) Log.d(...)`（`Debug.kt:55-57`），而 `app/build.gradle` 当前**没有** `testOptions`/`unitTests`/`returnDefaultValues`（我通读该文件确认，M-2 的全局弱化确实已回退），android.jar 桩会抛 `RuntimeException: Stub!`；`ContentProcessor.getContent` 又依赖 `appDb`/`appCtx`/`Build.VERSION`。故该逻辑在现有测试配置下**不可直调**：负面分支（`diagnostics==null`/`callback==null`/`executed!=true`/认领失败）理论上可测，正面分支（真输出第二份摘要）不可测。
- 报告其实在 `10-execution.md:146`（§6.3）承认了「真机上…未实机观察」，态度是诚实的；问题在于 §2 的证据列把它写成已有守卫/探针覆盖，且**完成条件第 1 条**因此客观未满足。
- 建议：二选一并写进报告——(a) 把判定条件下沉为不依赖 Android 的 collector 方法（如 `BookSourceDiagnosticsCollector.shouldReportReadingStage(): Boolean`），用现有契约测试钉住「会话结束→不补打」「已认领→不重复」「无真实读数→不补打」三态；(b) 或明确把该守卫测试写成 NOT RUN，并把「完成条件第 1 条是否算达成」交 controller 裁定，不要在 §2 以 `Debug.kt（onReadingStageRecorded）` 作为证据呈现。

### F-03 [低 · 安全/脱敏回归] F-03 的修法在**空 authority** 或 **userinfo 含 `/`** 的畸形 URL 上不再遮蔽 userinfo（相对修前是行为回退）

- 位置：`app/src/main/java/io/legado/app/help/book/diagnostics/DiagnosticRedactor.kt:122-124`：
  `val authority = authorityAndPath.substringBefore('/')` / `val at = authority.lastIndexOf('@')` / `val visibleHost = if (at >= 0) authority.substring(at + 1) else authority`。
- 具体反例（两个都能确定地复现，均为合成输入，不涉及真实凭据）：
  1. 空 authority：输入 `请求 https:///user:pw@api.example.com/x 失败` → `authorityAndPath = "/user:pw@api.example.com/x"` → `authority = ""` → `at = -1` → 输出 `请求 https:///user:pw@api.example.com/x 失败`。**修前**在整段 authority+path 上取最后一个 `@`，输出是 `请求 https://api.example.com/x 失败`（凭据被丢掉）。
  2. userinfo 含 `/`：输入 `请求 https://user:pa/ss@api.example.com/x 失败` → `authority = "user:pa"` → `at = -1` → 输出 `请求 https://user:pa/ss@api.example.com/x 失败`（凭据片段原样保留）；修前会被遮蔽。
- 影响与诚实边界：两者都需要畸形 URL（RFC 3986 不允许 userinfo 含 `/`，空 authority 也不是合法 URL），正常书源规则下不会出现，所以**不是可达的真实凭据泄漏**；但它是 F-03 修法「只在 authority 内找 userinfo」的一个明确反例，方向上把之前"过度切掉主机名"的安全副作用一并去掉了。F-03 针对的正当输入（`https://api.example.com/@user/profile`、`https://user:p4ssw0rd@api.example.com/@user/profile`）我已按代码静态复算确认修对了，与 `BookSourceDiagnosticsContractTest.kt:223-239` 和探针 check 7—8 自洽。
- 建议：对「切出的 authority 为空」保留保守回退（`authority.isEmpty()` 时在 `authorityAndPath` 上取最后一个 `@`，即旧行为），并在 KDoc 里写明「userinfo 含 `/` 的畸形 URL 不再被视为 userinfo」这一残余边界。

### F-04 [中 · 流程/回执，非代码] 执行侧的 runner 封套与 `role-verify` 未闭合，报告 §2 第 6 行把它写成已处置

- 事实（我读两个文件即可核实）：`10-execution.summary.json:5` 声明 `"source": "role"`、`"round_id": "executor-r001"`，而同文件 `:8` 的 `note` 与 `10-execution.md:10`、`:36` 都自述「本轮由人类在 DSH 窗口直接指派」，即**没有 runner 封套**。`source=role` 与「人类窗口直接指派」自相矛盾。
- 判定依据：P1 第二轮验收正是把同类事实记为「顶层 executor 因 DSH 第二轮由人类窗口直接指派、无 runner 封套…故整体 FAIL。不能把这个 FAIL 解释为代码失败，也不能冒充本轮执行来源已被协议绑定」（`book-source-p1-diagnostics/30-acceptance.md:59`）；P1 阻塞发现 4「任务授权与回执未闭合…二者均须在下一轮路由/任务书中留痕」（同文件 `:67`）本轮只闭合了 M-3 那一半。
- 报告的问题：`10-execution.md:36` 用「本轮改由 controller 用 `agent-protocol role reviewer -Entry dsh` 拉起 claude 复审」回答了「executor 回执」，这只覆盖 reviewer 一侧；任务书 `00-task.md:68` 要求的「独立复审（claude，**runner 封套**）」在执行侧仍无证据。
- 严重程度与处置建议：不是代码缺陷，但会像 P1 一样在验收侧触发整体 FAIL。我没有执行 `agent-protocol role-verify`（reviewer 可执行检查为空 → NOT RUN），因此**无法判定**它本轮的期望轮次；请 controller/runner 落盘该项回执，或把 §2 第 6 行改写为「执行侧 runner 封套未闭合 + role-verify NOT RUN」，不要以「已处置」记录。

### F-05 [低 · 口径/可见性] 阶段四的「一次性补打权」可被非阅读路径（导出/换源统计）先消费，导致真机预期中的阅读摘要不再出现

- 位置：`BookSourceDiagnostics.kt:110-113`（`claimReadingStageReport` 一次性）+ `Debug.kt:471-478` + `ContentProcessor.kt:234-245`；`accepts` 的匹配口径见 `BookSourceDiagnostics.kt:92-99`。
- 依据（**我用的是执行方自己的承认，不是我新读的文件**）：`10-execution.md:150`（§6.7）承认「同一本书同一章的**导出/换源统计**路径确实能满足条件并写入阶段四，其读数不代表阅读页正文」；P1 复审 F-04（`book-source-p1-diagnostics/20-review.md:50-55`）给出了具体调用点（`ExportBookService` 以 `includeTitle=false, reSegment=false, chineseConvert=false` 遍历全书章节调 `contentProcessor.getContent`）。我**没有**独立阅读 `ExportBookService.kt`/`ChangeBookSourceViewModel.kt`（超出本轮必要范围），此条依赖上述两处已披露事实。
- 具体失败场景：会话存活期间，用户先触发同一本书的导出（或换源字数统计），第 C 章走的正是 `ContentProcessor.getContent` → 写入阶段四 → `onReadingStageRecorded` 认领并打印一份**以导出参数生成**的 `4.阅读处理后 len=..,lines=..` 摘要；此后用户真的在阅读页打开第 C 章时，认领权已被用掉 → **不再输出**，而报告 §7 步骤 2 与 `10-execution.md:38` 的「更正预期」恰好要求它出现。读出值也可能与阅读页正文不一致（导出不含标题、不做重分段/简繁）。
- 建议：本轮已按验收建议收窄表述、未改行为，可不阻塞；但请在报告里补一句「补打权可能被非阅读路径消费，真机步骤 2 若先做过导出/统计则不成立」，或由 controller 裁定把阶段四限定为阅读路径专用（`accepts` 增加调用来源维度）。

### F-06 [低 · 简化/证据可读性] 两处小项

1. **`SerialTaskQueue.close()` 没有生产调用点**（`utils/SerialTaskQueue.kt:45-47`）：我读过的 6 个可能调用它的生产文件（`Debug.kt`、`ContentProcessor.kt`、`BookSourceDebugWebSocket.kt`、其余 diagnostics 三文件）均未调用，只有 `SerialTaskQueueTest.kt:97-107` 调用。是"为改动引入的、只被测试使用的公共 API"（消费者随 scope 取消这条路径已足够）。仅供参考，不构成返工项。
2. **`32-probe.log` 是 UTF-16LE（PowerShell 5.1 重定向默认编码）**：我用 `Read` 打开时字母被 NUL 分隔、中文呈乱码（`��P A S S  b l o c k e r 1 _ …`）；报告 §5.1 转写的内容与 9 条 PASS/`probe_failures=0/9` 我逐行比对**一致**，但 acceptor 直接打开该文件会看到乱码。建议按 UTF-8 重新导出，或在报告里注明文件编码。

---

## 三、逐项核对：本任务 5 项交付在工作树中的落地情况（工作树为准）

| 交付项 | 我的核对结果 | 证据（工作树行号） |
| --- | --- | --- |
| 1 默认摘要不得泄漏任意正文 | **已落地**。`describeError` 只返回 `"异常类型 " + error.javaClass.simpleName + "，消息默认不输出"`，全方法不引用 `error.message`；阶段内异常（`record` 的 `error != null` 分支）与链路级兜底（`recordFailure`）共用它。旁证核实：`DiagnosticClassifier.classify` 仍会用 `error.message` 做**分类**（`DiagnosticClassifier.kt:101`），但只返回枚举类别，文本不进入快照——这不算漏点。 | `BookSourceDiagnostics.kt:164-165`、`:138-143`、`:214-228`；守卫测试 `BookSourceDiagnosticsContractTest.kt:515-558`（3 条，逐字核对我数到 49 个 `@Test`） |
| 2 阶段四真实性与展示时机 | **代码已落地**（如实部分完备、可见部分完备，但见 F-01 的错误归属与 F-02 的守卫缺口）。`markNotExecutedIfMissing` 在已有快照时直接返回；`finishContentDiagnostics` 改用它；`ContentProcessor` 先 `record(READ_PROCESS, …)` 后调 `onReadingStageRecorded()`（顺序正确，`record` 的 upsert 会替换掉收尾写下的"未执行"）；会话已结束（`callback == null`）时不输出、不伪造；`claimReadingStageReport` 在诊断关闭时返回 false。 | `BookSourceDiagnostics.kt:173-177`、`:110-113`、`:231-237`；`Debug.kt:453-461`、`:471-478`；`ContentProcessor.kt:234-245` |
| 3 Web 端投递保序 | **已落地（提交顺序层面）**。`printLog` 不再每条日志起一个并发 IO 任务，改为提交到同一 socket 的 `SerialTaskQueue`；`Debug.kt` 侧「先 `finishContentDiagnostics()` 再 `log(state=1000)`」的提交顺序因此等于执行顺序，终止任务必然排在四行摘要之后。队列 = 无界 `Channel` + 单消费者，`trySend` 的 FIFO 是库语义；单任务失败不中断后续（`CancellationException` 重抛、其余 `Throwable` 吞掉）。**边界**：这只证明"投递顺序"，`send()` 在 NanoWSD 内部是否同步写 socket（是否存在库层缓冲/写线程）我无法从仓库内核实（nanohttpd 是外部依赖），故"四行完整到达后 socket 才关闭"仍属真机待验（报告 §6.1 已如实写 NOT RUN）。 | `BookSourceDebugWebSocket.kt:26-32`、`:93-109`；`utils/SerialTaskQueue.kt:20-48`；测试 `SerialTaskQueueTest.kt:24-107`（4 条，与报告数字一致） |
| 4 M-3 `AnalyzeByXPath.kt` 裁定 | **已闭合（由任务书给出授权）**。`00-task.md:51` 显式把该文件纳入白名单并写明"保留首轮等价替换，不再新增改动"；工作树中该文件仍只有那处替换（`:136-139`），我独立复核语义：`getResult` 返回 `List<JXNode>?`，对 `List` 而言 `it.joinToString("\n")` 与 `TextUtils.join("\n", it)` 都是"元素 `toString()` + 分隔符拼接、空集合得空串"，等价成立。 | `00-task.md:51`；`AnalyzeByXPath.kt:42-49`、`:132-140` |
| 5 F-03 URL 主机名保留 | **已落地（正当输入）**，但见 F-03 的畸形输入回退。`authorityAndPath.substringBefore('/')` 后再取 `lastIndexOf('@')`，路径里的 `@` 不再切主机名；userinfo 含 `@`（`user:p@ss@host`）仍取到最后一个 `@`，方向正确。 | `DiagnosticRedactor.kt:100-131`（关键在 `:122-124`）；测试 `BookSourceDiagnosticsContractTest.kt:223-239` |

**交叉核对（产物侧自洽，非运行证据）**

1. `app/build.gradle` 通读：**无** `testOptions`/`unitTests`/`returnDefaultValues`，依赖块只有 `testImplementation(libs.junit)`，未新增 Robolectric/Mockito → P1 的 M-2 越界确实已回退、严格 JVM 测试默认保持（与报告 §1.6 一致）。
2. 测试条数我按源码独立计数：`BookSourceDiagnosticsContractTest` = **49 个 `@Test`**（P1 为 40，新增 9 条与报告 §3.2「3+4+2」完全对应）；`SerialTaskQueueTest` = **4 个**。P1 基线 44 suites/380 tests → 本轮 45/393 = +1 类、+13 例（9+4），**与报告 `10-execution.md:104-111` 逐项对账一致**。这只能证明源码侧自洽，不能证明运行结果。
3. `32-probe.log` 内容与报告 §5.1 的转写逐行一致（9 条 PASS、`probe_failures=0/9`）；文件编码为 UTF-16LE（见 F-06.2）。
4. 「诊断关闭零额外处理」复核：`ContentProcessor.getContent` 中所有计时与 `record`/`onReadingStageRecorded` 都在 `diagnostics != null` 之内；`diagnostics?.record(..., text = contents.joinToString("\n"), ...)` 的实参在接收端为 null 时**不会被求值**（Kotlin 安全调用短路实参求值），因此关闭时不产生额外拼串；`BookContent.kt:192/283/306` 的计时也都写成"拿到接收端才读时钟"。唯一无条件开销是每章一次 `@Volatile` 读 + 一次 `accepts()`（三次字符串比较）。
5. 「不改变返回值」复核：本轮新增代码没有出现在任何 `return` 表达式中，`mContent`/`contentStr`/`contents`/`bookContent` 的取值路径未变（`ContentProcessor.kt:104-247`、`BookContent.kt:188-259` 通读）。
6. 安全面：改动未新增字符串→SQL/路径/shell/命令的拼接，无路径穿越、无命令注入、无 XSS 面变化；新增的唯一跨线程组件 `SerialTaskQueue` 只承载闭包，队列无界但生命周期随 socket scope；诊断数据全内存、不落盘、不联网（与 KDoc 一致）。`describeError` 的新输出不含任何用户数据（仅 Java 简单类名）。
7. 一处已裁定的既有限制未被误报：`DiagnosticRedactor.skeleton` 仍无生产调用点，但 KDoc 已如实标注且 P1 验收 L-3 已判「非阻塞、不必反复复审」（`book-source-p1-diagnostics/30-acceptance.md:29`），我不作为本轮发现。

---

## 四、我实际执行过的核对动作与输出

1. `Read` 任务书 `00-task.md` 与执行报告 `10-execution.md`、机器摘要、实施说明、路由记录 —— 建立白名单、完成条件、停止条件与待验证主张清单。
2. `Read` `06-review-commands.json` → `{"version":1,"network_policy":"deny-declared-not-enforced","commands":[]}` ⇒ **reviewer 侧无任何可执行检查，全部 NOT RUN**。
3. `Grep`/`Glob` 各调用一次（`SerialTaskQueue`、`CoroutineScope by MainScope()`）→ 均返回 `EPERM: operation not permitted, uv_spawn 'rg'` ⇒ 放弃全仓库搜索，改为白名单显式路径逐个 `Read`（共 23 个文件，见开头清单）。
4. `Read` 全部 6 个 diagnostics 生产文件 + `Debug.kt` + `ContentProcessor.kt` + `BookContent.kt` + `AnalyzeByXPath.kt` + `SerialTaskQueue.kt` + `BookSourceDebugWebSocket.kt` + `app/build.gradle`，逐行核对第三节表格中的每条主张。
5. 独立复算 F-01 的失败链路：`recordFailure` 的 `lastOrNull { executed }` 在「仅 `READ_PROCESS` 有 `executed=true` 快照」时必然选中阶段四（`DiagnosticStage.values()` 顺序 1→4），`snapshot.copy` 保留 `summary`/`elapsedMs` ⇒ 得到「4.阅读处理后 网络错误 len=17,lines=1,imgs=0 耗时3ms」。
6. 独立复算 F-03 的两个反例（按 `stripUrlSecrets` 的 `index/schemeAt/start/end/cut/authority/at/visibleHost` 逐步推演），并对照 `book-source-p1-diagnostics/20-review.md:43-48`（F-03 原文）确认修前行为确实是"切掉主机名、但凭据也一并丢弃"。
7. 独立复算 M-3 的语义等价性（`List<JXNode>` 上 `joinToString("\n")` vs `TextUtils.join("\n", it)`，含空集合情形）。
8. 按源码计数 `@Test`：`BookSourceDiagnosticsContractTest=49`、`SerialTaskQueueTest=4`；与 P1 的 44/380 对账 45/393。
9. `Read` P1 `30-acceptance.md` 与 `20-review.md`，把 F-01/F-02/F-04/F-05 与 B-1…B-5、M-1…M-3、F-01…F-07 的处置状态对齐，避免重复上报已被裁定为非阻塞的项（L-3、F-05 的 ReplaceRule/ContentProcessor 证据强度）。
10. `Read` `32-probe-source.java` 与 `32-probe.log`，逐行比对报告 §5.1 的转写（一致；并识别出文件的 UTF-16LE 编码）。
11. 未执行任何写操作、未运行任何命令、未改 `10-execution.md`/`30-acceptance.md`、未读 `15-reviewer-raw.md`。

---

## 五、我没检查到的范围（请勿读作已覆盖）

1. **一切运行侧证据 NOT RUN**：`:app:compileAppDebugKotlin`、`:app:testAppDebugUnitTest`、`assembleAppDebug`、合成探针、`:app:lintAppDebug`、`git status/diff`、`agent-protocol role-verify` —— 命令数组为空 + 本会话无 shell。因此「exit 0 / BUILD SUCCESSFUL in 3m38s / suites=45 tests=393 failures=0」我**没有**独立核实，只核实了源码侧自洽（第三节第 2 条）；报告引用的 `app/build/p1-r3-gate.log`、`app/build/test-results/**` 在 `app/build/**`（白名单外），我**未读取**。
2. **无法做 diff 级核对**：因此「白名单外写入为零」「`app/build.gradle` 与 HEAD 逐字节一致」「未提交/未推送/未切分支」「既有脏基线未被清理」「本轮改动集合等于报告 §3 所列」**全部无法独立证明**。我读到的 11 个生产/测试文件内容与报告描述一致，但这不等于"只有这些文件被改过"。执行报告 §4 的「本轮新增的生产改动只有 … 两处」与其后一句「其余改动落在 P1 已建的 … `Debug.kt`、`ContentProcessor.kt`…」表述上自相矛盾（前者指"白名单新增"，后者才是"本轮改过的文件"），建议改写以免验收方误读改动集合。
3. **全仓库符号/调用点搜索不可用**（`rg` 不可用）：F-06.1「`close()` 无生产调用点」、「其它 `Debug.Callback` 实现未改」这类判断仅限于我读过的文件；报告 §6.6 关于 `ui/book/source/debug/BookSourceDebugModel`、`ui/rss/**`、`RssSourceDebugWebSocket` 未改的声明我**未核实**（未读这些文件）。
4. **真机/UI/Web 端全部 NOT RUN**：调试页四阶段渲染、阶段四补打的真实读数是否出现、Web 端四行摘要的实际**到达**顺序（我只证明提交顺序）、`@webjs:`、真实书源/真实站点、开关前后正文逐字一致。未读取真实书源/语料/`bookshelf.json`/凭据，未操作设备。
5. **未读**：`PROJECT.md`（不在任务书给出的 reviewer 白名单内，故未按其内容核对构建/敏感路径声明）、`15-reviewer-raw.md`（禁读）、`rounds/**`、两份计划书、`AnalyzeRule.kt`（白名单内但报告称本轮未改，读它也无法判断"是否改过"）、`ExportBookService.kt`/`ChangeBookSourceViewModel.kt`（F-05 因此依赖执行方与 P1 复审已披露的事实）。
6. **未覆盖的端到端行为**：并发分页 `mapAsync` 与阶段一/二采集权的交互、音频/视频分支、Android 8 分支、`record` 的 upsert 在多页并发下"最后写入者胜"的一致性、NanoWSD 库层的 `send` 语义。
7. **F-02 的"不可测"结论依赖一条假设**：即 `testAppDebugUnitTest` 下 `BuildConfig.DEBUG == true`（故 `Debug.log` 首行即调 `android.util.Log.d` 桩）。我未运行测试验证该假设；若该假设不成立，F-02 的建议应改为"补一条直调 `Debug.onReadingStageRecorded` 的契约测试"，结论方向不变（当前确实没有这样的测试）。

---

## 六、给 acceptor 的最短路径建议

1. 在**最终工作树**上复跑任务书 §完成条件第 3 条的字面命令，记录退出码与直接解析 XML 得到的 `suites/tests/failures/errors/skipped` + XML 时间戳（闭合我无法核实的门禁侧）。
2. 把 F-01 当作本轮唯一的**代码级**返工项裁定：是否接受"阶段四不参与 `recordFailure` 落点"的最小改法；若接受，请同时修正执行报告 §7 步骤 5 的预期。
3. 把 F-02 交 controller 裁定完成条件第 1 条是否达成（建议至少把判定条件下沉为可测的 collector 方法）；不要以「探针 check 4—6」作为该项目的证据。
4. 闭合并落盘执行侧的 runner 封套/`role-verify` 回执（F-04），否则会复现 P1 第二轮的"整体 FAIL"。
5. F-03/F-05/F-06 建议登记为后续项（F-03 的三行回退 + 一条守卫测试即可），不要写成"已闭环"；真机抽查仍按执行报告 §7 的 1—7 步执行，并在步骤 5 之外补一条"先阅读后断网"的对照（对应 F-01）。
