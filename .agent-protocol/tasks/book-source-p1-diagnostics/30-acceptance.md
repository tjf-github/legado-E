# book-source-p1-diagnostics — Codex 独立验收

状态：`rework`

日期：2026-09-23。acceptor/controller：本次 Codex 实例。mode=triad，assurance=high，route_revision=7。结论：**本轮 P1 不通过，不得标记 accepted 或进入 P2。**

## 验收依据与范围

读取当前任务书、Claude 原始复审、DSH 报告和实际业务 diff，并检查新增 diagnostics 与契约测试源码。HEAD 仍为 b8ba92e194468f0d7e6103c1175ba9f2a8233d95，分支 codex/manga-import。P0、旧任务、.gitignore 等既有脏基线均保留，不计为 P1 新改动。

P1 跟踪文件改动为 app/build.gradle 及 ReplaceRule、ContentProcessor、Debug、AnalyzeByXPath、AnalyzeRule、BookContent；新增 6 个 diagnostics 生产文件、5 个契约测试类。未发现此次验收改动任何生产源码。role-verify 在验收前对 executor/reviewer 两项均 PASS；这只证明报告与回执哈希一致。

Claude 通过 claude-code --restricted、Read/Grep/Glob 只读工具策略执行。回执目的地为本机网关 http://127.0.0.1:15721，模型未显式指定，不将 CLI 品牌当作底层模型证明。复审有实际内容，不是空报告；其静态检查不等于独立运行门禁。

## Findings 处置

| Finding | Codex 核对与裁定 |
| --- | --- |
| B-1 错误分类未接线 | 确认。四个生产 record 调用均未传 error/timeoutMs；Debug.onError 也没有传异常给 collector。分类组件存在，但无法承担真实错误分层诊断。阻塞。复审中的 HTTP 200 错误页示例不能直接当作已复现的 HTTP 分类错误，本次只采信可验证的未接线事实。 |
| B-2 提取阶段标错 | 确认。AnalyzeRule.setContent:124-132 记录原始响应，真实 contentRule.content 提取在 BookContent:270。必须记录实际规则产出与对应耗时。阻塞。 |
| B-3 缺阶段 / L-5 并发采集权 | 确认。BookContent:46-48 是全局标志，76-79 用 nextChapterUrl 是否为空重置且 check-then-set 非原子；普通阅读也会写它，诊断会话没有自己的采集身份。缺失阶段无兜底。合并为同一返工项。 |
| B-4 Web 完成回调竞态 | 确认静态可达：Debug:408-414 先终止日志再摘要；WebSocket:89-93 收到成功或失败终止状态后可异步清空 collector/callback。成功与失败路径均需先完成诊断输出再发终止信号。真机竞态复现 NOT RUN。 |
| B-5 开关正文一致性 | 确认缺口。现有 disabledCollectorRecordsNothing 只证明容器关闭不存记录，不能证明真实正文链路开关前后相同。必须补生产链路差分测试；ContentProcessor 测试只覆盖抽取的尾部纯函数。 |
| M-1 脱敏 | 独立执行 P1RedactionProbe.java，三个合成样例全部泄漏（exit 1），见 32-redaction-probe.log。除复审指出的普通 URL 查询/fragment 外，发现 Cookie/Authorization 冒号后空格会保留完整值。阻塞；不得在修复前把异常消息接入摘要。 |
| M-2 构建测试默认值 | 确认越界：app/build.gradle 新增全局 returnDefaultValues=true。本轮不追认该全局弱化。返工必须恢复严格默认或提供经 controller 批准的局部测试替代，不能把静默 Android 默认值视为真机契约证明。当前通过的测试仍必须披露配置前提。 |
| M-3 XPath | 确认越界历史，TextUtils.join 到 joinToString 未发现当前 Iterable 调用语义差异；改动具备保留价值，但本次验收不改原白名单、不抹去越界。后续返工由 controller 显式纳入范围再保留。 |
| L-1 全局串扰 / 本地路径侵入 | 确认。ContentProcessor 无书源/书籍/章节/请求身份检查，所有阅读路径可写同一 collector，包括本地书；未发现文本转换本身回退，但已不满足本地路径零侵入和阶段归属。提升为阻塞，随 B-3 一起隔离。 |
| L-2 textNodes 错误结论 | 确认夹具只有 p 子元素，没有直系非空文本；JSoup 实现读取直系 textNodes 符合预期。不得作为产品 bug 推进后续修复。返工应更正测试注释与新一轮报告，保留本轮原始报告。 |
| L-3 抽象与死代码 | 非阻塞；无需为纯风格问题反复复审。skeleton 当前并非“连续字符压缩”，且其长度裁剪仍有意义，不能机械采纳 reviewer 所有建议；优先功能与脱敏边界。 |
| L-4 临时 Gradle 目录 | 确认 tmp-gradle-home 存在。本轮仅盘点，不删除不确定来源缓存或唯一证据；返工收尾须声明来源与范围并按清理规则处理。 |

