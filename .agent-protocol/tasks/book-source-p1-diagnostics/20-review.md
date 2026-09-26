<!-- agent-protocol-binding
round_id: reviewer-r002
task_digest: cf12fc2384da644c9cddd96eda6d4dadfac2d631fb0a75c233317c4a8bf51ede
snapshot_digest: 5166b7ff346e260a4e381368ec6f4f75069fd7eb33e7d4f22e1c41fa821e3665
--># book-source-p1-diagnostics — 第二轮独立复审报告（reviewer: claude，只读静态复审）

- 任务：`.agent-protocol/tasks/book-source-p1-diagnostics`，返工轮（executor round 2，无 runner 封套），mode=triad，route_revision=7，HEAD `b8ba92e194468f0d7e6103c1175ba9f2a8233d95`，分支 `codex/manga-import`。
- 复审方式：**只读静态复审**。本会话没有 shell（Bash 工具被禁用），`06-review-commands.json` 为 `{"version":1,"network_policy":"deny-declared-not-enforced","commands":[]}` → 所有构建、测试、探针、`git`、真机检查一律 **NOT RUN**。以下结论全部来自工作树源码与测试源码的直接阅读，不采信执行报告自述。
- 读取范围：`00-task.md`、`10-execution.md`、`10-execution.summary.json`、`11-implementation-notes.md`、`13-reviewer-context.json`、`06-review-commands.json`、`30-acceptance.md`；`app/src/main` 四阶段相关生产文件与调用链片段；`app/src/test` 六个契约/诊断测试类；`app/build.gradle`。未读 `PROJECT.md`（任务书未列入 reviewer 白名单）、`app/build/**`（日志与测试 XML，白名单外）、`rounds/**`、`15-*-raw.md`、首轮 `20-review.md` 原文。

## 一、结论

**有阻塞项。**

返工项 B-1、B-2、B-3/L-5、B-5、M-1、M-2、L-1（主体）、L-2、L-3、L-4 与「额外观察」在**源码层面均可核实为已落地**（逐项见第三节），我没有发现伪造快照、正文语义改变、越权写入新文件或凭据外泄。但有 3 项阻塞：

| # | 阻塞项 | 性质 |
| --- | --- | --- |
| 阻塞-1 | **阶段四（阅读处理后）在实现上不可观测**：`Debug.finishContentDiagnostics` 无条件把 READ_PROCESS 覆盖成「未执行」，而摘要只在这里输出一次；真实经过阅读处理的读数既被抹掉、也不会再显示。报告 §8 第 5 步的真机预期与代码矛盾。（发现 F-01） | 代码缺陷 + 报告与代码不一致 |
| 阻塞-2 | `AnalyzeByXPath.kt` 越界改动仍在工作树，任务书写白名单未包含该文件，本轮无人授权（M-3 转 controller 裁定）。（发现 F-06） | 授权越界（未处置） |
| 阻塞-3 | 本轮门禁**无任何可被本轮独立复核的证据**：任务书 §8.3 字面命令最后一次成功运行对应的是「少一个未被引用字段」的源码，最终源码只在改过 Gradle 主目录/启动方式/两个开关的等价通道下验证（报告 §5.1 自述），且我无 shell、无 runner 封套。需要 acceptor 复跑闭合。（发现 F-07） | 证据缺口（NOT RUN） |

B-4 的处置（发现 F-02）我认为**不构成阻塞**：顺序修正本身落在白名单内且已经改对，残余竞态在 `HandlerUtils.kt`/`BookSourceDebugWebSocket.kt`（白名单外）里，属需要 controller 决定是否扩范围的事项。

---

## 二、发现清单

### F-01 [阻塞 · 中] 阶段四被无条件覆盖，真实阅读处理读数既丢失又永不可见

