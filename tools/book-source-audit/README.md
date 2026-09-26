# 书源静态体检与分群工具

对应《书源与解析优化计划书》§7 **P0** 的第 1、2 项交付物：建立可重复运行的本地只读清单
工具，对当前书源快照分群并做全量离线静态检查。

- **只读**：只以只读方式打开输入，绝不写回源文件。
- **离线**：不 `import` 任何网络模块，不发起任何请求。联网测试属 P1，且只从本工具的分群中选代表样本。
- **不执行规则**：静态分析只做语法与结构判定，绝不求值任何书源 JS。
- **可重复**：不写死任何条数。书源新增后重跑即自动纳入（§2.7）。同输入 + 同
  `--generated-at` 两次运行输出字节一致。

## 用法

```bash
python tools/book-source-audit/audit.py
```

默认在当前目录与仓库根查找 `阅读书源.zip` 或 `bookSource.json`（`.gitignore` 内的本机文件）。

```bash
python tools/book-source-audit/audit.py --source 阅读书源.zip --merge-collections --publish docs/book-source-audit
```

常用参数：

| 参数 | 说明 |
| --- | --- |
| `--source <zip\|json\|dir>` | 输入。zip 内定位 `*bookSource.json`；目录会读取其中所有 `.json` 并归并 |
| `--merge-collections` | 读取 zip 内**所有**书源合集，按 `bookSourceUrl` 归并去重（复现 §3.4 的「16155 条引用 → 6139 唯一 URL」） |
| `--out <dir>` | 本机输出目录，默认 `.book-source-audit`（已在 `.gitignore` 内） |
| `--publish <dir>` | 把**聚合**报告写到该目录（可入库）。建议 `docs/book-source-audit` |
| `--full` | 额外写出逐源清单 `inventory.jsonl`（**只落本机，不要入库**） |
| `--include-identity` | 逐源清单附带书源名称与 URL（仅本机；必须与 `--full` 同用） |
| `--generated-at <ISO8601>` | 覆盖报告里的生成时间，用于验证重复运行一致 |
| `--stale-days <n>` | `lastUpdateTime` 早于多少天算陈旧，默认 730 |
| `--slow-respond-ms <n>` | `respondTime` 超过多少毫秒算偏慢，默认 15000 |
| `--label <标签>` | 发布文件名的口径标签：`<date>-<label>-静态分群报告.md`。缺省按口径自动取「单份清单」/「归并合集」 |

> **`--include-identity` 的输出含书源名称与 URL。** §3.4 的安全边界要求原始压缩包、
> 完整 Header、Cookie、登录数据与真实内容调试响应只能留本机。默认的 `--full` 逐源清单
> 用 `url_hash`（`bookSourceUrl` 的 SHA-256 前 12 位）作标识，不含名称、URL、选择器
> 原文、请求头取值或正文。

## 输出

| 文件 | 位置 | 能否入库 |
| --- | --- | --- |
| `report.md` | `--out` / `--publish` | ✅ 聚合，无身份信息 |
| `summary.json` | `--out` / `--publish` | ✅ 聚合，无身份信息 |
| `clusters.csv` | `--out` | ✅ 逐指纹聚合，无身份信息 |
| `inventory.jsonl` | `--out`（需 `--full`） | ❌ 逐源清单，只留本机 |
| `<date>-<label>-summary.json` | `--publish` | ✅ 同上，按口径分存，两次运行不互相覆盖 |

> **`--publish` 为什么带标签**：单份清单与 `--merge-collections` 的读数差别很大
> （`has_title_rule` 0 vs 36、启用数 4000 vs 4417）。不带标签时两者会写同一个文件名、
> 后者静默覆盖前者，看起来像「读数变了」。加标签后两份并存，谁都看得出哪份是哪套口径。

写入前会调用 `report.assert_sanitized()` 自检：聚合读数里一旦出现
`bookSourceUrl` / `bookSourceName` / `bookSourceComment` / `header` / `url` 任一键名就直接报错，
防止将来误把身份字段带进可入库的报告。

## 分群口径

| 维度 | 口径 |
| --- | --- |
| 启用状态 | `enabled` 字段，全部报告同时给「全部」与「启用」两列（§12 第 1 条要求先处理启用集） |
| 书源类型 | `bookSourceType`：0 文本 / 1 音频 / 2 图片 / 3 文件 / 4 视频 |
| 结构指纹 | 对全源所有规则字段的 `{段.字段: 语法种类}` 排序后取 SHA-256 前 12 位。**不含任何选择器原文**，同指纹 = 同一套解析策略 |
| 能力特征 | 见下节旗标 |
| 图片样式 | `imageStyle` **大写归一**后分桶 |

