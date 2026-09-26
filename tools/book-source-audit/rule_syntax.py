"""legado 书源规则语法分类器（离线、只读、不执行任何规则）。

本模块逐条对齐 App 的真实实现，不引入工具自己的口味。参考位置：

- 规则前缀与模式判定          ``AnalyzeRule.SourceRule`` init（AnalyzeRule.kt:600-677）
- ``$N`` 捕获组与正则模式判定  ``AnalyzeRule.splitRegex``（AnalyzeRule.kt:682-708）
- JS / WebJS 片段识别          ``AppPattern.JS_PATTERN`` / ``WebJS_PATTERN``（AppPattern.kt:7-11）
- 规则分段                     ``AnalyzeRule.splitSourceRule``（AnalyzeRule.kt:531-573）

**只做语法与结构判定，绝不执行规则**——书源里 1010 条正文规则带 JS/WebJS，静态分析能覆盖
的只有语法/结构（《书源与解析优化计划书》§3.4）。
"""

from __future__ import annotations

import re

# —— 与 AppPattern.kt 逐字对应的模式（Kotlin 的 [\w\W] 即“任意字符，含换行”）——————————
JS_PATTERN = re.compile(r"<js>([\w\W]*?)</js>|@js:([\w\W]*)", re.IGNORECASE)
WEBJS_PATTERN = re.compile(r"@webjs:([\w\W]{5,})", re.IGNORECASE)
EXP_PATTERN = re.compile(r"\{\{([\w\W]*?)\}\}")

# —— 与 AnalyzeRule.companion object 逐字对应的模式（AnalyzeRule.kt:946-949）———————————
PUT_PATTERN = re.compile(r"@put:(\{[^}]+?\})", re.IGNORECASE)
EVAL_PATTERN = re.compile(r"@get:\{[^}]+?\}|\{\{[\w\W]*?\}\}", re.IGNORECASE)
REGEX_CAPTURE_PATTERN = re.compile(r"\$\d{1,2}")

#: 规则片段里出现的语法标记。同一个字段可以同时命中多个（例如 CSS 选择器后面接 ``@js:``）。
KIND_CSS = "css"
KIND_XPATH = "xpath"
KIND_JSON = "jsonpath"
KIND_REGEX = "regex"
KIND_JS = "js"
KIND_WEBJS = "webjs"
KIND_EXPR = "expr"
KIND_GET = "get"
KIND_PUT = "put"

#: 结构性标记，用于区分“同一套语法、不同的拼接方式”的规则。
KIND_CHAIN = "chain"  # 出现 ## 分段
KIND_COMBINE = "combine"  # 出现 || / && / %%
KIND_ALLINONE = "allinone"  # @@ 前缀

ALL_KINDS = frozenset(
    {
        KIND_CSS,
        KIND_XPATH,
        KIND_JSON,
        KIND_REGEX,
        KIND_JS,
        KIND_WEBJS,
        KIND_EXPR,
        KIND_GET,
        KIND_PUT,
        KIND_CHAIN,
        KIND_COMBINE,
        KIND_ALLINONE,
    }
)

_COMBINE_PATTERN = re.compile(r"\|\||&&|%%")

#: 显式模式前缀，供 ``find_empty_prefix`` 复用。
_PREFIXES = ("@css:", "@xpath:", "@json:", "@js:", "@webjs:")


def split_segments(rule: str) -> list[tuple[str, bool]]:
    """按 ``AnalyzeRule.splitSourceRule`` 的顺序把规则切成 ``(片段, 是否JS)``。

    JS/WebJS 片段先于其它前缀判定被摘出（AnalyzeRule.kt:544-566），所以
    ``.box@html<js>…</js>`` 是两个片段，而不是一条 JS 规则。
    """
    segments: list[tuple[str, bool]] = []
    start = 0
    for matcher in JS_PATTERN.finditer(rule):
        if matcher.start() > start:
            head = rule[start:matcher.start()].strip()
            if head:
                segments.append((head, False))
        body = matcher.group(2) if matcher.group(2) is not None else matcher.group(1)
        segments.append((body or "", True))
        start = matcher.end()
    for matcher in WEBJS_PATTERN.finditer(rule):
        if matcher.start() > start:
            head = rule[start:matcher.start()].strip()
            if head:
                segments.append((head, False))
        segments.append((matcher.group(1) or "", True))
        start = matcher.end()
    if len(rule) > start:
        tail = rule[start:].strip()
        if tail:
            segments.append((tail, False))
    return segments


