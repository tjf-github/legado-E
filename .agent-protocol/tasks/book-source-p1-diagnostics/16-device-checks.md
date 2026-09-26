# book-source-p1-diagnostics — P1 真机抽查证据（DSH 接手 Codex 未完成的真机测试）

状态：`设备端抽查已执行；不构成验收，也不代表 P1 通过`

- 执行方：DSH（本窗口，人类直接指派）。当前 `.agent-protocol/CURRENT.md` 仍为 mode=triad / route_revision=7 / controller=codex / executor=dsh / reviewer=claude / acceptor=codex，本文件**不改任何角色原文**，只新增设备证据。
- 接手原因（事实）：Codex 的 P1 验收会话（rollout `2026-09-24T09-35-29-01a0d10d`，cwd=`D:\vsproject\legado-E`）在 **2026-09-24T02:19:24Z（本地 10:19:24）** 以 `task_complete` 结束，`last_agent_message=null`，`error.codex_error_info=usage_limit_exceeded`（用量耗尽）。该会话已完成：独立编译与 380 项单测复跑、隔离 `.debug` 包安装、合成书源导入，但**未产出四阶段摘要的真机证据，也未把本轮真机结果写进 `30-acceptance.md`**（该文件仍为 2026-09-23 首轮版本）。
- 授权范围：用户 2026-09-24 明确选择「按 P1 §8 六步全跑，F-01 只记录现状不要求通过」。
- 原始证据目录：同目录 `33-device-evidence/`（截图 png + uiautomator dump xml + 文本）。本文件中的每一行摘要都可在对应 xml 里逐字复核。

## 1. 设备与环境（实测）

| 项 | 实测值 |
| --- | --- |
| 设备 | `10CE5P1M1Z001P9`，model `V2338A`(PD2338)，vivo，USB |
| adb | `D:\adb\platform-tools\adb.exe`；授权状态在本次接手开始时为 `unauthorized/authorizing`，用户点「允许 USB 调试」后为 `device` |
| 被测包 | `io.legado.app.tjf.debug`，versionName `3.26.092409debug`，versionCode `26092409`，firstInstallTime/lastUpdateTime `2026-09-24 10:01:36` |
| 对应产物 | `app/build/outputs/apk/app/debug/legado_app_3.26.092409.apk`（2026-09-24 09:59:45，37690949 字节）⇒ 被测二进制就是**接手时工作树**编译出的包 |
| 同机其它包 | `io.legado.app.tjf`、`io.legado.app.releaseA`（未安装、未卸载、未清数据；测试只发生在 `.debug` 包内） |
| 合成夹具 | `app/build/p1-device-fixture/`（source.json / search.html / book.html / toc.html / chapter.html / chapter2.html / image.png），由 PC 侧 `python -m http.server 8765 --bind 127.0.0.1` 提供，经 `adb reverse tcp:8765 tcp:8765` 给设备访问 |
| 合成书源 | `bookSourceUrl=http://127.0.0.1:8765/`，name `P1 synthetic device check`；`ruleContent.replaceRegex=##SYNTHETIC##CHECK`（正文里的 SYNTHETIC 会被替换成 CHECK，用于验证替换阶段真的生效） |

未接触真实书源 JSON、`bookshelf.json`、`docs/book-source-audit/语料`、签名材料或任何凭据；全部输入为合成数据。

## 2. 方法学披露（重要，影响可复现性）

1. **设备输入法把 ASCII 标点转成全角**：`adb shell input text '--http://...'` 落进输入框变成 `－－http：、、127。0。0。1：8765、chapter。html`，因此**无法用 adb 直接敲出 `--<url>` / `++<url>` 调试入口**（Codex 当时也卡在这里，见其会话 item 682–745）。
2. 采用的替代通路：把要提交的查询写进合成书源自身的 `ruleSearch.checkKeyWord`，重新导入该源后，调试页左侧的**「我的」快捷按钮**（`text_my`）会把它原样提交（`BookSourceDebugActivity.kt:99-101`）。提交的字符串与手敲完全一致（屏幕上可见），只是「谁把它填进输入框」不同；这不是改产品行为、也不是改测试断言。
3. 纯字母查询（`abc`）不受输入法影响，用 `input text` + 提交按钮即可，用于跑完整 搜索→详情→目录→正文 链路。
4. 调试页在一次提交后会隐藏快捷面板（`openOrCloseHelp(false)`），因此**重复跑同一条查询需重进调试页**；这是操作体验问题，不是功能缺陷。

## 3. 逐项结果（P1 交付报告 `10-execution.md` §8 六步）

### §8.1 调试页 `--<正文页URL>` 的四阶段摘要（已验，通过）
设备实显（原样）：