### 规则语法分类（`rule_syntax.py`）

逐条对齐 App 实现，不引入工具自己的口味：

| 参考位置 | 内容 |
| --- | --- |
| `AnalyzeRule.kt:600-634` | 规则前缀与模式判定的 `when` 分支 |
| `AnalyzeRule.kt:644-648` | `@get:` / `{{}}` 命中把整片段模式**替换**为正则 |
| `AnalyzeRule.kt:682-708` | `##` 之前的 `$N` 捕获组触发正则模式 |
| `AnalyzeRule.kt:531-573` | JS/WebJS 片段先于其它前缀被摘出 |
| `AppPattern.kt:7-11` | `JS_PATTERN` / `WebJS_PATTERN` / `EXP_PATTERN` |

判定顺序：`@CSS:` → `@@` → `@XPath:` → `@Json:` → `$.` / `$[` → 以 `/` 开头 → 默认 CSS。
之后 `@get:` / `{{}}` / `##` 前的 `$N` 可把模式**升级并替换**为正则——所以 `@get:{c}`
记作 `get+regex`，而**不是** `css+get+regex`（把种类累加会和真实行为不符）。

`SourceRule.mode` 是单值，这是本工具最容易写错的地方，README 在此写明以免将来被改回去。

### 能力旗标

`has_search_url`、`has_explore_url`、`has_content_rule`、`has_replace_regex`、
`has_js_replace_regex`、`has_source_regex`、`has_next_content_url`、`has_webjs`、
`has_image_decode`、`has_pay_action`、`has_callback_js`、`has_title_rule`、`uses_js`、
`title_dedup_evidence`、`title_risk_layer`、`title_named_in_selector`、`title_named_in_js_only`、
`uses_webjs`、`content_uses_js`、`content_section_uses_js`、`has_login`、`has_cookie_jar`、
`has_concurrent_rate`、`has_book_url_pattern`、`has_variable_comment`、
`has_sensitive_header_substring`。

三档 JS 口径，**不要混用**：

| 旗标 | 范围 |
| --- | --- |
| `content_uses_js` | `ruleContent` 的 `content` / `subContent` 体含 JS/WebJS —— 与 §3.4 的「正文规则使用 JS/WebJS 1010」同口径 |
| `content_section_uses_js` | `ruleContent` 的**任意**字段含 JS/WebJS（含 `nextContentUrl`、`replaceRegex` 等） |
| `uses_js` | **任意**规则段的任意字段含 JS（最宽） |

## 离线静态检查：逐条判定口径

报告中每条都会列出计数与置信度，**零命中的检查同样列出**，便于确认口径生效。

### 确定性检查

| id | 判定口径 |
| --- | --- |
| `url_empty` | `bookSourceUrl` 为空 |
| `url_not_http` | `bookSourceUrl` 不以 `http://` 或 `https://` 开头（大小写不敏感） |
| `url_duplicate` | 同一输入内 `bookSourceUrl` 出现多次 |
| `no_content_rule` | **文本类**书源的 `content` 与 `subContent` 均空 |
| `no_content_rule_download_type` | 音频/图片/文件/视频类无正文规则。单列一条，因为下载型源缺正文属预期，§3.4 的「135 条需按类型判断」正是这两条之和 |
| `no_discovery` | `searchUrl` 与 `exploreUrl` 均空 |
| `search_book_list_empty` | 有 `searchUrl` 但 `ruleSearch.bookList` 为空 |
| `explore_book_list_empty` | `exploreUrl` 非空**且** `enabledExplore` 为真，但 `ruleExplore.bookList` 为空。发现被关掉时不计入——那不影响可用性 |
| `rule_prefix_empty` | 字段值恰好等于 `@css:` / `@xpath:` / `@json:` / `@js:` / `@webjs:` 之一，前缀后没有任何内容 |
| `replace_regex_unparsable` | `replaceRegex` **既不是 JS/WebJS 规则、又不含 `##` 段**。此时 `makeUpRule` 只会得到一个非空的选择器，`replaceRegex` 保持默认空串，替换逻辑整个不生效 |
| `replace_regex_hits_markup` | `##` 之后的第一段（match 段）会命中 `<img>`/`<a>`/`<p>`/`<div>`/`<br>`/`<span>`/`<script>` 或任意 `<...>`。对应 §6 B3.2 的「可能跨段、会命中图片/HTML 标签」风险类 |
| `replace_regex_trailing_pipe` | match 段去掉转义的 `\|` 后以 `\|` 结尾（末位分支为空）。§6 B3.3 预检测项 |
| `replace_regex_empty_alternative` | match 段去掉转义的 `\|` 后含 `\|\|`。§6 B3.3 预检测项 |
| `replace_regex_catastrophic_backtracking` | match 段含嵌套量词 `(...[+*]...)[+*]`，如 `(.*)+`。§6 B3.3 预检测项 |
| `title_rule_no_dedup` | `ruleContent.title` 非空，**且**没有任何去标题正则。`ContentRule.title` 的语义是「有些网站只能在正文中获取标题」（`ContentRule.kt:15`），它对正文页响应求值（`BookContent.kt:59,176-199`）、**只写 `bookChapter.title` 而从不修改 `contentStr`** —— 所以配了它等于「承认标题在正文里」，却没有任何去重手段 |
| `image_style_text` | `imageStyle` **大写**等于 `TEXT`。`TextChapterLayout.kt:530,579,949` 一律 `imageStyle?.uppercase()` 比较，所以判定必须大写归一；大小写敏感的朴素计数会少算 4 条 |
| `sensitive_header_keys` | `header` 解析后的**键名**分词后命中 `Cookie` / `Authorization` / `Token` / `Session` 之一 |
| `login_configured` | `loginUrl` / `loginUi` / `loginCheckJs` 任一非空 |
| `type_unknown` | `bookSourceType` 不是 0..4 的整数 |
| `last_update_stale` | `lastUpdateTime` 早于 `now - stale_days`（阈值见参数表） |
| `respond_time_missing` | `respondTime` 缺失、为 0 或非数值 |
| `respond_time_slow` | `respondTime` 超过 `--slow-respond-ms` |

