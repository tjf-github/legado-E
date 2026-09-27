# book-source-d2-match — 实施说明

- 时间：2026-09-26（本地时区）；executor=dsh（mode `dsh-claude`，route_revision=8）
- 依据：`docs/book-source-audit/D-2-补打匹配口径-裁定.md`（M1/M2/M3）、`.agent-protocol/tasks/book-source-p1-device/findings.json` F-0009

## 1. 设计决定

1. **归一放在纯 JVM 侧，Android 侧只传参**。新增 `help/book/diagnostics/DiagnosticIdentity.kt`（只用 `java.net.URL`），采集器内部只比归一后的键；`Debug`/`BookContent`/`ContentProcessor` 负责把 `chapter.baseUrl` 传进来。
   - 理由：D-2 的核心是「相对 vs 绝对」，必须有 base 才能解析；把解析放进采集器能让整条链在单测里断言，而不是只靠真机。
   - 不直接复用 `NetworkUtils.getAbsoluteURL`：那个文件带 Android 依赖（`android.net.*`、`splitties`），拉进纯单测有风险。这里用同一语义（`URL(URL(base), spec)`，base 先 `substringBefore(",")`），并在 KDoc 写明来源。
2. **选项切法照抄 `AnalyzeUrl.paramPattern`**（`\s*,\s*(?=\{)`）：只有「逗号 + 花括号」才算 URL 选项，查询串里的普通逗号不切。自带编译好的正则，避免把 Android 侧的 `AnalyzeUrl` 类拉进单测。
3. **身份只做归一，不放宽**。书源键只去空白/片段/查询串/尾斜杠；章节键只做绝对化 + 去选项。**明确不做**同域名放宽——那会推翻 P1 真机轮 S5 的会话隔离证据。
4. **不匹配只记数**：新增 `AtomicInteger rejectedIdentities`，`accepts()` 判定为「不属于本会话」时递增，**不保存任何 URL**；关闭诊断时（`isEnabled=false`）与未绑定目标时都不计数。
5. **章节匹配是「两条途径取或」，归一比较只做新增、不替换**：`归一后相等 || 原始串逐字相等`。
   - 理由（送审前自审发现的回归风险）：本改动若**替换**掉逐字比较，存量书 `baseUrl` 为空时两侧会归一到不同形态（绑定侧绝对、阅读侧相对），而这一场景在 P1 真机轮的 `++<tocUrl>` 里**本来是对得上的**——那等于把能用的场景改坏，违反计划书 §2 第 4 条「兼容默认行为」。
   - 因此 `Target` 额外保存绑定时的原始章节串，`accepts()` 里两条途径取或；守卫测试 `rawLiteralEqualityStillAcceptsWhenOnlyOneSideHasABase` 与 `rawEqualityDoesNotLeakAcrossDifferentChapters`（后者确认回退不是「谁来都收」）。
   - 边界：回退只在**原始串完全相同**时生效，与改动前的行为一致，不引入新的误接受面。
6. **M3 文案由采集器给出**（`readingStageMissingReason()`）而不是写在 `Debug` 里，这样它是可 JVM 断言的产品文案：
   没有排除记录 → 保留原口径「未进入阅读处理」；有排除记录 → 「本会话存活期有 N 次正文处理被身份隔离排除，未进入本次调试目标」（**以 `readingStageMissingReason()` 的实现为准**；2026-09-26 第三轮复审 F-1 指出此处曾与代码不同步，已对齐）。
7. **M3 必须有刷新点（第一轮复审 F-1 的整改）**：调试链路收尾**早于**用户阅读，收尾时计数为 0，若原因在写入时固化成字符串，默认时序下用户读到的仍是「未进入阅读处理」这句错误因果。
   处置：`refreshReadingStageMissingReasonIfMissing()` 在阶段四「未执行」时按**当前计数**重写 note；`Debug.diagnosticsFor` 的拒绝分支调用它，并用 `claimMismatchRefreshReport()` 把打印限制为**一会话一次**（逐章阅读会不断触发不匹配，不能每章刷屏）。
   守卫测试四条：时序刷新（`mismatchAfterChainFinishRefreshesTheReadingStageReason`）、已有真实读数不刷新、门闩只发一次、关闭诊断不刷新不占闩。
8. **删除只为测试存在的公开 API（F-9）**：`isSameSource` 在生产代码里没有调用点，已删除；测试改为直接比较 `sourceKey`，生产侧统一用 `sourceKey`。
9. **`Target.toString()` 显式覆盖（F-2 加固）**：`Target` 驻留原始章节串，默认 data class 输出会在有人 `log(target)` 时把 URL 带进日志；改为只暴露「键是否存在」而不回显内容（书源键保留可见——调试日志本来就打书源 URL，且它已去查询串/片段）。守卫测试 `targetToStringNeverLeaksUrls`。


## 2. 与裁定书的偏差（必须让复审看到）

| 裁定书条目 | 本轮做法 | 理由 |
| --- | --- | --- |
| M2 的 `sortUrls()` 集合兜底（「仍不等时，检查二者是否同属该书源 `sortUrls()` 集合」） | **未实现**，留在 M3 之后 | 两侧信息不对称：阅读侧 `ContentProcessor.getContent` 手上只有 `book`（`book.origin`），没有 `BookSource` 对象，算不出「该书的书源 URL 集合」；要用它就得按 origin 回查数据库并把 `BookSource` 透传进来，属明显扩大改动面。当前用「归一等值 + M3 可见化」覆盖了 D-2 的两种真实形态（相对/绝对、尾斜杠/查询串）；源 URL 真正被改写这一类仍会不匹配，但会**显式可见**而不是静默。裁定书已同步更正 |
| M3 的措辞「不匹配时在摘要里写明原因」 | 落地为 `readingStageMissingReason()`，措辞含计数、不含 URL；**并新增刷新点**（F-1） | 与「默认摘要不含 URL/正文」的既有安全边界一致；计数足以区分「链路没走到」与「身份没匹配上」；刷新点是默认时序下唯一能让该文案真正出现的手段 |
| M1 的「直接用 `BookChapter.getAbsoluteURL()`、不引入新的 URL 规范化实现」 | **未照做**：在 `help/book/diagnostics/DiagnosticIdentity.kt` 新写了一份纯 JVM 归一（`java.net.URL` + 同一选项切法），并保留「原始串逐字相等」这条既有途径 | 任务书 `00-task.md` 硬要求「归一实现集中在 `help/book/diagnostics/` 内的纯 JVM 代码，Android 侧只传参」——`NetworkUtils`/`BookChapter` 带 Android 依赖，直接复用会把单测绑到 Android 桩上；裁定书已就地更正（F-7） |
| M2 的归一范围（「去首尾空白、去尾斜杠、去查询串」） | 额外**去掉片段**（`#`） | 片段不参与请求、指向同一书源，属有意放宽且不推翻 S5 隔离证据；controller 已追认（F-5），KDoc 已写明 |

## 3. 开发过程中的一次自我纠错

首版测试 `sourceVariantStillMatchesSameSource` 把绑定串写成 `"$source/book?from=debug"` 并期望与 `"$source/"` 同源——这是**测试前提写错**（`/book` 是不同路径 ⇒ 不同书源），不是实现缺陷。修正测试为 `"$source?from=debug"`，并补一条反例：同主机不同路径**不得**视为同源。修改的是测试，代码未因此改动。

## 4. 未触碰

`help/ai/**` 与 `ReadBook` 的 AI 分支、数据库结构、正文提取/格式化/替换/请求/缓存路径、`ui/**`、签名与凭据、真实书源与语料。白名单外一行未改。
