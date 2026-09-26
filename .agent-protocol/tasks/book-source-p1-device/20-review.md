<!-- agent-protocol-binding
round_id: reviewer-r002
task_digest: 2e900d08080ae6fceacc4881680236819190b37ab470326652e2063f09035286
snapshot_digest: 78d9516e0d2109440467eee6ddf297059c0efe0844bdddd0bc269e0326aa11d8
--># book-source-p1-device 独立复审（reviewer: claude，第 1 轮）

## 一、结论

**通过（无阻塞项）：设备证据支持「P1 第三轮返工后的预期表现」这一主张，且其载重项（S4 补打、S7 失败不污染阶段四、S8 保序、S3a 脱敏、S9 正文一致）我在只读范围内逐条独立复算成立。**

同时有 **1 项中级**、**6 项低级**发现，全部是"报告自述与产物/任务书口径不一致"或"证据可达性"性质，不推翻任何设备结论；其中 F-1（S5 判定词宽于任务书场景）、F-2/F-3（F-5 声称的两项整改在证据目录里未落地）**必须在验收单里按收窄口径记录，否则会被误读为已覆盖**。

我**没有**发现：伪造读数、摘要泄漏正文/查询串/Cookie/令牌、越界改动生产代码、白名单外读取；也没有发现能让 S1—S9 结论翻转的反例。

判定依据的独立性：本轮 `06-review-commands.json` 的 `commands` 为**空数组**，命令类检查全部 `NOT RUN`；以下所有结论均来自**只读读文件 + 与白名单内源码对照**，不采信 executor 的自述。

---

## 二、逐项复核结果（我复算到的，不是转述）

| 项 | 报告的判定 | 我的独立复核 | 我复核所依据的原始件 |
| --- | --- | --- | --- |
| S1 | 通过 | **成立**。`20-S1-content-debug-four-stage.xml` 中四行文本与报告引用**逐字一致**（含 `耗时45ms / 1ms / 1ms`、`4.阅读处理后 未执行 (书源调试链路止于 BookContent，未进入阅读处理)`）；四行不含正文/查询串/Cookie/令牌 | uiautomator dump（Grep 逐行读出） |
| S2 | 通过 | **成立**（两件四行齐全，1/2/3 为真实读数 107/85/81，第 4 行未执行）。仅报告对 `耗时` 标注的转写与 `21-…xml` 实际错位（见 F-5） | `21-…xml`、`22-S2b-…xml` |
| S3a | 部分通过 | **成立**。`1.提取后 网络错误 len=0,lines=0,imgs=0 (异常类型 ConnectException，消息默认不输出)` + 2/3/4 显式未执行，与报告逐字一致 | `23-S3-connect-refused.xml` |
| S3b/D-4 | 不满足措辞（未知错误） | **现象成立**。`IOException: unexpected end of stream` 归 `未知错误`；我按白名单源码复算了成因（顶层是普通 `IOException`，不在类型表内；消息无任何分类标记 → `UNKNOWN`），但报告的**根因描述有误**（见 F-4） | `60-ws-frames-all.log` 帧 #22；`DiagnosticClassifier.kt` |
| S4 | 部分通过（仅 `++<tocUrl>`） | **成立且机制可复算**：帧 #18—#21（`t+6.60s`）第 4 行为真实读数；同一会话只补一次由 `BookSourceDiagnosticsCollector.shouldReportReadingStage()` 的 CAS 保证；`--<绝对URL>` 不复现由 `Debug.kt:320-323`（`chapter.url = key`，即用户给的绝对 URL）+ `accepts()` 逐字比较解释，与设备实测章节 URL 为相对值 `/chapter.html` 冲突 → D-2 **属实** | 帧日志 + `61-logcat-…full.log` 23:11:17.399 + `BookSourceDiagnosticsCollector` / `Debug.kt` |
| S5 | 通过 | **窄口径成立、判定词过宽**：同会话负向（帧 #18—#22 读 Chapter two 零 `【诊断】`）+ 正向（帧 #28—#31 四行含真实第 4 行）**确实排除了"会话空转"解释**（负向在前，排除"绑定后首次阅读才出诊断"这一竞争假设）；但任务书 S5 的场景是"**别的书/本地书**"，那部分本轮是 NOT RUN（见 F-1） | 帧日志 23:30:23 会话 + `60-fixture-server.log` 23:30:27、23:31:03 |
| S6 | 通过 | **成立（双通道）**：`50-…png` 状态栏时钟 **23:12**，而 `61-logcat-…full.log` 在 23:11:33.242 → 23:12:52.691 之间零行；代码侧 `cancelDebug(true)` 清 `diagnostics` 后 `diagnosticsFor()` 恒为 null，零输出是必然 | 截图 + logcat + `Debug.kt:133-141`、`:472-478`、`ContentProcessor.kt:110` |
| S7 | 通过 | **成立且逐行可复算**：`recordFailure()` 把错误贴在"已执行且非 READ_PROCESS"的最后一层（本例前 3 阶段无快照 → 落到阶段一），`markNotExecutedIfMissing` 不覆盖已有真实读数 → 帧 #22 与 #25 的组合是代码的必然输出，非巧合 | 帧 #22—#25 + logcat 23:11:33.237（与帧 #22 **逐字相同**）+ `BookSourceDiagnosticsCollector.recordFailure` |
| S8 | 通过 | **成立**：`Debug.contentDebug` 先 `finishContentDiagnostics()` 再 `log(…, state=1000)`；`BookSourceDebugWebSocket.printLog` 把发送/取消/关闭放进同一个 `SerialTaskQueue` 任务 → 摘要必先于关闭。普通会话帧 #23—#26 摘要 → #27 完成帧 → #28 ping → 关闭，顺序吻合 | 帧日志 + `Debug.kt:430-434`、`BookSourceDebugWebSocket.kt:93-109`、`SerialTaskQueue.kt` |
| S9 | 通过 | **成立（我直接读三份 XML 的 content-desc，三者逐字节相同）**：`Chapter one&#10;　　CHECK chapter one.&#10;　　Second line.&#10; &#10;`；`check_parity.py` 比较的是**三份不同文件**（非同文件自证），逻辑有效 | `33-`/`40-`/`50-…xml` + `check_parity.py` |
| P1 四项阻塞 | 通过 | 代码层可逐条解释：①脱敏 `describeError` 只写类名；②`markNotExecutedIfMissing` + 会话存活补打；③`recordFailure` 排除 READ_PROCESS；④`SerialTaskQueue` 保序。与设备表现一致 | 白名单内四个生产文件 |

