# book-source-p1-device — 真机逐项证据（executor: dsh，第 2 版，按 reviewer-r001 的 F-1…F-5 更正）

本文件只放**设备侧原始结果**与逐项判定；总览与偏差见 `10-execution.md`，构建/安装偏差见 `11-implementation-notes.md`。
所有行都可在 `33-device-evidence/` 的对应 png/xml/log 里逐字复核；本文件不替代原始证据。
**第 2 版相对第 1 版的改动**：S5 改判并补做同会话正/负对照（F-1）；更正 `hang-once` 的实际作用与 S4 截图的时刻措辞（F-2）；
补记「链路在 `contentDebug` 之前失败则无任何摘要」的边界（F-3）；S3/S4 判定词收窄（F-4）；补原始安装/dumpsys 件（F-5）。

- 设备：`10CE5P1M1Z001P9`（vivo，model `V2338A`/PD2338，USB，`adb devices` = `device`）
- 被测包：`io.legado.app.tjf.debug`（versionName `3.26.092422debug`，versionCode `26092422`；原始 dumpsys 见 `63-dumpsys-package-raw.txt`）
- 合成夹具：`app/build/p1-device-fixture/`，PC 侧 `serve_p1_fixture.py` 服务 `127.0.0.1:8765`，经 `adb reverse` 给设备；
  合成书源 `http://127.0.0.1:8765/`（name `P1 synthetic device check`），书架书 `Synthetic book`（两章）。
- 设备端章节 URL 实测（App 自带 Web API `/getChapterList`）：`/chapter.html`、`/chapter2.html`（**相对 URL**，不是绝对地址）；
  `book.origin = http://127.0.0.1:8765/`，`book.bookUrl = http://127.0.0.1:8765/book.html`。

## S1 调试页 `--<正文页URL>` 的四阶段摘要（Android 调试页渠道）——**通过**

入口：我的 → 书源管理 → 行内「更多菜单」→ 调试 → 快捷按钮 `text_my`（其内容为书源的 `ruleSearch.checkKeyWord`，
当前值 `--http://127.0.0.1:8765/chapter.html`）。设备实显（`20-S1-content-debug-four-stage.xml` 原文）：

```text
[00:00.001] ⇒开始访正文页:http://127.0.0.1:8765/chapter.html
[00:00.002] ︾开始解析正文页
[00:00.050] ≡获取成功:http://127.0.0.1:8765/chapter.html
[00:00.102] ┌获取章节名称
[00:00.102] └调试
[00:00.103] ┌获取正文内容
[00:00.103] └
　　CHECK chapter one.
　　Second line.
　　<img src="http://127.0.0.1:8765/image.png">
【诊断】1.提取后 正常 len=107,lines=4,imgs=1 耗时45ms
【诊断】2.HTML格式化后 正常 len=85,lines=3,imgs=1 耗时1ms
【诊断】3.书源替换后 正常 len=81,lines=3,imgs=1 耗时1ms
【诊断】4.阅读处理后 未执行 (书源调试链路止于 BookContent，未进入阅读处理)
[00:00.094] ︽正文页解析完成
```

判定：四行齐全；阶段 1/2/3 为真实读数（107 = `<p>SYNTHETIC chapter one.</p><p>Second line.</p><img …>` 文本化结果，
85 = 格式化后，81 = 应用 `replaceRegex` 后）；阶段 4 显式「未执行」而非伪造读数；四行**不含**正文、查询串、Cookie、令牌。
注意：同一屏上方由**既有调试功能**打印的正文与图片标签不是本次诊断新增（P1 之前就有）。

## S2 目录入口与完整链路——**通过**

- `++<tocUrl>` 入口（`21-S2-toc-entry-four-stage.xml`）：`⇒开始访目录页` → 目录解析成功 → 正文页解析，四行摘要与 S1 同值
  （`1.…耗时2ms` / `2.…耗时1ms` / `3.…（无耗时输出）` / `4.未执行`，逐字见 `21-S2-toc-entry-four-stage.xml`）。
  首轮复审指出的「目录入口下阶段 1/2 静默丢失」未复现。（更正 reviewer-r002 F-5：此处耗时标注曾错位，已按原始 XML 重抄。）
- 完整链路 搜索(`abc`)→详情→目录→正文（`22-S2b-full-chain-four-stage.xml`，需向下滚动才可见摘要）：
  `1.提取后 正常 len=107,lines=4,imgs=1 耗时2ms` / `2.HTML格式化后 正常 len=85,lines=3,imgs=1` /
  `3.书源替换后 正常 len=81,lines=3,imgs=1 耗时1ms` / `4.未执行`。
- 服务端日志佐证（`60-fixture-server.log`）：`GET /search.html` → `GET /book.html` → `GET /toc.html` → `GET /chapter.html`。

