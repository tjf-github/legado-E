<!-- agent-protocol-binding
round_id: reviewer-r001
task_digest: 2e900d08080ae6fceacc4881680236819190b37ab470326652e2063f09035286
snapshot_digest: 78d9516e0d2109440467eee6ddf297059c0efe0844bdddd0bc269e0326aa11d8
--># book-source-p1-device 独立复审报告（reviewer: claude，只读静态复审）

## 0. 结论

**有阻塞项。**

阻塞点是**证据层**、不是产品层：`16-device-checks.md` S5 用「会话存活时读另一章，会话帧里没有该章诊断行」作为「跨章隔离」的设备证据，但我把该结论引用的会话与夹具日志、以及白名单内的产品源码对上之后发现——**那个会话从头到尾没有绑定过诊断目标**（toc 抓取被夹具挂起 → 没进 `contentDebug`），因此它本来就不可能为任何章节产出诊断行，这条设备证据不成立（F-1）。同一批会话还被用来支撑执行侧新增发现 D-3 的前提（F-1 附带）。

另有一条**必须更正的叙述**：S4/S7 的方法说明称 `hang-once` 的作用是「让同期阅读链路照常取到内容 / 随后同 URL 的阅读链路抓取正常返回」，而夹具服务日志显示该窗口内 `/chapter.html` 只有被挂起的那一次请求，阅读走的是本地缓存，从未发生「并发抓取」窗口（F-2）。这条不影响 S4 的结论，但照现在的写法会被读成「已演示并发抓取窗口」。

**我不反对的核心结论**：S4/S7 的正面证据（阶段四真实读数在会话存活期内被补打、随后抓取失败没有污染第 4 行）由原始帧 + logcat 双源支撑，时间线可自洽复原，我没有找到推翻它的反例；S1/S2/S3/S6/S8/S9 我也独立核对过，成立。

| 项 | 报告判定 | 我的独立判定 | 依据 |
| --- | --- | --- | --- |
| S1 | 通过 | 成立 | `20-S1-…xml/png`（四行齐全、阶段 4 未执行）；线格式与白名单源码契约逐字一致 |
| S2 | 通过 | 成立 | `21-S2`、`22-S2b` XML（阶段 1/2 在目录入口与完整链路下均出现） |
| S3 | 通过 | 成立（子场景 b 未达 S3 措辞，见 F-4） | `23-S3-connect-refused.xml` 四行 = 网络错误 + 三个显式未执行 |
| S4 | 通过（Web 渠道） | 结论成立，**叙述需更正**，判定词偏宽 | 帧 #18–#21 + logcat 23:11:17.399；见 F-2、F-4 |
| S5 | 部分通过 | **「别的章节」子项证据不成立** | 见 F-1 |
| S6 | 通过 | 成立 | logcat 在 23:11:33.242–23:12:52.691 之间零 `sourceDebug` 输出；`50` 与 `33` 文本逐字相同 |
| S7 | 通过 | 成立 | 帧 #22–#25（阶段 1 未知错误、阶段 4 仍为真实读数）+ logcat 23:11:33 + 夹具日志 23:11:33 断服 |
| S8 | 通过 | 成立 | 帧序 #23–#26 → #27 → close；`BookSourceDebugWebSocket.printLog` 的队列保序可解释 |
| S9 | 通过 | 成立（脚本非空通过，我独立复核过三份输入） | 三份 dump 的 `content-desc` 我逐字取出比对，全部相同 |

---

## 1. 发现

### F-1（阻塞｜证据缺口）S5「别的章节不串入」所引会话从未绑定诊断目标，证据不成立

**位置**：`16-device-checks.md:91-98`（S5 正文，第 93-95 行是被引用的会话）；`10-execution.summary.json:36`；连带 `10-execution.md:107`（D-3 的前提）；证据侧 `33-device-evidence/60-ws-frames-all.log:237-246`、`60-fixture-server.log:27-30`、`60-ws-frames-all.log:190-236`。

**核对**：S5 说「该会话的全部帧只有 `frame#1 ⇒开始访目录页`、`frame#2 ︾开始解析目录页`」。`60-ws-frames-all.log` 中符合该描述的只有第 237-241、242-246 两段（`timeout=45.0s`，各 2 帧，且都**没有** `=== done ===` 行，下一行就是新的 `=== connect ===`）。它们对应夹具日志中 toc 被挂起的那两次会话：

- `[23:15:33] GET /toc.html mode=hang holding-without-response`（会话起于 logcat 23:15:32.168）
- `[23:16:09] GET /toc.html mode=hang holding-without-response`（会话起于 logcat 23:16:08.872）