```text
[00:00.003] ⇒开始访正文页:http://127.0.0.1:8765/chapter.html
[00:00.005] ︾开始解析正文页
[00:00.057] ≡获取成功:http://127.0.0.1:8765/chapter.html
[00:00.084] ┌获取章节名称
[00:00.085] └调试
[00:00.085] ┌获取正文内容
[00:00.085] └
　　CHECK chapter one.
　　Second line.
　　<img src="http://127.0.0.1:8765/image.png">
【诊断】1.提取后 正常 len=107,lines=4,imgs=1 耗时24ms
【诊断】2.HTML格式化后 正常 len=85,lines=3,imgs=1 耗时1ms
【诊断】3.书源替换后 正常 len=81,lines=3,imgs=1
【诊断】4.阅读处理后 未执行 (书源调试链路止于 BookContent，未进入阅读处理)
[00:00.094] ︽正文页解析完成
```

- 证据：`33-device-evidence/step1-content-debug-summary.png` + `.xml`（截图见调试页四行 `【诊断】`）。
- 结论：四行齐全；阶段 1 是**规则提取后**的产出（107 字 = `<p>SYNTHETIC chapter one.</p><p>Second line.</p><img …>` 的文本化结果），阶段 2 为格式化后（85），阶段 3 已应用 `replaceRegex`（SYNTHETIC→CHECK，81）；相对 URL `/image.png` 已被补成绝对 URL。
- 脱敏：四行只含阶段/分类/长度/行数/图片数/耗时，**不含正文、查询串、Cookie、令牌**。注意：同一屏上方由**既有调试功能**打印的正文与图片标签（`┌获取正文内容` 块）不是本次诊断新增，属 P1 之前就有的调试展示。

### §8.2 目录→正文入口 / 完整链路（已验，通过）
- `++http://127.0.0.1:8765/toc.html`：目录页 2 章 → 首章 `Chapter one` → 正文，摘要四行齐全（`step2-toc-entry-summary.png/.xml`，本次 `1.提取后 … 耗时2ms` / `2.HTML格式化后 … 无耗时` / `3.书源替换后 … 耗时1ms`）。
- 完整 搜索(`abc`)→详情→目录→正文：`step2b-full-chain-search-to-content.png/.xml`，摘要数值与 §8.1 完全一致（107/85/81，imgs=1），说明链路入口不同但阶段读数稳定。
- 结论：阶段 1/2 在目录入口与完整链路下都真实出现（这正是首轮 reviewer B-3 指出的会静默丢失的场景，返工后已修复）。

### §8.3 抓取失败时的错误分类与「未执行」标注（已验；发现一处分类缺口）
| 子场景 | 设备实显摘要 | 判定 |
| --- | --- | --- |
| 真·连接失败（移除 `adb reverse`，设备 127.0.0.1:8765 无监听） | `【诊断】1.提取后 网络错误 len=0,lines=0,imgs=0 (Failed to connect to /127.0.0.1:8765)`，2/3/4 全部 `未执行` | 与 §8.3 预期一致（`step3a-connect-refused-network-error.png/.xml`） |
| 反向隧道在但 PC 端监听已死 | `【诊断】1.提取后 未知错误 len=0,lines=0,imgs=0 (unexpected end of stream on http://127.0.0.1:8765/...)`，2/3/4 全部 `未执行` | **分类降级**：`java.io.IOException: unexpected end of stream` 不是 `SocketException/EOFException`，落进 `DiagnosticClassifier` 的消息兜底后无匹配 → `未知错误`（`step3b-reverse-tunnel-eof.png/.xml`） |

其余观察：异常说明只出现 URL 前缀 + `...`（被 `sanitizeMessage` 截断/脱敏），未出现查询串、Cookie、正文；阶段 1 在失败时给出 `len=0,lines=0,imgs=0`，没有伪造成「正常」。

### §8.4 会话隔离：调试会话存活时读别的书（**设备端不可判，非通过**）
- 现行实现下本项在设备上**无法给出可判结论**：`Debug.finishContentDiagnostics()`（`Debug.kt:452-460`）在打印前**无条件**把阶段四改写成「未执行」，因此「别人的 `4.阅读处理后` 行」无论是否写入过，最终都不可能出现在摘要里——设备端无法区分「没串进来」与「串进来后被覆盖」。这条与 F-01 同源。
- 可复核的隔离证据仍只有 JVM 契约测试（`collectorAcceptsOnlyTheBoundBookAndChapter` 等）与代码阅读，本轮**没有**新增设备级反例，也**不声称**隔离已在真机验证。

### §8.5 同一书源/同一本书/同一章正常阅读（**计划书预期在设备上不成立；阅读链路本身正常**）
- 阅读链路本身正常：搜索 `abc` → `Synthetic book` → 放入书架 → 阅读，正文渲染为 `CHECK chapter one.` + `Second line.` + 1 张图；章节二渲染 `CHECK chapter two.`（替换规则在阅读链路上同样生效）。
  - 证据：`step5-reading-page.png`、`step5b-reading-page-top.png`、`step5c-reading-chapter-one.png`、`step5d-reading-chapter-one-full.png`、`step5e-reading-after-refresh.png`，以及 `step5e-reading-content-desc.txt`（uiautomator `content-desc` 原文：`Chapter one\n　　CHECK chapter one.\n　　Second line.`）。