- 位置：`app/src/main/java/io/legado/app/model/Debug.kt:452-460`（`markNotExecuted(READ_PROCESS, …)` 在 `printBookSourceDiagnostics()` 之前且**无条件**执行）；`app/src/main/java/io/legado/app/help/book/diagnostics/BookSourceDiagnostics.kt:151-163` 与 `:202-208`（`markNotExecuted` → `record(StageSnapshot)` → `removeAll { it.stage == snapshot.stage }`，即 upsert **覆盖**同阶段已有快照）；产生阶段四的调用点 `app/src/main/java/io/legado/app/help/book/ContentProcessor.kt:110` 与 `:234-240`；摘要唯一输出点 `Debug.kt:128-131`（`printBookSourceDiagnostics` 只被 `Debug.kt:459` 调用）。
- 具体失败场景：书源调试会话存活期间，用同一书源、同一本书、同一章走真实阅读路径（`ReadBook.kt:817` 或 `ExportBookService.kt:548` 等同样命中 `Debug.diagnosticsFor(book.origin, book.bookUrl, chapter.url)`）。该次阅读在 `ContentProcessor` 阶段四写出一条**真实**快照；随后调试链路在 `WebBook.getContent` 的 `onSuccess` 里调用 `finishContentDiagnostics()`，第 454 行把这唯一一条真实读数替换为「未执行 (书源调试链路止于 BookContent，未进入阅读处理)」，第 459 行立刻把它打印成摘要。结果：调试摘要明确声称一个**实际执行过**的阶段「未执行」——这与任务书「未实际经过某阶段时必须如实标记未执行，不能伪造快照」的同一诚实性要求相反（反向伪造）。若阅读发生在调试链路结束之后，则阶段四快照只落进 collector，`printBookSourceDiagnostics` 再无第二次调用，同样永远看不到。
- 与报告矛盾：`10-execution.md` §8 第 5 步要求 acceptor「打开同一书源、同一本书、同一章正常阅读：预期 `4.阅读处理后` 有真实读数」。按现有实现，该预期**在任何先后顺序下都无法满足**：先读 → 被覆盖；后读 → 无输出。同理 §2 表中「`READ_PROCESS` 保留专属原因」的表述掩盖了「覆盖」这一副作用。
- 建议：最小修复是把第 454-457 行的无条件标注改为「仅当 `collector.snapshotOf(READ_PROCESS) == null` 时才写专属原因」（`markMissingStagesNotExecuted` 已有这个语义，第 170-177 行）。但这只解决「如实」，不解决「可见」——阶段四是否应在阅读路径上可被看到（例如阅读链路命中会话时再输出一次摘要），属产品口径，建议由 controller 明确；同时必须更正 `10-execution.md` §8 第 5 步的预期，不能让 acceptor 按一个不可能通过的步骤去验。

### F-02 [中] B-4 只修了「入队顺序」，Web 端投递仍是无序的 5 个并发 IO 任务

- 位置：`Debug.kt:428-442`（`onSuccess`/`onError` 先 `finishContentDiagnostics()` 再 `log(state=1000/-1)`）；`app/src/main/java/io/legado/app/utils/HandlerUtils.kt:42-50`（`runOnIO`：`isMainThread` 时 `launch(IO) { function() }`）；`app/src/main/java/io/legado/app/web/socket/BookSourceDebugWebSocket.kt:85-100`（`printLog` 内 `runOnIO { send(msg); if (state==-1||1000) { cancelDebug(true); close() } }`）；线程上下文证据：`app/src/main/java/io/legado/app/model/webBook/WebBook.kt:363-377`（`context=Dispatchers.IO`、`executeContext=Dispatchers.Main`）+ `help/coroutine/Coroutine.kt:174,181,211-239`（回调无 context 时在 `scope+executeContext`=Main 上调用，正文体在 `withContext(Dispatchers.IO)` 内）。
- 推导：`finishContentDiagnostics()` 与随后的 `log(state=1000)` 都在**主线程**执行；`printLog` 因此每次都走 `runOnIO` 的 `isMainThread==true` 分支 → 4 行摘要 + 1 行终止信号是 **5 个互相独立、可被 Dispatchers.IO 多 worker 并发执行**的任务。
- 具体失败场景：Dispatchers.IO 有空闲 worker 时（正常情况），第 5 个任务（`cancelDebug(true)+close()`）可先于第 1 个任务（`send(摘要行)`）的 socket 写完成；`send` 抛出的异常被 `runCatching` 静默吞掉（`BookSourceDebugWebSocket.kt:90-98`），前端因此只收到部分摘要行甚至收不到——正是 B-4 要消除的症状，只是从「必现」变成「随机」。`Debug.kt:431-432` 的注释「Web 端 printLog 收到 state=1000 会异步 cancelDebug(true)」恰恰说明作者知道投递是异步的，但结论只停在入队顺序。
- 说明与建议：真正的收口需要在白名单外的 `BookSourceDebugWebSocket`/`runOnIO` 上做（例如把摘要与终止信号合并进同一个 `launch(IO)` 块顺序 `send`，或让该 socket 用串行 dispatcher）。**本轮不建议把它算作 executor 的未完成项**——超出其写白名单；但报告不应把 B-4 写成已闭环，`10-execution.md` §7.1 已声明无实机证据，建议在验收结论里同步记为「残余竞态，需 controller 决定是否扩范围」。

