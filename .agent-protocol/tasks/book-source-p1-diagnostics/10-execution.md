<!-- 返工轮（round 2）执行报告。本轮由人类在 DSH 窗口直接指派，**未经 agent-protocol runner 拉起**，
     因此没有 runner 封套（agent-protocol-binding）与角色回执；role-verify 对本轮只能记为「未绑定」。
     首轮带封套的原文（round_id: executor-r001）原样保存在 rounds/executor-r001/10-execution.md。 -->
# book-source-p1-diagnostics — 返工轮执行报告（executor: dsh，round 2）

状态：`返工实现完成并已实跑门禁；交独立复审，不宣称 P1 达标`

- 运行实例 / 提供方：dsh（DeepSeek executor），mode=triad，route_revision=7，controller/acceptor=codex，reviewer=claude
- 返工依据：`30-acceptance.md` findings 处置表 +「唯一下一步」，以及 `20-review.md`
- 分支 `codex/manga-import`，HEAD `b8ba92e194468f0d7e6103c1175ba9f2a8233d95`（未提交、未推送、未切分支）
- 时间：2026-09-23 本地时区

---

## 1. 结论先行

1. 验收单上的 **B-1、B-2、B-3(+L-5)、B-4、B-5、M-1、L-1、L-2、L-3、L-4 与「额外观察」全部落地**，逐项对照见 §2。
2. **M-2 按验收要求恢复严格默认**：删掉首轮越界新增的 `returnDefaultValues = true`，`app/build.gradle` 现与 HEAD 逐字节一致；
   在**没有**该弱化配置的前提下，门禁仍然全绿（这同时推翻了首轮「必须有它才能跑测试」的理由）。
3. **M-3** 未改代码、未抹去越界事实，仍需 controller 显式把 `AnalyzeByXPath.kt` 纳入范围（见 §9）。
4. 门禁实跑：提权通道下（任务书原命令）`:app:compileAppDebugKotlin` + `:app:testAppDebugUnitTest` **BUILD SUCCESSFUL，exit 0**；
   最终源码上又用工作区内等价通道复跑一次，同样 **BUILD SUCCESSFUL，exit 0**（提权审批后来无人应答，通道差异与理由见 §5.1）；
   独立统计 **suites=44, tests=380, failures=0, errors=0, skipped=0**。
5. 验收方自带的脱敏探针复跑：**leaking_cases=0/3（exit 0）**，首轮是 3/3（exit 1）。
6. 仍然 **NOT RUN**：真机、UI 四阶段可见性、Web 端调试竞态复现、`lint`、`@webjs:`、真实书源、端到端开关正文一致性。

**没有做**：提交 / 推送 / 切分支 / 发布 / 删用户数据 / 装软件；没碰真实书源 JSON、语料、`bookshelf.json`、签名材料；
没改数据库结构；没动本地 TXT/EPUB 语义；没做 P2—P5。

---

## 2. 返工清单逐项处置（对照 `30-acceptance.md`）