- 但 `10-execution.md` §8 第 5 步要求的「同一书源同一本书同一章正常阅读 → `4.阅读处理后` 有真实读数」**在真机上不可能看到**：任何一次调试会话结束时该行都被改写为 `未执行 (书源调试链路止于 BookContent，未进入阅读处理)`。实测与 prediction 一致（见 §8.1/§8.2 的第 4 行）。
- 因此：**F-01 在设备侧的表现已确认**；「真实阅读读数是否被记录后又被覆盖」设备端不可判，仍以代码级 F-01 结论为准。`10-execution.md` §8 第 5 步的预期措辞需要按 F-01 更正。

### §8.6 关闭调试页后重复阅读（已验，通过）
- 关闭调试页（BACK 结束 `BookSourceDebugActivity` → `BookSourceDebugModel.onCleared` → `Debug.cancelDebug(true)`）后再走 书架→阅读：正文渲染与关闭前一致（`step5-reading-page.png` 与 `step5d/5e` 对比），全程**没有任何 `【诊断】` 行**，无异常弹层、无崩溃。
- 限制：诊断关闭时的「零额外处理」只能由代码/单测支撑（无会话时 `diagnosticsFor` 直接返回 null）；设备端只能证明「无可见诊断产物」。

## 4. 本轮新发现（设备侧）

| # | 发现 | 严重度 | 说明 |
| --- | --- | --- | --- |
| D-1 | F-01 的设备侧表现确认：第 4 行恒为「未执行」，与是否真的读过同一章无关 | 阻塞（沿用 F-01） | 已在上文 §8.4/§8.5 记录；设备端无法进一步区分 |
| D-2 | 错误分类缺口：`IOException: unexpected end of stream` 归为 `未知错误` 而非 `网络错误` | 低—中 | 只要失败发生在「连接被接受但响应不完整/被中间层断开」时就会降级；`DiagnosticClassifier` 只按类型匹配 `SocketException/EOFException`，消息侧没有 `unexpected end of stream`/`broken pipe` 等标记 |
| D-3 | 阶段耗时在 <1ms 时**整段省略**，读起来像「没计时」 | 低 | `StageSnapshot.format()` 仅在 `elapsedMs > 0` 时输出耗时；快速阶段（格式化/替换常见）会静默消失。建议区分「未计时」与「<1ms」 |
| D-4 | 调试页提交一次后快捷面板隐藏，重复执行同一条查询必须退出重进 | 低（体验） | 与诊断正确性无关，但会影响线上排障效率 |
| D-5 | 图片加载失败只显示占位符，摘要里仍是 `imgs=1` | 说明 | 属既有行为（图片是正文结构，元素被保留）；本轮夹具先缺图后补图，补齐后阅读页仍显示占位（疑似图片结果缓存），未继续追 |

## 5. NOT RUN（不得读作已覆盖）

- Web 端调试（`BookSourceDebugWebSocket`）竞态 B-4 / F-02：未启用 App 的 Web 服务，未做 Web 端「修复前/后」对照。
- 本地 TXT/EPUB 阅读路径是否被诊断串扰：本轮未导入本地书，未验证。
- 音频/视频/文件类书源、多页章（`nextContentUrl`）、`@webjs:`、真实书源与真实站点：未跑。
- `lint`（`:app:lintAppDebug`）：未跑（非发版轮）。
- 契约测试与门禁：本轮未复跑（沿用 Codex 2026-09-24 会话与 `10-execution.md` 的记录；本轮只做设备侧）。

## 6. 设备与工作树状态变更（如实登记）

1. 设备上新增/更新：`io.legado.app.tjf.debug`（Codex 2026-09-24 10:01 安装的本次工作树产物）及其内部数据——合成书源 1 条、书架 1 本（`Synthetic book`）。**未触碰** `io.legado.app.tjf`、`io.legado.app.releaseA` 的数据。
2. 工作树新增（均未提交）：`app/build/p1-device-fixture/*`（夹具，含新加的 `image.png`、`query_chapters.py`）、`app/build/p1-now.png`、本目录 `33-device-evidence/*`、本文件。
3. 未提交、未推送、未切分支、未安装/卸载其它包、未清数据、未改系统设置（未切换输入法；仅按用户确认授权 USB 调试）。
4. 收尾时已停止 PC 侧夹具服务并移除 `adb reverse`；设备上的测试包与合成数据保留，供 Codex 或后续窗口复核。

## 7. 交回 controller 的事项

1. `10-execution.md` §8 第 5 步的预期需按 F-01 更正（现措辞不可能通过）。
2. `30-acceptance.md` 需要补一轮能落笔的验收：Codex 因用量耗尽未写第二轮验收；本文件只是设备证据，**不是**验收判定。
3. D-2/D-3 是否进返工范围由 controller 定（都属诊断可读性，不涉及正文语义）。
4. 若仍要设备级隔离证据，得先修 F-01（否则摘要恒为「未执行」，判据不成立）。
