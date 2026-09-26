<!-- agent-protocol-binding
round_id: reviewer-r002
task_digest: 695f0681c170817674ab0e4e4e368ff0743ed238a9848e85008f4ebfae42256b
snapshot_digest: 0512414ce253576a06e9666d30fdb1d88fabad5f779d79b208c6e66a3ad6662f
--># book-source-p1-rework-r3 — 第二轮独立复审报告

- 任务：`.agent-protocol/tasks/book-source-p1-rework-r3`，round_id=`reviewer-r002`（`12-reviewer-checkpoint.json`），mode=`dsh-claude`，route_revision=8，assurance=standard，分支 `codex/manga-import`，HEAD `b8ba92e194468f0d7e6103c1175ba9f2a8233d95`（按任务书/执行报告声明；我无法独立核实 HEAD 与提交状态，见第六节）。
- 复审方式：**只读静态复审**。`06-review-commands.json` 的 `commands` 为空数组 → 按任务书 §复审冻结与轮次，reviewer 侧一切构建 / 测试 / 探针 / `git` / 真机检查一律 **NOT RUN**。本会话 `Read`、`Grep`、`Glob` 可用（与 r001 不同，`rg` 未被拒），但仍**没有任何执行通道**，因此本报告不含任何运行侧证据。
- 冻结窗口：任务书 `00-task.md`、`10-execution.md`、`06-review-commands.json` 在我启动至完成期间未被写入（我全程只读，未尝试任何写操作）。

---

## 一、结论

**通过——在源码层面未发现阻塞项。** 本任务 5 项交付与 reviewer-r001 的 F-01…F-06 处置，我逐项在工作树上核对为「已落地且与执行报告一致」；此外我对报告引用的测试统计做了独立复算，结果与源码逐项吻合（第四节 1）。分三层读：

1. **5 项交付（P1 验收阻塞 1—4 + F-03）：源码层面全部落地**，且每一项都有对应的守卫/契约测试（第三节表 A）。我未发现伪造快照、正文语义改变、白名单外写入、凭据外泄或为改一处引入多余抽象的迹象。
2. **r001 的 F-01/F-02/F-03/F-06 已按建议修掉**，F-04 按「改表述、不伪造封套」处置，F-05 登记为已知限制（第三节表 B）。我在 F-01 的新实现里**没有再找到**能被链路级失败污染阶段四读数的路径。
3. **但本报告不等于 P1 通过**：门禁、探针、测试**一次都没有被我独立复跑**（授权为空 → NOT RUN），真机/UI/Web 端全部 NOT RUN。执行报告的运行侧数字在源码侧自洽（第四节 1），但「自洽」不是「已执行」；最终门禁与真机仍须 acceptor 在冻结工作树上复跑。

发现的问题全部为**低/信息级**，不构成返工项：见第五节（F-02-1 测试未覆盖的告警分支、F-02-2 报告行号、F-02-3 验证步骤 2 的可观测性、F-02-4 RSS 同形未修、F-02-5 残余边界已不可达、F-02-6 白名单外写入不可证实、F-02-7 执行侧无 runner 封套）。

---

## 二、复审方式、工具可用性与读取范围

**可执行检查 = 空数组 ⇒ NOT RUN。** `06-review-commands.json`：`{"version":1,"network_policy":"deny-declared-not-enforced","commands":[]}`。因此本报告不含 `:app:compileAppDebugKotlin`、`:app:testAppDebugUnitTest`、探针、真机、`role-verify` 的任何独立结果。

**读取范围（全部落在任务书白名单内）**