两段帧里都没有 `≡获取成功:toc.html` → toc 解析从未成功。而产品侧的目标绑定只发生在 `contentDebug()` 里（`app/src/main/java/io/legado/app/model/Debug.kt:422` 的 `bindDiagnosticsTarget(...)`），`contentDebug` 又只从 `tocDebug()` 的 `onSuccess` 调用（`Debug.kt:393-411`）。于是 `BookSourceDiagnosticsCollector.target == null`，`accepts()` 在 `BookSourceDiagnostics.kt:92-99` 第一句就返回 false。

**具体失败场景（可判定该证据无效）**：任何一次正文处理（无论章节是否匹配）在这些会话里都拿不到接收端，都会「没有诊断帧」。所以「没有 Chapter two 的诊断行」不能归因于章节/书籍隔离——把被读的章节换成目标章节自己，结果一样是零帧。也就是说：该会话缺**正对照**（同一会话内目标章节应当出现补打），报告 S5 里「会话绑定的目标章节是 `/chapter.html`」这句话对该会话不成立。

同一批会话（`60-ws-frames-all.log:190-236` 的 23:13:57 段、以及上两段）也是 `10-execution.md:107` D-3 的观察窗口：D-3 说「补打依赖阅读路径真的重新执行 ContentProcessor；23:14 那次会话切回 Chapter one 没有补打（会话仍存活）」。在该会话里 toc 同样被挂起（`60-fixture-server.log:26` `[23:13:58] GET /toc.html mode=hang`），目标同样从未绑定，因此「没有补打」有更简单的解释：**不是阅读路径没重跑处理，而是这次会话根本没有绑定目标**。D-3 的「原因未定」在现有证据下应当优先归因于此。

**建议**：把 S5 的「别的章节」子项与 D-3 一并改判 `NOT RUN / 证据不足`，或按下列最小对照重做（夹具 `mode.txt=normal`，让 toc 解析成功、目标确实绑定）：
1. 会话存活期间在同一次会话内先读绑定章节 → 必须出现**补打**（正对照，否则该会话是空转）；
2. 同一次会话内再读另一章 `Chapter two` → 不得出现属于 Chapter two 的诊断行；
3. 保留同一时刻的夹具日志（证明第 2 步真的抓到了 `/chapter2.html` 或被缓存命中）与帧日志，并在帧日志里给出该会话的起止墙钟。

### F-2（中｜报告与工件不一致）S4/S7 的 `hang-once` 窗口叙述被夹具日志否证；S4 佐证截图的时刻也不符

**位置**：`10-execution.md:48`、`11-implementation-notes.md:54-55`、`16-device-checks.md:68-70` 与 `:85-86`；证据 `60-fixture-server.log:21-22`、`:25`、`:30`、`:32`。

**核对**：报告说 `hang-once` 的作用是「只让调试链路自己的抓取停在空中，让同期阅读链路照常取到内容 / 随后同 URL 的阅读链路抓取正常返回」。夹具日志显示：

```text
[23:11:11] GET /toc.html mode=hang-once:/chapter.html -> 200 137B
[23:11:11] GET /chapter.html mode=hang-once:/chapter.html consumed -> mode.txt reset to normal, holding-without-response
```
之后到 `[23:12:53]` 的下一次 `/chapter.html` 请求（又一次 `hang-once` 被消费并挂起）、`[23:16:15]`（挂起）、`[23:17:47]`（normal）为止，**没有任何一次成功的 `/chapter.html` 抓取**。也就是说 23:11:17.399 的那次真实阅读处理读的是**本地缓存的章节正文**，而不是「同 URL 的阅读链路抓取正常返回」；`hang-once` 想制造的「调试抓取挂起 + 阅读抓取成功」这一并发窗口在本轮从未出现过（各会话都一样：23:09:25/23:09:44 段同理）。

**具体影响**：S4 的结论（会话存活期内的补打带真实阶段四读数）不受影响——帧 #18–#21 与 logcat 23:11:17.399 证明阅读管线确实在那个时刻处理了该章；但现在的写法会让人以为「已演示并发抓取下阅读链路照常工作」，这是比实际发生的更强的断言。

**另**：`16-device-checks.md:85` 说截图 `40-S4-reading-chapter-one.png` 是「此刻阅读页显示 Chapter one」。该 PNG 的状态栏时钟是 **23:10**，而补打 logcat 时间戳是 **23:11:17.399**（会话 D 起于 23:11:10.77）。截图至少早 20 s 以上，不能佐证「此刻」。S4 的时刻证据实际来自 logcat + 原始帧（这两者成立），建议删掉「此刻」这一措辞或换一张与 23:11:17 同刻的截图。