## S3 抓取失败的错误分类与脱敏——**部分通过（仅"连接被拒"子场景；见 F-4）**

子场景 a（PC 侧无监听 → 连接被拒，`23-S3-connect-refused.xml` 原文）——**通过**：

```text
【诊断】1.提取后 网络错误 len=0,lines=0,imgs=0 (异常类型 ConnectException，消息默认不输出)
【诊断】2.HTML格式化后 未执行 (本次调试链路未执行该阶段)
【诊断】3.书源替换后 未执行 (本次调试链路未执行该阶段)
【诊断】4.阅读处理后 未执行 (书源调试链路止于 BookContent，未进入阅读处理)
```

判定：失败贴在链路实际停住的那一层；其余阶段显式「未执行」；异常说明**只写异常类名**。
与 P1 首轮真机证据（当时显示 `(Failed to connect to /127.0.0.1:8765)`）对照，r3 的脱敏修正在设备上生效。

子场景 b（连接被接受但响应不完整，`IOException: unexpected end of stream`）——**不满足本项措辞**：实测分类为
`未知错误`（见 S7 帧 #22），即 P1 首轮设备轮登记的 D-2 在本轮**仍然存在**（不在第三轮返工范围内，如实记录，不与 D-4 混为一谈）。

### S3 的边界（F-3，本轮新记录）

**失败发生在 `contentDebug` 之前（目录页/详情页阶段）时，一行四阶段摘要都不会打印**：诊断接收端只在
`Debug.contentDebug()` 里 `bindDiagnosticsTarget(...)`，而摘要打印只挂在 `contentDebug` 的 `onSuccess`/`onError` 与
阅读路径的补打上。实测两例：`60-ws-frames-all.log` 中 `timeout=45.0s/90.0s` 的两段会话（`23:15:33`、`23:16:09` 的
`GET /toc.html mode=hang`）在 toc 阶段停住，全程只有 `⇒开始访目录页`/`︾开始解析目录页` 两帧，没有任何 `【诊断】` 行；
logcat `61-logcat-sourceDebug-full.log` 同刻也无诊断行。因此本项与 S1/S2 的「四行齐全」都只对**正文抓取阶段及之后**的链路成立。

## S4 会话存活期间读同一书源同一本书同一章——**部分通过（仅 `++<tocUrl>` 入口绑定相对 URL 时可复现；Web 端渠道）**

窗口构造：不用 Android 调试页（finish 即 `onCleared()` → `cancelDebug(true)`，见 F-0003），改用 Web 端调试 socket
（`BookSourceDebugWebSocket`，不经 Activity）；用夹具服务的 `hang-once:<path>` 让**调试链路自己**的正文抓取一直停在飞行状态，
从而把会话存活窗口拉长到约 60s（App 侧读超时）。

**更正（F-2）**：`hang-once` 并不会让"同 URL 的阅读链路抓取"成功——实测该窗口内**没有**第二次成功的 `/chapter.html` 抓取
（`60-fixture-server.log` 显示被消费的那一次之后一直是挂起），阅读链路读的是**本地缓存的章节正文**。
`hang-once` 的真实作用是「只把调试链路自己那次抓取停在空中」，不是"制造并发抓取窗口"。

会话帧（`60-ws-frames-all.log`，2026-09-24 23:11 那一段；`t+` 为客户端收到时刻）：

```text
t+0.09s frame#17: [00:00.041] ︾开始解析正文页
t+6.60s frame#18: 【诊断】1.提取后 未执行 (本次调试链路未执行该阶段)
t+6.60s frame#19: 【诊断】2.HTML格式化后 未执行 (本次调试链路未执行该阶段)
t+6.60s frame#20: 【诊断】3.书源替换后 未执行 (本次调试链路未执行该阶段)
t+6.60s frame#21: 【诊断】4.阅读处理后 正常 len=81,lines=3,imgs=1 耗时1ms
```

- 帧 #18—#21 就是「补打」：会话仍存活、本次会话第一次拿到**真实**阶段四读数时补一次完整四行；同一会话只补一次。
- App 侧 logcat 独立记录（`61-logcat-sourceDebug-full.log`）：`09-24 23:11:17.399 … 【诊断】4.阅读处理后 正常 len=81,lines=3,imgs=1 耗时1ms`。
- 时刻证据以 logcat + 原始帧为准。第 1 版曾用 `40-S4-reading-chapter-one.png` 说"此刻阅读页显示 Chapter one"——
  该截图状态栏时钟是 23:10，早于补打的 23:11:17，**不能**佐证"此刻"（F-2）；该截图只在"该章此前已被读过"的意义上参考。