### F-03 [低] `stripUrlSecrets` 对「路径里含 `@`」的 URL 会把主机名切掉，摘要显示错误主机

- 位置：`app/src/main/java/io/legado/app/help/book/diagnostics/DiagnosticRedactor.kt:119-121`（在 `authorityAndPath`（**含 path**）上做 `lastIndexOf('@')`）。
- 具体失败场景：`sanitizeMessage("请求 https://api.example.com/@user/profile 失败")` → `authorityAndPath = "api.example.com/@user/profile"`，最后一个 `@` 落在 path 里 → `visible = "user/profile"` → 输出 `请求 https://user/profile 失败`。主机名被丢弃、结果指向一个不存在的地址。
- 影响：**不泄漏**（userinfo 一定被丢弃，方向是安全的），只是摘要可读性/忠实性受损，且可能把排障引向错误主机。
- 建议：只在第一个 `/` 之前寻找 `@`（即 `authorityAndPath.substringBefore('/')` 的范围内取 `lastIndexOf('@')`）。属 M-1 同文件的小修，可并入下一次返工。

### F-04 [低] 报告对 L-1 隔离范围的表述过宽：**同一书同一章**的导出/换源统计路径确实拿得到接收端

- 位置：`BookSourceDiagnostics.kt:86-93`（`accepts`）；`app/src/main/java/io/legado/app/service/ExportBookService.kt:538-556`（对 `book` 的全部章节调 `contentProcessor.getContent(book, chapter, …)`，`includeTitle=false, reSegment=false, chineseConvert=false`）；`app/src/main/java/io/legado/app/ui/book/changesource/ChangeBookSourceViewModel.kt:334`。
- 报告/说明中的原话（`10-execution.md` §2 L-1 行、`11-implementation-notes.md` §2.1）：「本地书、别的书、别的章节、**换源统计、API 导出**都取不到接收端」。
- 具体反例：调试书源 S 的书 B 第 2 章（`accepts` 要求书源一致 + 章节一致，书号在绑定非空时也一致）→ 对同一本书执行「导出」（遍历 B 的全部章节）或「换源字数统计」第 2 章 → 参数完全满足 `accepts` → **拿得到接收端**并写入阶段四。隔离真正成立的范围是「本地书（`origin=loc_book`）、别的书源、别的书、别的章节」，不是「任何导出/统计路径」。
- 影响与建议：后果不算严重（同一正文目标），但两点值得记录：(a) 报告结论需按事实收窄；(b) 这些路径用 `chineseConvert=false, reSegment=false, includeTitle=false` 处理正文，其阶段四读数**不代表阅读页的正文**，摘要可能给出误导性的 `len/lines/imgs`。是否要把阶段四限定为「阅读路径专用」属 P1 口径问题，建议 controller 裁定，本轮不要求改。

### F-05 [低] 四组件契约测试的证据强度不均，其中两处是「转写/常量级」而非生产逻辑

