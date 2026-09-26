<!-- agent-protocol-binding
round_id: reviewer-r001
task_digest: 412348366fff65c38495fa28ca0a0da001af4e7ddee758592cc798cf4edc3084
snapshot_digest: 60ac640f43e4cbfe68609a452fb64263a1fa12a8368167a9ad13ff29a47f3795
--># book-source-p1-diagnostics — 独立复审报告（reviewer: claude，只读静态复审）

- 任务：`.agent-protocol/tasks/book-source-p1-diagnostics`（round `executor-r001`，triad，route_revision 7）
- 复审方式：**只读静态复审**。`06-review-commands.json` 为 `{"version":1,"network_policy":"deny-declared-not-enforced","commands":[]}`，按任务书「本轮只读静态复审……不运行 shell、Gradle、联网或真机操作」执行；未运行任何独立门禁。
- 我未修改任何文件，未触碰 `10-execution.md` / `30-acceptance.md`。

## 结论

**有阻塞项**（建议 `rework` 后再交 acceptor；最终接受与否由 acceptor 判定，本报告不代替）。

阻塞项不是「代码把正文改坏了」——我没有发现任何正文语义回退；阻塞项是**交付物本身在真实链路里达不到计划书 §6 B0/P1 的退出条件「诊断信息能指出问题发生在哪一层」**：

1. 「统一错误分类」在真实调用链上**没有任何异常接入点**，九类错误里只有 2 类可达（B-1）；
2. 阶段一「提取后」记录的其实是**原始响应**，不是规则提取后的产出，标签与语义不符（B-2）；
3. 阶段一/二在真机 §8.2 的**搜索→详情→目录→正文**路径下会被静默跳过，且不带「未执行」标注（B-3）；
4. 完成条件第 1 条要求的「诊断开关前后正文一致性」没有自动化证据（B-5）。

另有 1 项安全相关缺陷当前处于**潜伏**状态（M-1：脱敏承诺与实现不符），以及 2 项已声明但需 controller 追认的越界写入（M-2、M-3）。

---

## 一、我实际执行过的核对动作与输出

| # | 动作 | 结果 |
| --- | --- | --- |
| 1 | 读 `06-review-commands.json` | `commands: []` → **本轮所有独立门禁复跑 = NOT RUN** |
| 2 | 逐套件重算 `app/build/test-results/testAppDebugUnitTest/*.xml` 的 `tests/failures/errors` 头 | **43 个套件，合计 358 用例，failures=0 errors=0**；新增 5 套件 27/16/17/14/25=99，与报告完全一致；全部套件 `timestamp="2026-09-22T06:51:35Z"`（同一次运行） |
| 3 | 全仓 grep `android.text.TextUtils` | 四个被改的生产类中**已无残留**；仓库别处仍在用（`StringUtils.kt:4`、`EventMessage.kt:3` 等，未改动） |
| 4 | 语义等价核对 | `ReplaceRule.kt:88 pattern.isEmpty()`、`AnalyzeRule.kt:317/323/224 ruleStr.isNullOrEmpty()`、`AnalyzeByXPath.kt:138 joinToString` 与原 `TextUtils.*` 等价（含空列表返回空串、null 元素同样输出 `null`） |
| 5 | `ContentLineNormalizer.normalize` 与 `ContentProcessor` 原内联逻辑逐行比对 | 一致（`\n` 切行 → `trim{ code<=0x20 \|\| it=='　' }` → 丢空行 → 首段不缩进 → 其余加 `paragraphIndent`），抽函数本身无语义改动 |
| 6 | 追踪四阶段全部生产调用点（`grep '\.record\(\|markNotExecuted\(\|error =\|timeoutMs ='`） | 共 4 处生产调用，**全部不传 `error`、不传 `timeoutMs`**（见 B-1） |
| 7 | 追踪 `Debug.diagnostics` 生命周期 | 建立在 `Debug.kt:270`，清空在 `cancelDebug(destroy=true)`（`Debug.kt:113-121`）；触发点：`BookSourceDebugModel.onCleared()`（`ui/book/source/debug/BookSourceDebugModel.kt:57`）、`BookSourceDebugWebSocket.kt:43/82/93` |
| 8 | 越界核对 | 写白名单内：`AnalyzeRule.kt`/`Debug.kt`/`BookContent.kt`/`ContentProcessor.kt`/`ReplaceRule.kt` + `help/book/diagnostics/**`（6 新文件）+ 5 个测试类。**白名单外 2 处已声明**（`app/build.gradle`、`AnalyzeByXPath.kt`），另有 **1 处未声明残留**（L-4） |
| 9 | 串扰/本地书路径核对 | `ContentProcessor.getContent` 在本地 TXT/EPUB 与阅读路径上同样会记录 `READ_PROCESS`（见 L-1）；无落盘、无网络、无凭据写入 diff |

