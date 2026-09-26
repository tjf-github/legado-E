# book-source-p1-rework-r3 — 执行报告（executor: dsh）

状态：`第二轮冻结版：P1 五项交付 + reviewer-r001 的 F-01…F-06 处置完毕，门禁与探针已复跑；交第二轮独立复审`

- 任务：`.agent-protocol/tasks/book-source-p1-rework-r3`，mode=`dsh-claude`，route_revision=8，assurance=standard
- 角色：controller=dsh（本窗口）｜executor=dsh｜reviewer=claude（runner 拉起）｜acceptor=dsh
- 依据：`book-source-p1-diagnostics/30-acceptance.md`（2026-09-24 第二轮验收）§阻塞发现 1—4 与 §下一步、`20-review.md`（reviewer-r002）F-01/F-02/F-03/F-06、`MODES.md` 的 standard 档两轮复审上限收敛规则，以及本任务第一轮复审 `20-review.md`（reviewer-r001）的 F-01…F-06
- 分支 `codex/manga-import`，HEAD `b8ba92e194468f0d7e6103c1175ba9f2a8233d95`（未提交、未推送、未切分支、未发布）
- 时间：2026-09-24 本地时区
- 生成器 `-Activate` 仍因模板状态行 CRLF 与 `^状态：`draft`$` 正则不匹配而回滚（与 P1 同一事实），本任务按 CLI 支持的 draft + controller 手工激活流程补全

---

## 1. 结论先行

1. `book-source-p1-diagnostics` 第二轮验收的**四个阻塞项与 F-03 全部落地**（逐项见 §2）；本任务第一轮复审的 **F-01/F-02/F-03/F-04/F-06 全部已处置**（逐项见 §3）。
2. 门禁实跑两次（提权通道，任务书原命令）：第一次 **BUILD SUCCESSFUL in 3m 38s**（本轮首版），复审后复跑 **BUILD SUCCESSFUL in 55s**（exit 0）。最终统计（直接解析测试 XML，时间戳 2026-09-24T22:32:07）：**suites=45, tests=394, failures=0, errors=0, skipped=0**。
3. 合成探针（在**编译产物**上直调生产类，只用合成常量）：**11/11 全部 PASS，exit 0**，覆盖 P1 验收阻塞 1—2 的复现场景与本轮 F-01/F-02/F-03 的守卫。
4. M-3 的越界裁定已由 controller 显式给出：把 `AnalyzeByXPath.kt` 纳入本轮白名单并保留等价替换（§2 第 4 项）。
5. **执行侧没有 runner 封套**：本轮由 controller（同一 DSH 实例）直接指派执行，`agent-protocol role-verify` 对本执行侧**不构成绑定**（`10-execution.summary.json` 的 `source` 字段已按事实改为 `direct-controller-dispatch`；见 §3 F-04）。**独立复审一侧是 runner 封套的**：`20-review.md`、`15-reviewer-raw.md` 由 `agent-protocol role reviewer -Entry dsh` 落盘。
6. 仍然 **NOT RUN**：真机/UI 四阶段可见性、Web 端竞态的真机前后对照、端到端（真机）开关正文一致性、`@webjs:`、真实书源与真实站点、`:app:lintAppDebug`（非发布轮）。

**没有做**：提交 / 推送 / 切分支 / 发布 / 装软件 / 装 APK；没碰真实书源、语料、`bookshelf.json`、签名材料与凭据；没改 P1 任务目录里的任何角色原文。

---

## 2. P1 验收阻塞项逐项处置