**装载项以外的旁证**：`62-install-raw.txt` = `Performing Streamed Install / Success`；`63-dumpsys-package-raw.txt` 第 824/826/903 行 = `versionCode=26092422`、`versionName=3.26.092422debug`、`firstInstallTime=2026-09-24 10:01:36`（未变）、`lastUpdateTime=2026-09-24 23:31:30`、声明权限为 `io.legado.app.tjf.debug.READ_WRITE`。`AndroidManifest.xml` 第 28/541/542 行静态核对为原名 `io.legado.READ_WRITE`，**无 `.debug.READ_WRITE` 残留**。夹具服务绑定 `("127.0.0.1", 8765)`（`serve_p1_fixture.py:111`），符合"只允许 127.0.0.1"的网络策略。

---

## 三、发现清单

### F-1【中｜判定词宽于任务书场景】S5 记"通过"，但任务书 S5 的场景（别的书/本地书）是 NOT RUN

- 落点：`10-execution.md` §1.4「S5 = **通过**」；`16-device-checks.md` S5 标题「**通过（同会话正/负对照…）**」。
- 任务书 S5 原文：`会话存活时读别的书或本地书 | 摘要里不出现别的书/本地书的第 4 阶段读数`。本轮实际对照的是"**同一本书的其它章节**"，"别的书/本地书"在 `10-execution.md` §6.2 明确记为 NOT RUN（设备上只有一本合成书、无本地书）。
- 具体误读场景：验收单只抄"P1 四项阻塞 + S5 通过"，把 S5 读成任务书全场景通过，而"本地书/别的书不串进摘要"这一条**从未在设备上验过**（`accepts()` 里 `boundSource != bookSourceUrl` 与 `bound.bookUrl != bookUrl` 两支只有 JVM 契约测试覆盖，而本轮测试未执行）。
- 建议：`S5 = 部分通过（仅同书其它章节的正/负对照）`；把"别的书/本地书"单列 NOT RUN 并写出判据不成立的原因（缺对照对象），与 S3/S4 的收窄写法保持一致。