**我采信的证据**：白名单内源码、`app/src/test` 下测试源码、`app/build/test-results/**` 的用例计数（仅套件名/计数/时间戳，无用户数据）、计划书 §3.1/§6/§7/§8。**我一律没有采信**执行报告的自述结论。

---

## 二、阻塞项

### B-1（中高）统一错误分类未接入任何异常路径，九类里只有 2 类在生产可达
- 位置：`app/src/main/java/io/legado/app/help/book/diagnostics/BookSourceDiagnostics.kt:84-120`、`:97-99`；调用点 `AnalyzeRule.kt:127-131`、`BookContent.kt:195-199`、`BookContent.kt:290-294`、`ContentProcessor.kt:223-227`
- 事实：`BookSourceDiagnosticsCollector.record(stage, text, elapsedMs, error, timeoutMs)` 的 `error` 与 `timeoutMs` 两个参数，**在整条生产链上没有任何调用方传值**（第 6 步 grep 结果，4/4 调用点均省略）。因此：
  - `DiagnosticClassifier.classify(error, stage)`（类型判定：`UnknownHostException`/`SocketTimeoutException`/`CharacterCodingException`/`PatternSyntaxException`/`RegexTimeoutException`/`ContentEmptyException`）**生产不可达**；
  - `timeoutMs` 分支（`:97-99`，按耗时判 `REPLACE_TIMEOUT`）**生产不可达**；
  - `classifyByMessage` 的九类标记（网络/HTTP/编码/规则语法/下一页循环/图片 URL 为空/正文为空/零命中）**没有任何生产调用点**——它只被 `record()` 内部在 `error != null` 时调用；
  - 生产实际可达的只有 `classifyOutput()` 的三个结果：`NONE`、`EMPTY_MATCH`、`EMPTY_CONTENT`。
  - 进一步的因果：`ContentProcessor.kt:189-192` 自己 `catch (e: RegexTimeoutException)` 后就地吞掉并写盘，永不外抛；`BookContent.kt:190` 的全文替换走 `AnalyzeRule.replaceRegex`（`compileRegexCache` + `String.replace`），不会产生 `RegexTimeoutException`。所以「替换超时」这一类**在真实链路上永远显示不出来**。
- 失败场景：某书源正文页返回 404 页面（HTTP 200 但内容是错误页）→ 摘要只会显示 `3.书源替换后 规则零命中 len=..`，**不会**出现「HTTP 错误 / 网络错误 / 编码错误」，与计划书 §6 B0「报错区分网络、HTTP、编码、规则语法、零命中、正文为空、下一页循环、图片 URL 为空和替换超时」不符；任务书复审清单「检查错误分类是否真正接入异常路径」的答案是：**没有接入**。
- 建议：在 `BookContent.analyzeContent` / `contentDebug` 的 `onError`、`AnalyzeUrl` 的响应码检查、`AnalyzeRule` 的 `replaceRegex` 调用处显式 `record(..., error = e)` 与 `timeoutMs = item.getValidTimeoutMillisecond()`；或（controller 裁定降级时）在报告与验收里明确写成「分类组件已建，链路接入属 P1 未完成项」。