| # | 验收要求 | 本轮处置 | 证据 |
| --- | --- | --- | --- |
| 1 默认摘要仍可泄漏任意正文（安全阻塞） | 默认只输出稳定错误类别/固定说明，补任意消息正文守卫测试 | `describeError` 不再引用 `error.message`，只写 `异常类型 <类名>，消息默认不输出`；阶段内异常与链路级兜底共用同一实现 | `BookSourceDiagnostics.kt:164-165`；测试 `arbitraryExceptionMessageNeverReachesTheSummary` 等 3 条；探针 check 1—3 |
| 2 第四阶段被无条件覆盖成「未执行」（功能阻塞） | 避免把已执行快照覆盖为未执行，并确定展示时机 | ①`markNotExecutedIfMissing`：已有快照直接返回，`finishContentDiagnostics` 改用它；②`ContentProcessor` 记录阶段四后调用 `Debug.onReadingStageRecorded()`：会话存活且判定为「应当补打」时补一次含真实读数的摘要；③判定下沉为 `BookSourceDiagnosticsCollector.shouldReportReadingStage()`（可 JVM 直测）；④会话已结束时不输出、不伪造 | `BookSourceDiagnostics.kt`、`Debug.kt`、`ContentProcessor.kt:234-245`；测试 3 条；探针 check 4—6、8 |
| 3 Web 端摘要与终止信号投递竞态（阻塞） | 让摘要与终止信号按顺序投递 | 新增 `SerialTaskQueue`（无界通道 + 单消费者，提交顺序即执行顺序）；`printLog` 改为提交到同一串行队列，终止信号必然排在四行摘要之后 | `utils/SerialTaskQueue.kt`、`web/socket/BookSourceDebugWebSocket.kt:26-32,93-109`；测试 3 条 |
| 4 M-3 `AnalyzeByXPath.kt` 越界（授权阻塞） | controller 必须显式扩范围或要求回退 | **显式纳入范围并保留**：对 `List<JXNode>` 而言 `joinToString` 与 `TextUtils.join` 语义等价；回退会让 XPath 契约测试调用未实现的 android.jar 桩（首轮加 `returnDefaultValues` 的根因，该弱化已被 M-2 判越界并回退） | 任务书白名单；`AnalyzeByXPath.kt:136-139`；`AnalyzeRuleContractTest` 27 例全绿 |
| 5 F-03 URL 主机名被路径里的 `@` 切掉（低） | 只在 authority 内找 userinfo | `stripUrlSecrets` 先在第一个 `/` 之前取 authority；切不出 authority 时保守回退到旧行为 | `DiagnosticRedactor.kt:100-135`；测试 3 条；探针 check 9—11 |

---

## 3. 第一轮复审（reviewer-r001）findings 处置

| Finding | 判定与处置 |
| --- | --- |
| **F-01 [中] `recordFailure` 会把抓取失败贴到阅读路径的阶段四读数上** | **确认并修**。根因：旧实现的无条件覆盖把错误归属问题掩盖了，取消覆盖后它变成可见输出。`recordFailure` 的候选集合改为排除 `READ_PROCESS`（阶段四由阅读路径产生，与本次抓取链路成败无关），无候选时沿用「归到阶段一」分支；KDoc 写明理由。守卫测试 `chainFailureNeverStampsTheReadingPathStage`、探针 check 7。§7 步骤 5 的预期按新行为改写 |
| **F-02 [中] 「阶段四展示时机」缺少守卫测试** | **确认并修（采纳建议 a）**。判定条件下沉为 `BookSourceDiagnosticsCollector.shouldReportReadingStage()`：诊断开启 + 阶段四确有真实执行快照 + 本会话未补打过，三者同时成立才为 true，并当场占用补打权。`Debug.onReadingStageRecorded()` 只负责「会话是否还活着」这一 Android 相关判断（`callback != null`），然后调用它。测试 `readingStageReportNeedsARealReadingSnapshotAndHappensOnlyOnce`（三态）、`disabledCollectorNeverReportsReadingStage`；探针 check 8。`Debug` 侧的打印分支仍依赖 Android 日志桩，**真机可见性仍是 NOT RUN** |
| **F-03 [低] 畸形 URL 上不再遮蔽 userinfo** | **部分确认并修**。「空 authority」按建议保留保守回退（切不出 authority 时在整段上取最后一个 `@`，宁可多切主机名也不留凭据），测试 + 探针 check 11；「userinfo 含 `/`」按 reviewer 判断属 RFC 3986 不允许的畸形写法，**接受为残余边界**并写进 KDoc，不再加代码 |
| **F-04 [中] 执行侧回执表述与事实不符** | **确认并修（改表述，不伪造封套）**。`10-execution.summary.json` 的 `source` 由 `role` 改为 `direct-controller-dispatch`，`note` 明写「执行侧无 runner 封套」；本报告 §1.5 与 §9 同步。事实：dsh-claude 下 controller=executor=acceptor=dsh，执行侧不存在独立 runner 封套；`role-verify` 的实况由 acceptor 在 `30-acceptance.md` 原样记录，不写成「已处置」 |
| **F-05 [低] 补打权可能被导出/换源统计路径先消费** | **确认，登记为已知限制**（不改行为）。§7 步骤 2 补充前置条件：若先对同一本书做过导出/换源统计，该章的补打权可能已被用掉；是否把阶段四限定为阅读路径专用留待 P1 之后的口径决定（与 P1 复审 F-04 同源） |
| **F-06 [低] 两项小项** | ①`SerialTaskQueue.close()` 无生产调用点 → **删除**（消费者随 scope 取消已足够），对应测试一并删除；②`32-probe.log` 是 UTF-16LE → **改为 UTF-8 重新导出**，本版报告所引用的日志即 UTF-8 |