### F-2【低｜报告自述与产物不符】`31-apk-parity.txt` 不是 UTF-8

- 落点：`10-execution.md` §3 表格（"`31-apk-parity.txt`，UTF-8"）与 §7 F-5 处置（"`31-apk-parity.txt` 改 UTF-8"）。
- 实测：该文件是 **UTF-16LE（BOM `FF FE`）**，用常规 UTF-8 工具打开仍是逐字符夹 NUL 的乱码——正是 F-5 抱怨的"不便复核"。`62-install-raw.txt`、`63-dumpsys-package-raw.txt` 同为 UTF-16LE（PowerShell 重定向默认编码）。
- 影响：**不影响结论**。parity 的**内容**我逐条读出并与报告一致：`entries A/B: 3621 3621`、`only_in_A/B: []`、`differing_entries: ['AndroidManifest.xml', 'META-INF/CERT.RSA', 'META-INF/CERT.SF', 'META-INF/MANIFEST.MF']`、23 个 `.dex` 且 `dex identical: True`。
- 建议：转存 UTF-8，或在报告里注明编码（原始 PowerShell 输出保持 UTF-16 也可，但要写清）。

### F-3【低｜报告自述与产物不符】证据目录里的 `ws_p1_debug.py` 没有墙钟代码

- 落点：`10-execution.md` §7 F-5 处置「WS 客户端每帧加墙钟（`ws_p1_debug.py` **已同步到证据目录**）」。
- 实测：证据目录里的 `ws_p1_debug.py` 只写 `t+{秒}s frame#{n}: …`（第 22-25、52-54 行），**没有任何墙钟**；但 `60-ws-frames-all.log` 的 23:29:40 / 23:30:23 两段每行都带 `[HH:MM:SS] ` 前缀，**连 `=== connect` / `opened` / `sent` 这些脚本自身的行也被加了前缀** → 该前缀必然来自外层工具或另一版未同步的客户端。
- 影响：墙钟证据本身存在于日志里（我据此把 S5 的两臂分别定位到 23:30:27 与 23:31:04，与 `60-fixture-server.log` 的两次 200 一致），所以结论不受影响；受影响的是"该格式可由这份脚本复现"这一可复核性声明。
- 建议：把实际使用的客户端版本同步进证据目录，或注明前缀由外层工具添加。

### F-4【低｜根因描述与源码不符】D-4 对 `DiagnosticClassifier` 的描述不准确，且 `EOFException` 实际归"编码错误"

- 落点：`10-execution.md` §5 D-4「`DiagnosticClassifier` 只按类型匹配 `SocketException/EOFException`，消息侧没有 `unexpected end of stream`/`broken pipe` 标记」。
- 源码实况（`DiagnosticClassifier.kt:81-99`）：类型匹配覆盖 `UnknownHostException / SocketTimeoutException / SocketException / InterruptedIOException / TimeoutException / CharacterCodingException / EOFException`，并且 **`EOFException -> ENCODING`（第 97 行）**，不是网络类；`InterruptedIOException -> NETWORK`（第 89 行），这解释了 WS 帧 #3（`异常类型 InterruptedIOException` → 网络错误）。
- 具体反例：同一个"服务端接受连接后不带状态行就关闭"（夹具 `reset` 模式 / 本次杀进程）失败，若 okhttp 把它以**裸 `EOFException`** 抛出会显示 `编码错误`，以**包装成 `IOException("unexpected end of stream")`** 抛出会显示 `未知错误`（本次实测），**两种都不是网络错误**。按报告描述去改的人很可能把 EOF 当成网络类，把"零网络故障"误判成已修。
- 建议：把 D-4 根因改写成"顶层异常类型不在类型表内、且只按消息标记判定，没有 cause 链匹配；`EOFException` 当前映射到编码类"。

### F-5【低｜时刻不可证】`90-S5-control-chapter-two.png` 被标为"刷新后"，但它早于该动作