- `app/src/test/java/io/legado/app/help/book/ContentProcessorContractTest.kt:101-115`（`replaceStageLineTrimNormalizationIsStable`/`replaceStageLineTrimDoesNotTouchInnerText`）实际测的是 `lines.joinToString("\n") { it.trim() }` 这一 Kotlin 标准库表达式，**没有**调用任何生产方法；该文件对 `ContentProcessor` 的覆盖是 `ContentLineNormalizer`（`ContentProcessor.kt:226` 确实委托给它，故仍是同一份逻辑，但属抽取后的等价物，非生产类直调）。
- `app/src/test/java/io/legado/app/data/entities/ReplaceRuleContractTest.kt:51-58` 与 `:137-159` 自述：`isValid()` 的失败分支（`PatternSyntaxException`/`isRegex=false`）因 `appCtx`（`ReplaceRule.kt:14`）无法在纯 JVM 覆盖；灾难性回溯用例的「超时」断言实际不成立，只冻结「不挂死」。两处都**如实标注**了，我没有把它当作隐瞒，但这意味着「四组件契约测试真实通过」里的 ReplaceRule/ContentProcessor 两项覆盖是部分的。
- 建议：不作为返工项（验收方对 L-3 已明示「不为纯风格问题反复复审」）；建议在验收结论里把这两处证据强度与原 NOT RUN 项并列写明，避免后续把「121 个用例全绿」等同于「四组件契约齐备」。
- 反面对照（做得好的）：`DiagnosticsToggleParityTest.kt`（7 用例）与 `BookSourceDiagnosticsContractTest.kt`（40 用例）都直调生产类，且 `DiagnosticsToggleParityTest.kt:82-86` 额外断言「会话必须真产出 3 条快照」以防等价断言空跑；该文件 `:17-22` 明确写了「没有调用 `ContentProcessor`/`BookContent`」的边界。

### F-06 [阻塞 · 授权] M-3 越界改动仍在工作树

- 位置：`app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeByXPath.kt:137-138`（`TextUtils.join("\n", it)` → `it.joinToString("\n")`）。任务书「正式文件白名单」不含该文件；本轮报告 §9.1 未抹去越界事实、未再改该文件，符合「不自行扩权」。
- 我独立核对了语义：`getResult(xPath)` 返回 `List<JXNode>?`（`AnalyzeByXPath.kt:42-49`），对 `List` 而言 `joinToString("\n")` 与 `TextUtils.join("\n", it)` 等价，验收方 M-3 的判断成立，改动具备保留价值。
- 失败场景：不是运行期失败，而是**流程失败**——若在未扩白名单的情况下把本轮判为 accepted，等于默认追认了一次白名单外改动，后续「白名单是硬上限」的约束就失效。
- 建议：controller 必须二选一并写进验收：显式把该文件纳入范围（保留），或要求回退。本轮 reviewer 不代做这个决定。

### F-07 [阻塞 · 证据] 本轮门禁与探针无法被本轮独立复核；最终源码只在「等价通道」下验证

- 事实：`06-review-commands.json` 为空 → reviewer 侧「未配置检查为 NOT RUN」；本会话无 shell → `:app:compileAppDebugKotlin`/`:app:testAppDebugUnitTest`/脱敏探针/`git diff` 全部 NOT RUN。
- 报告自述（`10-execution.md` §5 与 §5.1）：§8.3 字面命令最后一次成功（第 4 次，`BUILD SUCCESSFUL`，exit 0）对应的源码**还多一个未被引用的 `sessionId` 字段**；删掉该字段后，最终源码只在不满足 §8.3 的三处差异（`GRADLE_USER_HOME` 换成工作区内 `build/drp-gradle-home`、直接调 `gradle.bat` 而非 `gradlew.bat`、加 `-Pkotlin.compiler.execution.strategy=in-process` 与 `--no-problems-report`）下跑过，且最后一遍（第 7 次）是 `57 up-to-date`，**没有再次真实执行测试任务**。
- 影响：`suites=44 tests=380 failures=0` 这一关键完成条件（任务书 §8.3）在本轮**没有独立证据**；我能独立核实的只有「测试源码侧自洽」（见第四节第 5、6 条），这不能替代运行结果。报告 §5.1 对差异做了披露，态度是诚实的，但差异本身就是未闭合的完成条件。
- 建议：acceptor 复跑时请在**最终工作树**上跑，并同时记录 `suites/tests/failures/errors/skipped` 与测试 XML 时间戳；若字面命令仍被沙箱拒绝，需把「等价通道」正式记入环境事实（报告 §9.4 的建议方向正确）。

---

## 三、逐项核对：验收单/首轮复审要求的落地情况（工作树为准）