### B-2（中）阶段一标签「提取后」与实际记录内容不符：记录的是原始响应
- 位置：`AnalyzeRule.kt:124-132`（`text = content.toString()`，注释自述「此处只有原始响应」）、`DiagnosticStage.kt:15`（`EXTRACT("提取后", 1)`）、真正的提取点在 `BookContent.kt:270`（`analyzeRule.getString(contentRule.content, unescape = false)`）
- 事实：快照在 `setContent()` 里采集，即**把响应体交给解析器的那一刻**，早于 `ContentRule.content` 的提取；`elapsedMs` 测的是 `content.toString() + isJson()` 的耗时，与「提取」无关。计划书 §3.1 对「规则提取后」要回答的问题是「CSS/XPath/JSONPath/JS 规则是否**选多、选少或拼接错误**」——这只能由**提取产出**回答；现在记录的是 §3.1 里另一行「原始响应」。
- 失败场景：某源正文页整页 HTML 为 52 KB，规则只命中 1 段（或选多）。摘要第 1 行显示 `【诊断】1.提取后 正常 len=52000,lines=900,imgs=30`。调试者会认为「提取出了 5 万字」，而真实提取结果在第 2 行才出现——阶段一完全无法用来定位「选多/选少」。另外此行的耗时读数也会被误读为提取耗时。
- 建议：把阶段一移到 `BookContent.kt:270` 之后（`getString` 返回处），`elapsedMs` 计提取耗时；若确实要保留原始响应读数，应新增/改名一个阶段（如 `RAW`）而不是占用「提取后」标签。

### B-3（中）阶段一/二会被全局标志静默跳过，且不标「未执行」
- 位置：`BookContent.kt:46-48`（`@Volatile private var primaryStagesCaptured`）、`:76-79`（`if (nextChapterUrl.isNullOrEmpty()) primaryStagesCaptured = false`）、`Debug.kt:295-304`（`--` 入口传 `nextChapterUrl = null`）、`Debug.kt:384`（目录入口传 `toc.getOrNull(1)?.url ?: toc.first().url`，恒非空）、`ReadBookViewModel.kt:385` / `ReadMangaViewModel.kt:206`（正常阅读传 `nextChapterUrl = nextChapter.url`，恒非空）
- 事实：这个「首个抓取页」判据借用 `nextChapterUrl` 是否为空来重置，而**正常阅读路径传的是非空值**，因此阅读每加载一章都会把该全局标志置为 `true`；而书源调试的**目录→正文**入口传的也是非空值，**不会重置**。标志只在 `--<url>` 入口被重置。
- 复现步骤（完全按计划书 §8.2 的真机清单）：
  1. 打开任意在线书，正常翻阅 1—5 章（`ReadBookViewModel.kt:385` 已把 `primaryStagesCaptured` 置 true）；
  2. 进某书源调试页 → 搜索「我的」→ 详情 → 目录 → 正文；
  3. 摘要只会出现 `【诊断】3.书源替换后 …` 与 `【诊断】4.阅读处理后 未执行 …`；**阶段 1、2 既不出现也不标注未执行**（`markNotExecuted` 只对 FORMAT 的音频/视频分支和 REPLACE 的空规则分支调用，`BookContent.kt:296-302`、`:200-205`）。
- 影响：调试者看到「三段齐全的摘要缺两段」而无任何解释，会把「没有阶段一/二」误读成「阶段一/二没有内容差异」。这与任务书「未实际经过某阶段时必须如实标记未执行」直接冲突。
- 建议：把「首个抓取页」的判定与诊断会话绑定（例如由 `Debug.startBookSourceDebugSession()` 生成一个 session id/代次，`BookContent` 只认这一代次的第一次调用），**不要**复用 `nextChapterUrl` 这种与诊断无关的业务参数；同时对阶段 1/2 补 `markNotExecuted` 兜底。

### B-4（中）Web 端书源调试路径下四阶段摘要有竞态，可能整段不输出
- 位置：`Debug.kt:408-414`（`onSuccess` 先 `log(..., state = 1000)` 再 `finishContentDiagnostics()`）、`Debug.kt:422-428`、`Debug.kt:101-104`、`app/src/main/java/io/legado/app/web/socket/BookSourceDebugWebSocket.kt:85-95`
- 事实：`BookSourceDebugWebSocket.printLog` 收到 `state == 1000` 时在 `runOnIO { }` 里执行 `Debug.cancelDebug(true)` → 把 `Debug.diagnostics` 与 `Debug.callback` 一起置空并关闭 socket。而 `finishContentDiagnostics()` 在调用线程上紧接着执行 `markDiagnosticStageNotExecuted` + `printBookSourceDiagnostics()`，后者经 `log()` 打印**必须 `Debug.callback != null`**。两者不同线程、无同步。
- 失败场景：用 Web 端调试（`BookSourceDebugWebSocket`）跑正文，IO 线程先拿到 `state=1000` → `cancelDebug(true)` → 摘要既进不了 collector（`diagnostics == null`）也打不出去（`callback == null`）→ **四条诊断行一条都不出现**。同一路径在 `BookSourceDebugModel`（App 内调试页）不受影响（其 `printLog` 不清会话）。
- 建议：在 `cancelDebug(true)` 之前把摘要落成消息发出，或让 `finishContentDiagnostics()` 先于 `state=1000` 的日志执行，并把摘要生成与打印做成不依赖 `Debug.callback` 的原子操作。

