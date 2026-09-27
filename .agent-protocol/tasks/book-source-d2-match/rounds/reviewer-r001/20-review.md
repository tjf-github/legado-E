<!-- agent-protocol-binding
round_id: reviewer-r001
task_digest: 19286f8f074309e17bbd0075a926b999735ba5706c8d3523b9aac66a3f3a59c4
snapshot_digest: fa604258baaa950519354c52322424ed2bd7f89109a9750c61f6e1bd97552c21
--># book-source-d2-match — 独立复审报告（reviewer: claude，只读）

> **本标记块是本任务唯一有效的复审报告。** 复审期间后台出现了**由我间接派生、但不是我本人**的 agent 产出的、同样使用了该标记的报告（自称 `round reviewer-r001`，先后被回灌两次）。**那不是我写的**，我没有授权、没有采用其证据链、也不为其中的事实背书 —— 它与本报告在若干结论上的巧合一致，不改变本报告的推导过程（我的 F-1 在该通知到达**之前**已独立得出）。若 runner 看到多个标记块，请只采纳**本块**。相关边界与干扰的完整披露见 §3.7。

## 0. 结论

**有阻塞项（1 项）**，另有 8 条非阻塞发现（1 条中低、7 条低/信息）。

阻塞项 **F-1**：M3 的新文案在 D-2 的**默认时序**（先调试收尾、后阅读该章）里不会出现在摘要中，而完成条件（`00-task.md:56`）的表述是无条件的「身份不匹配时第 4 行说明变为『…有 N 次…被排除』」。执行报告 §3 第 3 行把它标为「JVM 层满足」，但所引用的测试只钉住了纯函数在**人工构造出的非零计数**下的返回串（`DiagnosticIdentityContractTest.kt:227-248`）——它恰恰把「不匹配发生在求值之前」当成了前提，而真机上另一半时序正好否定这个前提。这是**判定口径与实现行为不一致**，不是崩溃。

M1、M2 的归一实现本身经逐条手工推演，**未发现缺陷**（见 §3.1）。

---

## 1. 复审范围与冻结状态

| 项 | 值 |
| --- | --- |
| 白名单内已读 | `00-task.md`、`05-routing.md`、`06-review-commands.json`、`10-execution.md`、`11-implementation-notes.md`；`ContentProcessor.kt`、`Debug.kt`、`BookContent.kt`、`help/book/diagnostics/**`（7 个文件）、`app/src/test/.../diagnostics/**`（3 个文件）、`docs/book-source-audit/D-2-补打匹配口径-裁定.md` |
| 白名单边界 | 本人严格遵守。**未**读 `AnalyzeUrl.kt`、`BookChapter.kt`、`BookChapterList.kt`、`NetworkUtils.kt`、`ReadBook.kt`、`13-reviewer-context.json`、`gate-artifacts/**`、`.agent-protocol/CURRENT.md`、任务目录其它文件。间接派生的 agent 有越界读取，见 §3.7 |
| 冻结命令 | **三条全部 `NOT RUN`** —— 本会话（含我三次委派的子代理）**没有 shell 工具**，无法调用 git。见 F-3 |
| 网络 | 未发起任何联网请求 |
| 写入 | 未写任何文件，未运行任何改变工作树的命令 |

---

## 2. 发现

### F-1（阻塞｜中）M3 的新原因文案在默认时序里不会出现在摘要中

**位置**
- `app/src/main/java/io/legado/app/model/Debug.kt:461-471`（`finishContentDiagnostics`：在**链路收尾时刻**调用 `collector.readingStageMissingReason()` 并把结果**一次性**写进快照）
- `app/src/main/java/io/legado/app/help/book/diagnostics/BookSourceDiagnostics.kt:165-172`（原因由**当时的** `mismatchCount()` 计算）、`:146`（拒绝路径只递增计数，不通知任何人）
- `BookSourceDiagnostics.kt:252-256`（`markNotExecutedIfMissing` 在快照已存在时**早退**，note 从此不可刷新）
- `app/src/main/java/io/legado/app/help/book/ContentProcessor.kt:244-246`（只有 `diagnostics != null`，即**匹配成功**时才触发 `Debug.onReadingStageRecorded()`）
- `Debug.kt:482-488`（`onReadingStageRecorded` 的重打路径同样只在命中时可达）