### 低置信度启发式（**不构成书源缺陷结论**，§4.2）

| id | 判定口径 | 为什么低置信度 |
| --- | --- | --- |
| `js_bracket_mismatch` | JS/WebJS 片段体的 `{}` `[]` `()` 嵌套不配对 | 纯结构启发式：先线性剥离注释/字符串/模板串、把正则字面量替换成 `/x/`，再做嵌套配对。它不认识标签模板、装饰器一类的现代语法，也不能替代真实执行。只用于给 P1 的 JS 契约测试挑候选 |
| `next_content_url_self_ref` | `nextContentUrl` 与 `content` 字段**逐字相同** | 同值不一定错（某些站点两处写法确实一致），只是自引用翻页的典型形态 |
| `title_selector_named` | `content`/`subContent` 的**选择器部分**命中 `title` / `chapterName` / `章节名` / `h1` | **召回率很低**：绝大多数源的正文选择器是 `#content` / `.read-content` 这类容器名，标题节点藏在容器内部，选择器文本里根本看不出来。所以它只能证明「存在」，**不能证明「不存在」**——用它下「其余源没问题」的结论是错的 |
| `title_selector_named_js_only` | 同样的 token **只出现在 JS/WebJS 片段**里 | 那处 `title` 可能是 `document.title`、书名或无关变量，不必然是章节标题节点 |

### 关于「正文里出现标题」的分层口径

三条检查 + 两个旗标共同描述同一个问题（诊断卡 06），**它们的置信度不一样，不要混用**：

| 层 | 工具里的判据 | 置信度 |
| --- | --- | --- |
| **T** | 旗标 `has_title_rule` | **正向证据**。作者明写「标题要从正文页取」 |
| **T¬** | 检查 `title_rule_no_dedup` | **最有价值的一档**：有正向证据，且**没有**任何缓解手段 |
| **N** | 检查 `title_selector_named` | 启发式，召回低（见上表） |
| **Njs** | 检查 `title_selector_named_js_only` | 弱证据 |
| **RR** | 旗标 `title_dedup_evidence == replace_regex` | 作者自己承认过标题在正文里，**但已缓解** |

判定「去掉标题行」的**精确口径**（`_has_title_dedup_in_replace_regex`）：

1. 按 `AnalyzeRule.makeUpRule`（`AnalyzeRule.kt:762-779`）的真实语义，规则串 `split("##")` 后
   **`parts[1]` 才是匹配段**；
2. 两个来源都算：`ruleContent.replaceRegex` 字段本身，以及 `content`/`subContent` 里
   内嵌的 `##…` 尾段（`AnalyzeRule.kt:335,350-351`）；
3. **JS 形式的 `replaceRegex` 必须排除** —— 整串交给 JS 求值、不走 `##` 分段，
   对它切分只会误伤（快照里这类有 54 条 / 归并 55 条）；