- 会话身份绑定：本轮的绑定值是 `++<tocUrl>` 入口解析出的章节链接 `/chapter.html`（与书架里该章存储的 URL 逐字相同）。

**收窄**：同一场景用 `--http://127.0.0.1:8765/chapter.html`（绝对 URL）作会话键时，会话存活期内阅读同一章**没有**补打
（见 D-2）。因此本项判定为**部分通过**：它只在"绑定值与被阅读章节的存储 URL 逐字相同"的入口下复现。
Android 调试页渠道的同项 **NOT RUN**（F-0003 窗口在真机不可复现）。

### S4 的触发条件（第 2 版新增观察，附同会话对照）

补打依赖阅读链路**真的重新执行** `ContentProcessor.getContent`。同一会话（23:30:23—23:31:23，绑定 `/chapter.html`）内：

- `23:30:45` 用目录抽屉从 Chapter two **切回** Chapter one：`state` 探针确认阅读页确实切到了 Chapter one，
  但**没有**补打帧；
- `23:31:04` 对同一章点阅读菜单的「**刷新**」：立即出现四行补打（`t+40.26s frame#28—#31`，第 4 行 `正常 len=81,lines=3,imgs=1 耗时1ms`）。

即：本次会话里“切章那一刻没有补打帧”、而“刷新同一章立即出现补打”。**收窄（reviewer-r002 F-7）**：切章那一半只有帧静默作为依据，`state` 探针产物不带时钟，不能断言“阅读一定重跑了处理但没补打”；可断言的是“该时刻没有补打帧”。该观察登记为 D-3（低），不再作为"会话空转"的解释（第 1 版的解释已被 F-1 推翻）。

## S5 会话存活期间读别的章节——**部分通过（仅“同一本书的其它章节”；任务书写的“别的书/本地书”仍是 NOT RUN，见 reviewer-r002 F-1）**

第 1 版把 `23:15:33`/`23:16:09` 两段"只有 2 帧"的会话当成隔离证据——**该证据不成立**（reviewer-r001 F-1）：
那两次的 `GET /toc.html` 本身被 `hang` 挂起，`tocDebug.onSuccess` 没跑，`contentDebug` 从未调用，
`bindDiagnosticsTarget` 从未执行 → 接收端 `target == null` → `accepts()` 恒 false → 任何章节都不会有诊断行。
第 2 版按 reviewer 建议的最小对照重做，一次会话内同时给出**负向**与**正向**：

- 准备：阅读页停在 **Chapter two**（非绑定章）；`mode.txt = hang-once:/chapter.html`；会话键 `++http://127.0.0.1:8765/toc.html`。
- 会话起于 `23:30:23`：`[23:30:23] GET /toc.html … -> 200 137B`（toc 解析成功 → **确实绑定**了 `/chapter.html`），
  随后 `[23:30:23] GET /chapter.html … consumed … holding-without-response`（调试链路自己的抓取停在空中，会话保持存活）。
- **负向**（`23:30:27`，`t+3.5s`）：对另一章 **Chapter two** 点「刷新」→ 阅读链路真实重启了该章的取数与处理，
  同一 socket 上能看到**阅读链路自己的**调试行 `≡获取成功:http://127.0.0.1:8765/chapter2.html`、`└Chapter two`（帧 #18—#22），
  但**没有一行 `【诊断】`**。
- **正向对照**（`23:31:04`，`t+40.3s`，同一会话、同一接收端）：对**绑定章** Chapter one 点「刷新」→
  `frame#28—#31` 立即出现四行摘要，第 4 行 `正常 len=81,lines=3,imgs=1 耗时1ms`。

判据成立的理由：正向对照证明该接收端在整段窗口里**已绑定且存活**，因此负向窗口里的"零 `【诊断】`"不能被解释成"会话空转"；
两个动作走的是同一条阅读链路（同一次「刷新」交互）。截图与产物：`90-S5-control-chapter-two.png`（**准备态**：会话开始前阅读页停在 Chapter two，状态栏时钟 23:29，早于负向动作 23:30:27，不佐证刷新后）、`91-S5-control-chapter-two-refresh.xml`（Chapter two 刷新后的阅读页内容）、`92-S5-control-chapter-one-refresh.png`（时钟 23:31，刷新按钮可见）。
（更正 reviewer-r002 F-5：曾把 `90-…png` 标成“Chapter two 刷新后”，不成立。）原始帧与夹具日志：`60-ws-frames-all.log`、`60-fixture-server.log`。

**局限（不夸大）**：本轮只覆盖"同一本书的其它章节"。**本地 TXT/EPUB 与别的书源的书的隔离对照 = NOT RUN**
（设备上只有这一本合成书、没有本地书，缺对照对象）；跨书隔离的单元级证据仍只有 JVM 契约测试，本轮未执行测试。