### B-5（中）完成条件第 1 条的「诊断开关前后正文一致性」缺自动化证据
- 位置：`app/src/test/java/io/legado/app/help/book/diagnostics/BookSourceDiagnosticsContractTest.kt:217-225`（只有「关闭时零记录」）
- 事实：任务书完成条件明确要求「四组件契约测试与诊断分类/脱敏测试真实通过，**包含诊断开关前后正文一致性**」。现有 99 个用例中没有任何一个断言「同一输入下 collector 开启与关闭时，四阶段产出/最终正文逐字节相同」。执行报告 §5 也自述该条为「部分覆盖 / 端到端未在真机验证」。
- 我的静态核对结论（支持性、但不等价于证据）：改动确实只影响「是否记录」——`BookContent.kt:262` 只决定传给 `setContent` 的 collector；`ContentProcessor.kt:223-227` 只是读一次 `Debug.diagnostics` 并（开启时）做一次 `joinToString`；`AnalyzeRule.kt:126-132` 只在 `diagnostics != null && isEnabled` 时 `record`。四段返回值均未被改写。
- 建议：补一条可在 JVM 跑的「开关前后产出等价」用例（例如对 `AnalyzeRule`/`ContentLineNormalizer`/`HtmlFormatter` 分别以 collector 开/关两种入参断言输出 `assertEquals`），否则该完成条件只能由 controller 显式降级。

---

## 三、其他发现

### M-1（低中，安全/隐私）脱敏实现与自身契约不符：URL 查询串与片段实际未被删除
- 位置：`DiagnosticRedactor.kt:67-71`（KDoc：「删除 URL 的 userinfo、**查询串与片段**，只留 `scheme://host/path`」）、`:92-115`（实现只做 `lastIndexOf('@')` 之后的 userinfo 剥离）、`:228-231`（`isUrlTerminator` 不含 `?`、`#`、`,`）
- 事实：`stripUrlSecrets("https://example.com/api?sid=SECRETVALUE")` 的结果是 `https://example.com/api?sid=SECRETVALUE` —— 只有 userinfo 被去掉，查询串与片段原样保留。`SECRET_KEYS`（`:233-248`）里没有 `sid`/`auth`/`key`/`tk`/`t` 这类常见键名。
- 为什么现有测试看不出来：`BookSourceDiagnosticsContractTest.kt:174-183` 用的样本里每个查询参数键恰好都在 `SECRET_KEYS` 中（`token`、`sign`），是 `stripCredentialAssignments` 把值掩成了 `***`，`stripUrlSecrets` 并没有起作用——该用例**不能证明**它声称覆盖的契约。
- 当前不是线上泄漏：我核对了全部 4 个生产调用点，`error` 恒为 null，`note` 只来自 `markNotExecuted` 的固定字面量，所以异常消息目前**进不了**诊断输出。但 B-1 一旦按要求接入异常消息，任何带 `?sid=…` 的 URL 会原样出现在调试日志里，与任务书「不输出 …URL 查询秘密等」冲突。
- 建议：在 `stripUrlSecrets` 里把 `?`/`#`（以及半角 `,`、`;`）纳入终止符，或改为「保留 host+path、丢弃 query+fragment」；并补一条**只用非凭据键名**的用例（如 `?sid=…`）作为真正的守卫。