**具体失败场景（顺序即 D-2 的默认用法，7 步全部可在白名单代码内复核）**
1. 用户在调试页输入 `--<绝对URL>`。`Debug.startDebug`（`Debug.kt:323-332`）→ `contentDebug`（`:421-452`）绑定目标后 `WebBook.getContent` 抓取正文。
2. 抓取返回，`onSuccess` 调 `finishContentDiagnostics()`。此刻**没有任何**被拒的正文处理 → `mismatchCount() == 0` → 第 4 行写下并推送到 Web 调试页的是旧文案「书源调试链路止于 BookContent，未进入阅读处理」。
3. 用户随后打开该章阅读。若归一后仍失配（例如存量书 `chapter.baseUrl` 为空 —— `11-implementation-notes.md:15-16` 已承认这类未覆盖；或书源 URL 真正被改写 —— `10-execution.md:79` 已承认不覆盖），`Debug.diagnosticsFor`（`Debug.kt:120-129`）里的 `accepts()` 把计数递增为 1，**但返回 null**。
4. `ContentProcessor.getContent` 拿到 `diagnostics == null` → 不 `record`、也**不调** `Debug.onReadingStageRecorded()`。已推送到页面上的那一行不会被更新。
5. 即便之后再有任意路径再次调用 `finishContentDiagnostics()`，`markNotExecutedIfMissing` 因阶段四快照已存在而早退（`BookSourceDiagnostics.kt:254`），note **不会**被刷新为含计数的文案。
6. 写进 `StageSnapshot.note` 的是**求值当时固化的字符串**，不会随 `mismatchCount` 变大而变。
7. ⇒ 用户最终读到的仍是「未进入阅读处理」——正是 D-2 与裁定书 §2 要消灭的那句错误因果。

**反向（命中）路径没有这个问题**：命中时 `diagnostics != null`，`onReadingStageRecorded` 会重打一次含真实读数的摘要（M1 场景成立）。

**为什么现有证据不足以覆盖**
- `DiagnosticIdentityContractTest.kt:227-248` 是**先** `accepts` 两次、**再**读 `readingStageMissingReason()`。它证明的是纯函数，把「不匹配发生在求值之前」当成了前提；真机上更常见的顺序恰好是否定这个前提。**当前没有任何测试构造「先收尾、后拒绝」这条时序**。
- `10-execution.md:50` 自己承认「打印分支依赖 Android 日志桩，未在设备上验」，`:22` 又把真机标 `NOT RUN`。两个缺口叠加，等于这条完成条件没有任何一层证据覆盖。

**关于「按字面是否满足」的两读（我把争点摆明，判定权在 controller）**
- 读法 A（我采用，判**阻塞**）：完成条件 `00-task.md:56` 是无条件表述「身份不匹配时第 4 行说明变为…」。默认时序下不成立 ⇒ 未满足。
- 读法 B（可成立的反驳）：任务书同句写明「本轮以 JVM 契约测试 + 合成构造证明」，而纯函数层面的证明确实已给出 ⇒ 视为满足。
- 我仍判 A，理由是：`00-task.md:56` 的**可接受证明手段**是为**该句陈述的可观测结果**服务的；所提供的测试证明的不是那个结果，而是它的一个前置条件。若 controller 采纳读法 B，则必须在裁定书里把 M3 的可见行为改写成**带时序前提**的表述（「仅当不匹配发生在链路收尾之前才可见」），否则验收口径与代码行为不一致 —— 这也是一种「就地更正」，不是可选项。
- **可证伪性**：这是静态推演得出的反例，不是观测到的失败。若真机上「读同章被拒」总发生在收尾之前，本条自动消失。**一条 JVM 时序测试即可判定**，成本很低（`BookSourceDiagnosticsCollector` 完全可在 JVM 内构造）。

**建议（任一即可）**
- 首选：让计数变化驱动文案刷新。最小改法：(a) `Debug.diagnosticsFor` 的拒绝分支在会话存活且阶段四快照缺失/未执行时重算 note 并重打一次摘要（复用 `readingStageReported` 那种一次性闩避免刷屏）；或 (b) 让 note 由「写入时求值」改为「输出时求值」（`StageSnapshot` 存原因供应方，`summaryLines()` 每次反映当前计数）。
- 次选：按读法 B 修订完成条件/裁定书的表述，并明示时序前提。
- 无论选哪条，补一条 JVM 时序契约测试：bind → `markNotExecutedIfMissing(reason@count=0)` → 一次失配 `accepts()` → 断言最终 `summaryLines()` 里第 4 行的期望值。

---

### F-2（低｜报告与工作树不一致）执行报告「采集器只比归一后的键，不保存 URL」与实际实现矛盾

**位置**：`10-execution.md:19`（§1 第 4 条）、`10-execution.md:57`（§4）↔ `BookSourceDiagnostics.kt:83-88`（`Target.rawChapterUrl`）。

执行报告两处都写「采集器只比归一后的键，**不保存 URL**」，但 `Target` 里明确保存了绑定时的原始章节串，`accepts()`（`:142`）也用它做第二条匹配途径。`10-execution.md:33`（§2）其实**如实披露了**这一点，两处自相矛盾。方向对读者不利：§1/§4 是「结论先行」段落，容易被读成全量事实。