**建议**：改写 `10-execution.md:48`、`11-implementation-notes.md:54-55`、`16-device-checks.md:68-70`：如实写「本窗口内阅读链路命中本地缓存，未发生并发抓取；`hang-once` 保证的是调试抓取在会话存活期内一直处于飞行状态」。若确实要证明并发抓取窗口，需要一次真正产生第二次 `/chapter.html` 成功抓取（夹具日志可见 `-> 200`）的复跑。

### F-3（低｜未记录的设备行为）链路在 toc/详情阶段失败时，一行四阶段摘要都不会打印

**位置**：证据 `60-ws-frames-all.log:190-236`（`timeout=90.0s`，`frames=4`）、`61-logcat-sourceDebug-full.log:105-107`；源码 `Debug.kt:393-411`（`tocDebug` 的 `onError` 只写堆栈）、`Debug.kt:435-442`（只有 `contentDebug` 的 `onError` 才触发 `recordFailure` + 摘要）。

**核对**：23:13:57.655 的会话在 toc 抓取上超时（`java.io.InterruptedIOException: timeout`，23:14:57.667），从帧到 logcat 都**没有任何 `【诊断】` 行**；23:16:08.872 的会话（toc 被挂起）同样只有 `⇒开始访目录页` / `︾开始解析目录页` 两行。原因在源码里很清楚：诊断采集器只在 `contentDebug` 被调用后才绑定、才可能打摘要；链路在此之前就死掉时，收尾函数不会被调用。

**为什么值得记**：任务书 S3 把「抓取失败」写成「第 1 行分类为网络类错误，其余阶段显式未执行」，读者容易外推为「任何抓取失败都有四行摘要」。实际边界是「失败必须发生在正文抓取阶段及之后」。这不是本轮 P1 修复目标，但报告未记录，建议补进 `16-device-checks.md` 的 NOT RUN/边界说明，或由 controller 决定是否另开项。

### F-4（低｜判定口径）S4 记「通过」偏宽；S3 的措辞与 D-4 未闭合

**位置**：`16-device-checks.md:66`（S4 = 通过）、`:88-89`（偏差披露）；`10-execution.md:106`（D-2）、`:108`（D-4）。

- `--<正文页URL>` 入口（也就是 S1 用的那个入口）在同一场景下**不会**补打（D-2 已如实披露，且与 `BookSourceDiagnostics.kt:92-99` 的逐字比较一致；设备侧章节 URL 是相对值 `/chapter.html`，绑定值却是绝对 URL）。S4 的期望句是「会话存活时读同一书源同一本书同一章」，因此在被覆盖的入口里只是**部分**成立。建议把 S4 判定词改成「部分通过（仅 `++<tocUrl>` 入口绑定相对 URL 时可复现）」，与 S5 的写法保持一致。
- S3 的「隧道断开」如果指「服务端在飞行中被打断」，本轮实测是 `未知错误`（D-4、帧 #22），并不满足 S3 措辞里的「网络类错误」；如果指「连接被拒/隧道不存在」，则 S3a 已满足。建议在 S3 判定里点明是哪一个子场景通过了，避免与 D-4 并列时读起来自相矛盾。

### F-5（低｜证据卫生）三项原始工件缺失/不便复核

**位置**：`11-implementation-notes.md:32-38`（dumpsys 与 `install -r` 结果是**转写**，`33-device-evidence/` 下没有对应原始文件）；`33-device-evidence/31-apk-parity.txt`（UTF-16 系编码，普通 `grep`/`diff` 会失配）；`33-device-evidence/60-ws-frames-all.log`（多次运行**拼接**，其中 237-241、242-246、166-189 三段没有 `=== done ===` 边界，也没有会话起止墙钟）。

**影响**：安装/版本/权限名这些「设备实况」只能靠转写取信；S5/S6 的「会话存活期间」「反复切章」只能靠报告叙述与另一份证据（logcat）对齐墙钟，而 logcat 缓冲区从 23:11:10 起，最早的 A/B/C 三段会话在 logcat 里没有对应行。这不影响已列结论，但会限制 acceptor 的复核效率。建议补最小原始件：安装与 dumpsys 的原始输出文件、夹具日志与帧日志的会话起止墙钟、以及把 parity 文本转成 UTF-8。