### M-2（低中，授权）`app/build.gradle` 越界写入 —— 已声明，需 controller 追认
- 位置：`app/build.gradle:169-175`（新增 `testOptions { unitTests { returnDefaultValues = true } }`）
- 该文件不在任务书写白名单内；执行报告 §3 已如实声明并给出理由（无 Robolectric/mock，`TextUtils.join` 等 android.jar 桩会抛 `not mocked`）。
- 关于任务书复审清单「returnDefaultValues=true 是否掩盖 Android 桩调用失败，使 358 测试通过不能代表契约成立」：
  - 我核对 `app/src/test` 全量源码：**没有任何测试文件 import `android.*`/`androidx.*`/`splitties.*`/`appDb`**，因此不存在「测试自己直接打桩」的情形；
  - 生产侧在**被测试覆盖的路径**上，`TextUtils` 用法已被 3 处等价替换清掉（第 3 步 grep）；`NetworkUtils.getAbsoluteURL`、`StringExtensions.isAbsUrl/isDataUrl`、`HtmlFormatter`、`AnalyzeByXPath` 均为纯 JVM，不经过 android.jar；
  - 但 `returnDefaultValues = true` 的语义是「未实现方法**静默返回默认值**」而不是抛错，仓库里仍存在被测试类可达的桩调用残留：`StringUtils.kt:4,277`（`TextUtils.isEmpty` → 恒 `false`）、`EventMessage.kt:13`（`TextUtils.equals` → 恒 `false`）。我没有构造出「因此测试假通过」的具体用例（`StringUtils.trim("")` 在两条路径上恰好都返回 `""`），所以这是一条**残余风险**而不是已证实的假通过；
  - 执行报告 §3 第 4 点「开启后对既有 38 个测试类无影响」**没有提供任何证据**（既无改动前后的对照运行，也无逐类审查记录），我无法验证。
- 建议：controller 二选一——(a) 追认并记录「测试套件已审查、被覆盖路径不含 android.jar 桩」这一前提；(b) 若坚持「不为测试改构建」，则回退该行并接受 `AnalyzeByXPath` 的 XPath 契约缺失（Robolectric 属新增依赖，任务书禁止）。无论哪种，都不应在验收里把「358 通过」直接等同于「契约在真机成立」。

### M-3（低，授权）`AnalyzeByXPath.kt` 越界写入 —— 已声明，语义等价，我核对通过
- 位置：`AnalyzeByXPath.kt:137-138`（`TextUtils.join("\n", it)` → `it.joinToString("\n")`）
- 语义核对：`TextUtils.join(delim, Iterable)` 对非空元素列表与 `joinToString` 逐元素 `toString()` 完全一致；空列表两者都返回 `""`（`getString` 随后 `result == null` 分支不受影响，`:139` 仍 `return null`）。**未发现语义差异**。
- 但该文件不在白名单内，与 `app/build.gradle` 同属需 controller 追认的越界写入。

### L-1（低）跨会话/跨书串扰：诊断接收端是进程级全局，且阅读路径与本地书也往里写
- 位置：`Debug.kt:36`（`var diagnostics: BookSourceDiagnostics?`，非 `@Volatile`）、`ContentProcessor.kt:109-110,223-227`、`BookSourceDiagnostics.kt:144-145,152-153`
- 事实：`ContentProcessor.getContent` 是所有阅读路径（含**本地 TXT/EPUB**、API 导出 `BookController.kt:216`、换源字数统计 `ChangeBookSourceViewModel.kt:334`）的公共出口。只要调试会话未销毁（`cancelDebug(true)` 只在 ViewModel `onCleared`/WebSocket 关闭时发生），这些路径都会往同一个 collector 里写 `READ_PROCESS`，且摘要按 `stage.order` 全量输出。
- 失败场景：调试会话仍存活时（多窗口/分屏、或 ViewModel 尚未 `onCleared`）阅读另一本书的一章 → 调试摘要里出现 `4.阅读处理后 正常 len=…`（其实来自另一本书）。叠加 `Debug.kt:423-426` 的 `markNotExecuted(READ_PROCESS, …)`，同一阶段会**同时出现两行**（一条「正常」一条「未执行」），互相矛盾。
- 另：`Debug.diagnostics` 为普通 `var`，主线程写、IO 线程读，属形式上的数据竞争（`primaryStagesCaptured` 已用 `@Volatile`，此项没有）。