---

## 4. 改动清单

### 4.1 生产代码

| 文件 | 改动要点 |
| --- | --- |
| `help/book/diagnostics/BookSourceDiagnostics.kt` | `describeError` 只输出异常类名；新增 `markNotExecutedIfMissing`；新增 `shouldReportReadingStage`（三态判定，诊断关闭时为 false）；`recordFailure` 排除 `READ_PROCESS`；删除不再使用的 import |
| `help/book/diagnostics/DiagnosticRedactor.kt` | `stripUrlSecrets` 限定 userinfo 在 authority 内；空 authority 保守回退；KDoc 记录残余边界 |
| `model/Debug.kt` | `finishContentDiagnostics` 改用 `markNotExecutedIfMissing`；新增 `onReadingStageRecorded()` |
| `help/book/ContentProcessor.kt` | 记录阶段四后，仅在拿到接收端时触发 `Debug.onReadingStageRecorded()` |
| `utils/SerialTaskQueue.kt` | **新增**：按提交顺序串行执行任务的队列（无界通道 + 单消费者；单任务失败不中断后续） |
| `web/socket/BookSourceDebugWebSocket.kt` | `printLog` 改为提交到 `logQueue`，日志与终止信号保序 |
| `model/analyzeRule/AnalyzeByXPath.kt` | **本轮未改**；首轮等价替换按 controller 裁定保留 |

### 4.2 测试

| 文件 | 改动 |
| --- | --- |
| `help/book/diagnostics/BookSourceDiagnosticsContractTest.kt` | 40 → **51** 例：异常消息守卫 3、阶段四不覆盖/兜底/展示时机三态/关闭态/链路失败不贴阶段四 5、URL 主机名与畸形回退 3 |
| `utils/SerialTaskQueueTest.kt` | **新增 3 例**：保序（200 任务）、并发提交不重叠（4 线程 × 30 任务）、失败任务不中断后续 |

### 4.3 任务证据

- 本文件、`11-implementation-notes.md`、`10-execution.summary.json`；
- `32-probe-source.java`、`32-probe.log`（UTF-8）：探针源码与原始输出，只含合成常量；
- P1 任务目录（`book-source-p1-diagnostics`）只读引用，未改一字。

### 4.4 白名单声明