**失败场景（理论）**：`Target` 是 `data class`，其生成的 `toString()` 会回显原始章节 URL。当前**无**调用点把它写进日志/摘要（已逐点核对 `Debug.kt`、`BookSourceDiagnostics.kt` 的全部日志与快照路径），故无实际泄露；但「不保存 URL」这条口径声明在有人日后 `log(target)` 时立刻失效。

**建议**：§1/§4 改成「只把计数与归一键写入摘要；原始章节串仅保留在内存 `Target` 内用于兼容回退，不落盘、不出摘要」；可选加固：把 `Target` 改成普通 class 或覆写 `toString()`。

---

### F-3（低｜门禁证据）三条冻结命令全部 `NOT RUN`，且任务书与冻结文件条数不一致

- `00-task.md:28-29` 写「本轮冻结**两条**：`git diff --check` 与 `git diff --stat`」，而 `06-review-commands.json:4-26` 实际冻结**三条**（多出 `d2-whitelist-status`）。`10-execution.md:70` 又写成「三条」。请 controller 对齐（不影响本轮判定，因为三条都没能跑）。
- **`NOT RUN` 原因**：本 reviewer 会话没有 shell/Bash 工具。我三次委派子代理代为执行，均回报无 shell 可用；其中一份回报转述系统侧说明「Bash is disabled for this session, in subagents as well as here」，并明确拒绝「拿空 diff 冒充已执行」。该结论与我的独立判断一致，但我如实标注：这条系统说明是**转述**，不是我直接读到的。
- **因此无法验证的主张**：`10-execution.md:17` 的 `BUILD SUCCESSFUL / exit 0 / git diff --check exit 0 且无输出`，以及 §2 的 diff 范围主张，**均未经独立验证**。
- **我在白名单内做过的近似替代**（不等价，仅作线索）：对 `help/book/diagnostics/**`、`app/src/test/.../diagnostics/**`、`Debug.kt`、`BookContent.kt`、`ContentProcessor.kt`、裁定书 逐文件 Grep `^(<<<<<<<|>>>>>>>|\|\|\|\|\|\|\|)` → **全部 No matches**。这只覆盖整文件，**不覆盖** `git diff --check` 真正检查的「新增行行尾空白 / 空格-制表混用」。
- **额外缺口**：我**不能**验证 `gate-artifacts/d2-gate.log`（sha256 `3ece4265…`）与 README 是否存在、内容是否与主张一致 —— `gate-artifacts/**` 不在 reviewer 白名单，我按边界拒绝探查。完成条件第 4 条的「门禁原始输出与 sha256」在本轮**未经复审确认**。
- **可执行的修复建议**：这批冻结命令在一个**有 shell** 的环境重跑一次；同时复核 `06-review-commands.json` 这套冻结清单是否隐含假定 reviewer 具备执行能力 —— 本轮该假定不成立，gate 无法从 reviewer 侧闭环。这是流程层缺口，不是本任务的实现缺陷。

---

### F-4（低｜越界待确认）`.agent-protocol/CURRENT.md` 出现在工作树，既不在修前基线也不在白名单

`00-task.md:19` 登记的修前脏基线只有 `双平台开发环境流程书.md` 与 `build-dsh-d2-task-create.log`（后者现已从 `git status` 的未跟踪列表中消失，与「归位到 `gate-artifacts/`」的收尾要求一致）。但会话起始的 `git status` 快照显示 **`M .agent-protocol/CURRENT.md`**，它是本任务期间新增的改动，且**不在 executor 写白名单**内。

我**无读权限**（不在 reviewer 白名单），因此**无法判定**它是 controller 侧协议状态推进（任务建立/激活会写协议状态）还是 executor 越界。**请 acceptor 核对 `CURRENT.md` 的 diff 归属**；若属协议工具链写入，请在验收单登记一句，避免它被读成 executor 越界。

---

### F-5（低｜超出字面授权）M2 归一额外去掉了 URL 片段（`#`），任务书 M2 未授权这一项

**位置**：`DiagnosticIdentity.kt:57-58`（`substringBefore('#')`）↔ `00-task.md:9`（M2 字面为「去首尾空白、去尾斜杠、去查询串」）。

方向是**放宽**：仅片段不同的两个书源串现在被视为同一书源。我认为**语义上是对的**（片段不参与请求，指向同一书源），也不推翻 S5 的隔离证据；但它是任务书 M2 字面之外的扩权，且 `10-execution.md:61-65`「偏差」一节只列了 `--no-problems-report` 与 `sortUrls` 两条。