def detect_js_fragments(rule: str) -> list[str]:
    """只返回 JS/WebJS 片段的源码文本，供结构检查使用（不做任何求值）。"""
    return [text for text, is_js in split_segments(rule) if is_js]


def _classify_segment(text: str) -> set[str]:
    """判定**单个非 JS 片段**的语法种类。

    ``SourceRule`` 的 ``mode`` 是**单值**：``when`` 分支先定出模式，随后 ``@get:``/``{{}}``
    的命中可能把整个片段升级为正则（AnalyzeRule.kt:644-648）。所以这里也是「先定模式、
    再替换」，而不是把种类累加——把 ``@get:{c}`` 同时算作 CSS 和正则会和真实行为不符。
    """
    if not text:
        return set()

    kinds: set[str] = set()
    lowered = text.lower()

    if text.startswith("@@"):
        # @@ 剥掉前缀后模式仍是 Default，即 CSS（AnalyzeRule.kt:608-611）。
        mode = KIND_CSS
        kinds.add(KIND_ALLINONE)
    elif lowered.startswith("@css:"):
        mode = KIND_CSS
    elif lowered.startswith("@xpath:"):
        mode = KIND_XPATH
    elif lowered.startswith("@json:"):
        mode = KIND_JSON
    elif text.startswith("$.") or text.startswith("$["):
        mode = KIND_JSON
    elif text.startswith("/"):
        # “XPath 特征很明显，无需配置单独的识别标头”（AnalyzeRule.kt:628）
        mode = KIND_XPATH
    else:
        # Mode.Default 走 AnalyzeByJSoup，即 CSS 选择器。
        # 注意：响应是 JSON 时 Default 会变成 JSONPath，那是运行期才知道的事，
        # 静态无法区分——README 已写明这一限制。
        mode = KIND_CSS

    if PUT_PATTERN.search(text):
        kinds.add(KIND_PUT)
    if "@get:" in lowered:
        kinds.add(KIND_GET)
    if EXP_PATTERN.search(text):
        kinds.add(KIND_EXPR)

    # 正则升级：命中 @get:/{{}} 且不在 ## 链头部时，整片段模式被替换为正则。
    escalated = False
    eval_match = EVAL_PATTERN.search(text)
    if eval_match is not None:
        head = text[: eval_match.start()]
        if eval_match.start() == 0 or "##" not in head:
            escalated = True
    # splitRegex 只看 ## 之前那一段里的 $N
    if REGEX_CAPTURE_PATTERN.search(text.split("##", 1)[0]):
        escalated = True

    kinds.add(KIND_REGEX if escalated else mode)
    return kinds


def classify(rule: str | None) -> tuple[str, ...]:
    """返回该字段命中的语法种类，排序后去重。

    判定顺序与 ``AnalyzeRule.SourceRule`` 的 ``when`` 分支一致：
    ``@CSS:`` → ``@@`` → ``@XPath:`` → ``@Json:`` → ``$.``/``$[`` → ``/`` 开头，
    之后再由 ``@get:``/``{{}}``/``$N`` 把模式升级为正则。JS/WebJS 片段单独成段。
    """
    if rule is None:
        return ()
    text = rule.strip()
    if not text:
        return ()

    kinds: set[str] = set()
    for segment, is_js in split_segments(text):
        if not is_js:
            kinds.update(_classify_segment(segment))

    # JS/WebJS 的种类标记按整串判定，避免在分段里重复推导。
    if JS_PATTERN.search(text):
        kinds.add(KIND_JS)
    if WEBJS_PATTERN.search(text):
        kinds.add(KIND_WEBJS)

    if "##" in text:
        kinds.add(KIND_CHAIN)
    if _COMBINE_PATTERN.search(text):
        kinds.add(KIND_COMBINE)

    return tuple(sorted(kinds))


def find_empty_prefix(rule: str | None) -> str | None:
    """返回该字段里“有前缀、无内容”的前缀（如 ``@js:`` 后面什么都没有），否则 None。"""
    if not rule:
        return None
    lowered = rule.strip().lower()
    for prefix in _PREFIXES:
        if lowered == prefix:
            return prefix
    return None