- 本任务目录：`00-task.md`、`10-execution.md`、`10-execution.summary.json`、`11-implementation-notes.md`、`06-review-commands.json`、`32-probe-source.java`、`32-probe.log`、`12-reviewer-checkpoint.json`、`20-review.summary.json`。
- P1 任务（只读）：`book-source-p1-diagnostics/30-acceptance.md`（用于核对 4 条阻塞的原始措辞是否被完整承接）。
- 生产源码：`help/book/diagnostics/{BookSourceDiagnostics,DiagnosticRedactor,DiagnosticClassifier,DiagnosticStage}.kt`、`model/Debug.kt`、`help/book/ContentProcessor.kt`、`model/webBook/BookContent.kt`、`model/analyzeRule/AnalyzeByXPath.kt`、`utils/SerialTaskQueue.kt`、`web/socket/BookSourceDebugWebSocket.kt`、`ui/book/source/debug/{BookSourceDebugModel,BookSourceDebugActivity}.kt`、`web/socket/RssSourceDebugWebSocket.kt`。
- 测试：`help/book/diagnostics/BookSourceDiagnosticsContractTest.kt`、`utils/SerialTaskQueueTest.kt`。
- 构建脚本：`app/build.gradle`（通读）。

**未读**：`15-reviewer-raw.md`（禁读）；`20-review.md`（我的产物目标，非读入对象）；`app/build/**`（门禁日志与测试 XML，白名单外）；`book-source-p1-diagnostics` 的 `20-review.md` 与任务书（本轮改动的原始来源已在任务书 §目标里逐条复述，我改读其 `30-acceptance.md` 做完整性交叉核对）；`ExportBookService`/`ChangeBookSourceViewModel`（F-05 因此依赖执行方与 P1 已披露的事实，与 r001 相同）；真实书源/语料/`bookshelf.json`/签名材料/凭据一律未读。

**白名单边界的自我披露（两点）**

1. **`.agent-protocol/PROJECT.md`：我读了。** 我把它当成任务书白名单里的「`.agent-protocol` 入口」（它确实是协议的项目级入口配置）。该文件只含项目名、构建/测试命令、发布门禁、`sensitive_paths` 模式、`prohibited_operations`、`allowed_external_providers` 与本地代理预设，**不含凭据、真实用户内容或任何挑战串**；我的结论不依赖它（任务书 §完成条件第 3 条已逐字给出同一条 gradle 命令）。若 controller 认为它不在白名单内，请按「一个文件的范围外读取」记录，我的技术结论不变。**顺带发现一处口径不一致（信息级）**：`PROJECT.md` 的 `allowed_external_providers` 仍写 `mode=triad`，而本任务书/路由为 `mode=dsh-claude`；按任务书「准确路由以运行回执为准，不猜测」，我不据此推断任何东西，只作登记。
2. **第三次 `Grep` 的 path 用了 `app/src`（含 `androidTest`）**：结果只命中 `app/src/main` 与 `app/src/test`，`androidTest` 下未读取任何文件。前两次 Grep 分别限定 `app/src/main`、`app/src/test`。

---

## 三、逐项核对（工作树为准）

### 表 A：本任务 5 项交付