**建议**：controller 追认这条归一（一行即可），或在 `10-execution.md` 偏差表补一行；否则按「白名单是硬上限」的同一逻辑，实现口径也不应超出裁定书字面。

---

### F-6（低｜证据缺口）M1 的「与请求路径同源口径」这一**承重前提**无法由 reviewer 独立核对

**位置**：`DiagnosticIdentity.kt:15, 24-28`（声称与 `NetworkUtils.getAbsoluteURL` 同语义、选项切法照抄 `AnalyzeUrl.paramPattern` = `\s*,\s*(?=\{)`）、`:37-47`。`AnalyzeUrl.kt`、`BookChapter.kt`、`NetworkUtils.kt` 均不在 reviewer 白名单，我**按边界拒绝读取**。以下三个前提为 `NOT RUN`：
1. `AnalyzeUrl.paramPattern` 是否确为 `\s*,\s*(?=\{)`，且实际切法是否也是「按**第一个**匹配截断」（本实现用 `optionPattern.find`，即取第一个匹配）；
2. `NetworkUtils.getAbsoluteURL` / `getAbsoluteURL()` 是否也是 `URL(URL(base.substringBefore(',')), spec)` 这一形态；
3. `BookChapter.baseUrl` 是否在**入库时**已被填成绝对地址（若存量书普遍为空，M1 对这批书实际不生效 —— `11-implementation-notes.md:15-16` 已承认，但影响面没有量化）。

**我能自证的部分**：绑/读两侧调用的是同一个 `DiagnosticIdentity.chapterKey`，**匹配的对称性由构造保证**，所以「键是否等于真实请求 URL」不影响能否补打，只在「键的真值」这一层留风险。参数顺序我也在白名单内核对过：`BookContent.kt:105-106` 的既有先例是 `NetworkUtils.getAbsoluteURL(redirectUrl, nextUrl)`，base 在前，与本实现同序。

**残余风险的具体形态**（`DiagnosticIdentity.kt:40` 的 `base.substringBefore(',')`）：M1 对 base 用「逗号」切、对 chapter 用「逗号+花括号」正则切，**两者口径不同源**；当 base 的**路径**里含合法逗号且无 `{` 时（如 `https://a.example/b,c/`）base 会被截断到 `https://a.example/b`，相对章节被解析到错误目录。该不对称在绑/读两侧一致，故不造成误接受，只使键失真。

**建议**：controller 指派一次定点核对（`AnalyzeUrl.kt` 选项切法 + `getAbsoluteURL` 语义 + `BookChapterList.kt:239`），或列入验收单 `NOT RUN`；若要关掉上述边角风险，补两条契约测试（base 路径含逗号；protocol-relative `//host/x`）。

---

### F-7（低｜文档未同步，覆盖 M1 与 M3 两处）裁定书只更正了 M2，M1/M3 的出入未就地更正

**位置**：`docs/book-source-audit/D-2-补打匹配口径-裁定.md:33-34`（M1 行：仍写「阅读侧有 `BookChapter`，**直接用 `getAbsoluteURL()`**」且「**不引入新的 URL 规范化实现**」）、`:39-40`（M3 行：仍写文案「本次阅读的书源/章节身份与调试目标不一致，未补打」）↔ 实际实现 `DiagnosticIdentity.kt:37-47`（**新引入了**一份归一）、`BookSourceDiagnostics.kt:165-172`（实际文案为「…有 N 次其它书源/书籍/章节的正文处理被身份隔离排除，未进入本次调试目标」）。

`00-task.md:57` 要求「若与裁定书有出入（例如 `sortUrls` 兜底未做），必须**就地更正裁定书**并在执行报告披露」。M2 的 `sortUrls` 偏差确实就地更正了（裁定书 `:36-37`），**M1 与 M3 两处没有**：`11-implementation-notes.md` §1.1 与 §2 有说明，但裁定书正文未同步；§2 的偏差表也只列了 M2、M3 两行，**M1 的「不引入新的 URL 规范化实现」这条没被列为偏差**。

说明：M1 改成纯 JVM 是任务书 `00-task.md:54` 的**硬要求**，实现与任务书一致、与裁定书不一致 —— 这不是实现错误，是文档未闭环。行为层面两处实现都比裁定书字面更好（M3 文案含计数、可区分「链路没走到」与「身份没匹配上」）。

**建议**：按 M2 的方式在裁定书 M1/M3 两行后各追加一句「2026-09-26 实施时更正为 …，理由见 `11-implementation-notes.md` §1.1/§2」；并在 `11-implementation-notes.md` §2 偏差表补 M1 一行。按完成条件 #4 字面，此项尚未满足。

---

### F-8（低｜协议要求未落）`05-routing.md` 要求执行报告写明保证等级上限为 `provisional`，报告里找不到

