# 冻结兼容样本（P0）

《书源与解析优化计划书》§7 P0 的第 3、5 项交付物：**脱敏最小内容样本**与**冻结兼容样本**。

> **全部内容为合成数据。** 所有 URL 使用保留域名（`example.invalid`、`novel.test`、
> `img.example.invalid`），没有任何真实站点、真实正文、Cookie、请求头或登录数据。
> 本目录可以直接入库。

## 这里有什么

| 用例 | kind | 覆盖 |
| --- | --- | --- |
| `case-01-html-basic` | html | 普通 HTML：块标签、实体、NBSP、注释、嵌套行内标签 |
| `case-02-json-api` | json-api | JSON 接口：`ruleContent` 走 JSONPath，正文体仍是 HTML |
| `case-03-next-page` | next-page | 正文分页：两页 + 页边界成片空白 |
| `case-04-inline-img` | inline-img | 文本内插图：绝对/相对 URL、单双引号、`data-src`、逗号花括号参数、相邻图片 |
| `case-05-pure-image` | pure-image | 纯图片书：连续 `img`，含同标签同时带 `src` 与 `data-src` |
| `case-06-poetry-blank` | poetry-blank | 诗歌空行：`<br>` 与 `<br><br>` 的换行语义 |
| `case-07-existing-replace` | existing-replace | 书源内 `replaceRegex` 与用户 `ReplaceRule` 的组合与顺序 |
| `case-08-punctuation` | punctuation | 正常中文/英文/全角标点与符号（「多出符号」的对照基准） |
| `case-09-title-in-body` | title-in-body | 正文里出现章节标题：7 个场景，命中与不命中两种结果 |
| `case-10-entity-newline` | entity-newline | 实体形式的换行与空白：`&#10;`×4 / `&#160;` / `&#12288;`，含真实字符对照组 |

`case-01` … `case-05` 对应 P0 的「3—5 个脱敏最小内容样本」五类：普通 HTML、JSON 接口、
正文分页、文本内插图、纯图片书。纯图片书另有一项「图片字专项」需要 1 个**真实**目标源，
见 `docs/book-source-audit/诊断卡/PENDING.md`——本目录不提供，也不应提供。

`case-06` … `case-08` 加上 `case-04` 的相邻图片部分，覆盖 §6 B2 点名的
「正常标点、诗歌空行、相邻图片、特殊 HTML、已有替换规则」五项。

`case-09` 与 `case-10` 是 2026-09-22 之后补的两条，都不来自 §6 B2 的清单，而来自
**实际发现的缺口**：

- `case-09` — 你追问的「**正文出现标题**」。此前零覆盖：不在 `INVARIANTS.md`、
  不在任何用例、不在诊断卡里。它是一个真实缺口（约 130—300 个源，中心约 200），
  详见 [`诊断卡/06-正文出现标题.md`](../../../../docs/book-source-audit/诊断卡/06-正文出现标题.md)。
- `case-10` — 语料归因时发现的**实体解码次序**问题：`HtmlFormatter` 的换行归一架在
  实体解码之前，`&#10;` 躲过归一、解码后变成不再受任何约束的真换行。
  这是本目录唯一一张能把「格式化后 / 解码后」两段**离线量出来**的用例，
  详见 [`诊断卡/07-实体解码与格式化次序.md`](../../../../docs/book-source-audit/诊断卡/07-实体解码与格式化次序.md)。

> ⚠️ `case-09` 带一处**方向冲突**：语料 D03 预期「保留标题」、I01 预期「删掉重复行」，
> 而现存缺陷是「标题行没被删掉」。「删」与「不删」定错就会把它们改坏。
> 本目录只冻结现象，**方向待你裁决**，见 `INVARIANTS.md` 的 K 节。

## 每个用例的结构

```
case-XX-name/
  case.json    元信息：id / kind / synthetic / target_stage / purpose / rules / notes / invariants
  input.*      合成输入。HTML 用例是 input.html，分页用例有 page-1.html + page-2.html，
               JSON 用例是 input.json
```

`case.json` 的 `rules` 只写该用例**用到**的那几个规则字段，不是完整书源；
`invariants` 指向 `INVARIANTS.md` 里的不变量 id。

## 本目录**不含** golden 输出

这是刻意的取舍，已与你确认：P0 只冻结**输入**与**不变量**，零 Kotlin 改动。
录制 golden 属 P1——P1 的契约测试要为 `AnalyzeRule` / `HtmlFormatter` / `ContentProcessor`
建覆盖，那时把输出录下来，才能让这些样本真正变成回归基线。

在 golden 落地之前，本目录的作用是：

1. 固定「要保住什么」——`INVARIANTS.md` 逐条给出正例与反例；
2. 让 P1 的测试有确定的输入，不必再去真实站点取内容；
3. 让 P3 的格式改动有一个**改动前**的对照物。

## P1 怎么用它录制 golden

1. 对每个用例构造 `BookSource`（用 `case.json` 的 `rules`）+ `Book` + `BookChapter`；
2. 走 `BookContent.getContent()` → `ContentProcessor.getContent()` 真实链路，
   不要绕过任何一步——`case-07` 的价值恰恰在于两处按行 `trim` 的位置；
3. `case-04` 需要固定 `redirectUrl = https://novel.test/chapter/12`，否则相对地址拼接结果不稳定；
4. 录到的输出写进 `golden/`，**每条断言带上对应的不变量 id**，失败时能直接指向
   `INVARIANTS.md` 的哪一行；
5. golden 只固定**当前行为**。`INVARIANTS.md` 的「已知的取舍」一节写明：现有实现在
   「缩进」与「诗歌空行」上存在必然冲突，golden 会把这个冲突一并录下来——这是记录，
   不是背书。

`INVARIANTS.md` 里标【待录制】的两条（`INV-FMT-ENTITY-4`、`INV-FMT-COMMENT-2`）
依赖 jsoup 的解析与转义行为，本次只做了静态阅读，P1 录制时才确定取值。

## 真实样本的入库规则

真实站点内容**一律不入库**（《书源与解析优化计划书》§3.4 安全边界）。若确实需要一个
真实样本才能复现某个问题：

1. 先做**脱敏最小化**：只留复现所需的最小片段，删掉站点名、域名、广告、导航；
2. 域名换成保留域（`example.invalid`）；
3. 正文替换成合成文字，只保留**触发问题的那个结构**（标签、属性、空白）；
4. 经你明确批准后才写入本目录，并在 `case.json` 里把 `synthetic` 标成 `false`
   并写明批准来源。

未满足以上四条的真实内容，一律只留在本机，不进仓库。

## 洁净性自检

入库前对本目录做两项检查，两项都必须通过：

```bash
grep -rn "http" app/src/test/resources/bookSourceBaseline/
```

只应命中保留域 `example.invalid` / `novel.test`（2026-09-22 实测：15 处，全部为这两个域）。

```bash
grep -rniE "cookie|authorization|token|session|apikey|secret" app/src/test/resources/bookSourceBaseline/
```

数据文件应零命中。**唯一允许的命中是 `README.md` 自身的说明文字**（本节的命令与上一段的
「没有任何真实站点、真实正文、Cookie、请求头或登录数据」），实测即如此。
`case-04` 的 `src` 里有一个 `"headers"` 键名（逗号花括号参数的样例），它不在敏感词表内，
也不含任何凭据取值。
