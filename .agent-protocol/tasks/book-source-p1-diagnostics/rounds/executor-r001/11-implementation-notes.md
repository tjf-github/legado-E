# book-source-p1-diagnostics — 实施说明（11-implementation-notes.md）

本文件只记录**实现层面的设计事实与可复现环境配方**，过程与证据见 `10-execution.md`，机器可读摘要见 `10-execution.summary.json`。

---

## 1. 四阶段是怎么接进真实调用链的

数据流（§6 B0 的分段口径 → 代码位置）：

| 阶段 | 语义 | 记录位置 | 触发条件 |
| --- | --- | --- | --- |
| 1 `EXTRACT` 提取后 | 抓到的原始响应体，尚未做任何格式化/替换 | `AnalyzeRule.setContent(content, baseUrl, diagnostics)` 入口 | 仅 `BookContent` 抓取到的**首个**页传入 collector |
| 2 `FORMAT` HTML格式化后 | `usehtml` 占位 → `HtmlFormatter.formatKeepImg` → `unescapeHtml4` → `usehtml` 还原 | `BookContent.analyzeContent`（私有）音频/视频判断之后 | 非音频/视频；音频/视频显式 `markNotExecuted` |
| 3 `REPLACE` 书源替换后 | `contentRule.replaceRegex` 全文替换（含逐行 trim 与 onLineTxt 缩进） | `BookContent.analyzeContent`（公开）替换块之后 | 有 `replaceRegex`；没有则显式 `markNotExecuted` |
| 4 `READ_PROCESS` 阅读处理后 | 去重标题 / 重分段 / 简繁 / 净化替换 / usehtml 还原 / 段落切分 | `ContentProcessor.getContent` 尾部 | 真实阅读路径；书源调试链路显式 `markNotExecuted` |

**为什么不用全局「当前 sink」**：并发分页（`mapAsync`）会同时跑多页，全局 sink 无法区分页；因此阶段 1/2 由 `BookContent.primaryStagesCaptured` 只让首个抓取页采集，阶段 3/4 天然每章一次。

**为什么接收端挂在 `Debug` 上**：`Debug` 已是书源调试会话的既有入口（`Debug.startDebug` / `Debug.cancelDebug`），把 `diagnostics` 挂在那里可以做到「对现有函数签名零侵入」。`HtmlFormatter`、`WebBook` 等白名单外文件因此**完全不用改**。

**关闭时的零副作用**：`Debug.diagnostics` 为 null → 所有记录点都是 `?.` 空安全调用或一次 null 比较；不会构造快照、不会拼接摘要、不落盘。

---

## 2. 诊断组件设计要点

- **只出摘要不出正文**：`StageSnapshot` 里唯一的内容相关字段是 `summary`（`len=..,lines=..,imgs=..`）与 `skeleton`（结构骨架，字符被折叠成 `.`/`_`/`?`）。`BookSourceDiagnosticsCollector.record(stage, text, ...)` 接收真实正文但**只用来算长度/数量**，正文不进入快照。
- **分类不解析规则片段**：`DiagnosticClassifier` 判定规则语法错误时用**异常类型**（`PatternSyntaxException`）或整句标记，绝不把规则原文当依据——规则原文正是需要脱敏的内容。
- **脱敏按「可证明的危险片段」删，不做模糊美化**：URL 只保留 `scheme://host/path`，删 userinfo、查询串、片段；`token/cookie/authorization/sign/session/...` 的赋值值换成 `***`；`Bearer <值>` 换成 `Bearer ***`；超长截断。
- **未执行 ≠ 有产出**：`markNotExecuted` 写 `executed=false`，`format()` 输出「未执行」且**不输出耗时**，避免摘要看起来四段齐全。

---

## 3. 可复现的 JVM 门禁配方（本沙箱专用）

任务书指定的 `$env:GRADLE_USER_HOME="D:\gradle_home"; .\gradlew.bat ...` 在本 DSH 会话被沙箱拒绝（wrapper 要在沙箱外建 `.lck`）。下列配方**在本会话内实测可跑通编译与测试任务**：

```powershell
$env:GRADLE_USER_HOME = "D:\vsproject\legado-E\build\drp-gradle-home"   # 工作区内既有缓存，含 AGP 8.13.2
$gradle = "D:\gradle_home\wrapper\dists\gradle-8.14.4-bin\92wwslzcyst3phie3o264zltu\gradle-8.14.4\bin\gradle.bat"

& $gradle --offline --no-daemon --no-watch-fs --no-configuration-cache --console=plain `
  -Pkotlin.compiler.execution.strategy=in-process :app:compileAppDebugKotlin

& $gradle --offline --no-daemon --no-watch-fs --no-configuration-cache --console=plain `
  -Pkotlin.compiler.execution.strategy=in-process :app:testAppDebugUnitTest
```

各开关的必要性（都是实测踩出来的，不是抄来的）：