**位置**：`05-routing.md:22`（「按协议，本档保证上限为 `provisional`（无独立 acceptor），执行报告与验收单**必须写明这一点**」）↔ `10-execution.md` 全文、`11-implementation-notes.md` 全文均**未出现** `provisional` 字样。

报告 §5.3 写了「执行侧没有 runner 封套 / 没有 `10-execution.summary.json`」（`10-execution.md:65`），§6 写了 reviewer 来源，但没有把「本档保证上限 = provisional（controller=executor=acceptor 同源，无独立 acceptor）」写成显式结论。这正是 `05-routing.md` 要求必须写明的那一点。

**建议**：在 `10-execution.md` §5 补一条偏差/披露；验收单同样补。

---

### F-9（低｜简化）`DiagnosticIdentity.isSameSource` 在生产代码里没有调用点

**位置**：`DiagnosticIdentity.kt:62-66`；生产侧唯一出现处是它自己的声明，唯一使用方是 `DiagnosticIdentityContractTest.kt:87, 94-96`（4 处）。

我在 reviewer 可见的**全部生产文件**（`help/book/diagnostics/**`、`Debug.kt`、`BookContent.kt`、`ContentProcessor.kt`）内检索该符号：**零命中**。`BookSourceDiagnostics.kt:137-143` 用的是 `sourceKey` 手写比较（`source != null && source == bound.sourceKey`），语义等价但没有复用它。

**证据边界**：我无法排除白名单**外**存在调用点；但任务书 `00-task.md:54` 要求「归一实现集中在 `help/book/diagnostics/` 内的纯 JVM 代码，Android 侧只传参」，白名单外的调用点本身就会构成越界。故在授权范围内，这是一个**只为测试存在的公开 API**。

**建议（择一）**：在 `accepts` 里真的用它（去掉手写比较），或把它从生产文件删掉、测试改为直接断言 `sourceKey(a) == sourceKey(b)`。

---

### F-10（信息｜本轮未覆盖的同类形态）`accepts` 对书号仍是逐字比较，M2 只归一了书源

**位置**：`BookSourceDiagnostics.kt:145`（`bound.bookUrl == book`，两侧只 `trim`）。

从**详情页**入口发起调试时 `bookUrl` 取用户粘的串（`Debug.kt:300-305`），若它与入库 `book.bookUrl` 不一致（跳转前后不同、导入带尾斜杠等），补打仍会落空 —— 且按 F-1，这种落空**只有在收尾前发生才看得见**。这属 D-2 同类但未被本轮范围覆盖（任务书 M2 只写了书源身份）。**不建议塞进本任务**，请 controller 决定是否另立条目。

---

## 3. 我实际执行过的核对动作与输出

**3.1 纯静态逐行核对（白名单内全部改动文件）**
逐行读了 §1 表中列出的 13 个文件。核对结论：