### F-6（低｜潜在假读数，本轮未触发）阶段四快照对「非正文输入」不设防

**位置**：`app/src/main/java/io/legado/app/help/book/ContentProcessor.kt:110-116` 与 `:226-246`；`BookSourceDiagnostics.kt:128-160`；`DiagnosticClassifier.kt:147-153`。

**说明**：`getContent` 只在 `content != "null"` 时做净化，但无论是否跳过，末尾都会 `diagnostics?.record(READ_PROCESS, text = contents.joinToString("\n"), ...)`，且 `error == null` 时错误类别由 `classifyOutput` 判——非空文本一律 `正常`。因此如果阅读路径在「未下载哨兵 / 获取失败文案」上仍然调用本函数，阶段四会打出一条 `正常 len=…,lines=…` 的「真实读数」。设备侧有一条间接迹象：`33-device-evidence/53-after-timeout.xml` 的 `content-desc` 是 `Chapter one / 　　获取正文失败 / 　　timeout`——即失败文案按正文渲染（带标题与段落缩进）过，但那时没有绑定会话，无法判定是否走到阶段四记录。

**为什么只报低**：调用方（`ReadBook`/`BookContent` 等）不在我的读取白名单内，我无法确认这条路径是否会触发；本轮也没有「绑定会话 + 阅读自身抓取失败」的组合（S7 是调试链路失败、阅读早已成功），所以这是一个**未验证的风险点**而非已发生的缺陷。建议下一轮补一次该组合（绑定会话内让阅读自己的抓取失败），确认阶段四不会为其打「正常」。

---

## 2. 我实际执行过的核对动作与输出

只读操作：Read / Grep，全部落在任务书白名单内（本任务目录证据文件、`AndroidManifest.xml`、`help/book/diagnostics/**`、`Debug.kt`、`ContentProcessor.kt`、`BookSourceDebugWebSocket.kt`、`SerialTaskQueue.kt`）。**没有执行任何命令**（`06-review-commands.json` 的 `commands` 为空数组 → 一切命令类检查均为 NOT RUN），未接触设备，未写任何文件。

1. **临时改名已还原（静态核对通过）**：`Grep READ_WRITE app/src/main/AndroidManifest.xml` → 3 处：`:28` `android:name="io.legado.READ_WRITE"`、`:541` `readPermission`、`:542` `writePermission`；文件中不存在任何 `io.legado.app.tjf.debug.READ_WRITE` 残留。
2. **产物一致性**：`31-apk-parity.txt` → entries A/B = 3621/3621，`only_in_A/B = []`，`differing_entries = ['AndroidManifest.xml','META-INF/CERT.RSA','META-INF/CERT.SF','META-INF/MANIFEST.MF']`，`dex entries: 23 个`，`dex identical: True`；sha256/大小与 `10-execution.md:27`、`10-execution.summary.json` 完全一致（A `37688190`/`87aa25c9…`，B `37693471`/`31381c41…0a8cff`）。
3. **设备输出与白名单源码契约逐字对齐**（这是我最关心的「设备真的跑了这份代码」核对）：`Grep '【诊断】[^"<]*'` 覆盖全部 33 个 xml，得到 S1/S2/S2b/S3b 的 `1.提取后 正常 len=107,lines=4,imgs=1` … `4.阅读处理后 未执行 (书源调试链路止于 BookContent，未进入阅读处理)`，S3 的 `1.提取后 网络错误 len=0,lines=0,imgs=0 (异常类型 ConnectException，消息默认不输出)`。这些行格式与 `StageSnapshot.format()`、`summarizeText(null)`、`describeError()`、`finishContentDiagnostics()` 的固定文案一一对应（含「没有测得耗时时不写 0ms」这条规则）。
4. **时间线复原（帧 ↔ logcat ↔ 夹具日志三方一致）**：会话 D 起于 23:11:10.77（logcat `⇒开始访目录页` 23:11:10.856 = 帧 #1 `t+0.09`），`t+6.60` 的四行 → logcat `23:11:17.399`；`t+22.43` 的失败四行 → logcat `23:11:33.237`；`t+22.44` 关闭 → 夹具日志 `[23:11:11]` 挂起的那条请求在 ~23:11:33 断服务。`61-logcat-sourceDebug-full.log` 的 `【诊断】` 行数是 **16 行**（23:11:17/23:11:33/23:12:59/23:18:08 各 4 行），与报告所述一致。
5. **S9 脚本非空通过（我独立重算）**：`check_parity.py` 的比较对象是 `33-reading-chapter-one-diag-off`、`40-S4-reading-chapter-one`、`50-S6-reading-after-session-closed`。我用 `Grep` 直接从三份 xml 取出 `content_text_view` 的 `content-desc`，三份逐字相同：`Chapter one&#10;　　CHECK chapter one.&#10;　　Second line.&#10; &#10;`（`35b`/`35c`/`42`/`44`/`47`/`48`/`49`/`54` 亦为同一串）。这条同时排除了「三份都取不到值 → `all(...)` 恒真」的假通过可能。
6. **S6 增量核对**：`61-logcat-sourceDebug-full.log` 在 `23:11:33.242`（阶段四真实读数之后的失败堆栈）到 `23:12:52.691`（下一次会话）之间**没有任何一行** `sourceDebug`。
7. **S8 保序核对**：`BookSourceDebugWebSocket.kt:93-109` 的 `printLog` 入队 → 单消费者 `SerialTaskQueue.submit` → 终止帧（`state==-1/1000`）在**同一队列任务内**先 `send(msg)` 再 `cancelDebug(true)`+`close()`，与帧日志 #23–#26 → #27 → `closed by server` 的顺序一致；`Debug.kt:430-434` 也确认收尾摘要先于终止信号。
8. **D-2/D-4 复现性核对**：D-2 的两条会话键与设备侧相对 URL 的差异，从帧 `◇章节链接:/chapter.html`、logcat `23:11:10.889` 与 `BookSourceDiagnostics.kt:96` 的比较语句可以完整解释；D-4（`unexpected end of stream` 为 `IOException`，非 `SocketException`/`EOFException`）与 `DiagnosticClassifier.kt:81-99` 一致。
9. **截图**：`20-S1-content-debug-four-stage.png` 确认四行摘要在调试页 UI 上可见（时钟 23:03，与夹具日志 `[23:03:29] GET /chapter.html -> 200` 吻合）；`40-S4-reading-chapter-one-during-session.png` 见 F-2 的时刻不符。