| 项 | 我的核对结果 | 证据 |
| --- | --- | --- |
| B-1 错误分类接线 | **已落地**。三处阶段内 `catch` 分类后原样上抛：提取 `BookContent.kt:288-297`、格式化 `:319-327`、书源替换 `:196-207`（`CancellationException` 三处均先重抛，未被诊断改动吞掉）；链路级兜底 `Debug.kt:435-442` → `BookSourceDiagnostics.kt:185-199`（归到「已执行阶段里最靠后的一个」，无阶段则归阶段一）；阅读处理阶段被吞异常带出 `ContentProcessor.kt:142-145, 194-208, 234-240`。分类器按类型优先、消息标记兜底（`DiagnosticClassifier.kt:79-102`）。 | 源码直读 |
| B-2 阶段一改为真实提取产出 | **已落地**。记录点移到 `analyzeRule.getString(contentRule.content, unescape=false)` 返回处（`BookContent.kt:283-304`），耗时只计提取；`AnalyzeRule` 的 3 参诊断重载与诊断 import 已删除，现仅 `getString(ruleStr, mContent, isUrl)`/`getString(ruleStr, unescape)`/`getString(list,…)`（`AnalyzeRule.kt:295,301,308`），全文件无 `currentTimeMillis`/`Diag` 命中。 | 源码 + Grep |
| B-3 缺阶段兜底 | **已落地**（但见 F-01）。`Debug.kt:452-459` 先标 READ_PROCESS 专属原因，再 `markMissingStagesNotExecuted`，再打印。 | 源码直读 |
| L-5 并发采集权 | **已落地**。采集权从全局标志改为会话内 `AtomicBoolean.compareAndSet`（`BookSourceDiagnostics.kt:62,96`），调用点 `BookContent.kt:82`，不再借用 `nextChapterUrl`。 | 源码直读 |
| B-4 终止信号顺序 | **入队顺序已改对，投递仍竞态**（见 F-02）。`Debug.kt:433-434,440-441` 均为「先摘要后终止」；`BookSourceDebugWebSocket.kt:85-100` 未改（白名单外）。 | 源码直读 |
| B-5 开关正文一致性 | **组件层已补**。`DiagnosticsToggleParityTest.kt` 同一输入在「无会话 / 有会话 / 显式关闭的 collector」三种情形下跑 `AnalyzeRule`→`HtmlFormatter.formatKeepImg`→`ContentLineNormalizer` 并逐字节比较，另断言会话真产出 3 条快照；`:17-22` 如实声明未调用 `ContentProcessor`/`BookContent`（无 Robolectric）。 | 测试源码直读 |
| M-1 脱敏 | **已落地**。`stripUrlSecrets` 在 URL 范围内只保留 authority+path、`?`/`#` 之后整段随范围丢弃（`DiagnosticRedactor.kt:100-126`）；`replaceAssignment` 对 `:` 按行掩码、对 `=` 掩到分隔符，并跳过分隔符后空白（`:172-212`）；SECRET_KEYS 补 `set-cookie/sid/auth/key/tk/csrf`（`:262-284`）。我按代码**静态复算**了验收方探针的三个样例：`…?chapter=…`→`https://example.invalid/page`（在第 5 步才出现查询串分界，`?` 前的 host/path 保留、之后丢弃）；`Cookie: X`→`Cookie: ***`；`Authorization: X`→`Authorization: ***`——与报告「0/3 泄漏」自洽（**未实跑**）。 | 源码 + 静态推演 |
| M-2 构建越界 | **当前状态已恢复**。全仓库 `*.gradle*` 无 `testOptions`/`unitTests`/`returnDefaultValues` 任何命中；`app/build.gradle` 依赖块只有 `testImplementation(libs.junit)`，未新增 Robolectric/Mockito。**但我无法证明它与 HEAD 逐字节一致**（无 git）。 | Grep |
| M-3 XPath | **仍未处置**（见 F-06）。 | 源码直读 |
| L-1 会话隔离 | **主体已落地，表述过宽**（见 F-04）。`accepts` 三键匹配（`BookSourceDiagnostics.kt:86-93`），`bindTarget` 在抓正文前绑定（`Debug.kt:422`），本地书 `origin=loc_book`（`BookType.kt:71`）永不匹配。 | 源码直读 |
| L-2 textNodes | **已更正且与实现一致**。`AnalyzeByJSoup.kt:239-251` 确认 `@textNodes` 取直系子文本节点并用 `\n` 拼成一条；`AnalyzeRuleContractTest.kt:65-82` 的注释与「`第一段<br>第二段` 正例」与该实现吻合。 | 源码交叉验证 |
| L-3 死代码 | **已按验收口径处理**。`BookSourceDiagnostics` 接口与 `DISABLED` 单例已删（全仓库只剩 collector 类）；`skeleton` 仍在但 KDoc 明写「当前无生产调用点」（`DiagnosticRedactor.kt:44-45`），且确无生产调用点（Grep 证实）。 | Grep + 源码 |
| L-4 tmp-gradle-home | **已删除**。对任务目录做递归枚举，结果中不存在任何 `tmp-gradle-home/**` 条目（首轮曾存在 200 个文件）。 | Glob 枚举 |
| 额外观察（关闭时读时钟） | **已落地**。`ContentProcessor.kt:112` 与 `BookContent.kt:192,283,306` 三处计时全部写成「拿到接收端才读时钟」，`AnalyzeRule` 内计时随重载消失。 | 源码 + Grep |
| 正文语义零改变 | **未发现改变**。四阶段只新增「取接收端 + 写快照」，`elapsedSince`/`record` 不参与返回值；`ContentLineNormalizer.normalize`（`:21-40`）与 `ContentProcessor.kt:226-230` 的调用形式逐行等价于原尾段逻辑（`trim { it.code <= 0x20 || it == '　' }`、首段判 `includeTitle`、其余加 `paragraphIndent`）。**逐字节对照 HEAD 无法做**（无 git）。 | 源码直读 |
| 诊断关闭零额外处理 | **成立**。无会话时 `Debug.diagnosticsFor` 直接返回 null（`Debug.kt:113-121`），四阶段全部走 `?.` 空分支，不读时钟、不拼摘要、不落盘（collector 全内存 `synchronizedList`）。唯一无条件开销是每章一次 `@Volatile` 读 + 一次 `accepts` 调用。 | 源码直读 |