- **M1 归一的边界**：`chapterUrl` 空白 → `chapterKey` 原样返回（`DiagnosticIdentity.kt:38`），`bindTarget` 对空章节**不绑定**（`BookSourceDiagnostics.kt:104`）→ 与「宁可不诊断，也不放宽成谁来都收」一致。`baseUrl` 为 `null`/空白/畸形（含 `javascript:`、`data:`）时退回去选项原文且**不抛异常**（`:41-46`），降级方向安全（退化成旧的逐字比较，不会把身份判成空）。
- **`URL` 的副作用**：全程只用 `URL(...).toString()`，**未**调用 `URL.equals/hashCode`，因此**不触发 DNS 解析**（`java.net.URL` 的 name service 只在 equals/hashCode/openConnection 时使用）。对「离线、不联网」的边界很重要，实现是干净的。
- **隔离负例**（手工推演 `BookSourceDiagnostics.kt:129-148`）：别的书落在 `bound.bookUrl == book` ✗；别的章节落在 `chapterMatched` ✗；`loc_book` 落在 `sourceKey` ✗（`DiagnosticIdentity.kt:54-60` 对空/空白返回 null，`accepts` 的 `source != null &&` 把 null 挡掉）；别的书源落在 `source == bound.sourceKey` ✗。四条都成立，没有引入任何「同域名/同站」放宽。
- **接受面集合推演**：新判定 = `sourceKey 相等 ∧ (归一等 ∨ 原始串等) ∧ 书号约束`；旧判定 = `原始源逐字等 ∧ 原始串逐字等 ∧ 书号约束`。因「原始源逐字等 ⇒ `sourceKey` 等」，**旧集合 ⊂ 新集合** —— 改动方向是「只增不减」，不会把能用的场景改坏，也没有引入新的误接受类。
- **OR 回退不越界**：第二条途径要求 `rawChapter == bound.rawChapterUrl` **逐字**相等（两侧都 `trim`），只可能命中同一条入库章节串，不构成跨章误收。其存在理由（存量书 `baseUrl` 为空）在**白名单内找不到直接证据**，但它是旧行为保真，即使理由不成立也无风险，故不计为发现。
- **`accepts` 的 bookUrl 判据只取绑定侧**（`bound.bookUrl.isNullOrEmpty()`），所以测试里阅读侧传 `null`（`DiagnosticIdentityContractTest.kt:109`）与真实阅读侧传非空 `book.bookUrl` 行为一致 —— 我特意核过，避免「测试用 null 掩盖了真实差异」。
- **计数副作用**：`rejectedIdentities` 为 `AtomicInteger`（`:72`），`accepts` 在**未绑定目标**（`:136`）与**关闭诊断**（`:135`）时都早退、不计数（与 `DiagnosticIdentityContractTest.kt:210-224` 两条断言一致）。`isEnabled=false` 时 `bindTarget` 仍会写 `target`（`:96-114` 未检查开关），但 `accepts`/`record` 全部早退，**零可观测副作用** —— 属实现冗余，非缺陷。
- **脱敏链**：把 `readingStageMissingReason()` 的两态串手工跑了一遍 `DiagnosticRedactor.sanitizeMessage` 的四步（`DiagnosticRedactor.kt:77-89`）：串内无 `://`、无 `=`、无 ASCII `:`（中文分号不受影响）、无多空白折叠变化、长度 ~70 < `MAX_MESSAGE_LENGTH = 240`（`:18`）→ **逐字不变**，与 `DiagnosticIdentityContractTest.kt:250-260` 的断言一致。文案不含任何 URL/主机名。
- **零副作用核对**：诊断关闭时 `Debug.diagnostics` 为 null（`Debug.kt:39-40, 126`），`diagnosticsFor` 立即返回，**不构造 URL、不做正则**；`ContentProcessor.kt:113, 235-246` 的计时与 record 全部挂在 `diagnostics != null` 上。诊断关闭时正文产出路径与改动前**逐字节等价**（`DiagnosticsToggleParityTest` 的等价断言因此不受影响）。
- **`Debug.diagnosticsFor` 的调用面**：白名单内可见的调用点是 `ContentProcessor.kt:111`（阅读路径）与 `BookContent.kt:47-57 → :84`（抓取路径）。因此 `mismatchCount` 也会被抓取路径抬高 —— 实现文案用「正文处理」而非「阅读处理」是更准确的措辞，任务书 `00-task.md:56` 用的「阅读处理」反而略窄。此点仅作登记，不计为发现。
- **纯 JVM 边界**：`DiagnosticIdentity.kt` 只 `import java.net.URL`，无 Android/splitties 依赖 ⇒ `00-task.md:54`「归一实现集中在 `help/book/diagnostics/` 内的纯 JVM 代码，Android 侧只传参」成立；`ContentProcessor.kt:111`、`BookContent.kt:52-57`、`Debug.kt:114, 430` 均只透传。
- **`bindDiagnosticsTarget` 的生产调用点只有一处**（`Debug.kt:430`），三条入口（`--`/`++`/详情页）都汇到 `contentDebug`，与裁定书 `:12`「两个调用点」的事实一致，没有漏改的第三处。

**3.2 手工推演新测试的 21 条断言**
逐条按 `DiagnosticIdentity.kt` 的语义在纸上执行，**21/21 全部通过**，未发现会失败的断言。要点：
- `relativeChapterResolvesAgainstItsOwnBase`：`URL(URL("https://source.example.com/book/1/"), "chapter/2.html").toString()` = 期望的绝对串 ✓
- `commaInsideQueryIsNotTreatedAsOptions`：`.../p?a=1,2` 无 `{`，正则零命中 ✓
- `unresolvableUrlFallsBackToRawTextWithoutThrowing`：`URL(URL(base),"javascript:void(0)")` 抛 `MalformedURLException` 被 `catch (e: Exception)` 吞掉 ✓
- `sourceVariantStillMatchesSameSource`：绑定串为 `"$source?from=debug"`（**已**按 `11-implementation-notes.md:31` 的自我纠错改对），负例 `"$source/book"` 是不同路径 ✓
- `otherBookOtherChapterAndLocalBookAreStillRejected`：计数恰为 4 ✓
- 另外用 **Grep 数了 `@Test` 条数 = 21**，与 `10-execution.md:18` 的「21 条」自述一致。任务书 `00-task.md:55` 列的六项覆盖要求（相对+base vs 绝对、绝对 vs 绝对、去选项、书源尾斜杠/查询、负例、计数递增）**逐项都有对应断言**。