| 交付项 | 核对结果 | 我读到的证据（工作树行号） |
| --- | --- | --- |
| 1 默认摘要不得泄漏任意正文 | **已落地**。`describeError` 只返回 `"异常类型 " + error.javaClass.simpleName + "，消息默认不输出"`，方法体不引用 `error.message`；阶段内异常（`record` 的 `error != null` 分支）与链路级兜底（`recordFailure`）共用它。`sanitizeMessage` 在生产代码中的唯一调用点是 `markNotExecuted` 的 reason（我 grep 枚举了全部 `markNotExecuted*` 调用点，均为固定文案，见下）。 | `BookSourceDiagnostics.kt:170-171`、`:144-149`、`:224-240`；`DiagnosticClassifier.classify` 仍读 `error.message` 但**只返回枚举**（`DiagnosticClassifier.kt:101`），文本不进入快照 |
| 2 阶段四真实性与展示时机 | **已落地**。①`markNotExecutedIfMissing` 有快照即返回，`finishContentDiagnostics` 改用它；②判定下沉为 `shouldReportReadingStage()`（诊断开启 + 阶段四 `executed==true` + 会话未补打过，CAS 当场占用），`Debug.onReadingStageRecorded()` 只保留 `callback == null` 这一 Android 相关判断；③`ContentProcessor` 先 `record(READ_PROCESS, …)` 再触发补打；④会话已结束（`diagnostics`/`callback` 被清空）→ 无输出、不伪造。补打时 `markMissingStagesNotExecuted` 不会覆盖阶段四（已存在快照即跳过），1/2/3 的「未执行」占位会被后续真实 `record` 的 upsert 替换。 | `BookSourceDiagnostics.kt:115-119`、`:179-183`、`:205-212`；`Debug.kt:453-461`、`:472-478`；`ContentProcessor.kt:234-245` |
| 3 Web 端投递保序 | **已落地（提交顺序层面）**。`printLog` 不再 `runOnIO` 一条一个任务，改为 `logQueue.submit`，终止信号（`state==-1||1000`）的 `cancelDebug(true)+close()` 被移入**队列任务内部**，因此必然排在四行摘要之后。`SerialTaskQueue` = 无界 `Channel` + 单消费者（`trySend` 的 FIFO 是库语义），单任务失败不中断后续（`CancellationException` 重抛，其余 `Throwable` 吞掉）。四行摘要本身由同一线程顺序提交（`Debug.printBookSourceDiagnostics` 的 `forEach`），终止任务紧随其后，相对顺序有保证。**边界**：这只证明「投递/执行顺序」；NanoWSD 的 `send` 是否同步写 socket 仍是库层问题，我无法从仓库内核实（第四节 5）。 | `BookSourceDebugWebSocket.kt:26-32`、`:93-109`；`SerialTaskQueue.kt:20-45`；`Debug.kt:128-131`、`:453-461` |
| 4 M-3 `AnalyzeByXPath.kt` 裁定 | **已闭合（任务书给出授权）**。`00-task.md:51` 显式纳入白名单并写明「保留首轮等价替换，不再新增改动」；工作树里该文件只有那一处改动，我独立复核语义：`getResult` 返回 `List<JXNode>?`，对 `List` 而言 `it.joinToString("\n")` 与 `TextUtils.join("\n", it)` 都是「元素 `toString()` + 分隔符拼接、空集合得空串」。 | `00-task.md:51`；`AnalyzeByXPath.kt:132-140`（`getString` 的 `rules.size == 1` 分支） |
| 5 F-03 URL 主机名保留 | **已落地**。先取 `authorityAndPath.substringBefore('/')`，只在 authority 内取最后一个 `@`，再把 `/path` 原样接回；authority 切不出时（畸形 URL）保守回退到旧行为。我按 `index/schemeAt/start/end/cut/authority/at/visible` 逐步推演了探针的三例与两个额外反例（第四节 3），与源码一致。 | `DiagnosticRedactor.kt:100-138`（关键 `:119-133`） |

### 表 B：r001 findings 处置

| Finding | 我的核对 |
| --- | --- |
| **F-01 [中]** 链路失败贴到阶段四 | **已修且修法正确**。`recordFailure` 候选集合为 `it != READ_PROCESS && snapshotOf(it)?.executed == true`，无候选时走「归到阶段一」分支。我复算了 r001 的失败场景（仅阶段四 `executed=true` 时 `recordFailure`）：阶段四快照保持 `errorClass=正常`、`note=null`，错误落到阶段一（`DiagnosticErrorClass.NETWORK`），不再出现「4.阅读处理后 网络错误 len=…」的假读数。 | `BookSourceDiagnostics.kt:226-232`；守卫测试 `BookSourceDiagnosticsContractTest.kt:615-630`；探针 check 7 |
| **F-02 [中]** 展示时机缺守卫 | **已按建议 (a) 修**。判定条件不再依赖 Android，三态（无快照 / 只有「未执行」标注 / 首次真实读数 + 去重）由契约测试与探针双双钉住；`Debug` 侧只剩「会话是否还活着」。**残余**见第五届 F-02-1（`callback == null` 分支无测试）。 |
| **F-03 [低]** 畸形 URL 回退 | **已按建议修**（空 authority 回退到整段最后一个 `@`）；「userinfo 含 `/`」写进 KDoc 作残余边界。见第五节 F-02-5：该残余在本轮之后**已无可达的调用方**。 |
| **F-04 [中]** 回执表述 | **已按第二个选项处置**：`10-execution.summary.json` 的 `source` 改为 `direct-controller-dispatch`，`source_note` 明写「没有 runner 封套」「不接受把本文件读作执行来源已被协议绑定」，报告 §3 不再写「已处置」。事实陈述与 r001 的核实一致。**结构性问题本身仍在**（第五节 F-02-7）。 |
| **F-05 [低]** 补打权可被导出统计消费 | **已按建议登记**（`10-execution.md:162`、`:176` 前置条件）。我未读 `ExportBookService`，此条依赖执行方与 P1 复审已披露的事实，与 r001 相同。 |
| **F-06 [低]** 两小项 | **均已处置**：`SerialTaskQueue.close()` 已删除（我 grep 全仓 `app/src` 无 `SerialTaskQueue.close`/`claimReadingStageReport` 残留引用，说明不是「删了还会编译失败」的假删）；`32-probe.log` 已可按 UTF-8 正常读取。 |