| 项 | 验收要求 | 本轮处置 | 证据位置 |
| --- | --- | --- | --- |
| B-1 错误分类未接线 | 必须把分类接到真实异常路径 | **已接线**：`BookContent` 提取/格式化/替换三处 catch 分类后原样上抛；`Debug.contentDebug.onError` 对链路级异常兜底（网络/HTTP/编码可达）；`ContentProcessor` 把原本被吞掉的替换超时/规则报错带进阶段四，并带上该规则超时阈值；`RegexTimeoutException` 的消息含规则与整段正文，故只写固定说明 | `BookContent.kt`、`Debug.kt`、`ContentProcessor.kt`；测试 `chainFailureWithoutAnyStageIsClassifiedOnFirstStage`、`chainFailureKeepsTheLastStageSummaryAndAddsErrorClass`、`stageErrorIsClassifiedByItsOwnException`、`replaceTimeoutNoteNeverCopiesTheExceptionMessage` |
| B-2 提取阶段标错 | 必须记录实际规则产出与对应耗时 | **已改**：阶段一从 `AnalyzeRule.setContent` 移到 `analyzeRule.getString(contentRule.content, unescape=false)` 返回处；耗时只计提取；`AnalyzeRule` 的 3 参诊断重载与其时钟读取整体删除 | `BookContent.kt`、`AnalyzeRule.kt` |
| B-3 缺阶段 | 缺失阶段要有明确表示与兜底 | **已改**：收尾 `markMissingStagesNotExecuted()` 把仍无快照的阶段全部标「未执行」；`READ_PROCESS` 保留专属原因 | `Debug.finishContentDiagnostics`、`BookSourceDiagnostics.kt`；测试 `missingStagesAreAllFilledAtSessionEnd` |
| L-5 并发采集权 | 与 B-3 合并处理 | **已改**：会话内 `AtomicBoolean.compareAndSet` 一次性认领；`nextChapterUrl` 判据与全局 `primaryStagesCaptured` 整体删除 | `BookSourceDiagnostics.kt`；测试 `primaryStagesClaimIsGrantedExactlyOnceUnderConcurrency`（32 线程并发只 1 个成功） |
| B-4 Web 完成回调竞态 | 先完成诊断输出再发终止信号 | **已改**：`onSuccess` 与 `onError` 都是「先 `finishContentDiagnostics()` → 再 `log(state=1000/-1)`」 | `Debug.contentDebug` |
| B-5 开关正文一致性 | 必须补生产链路差分测试 | **已补**：新增 `DiagnosticsToggleParityTest`，同一输入在「有诊断会话 / 无诊断会话 / 显式关闭的 collector」三种情形下跑生产链路（`AnalyzeRule` → `HtmlFormatter.formatKeepImg` → `ContentLineNormalizer`）并逐字节比较；并断言「有会话时必须真产出 3 条快照」，避免空跑 | 新增测试文件（7 用例全绿） |
| M-1 脱敏 | 修 `stripUrlSecrets`，补非凭据键守卫 | **已修**：URL 范围内只保留 authority+path，`?`/`#` 之后连同查询串/片段整段丢弃；`key: value` 按 Header 语义掩到行尾（首轮只掩一个词）；SECRET_KEYS 补 `set-cookie/sid/auth/key/tk/csrf` | `DiagnosticRedactor.kt`；测试 5 条（含 `?sid=` 非凭据键、行内掩码）；验收方探针 0/3 |
| M-2 构建越界 | 恢复严格默认，或提供经批准替代 | **已恢复严格默认**并重跑门禁通过；`app/build.gradle` 与 HEAD 一致 | §5 门禁证据 |
| M-3 XPath 越界 | 由 controller 显式纳入范围再保留 | **本轮不动该文件**，保留越界事实，交 controller 裁定 | §9 |
| L-1 串扰 / 本地路径侵入 | 与 B-3 一起隔离 | **已隔离**：接收端改为会话目标绑定 (书源+书+章节)，本地书（origin=`loc_book`）、别的书、别的章节、换源统计、API 导出都取不到接收端 | `Debug.diagnosticsFor`、`BookSourceDiagnosticsCollector.accepts`；测试 `collectorAcceptsOnlyTheBoundBookAndChapter` 等 3 条 |
| L-2 textNodes 错误结论 | 更正测试注释与新一轮报告 | **已更正**：注释改为「`@textNodes` 取的是直系子文本节点，夹具无直系文本故返回空，属正确行为」，并补 `第一段<br>第二段` 正例 | `AnalyzeRuleContractTest.cssRuleTextNodesSeesOnlyDirectTextChildren` |
| L-3 未接线代码 | 非阻塞，勿机械采纳 | `skeleton()` 保留但 KDoc 如实写「当前无生产调用点」；删掉无人使用的 `BookSourceDiagnostics` 接口与 `DISABLED` 单例（只留一个生产类型） | `DiagnosticRedactor.kt`、`BookSourceDiagnostics.kt` |
| L-4 tmp-gradle-home | 声明来源与范围并按清理规则处理 | **已盘点并删除**（来源与范围见 §6） | §6 |
| 额外观察 | 诊断计时也要受开关限定 | ContentProcessor 计时改成「拿到接收端才读时钟」；AnalyzeRule 里那次计时随重载删除 | `ContentProcessor.kt`、`AnalyzeRule.kt` |

---

## 3. 改动清单

### 3.1 生产代码（白名单内）