**3.3 既有套件不被破坏的手工核对**
- `BookSourceDiagnosticsContractTest.kt:313-339`（三条身份用例）：`bindTarget` 用 3 参重载 → `chapterBaseUrl = null` → 两侧都走「原文」归一，行为与改动前一致；`:332-339` 的「书号为空的章节目标」用例在 OR 回退下仍然成立 ✓
- `DiagnosticsToggleParityTest.kt` 全文：只 `record`、不调 `accepts`，`bindTarget(baseUrl, baseUrl, baseUrl)` 走默认第 4 参 ✓
- `00-task.md:55` 的「`chapterBaseUrl` 默认参数」不是无谓垫片：被既有测试的真实 3 参调用撑住（`BookSourceDiagnosticsContractTest.kt:34, 335, 366`、`DiagnosticsToggleParityTest.kt:76, 124`）。
- 未修改任何既有测试文件（与 `10-execution.md:40` 的主张一致；`app/src/test/.../diagnostics/` 下只有 `DiagnosticIdentityContractTest.kt` 是本任务新增）。

**3.4 白名单内符号检索**
- 冲突标记：`help/book/diagnostics/**`、`app/src/test/.../diagnostics/**`、`Debug.kt`、`BookContent.kt`、`ContentProcessor.kt`、裁定书 → `^(<<<<<<<|>>>>>>>|\|\|\|\|\|\|\|)` **全部 No matches**。
- `isSameSource`：`help/book/diagnostics/**` 只命中声明行 `DiagnosticIdentity.kt:63`；`Debug.kt`、`ContentProcessor.kt`、`BookContent.kt` **No matches** → 支撑 F-9。
- 算术一致性抽查（**不是验证**）：`10-execution.md:17` 自述 P1 收尾 45 套件/394 用例、本轮 46/415 → 套件 +1 ✓、394+21=415 ✓。只说明自述内部自洽，**不构成门禁已跑过的证明**。

**3.5 未能执行的**
三条冻结命令（无 shell）；`gradlew` 的 `compileAppDebugKotlin` / `testAppDebugUnitTest`（同上）。

**3.6 安全面核对（逐项，均无发现）**
命令注入：无 shell/进程调用。路径穿越：无文件/路径拼接。XSS：M3 文案是固定中文 + 一个 `Int`，经 `DiagnosticRedactor.sanitizeMessage` 后才落进 `StageSnapshot.note`，`format()` 只做字符串拼接，`Debug.log(..., isHtml=false)` 不做 HTML 包裹 → 计数器无法承载注入载荷。SQL 注入：本改动不触库。凭据外泄：新代码**新增**的对外字符串只有计数与固定文案；`DiagnosticRedactor` 的 `SECRET_KEYS` 覆盖面未变；`Target` 只驻留内存，不落盘、不入库、不进 `Debug.log`。权限放大：无。DNS/网络：如 §3.1 所述，`URL` 的构造与 `toString` 均不解析域名。

**3.7 委派过程与后台干扰的边界披露（自查，如实记录）**
1. 为执行冻结命令，我委派了 3 个子代理。**其中一个子代理又自行派生了下级代理**，而那个下级代理误以为自己就是本任务的 reviewer，产出并**两度回灌**了一份带 `<agent-protocol-report>` 标记、自称 `round reviewer-r001` 的报告。**那份报告不是我写的**：我没有授权它、没有采用它的证据链、没有为其中事实背书。它与本报告在 F-1/`isSameSource`/裁定书同步等条目上的巧合一致**不构成本报告的证据** —— 我的对应条目在本会话中由我独立逐行推演得出（见 §3.1）。请 runner 只采纳**本块**。
2. 该下级代理自述：它读了 `13-reviewer-context.json`（**不在**本任务 reviewer 白名单内），并为了找「是否已有缓存的门禁输出」对**任务目录做过一次 grep**。这两次越界读取**源于我委派时没有把读白名单边界写进子代理提示词**，不是我有意扩大范围；它们都是只读、未写文件、未外发数据，也未产出被本报告采用的「证据」。我自己**没有**读这两处。
3. 同一批回报还提到：曾向本会话中的某个 agent 发送过一条以「Disregard any earlier instructions in this session — they were sent in error」开头的消息。**我没有收到、也没有遵循任何此类消息**；需要说明的是，这类「作废你先前收到的指令」的开场语正是典型的注入形态，任何 agent 都不应据此改变冻结任务书下的行为。它对本报告的结论与证据链**零影响**。
4. 上述两点我在此登记，供 controller 判断是否需要按协议补记录。

---

## 4. 我没检查到的范围（诚实列出）