### 表 C：任务书完成条件

| 条件 | 我的核对 |
| --- | --- |
| 1 五项落地 + 各自守卫/契约测试 | ✅ 源码层面成立（表 A；测试逐条见第四节 1） |
| 2 关闭诊断零额外处理 / 不改变返回值 | ✅ `Debug.diagnosticsFor` 返回 null 时：`stageStart=0`、`elapsedSince` 不读时钟、`diagnostics?.record(..., text = contents.joinToString("\n"), …)` 的实参因安全调用短路**不被求值**、`if (diagnostics != null) Debug.onReadingStageRecorded()` 不进入。本轮新增代码全部是语句（无一处出现在 `return` 表达式中），`mContent`/`contentStr`/`contents`/`bookContent` 的取值路径未变（`ContentProcessor.kt:104-247`、`BookContent.kt:188-259`）。 |
| 3 门禁 + 直接解析 XML 的五个计数 | 报告 §6 给出了（exit 0 / suites=45 / tests=394 / 0/0/0）。**我未复跑**，但复算出的源码计数与报告逐项吻合（第四节 1） |
| 4 改动清单 / 白名单声明 / 脏基线 / 命令证据（含失败）/ 限制与 NOT RUN / 真机步骤 | ✅ 六个要素齐备；两次失败尝试（未提权 exit 1、陈旧 Kotlin 增量缓存 `Unresolved reference`）都写了命令与处置，没有静默改写 |
| 5 独立复审 + 独立验收分别产出证据 | 复审 = 本报告（runner 封套：`12-reviewer-checkpoint.json` 存在，`role reviewer` 路径）。执行侧封套见第五节 F-02-7 |
| 6 真机未验如实写 NOT RUN | ✅ 报告 §7/§8 明写 1—7 全 NOT RUN，且未宣称 P1 完成 |

---

## 四、我实际执行过的核对动作与输出

