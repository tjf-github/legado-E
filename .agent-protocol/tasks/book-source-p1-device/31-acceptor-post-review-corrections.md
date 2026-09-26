# book-source-p1-device — 验收侧对 reviewer-r002 发现的更正（不写回被冻结的执行报告）

状态：`accepted 后的更正记录`（acceptor: dsh）

## 为什么单独落一份

`10-execution.md` 是复审冻结对象：`close` 会用 runner 记录的 `ExecutionDigest` 复核它是否被事后改动
（`Close-AgentProtocolTask.ps1` 的门禁），当前值 `a5926af3c34dbd77e0fc220c35105e4457e6b2cf2989a9c46b00ba47c6d8bb2a`
（即 reviewer-r002 看到的那一版）。MODES 允许"最后一轮复审后只做删除或收窄的文档修正，且 acceptor 必须逐项披露"，
因此这些更正**不改写执行报告原文**，只落在本文件与 `30-acceptance.md`；`16-device-checks.md`（不在冻结摘要内）已就地更正。

## 更正清单（对应 reviewer-r002 的 F-1…F-9）

| # | 执行报告原文（冻结） | 验收侧的更正 | 依据 |
| --- | --- | --- | --- |
| 1 | §1.4 写 `S5 = **通过**` | **收窄为「部分通过」**：同会话正/负对照成立，但只覆盖**同一本书的其它章节**；任务书写的"别的书/本地书"仍是 NOT RUN | reviewer-r002 F-1；§16-device-checks.md S5 |
| 2 | §5 D-3 写"切回刚读过的章不触发补打，而刷新立即补打" | **收窄**：切章那一半只有帧静默、无带时钟产物，只能断言"该时刻没有补打帧" | reviewer-r002 F-7 |
| 3 | §5 D-4 写 `DiagnosticClassifier` 只按类型匹配 `SocketException/EOFException` | **更正根因**：类型表还含 `UnknownHost/SocketTimeout/InterruptedIOException/Timeout/CharacterCoding/EOFException`，且 `EOFException` 映射**编码类**、`InterruptedIOException` 映射网络类；判别**没有 cause 链匹配**，故"接受连接后不带状态行就关闭"这种失败会显示 `编码错误`（裸 EOF）或 `未知错误`（包装成 `IOException`） | reviewer-r002 F-4；`DiagnosticClassifier.kt:81-99` |
| 4 | §5 F-6 写"未验证风险点" | **补充**：代码可达性已确认（`ContentProcessor` 末尾无条件 `record(READ_PROCESS, …)`），只是本轮未触发该组合 | reviewer-r002 F-9；`ContentProcessor.kt:226-246` |
| 5 | §5 只有 D-1…D-4 与 F-6 | **新增 F-8 加固项**：夹具脚本 `serve_p1_fixture.py` 的路径拼接未做 `..` 归一化（测试脚本、仅 127.0.0.1 可达、未见被利用） | reviewer-r002 F-8 |
| 6 | §7 F-5 处置声称"parity 已改 UTF-8、WS 客户端已同步" | **更正为已实际落地**：复审时该声明未生效（实测 parity 为 UTF-16LE、证据目录里的客户端无墙钟代码）；现在 `31-apk-parity.txt`、`62-`、`63-` 三份均为 UTF-8，带墙钟的 `ws_p1_debug.py` 已同步进证据目录 | reviewer-r002 F-2/F-3 |
| 7 | §1.4 与 §16 S5 的场景表述 | 截图标注更正：`90-…png` 为**准备态**（时钟 23:29，早于负向动作 23:30:27），"刷新后"应引 `91-S5-control-chapter-two-refresh.xml`；S2 耗时标注按原始 XML 重抄（1:2ms / 2:1ms / 3:无）；S9 表把 `40-…xml` 的"补打发生的那一次阅读"改为"会话存活期间的阅读页（时刻不可证）" | reviewer-r002 F-5/F-6 |

## 覆盖声明

- 上述更正**只收窄或更正表述**，不新增任何设备证据、不改变任何一条原始结论的方向；
- `10-execution.md` 原文保持 reviewer-r002 所见的字节（sha256 见上），可被 `role-verify` 与 `close` 复核未被改动；
- 最终判定以 `30-acceptance.md` 为准；`16-device-checks.md` 是逐项设备证据的第二版（就地更正，未在冻结摘要内）。