| 文件 | 改动要点 |
| --- | --- |
| `help/book/diagnostics/BookSourceDiagnostics.kt` | 重写：会话号、`Target` 绑定与 `accepts()`、原子采集权、upsert 单一阶段快照、`recordFailure()`、`markMissingStagesNotExecuted()`、`describeError()`、耗时未知不写 `0ms`；删除接口与 `DISABLED` |
| `help/book/diagnostics/DiagnosticRedactor.kt` | 查询串/片段整段丢弃；`key: value` 行内掩码；SECRET_KEYS 扩充；`skeleton` KDoc 更正 |
| `model/Debug.kt` | 会话/目标绑定 API、`diagnosticsFor()`、私有化的 `@Volatile diagnostics`、摘要先于终止状态、链路异常兜底、缺失阶段兜底 |
| `model/webBook/BookContent.kt` | `diagnosticsFor()` 目标匹配、会话认领采集权、阶段一改为真实提取产出、三处异常接线、`elapsedSince()` |
| `help/book/ContentProcessor.kt` | 目标匹配取接收端、计时受开关限定、被吞异常分类带上阶段四、超时阈值随规则 |
| `model/analyzeRule/AnalyzeRule.kt` | 删除 3 参诊断重载与诊断 import（恢复单签名），保留 `TextUtils.isEmpty → isNullOrEmpty` 等价替换 |
| `data/entities/ReplaceRule.kt` | 首轮改动保持不变（`TextUtils.isEmpty → pattern.isEmpty()`） |
| `model/analyzeRule/AnalyzeByXPath.kt` | **本轮未改**；首轮越界改动仍保留（见 §9 M-3） |
| `app/build.gradle` | **恢复原状**（删除首轮越界新增的 `returnDefaultValues`），`git diff` 为空 |

### 3.2 测试

| 文件 | 改动 |
| --- | --- |
| `help/book/diagnostics/BookSourceDiagnosticsContractTest.kt` | 重写扩充：25 → **40** 用例（会话身份、并发认领、upsert、缺失阶段兜底、链路异常归位、超时阈值、脱敏守卫、异常说明不引用消息） |
| `help/book/diagnostics/DiagnosticsToggleParityTest.kt` | **新增 7 用例**：开关前后产出等价（CSS / XPath / JSONPath / 替换 / 离线 JS / 零命中 / 显式关闭） |
| `model/analyzeRule/AnalyzeRuleContractTest.kt` | 更正 `@textNodes` 注释并补直系文本节点正例 |
| 其余 3 个契约测试类 | 未改（HtmlFormatter 16 / ReplaceRule 17 / ContentProcessor 14） |

### 3.3 任务证据

- `rounds/executor-r001/`：**首轮 7 个证据文件原样归档**（复制，未删除未修改）——`10-execution.md`、`10-execution.summary.json`、`11-implementation-notes.md`、`12-executor-checkpoint.json`、`13-executor-context.json`、`14-executor-prompt.md`、`15-executor-raw.md`；
- 顶层 `10-execution.md`（本文件）、`11-implementation-notes.md`、`10-execution.summary.json` 改写为返工轮版本；
- 其余角色文件（`20-review.md`、`30-acceptance.md`、`31/32-*`、`00-task.md`、`05-routing.md`、`06-review-commands.json`）**本轮未动**。

### 3.4 白名单声明

- **白名单外写入只有一处**：`app/build.gradle`，且是**删除**首轮越界新增行、把文件恢复到 HEAD 状态（验收单 M-2 明确要求「返工必须恢复严格默认」）；
- `rounds/executor-r001/` 是按 `MODES.md` 的轮次归档约定新建的目录（runner 通常会做这件事，本轮无 runner，故由 executor 代为复制留档）；
- 未使用沙箱提权以外的任何绕过手段；提权只用于 Gradle 命令（§5）。

---

## 4. 相对既有脏基线的说明

- 起点是首轮提交前的同一工作树，HEAD 仍为 `b8ba92e194468f0d7e6103c1175ba9f2a8233d95`；
- **P0 / 首轮之外的既有脏基线未触碰**：`tools/book-source-audit/**`、`docs/book-source-audit/**`、
  `app/src/test/resources/bookSourceBaseline/**`、`.gitignore`、`.agent-protocol/CURRENT.md`、
  `dual-release-privacy/**`、`书源与解析优化计划书.md`、`bookshelf.json`、仓库根 `build-dsh-p1-*.log`；