- 落点：`16-device-checks.md` S5「截图：`90-S5-control-chapter-two.png`（**Chapter two 刷新后**）」。同文件 S2 的摘要转写也把耗时标注错位（`21-…xml` 实为 1:耗时2ms、2:耗时1ms、3:无；报告写成 1:耗时2ms、2:无、3:耗时1ms，后者是 `22-S2b-…xml` 的形态）。
- 实测：该 PNG 状态栏时钟为 **23:29**，负向动作在 **23:30:27**；画面里也没有阅读菜单/刷新按钮（`92-…png` 才有）。真正对应"Chapter two 刷新后"的是 `91-S5-control-chapter-two-refresh.xml`（内容确为 `CHECK chapter two`）。
- 说明：这与同文件 §S4 的 F-2 更正是同一类问题（用无法锚定时刻的截图佐证"此刻"），S5 的**实质证据**仍由原始帧 + 夹具日志承担（我独立核对成立）。
- 建议：`90-…png` 改标为"准备态（会话开始前阅读页停在 Chapter two）"，"刷新后"改引 `91-…xml`；并修正 S2 的耗时错位转写。

### F-6【低｜标注与自身更正冲突】S9 表把 `40-S4-…xml` 说成"补打发生的那一次阅读"

- 落点：`16-device-checks.md` S9 表第 2 行「会话存活、补打发生的那一次阅读 | `40-S4-reading-chapter-one.xml`」。
- 与 §S4 的 F-2 更正直接冲突：同一份 §S4 已写明该截图时钟 23:10、**早于** 23:11:17 的补打，"不能佐证此刻"；该 XML 无任何时间锚点，无法与补打绑定。
- 影响：S9 的**结论**成立（我逐个读出三份 XML 的 content-desc 完全相同）。建议把该行改为"会话存活期间的阅读页（时刻不可证）"。

### F-7【低｜观察类证据不足】D-3 的负向半场只有"帧静默"

- 落点：`10-execution.md` §5 D-3 与 `16-device-checks.md` S4「触发条件」。
- 事实：23:30:45 的"切章无补打"以 `60-ws-frames-all.log` 在 23:30:27 与 23:31:04 之间的**静默**为据；而"state 探针确认阅读页确实切到了 Chapter one"没有带时间锚点的产物（`state-probe.xml` / `35b/35c-reading-state.xml` 均无时钟）。静默无法区分"阅读重跑但未补打"与"阅读根本没重跑"——这正是第一轮 F-1 的同一类缺口。
- 建议：把 D-3 收窄为"该时刻无补打帧"，或补一张带时钟的截图。

### F-8【低｜加固】夹具脚本存在路径穿越入口（未发现被利用，不在本任务授权修复范围）

- `serve_p1_fixture.py:84`：`target = ROOT / path.lstrip("/")`，只去掉前导 `/`，未做 `..` 归一化或包含性校验 → `GET /../<任意文件>` 可越出夹具目录被当作响应体返回。仅 `127.0.0.1` + `adb reverse` 可达、目录内无凭据，故严重度低，但它落在"检查路径穿越"的范围内且报告未提。
- 建议（后续加固，非本任务）：`p = (ROOT / path.lstrip('/')).resolve(); if not p.is_relative_to(ROOT): 404`。

### F-9【低｜风险项确认，非缺陷】F-6 的"阶段四假读数"不是纯理论，代码路径可达

- `ContentProcessor.kt:116` 的哨兵分支 `if (content != "null")` **之后**，第 234 行 `diagnostics?.record(READ_PROCESS, text = contents…)` 与第 244 行 `Debug.onReadingStageRecorded()` 都是**无条件**执行。因此只要阅读路径把哨兵（`"null"`）或失败文案传进来，就会打出一条"正常"的第 4 行**并触发一次补打**。
- 旁证：`53-after-timeout.xml` 的 content-desc 是 `Chapter one / 获取正文失败 / timeout`——失败文案确实会作为正文渲染；本轮恰好因该会话 `toc` 被挂起 → `contentDebug` 未执行 → `target == null`（`accepts()` 恒 false）才没有触发（与 F-3 边界自洽）。
- 处置评价：报告把它记为"未验证风险点、不修、不宣称已验"是**诚实且够用**的（本任务不改代码）。建议把"代码可达性已确认"补进描述，别让下一个窗口当成纯理论。

---

## 四、我实际执行过的核对动作与输出