- 相对 P1 任务书，本轮由 controller 显式扩展三处：`web/socket/BookSourceDebugWebSocket.kt`、`utils/SerialTaskQueue.kt`（新增）、`model/analyzeRule/AnalyzeByXPath.kt`（M-3 裁定保留）；证据文件目录扩展为「本任务目录的 10-*/11-*/16-*/31-*/32-*」。
- **白名单外的写入为零**：`app/build.gradle`、依赖、数据库、`.gitignore`、其它任务证据均未触碰；`CURRENT.md` 按 controller 职责更新指针、mode 与 route_revision。

---

## 5. 相对既有脏基线的说明

- 起点是 P1 两轮返工之后的同一工作树，HEAD 仍为 `b8ba92e19`；
- P1 之前的既有脏基线（`docs/book-source-audit/**`、`tools/book-source-audit/**`、`bookSourceBaseline` 夹具、`.gitignore`、`书源与解析优化计划书.md`、`bookshelf.json`、仓库根 `build-dsh-p1-*.log`）原样保留；
- **本轮改动的文件集合**（相对 HEAD，含 P1 遗留）：tracked 侧 `ReplaceRule.kt`、`ContentProcessor.kt`、`Debug.kt`、`AnalyzeByXPath.kt`、`AnalyzeRule.kt`、`BookContent.kt`、`web/socket/BookSourceDebugWebSocket.kt`；untracked 侧 `help/book/diagnostics/**`（6 个生产文件）、`utils/SerialTaskQueue.kt`、`app/src/test/java/io/legado/app/**` 下 7 个测试文件与既有夹具。**本轮（第三轮）新增的相对 P1 两轮**只有：`SerialTaskQueue.kt`、`BookSourceDebugWebSocket.kt`、两个测试文件、以及 diagnostics 三处方法级修改（`describeError`、`markNotExecutedIfMissing`/`shouldReportReadingStage`、`stripUrlSecrets`）、`Debug.kt` 的 `onReadingStageRecorded` 与 `ContentProcessor.kt` 的一处触发；
- `git diff --check` 无空白错误；未提交、未推送。

---

## 6. 命令证据（含失败与处置）

门禁命令（任务书 §完成条件）：

```powershell
$env:GRADLE_USER_HOME = "D:\gradle_home"
.\gradlew.bat :app:compileAppDebugKotlin :app:testAppDebugUnitTest --console=plain
```

| # | 执行 | 结果 | 处置 |
| --- | --- | --- | --- |
| 1 | 未提权直接跑 | wrapper 写 `D:\gradle_home\...\gradle-8.14.4-bin.zip.lck` 被沙箱拒绝，exit 1 | 按任务书授权对**同一条命令**申请一次 `danger-full-access` 重试 |
| 2 | 提权复跑 | **编译失败**：`BookSourceDebugWebSocket.kt` 12 条 `Unresolved reference`（`appDb`/`GSON`/`isJson`/`printOnDebug`，全指向未改动文件） | 判定为陈旧 Kotlin 增量编译状态，清理 `app/build/kotlin`、`app/build/tmp/kotlin-classes`、`app/build/kspCaches` 后重跑（均在授权临时目录内） |
| 3 | 清理后提权复跑（首版） | **BUILD SUCCESSFUL in 3m 38s，exit 0**；两个目标任务真实执行；XML 时间戳 22:22:54，suites=45 tests=393 | 提交第一轮复审 |
| 4 | 复审后提权复跑（最终版） | **BUILD SUCCESSFUL in 55s，exit 0**；`:app:compileAppDebugKotlin` 与 `:app:testAppDebugUnitTest` 均实际执行；原始日志 `app/build/p1-r3-gate-r2.log` | 独立统计测试 XML |

独立统计（`app/build/test-results/testAppDebugUnitTest/*.xml`，不采信 Gradle 摘要；XML 时间戳 2026-09-24T22:32:07）：

```text
suites=45 tests=394 failures=0 errors=0 skipped=0
BookSourceDiagnosticsContractTest tests=51 failures=0
SerialTaskQueueTest                tests=3  failures=0
DiagnosticsToggleParityTest        tests=7  failures=0
AnalyzeRuleContractTest            tests=27 failures=0
HtmlFormatterContractTest          tests=16 failures=0
ReplaceRuleContractTest            tests=17 failures=0
ContentProcessorContractTest       tests=14 failures=0
```