---

## 四、我实际执行过的核对动作与输出

1. 读取任务书、执行报告、机器摘要、实施说明、验收单、reviewer 上下文、`06-review-commands.json`；确认 `commands` 为空 → reviewer 侧无任何可执行检查。
2. 逐个读完全部 6 个 diagnostics 生产文件 + `Debug.kt` + `BookContent.kt` + `ContentProcessor.kt` + `AnalyzeRule.kt` + `ReplaceRule.kt` + `AnalyzeByXPath.kt` + `app/build.gradle`。
3. `Grep "testOptions|unitTests|returnDefaultValues"`（`*.gradle*`）→ **0 命中**（M-2 当前状态核实）。
4. `Grep "TextUtils"`（`app/src/main`）→ analyzeRule 包仅 `AnalyzeByXPath.kt:137` 一条注释提及；`ReplaceRule.kt`/`ContentProcessor.kt` 已无 `TextUtils`；其余命中均在无关文件（RssSource、BookSource、QRCodeUtils、ObsoleteUrlFactory 等）。
5. `Grep "@Test"` 计数：`BookSourceDiagnosticsContractTest=40`、`DiagnosticsToggleParityTest=7`、`AnalyzeRuleContractTest=27`、`HtmlFormatterContractTest=16`、`ReplaceRuleContractTest=17`、`ContentProcessorContractTest=14`，合计 **121**，与报告逐类数字**完全一致**。与验收单首轮的独立统计（`suites=43, tests=358`）对账：358 + 15（诊断类 25→40）+ 7（新增 parity 类）= **380**，`suites` 43→44 恰为新增一个测试类——报告数字自洽（**这只能证明源码侧自洽，不能证明运行结果**）。
6. `Grep` 生产调用点：`summarizeText`/`classifyOutput`/`recordFailure`/`markNotExecuted`/`markMissingStagesNotExecuted`/`claimPrimaryStages`/`bindTarget`/`diagnosticsFor`/`printBookSourceDiagnostics` 全部有生产调用点；`skeleton` 无生产调用点（与 L-3 声明一致）。
7. 读 `AnalyzeByJSoup.kt:239-251` 独立确认 L-2 的更正在实现上成立（直系文本节点 + `\n` 拼接）。
8. 读 `RegexExtensions.kt:70-88` 独立确认 `RegexTimeoutException` 的消息确含「规则名称 + 替换规则 + 整段替换内容」，因此 `BookSourceDiagnostics.kt:145-148` 只写固定说明是**正确的安全选择**；同时确认 `ContentProcessor.kt:202` 把 `item.name + e.stackTraceStr` 写进正文是既有行为、本轮未改。
9. 读 `HandlerUtils.kt:42-50`、`Coroutine.kt:30,174,181,226-239`、`WebBook.kt:363-377`，建立 F-02 的线程上下文推导（回调在 Main、`runOnIO` 在主线程时改走 `launch(IO)`）。
10. 读 `ExportBookService.kt:538-556`、`ChangeBookSourceViewModel.kt:325-340`，构造 F-04 的反例（同一书同一章的导出/统计确实满足 `accepts`）。
11. 按代码静态复算验收方 `P1RedactionProbe.java` 的三个合成样例在现实现下的输出（见上表 M-1 行），结论与报告一致（**未实跑**）。
12. 对任务目录做递归枚举，确认 `tmp-gradle-home/**` 已不存在（L-4）。