1. `00-task.md`、`10-execution.md`、`11-implementation-notes.md`、`16-device-checks.md`、`05-routing.md`、`06-review-commands.json` 全文读取；`06` 的 `commands` = `[]` → 命令类一律 NOT RUN。
2. `31-apk-parity.txt`：读出 `entries A/B: 3621 3621`、`only_in_A/B: []`、`differing_entries=[AndroidManifest.xml, META-INF/CERT.RSA, META-INF/CERT.SF, META-INF/MANIFEST.MF]`、`dex entries` 23 个、`dex identical: True`；同时判定其编码为 UTF-16LE（见 F-2）。
3. `62-install-raw.txt`（`Performing Streamed Install` / `Success`）、`63-dumpsys-package-raw.txt`（第 824/826/846/860/903 行：`versionCode=26092422`、`versionName=3.26.092422debug`、`lastUpdateTime=2026-09-24 23:31:30`、`declared permission=io.legado.app.tjf.debug.READ_WRITE`、`firstInstallTime=2026-09-24 10:01:36`）。
4. `60-ws-frames-all.log` 全文件逐段解析：分离出 11 段会话，核出 S4 段（#18—#21 @t+6.60s，与 logcat 23:11:17.399 **逐字相同**）、S7 段（#22—#25 @t+22.43s，第 4 行仍为真实读数；#26 为异常帧、socket 在 t+22.44s 关闭）、S5 段（23:30:23 会话：#18—#22 读 Chapter two 零诊断、#23—#31 读 Chapter one 四行补打）、S8 段（#23—#26 摘要 → #27 `︽正文页解析完成` → #28 ping → 关闭）。
5. `61-logcat-sourceDebug-full.log`：数出 **16 行** `【诊断】`（23:11:17、23:11:33、23:12:59、23:18:08 各 4 行）；23:11:17.399 与 23:11:33.237 两组与帧日志的对应行**逐字相同** → 设备侧与 PC 侧双通道互证 S4/S7。另发现 23:17:14.198 与 23:17:46.075 之间缺 `︽目录页解析完成` / `︾开始解析正文页` 两行（其余三处 toc 成功都有）：该次 toc 抓取更像**阅读链路**的取数（`Debug.tocDebug` 自己的两行不出现），而定论要看 `WebBook.kt`——该文件**不在白名单**，故这一解读我按"推定"处理，未据此改动任何结论。
6. `60-fixture-server.log`：`[23:11:11] hang-once consumed → holding-without-response`（会话被拉长）、`[23:12:45] server start pid=12700`（佐证"杀进程"）、`[23:30:27] GET /chapter2.html 200`、`[23:31:03] GET /chapter.html 200`（佐证 S5 两臂的阅读链路真的重新取数）。
7. `20/21/22/23b/23-S3-*.xml`：以 `-o` Grep 逐行读出四行摘要原文，与报告引用比对（S1、S3a 逐字一致）。
8. `33-/40-/50-reading-chapter-one*.xml`：读出三份 `content_text_view` 的 content-desc，**三者逐字节相同**；`check_parity.py` 读后确认比较对象是三个不同文件（非自证）。
9. 图片目视 3 张：`50-…png`（时钟 23:12，S6 窗口成立）、`92-…png`（时钟 23:31，阅读菜单/刷新按钮可见，正向臂成立）、`90-…png`（时钟 **23:29**，与报告标注不符 → F-5）。
10. 源码核对（全在白名单内）：`help/book/diagnostics/{BookSourceDiagnostics,DiagnosticClassifier,DiagnosticRedactor,DiagnosticStage,DiagnosticErrorClass}`、`model/Debug.kt`（`diagnosticsFor` / `bindDiagnosticsTarget` / `finishContentDiagnostics` / `onReadingStageRecorded` / `contentDebug` / `startDebug` 的 `--`、`++` 分支）、`help/book/ContentProcessor.kt`（110-112、116、226-245）、`web/socket/BookSourceDebugWebSocket.kt`、`utils/SerialTaskQueue.kt`。P1 四项阻塞与 S4 补打/只补一次/S7 归层/S8 保序均在此得到机制级解释。
11. `app/src/main/AndroidManifest.xml` Grep：第 28/541/542 行为 `io.legado.READ_WRITE`，无改名残留。
12. `serve_p1_fixture.py`、`ws_p1_debug.py`、`check_parity.py` 全文读取；`60-fixture-mode-at-end.txt` = `normal`；`71/72-my-page-*.xml` 的 Switch 节点 `checked="true"` → `checked="false"`，`80-current.xml` 亦为 false。