1. **独立复算测试统计（最有价值的一条）**：`Grep`（`^\s*@Test\b`，path=`app/src/test`）→ **"Found 394 total occurrences across 45 files."** 逐类计数：`BookSourceDiagnosticsContractTest=51`、`SerialTaskQueueTest=3`、`DiagnosticsToggleParityTest=7`、`AnalyzeRuleContractTest=27`、`HtmlFormatterContractTest=16`、`ReplaceRuleContractTest=17`、`ContentProcessorContractTest=14`。与报告 §6 的 `suites=45 tests=394 failures=0 errors=0 skipped=0` 与 7 条 per-suite 数字**逐项相等**。这不能证明「跑过且全绿」，但把「数字是编的」这一假设压到很低：45 个体量、394 个用例、7 个抽样值同时猜中的概率可忽略。
2. **口径对账**：r001 记的是 45/393（该轮 49+4）；本轮 51+3 ⇒ 净 +1 ⇒ 394。与报告 §4.2「40 → 51（3+5+3）」及 `10-execution.summary.json` 的 393→394 一致，无自相矛盾。报告 §5 关于「本轮新增 vs P1 遗留」的表述经 r001 指出后已改写，现在前后自洽。
3. **独立推演 `stripUrlSecrets`**：手算三例——`https://api.example.com/@user/profile`（authority=`api.example.com`，`at=-1`，输出主机名保留）、`https://user:pw@api.example.com/@user/profile`（`at` 落在 authority 内，userinfo 丢弃、`/@user/profile` 保留）、`https:///user:pw@api.example.com/x`（`authority=""` → 回退到整段最后一个 `@`，凭据丢弃）。三条与探针 check 9—11 及 `BookSourceDiagnosticsContractTest.kt:224-247` 完全一致。另补算两例：`https://host`（无路径）与 `https://:8080/path`（空 host）——输出均不产生凭据残留。
4. **独立推演 `recordFailure`**：从 `DiagnosticStage.values()` 顺序与快照集合出发复算 r001 的 F-01 场景，结论见表 B；并核对探针 check 7 的转写（`StageSnapshot(... executed=true, note=null) | StageSnapshot(stage=1..., errorClass=网络错误 ...)`）与源码行为一致。
5. **探针日志与源码互证**：`32-probe-source.java` 的 11 条 check 我逐条按生产源码推算了期望输出，与 `32-probe.log` 的 11 行逐字一致；其中可数值化的部分对得上（`SYNTHETIC_BODY`=14、`SYNTHETIC_READING`=17、`markNotExecuted` 快照 `summary` 为空、`len=0` 兜底），说明日志不是脱离源码生成的。**探针边界**：它不覆盖 `Debug`/`ContentProcessor`（Android 桩），因此「真的多打印一段摘要」仍只有真机能验。
6. **调用点枚举（本轮新增能力）**：`Grep` 枚举 `markNotExecuted|markMissingStagesNotExecuted|markNotExecutedIfMissing|recordFailure|shouldReportReadingStage|onReadingStageRecorded` 于 `app/src/main` —— 全部调用点只有 `BookContent.kt:219,342`、`Debug.kt:438,455,459,475,476`、`BookSourceDiagnostics.kt`（定义）、`ContentProcessor.kt:244`，**所有 reason 都是固定文案**。这同时闭合了「阻塞 1 的修法是否还有别的入口把无界文本送进摘要」这一问：没有。
7. **`Grep` 枚举 `Debug.Callback|sanitizeMessage|SerialTaskQueue|diagnosticsFor`**：`Debug.Callback` 实现共 4 个（书源/订阅源 Web 端 + 两个 ViewModel），`SerialTaskQueue` 的生产使用点只有 `BookSourceDebugWebSocket` 一处，`Debug.diagnosticsFor` 的调用点只有 `BookContent.kt:52`、`ContentProcessor.kt:110`（即四阶段数据的全部入口）。
8. **通读 `app/build.gradle`**：无 `testOptions`/`unitTests`/`returnDefaultValues`，`testImplementation` 只有 `libs.junit`，未新增 Robolectric/Mockito ⇒ P1 的 M-2 全局弱化确实已回退（与 r001 的独立结论一致，M-2 未复发）。
9. **`Read` `book-source-p1-diagnostics/30-acceptance.md`（P1 第二轮验收原文）** 做完整性交叉核对：4 条阻塞与 F-03 的要求与任务书 §目标 1—5 **逐条对应**，没有「任务书复述时漏掉的子要求」。其中阻塞 2 的第二句「收尾后阅读又不会再次输出摘要」被任务书重述为「会话存活期间…再输出一次；会话结束之后…无输出、不伪造」，本轮实现覆盖了这个新口径（并在 UI 调试路径可达成；Web 端因终止信号会结束会话而结构上不适用，见第五节 F-02-3）。
10. 未执行任何写操作，未运行任何命令，未改 `10-execution.md`/`30-acceptance.md`，未读 `15-reviewer-raw.md`，未碰真实书源/语料/`bookshelf.json`/凭据/设备。

---

## 五、发现清单（全部非阻塞，按严重程度排序）

### F-02-1 [低 · 证据缺口] 「会话已结束 → 不输出且不消费补打权」分支没有守卫测试