### L-2（低）执行报告对 `@textNodes` 的「既有缺口」结论不成立，会误导后续阶段
- 位置：`AnalyzeRuleContractTest.kt:64-73`（注释称「`@textNodes` 当前取不到文本节点 …正文需用 `@text` + `##\n##` 替换」）、`AnalyzeByJSoup.kt:239-251`
- 事实：`@textNodes` 在 `getResultLast` 中实现为 `element.textNodes()`（jsoup 的**直系子文本节点**）。该用例的夹具是 `<div class="content"><p>第一段</p><p>第二段</p></div>` —— 文本全在 `<p>` 子元素里，直系文本节点只有空白，**任何正确的实现都会返回空列表**。该断言证明的是夹具性质，不是产品缺陷。
- 建议：把 `10-execution.md` §6 第 2 条与测试注释改写为「夹具无直系文本节点，故返回空」，或改用 `<div class="content">第一段<br>第二段</div>` 之类的夹具真正覆盖该分支；否则这一「已记录缺口」会作为错误事实流入 P3 的修复清单。

### L-3（低）为一次性用途引入的未接线代码/过度抽象
- `DiagnosticRedactor.kt:40-65 skeleton()`：**仅被测试调用**，生产无任何调用点（`grep skeleton(` 结果）。其 `if (sb.length > maxLength) sb.substring(0, maxLength)` 分支也不可达（循环内 `>= maxLength` 已 break）。
- `BookSourceDiagnostics.kt:48-63`：`BookSourceDiagnostics` 接口 + `DISABLED` 单例在生产中**从不使用**——`Debug.diagnostics` 关闭时是 `null` 而非 `DISABLED`，且所有消费者（`Debug.kt:102/110`、`BookContent.kt:43`、`ContentProcessor.kt:109`）都立刻 `as? BookSourceDiagnosticsCollector` 向下转型。接口层没有带来第二实现，只增加了转型噪声。
- `BookSourceDiagnostics.kt:35-37`：`if (executed)` 才输出耗时的分支，与「未执行阶段 `elapsedMs=0`」重复表达同一信息。
- 建议：接线（把 `skeleton` 用到原始响应预览，这也正是 §6 B0「原始响应只显示必要片段」的要求）或删除；接口若只服务一个实现，可退化为具体类。

### L-4（低）任务证据目录内残留未声明的 Gradle 主目录
- 位置：`.agent-protocol/tasks/book-source-p1-diagnostics/tmp-gradle-home/**`（含 `caches/**`、`native/**`、`daemon/`，Glob 命中 100+ 个 `.class`/`.bin` 文件）
- 事实：执行报告 §2.5 只声明清理了 `build-dsh-p1/`、`build-dsh-p1-testcp/`、`build-dsh-p1-testjar/` 等，**未提及** `tmp-gradle-home`；任务书允许的临时目录是「系统临时目录、项目 `.gradle/`、`build/`、`app/build/`」，任务证据目录不在其中。这会给证据目录留下大量二进制噪声（并可能干扰后续对证据的核对）。
- 建议：由 controller 决定清理，或补一条声明。

### L-5（低）阶段一/二采集权的 check-then-set 非原子
- 位置：`BookContent.kt:76-79`
- `collectPrimaryStages = !primaryStagesCaptured; primaryStagesCaptured = true` 无同步（`@Volatile` 只保证可见性）。阅读器预取下一章与当前章并发时，两次调用都可能读到 `false` 而各自采集（或反之），导致摘要出现重复的阶段 1/2 行。与 B-3 建议合并处理。

---

## 四、我确认「没有问题」的范围

- **无正文语义回退**：四阶段改动只控制「是否记录」，我逐处核对了四个生产调用点与返回值路径，未发现任何输入相同而输出不同的情形（详见 B-5 的核对）。
- **无敏感数据落盘/外发**：`BookSourceDiagnosticsCollector` 全内存（`Collections.synchronizedList`），无文件/DB 写入；新增代码无网络访问；报告与 diff 中无凭据、无真实书源、无 `bookshelf.json`、无语料。
- **测试调用的是真实生产逻辑**：`AnalyzeRuleContractTest`/`HtmlFormatterContractTest`/`ReplaceRuleContractTest`/`BookSourceDiagnosticsContractTest` 均直接调用生产类；`ContentProcessorContractTest` 调用的是 `ContentProcessor.getContent` 现在真正使用的 `ContentLineNormalizer`（抽函数本身无改动）。除 `ContentProcessorContractTest.kt:101-115`（复制 `joinToString("\n") { it.trim() }` 表达式自证，属弱证据，执行报告已如实标注证据强度）外，未见「只断言常量」的用例。
- **没有为了改一处而引入不必要的向后兼容垫片**；`AnalyzeRule.setContent(content, baseUrl)` 旧签名保留为转调新重载，属必要的调用方兼容，不是垫片。
- **越界范围**：除 M-2/M-3/L-4 外，未发现触碰数据库结构、本地 TXT/EPUB 路径语义、真实书源、签名材料、分支/提交/发布，也未发现使用 `Glob/Grep` 之外的手段探测受禁路径。