- P1 全部改动（**含首轮**，相对 HEAD）在 tracked 侧是 6 个文件 `+233 / −27`（`git diff --numstat` 实测），
  `git diff --check` 无空白错误；untracked 侧是 `help/book/diagnostics/**` 6 个生产文件、
  `help/book/diagnostics/**` 与 `model/analyzeRule/**` 等测试目录（其中 `DiagnosticsToggleParityTest.kt` 是本轮新建）。

---

## 5. 命令证据（含失败与处置）

门禁命令（任务书 §8.3）：

```powershell
$env:GRADLE_USER_HOME = "D:\gradle_home"
.\gradlew.bat :app:compileAppDebugKotlin :app:testAppDebugUnitTest --console=plain
```

| # | 执行 | 结果 | 处置 |
| --- | --- | --- | --- |
| 1 | 未提权直接跑 | wrapper 写 `D:\gradle_home\wrapper\dists\...\gradle-8.14.4-bin.zip.lck` 被沙箱拒绝，exit 1（与首轮 §4.1 同一事实） | 提权到 `danger-full-access` 复跑（每次提权只对该条命令生效） |
| 2 | 提权复跑 | **编译失败**：85 条 `e: file ... Unresolved reference`，全部落在我改过的 4 个文件里，且引用的都是**未改动文件**里的符号（`appDb`、`utils` 扩展、`help` 扩展） | 判定为**陈旧的 Kotlin 增量编译状态**（上一轮/验收轮遗留的 `app/build/kotlin/compileAppDebugKotlin` 与 `app/build/tmp/kotlin-classes/appDebug`），不是源码问题；按任务书授权清理这些临时目录后重跑 |
| 3 | 清缓存后提权复跑 | 编译成功；测试 **378 completed, 1 failed** → `messageSanitizeRemovesCookieAndBearerValues` 失败：`Authorization: Bearer <token>` 按冒号只掩一个词会**掩掉 `Bearer` 而把 token 留在输出里**——这是我改脱敏时引入的真实回归 | 把 `key: value` 的掩码范围改为**行内**（Header 语义），并补 2 条守卫用例 |
| 4 | 修复后提权复跑 | **BUILD SUCCESSFUL in 1m 31s，EXITCODE=0**；两个目标任务均实际执行（`:app:compileAppDebugKotlin`、`:app:testAppDebugUnitTest`） | 原始日志：`app/build/p1-rework-gate.log`（任务书授权临时目录内） |

### 5.1 最终源码上的门禁（提权通道已不可用后的等价通道）

上面的第 4 次成功执行之后，我又删掉了一个**未被任何代码引用的** `sessionId` 字段（避免给复审留下新的死代码），
因此必须在最终源码上重跑一次。此时提权审批连续两次未获应答（命令发不出去），
于是改用**不写任何工作区外路径**的等价通道，仍然使用同一份 Gradle 8.14.4 发行版：

```powershell
$env:GRADLE_USER_HOME = "D:\vsproject\legado-E\build\drp-gradle-home"   # 工作区内既有缓存，任务书授权临时目录
& "D:\gradle_home\wrapper\dists\gradle-8.14.4-bin\92wwslzcyst3phie3o264zltu\gradle-8.14.4\bin\gradle.bat" `
  "-Pkotlin.compiler.execution.strategy=in-process" "-Dorg.gradle.vfs.watch=false" --no-problems-report `
  :app:compileAppDebugKotlin :app:testAppDebugUnitTest --console=plain
```

| # | 执行 | 结果 | 处置 |
| --- | --- | --- | --- |
| 5 | 未提权 + 工作区 GUH（原样命令） | 三个门禁任务都执行、测试 **380 通过 0 失败**（XML 时间戳 15:31），但 Kotlin 编译守护进程写 `%LOCALAPPDATA%\kotlin\daemon\*.tmp` 被拒 → 降级为进程内编译；Gradle 把该降级记为 problem，收尾写 `build/reports/problems/problems-report.html` 又失败 → 整体 `BUILD FAILED`（exit 1） | 定位为环境问题（首轮 §4.1 同源），改用进程内编译策略绕过守护进程 |
| 6 | 加 `-Pkotlin.compiler.execution.strategy=in-process` | 编译与测试任务全部执行、**XML 时间戳 16:37、380 通过 0 失败**；收尾仍因同一 `problems-report.html` 写入失败 → exit 1 | 该文件由**提权运行**创建、未提权进程不可覆盖（我单独试过写/删，均 Access denied），改用 `--no-problems-report` |
| 7 | 再加 `--no-problems-report` | **BUILD SUCCESSFUL in 17s，EXITCODE=0**；`57 actionable tasks: 57 up-to-date`（Gradle 校验输入未变，即第 6 次的编译/测试产物与最终源码一致） | 原始日志：`app/build/p1-rework-gate-final.log` |

