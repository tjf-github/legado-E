# book-source-p1-rework-r3 — 实施说明（executor: dsh）

状态：`实现完成 + reviewer-r001 findings 处置完成；交第二轮独立复审`

本文件记录第三轮返工的设计取舍与边界，供 reviewer 核对「为什么这么改、哪里没改」。

## 1. 默认异常说明为什么一律不引用 message

- 异常消息是无界输入：`RegexTimeoutException` 的消息里带规则名、正则和整段待替换正文；其它异常也可能内嵌响应体或正文。
- `DiagnosticRedactor.sanitizeMessage` 的能力边界是「删除可证明的秘密形态」（URL userinfo/查询串/片段、凭据赋值、Bearer、超长截断），**认不出普通正文**。P1 第二轮验收用合成哨兵证明了这一点。
- 因此 `describeError` 改为只输出 `异常类型 <简单类名>，消息默认不输出`。异常类名是稳定且不含用户数据的诊断信息；错误语义由 `DiagnosticErrorClass` 承担（行内已显示）。
- 取舍：牺牲了「异常消息里的有用细节」，换来「默认摘要不可能泄漏正文」。任务书 §目标 1 属安全阻塞，这个方向的取舍是刻意的；需要更多细节时应走显式的调试日志通道（`Debug.log` 全文，既有行为未改），而不是新增诊断摘要。
- 阶段内异常（`record(..., error = e)`）与链路级兜底（`recordFailure`）共用同一实现，不存在「一处改了一处漏」的分叉。

## 2. 阶段四：从「永远未执行」到「如实 + 可见」

- 覆盖问题的根因是 `record(StageSnapshot)` 天然 upsert（同阶段覆盖），而收尾无条件 `markNotExecuted(READ_PROCESS, ...)`。
- 改法分两层：
  1. **如实**：新增 `markNotExecutedIfMissing`，已有快照就直接返回；`finishContentDiagnostics` 只对「真的没跑过」的阶段标未执行。
  2. **可见**：阶段四只发生在阅读处理里，因此在 `ContentProcessor` 记录后新增 `Debug.onReadingStageRecorded()`。它要求：会话仍存活（`callback != null`，Android 相关判断留在 `Debug`）且 `BookSourceDiagnosticsCollector.shouldReportReadingStage()` 为 true——后者编码「诊断开启 + 阶段四确有真实执行快照 + 本会话未补打过」三态，并当场占用补打权，便于 JVM 直测（reviewer-r001 F-02）。两个条件都满足时补齐缺失阶段并再打印一次摘要。
- 为什么用「一次性认领」而不是每次都打：同一章可能被阅读、同章导出统计等路径重复处理，反复打印会把调试日志刷满；一次性认领让「第一次真实读数」可见且可预期。
- 为什么在 callback 为 null 时不补打：会话已结束（Web socket 关闭 / 调试页退出）时 `Debug.cancelDebug(true)` 会清空 collector 与 callback，此时既无输出通道，也不该伪造读数——如实表现为「没有输出」。
- 未改的口径（F-04）：`accepts` 仍是「书源 + 章节一致（绑定书号时书号也一致）」，同书同章的导出/换源统计仍能满足条件。本轮按要求收窄了**表述**，没有改行为；是否把阶段四限定为阅读路径专用留待 P1 之后决定。

## 3. Web 端保序：为什么引入 SerialTaskQueue

- 原实现 `printLog` 每次调用都走 `runOnIO`，在主线程时变成 `launch(IO)`：四行摘要 + 终止信号是 5 个可被 Dispatchers.IO 并发调度的独立任务。`Debug.kt` 只能保证**入队**顺序，不能保证**发送**顺序；终止任务先跑就会 `cancelDebug(true)` + `close()`，摘要行被 `runCatching` 静默吞掉。
- 修法：`SerialTaskQueue`（无界通道 + 单个消费者）把「提交顺序」变成「执行顺序」，并保证同一时刻只有一个任务在跑。它不持有额外线程：消费者随 socket 的 `MainScope` 取消而结束。
- 为什么不用 `Dispatchers.IO.limitedParallelism(1)`：限并发不承诺唤醒顺序（等待者由信号量调度），拿不到「FIFO」的硬保证；通道的 FIFO 是语言/库层面的既定语义，且能被单测直接钉住。
- 单任务失败不中断后续任务（消费者内部捕获 `Throwable`，`CancellationException` 重新抛出），任务自己的失败仍由它自己的 `runCatching` 上报，不被队列吞掉。
- 未纳入本轮：`ui/book/source/debug/BookSourceDebugModel`、`ui/rss/source/debug/RssSourceDebugModel`、`web/socket/RssSourceDebugWebSocket` 的 `printLog`。P1 阻塞项点名的是书源调试的 Web 端；RSS 链路与 P1 四阶段诊断无关。

## 4. M-3（AnalyzeByXPath.kt）裁定理由

- 该文件首轮改动是把 `TextUtils.join(换行符, it)` 换成 `it.joinToString(换行符)`；`getResult` 返回 `List<JXNode>?`，两者对 `List` 语义等价（元素 `toString` 拼接，空集合都是空串）。
- 回退的代价不是「风格」而是「测试不可运行」：纯 JVM 单测里 `TextUtils` 是未实现的 android.jar 桩，XPath 契约测试一旦走到 `AnalyzeByXPath.getString` 就会抛「not mocked」；首轮正是因此加了 `returnDefaultValues = true`，而那个全局弱化已被验收判为越界（M-2）并回退。
- 因此 controller 在本轮任务书里显式把该文件纳入白名单并保留改动；本轮未再改该文件其它内容。

## 5. 第一轮复审后的加固（reviewer-r001 F-01/F-02/F-03/F-06）

- **F-01 错误归属**：`recordFailure` 原先把「已执行阶段里最靠后的一个」当作链路停住的位置，而阶段四可能来自**另一次调用**（阅读路径）。取消「未执行覆盖」后这个错误归属会变成可见输出，因此候选集合排除 `READ_PROCESS`：抓取链路的失败只能落在阶段一/二/三里，没有任何抓取产出时归到阶段一。
- **F-02 展示时机可测化**：三态判定下沉为 `BookSourceDiagnosticsCollector.shouldReportReadingStage()`；`Debug` 只保留「会话是否还活着」这一 Android 相关判断。
- **F-03 畸形 URL**：切不出 authority 时回退到旧行为（在整段上取最后一个 `@`），保证「宁可多切主机名，也不把凭据留在摘要里」；userinfo 含 `/` 的畸形 URL 不再被识别为 userinfo，写进 KDoc 作为残余边界（RFC 3986 不允许该写法）。
- **F-06 简化**：删除 `SerialTaskQueue.close()`——没有生产调用点，消费者随 socket scope 取消已经足够。

## 6. 没改什么（边界自证）

- 诊断关闭时：`Debug.diagnosticsFor` 返回 null，`ContentProcessor` 的计时与补打都被 `diagnostics != null` 挡住，不做额外处理、不落盘（collector 全内存）。
- 数据库、本地 TXT/EPUB 导入与阅读路径、`app/build.gradle`、依赖：未改。