4. 匹配段里出现 `title` / `chapterName` / `章节名` / `h1` 才算「去标题」，
   去广告类的交替分支不算。

同理，切分「选择器部分」必须走 `rule_syntax.split_segments()` 而**不能**按第一个 `##` 硬切：
`AnalyzeRule.kt:544-566` 是**先摘 JS 片段**再判前缀，所以 `@js:…##…` 里 `##` 之前的整段其实是 JS。
按 `##` 硬切会把这批 JS 误算成「选择器点名了标题」（实测 S 口径 96 → 修正后 11）。

> ⚠️ **静态判不出「某个源是否真的把标题渲染成可见行」**。上表全部是**代理指标**，
> 不得单独当作缺陷结论。诊断卡 06 第 3 节列出了六条误差来源，下界与静默层的区别也在那里。

### 关于 `js_bracket_mismatch` 的实现

初版用朴素字符计数，在 1300 个 JS 片段里报了 866 个不配对——误报率过高，不可用。
改成「线性扫描剥离字符串/注释 + 识别正则字面量 + 老老实实做嵌套配对」后降到 32。

两个必须保留的实现细节，改动前请先读：

1. **不能用回溯型正则识别 JS 正则字面量。** 在 5877 字符的片段上会回溯到超时。
   现在用的是逐字符线性扫描。
2. **配对必须是真正的栈式嵌套，不能用「见到 `[` 就跳到下一个 `]`」的偷懒写法**——
   `x[x[y]]` 这种嵌套会被判错。

### 关于 `replaceRegex` 的三段式语义

`AnalyzeRule.makeUpRule`（`AnalyzeRule.kt:709-780`）把 `replaceRegex` 按 `##` 切分：

| 写法 | `rule` | `replaceRegex` | `replacement` | `replaceFirst` | 实际语义 |
| --- | --- | --- | --- | --- | --- |
| `##pattern` | `""` | `pattern` | `""`（默认） | false | **删除**所有命中——这是正常写法，书源里大量用于去广告 |
| `##pattern##repl` | `""` | `pattern` | `repl` | false | 把所有命中替换成 `repl` |
| `##pattern##repl##` | `""` | `pattern` | `repl` | **true** | 只替换第一个命中 |
| `<js>…</js>` / `@js:` | — | — | — | — | 整串交给 JS 求值，**不走 `##` 分段** |

两个容易写错的地方，改动前请先读：

1. **`##pattern`（两段）不是「缺 replacement 段」**。`replacement` 的默认值是空串
   （`AnalyzeRule.kt:591`），语义是删除命中，是书源里的常规做法（实测 1544 条）。
   把它当成缺陷会造出上千条误报。
2. **JS 形式的 `replaceRegex` 必须先排除再做 `##` 与正则结构判定**。实测有 54 条书源
   （启用 26）把整段 JS 脚本写在 `replaceRegex` 里，对它们做 `##` 切分会切到 JS 代码中间，
   正则结构检查也会命中 JS 里的括号，全是误报。这类值用 `has_js_replace_regex` 旗标单独计数。

### 本工具**刻意不做**的检查

| 检查 | 为什么不做 |
| --- | --- |
| `unbalanced_delimiters`（非 JS 规则的括号/引号配对） | 实测误报率过高。书源的 `##正则` 段大量使用 `\(`、`\)`、`\]`、`[（【(]` 这类转义与字符类，纯字符计数会全判成不配对，而真正的配对判定需要正则解析器。宁可少一条检查，也不发布一份以噪声为主的清单 |
| `regex_not_compilable`（用 Python 正则试编译） | Python 与 Java 正则方言不同（`\p{...}`、占有量词等），拿 Python 的编译结果判 Java 规则合法性会系统性误报 |

## 与 §3.4 快照读数对照

报告末尾有一张对照表，逐项列出 §3.4 的读数与本工具的读数。**§3.4 的读数是 2026-09-21 的
一次性人工盘点，不是验收基数**；§3.4 明确「数字变化不等于回归」，所以口径不同或数值有出入
的项只写清差异来源，不调阈值凑数。

已知的口径差异：

- **正文规则使用 JS/WebJS**：§3.4 的 1010 只数 `ruleContent` 的 `content`/`subContent` 体。
  本工具的 `content_section_uses_js`（1151）与 `uses_js`（3156）是更宽的口径，对照表里
  用的是同口径的 `content_uses_js`。
- **请求头含敏感键名**：§3.4 的「≥239」是拿敏感词在**原始请求头字符串**里做子串匹配
  （本工具用 `has_sensitive_header_substring` 复现，得 239）。按解析后的**键名**判定
  （`sensitive_header_keys`）只得 164。两个读数都列出，键名口径才是能落到代码的结论。