**披露（不要当成与任务书命令完全等同）**：第 5—7 次与 §8.3 的字面命令有三处差异——
Gradle 主目录用工作区内的 `build/drp-gradle-home` 而非 `D:\gradle_home`、直接调用同一份 Gradle 8.14.4 发行版而非 `gradlew.bat`、
加了 `-Pkotlin.compiler.execution.strategy=in-process` 与 `--no-problems-report`。
差异全部由 DSH 沙箱拒绝工作区外写入引起（首轮 §4.1 已登记同一环境事实），**不改变编译产物与测试结果**：
第 6 次的两个任务真实执行且测试结果是最新的，第 7 次以 up-to-date 校验确认产物对应最终源码。
第 4 次（提权、任务书原命令）当时对应的是最终源码减去那个未被引用的 `sessionId` 字段。

独立统计（直接解析 `app/build/test-results/testAppDebugUnitTest/*.xml`，不采信 Gradle 摘要）：

```text
suites=44 tests=380 failures=0 errors=0 skipped=0
BookSourceDiagnosticsContractTest tests=40 failures=0
DiagnosticsToggleParityTest        tests=7  failures=0
AnalyzeRuleContractTest            tests=27 failures=0
HtmlFormatterContractTest          tests=16 failures=0
ReplaceRuleContractTest            tests=17 failures=0
ContentProcessorContractTest       tests=14 failures=0
```

**注意**：本轮测试在**没有** `returnDefaultValues` 的严格默认下通过；首轮「必须弱化才能跑测试」的理由已随 3 处 `TextUtils` 等价替换而不成立。

验收方探针复跑（仅只读使用 `P1RedactionProbe.java`，未改该文件）：

```powershell
java -cp "app\build\tmp\kotlin-classes\appDebug;<kotlin-stdlib-2.3.10.jar>" \
  .agent-protocol\tasks\book-source-p1-diagnostics\P1RedactionProbe.java
```

```text
synthetic_input=https://example.invalid/page?chapter=SYNTHETIC_PRIVATE_VALUE#SYNTHETIC_FRAGMENT
actual=https://example.invalid/page                       -> retained=false
synthetic_input=Cookie: SYNTHETIC_COOKIE_VALUE
actual=Cookie: ***                                        -> retained=false
synthetic_input=Authorization: SYNTHETIC_AUTH_VALUE
actual=Authorization: ***                                 -> retained=false
leaking_cases=0/3        （首轮：3/3，exit 1；本轮 exit 0）
```

原始输出：`app/build/p1-rework-redaction-probe.log`（上面这次是在第 7 次门禁之后的最终产物上复跑的，exit 0）。

---

## 6. 临时目录与清理（L-4 及首轮遗留）

- `tmp-gradle-home/`（首轮留在任务证据目录内）：**已删除**。来源可证：其 `daemon/8.14.4/daemon-12168.out.log`
  记录 2026-09-22 13:52:59 启动、13:53:15 停止并清理 `wrapper/dists`，即首轮开工时那次 `GRADLE_USER_HOME` 指向任务目录的早期尝试；
  内容为 Gradle 缓存（`caches/`、`native/`、`daemon/`、`.tmp/problems-report*.html`，200 个文件 / 1.5 MB），
  **首轮报告未引用其中任何文件**，按 `CLOSEOUT.md` §3「必删：构建与编辑器缓存」处置。删除前的完整盘点见本节。