（对照：P1 第二轮 44/380 → 本任务第一轮 45/393 → 最终 45/394。）

### 6.1 合成探针（在编译产物上直调生产类）

```powershell
javac -encoding UTF-8 -cp "app\build\tmp\kotlin-classes\appDebug;<kotlin-stdlib-2.3.10.jar>" \
  -d app\build\p1-r3-probe\classes app\build\p1-r3-probe\P1R3Probe.java
java "-Dfile.encoding=UTF-8" -cp "app\build\tmp\kotlin-classes\appDebug;<kotlin-stdlib-2.3.10.jar>;app\build\p1-r3-probe\classes" P1R3Probe
```

```text
PASS blocker1_exception_message_not_in_summary :: 【诊断】1.提取后 未知错误 len=0,lines=0,imgs=0 (异常类型 IllegalArgumentException，消息默认不输出)
PASS blocker1_stable_class_name_present :: （同上）
PASS blocker1_chain_failure_message_not_in_summary :: 【诊断】1.提取后 未知错误 len=14,lines=1,imgs=0 耗时1ms (异常类型 IllegalStateException，消息默认不输出)
PASS blocker2_executed_snapshot_kept :: StageSnapshot(stage=4.阅读处理后, errorClass=正常, summary=len=17,lines=1,imgs=0, elapsedMs=3, executed=true, note=null)
PASS blocker2_summary_keeps_real_reading_readout :: 【诊断】4.阅读处理后 正常 len=17,lines=1,imgs=0 耗时3ms
PASS blocker2_missing_stage_still_marked :: StageSnapshot(stage=4.阅读处理后, errorClass=正常, summary=, elapsedMs=0, executed=false, note=未进入阅读处理)
PASS f01_chain_failure_not_stamped_on_reading_stage :: StageSnapshot(... executed=true ...) | StageSnapshot(stage=1.提取后, errorClass=网络错误, ...)
PASS f02_report_decision_three_states :: noSnapshot=false,onlyNotExecuted=false,firstReal=true,secondReal=false
PASS f03_host_kept_when_path_has_at :: 请求 https://api.example.com/@user/profile 失败
PASS f03_userinfo_still_dropped :: 请求 https://api.example.com/@user/profile 失败
PASS f03_malformed_url_without_authority_drops_credentials :: 请求 https://api.example.com/x 失败
probe_failures=0/11
probe_exit=0
```

原始输出与源码：`32-probe.log`（UTF-8）、`32-probe-source.java`。探针只用合成常量（`SYNTHETIC_*`），不涉及真实正文、凭据或设备数据。

**探针自身的边界**（不掩盖）：首跑曾因 Java 源文件按平台默认编码读取导致中文断言假 FAIL，改为 `javac -encoding UTF-8` 后消失；探针覆盖组件层语义，不能替代真机端到端。

---

## 7. 限制与 NOT RUN（不要读成已验收）