- **配置登录**：§3.4 记 790，本工具按 `loginUrl`/`loginUi`/`loginCheckJs` 任一非空得 793。
  差 3 条，如实列出。

## 归并模式与单份清单会得到不同的读数

`bookSource.json` 与 `--merge-collections` 的**总量一致**（都是 6139 条、6139 个唯一 URL），
但**逐项读数不同**，这不是 bug：

压缩包内其余 15 份合集里有同一 `bookSourceUrl` 的**不同副本**。归并时按 `lastUpdateTime`
取更新的那一份，于是有 20—40 条记录的规则体被换成了另一份合集里的版本。实测差异（2026-09-22）：

| 读数 | `bookSource.json` | `--merge-collections` |
| --- | --- | --- |
| 结构指纹种类 | 3812 | 3826 |
| `has_title_rule` | 0 | 36 |
| `title_rule_no_dedup`（T 且无去标题正则） | 0 | 33 |
| `title_dedup_evidence == replace_regex` | 160 | 161 |
| `title_selector_named` | 11 | 11 |
| `content_uses_js` | 1010 | 1012 |
| `no_content_rule` + 下载型 | 89 + 46 | 86 + 49 |
| `replace_regex_trailing_pipe` | 43 | 44 |

**两份读数都发布到 `docs/book-source-audit/`**，文件名用 `--label` 区分口径：

| 文件 | 口径 | 用途 |
| --- | --- | --- |
| `<date>-单份清单-静态分群报告.md` | `bookSource.json` | **对 §3.4 基线**。逐项复现了 §3.4 的全部数字（6139 / 4000 / 5805·109·188·37 / 6004 / 1635 / 2030 / 1010 / 45·21 / 239） |
| `<date>-归并合集-静态分群报告.md` | `--merge-collections` | **全库视角**。16 份合集归并去重后的读数 |

在此之前只发布了单份清单那一份，导致按「全库」说话的段落引用了一个自己标注「未归并」的数字
（`has_title_rule` 报 0，实为 36）—— 见诊断卡 06 第 3 节。**引用任何读数前先看文件名里的口径标签。**
两套口径**不要**互相比较，也不要拿归并读数去对 §3.4。

## 已知限制

- **JSON 响应下的 `Mode.Default` 无法静态判定。** 响应是 JSON 时 Default 会走 JSONPath 而非
  CSS，但响应格式只有运行时才知道。因此裸选择器一律记为 `css`，指纹与语法分类在此处会有偏差。
- **`imageStyle` 的无效值不做检查。** 数据里存在 `"0"`、`"0.0"` 等非规范值，它们不匹配
  `TEXT`/`FULL`/`SINGLE` 中任何一个，落到默认分支，不构成缺陷，故不单列检查。
- **`rule_prefix_empty` 只认「整字段恰好等于前缀」。** `@js: ` 后面跟空白也会命中（比较前
  `strip()`），但前缀后跟注释一类的情况不认为是空体。
- **静态检查不能替代真实站点验证。** P0 的退出条件是「每个准备实施的问题都能离线复现，
  或明确标记为『仅真实站点可复现』」。本工具的结论要么是离线可复现的事实，要么已在报告里
  标注置信度。

## 新增书源后怎么重跑

工具不写死任何条数，重跑即自动纳入新书源：

```bash
python tools/book-source-audit/audit.py --source 阅读书源.zip --merge-collections --publish docs/book-source-audit
```

## P0 自检

P0 的工具和冻结样本不依赖 Gradle。改动本目录或
`app/src/test/resources/bookSourceBaseline/` 后，先运行：

```bash
python -X utf8 tools/book-source-audit/verify_p0.py
python -X utf8 -m unittest discover -s tools/book-source-audit/tests -v
```

`verify_p0.py` 会检查：样本目录/id 一致、JSON 可读、`synthetic=true`、不变量引用存在、
场景文件存在、每例至少有一个实际输入，以及样本 URL 只使用 `.invalid` / `.test` / `.example`
保留域名。单测则锁住规则分类、敏感 Header 键名口径、聚合脱敏和确定性输出。

这组自检只证明 **P0 本地产物自洽**；它不能替代真实站点请求、真机渲染或 P1 的 Kotlin
契约测试。

发布目录里按生成日期出 `<YYYY-MM-DD>-静态分群报告.md` 与 `summary.json`，后者会被覆盖为
最新一次运行的读数。要对比两批快照，分别导出到不同 `--out` 再 diff 两份 `summary.json`。