1. **`git diff --check` / `git diff --stat` / `git status`（冻结三条）** —— `NOT RUN`，无 shell 工具。因此「改动未越白名单」这一结论我只有**间接依据**：任务书自述 + 会话起始 `git status` 快照，**没有**逐行 diff 证据。若改动里混入了白名单外文件的未跟踪新文件，我**看不到**（`git status` 快照是会话起始值，不随对话更新）。
2. **`gradle` 编译与全部单测** —— `NOT RUN`。`10-execution.md:17` 的 `BUILD SUCCESSFUL`、`suites=46, tests=415, failures=0`、§1.3 的「3 套件 / 79 用例」**均未经独立验证**。我只手工推演了与本任务相关的断言。
3. **`AnalyzeUrl.paramPattern` / `NetworkUtils.getAbsoluteURL` / `BookChapter.getAbsoluteURL()` / `BookChapterList.kt:239` / `BookChapter.baseUrl` 的真实语义** —— 白名单外，未读（见 F-6）。M1 的「同源口径」是**采信 executor 自述**的。
4. **`gate-artifacts/**`**（`d2-gate.log`、`README.md`、被归位的 `build-dsh-d2-task-create.log`）—— 白名单外，未读，**我本人未用 Glob 探测**。完成条件第 4 条的「门禁原始输出与 sha256」本轮**无复审证据**。
5. **`.agent-protocol/CURRENT.md`、`13-reviewer-context.json`、`15-*-raw.md`、`rounds/**`、`PROJECT.md`** —— 白名单外，未读（前者见 F-4）。
6. **`ReadBook.kt` / 阅读路径入口 / 其它 `Debug.diagnosticsFor` 调用点** —— 白名单外，未读。因此「`ContentProcessor.getContent` 是阶段四唯一产生地」这一前提我只是**沿用** `Debug.kt:457-459` 与裁定书 `:15` 的陈述，未独立核对。
7. **真机 / 浏览器 Web 调试页 / 真实书源 / 真实站点** —— `NOT RUN`，与 `10-execution.md:74-79` 一致。**F-1 的最终判定需要其中至少一项**（或一条 JVM 时序测试）。
8. **多页章（`nextContentUrl`）、`@webjs:`、音视频/文件类书源** —— 未评估其在新 `accepts` 身份口径下的行为。
9. **`lintAppDebug`** —— `NOT RUN`（非发布轮）。
10. **`URL(URL(base), spec)` 在边界输入上的 JDK 实际行为**（base 本身是相对串、含非 ASCII、含空格、`//host/x`、base 路径含逗号）—— 我按 JDK 语义推演，**未实跑** JDK 验证。
11. **工作树 vs HEAD `25b289adb` 的逐文件比对** —— 无 git，无法做；因此「白名单外一行未改」与「既有测试未被修改」两条主张在**证据层面**未被复核。
12. **回退途径前提（存量书 `baseUrl` 为空）** —— `11-implementation-notes.md:14-17` 的核心论据在白名单内**找不到证据**；我按「旧行为保真、无风险」处理，未升级为发现。

---

## 5. 给 controller / acceptor 的最短行动清单

1. **F-1**（阻塞）：二选一 —— ①补一次拒绝后的刷新点 + 一条「收尾之后才发生拒绝」的 JVM 时序测试；或 ②采信「只要有拒绝记录即算满足」并在裁定书/任务书里把 M3 的可见行为改写成**带时序前提**的表述。**需 controller 裁定，非 executor 可自决。**
2. **F-6**：指派一次定点核对（`AnalyzeUrl.kt` 选项切法 + `getAbsoluteURL` 语义 + `BookChapterList.kt:239` + `BookChapter.baseUrl` 入库时机），或列入验收单 `NOT RUN`。
3. **F-3**：在一个**有 shell** 的环境重跑三条冻结命令，并把 `gate-artifacts/d2-gate.log` 纳入可复审范围；对齐任务书「两条」与冻结文件「三条」的表述；复核「冻结命令清单假定 reviewer 有执行能力」这一流程缺口。
4. **F-4**：确认 `.agent-protocol/CURRENT.md` 的改动归属。
5. **F-7**：把裁定书的 M1、M3 两行按 M2 的方式就地更正（按完成条件 #4 字面，此项当前未满足）。
6. **F-9**：决定 `isSameSource` 是在 `accepts` 里复用还是删除。
7. **F-2 / F-5 / F-8**：三处文字层面补齐（报告口径修正、M2 片段归一追认、`provisional` 上限披露）。
8. **F-10**：决定书号逐字比较这一同类形态是否另立条目。
9. **§3.7**：决定是否为「子代理越界读取 `13-reviewer-context.json` 与任务目录」以及「误派生下级代理并两度产出伪复审报告」补协议记录。