1. **真机 / UI / Web 端**：NOT RUN。阻塞 3 的证据是「串行队列保序单测 + 静态调用链核对」，**没有**「修复前后真机对照」；NanoWSD 库层的 `send` 是否同步写 socket 无法从仓库内核实。
2. **端到端（真机）开关正文一致性**：JVM 侧由 `DiagnosticsToggleParityTest`（7 例）覆盖；`BookContent`/`ContentProcessor` 依赖 `appDb`/`appCtx`/`Build.VERSION`，无 Robolectric 无法实例化。
3. **`Debug.onReadingStageRecorded` 的打印分支**：判定条件已可 JVM 直测（F-02 处置），但「真的多打印一段摘要」的正面分支依赖 Android 日志桩，只有真机能验。
4. **可接受的观感副作用**：当阅读路径在调试收尾**之前**补打过摘要时，用户会在收尾再看到一份摘要（同一会话最多两份）。这是「每个时点如实」的代价，未做去重，登记为已知观感项。
5. **补打权可能被非阅读路径消费**（reviewer-r001 F-05）：同书同章的导出/换源统计也会写阶段四并占用补打权；此时真机步骤 2 的预期不成立。未改行为，属 P1 之后的口径决定。
6. **`lint`（`:app:lintAppDebug`）**：非发布轮，NOT RUN。
7. **`@webjs:`、真实书源、真实站点**：NOT RUN，未读取真实语料。
8. **其它 `Debug.Callback` 实现未纳入本轮**：`ui/book/source/debug/BookSourceDebugModel`、`ui/rss/source/debug/RssSourceDebugModel`、`web/socket/RssSourceDebugWebSocket` 的 `printLog` 未改（P1 阻塞项点名的是书源调试 Web 端；RSS 链路与四阶段诊断无关）。reviewer-r001 因无全仓库搜索能力未核实此声明，acceptor 已在验收侧复核。
9. **执行侧 runner 封套不存在**（F-04）：dsh-claude 下 controller=executor=acceptor=dsh；`role-verify` 对本执行侧的结论由 acceptor 原样记录。
10. **环境事实（建议登记）**：DSH 沙箱下 `gradlew` 写 `D:\gradle_home`、覆盖提权进程创建的 `build/reports/problems/problems-report.html` 会被拒；陈旧 Kotlin 增量缓存会让**未改动文件**的符号变成 `Unresolved reference`（本轮第 2 次尝试即此现象）。

---

## 8. 真机抽查步骤（交给 acceptor / 后续验收）

前置：`assembleAppDebug` 装隔离 debug 包（P1 第二轮记录的包名权限冲突需隔离包名，不要卸载用户现装包）。

1. 书源调试页输入正文页 URL：预期 `1.提取后`/`2.HTML格式化后`/`3.书源替换后` 三行 + `4.阅读处理后 未执行 (书源调试链路止于 BookContent，未进入阅读处理)`；每行只含阶段/分类/长度/行数/图片数/耗时，不含正文、查询串、Cookie、令牌。
2. **调试会话保持存活**，切到书架打开同一书源、同一本书、同一章阅读：预期随后再出现一段摘要，其中 `4.阅读处理后` 是真实读数（`正常 len=..,lines=..,imgs=.. 耗时..ms`），且不含「未执行」；同一会话只补打一次。**前置条件**：此前没有对同一本书做过导出/换源统计（否则补打权可能已被消费，见 §7.5）。
3. 关闭调试页后再读同一章：预期零诊断输出、零额外处理。
4. 读**另一本书**或本地 TXT/EPUB：预期摘要里不出现别的书/本地书的 `4.阅读处理后` 行。
5. **断网或改成不存在的域名触发抓取失败**：预期第 1 行显示 `网络错误`、2/3 显式「未执行」；即使会话期间同章被缓存阅读过，第 4 行也**必须仍是阅读的真实读数**（`正常`），不得被标成网络错误（reviewer-r001 F-01 的真机对照）。
6. Web 端调试：预期四行摘要完整到达后 socket 才关闭。
7. 阅读页正文与关闭诊断时逐字一致。

以上 1—7 全部 **NOT RUN**，本轮不据静态证据宣称通过。

---

## 9. 交接与待办

1. **本任务不声明 P1 通过**：实现、门禁、探针与第一轮复审处置已完成，独立复审第二轮与验收（dsh，standard 档不要求独立 acceptor）尚待执行。
2. **P1 任务（`book-source-p1-diagnostics`）保持冻结**：其两轮复审已用尽 standard 档上限，本任务即收敛规则要求的后续任务；原目录只读。
3. **M-3 已裁定**（保留并纳入范围）；**F-04 的执行侧封套按事实记为不存在**，不冒充。
4. **建议登记环境事实**：见 §7.10。