---

## 五、我没检查到的范围（诚实声明）

- **命令类全部 NOT RUN**（`06-review-commands.json` 为空数组）：无法重跑构建/安装/`logcat`/`check_parity.py`/`compare_apk.py`；**无法验证** `git diff -- app/src/main/AndroidManifest.xml` 为空、无法核对 APK sha256（`31381c41…`/`87aa25c9…`）、无法确认 `adb reverse`/`adb forward` 已移除、无法确认 PC 夹具进程已退出。凡是"某命令输出为空/无残留"的声明，我只做到了**静态内容核对**（如 manifest 三行仍是原名），不等同于 diff 为空。
- **白名单外源码未读**：`WebBook.kt`、`AnalyzeUrl.kt`、`AnalyzeRule.kt`、`BookContent.kt`、`ui/book/source/debug/**`、`WebService.kt`、`BookSourceController.kt`、`app/src/test/**` 等一律未读 → 阶段 1—3 的 `record()` 调用点、阅读链路是否把哨兵/失败文案送进 `ContentProcessor`、`--` 入口是否会做 URL 归一化、Web socket 的端口/握手细节，我只能基于白名单代码**推断**。特别地，F-9 所述"假读数可达"我确认的是**本函数的无条件记录路径**，未确认调用方是否会真的传入该文案。
- **未执行 JVM 契约测试**（测试源码不在白名单，且命令 NOT RUN）→ 跨书/本地书的隔离只有本轮 S5 的窄口径设备对照（见 F-1）。
- **未核对第一轮复审原文** `20-review.md` / `15-reviewer-raw.md`（白名单未列，且运行器会把本轮报告写入同名文件）→ 我对 reviewer-r001 的 F-1…F-6 只按 `10-execution.md` §7 的转述评估。
- **未读** `rounds/**`、`temp-manifests/**`、`temp-artifacts/**`、`.review-snapshot.json`（运行器内部件）；**未读** `app/build/p1-device-fixture/**`（白名单外）→ 夹具页面 HTML、`source.json`、`image.png` 的内容我未直接核对，"夹具不含敏感数据"这一点未经我验证。
- **设备状态还原只有部分可核**：可核到的是夹具 `mode.txt=normal`、Web 服务开关 `true→false`（`71→72`）。但 **S5 对照轮必然要求 Web 服务再次开启**，而该轮产物（`90`—`92`）之后**没有**"我的"页 dump/截图 → "开关已关闭还原"的**最终态**不可复核；同样，合成书源 `checkKeyWord` 的最终值、`adb reverse/forward` 的移除、浏览器端 Web 调试页、以及所有真实书源/语料/`bookshelf.json`/签名材料（白名单外）均未触及。
- 只目视了 3 张 png；其余截图未逐张看图（文本类证据已由 XML 覆盖）。
- 我未运行任何会改变工作树或设备状态的命令，未发起任何网络请求，未写任何文件。

---

## 六、给验收（dsh）的建议

1. 抄写结论时，**S5 按"部分通过（仅同书其它章节）"记录**，与 §6.2 的 NOT RUN 并列，不要把 S5 读成覆盖任务书全场景（F-1）。
2. S4/S3 的收窄口径（`++<tocUrl>` 入口 / 仅"连接被拒"子场景）**成立**，可直接采用；D-2、D-3、F-6 按报告建议交 controller 处置，其中 D-2 建议按"会话键与章节 URL 规范化匹配"立项（我在源码侧补了一条同类反例：`accepts()` 还要求 `book.origin` 与 `bookSource.bookSourceUrl` **逐字相同**，用户改过书源 URL 的存量书会同样静默不补打）。
3. 把 F-2/F-3 的两处"整改声明未落地"在验收单里更正（parity 编码、ws 客户端墙钟），否则下一个窗口会按错误前提复核。
4. 设备侧最终态建议补一张收尾截图（Web 服务开关）并在验收单注明时刻；`31-`/`62-`/`63-` 建议转存 UTF-8 或标注编码。
5. 本次复审为**只读静态复审**：所有设备证据我均以其**原始件**为准复核，未采信转写；P1 是否通过应由独立 acceptor 在设备/构建层面另行确认（本报告不构成验收）。