- 位置：`app/src/main/java/io/legado/app/model/Debug.kt:472-478`（`if (callback == null) return` 在 `shouldReportReadingStage()` **之前**）。
- 为什么是缺口：任务书 §完成条件第 1 条要求「阶段四覆盖与**展示时机**」有守卫测试；现有测试覆盖了 collector 侧三态（`BookSourceDiagnosticsContractTest.kt:594-604`）与关闭态（`:607-612`），但 `Debug` 侧唯一的分支——「会话已死 → 不输出、不占用补打权」——只有静态核对。这条正是任务书 §目标 2 第三句「阅读发生在调试会话结束之后…无输出、不伪造」的告警路径。
- 具体可测法（不需要 Android 桩，因为该分支在打印前返回）：`Debug.startBookSourceDebugSession()` + `bindDiagnosticsTarget(...)` + 用 `Debug` 可访问的途径写入阶段四真实快照（或先 `Debug.callback = null`），调用 `Debug.onReadingStageRecorded()`，断言 `collector.shouldReportReadingStage()` 仍为 `true`（未被消费）。需在 `@After` 里 `Debug.cancelDebug(true)` 清理单例状态。
- 风险：低（当前顺序正确，这是回归守卫）。若顺序被后人调换成「先认领再判断 callback」，会话外的阅读会静默吃掉补打权，届时没有测试会红。

### F-02-2 [低 · 报告准确性] §2 第 1 行的证据行号指向 KDoc 而非实现

- `10-execution.md:31` 写「`BookSourceDiagnostics.kt:164-165`」，实际 `describeError` 在 `:170-171`；`:164-165` 落在 KDoc 内。同类引用（§2 第 2—5 行）我逐条核过都在正确位置，只有这一处偏差。属文档级，不影响结论；但 r001 已就「以位置引用当证据」提过一次，建议顺手修正。

### F-02-3 [低 · 验证步骤的可观测性] §8 步骤 2 的预期在两种会话载体上都难以人工达成

- 位置：`BookSourceDebugModel.kt:45-53,55-58`（`printLog` 不处理 state=1000，但 `onCleared()` → `Debug.cancelDebug(true)`）、`BookSourceDebugActivity.kt:35,47-61`（ViewModel 为 Activity 作用域，页面无通往阅读页的入口）、`BookSourceDebugWebSocket.kt:98-108`（终止任务在发完摘要后 `cancelDebug(true)`）。
- 具体问题：补打要求「会话存活期间」阅读同一章。Web 端在收尾的终止任务里就 `cancelDebug(true)`（`diagnostics=null`）；Android 调试页要打开阅读页通常意味着 finish 该 Activity → `onCleared()` → 同样清空。也就是说，§8 步骤 2 按字面执行时，「会话是否还活着」不由步骤控制，实测很可能得到「无输出」并被误读为功能失效。
- 可达但需要设计时的窗口：(a) Web 端在一条正文调试链**仍在飞行**时（例如指向域名不存在/慢源，抓取超时窗口内）用同一本书同一章走缓存阅读；(b) Android 端用不经 finish 调试页的入口（如通知/深链）进入阅读页。建议把步骤 2 改写成这两种可复现窗口之一，或明确写「无确定性人工窗口时记为 NOT RUN」。
- 风险：低（代码行为符合任务书口径，问题在证据计划），但会直接影响 acceptor 的真机结论。

### F-02-4 [低 · 同类缺陷未修（已披露）] `RssSourceDebugWebSocket.printLog` 仍是旧形态

- 位置：`app/src/main/java/io/legado/app/web/socket/RssSourceDebugWebSocket.kt:80-95`（`runOnIO { send(msg); if (state==-1||1000) cancelDebug+close }`）。
- 与阻塞 3 同形：每条日志一个并发 IO 任务，末尾的 `state=1000` 可能先关 socket。P1 阻塞项点名的是书源调试 Web 端，报告 §7.8 已如实披露未纳入，**本轮无需处理**；建议登记为后续项（若 RSS 调试日志也出现末尾丢行，根因相同，`SerialTaskQueue` 可直接复用）。

### F-02-5 [信息] F-03 登记为「残余边界」的那条路径，本轮之后已无可达调用方

- `DiagnosticRedactor.sanitizeMessage` 在生产代码里的唯一调用点是 `markNotExecuted` 的 reason（`BookSourceDiagnostics.kt:195`），而调用方（`BookContent.kt:221,344`、`Debug.kt:457,476`）传的都是固定文案（第四节 6 的枚举）。也就是说「userinfo 含 `/` 的畸形 URL 不再被遮蔽」这一残余边界不再承载无界输入，把它写成风险偏保守；同时也意味着 `stripUrlSecrets` 目前只为「未来的动态调用方」存在。不影响本轮结论，供后续决定是否保留该工具时参考。

