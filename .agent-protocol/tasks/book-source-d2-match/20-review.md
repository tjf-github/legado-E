<!-- agent-protocol-binding
round_id: reviewer-r004
task_digest: 1e7d31cd2360dda71d045c69bcb093582ddeb4a5060b2f515d02bbd148bb30f3
snapshot_digest: 5997d4e4fe603c56d4ff6d58eb052229b1fbf75ec5dd19dec032dc949d6bb09f
--># book-source-d2-match — 第三轮独立复审报告（reviewer）

## 0. 结论

**通过，无阻塞项。** 3 条低严重度发现（均为文档/语义层面，不含实现错误与越界），另有若干证据无法由本侧独立复算（见 §5）。

- 我复核的**实现事实**与执行报告相符：M1/M2/M3 三条修法确实落地在 `DiagnosticIdentity.kt` + `BookSourceDiagnostics.kt` + `Debug.kt` 的透传链上；归一实现是纯 JVM（`DiagnosticIdentity.kt:3` 只 import `java.net.URL`，无 Android 依赖）；阅读/抓取两条生产路径都显式传了 `chapter.baseUrl`。
- 报告 §1.2/§1.4 的两项关键主张（门禁 `BUILD SUCCESSFUL`、`--no-problems-report` 差异已披露）**有原始件支撑**；参考件不含裁定书一事另有独立旁证（见 §3.3）。
- 报告的**文档同步声明**与工作树不符（§3.1），**刷新语义**存在一个可复现的贴错因果场景（§3.2）。两者都不推翻 M1–M3 的落地事实，因此不构成阻塞。
- 保证上限：本任务 controller=executor=dsh、无独立 acceptor，**上限仍是 `provisional`**；本轮通过不等于 `accepted`。报告 §1 已写明，我确认这条声明正确。

---

## 1. 冻结与基线核对

| 项 | 我看到的 | 与报告是否一致 |
| --- | --- | --- |
| 分支 / HEAD | `codex/manga-import` / `25b289adb…`（会话 git 快照） | 一致（任务书「实时基线」、报告抬头） |
| 已跟踪改动 | `ContentProcessor.kt`、`BookSourceDiagnostics.kt`、`Debug.kt`、`BookContent.kt`、`docs/book-source-audit/D-2-补打匹配口径-裁定.md`、`双平台开发环境流程书.md`、`.agent-protocol/CURRENT.md` | 与报告 §6「repo 级对账」**逐条一致**（含 `CURRENT.md` 归协议写入、环境流程书归 controller 追认） |
| 未跟踪 | 任务目录、`DiagnosticIdentity.kt`、`DiagnosticIdentityContractTest.kt` | 一致 |
| 仓库根残留日志 | 会话快照里**没有** `build-dsh-d2-task-create.log`；`gate-artifacts/d2-task-create.log` 存在 | 一致（收尾条件已满足） |
| 白名单外改动 | 从会话快照的已跟踪清单看不出任何白名单外业务文件被改 | 一致 |

> 我**没有**权限自行跑 `git status`（本侧无 shell），以上以 harness 在会话开始提供的 git 快照为独立来源；它与报告自述对账结果一致，这是我能在白名单内取得的最强旁证。

---

## 2. 我实际执行的核对动作与输出

**读过的文件（全部在任务书 `00-task.md` 的 reviewer 白名单内）**：
`00-task.md`、`05-routing.md`、`06-review-commands.json`、`10-execution.md`、`11-implementation-notes.md`、`gate-artifacts/**`（`README.md`、`d2-gate-final.log`、三条冻结命令的 stdout/stderr 共 5 个文件）；业务侧 `DiagnosticIdentity.kt`、`BookSourceDiagnostics.kt`、`Debug.kt`、`BookContent.kt`、`ContentProcessor.kt`、`DiagnosticStage.kt`、`DiagnosticRedactor.kt`、`DiagnosticIdentityContractTest.kt`、`BookSourceDiagnosticsContractTest.kt`、`DiagnosticsToggleParityTest.kt`；`docs/book-source-audit/D-2-补打匹配口径-裁定.md`；白名单追加的定点核对源：`NetworkUtils.kt:150-190`、`data/entities/BookChapter.kt:1-60,155-182`、`AnalyzeUrl.kt:220-250,758-770`、`BookChapterList.kt:222-250`；`双平台开发环境流程书.md`（仅按 §8 范围检索）。

