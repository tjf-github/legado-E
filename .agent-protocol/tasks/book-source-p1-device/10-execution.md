# book-source-p1-device — 执行报告（executor: dsh，第 2 版，按 reviewer-r001 的 F-1…F-5 更正）

状态：`完成（设备侧）`　结论：`P1 的设备端表现与 r3 修复后的预期一致；S4/S5 的判定词已按 F-1/F-4 收窄或重做`

- 运行实例 / 提供方：dsh（本窗口；mode=`dsh-claude`，route_revision=8）。执行由 controller 直接完成，**没有** runner 封套
  （与 P1 前两轮同一结构缺口，如实记录，不冒充已绑定）。
- 实际读取：`00-task.md`/`05-routing.md`/`06-review-commands.json`、`book-source-p1-rework-r3/30-acceptance.md`、
  `book-source-p1-rework-r3/10-execution.md` `8、`book-source-p1-diagnostics/16-device-checks.md`、`AGENTS.md`、
  `.agent-protocol/{README,MODES,CLOSEOUT,ROLES}.md`、`app/src/main/java/io/legado/app/help/book/diagnostics/**`、
  `model/Debug.kt`、`help/book/ContentProcessor.kt`、`web/socket/BookSourceDebugWebSocket.kt`、`utils/SerialTaskQueue.kt`、
  `ui/book/source/debug/*`、`ui/book/source/manage/BookSourceAdapter.kt`、`service/WebService.kt`、
  `api/controller/BookSourceController.kt`、`app/src/main/AndroidManifest.xml`、`app/build.gradle`。
- 实际修改（全部在工作树内、与 P1 业务改动无关）：
  - `app/src/main/AndroidManifest.xml`：**临时**把自定义权限 `io.legado.READ_WRITE` 改名为
    `io.legado.app.tjf.debug.READ_WRITE`（4 行），构建装包后**已还原**，`git diff -- app/src/main/AndroidManifest.xml` 为空（见 `4）；
  - `app/build/p1-device-fixture/**`：合成夹具与 PC 侧脚本（`serve_p1_fixture.py`、`ws_p1_debug.py`、`p1dev.py`、
    `p1api.py`、`check_parity.py`、`compare_apk.py`），已被 .gitignore 忽略；
  - 本任务目录证据文件。
- 未触碰：任何生产业务代码、测试、构建脚本（除上面 4 行临时改动）、`.agent-protocol` 协议稳定文件、其它任务目录原文、
  真实书源/语料/`bookshelf.json`、签名材料与凭据。

**第 2 版改动摘要（对应 reviewer-r001 的发现）**：S5 推翻第 1 版证据并按最小对照重做（F-1）；
`hang-once` 的作用描述与 S4 截图措辞更正（F-2）；补记 `contentDebug` 之前的失败不打摘要的边界（F-3）；
S3/S4 判定词收窄（F-4）；补原始安装/dumpsys 件与 UTF-8 parity 文件（F-5）；F-6 登记为未验证风险点。

---

## 1. 结论先行

1. **被测二进制**：由当前工作树（HEAD `b8ba92e19` + P1 第三轮返工的未提交改动）编译，`:app:assembleAppDebug` 成功，
   `legado_app_3.26.092422.apk`（37693471 B，sha256 `31381c41…0a8cff`，锁定 `-PVERSION=3.26.092422 -PVERSION_CODE=26092422`）。
2. **安装**：`adb install -r` 成功（原始输出 `62-install-raw.txt`），`firstInstallTime` 未变（数据保留）；首次尝试被
   `INSTALL_FAILED_DUPLICATE_PERMISSION` 拒绝，按任务书 `「构建期临时改名的必要性」处理，见 `4。
3. **P1 四项阻塞 + F-03 的设备端表现**（逐项见 `16-device-checks.md`）：
   - 默认摘要不泄漏正文/异常 message：**通过**（S3a、S7 三处异常说明都只写异常类名）；
   - 阶段四不再被收尾覆盖、会话存活时可补打真实读数：**通过**（S4 帧 #18—#21 + logcat 23:11:17）；
   - 抓取失败不污染阶段四：**通过**（S7 帧 #22—#25）；
   - Web 端摘要先于终止信号到达：**通过**（S8）；
   - 目录入口/完整链路下阶段 1/2 不再静默丢失：**通过**（S2）。
4. **判定词收窄的两项**：S4 = **部分通过**（只在 `++<tocUrl>` 入口、绑定值与章节存储 URL 逐字相同时复现；`--<绝对URL>` 入口不复现，见 D-2）：
   S3 = **部分通过**（"连接被拒"子场景通过；"响应被中途打断"子场景仍是 `未知错误`，见 D-4）。
   S5 = **通过**（第 2 版按 reviewer 建议的同会话正/负对照重做，见 `16-device-checks.md ` S5）。
5. **NOT RUN**：本地 TXT/EPUB 与别的书的隔离对照、Android 调试页渠道的补打窗口、`@webjs:`、音频/视频/文件类书源、多页章、
   真实书源与真实站点、`:app:lintAppDebug`、浏览器端 Web 调试页。清单见 `6。
6. 设备轮新增发现 4 条（D-1…D-4）与 1 条未验证风险点（F-6），均不改变 P1 的另一面结论，但 D-1/D-2 需要 controller 处置，见 `5。

---

## 2. 方法与窗口修正（依据 F-0003）

- Android 书源调试页的会话在 Activity finish 时经 `onCleared()` → `Debug.cancelDebug(true)` 结束，
  「保留调试页 → 切到书架阅读」在真机上不可能成立，原 7 步里的第 2/5 步（补打与失败对照）无法在该渠道复现。
- 本轮改用两条入口：
  1. **Android 调试页渠道**（用户可见渠道）——覆盖 S1/S2/S3/S6 的摘要可见性与脱敏；
  2. **Web 端调试 socket**（`/bookSourceDebug`，不经 Activity，会话在 socket 存活期一直有效）——覆盖 S4/S5/S7/S8。
- **窗口机制（已按 F-2 更正）**：夹具服务的 `hang-once:<path>` 只把**调试链路自己**那次正文抓取停在空中，
  把会话存活窗口拉到约 60s；该窗口内**没有**第二次成功的正文抓取，阅读链路读的是**本地缓存**——
  所以 `hang-once` 不制造"并发抓取窗口"，它只保证调试抓取在会话存活期内一直处于飞行状态。
- 两条入口走的是同一套产品代码（`Debug.startDebug` → `WebBook.getContent` → `ContentProcessor`），差别只在
  `Debug.Callback` 的实现与投递通道。S1 的四行摘要在 Activity 渠道截图留证；S4 的补打在 Web 端渠道留原始帧 + logcat 双证。

---

## 3. 命令证据（关键命令与结果）

| 命令 | 结果 |
| --- | --- |
| `$env:GRADLE_USER_HOME='D:\gradle_home'; .\gradlew.bat :app:assembleAppDebug '-PVERSION=3.26.092422' '-PVERSION_CODE=26092422'` | `BUILD SUCCESSFUL`（首次因 `D:\gradle_home\…zip.lck 拒绝访问` 被沙箱拒绝，按任务书授权提权重跑成功；还原 manifest 后同参数再构建一次也 `BUILD SUCCESSFUL`） |
| `adb -s 10CE5P1M1Z001P9 install -r <apk>` | 第 1、2 次 `Failure [INSTALL_FAILED_DUPLICATE_PERMISSION …io.legado.app.tjf.release]`；临时改名后 `Success`（原始输出 `62-install-raw.txt`，S5 对照轮再次 `Success`） |
| `adb -s … shell dumpsys package io.legado.app.tjf.debug` | `versionCode=26092422 versionName=3.26.092422debug`，`firstInstallTime=2026-09-24 10:01:36`（未变）；原始输出 `63-dumpsys-package-raw.txt` |
| `adb -s … reverse tcp:8765 tcp:8765` + PC 侧 `python serve_p1_fixture.py` | 设备可访问合成夹具（服务端日志 `60-fixture-server.log`） |
| `adb -s … forward tcp:1122/1123` | App Web 服务开启后 `GET /getBookSources` 返回 200；WS `/bookSourceDebug` 握手成功 |
| `python ws_p1_debug.py "++http://127.0.0.1:8765/toc.html"` | 原始帧序见 `60-ws-frames-all.log`（S4/S5/S7/S8 证据；每帧带客户端墙钟） |
| `adb -s … logcat -d -v time -s sourceDebug` | 16 行 `【诊断】`，时间戳独立佐证 S4/S7（`61-logcat-sourceDebug-full.log`） |
| `python check_parity.py` | `parity(all three equal): True`（S9 正文一致性） |
| `python compare_apk.py` | 冻结树产物 vs 被测包：3621 条目，**只有** `AndroidManifest.xml` 与 `META-INF/{CERT.RSA,CERT.SF,MANIFEST.MF}` 不同；23 个 dex 全部逐字节相同（`31-apk-parity.txt`，UTF-8） |

---

## 4. 偏差与处置（必须读）

### 4.1 安装被 `INSTALL_FAILED_DUPLICATE_PERMISSION` 拒绝（D-1）

```text
Failure [INSTALL_FAILED_DUPLICATE_PERMISSION: Package io.legado.app.tjf.debug attempting to
         redeclare permission io.legado.READ_WRITE already owned by io.legado.app.tjf.release]
```

- 实测：`io.legado.READ_WRITE` 由设备上已存在的 `io.legado.app.tjf.release` 持有（`dumpsys package permissions`），
  该包与本工作树产物签名不同（`93:A2:84…` vs `BE:A4:23…`）；设备上还装有同证书的 `io.legado.app.tjf`，
  因此卸载单个 `.tjf.release` 也释放不了占用，且卸载用户现装包不在授权范围内。
- 处置：沿用 `.agent-protocol/tasks/ai-stage-e-device/00-task.md` 第 51 行的先例（该任务已由 claude reviewer 复审通过），
  **临时**把自定义权限改名为 `io.legado.app.tjf.debug.READ_WRITE`（manifest 4 行），构建装包，装完立即还原并核对
  `git diff -- app/src/main/AndroidManifest.xml` 为空（独立复审也用 Grep 复核过无改名残留，见 `20-review.md` `2.1）。
- 对结论的影响与证明：改名只影响这个**测试包 provider 的权限名**（设备上其它包零影响），
  被测代码与冻结工作树产物**逐字节相同**——`31-apk-parity.txt` 显示 3621 条目中仅 `AndroidManifest.xml` 与 3 个签名文件不同，
  23 个 `.dex` 全部相同；被测包 SHA-256 = `31381c41…0a8cff`（留存副本 `app/build/p1-device-apk-B-renamed.apk`）。

### 4.2 其它偏差

- 设备输入法会把 ASCII 标点转全角，无法用 `adb shell input text` 敲 `--<url>` 调试键（P1 首轮已记录）。
  本轮改为：把调试键写进合成书源的 `ruleSearch.checkKeyWord`（经 App 自带 Web API `/saveBookSource`），
  再用调试页的「我的」快捷按钮提交——提交的字符串与手敲完全一致（屏幕上可见），不是改产品行为。
- 需要打开 App 的「Web 服务」开关（`我的` → `Web 服务`）才能用 Web API 与 WebSocket 调试；**收尾时已关闭还原**。
- 被测包是更新安装（`install -r`），设备上原有的合成书源/书架数据保留；`checkKeyWord` 在收尾时改回
  `--http://127.0.0.1:8765/chapter.html`。

---

## 5. 设备轮发现与未验证风险（不自行修复，交 controller）

| # | 发现 | 严重度 | 证据与说明 |
| --- | --- | --- | --- |
| D-1 | 本机安装任何本仓库产物都会被 `INSTALL_FAILED_DUPLICATE_PERMISSION` 拒绝（设备上存在异签名的 `io.legado.app.tjf.release` 占用 `io.legado.READ_WRITE`） | 环境事实（中，影响所有真机轮） | `4.1；建议登记进《发布推送流程书》或《维护计划书》，避免下个窗口误判为构建缺陷 |
| D-2 | **阶段四补打按章节 URL 字符串精确匹配**：书架书的章节 URL 是相对值（`/chapter.html`），用 `--<绝对URL>` 作会话键时，会话存活期阅读同一章**不会**补打 | 中（限制功能可达性，不是错误读数） | 23:07—23:08 会话（键 `--http://127.0.0.1:8765/chapter.html`）内阅读 Chapter one 无补打；23:11 与 23:30 两次用 `++<tocUrl>` 键（绑定 `/chapter.html`）同场景均补打成功（后者为 S5 的正向对照） |
| D-3 | 补打依赖阅读链路**真的重新执行** `ContentProcessor.getContent`：同一会话内「切回刚读过的章」不触发补打，而对该章点「刷新」立即补打 | 低（有同会话对照） | 23:30:45 切章无补打 / 23:31:04 刷新有补打（同一接收端、同一章）。第 1 版把 23:14 的同类观察归因为"未定"，现按 F-1 更正：那次会话根本没绑定目标；本条只保留 23:30 的同会话对照 |
| D-4 | 连接被接受但响应不完整的失败仍分类为 `未知错误`（`IOException: unexpected end of stream`） | 低（沿用首轮 D-2，未修） | S7 帧 #22；`DiagnosticClassifier` 只按类型匹配 `SocketException/EOFException`，消息侧没有 `unexpected end of stream`/`broken pipe` 标记 |
| F-6 | **未验证风险点**：`ContentProcessor.getContent` 末尾无条件记录阶段四，`error == null` 时非空文本一律判 `正常`；若阅读路径把"未下载哨兵/获取失败文案"也送进本函数，阶段四会打出一条"正常"读数 | 低（本轮未触发） | reviewer-r001 ` F-6；设备侧只有间接迹象（`53-after-timeout.xml` 显示失败文案按正文渲染）。本轮**没有**"绑定会话 + 阅读自身抓取失败"的组合，建议后续补一次 |

边界补充（F-3）：**链路在 `contentDebug` 之前失败（目录页/详情页阶段）时，一行四阶段摘要都不会打印**——
接收端只在 `contentDebug` 里绑定，摘要打印只挂在 `contentDebug` 的成功/失败与阅读路径的补打上。实测两例见 `16-device-checks.md ` S3 边界。

---

## 6. NOT RUN（不要读作已验收）

1. **Android 调试页渠道的「补打」同屏证据**：需要同时保留调试页与阅读页，而 finish 即结束会话（F-0003），本轮只在 Web 端渠道取得补打证据。
2. **本地 TXT/EPUB 与「别的书」的隔离对照**：设备上只有一本合成书、没有本地书，缺少对照对象；跨书隔离仍只有 JVM 契约测试（本轮未执行测试）。
3. **真实书源与真实站点**：本轮全部输入为合成夹具，未读取任何真实书源 JSON/ZIP、`docs/book-source-audit/语料/**`、`bookshelf.json`。
4. **音频/视频/文件类书源、多页章（`nextContentUrl`）、`@webjs:`**：未跑。
5. **`:app:lintAppDebug`**：非发布轮，未跑（本轮也没有改业务代码，门禁沿用 r3 的 `compile+test` 结论）。
6. **Web 端在真实浏览器里的表现**：本轮用 PC 侧 WebSocket 客户端直连 `/bookSourceDebug`，未验证浏览器端页面。
7. **其它 `Debug.Callback` 实现**（`RssSourceDebugWebSocket` 等）：未纳入、未改（P1 遗留 F-0004）。
8. **F-6 的组合场景**（绑定会话内让阅读自身的抓取失败）：未跑。

---

## 7. reviewer-r001（第一轮独立复审）findings 处置

复审结论：**有阻塞项（证据层）**，产品层结论未被推翻；原文 `20-review.md`，回执 `15-reviewer-raw.md`（sha256 `d9993e9c…`）。

| Finding | 处置 | 落点 |
| --- | --- | --- |
| F-1【阻塞｜证据缺口】S5 引用的会话从未绑定诊断目标（toc 抓取被挂起 → 未进 `contentDebug` → `target == null`），"读别的章没有诊断行"因此不成立；D-3 的前提同源 | **采纳并重做**：S5 按最小对照重做（同会话负向：刷新 Chapter two → 零 `【诊断】`；正向对照：刷新绑定章 Chapter one → 四行补打）；D-3 改按 23:30 的同会话对照重述 | `16-device-checks.md ` S5、S4 触发条件；本文件 `5 D-3 |
| F-2【中】`hang-once` 的作用描述被夹具日志否证（窗口内没有第二次成功的 `/chapter.html` 抓取，阅读读的是缓存）；`40-S4-…png` 的"此刻"与补打时刻不符（23:10 vs 23:11:17） | **采纳并更正**表述：`hang-once` 只让调试抓取停在飞行中；时刻证据改用 logcat + 原始帧，截图措辞删除 | `2、`16-device-checks.md ` S4 |
| F-3【低】链路在 `contentDebug` 之前失败时不打任何摘要，报告未记录 | **采纳**：补边界说明 | `5 边界补充、`16-device-checks.md ` S3 边界 |
| F-4【低】S4 记"通过"偏宽；S3 措辞与 D-4 未闭合 | **采纳**：S4 改"部分通过（仅 `++<tocUrl>` 入口）"；S3 改"部分通过（仅连接被拒子场景）"并点名 D-4 | `1.4、`16-device-checks.md ` S3/S4 |
| F-5【低】安装/dumpsys 只有转写、parity 文件编码不便复核、帧日志缺会话边界 | **部分采纳**：补原始件 `62-install-raw.txt`/`63-dumpsys-package-raw.txt`、`31-apk-parity.txt` 改 UTF-8、WS 客户端每帧加墙钟（`ws_p1_debug.py` 已同步到证据目录）；帧日志仍是多次运行拼接（保留原始形态，未改写） | 证据目录 |
| F-6【低】阶段四对"非正文输入"不设防（潜在假读数，本轮未触发） | **记录为未验证风险点**，不修、不宣称已验 | `5 F-6 |

复审指出的其它"未检查范围"（命令类全部 NOT RUN、白名单外源码未读、前几轮证据引用无法核对）本轮**不反驳**：
本任务 `06-review-commands.json` 为空数组，命令类检查本来就是 NOT RUN；这一限制在验收单里同样披露。

---

## 8. 交接与建议

1. 本报告**不构成验收**：第二轮独立复审与验收（acceptor=dsh）在复审之后进行。
2. 建议 controller 在 P1 收口时把 D-2 转成明确后续项（在会话键与章节 URL 之间做规范化匹配，或在调试页明示「按 URL 精确匹配」），
   D-1 登记为环境事实（影响所有真机轮），D-3 与 F-6 作为观察/风险项留待有控制实验时再定性。
3. 提交/推送/发布：本任务**未授权、未执行**；工作树改动仍全部未提交。