| 开关 | 为什么必须 |
| --- | --- |
| 用已解包发行版的 `gradle.bat` 而不是 `gradlew.bat` | wrapper 无条件在 `D:\gradle_home\wrapper\dists\...` 创建 `.zip.lck`，沙箱拒绝 |
| `GRADLE_USER_HOME` 指向工作区内 | 沙箱只允许写工作区；`D:\gradle_home` 可读不可写 |
| `--offline` | 沙箱无网络保证；依赖已在缓存里 |
| `-Pkotlin.compiler.execution.strategy=in-process` | 外部 Kotlin daemon 在本沙箱连不上（`Could not connect to Kotlin compile daemon`） |
| `--no-watch-fs` | 原生文件监视器初始化失败（`Couldn't open current thread, error = 5`） |
| `--no-configuration-cache` | 换过 Gradle 主目录/构建目录后配置缓存会报跨模块输出冲突 |
| **不要**用 `-Dorg.gradle.project.buildDir=...` 重定位构建目录 | 会让 `:modules:book` 与 `:modules:rhino` 的中转产物落到同一路径，触发 `parseDebugLocalResources` 隐式依赖校验失败 |

已知残余阻断：每一轮收尾 Gradle 都要重写 `build/reports/problems/problems-report.html`，该文件被本会话策略钉成只读 → 整轮 `BUILD FAILED`，但**任务本身已经跑完**（编译产物在 `app/build/tmp/kotlin-classes/appDebug`，测试结果在 `app/build/test-results/testAppDebugUnitTest/*.xml`）。

`build\drp-gradle-home` 里原本缺 junit/hamcrest/asm/joni/jcodings/json-smart/snakeyaml 等测试期依赖，我从只读的 `D:\gradle_home\caches\modules-2` 拷入对应模块与 `metadata-2.107` 索引后才跑通单测；这些缓存属于可重建内容。

---

## 4. 测试证据强度分级（不要读成比实际更强）

| 级别 | 覆盖对象 | 说明 |
| --- | --- | --- |
| **真实调用生产类** | `AnalyzeRule`（27 例）、`HtmlFormatter`（16 例）、`ReplaceRule`（17 例）、`BookSourceDiagnostics*`（25 例） | 直接 new/调用生产对象，断言真实返回值 |
| **转写级** | `ContentProcessor` 的段落规范化（14 例中的 8 例） | `ContentProcessor.getContent` 依赖 `appDb`/`Build.VERSION`/`appCtx`，JVM 无法实例化；冻结的是**从它尾部原样抽出**的 `ContentLineNormalizer`。逻辑同一份，但**不是调用生产类** |
| **数据结构级** | `BookContent`（14 例中的 4 例） | 直接测生产 data class 的字段与 `toString()` |
| **未覆盖** | `ContentProcessor` 全链路、`Debug` 会话、`BookContent.analyzeContent` 四阶段接线 | 需要真机/仪器测试 |

因此 `10-execution.md` §5 把「诊断开关前后正文一致性」在真机维度记为 NOT RUN。

---

## 5. 冻结的既有行为（契约测试里显式写明，防止后来者误当 bug 改掉）

1. `HtmlFormatter.format` 的**双重缩进**：`indent1Regex`（`\s*\n+\s*` → `\n　　`）之后再跑 `indent2Regex`（`^[\n\s]+` → `　　`），所以首行 4 个全角空格、后续行 2 个；HTML 源换行处还会留下「换行 + 2 个全角空格」的段内尾巴。
2. `&nbsp;+` 折叠为**一个**半角空格（不是逐个保留）。
3. `AnalyzeByJSoup` 的 `@text` 取整个元素的 `text()`，会把子元素（含链接）文本并入。
4. XPath 选中**元素节点**时返回节点外层 HTML，`/text()` 才返回纯文本。
5. 正则规则要生效必须用 `:` 前缀走 `getElement`（返回 `[整段匹配, 捕获组...]`）；`getString`/`getStringList` 没有正则分支。
6. `##match##replacement` 的**第二段**不参与规则切分（其中的 `$1` 不会被解析），第三段才作为替换式，且 `Matcher.appendReplacement` 支持 `$1` 捕获组引用。
7. 离线 JS 数值结果字符串化会带 `.0`（`parseInt` + 1 → `"13.0"`）。

---

## 6. 给后续阶段的接口提示

- P3（格式规范化）若要改 `HtmlFormatter.format`，可直接复用 `DiagnosticStage`/`DiagnosticRedactor` 的输出做前后对比，不必新造摘要格式。
- P4（替换与图片保护）若要加规则命中数/耗时，`BookSourceDiagnosticsCollector` 已在阶段 3 记录了耗时与空产出分类，只需补一条「每条规则命中数」的字段。
- 真机验证时如果想在**阅读路径**看四阶段，需要让阅读页也能安装 `Debug.diagnostics`（当前只有书源调试会话会装）；这属于后续可选增强，本批未做，以免改动 `ReadBook.kt`（白名单外）。