额外观察：ContentProcessor:111 和 AnalyzeRule.setContent 即使诊断关闭仍读取时钟，不应宣称严格“零额外处理”；返工时将诊断计时与会话开关一起限定。暂未发现正文输出变化，不等于全链路一致性已经证明。

## 独立验证

设置 GRADLE_USER_HOME=D:/gradle_home 后运行 .\gradlew.bat :app:compileAppDebugKotlin :app:testAppDebugUnitTest --console=plain。沙箱内因 wrapper 锁文件访问被拒失败；授权沙箱外复跑退出码 0，BUILD SUCCESSFUL in 3m 40s（57 tasks，14 executed，43 up-to-date）。两个目标任务均实际运行，原始日志见 31-acceptance-gradle.log。XML 独立统计：suites=43, tests=358, failures=0, errors=0, skipped=0。此前 DSH problems-report 写盘失败本轮未复现。注意测试沿用现有 returnDefaultValues=true 配置，绿色结果不消除 M-2 的风险。编译完成后重跑脱敏探针，仍 exit=1、3/3 失败。

脱敏反例：直接调用生产 DiagnosticRedactor，URL chapter=SYNTHETIC_PRIVATE_VALUE 与 #SYNTHETIC_FRAGMENT 原样保留；Cookie: SYNTHETIC_COOKIE_VALUE 变为 Cookie:*** SYNTHETIC_COOKIE_VALUE；Authorization 同样保留原值。3/3 失败。探针只用合成数据，源文件与原始日志作为持久验收证据保留。

NOT RUN：真机、UI 四阶段可见性、WebView、真实书源、诊断开关完整端到端正文一致性、lint（本轮非发布）。未安装 APK、未操作设备或用户数据。

## 最终判定与下一步

**rework。** 编译/测试绿色也不能覆盖已确认的阶段真实性、会话隔离、错误接线与脱敏缺陷。P1 尚未达到退出条件。

唯一下一步：为 DSH 准备有明确白名单的返工轮次，先修脱敏与会话边界，再补真实阶段/错误接入及开关差分测试，处理构建越界与错误注释，重跑门禁后进入第二轮独立复审。当前轮执行/复审原文不覆盖。此次请求为验收，本轮不启动新的返工代理，不提交、不推送、不发布。

不调用 accepted/closed 收口。状态指针与任务书同步为 rework；下一轮 runner 应归档本轮冻结快照与角色证据。复审后本轮只增加 acceptor 证据并记录状态转换，未扩大执行授权。

---

## 2026-09-24 第二轮返工验收（Codex acceptor）

**结论：rework，P1 不接受，不进入 P2。** 本节保留上方首轮验收原文。当前分支仍为 `codex/manga-import`，HEAD 仍为 `b8ba92e19`；未提交、推送或发布。DSH 第二轮报告的最后写入时间为 2026-09-23 16:38，本次验收未发现比该报告更新的生产实现。

### 已独立闭合的证据

- 在最终 P1 源码上设置 `GRADLE_USER_HOME=D:\gradle_home`，运行 `./gradlew.bat :app:compileAppDebugKotlin :app:testAppDebugUnitTest :app:assembleAppDebug --console=plain`：`BUILD SUCCESSFUL in 6m 31s`，退出码 0。直接统计 `app/build/test-results/testAppDebugUnitTest/*.xml`：44 套件、380 用例、failures/errors/skipped 均为 0。此门禁独立闭合第二轮复审 F-07 的构建证据缺口。非发布轮，lint 未运行。
- Claude reviewer-r002 在只读 `tool-policy` 下完成独立源码复审，回执 `Outcome=ok`，`20-review.md` 存在完整报告；其构建和真机检查均如实为 NOT RUN。`agent-protocol role-verify book-source-p1-diagnostics` 对当前及历史 reviewer 均 PASS；顶层 executor 因 DSH 第二轮由人类窗口直接指派、无 runner 封套，报告 `round_id=executor-r001, expected=executor-r002`，故整体 FAIL。不能把这个 FAIL 解释为代码失败，也不能冒充本轮执行来源已被协议绑定。
- 首轮的 URL 查询串和 Cookie/Authorization 三例在现有代码中已修复；`app/build.gradle` 当前未保留 `returnDefaultValues=true`。`AnalyzeByXPath.kt` 的 `TextUtils.join` 等价替换仍是任务书白名单外的历史改动，下一轮须由 controller 明确扩范围或回退，本轮不静默追认。