## S6 会话结束后阅读：零诊断输出——**通过**

在 WS 会话已关闭（socket 于 23:11:33 关闭）之后，于 23:12:0x—23:12:2x 反复切章阅读同一章与另一章：

- logcat 过滤 `sourceDebug` 的增量 = **0 行**（无任何 `【诊断】` 行、也无其它 sourceDebug 输出）；
- 阅读页正文正常（`50-S6-reading-after-session-closed.xml`，与 S9 基线逐字相同）；
- 该窗口内夹具服务是**停止**状态，阅读仍正常完成 → 说明阅读走的是本地缓存，诊断关闭时正文链路零额外处理。

## S7 抓取失败不得污染阶段四（reviewer-r001 F-01 的真机对照）——**通过**

在同一会话内（S4 的补打之后），把 PC 侧夹具服务杀掉，让**仍在飞行中**的调试抓取以 EOF 失败；设备端帧：

```text
t+22.43s frame#22: 【诊断】1.提取后 未知错误 len=0,lines=0,imgs=0 (异常类型 IOException，消息默认不输出)
t+22.43s frame#23: 【诊断】2.HTML格式化后 未执行 (本次调试链路未执行该阶段)
t+22.43s frame#24: 【诊断】3.书源替换后 未执行 (本次调试链路未执行该阶段)
t+22.43s frame#25: 【诊断】4.阅读处理后 正常 len=81,lines=3,imgs=1 耗时1ms
t+22.44s frame#26: [00:22.387] java.io.IOException: unexpected end of stream on http://127.0.0.1:8765/...
t+22.44s frame#27: <empty payload frame, treated as ping>
closed by server at t+22.44s
```

判定：失败类别贴在链路停住的阶段一，**第 4 行仍是阅读的真实读数**（`正常 len=81,lines=3,imgs=1`），
没有被改成网络错误、也没有被收尾改写成「未执行」。三段 logcat 时间戳（23:11:17 补打、23:11:33 失败摘要）与原始帧一一对应。

## S8 Web 端摘要与终止信号的到达顺序（P1 阻塞 3 的真机对照）——**通过**

- 上述 S7 会话：摘要四行（#22—#25）→ 终止说明（#26）→ socket 关闭（t+22.44s），顺序与 `SerialTaskQueue` 的提交顺序一致；
- 另一次普通模式会话（`t+0.08s` 全部帧，#23—#26 四行摘要、#27 `︽正文页解析完成`、#28 ping，随后由服务端关闭）同样是
  **四行完整到达之后**才关闭，没有出现「终止信号先关 socket 导致摘要丢失」。

## S9 开关诊断时正文逐字一致——**通过**

同一章 `Chapter one` 的阅读页输出（uiautomator `content-desc`，脚本 `check_parity.py` 复核）：

| 场景 | 证据 | content-desc |
| --- | --- | --- |
| 诊断从未开启 | `33-reading-chapter-one-diag-off.xml` | `Chapter one / 　　CHECK chapter one. / 　　Second line. / ␣` |
| 会话存活期间的阅读页（**时刻不可证**，见 §S4 的更正） | `40-S4-reading-chapter-one.xml` | 同上（逐字相同） |
| 会话结束后的阅读 | `50-S6-reading-after-session-closed.xml` | 同上（逐字相同） |

`parity(all three equal): True`。

## 设备/工作树状态变更（如实登记）

1. 设备侧：`io.legado.app.tjf.debug` 由 3.26.092409 更新到 **3.26.092422debug**（`install -r` 保留数据，
   `firstInstallTime` 仍为 2026-09-24 10:01:36；原始输出 `62-install-raw.txt`、`63-dumpsys-package-raw.txt`）；
   S5 对照轮期间再次以同一 APK `install -r`（23:31:30，同为 `Success`，`firstInstallTime` 依旧不变）；
   开关过「Web 服务」并**已关闭还原**；合成书源 `checkKeyWord` 被 Web API 改写成 `abc`（一次），随后改回
   `--http://127.0.0.1:8765/chapter.html`；阅读进度/章节缓存有变化。
2. 未触碰：`io.legado.app.tjf`、`io.legado.app.tjf.release`、`io.legado.app.releaseA` 的数据；未卸载任何包；
   未改系统设置（未切输入法、未改网络）；未访问真实书源与真实站点。
3. PC 侧：夹具服务、`adb reverse tcp:8765`、`adb forward tcp:1122/1123` 在收尾时全部停止/移除（S5 对照轮曾临时重开，结束后再次移除）；
   工作树只多出 `app/build/p1-device-fixture/**`（夹具与脚本，已被 .gitignore 忽略）与本任务证据目录。