- `app/build/kotlin/compileAppDebugKotlin`、`compileAppDebugUnitTestKotlin`、`app/build/tmp/kotlin-classes/appDebug(|UnitTest)`、`app/build/kspCaches`：为消除陈旧增量状态而清理（授权临时目录内），随后重跑门禁成功。
- 仓库根 `build-dsh-p1-*.log`（4 个，2026-09-22，共约 95 KB）：**首轮原始门禁日志，本轮保留未动**。
  它们不在任务书授权的临时目录里，但首轮报告 §4/§4.5 直接引用其内容，删除会让已归档的首轮证据断链——
  建议 controller 在收尾时决定「移入任务证据目录」或「随首轮归档一起删除」，本轮不擅自处置。

---

## 7. 限制与 NOT RUN（不要读成已验收）

1. **真机 / UI / Web 端**：NOT RUN。B-4 的修复是静态顺序修正（摘要先于终止信号），
   Web 端竞态「修复前必现、修复后不复现」**没有**实机复现证据；
2. **端到端开关正文一致性**：JVM 侧由 `DiagnosticsToggleParityTest` 在生产组件层覆盖；
   `BookContent`/`ContentProcessor` 依赖 `appDb`/`appCtx`/`Build.VERSION`，无 Robolectric 无法实例化，
   因此**没有**端到端差分证据，仍需真机抽查（步骤见 §8）；
3. **`lint`（`:app:lintAppDebug`）**：本轮未跑（非发版轮），NOT RUN；
4. **`@webjs:`、真实书源、真实站点**：NOT RUN，且未读取真实语料；
5. **本轮无 runner 封套**：`role-verify` 无法绑定本轮执行报告与回执（见文件头说明）；
6. **`ContentProcessor` 的 `RegexTimeoutException` 就地吞掉后把 `item.name + stackTraceStr` 写进正文**
   这一既有行为**未改**（属正文语义，任务书禁止本轮修改），只是把它分类进了诊断。

---

## 8. 后续真机抽查步骤（交给 controller / acceptor）

1. `assembleAppDebug` 装真机 → 书源调试页 → `--<正文页URL>`：
   - 预期看到 `1.提取后`/`2.HTML格式化后`/`3.书源替换后` 三行（无 `replaceRegex` 时第 3 行显式「未执行」）与
     `4.阅读处理后 未执行 (书源调试链路止于 BookContent，未进入阅读处理)`；
   - 每行只含阶段/分类/长度/行数/图片数/耗时，不含正文、查询串、Cookie、令牌。
2. 目录→正文入口重复一次：阶段 1/2 必须仍然出现（首轮在「先正常翻阅再调试」时会静默丢失）。
3. 断网或改成不存在的域名触发抓取失败：预期第 1 行显示 `网络错误`，其余阶段显式「未执行」。
4. 调试会话存活时，切到书架读**另一本书**（或本地 TXT/EPUB）：预期调试摘要里**不再出现**别人的 `4.阅读处理后` 行。
5. 打开同一书源、同一本书、同一章正常阅读：预期 `4.阅读处理后` 有真实读数，且阅读页正文与关闭诊断时逐字一致。
6. 关闭调试页后重复上述阅读：预期零诊断行、零额外处理。

---

## 9. 交接与待 controller 裁定

1. **M-3**：`AnalyzeByXPath.kt` 的 `TextUtils.join → joinToString` 仍在工作树里，本轮未改也未抹去越界记录；
   需要 controller 显式把该文件纳入返工范围（或要求回退）。
2. **本轮执行授权边界**：本轮由人类在 DSH 窗口直接指派，没有 runner 封套与角色回执；
   若要产生可被 `role-verify` 绑定的证据，请用 `agent-protocol role executor -Entry dsh` 重跑一轮，
   当前工作树可直接复用（本轮所有产出一字未提交）。
3. **本轮不声明 P1 通过**：实现与门禁已重跑通过，独立复审（reviewer）与验收（acceptor）都还没做；
   真机项见 §7/§8。
4. **建议登记环境事实（`CLOSEOUT.md` §5）**：本机 DSH 沙箱下，`gradlew` 写 `D:\gradle_home`、Kotlin 编译守护进程写
   `%LOCALAPPDATA%\kotlin\daemon`、以及覆盖 `build/reports/problems/problems-report.html` 都会失败；
   非提权时的可用等价通道见 §5.1。建议由 controller 在《发布推送流程书.md》或`维护计划书`里加一行索引，
   避免下一个窗口把这些环境拒绝误判成产品缺陷（首轮 §4.1 已经踩过一次）。