### 阻塞发现

1. **默认诊断摘要仍可泄漏任意正文片段（安全阻塞）。** `BookSourceDiagnostics.kt:145-148` 对非 `RegexTimeoutException` 直接把 `error.message` 交给 `DiagnosticRedactor.sanitizeMessage`，后者只识别 URL、凭据赋值和 Bearer 形态，普通文本保持原样。独立合成探针在最终编译类上执行 `collector.record(EXTRACT, null, 0, IllegalArgumentException("parse failed near SYNTHETIC_PRIVATE_BODY_SENTINEL"), 0)`，`summaryLines()` 包含该 sentinel，输出 `summary_leaks_plain_content=true`。不涉及真实正文或凭据。异常消息是无界输入；只靠 240 字截断不能满足任务书「默认摘要不输出真实正文」。应默认只输出稳定错误类别/固定说明，补任意消息正文守卫测试；原有 `Debug.log` 全文输出另按既有调试行为边界评估，不用它为新增摘要泄漏开例外。
2. **第四阶段会被错误覆盖，且阅读路径无可见输出（功能阻塞）。** `ContentProcessor.kt:234-240` 可记录真实 `READ_PROCESS`；`Debug.kt:454-459` 在收尾无条件 `markNotExecuted(READ_PROCESS)`，`BookSourceDiagnostics.kt:202-208` 同阶段写入会覆盖旧快照。独立合成探针先 `record(READ_PROCESS, "SYNTHETIC_READING", 3, null, 0)` 再 `markNotExecuted`，得到 `executed_after_finish=false, summary_count=1`。先阅读后收尾会误报未执行；收尾后阅读又不会再次输出摘要。DSH 报告 §8.5 所述「同章正常阅读可见真实第四阶段读数」与代码不符。必须确定第四阶段的展示时机，并避免把已执行快照覆盖为未执行。
3. **Web 端完成日志仍有投递竞态（需纳入下一轮范围）。** reviewer-r002 F-02 追踪到 `BookSourceDebugWebSocket.printLog` 将四行摘要和终止信号分别投递到并发 IO 任务；`Debug.kt` 的调用顺序只保证入队顺序。终止任务可先关闭 socket，四行摘要可能丢失。尚未作 Web 真机复现，不把静态可达性写成实测发生；但 P1 的四阶段可见性不能据此判通过。修复需要明确扩到 WebSocket/调度相关文件。
4. **任务授权与回执未闭合。** `AnalyzeByXPath.kt` 不在原写白名单；第二轮 executor 没有 runner 绑定。二者均须在下一轮路由/任务书中留痕，不因本次门禁绿色而忽略。

### 真机抽查边界

- ADB 曾识别 `10CE5P1M1Z001P9`。原始 `.debug` APK 因设备现有 `.release` 包拥有 `io.legado.READ_WRITE` 而安装失败；没有卸载或覆盖用户包。随后仅在临时 `app/src/debug/AndroidManifest.xml` 中移除 `.debug` 包的同名权限声明，重建并安装隔离的 `io.legado.app.tjf.debug`，安装返回 `Success`。该临时 manifest 已精确删除，并重新生成原始 manifest 对应的本地 debug APK；测试包和正式构建的 manifest 差异必须保留在证据中。
- 在隔离测试包里经 `adb reverse` 从本机导入了合成书源并打开书源调试页。直接正文 URL 输入受设备中文输入法全角标点影响，未触发目标正文链路；合成搜索第一次得到「搜索url不能为空」，之后确认测试包数据库已更新含合成 `searchUrl`，但会话在正文诊断出现前中断。当前 `adb devices -l` 无设备。**未观察到真机四阶段摘要、Web 端顺序、五章翻阅或开关前后正文一致性；这些项目均为 NOT RUN，不能据已安装 APK 宣称真机通过。** 测试只涉及新装 `.debug` 包和本机合成资源，没有读取真实书源、正文或设备既有 App 数据。

### 下一步

保持 `CURRENT.md` 的 `state: rework`。下一轮先修默认异常说明、第四阶段真实性和 Web 端投递顺序，明确 XPath 范围与 executor 回执，再做独立复审和真机合成链路抽查。P2、提交、推送、发布均未授权也未执行。