### F-02-6 [低 · 不可证实] 「白名单外写入为零」在本轮无法独立验证

- 命令数组为空 + 无 shell ⇒ 我无法做任何 diff/`git status` 核对。我读到的 11 个生产/测试文件内容与报告一致，但「只有这些文件被改过」不成立为结论。
- 具体风险点：本会话的 git 状态快照显示工作树里还躺着**本任务目录之外**的其它任务证据改动（`.agent-protocol/tasks/ai-stage-e-anchor-controlled/05-routing.md`、`.agent-protocol/tasks/dual-release-privacy/**`）。我**没有**读这些文件，也无法判定它们的改动时间是否落在本轮；因此 `10-execution.md:82` 的「白名单外的写入为零」以及 `10-execution.summary.json` 的 `untouched_baseline` 属于**未证实**（不作为违规判定）。请 acceptor 用 `git diff`/时间戳闭合。

### F-02-7 [低 · 流程，非代码] 执行侧 runner 封套仍不存在（已如实披露）

- `10-execution.summary.json:6-7`：`source=direct-controller-dispatch`，`source_note` 明写「没有 runner 封套」。事实与 r001 的核实一致，处置方式（改表述、不伪造）我认可。
- 但任务书 `00-task.md:68` 要求的「独立复审（claude，**runner 封套**）与验收（dsh）分别产出证据」在执行侧仍无封套证据；P1 第二轮验收正是因同类事实记了整体 FAIL（`book-source-p1-diagnostics/30-acceptance.md:59`）。我没有执行 `agent-protocol role-verify`（可执行检查为空 → NOT RUN），**无法判定**它本轮的期望轮次。请 controller 裁定：要么落盘该项回执，要么在验收侧按 P1 先例明示这是结构缺口而非代码失败。

### 未发现（明确写出，避免读作「没查」）

- **安全面**：改动未新增字符串→SQL/路径/shell/命令的拼接，无路径穿越、无命令注入、无 XSS 面变化。`describeError` 的输出只含 JVM 简单类名（我未找到能把正文塞进类名的可达路径，但**未穷尽**：类名由运行时决定，我只核对了本项目可达的异常类型）。诊断数据全内存、不落盘、不联网；`SerialTaskQueue` 只承载闭包，不含动态求值。
- **凭据外泄**：三条脱敏守卫测试 + 我手算的正/负例均未发现凭据进入摘要的路径；`32-probe.log` 只含合成常量。
- **简化/死代码**：本轮删除的 `close()` 属正确的去死代码；新增的 `SerialTaskQueue` 是唯一被生产使用的公共 API（除测试外无其它使用点）；未发现为改一处而引入的兼容垫片。**一处已知无生产调用点的成员**：`DiagnosticRedactor.skeleton` 仍存在，但其 KDoc 已如实标注且 P1 验收 L-3 已判「非阻塞、不必反复复审」，我不作为本轮发现。
- **返回值/正文语义**：四阶段新增代码不改变任何 `return` 值（表 C 第 2 行）。
- **两处观察，我判定不构成发现**（登记以免下轮重复发现）：①同一会话最多两份摘要（报告 §7.4 已登记为观感项）；②`SerialTaskQueue` 无关闭 API，scope 取消后若仍有 `submit` 会把任务留在无界通道里——生产上不可达（会话结束时 `Debug.cancelDebug(true)` 已把 `callback`/`diagnostics` 清空，`printLog` 再无入口），且无额外线程持有，故不报为缺陷。

---

## 六、我没检查到的范围（请勿读作已覆盖）