**核对动作与结果**：

1. **M1「与请求路径同源口径」——我自己核过了，不再采信执行侧转述**：
   - `AnalyzeUrl.kt:768` = `Pattern.compile("\\s*,\\s*(?=\\{)")`，`AnalyzeUrl.kt:224-227` 用 `matcher.find()` → `substring(0, start)` → `NetworkUtils.getAbsoluteURL(baseUrl, urlNoOption)`；
   - `NetworkUtils.kt:161-190`：base 先 `substringBefore(",")`，再 `URL(baseURL, relativePath)`；对 `isAbsUrl()`/`isDataUrl()` 原样返回、对 `javascript` 返回 `""`；
   - `BookChapter.kt:171-182`：同一 `paramPattern` 切法 + `NetworkUtils.getAbsoluteURL(baseUrl, urlBefore)`，并把选项拼回；
   - `BookChapterList.kt:236`：`BookChapter(bookUrl = book.bookUrl, baseUrl = redirectUrl)` ⇒ base 是**目录页 redirectUrl**，`BookChapter.kt:46` 的 `baseUrl` 是持久化列（无 `@Ignore`）。
   - 结论：`DiagnosticIdentity.chapterKey` 的「base `substringBefore(',')` + `URL(URL(base), spec)`」与请求路径同源，**该主张成立**。报告 §5.5/§5.6 的两处有意差异（chapter 侧用花括号正则、`javascript:`/`data:` 不塌成空）我逐条复核，确为**身份专用**、绑/读两侧一致，既不误接受也不影响请求。
2. **三条冻结命令——本侧 NOT RUN**（无 shell，`06-review-commands.json` 是唯一允许通道）。改为读原始件并与报告对账：
   - `d2-whitelist-status…stdout.txt` = **6 行**（4 M + 2 ??），与报告 §6 完全一致；
   - `d2-whitelist-diffstat…stdout.txt` = `4 files changed, 163 insertions(+), 20 deletions(-)`，与报告 §1.2「4 文件、+163/−20」一致；
   - `d2-whitelist-diff-check…stdout.txt` **空**、`…stderr.txt` 仅一条 CRLF 警告，与「exit 0、无输出」不矛盾；
   - `d2-gate-final.log` 实读到 `> Task :app:compileAppDebugKotlin`、`> Task :app:testAppDebugUnitTest` 均**实际执行**（非 UP-TO-DATE）与 `BUILD SUCCESSFUL in 1m 27s` / `57 actionable tasks: 8 executed, 5 from cache, 44 up-to-date`，与报告 §1.2 一致。