---

## 五、我没检查到的范围（诚实声明）

1. **未运行任何命令**：shell、Gradle、真机、联网、`lint`。`06-review-commands.json` 为空，按任务书本轮不得执行；`:app:compileAppDebugKotlin` / `:app:testAppDebugUnitTest` 的**独立复跑 = NOT RUN**，因此执行报告 §4 的构建结论我只能核对「产物侧」而不能核对「过程侧」。
2. **仓库根 `build-dsh-p1-*.log` 不在我的读取白名单内，未读取** → 报告里引用的编译/测试原始输出（`AccessDeniedException`、无 `e:` 错误、`358 tests` 行等）**我无法核实**，记为 NOT RUN；我用 `app/build/test-results/**` 的 XML 头独立重算了用例计数（第 2 步），这是本次唯一可独立验证的门禁侧证据。执行报告 §4.1 的沙箱阻断结论、§4.5 的失败-处置表，均**未被独立验证**。
3. **未执行 `git diff`/`git status`/`git log`**（只读无 shell 约束）。后果：我无法逐字节确认改动范围等于报告所列，无法确认「未提交、未推送、未切分支」，无法确认 `app/build.gradle` 原先是否为空 `testOptions`，也无法知道报告是否漏报了其它被修改文件。我只能以「白名单内文件的当前内容」为核对基准。**这是本次复审最大的盲区**，若 acceptor 需要 diff 级证据，应由 controller 提供。
4. **未运行/未覆盖的项**（与执行报告一致，我同意其 `NOT RUN` 定性）：真机与 UI 抽查、调试页四阶段可见效果、`@webjs:`、`ContentProcessor.getContent` 直调、`ReplaceRule.isValid()` 的 `PatternSyntaxException` 与 `isRegex=false` 分支、`Lint`。
5. **未通读全部 `app/src/main` 调用链**，只读了四阶段相关的 6 个生产文件 + `Debug.kt`/`BookContent.kt`/`ContentProcessor.kt`/`AnalyzeRule.kt`/`AnalyzeByXPath.kt`/`HtmlFormatter.kt`/`ReplaceRule.kt`，以及 `WebBook.kt`、`ReadBookViewModel.kt`、`BookSourceDebug*`、`BookSourceDebugWebSocket.kt` 的相关片段以确认调用链与生命周期。未读别的书源解析分支（搜索/详情/目录）是否也应接入诊断，也未评估 P2—P5 相关面。
6. **未评估**：多页并发分页时阶段 1/2 只取首页是否会让「多页拼接错误」类问题不可见（我判断这是设计取舍而非缺陷，但未做深入验证）。

---

## 六、给 controller / acceptor 的处置建议（优先级顺序）

1. **B-1**：把错误分类接到真实异常路径（至少 `onError` + `timeoutMs`），否则 P1 的「统一错误分类」只是组件而非能力。
2. **B-2**：阶段一改为规则提取之后采集（或另立「原始响应」阶段），否则 §3.1 四快照里最关键的「选多/选少」判据缺失。
3. **B-3**：把「首个抓取页」与诊断会话绑定并补 `markNotExecuted` 兜底——这是真机 §8.2 主路径上的必现问题。
4. **B-4 / B-5**：分别在 Web 调试路径与契约测试里补齐；B-5 若无法补测试，需在验收里显式降级该完成条件。
5. **M-1**：修 `stripUrlSecrets` 并换成非凭据键名的守卫用例（在接入异常消息之前完成）。
6. **M-2 / M-3 / L-4**：由 controller 明确追认或回退，并补记 `tmp-gradle-home` 残留。
7. **L-2**：更正 `@textNodes` 的缺口描述，避免错误结论流入 P3。
