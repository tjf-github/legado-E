# book-source-p1-diagnostics — 返工轮实施说明（executor: dsh，round 2）

本文件只记录**实现层面的设计事实**；命令证据、改动清单与限制见 `10-execution.md`，
机器可读摘要见 `10-execution.summary.json`。首轮同名前文件已原样归档到 `rounds/executor-r001/`。

依据：`30-acceptance.md` 的 findings 处置表与「唯一下一步」，以及首轮复审 `20-review.md`。

---

## 1. 返工项到改动的映射

| 返工项 | 做法 | 落地位置 |
| --- | --- | --- |
| B-1 错误分类未接线 | 每个阶段的真实 catch 点把异常交给 collector；链路级异常在 `Debug.contentDebug.onError` 兜底；ContentProcessor 的替换超时把规则阈值一起带上 | `BookContent.kt`（提取/格式化/替换三处 try/catch）、`Debug.kt`、`ContentProcessor.kt` |
| B-2 阶段一标错 | 「提取后」不再记录原始响应，改记 `analyzeRule.getString(contentRule.content)` 的真实产出，耗时只计提取本身；`AnalyzeRule` 的 3 参诊断重载整体删除 | `BookContent.kt`、`AnalyzeRule.kt` |
| B-3 缺阶段 / L-5 并发采集权 | 采集权从「全局标志 + `nextChapterUrl` 判据」改为**会话内 AtomicBoolean 一次性认领**；收尾对仍无快照的阶段统一补「未执行」 | `BookSourceDiagnostics.kt`、`Debug.finishContentDiagnostics` |
| B-4 Web 完成回调竞态 | 先输出摘要、再发 `state=1000/-1` 终止信号（Web 端收到终止信号才异步 `cancelDebug(true)`） | `Debug.contentDebug` |
| B-5 开关一致性缺证据 | 新增 `DiagnosticsToggleParityTest`：同一输入在「有 / 无诊断会话」下跑生产链路并逐字节比较 | 新增测试文件 |
| M-1 脱敏 | `stripUrlSecrets` 改为 URL 范围内只保留 authority+path、`?`/`#` 之后整段丢弃；`replaceAssignment` 跳过分隔符后的空白；SECRET_KEYS 补常见短键 | `DiagnosticRedactor.kt` |
| M-2 构建越界 | 删除 `app/build.gradle` 的 `returnDefaultValues = true`，恢复严格默认（该文件现与 HEAD 完全一致） | `app/build.gradle` |
| M-3 XPath 越界 | 不改代码、不抹去越界事实，保留 `TextUtils.join → joinToString`；须 controller 显式纳入范围 | `AnalyzeByXPath.kt`（本轮未改） |
| L-1 串扰 / 本地路径侵入 | 接收端改为**会话目标绑定**：只有 (书源 + 书 + 章节) 与调试目标一致才拿得到；本地书 origin 为 `loc_book`，永不匹配 | `BookSourceDiagnostics.kt`、`Debug.kt`、`BookContent.kt`、`ContentProcessor.kt` |
| L-2 textNodes 错误结论 | 更正测试注释并补「直系文本节点」正例；首轮报告的错误结论只保留在归档并标注失效 | `AnalyzeRuleContractTest.kt` |
| L-3 未接线代码 | `skeleton()` 保留但 KDoc 如实写「当前无生产调用点」；未被使用的 `BookSourceDiagnostics` 接口与 `DISABLED` 单例删除 | `BookSourceDiagnostics.kt`、`DiagnosticRedactor.kt` |
| L-4 tmp-gradle-home | 盘点来源（首轮早期一次 `GRADLE_USER_HOME` 尝试，daemon 日志时间戳 2026-09-22 13:52–13:53），确认是 Gradle 缓存后按清理规则删除 | `10-execution.md` §5 |
| 额外观察（关闭时仍读时钟） | ContentProcessor 计时改为「拿到接收端才读时钟」；AnalyzeRule 里那次计时随重载一起删除 | `ContentProcessor.kt`、`AnalyzeRule.kt` |

---

## 2. 关键设计决定

### 2.1 会话身份：谁能写进调试摘要