3. **用例数自洽性**：`394 + 30 = 424`、`45 + 1 = 46`、`422 + 2 = 424`（r003 的 F-2/F-4 各补 1 条）三处算术自洽；但 XML 统计本身**无法复核**（见 §5）。
4. **契约测试覆盖清单逐条对照**（任务书完成条件 2 要求的最小覆盖面）：M1 正例 `relativeChapterResolvesAgainstItsOwnBase`、绝对 vs 绝对 `absoluteChapterIgnoresBaseAndStaysUnchanged`、去选项 `urlOptionsAreStrippedBeforeComparing`、M2 `sourceKeyIgnoresTrailingSlashQueryAndFragment` + `sourceVariantStillMatchesSameSource`、负例 `otherBookOtherChapterAndLocalBookAreStillRejected`（别的书/别的章节/`loc_book`/别的书源四条）、计数 `mismatchCountOnlyCountsRejectedIdentities`——**全部存在**，无缺项。
5. **反例尝试（我自己构造并走读代码）**：
   - 并发丢更新：refresh 的「取→判→写」整体在 `synchronized(snapshots)` 内，且 `record(StageSnapshot)` 复用同一把锁（可重入），阅读侧 `record` 不可能插入判断与写回之间 ⇒ 真实读数不会被覆盖成「未执行」。**F-2 的修复有效**（不是转述，是我按锁序推的）。
   - 误接受：`chapterKey` 对空白章节返回原文，但 `bindTarget` 用 `isNullOrBlank` 拒绝绑定、`accepts` 用 `isNullOrEmpty`+原始串两条途径，构造不出「空白键命中」的路径；`rawChapterUrl` 回退只在逐字相等时生效，与改前行为一致，**未引入新的误接受面**。
   - 异常穿到阅读链：`chapterKey`/`sourceKey`/`refresh…` 全路径 `catch (e: Exception)` 或纯字符串操作，`Debug.diagnosticsFor` 未绑定/未开启时首行返回 ⇒ 诊断不会把异常抛进 `ContentProcessor.getContent`。**未发现**。
   - 关闭态成本：`Debug.diagnosticsFor` 首行 `diagnostics ?: return null`，诊断关闭时阅读路径零额外 URL 构造。**未发现**。
6. **越界检查**：白名单外我未用 Glob/Grep/遍历去命中任何路径（唯一一次 `Grep` 的 `path` 是白名单内的 `双平台开发环境流程书.md`，用于确认 §8 范围；`Glob` 两次分别限定在 diagnostics 主/测试目录）。**未发现越界改动**。
7. **安全**：新增代码无网络、无 DB 写、无文件 IO、无命令拼接、无凭据输出；`Target.toString()` 已遮蔽章节串与书号（`BookSourceDiagnostics.kt:95-98`，守卫测试 `targetToStringNeverLeaksUrls` 存在且我核对过断言能覆盖「密钥在查询串里」的形态）；新增文案只含计数。**未发现**注入/穿越/XSS/凭据外泄/权限放大。
8. **简化**：`isSameSource` 确已从生产文件删除（`DiagnosticIdentity.kt` 只剩 `chapterKey`/`sourceKey`/`stripOptions`），测试改为直接比 `sourceKey`；新增的 `rawChapterUrl`、`claimMismatchRefreshReport` 都有明确生产调用点与守卫测试，**未发现**为改一处而引入的垫片或死代码。

---

## 3. 发现

### F-1（低，非阻塞）M3 文案在裁定书与实施说明里仍是**旧版**，报告却声明「裁定书 M3 行同步」

- **位置**：`docs/book-source-audit/D-2-补打匹配口径-裁定.md:40`；`11-implementation-notes.md:19`；实现事实见 `BookSourceDiagnostics.kt:180`；声明见 `10-execution.md:55`（§2b F-3 行）。
- **事实**：代码实际文案为
  `书源调试链路止于 BookContent；本会话存活期有 $mismatches 次正文处理被身份隔离排除，未进入本次调试目标`（已按 r003 F-3 去掉「其它」）。
  但裁定书第 40 行仍写 `……N 次其它书源/书籍/章节的正文处理被身份隔离排除……`，`11-implementation-notes.md` §1.6 也仍是同一句旧文案（含「其它」）。两份文件都**没有**本轮 r003 F-3 的更正痕迹。
- **失败场景**：这是**文档与实现不一致**，不是行为缺陷。具体危害：①后续任务/P2 若照裁定书原文写断言（例如按字面断言含「其它书源/书籍/章节」），会得到一个必然失败的测试并误判为回归；②真机轮按裁定书逐字核对第 4 行时会把正确输出当错误。任务书完成条件 4「`11-implementation-notes.md` 与裁定书同步」在字面上**未达成**。
- **建议**：把两处替换为代码实际字符串；或改写成引用式表述（「文案以 `readingStageMissingReason()` 为准，见实现」），避免下次再漂移。注意现有测试只断言 `"N 次"` 与「不含 URL」，**钉不住文案漂移**（`DiagnosticIdentityContractTest.kt:265-269`），所以这条不会被门禁拦住。