---

## 五、我**没有**检查到的范围（请勿读作已覆盖）

1. **门禁与探针未运行**：`:app:compileAppDebugKotlin`、`:app:testAppDebugUnitTest`、`P1RedactionProbe`、`lint` 一律 NOT RUN（无 shell + `06-review-commands.json` 为空）。报告引用的 `app/build/p1-rework-gate*.log`、`p1-rework-redaction-probe.log`、测试 XML 全在 `app/build/**`，**不在我的读取白名单内**，我未读取，故「380/0/0」「0/3 泄漏」的**过程侧**我没有核实，只核对了产物/源码侧的自洽性。
2. **未执行任何 git 操作**：无法逐字节确认本轮改动集合等于报告 §3 所列、无法确认 `app/build.gradle` 与 HEAD 完全一致、无法确认 `rounds/executor-r001/**` 是「原样复制」（我只确认该目录存在且首轮 7 个文件在其中）、无法确认「未提交/未推送/未切分支」。
3. **未读**：`PROJECT.md`（任务书未列入白名单）、`rounds/**`（被协议排除）、`15-*-raw.md`、首轮 `20-review.md` 原文（其全部 finding 已由 `30-acceptance.md` 处置表覆盖，我按该表逐项核对）。
4. **真机/UI/Web 端**：调试页四阶段渲染、Web 端竞态「修复前后」对照、阅读页正文与关闭诊断时逐字一致、`@webjs:`、真实书源/真实站点——全部 NOT RUN，且未读取真实语料/书源/`bookshelf.json`。
5. **未通读全部调用链**：只读了四阶段相关的生产文件与其调用方/生命周期片段（`WebBook`、`ReadBook`、`ExportBookService`、`ChangeBookSourceViewModel`、`BookSourceDebug*`、`WebSocket`）。未评估搜索/详情/目录等其它书源解析分支是否也应接入诊断，未评估 P2—P5 相关面，未评估 Android 8 的 `isAndroid8` 分支、音视频链路与 `mapAsync` 并发分页的端到端行为。
6. **两项「可达性未验证」我明确留白**：(a) `HTTP错误`/`图片URL为空`/`下一页循环` 三个类别在真实链路里的可达性——我未找到把 HTTP 状态码写进异常消息的确定路径（`getErrResponse` 造的是 500 响应体，`ObsoleteUrlFactory` 的 `FileNotFoundException` 不含状态码），只有分类函数级单测；(b) 异常消息若内嵌大段响应/正文（我只在 `RegexExtensions.replace` 找到确定实例且已被专门处理），`sanitizeMessage` 的 240 字截断是唯一护栏——本轮未找到该类异常，故不作为发现上报，但这是一个未验证的脱敏边界。
7. **2.4MB/大量文件级回归**：未做全量编译期符号核查（无 shell），#4/#5 的「陈旧增量编译」结论我无法独立复核。

---

### 给 acceptor 的最短路径建议

1. 在**最终工作树**上复跑 §8.3 门禁并记录 `suites/tests/failures/errors/skipped` + XML 时间戳（闭合阻塞-3）；
2. 裁定 M-3（闭合阻塞-2）；
3. 决定 F-01 的口径：至少把 `Debug.kt:454-457` 改成「无快照才标未执行」，并更正 `10-execution.md` §8 第 5 步的预期（闭合阻塞-1）；阶段四是否需要在阅读路径可见，请显式表态；
4. F-02/F-03/F-04 建议登记为后续项（其中 F-02 的收口需要白名单外的文件），不要以「已闭环」记录。