```text
Debug.startBookSourceDebugSession()        // 新会话：目标为空，拒绝一切写入
Debug.bindDiagnosticsTarget(源, 书, 章节)   // contentDebug 抓正文前绑定
Debug.diagnosticsFor(源, 书, 章节)          // 正文管线取接收端；不匹配返回 null
```

匹配规则：书源必须一致；章节必须一致；会话目标带书号时书号也必须一致。
因此**本地 TXT/EPUB（origin=`loc_book`）、同一书源下的别的书、同一本书的别的章节、
换源字数统计、API 导出**这些共用 `ContentProcessor.getContent` 的路径都拿不到接收端。

首轮的全局 `primaryStagesCaptured` + `nextChapterUrl.isNullOrEmpty()` 判据整体删除：
它既会被普通阅读写脏，又在目录→正文入口不重置。现在采集权属于会话对象本身（`AtomicBoolean.compareAndSet`），
并发分页只有一个调用能拿到。

### 2.2 阶段语义（返工后的口径）

| 阶段 | 采集点 | 耗时口径 |
| --- | --- | --- |
| 1 提取后 | `analyzeRule.getString(contentRule.content, unescape=false)` 返回处 | 只计这次提取 |
| 2 HTML格式化后 | 内置净化格式化 + 实体解码 + usehtml 还原之后 | 只计格式化 |
| 3 书源替换后 | `contentRule.replaceRegex` 全文替换（含 onLineTxt 缩进）之后 | 只计替换 |
| 4 阅读处理后 | `ContentProcessor.getContent` 段落切分之后 | 只计阅读处理 |

阶段 1/2 仍只由本次会话的首个抓取页采集（并发分页其余页不写），但认领方式改为原子一次性。

### 2.3 错误分类的接线点

- **阶段内异常**：`BookContent` 的提取、格式化、替换三处 `catch` 分类后**原样上抛**，不改变异常语义；
- **链路级异常**：`Debug.contentDebug.onError` 把异常交给 collector，归到「已执行阶段里最靠后的一个」——
  产出到哪一层就报在哪一层；一个阶段都没跑就归到阶段一。网络/HTTP/编码这类抓取层错误由此进入摘要；
- **阅读处理阶段**：ContentProcessor 里原本被就地吞掉的异常（替换超时、规则报错、去重标题出错）
  现在会带进阶段四的分类，`RegexTimeoutException` 同时带上该规则自己的超时阈值；
- **异常消息默认不整段引用**：`RegexTimeoutException` 的消息里带规则名、正则与整段待替换正文
  （见 `RegexExtensions.replace`），这一类只写固定说明；其余异常走 `sanitizeMessage` 脱敏。

### 2.4 阶段唯一与兜底

同一阶段只保留最后一条快照（upsert），摘要里不会出现同一阶段两行自相矛盾。
收尾时 `markMissingStagesNotExecuted` 把所有仍无快照的阶段标成「未执行」，
所以「链路在提取前就失败」不会再显示成「摘要缺了两段而没有任何解释」。

### 2.5 关闭时的零副作用

`Debug.diagnostics` 为 null（或无匹配目标）时：`ContentProcessor` 不读时钟、不拼摘要、不落盘；
`BookContent` 不认领采集权、不写快照；`AnalyzeRule` 已经完全不含诊断代码。
接收端只用 `Collections.synchronizedList` 在内存中缓存本次会话的快照，无网络、无持久化。

---

## 3. 证据强度与边界（不要读成比实际更强）

- **诊断组件**：`BookSourceDiagnosticsContractTest` 与新增的开关差分测试都在纯 JVM 下调用真实生产类，
  不 mock、不复制实现；
- **正文管线**：`ContentProcessor` / `BookContent` 依赖 `appDb`/`appCtx`/`Build.VERSION`，
  无 Robolectric 无法在 JVM 实例化，因此**没有**端到端单测；它们的诊断耦合只有「取接收端 + 写一条快照」，
  不参与返回值计算，这一点由组件层的开关差分测试覆盖；
- **真机**：调试页四阶段可见性、Web 端调试竞态、开关前后阅读页正文一致，本轮仍为 NOT RUN；
- **构建前提**：本轮已恢复严格测试默认，门禁结果不再依赖 `returnDefaultValues` 这一弱化配置。