### F-2（低，非阻塞）阶段四原因行的刷新会被**与「为何未执行」无关**的会话级不匹配改写，重新引入一类「贴错因果」

- **位置**：`BookSourceDiagnostics.kt:198-208`（`refreshReadingStageMissingReasonIfMissing`）、`Debug.kt:143-148`（`onIdentityMismatch`）；相关的收尾链 `Debug.kt:462-469` + `BookSourceDiagnostics.kt:339-355`（`recordFailure`）。
- **失败场景（可复现的时序）**：
  1. 会话开始，调试链路在**抓取/提取层就失败**（断网/HTTP 错误）：`recordFailure` 把「网络错误」归到阶段一，`finishContentDiagnostics` 把阶段四写成「未执行（书源调试链路止于 BookContent，未进入阅读处理）」——**这是正确因果**。
  2. 会话仍存活（按报告 §5.8，`++<tocUrl>` 流属于这种），用户去读**别的书**的某一章 → `accepts` 判不匹配 → 计数 0→1 → refresh 把阶段四的 note 覆写成「本会话存活期有 1 次正文处理被身份隔离排除，未进入本次调试目标」。
  3. 用户看到的第 4 行于是把「链路在阶段一就断了」说成了「身份被隔离」，真正的失败原因只剩在第 1 行。
  - 句中每个字都**为真**（会话确实发生过 1 次隔离），但它占据了「未执行原因」的位置，会被读成因果——这正是 D-2 要消除的「贴错标签」的另一种形态，只是这次错在把**别处**的事件当成了本阶段的原因。
- **边界（如实说明）**：Web 调试页流里 `cancelDebug(true)` 会清空 collector，此场景不可达；它只在「链路失败 + 会话存活 + 用户读非目标章」三个条件同时成立时可达，报告 §5.8 只披露了「刷新在 Web 流不可达」，**没有披露这条反向可达路径**。
- **建议**（任选其一，都很小）：把文案改成非因果式（如「另：本会话存活期有 N 次正文处理被身份隔离排除」），或让 refresh 仅在「阶段一/二/三没有任何错误类别」时才覆写；并补一条契约测试：`recordFailure` 之后触发一次不匹配，断言阶段四 note 不把失败原因改写成隔离说明。

### F-3（低，非阻塞）冻结证据集**在构造上**覆盖不到本任务改过的裁定书；报告对该现象的归因是二者择一，未定论

- **位置**：`06-review-commands.json:8,15,22`（三条命令都带显式 pathspec，其中 `docs/book-source-audit/D-2-补打匹配口径-裁定.md` 是非 ASCII 路径）；原始件 `gate-artifacts/d2-whitelist-status-20260926T131411281Z-bdf308c1.stdout.txt`（6 行，不含裁定书）；`10-execution.md:113`。
- **我独立取得的旁证**：会话 git 快照显示该裁定书**当前确实是已跟踪修改文件**（` M "docs/book-source-audit/D-2-\350\241\245…"`），而冻结的 `status`/`diffstat` 原始件都不含它 ⇒ **该原始件此刻已经是不完整的**，不只是历史快照。（报告 §6 自述也承认这点，但把原因写成「疑为生成早于该改动或非 ASCII 路径未匹配」两条猜测。）
- **失败场景**：若后续有人拿这两份原始件当「越界检查通过」的证据，会因为定义域缺口而**高估覆盖**——而这正是第二轮复审 F-1 指出的循环论证。裁定书属本任务正式白名单文件，缺口正好落在它身上。
- **建议**：重新冻结（或在 `gate-artifacts/README.md` 注明「本件不覆盖非 ASCII 路径」），pathspec 改用 `-- 'docs/book-source-audit/*.md'` 或 `:(literal)` 形式；否则「无白名单外改动」只能继续靠 repo 级对账自述。

---

## 4. 逐维度小结（对应复审要求）