1. **一切运行侧证据 NOT RUN**：`:app:compileAppDebugKotlin`、`:app:testAppDebugUnitTest`、`assembleAppDebug`、合成探针的实跑、`:app:lintAppDebug`、任何 `git` 命令、`agent-protocol role-verify`。因此「exit 0 / BUILD SUCCESSFUL / 394 全绿 / 探针 11/11」我**没有**独立核实；报告引用的 `app/build/p1-r3-gate*.log`、`app/build/test-results/**` 在 `app/build/**`（白名单外）我也**未读取**。我只核实了「源码计数与报告数字逐项吻合」（第四节 1）与「探针输出与源码行为自洽」（第四节 5）。
2. **无 diff 级核对** ⇒「只有这些文件被改过」「`app/build.gradle` 与 HEAD 逐字节一致」「未提交/未推送/未切分支」「既有脏基线未被清理/未被删除」「白名单外写入为零」全部**无法独立证明**（见 F-02-6）。
3. **库层语义**：NanoWSD 的 `send`/`close` 是否同步写 socket、`close()` 是否必然触发 `onClose`（进而 `cancel()`）——依赖为外部 jar，我未读其源码，因此「四行摘要完整到达后 socket 才关闭」仍是真机待验（与报告 §7.1 一致）。
4. **真机/UI/Web 端全部 NOT RUN**：调试页四阶段渲染、阶段四补打的真实读数是否出现、Web 端四行摘要的实际**到达**顺序（我只验证了提交顺序）、`@webjs:`、真实书源/真实站点、开关前后正文逐字一致。未读真实书源/语料/`bookshelf.json`/凭据，未操作设备。
5. **未读**：`15-reviewer-raw.md`（禁读）、`app/build/**`、`rounds/**`、`book-source-p1-diagnostics` 的 `20-review.md` 与其任务书（用其 `30-acceptance.md` 替代做完整性核对）、`ExportBookService`/`ChangeBookSourceViewModel`（F-05 因此沿用执行方与 P1 已披露事实）、`AnalyzeRule.kt`、`ReplaceRule.kt`（白名单内，但读它们无法判断「本轮是否改过」）、两份计划书（本轮不需要）。
6. **其余测试文件未逐行审阅**：`ContentProcessorContractTest`、`DiagnosticsToggleParityTest`、`AnalyzeRuleContractTest` 等我只做了 `@Test` 计数（第四節 1），**没有**检查它们是否随本轮改动需要同步更新；执行报告称它们全绿，我只能确认其用例数未变。
7. **未覆盖的端到端行为**：并发分页 `mapAsync` 与阶段一/二采集权（`claimPrimaryStages`）的交互、`record` 的 upsert 在多页并发下「最后写入者胜」的一致性、音频/视频分支、Android 8 分支、`Debug` 单例在「书源调试会话未结束又启动订阅源调试会话」时的诊断残留（`Debug.startDebug(rssSource)` 只调 `cancelDebug(false)`，理论上会把旧 collector 留给新 `callback`——第 5 条的窄路径，我未做实证，只作登记）。
8. **F-02-1 的「可测」结论依赖一条假设**：即 `@After` 里能可靠清理 `Debug` 单例（`cancelDebug(true)`）且 `startBookSourceDebugSession()`/`bindDiagnosticsTarget()` 在 JVM 单测下不触碰 Android API。我按源码判断它们不触碰，但**未运行**验证；若假设不成立，结论方向不变（当前确实没有这条测试）。

---

## 七、给 acceptor / controller 的最短路径建议

1. 在**最终工作树**上复跑任务书 §完成条件第 3 条的字面命令，记录退出码 + 直接解析 XML 的 `suites/tests/failures/errors/skipped` + XML 时间戳。我这边能给的最强旁证是：源码 `@Test` 计数 = 45 文件 / 394 例，与报告数字逐项吻合（第四节 1）——若复跑得不到 45/394，那将是一处需要解释的差异。
2. 把 F-02-6（白名单外写入、HEAD/提交状态、既有脏基线）用 `git diff --name-only` + `git status --porcelain` 一次闭合；这是本轮**唯一**「报告声称而无人能证」的面。
3. F-02-3（§8 步骤 2 的可观测窗口）建议在真机抽查前改写步骤，否则容易把「会话已结束 → 无输出」误判为功能失效。
4. F-02-1/F-02-2 建议随手修（一条测试 + 一处行号），不必单开返工轮。
5. F-02-4（RSS Web 端同形）建议登记为后续项，不并入 P1。
6. F-02-7（执行侧 runner 封套）请 controller 明示口径，避免在验收侧复现 P1 第二轮的「整体 FAIL 被误读为代码失败」。