## 3. 未检查到的范围（诚实清单）

- **所有命令类检查 = NOT RUN**：没有跑 Gradle/测试/`:app:lintAppDebug`，没有跑 `adb`，没有复核 `git diff -- app/src/main/AndroidManifest.xml` 是否为空（我只能从文件内容确认无改名残留；**无法**排除 manifest 相对 HEAD 的其它改动），也无法复核安装时间、包版本、设备上书源 `checkKeyWord`、Web 服务开关是否已关闭、`adb reverse/forward` 是否已移除、其它包数据是否未被触碰。
- **白名单外的源码我没读**，因此以下点我无法逐行核对：阶段 1/2/3 的采集点与 `claimPrimaryStages()` 的调用点（`model/analyzeRule/AnalyzeRule.kt`、`model/webBook/BookContent.kt` 等）、`HtmlFormatter` 的格式化语义、Android 调试页侧 `Debug.Callback` 实现（`ui/book/source/debug/*`）、`WebService`/`BookSourceController`、以及 `app/build/p1-device-fixture/**` 里的夹具本体（`source.json` 与各 HTML；我只能从 logcat 打印出的响应体间接看到 `toc.html`/`chapter.html` 的内容）。
  - 由此，**S1/S2 里 `len=107/85/81`、`lines=4/3/3`、`imgs=1` 的具体算术我无法重算**（依赖夹具 content 规则与 HtmlFormatter）。我确认的是这些行与白名单源码的输出契约、以及彼此递减的方向自洽，不确认数值本身；如果 acceptor 要卡数值，需要夹具 `source.json` 与格式化实现。
- **前几轮证据的引用未能核对**：`16-device-checks.md:60-61` 拿首轮真机证据做对照、`:89`/`:97-98` 引用 `collectorAcceptsOnlyTheBoundBookAndChapter` 这个 JVM 契约测试——`book-source-p1-rework-r3/**`、`book-source-p1-diagnostics/**` 与测试源码都不在我的读取范围内，本轮也没有任何测试执行证据，因此「跨书隔离只有 JVM 契约测试」这一点在本轮**仍是未执行**的静态主张。
- **未做**：真实书源/真实站点、音频/视频/文件类书源、多页章 `nextContentUrl`、`@webjs:`、浏览器端 Web 调试页、其它 `Debug.Callback` 实现、发布类门禁——与报告 §6 的 NOT RUN 清单一致，我没有独立证据能覆盖或否定这些项。
- 本报告依据的工件与我启动时看到的任务书/执行报告一致，未发现冻结窗口内的改动迹象（但我无法做前后快照比对——无命令权限）。