| 维度 | 结论 |
| --- | --- |
| 证据缺口 | 见 F-1（声明与文件不符）、F-3（冻结件定义域缺口）；另有若干项本侧无法复算，见 §5。**未发现**「该跑而没跑却写成结论」的项：报告 §7 的 NOT RUN 清单（真机、lint、Web 端、真实书源、多页章、`sortUrls`、存量 `baseUrl` 填充率）与我的观察一致。 |
| 反例 | 找到 1 条具体的贴错因果时序（F-2）；另构造了并发丢更新、空白键命中、异常穿透阅读链、关闭态成本四类反例，**均未击穿**（§2.5）。 |
| 越界 | **未发现**。白名单外文件、敏感路径、禁止操作（联网/DB/凭据/真实书源）均无接触痕迹；仓库根临时日志已按任务书归位。 |
| 安全 | **未发现**新增风险面：无网络/DB/文件 IO，无命令拼接，摘要只含计数与归一键，`Target.toString` 已遮蔽。 |
| 简化 | **未发现**无谓抽象、兼容垫片或死代码；`isSameSource` 已删，新增 API 均有生产调用点与守卫测试。 |

---

## 5. 我没检查到的范围（NOT RUN / 能力边界）

1. **三条冻结命令未由我执行**：本侧无 shell，`06-review-commands.json` 虽冻结了参数数组，但无法调用 `git`。我只读了它们的原始件，未复算退出码。
2. **`suites=46 / tests=424 / failures=0 / skipped=0` 未能独立验证**：计数来源是 `app/build/test-results/**`（不在 reviewer 白名单），且无 shell。我只验证了内部算术自洽（§2.3）与门禁日志里 `testAppDebugUnitTest` 确实执行。
3. **所有 sha256 未复算**（`d2-gate-final.log` 的 `a6d29216…`、`d2-task-create.log` 的 `a2f19b6a…`、两轮复审回执的 `fb5b715f…`/`94718490…`）：无 shell。
4. **未读、因而未核**：`30-acceptance.md`、`rounds/**`（含 r001/r003 报告原文）、`13-reviewer-context.json`、`.agent-protocol/CURRENT.md`、UI/Web 调试页代码、`ReadBook`、`help/ai/**`、真实书源与语料——均为白名单外，我未用任何间接方式探测。因此 F-4/F-8/F-9/§3.7/§6.1 等前两轮处置与协议账面事件，我只能确认「报告这样写了」，不能确认「事实如此」。
5. **真机/联网/端到端**：与报告 §7 一致，全部 NOT RUN；我**不**因 JVM 测试或门禁日志宣称真机已验证。
6. **可达性主张未验证**：报告 §5.8 称 `++<tocUrl>` 流会话存活、刷新分支可达——这依赖 UI 侧的 `cancelDebug` 调用点（不在白名单，未读），我无法独立确认；F-2 与 M1 补打的可见效果都挂在这一点上。
7. **§1.4「编译器已确认所有生产调用点都显式传 base」是**有界主张**：去掉默认值只能证明没有调用点**省略**参数，不能排除白名单外某处**显式传 `null``**。我在白名单内看到的 4 个调用点（`Debug.kt:114`、`Debug.kt:449`、`ContentProcessor.kt:111`、`BookContent.kt:52`）都传了真实 base，白名单外是否存在显式 `null` 无法核对——建议把它从「编译器已确认所有调用点都显式传 base」收紧为「编译期已排除省略实参」。
8. **复审冻结窗口（前后快照一致）我无法自证**：本侧无 shell，无法取前后哈希；若 runner 判定快照不一致，本报告按任务书规则作废。

---

## 6. 复审过程自查（白名单纪律）

- 只读：未写任何文件，未执行任何改变工作树的命令（本侧无 shell）。
- 未读白名单外文件内容；两次 `Glob` 分别限定在 `app/src/main/.../diagnostics/` 与 `app/src/test/.../diagnostics/`（均在白名单）；唯一一次 `Grep` 的 `path` 是白名单内的 `双平台开发环境流程书.md`。
- 未修改 `10-execution.md` 与 `30-acceptance.md`（事实上未写任何文件）。
