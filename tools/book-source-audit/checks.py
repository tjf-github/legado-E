"""书源归一化、特征旗标、结构指纹与离线静态检查。

本模块只读、离线、不执行任何书源规则。所有检查都给出**明确 id 与判定口径**，
报告里逐项列出，避免把「工具的口味」当成书源的问题（《书源与解析优化计划书》§4.2）。

刻意**不包含**的两类检查，以及原因：

- ``unbalanced_delimiters``（非 JS 规则的括号/引号配对）：实测误报率过高。书源的
  ``##正则`` 段大量使用 ``\\(``、``\\)``、``\\]``、``[（【(]`` 这类转义与字符类，
  纯粹的字符计数会把它们全判成不配对，而真正的配对判定需要正则解析器。
  宁可少一条检查，也不发布一份以噪声为主的清单。
- ``regex_not_compilable``（用 Python 正则试编译）：Python 与 Java 的正则方言不同
  （``\\p{...}``、占有量词等），拿 Python 的编译结果去判 Java 规则合法性会系统性误报。
"""

from __future__ import annotations

import hashlib
import json
import re
from typing import Any

from rule_syntax import (
    KIND_JS,
    KIND_WEBJS,
    split_segments,
    classify,
    detect_js_fragments,
    find_empty_prefix,
)

# —— 书源类型（BookSourceType）———————————————————————————————————————————————
SOURCE_TYPES = {0: "text", 1: "audio", 2: "image", 3: "file", 4: "video"}

#: 这些类型常见「纯下载型」书源，没有正文规则属于预期，不按缺失计（§3.4 的 135 条需按类型判断）。
DOWNLOAD_ORIENTED_TYPES = frozenset({1, 2, 3, 4})

# —— 规则字段目录，字段名取自 entities/rule/ 下的实体类 ——————————————————————————
SEARCH_FIELDS = (
    "checkKeyWord", "bookList", "name", "author", "intro", "kind",
    "lastChapter", "updateTime", "bookUrl", "coverUrl", "wordCount",
)
EXPLORE_FIELDS = (
    "bookList", "name", "author", "intro", "kind",
    "lastChapter", "updateTime", "bookUrl", "coverUrl", "wordCount",
)
BOOK_INFO_FIELDS = (
    "init", "name", "author", "intro", "kind", "lastChapter", "updateTime",
    "coverUrl", "tocUrl", "wordCount", "canReName", "downloadUrls",
)
TOC_FIELDS = (
    "preUpdateJs", "chapterList", "chapterName", "chapterUrl", "formatJs",
    "isVolume", "isVip", "isPay", "updateTime", "nextTocUrl",
)
CONTENT_FIELDS = (
    "content", "subContent", "title", "nextContentUrl", "webJs", "sourceRegex",
    "replaceRegex", "imageStyle", "imageDecode", "payAction", "callBackJs",
)

RULE_SECTIONS: dict[str, tuple[str, ...]] = {
    "ruleSearch": SEARCH_FIELDS,
    "ruleExplore": EXPLORE_FIELDS,
    "ruleBookInfo": BOOK_INFO_FIELDS,
    "ruleToc": TOC_FIELDS,
    "ruleContent": CONTENT_FIELDS,
}

# —— 检查组定义。每条都会在报告里单独列出计数与口径说明。———————————————————————
CHECK_CATALOG: dict[str, str] = {
    "url_empty": "bookSourceUrl 为空",
    "url_not_http": "bookSourceUrl 不以 http(s):// 开头",
    "url_duplicate": "同一快照内 bookSourceUrl 重复",
    "no_content_rule": "文本类书源没有任何正文规则（content/subContent 均空）",
    "no_content_rule_download_type": "音频/图片/文件/视频类无正文规则（通常为下载型，属预期）",
    "no_discovery": "searchUrl 与 exploreUrl 均空，无搜索也无发现入口",
    "search_book_list_empty": "有 searchUrl 但 ruleSearch.bookList 为空",
    "explore_book_list_empty": "启用了发现且 exploreUrl 非空，但 ruleExplore.bookList 为空",
    "rule_prefix_empty": "规则只写了前缀（如 @js:），前缀后没有任何内容",
    "js_bracket_mismatch": "JS/WebJS 片段体的 {} [] () 嵌套不配对（**低置信度启发式**，未执行代码）",
    "replace_regex_unparsable": "replaceRegex 既非 JS 规则、又不含 ## 段，替换逻辑不会生效",
    "replace_regex_hits_markup": "replaceRegex 的匹配段会命中 <img> 或 HTML 标签，存在误伤图片/结构片段的风险",
    "replace_regex_trailing_pipe": "replaceRegex 匹配段以未转义的 | 结尾，末位分支为空",
    "replace_regex_empty_alternative": "replaceRegex 匹配段含未转义的 ||，存在空分支",
    "replace_regex_catastrophic_backtracking": "replaceRegex 匹配段含嵌套量词（如 (.*)+ ），有灾难性回溯风险",
    "next_content_url_self_ref": "nextContentUrl 与 content 规则同值，存在自引用翻页风险（低置信度）",
    "title_rule_no_dedup": "配了 ruleContent.title（从正文里取标题）却没有任何去标题正则——有正向证据且无缓解手段",
    "title_selector_named": "content/subContent 的**选择器部分**点名了标题节点（title/h1/chapterName），存在正文里带标题的风险",
    "title_selector_named_js_only": "标题标识只出现在 content/subContent 的 **JS 片段**里（弱证据，可能是 document.title 或无关变量）",
    "image_style_text": "imageStyle 大写等于 TEXT（图片字路径）",
    "sensitive_header_keys": "请求头**键名**命中 Cookie/Authorization/Token/Session",
    "login_configured": "配置了 loginUrl / loginUi / loginCheckJs 之一",
    "type_unknown": "bookSourceType 不在 0..4",
    "last_update_stale": "lastUpdateTime 早于 --stale-days 阈值",
    "respond_time_missing": "respondTime 缺失或为 0，无耗时基线",
    "respond_time_slow": "respondTime 超过 --slow-respond-ms 阈值",
}

#: 低置信度启发式：只能作为 P1 挑候选的线索，**不得**当作书源缺陷结论（§4.2）。
LOW_CONFIDENCE_CHECKS = frozenset(
    {
        "js_bracket_mismatch",
        "next_content_url_self_ref",
        # 标题类三条中，只有 title_rule_no_dedup 是正向证据（见下），另两条是启发式。
        "title_selector_named",
        "title_selector_named_js_only",
    }
)

#: 判定「正文出现标题」风险时，选择器里出现下列 token 即算「点名了标题节点」。
#: 注意 **召回率很低**：绝大多数源的正文选择器是 #content / .read-content 这类容器名，
#: 标题节点藏在容器内部，选择器文本里根本看不出来 —— 所以这条只能证明存在，
#: 不能证明不存在（诊断卡 06 的 134 是下界而不是估计值）。
_TITLE_TOKEN_PATTERN = re.compile(
    r"chapterName|chapterTitle|章节名|章节标题|章名|\bh1\b|\btitle\b",
    re.IGNORECASE,
)

#: 「已用 replaceRegex 去掉标题行」的判据：匹配段（## 之后的第一段）里出现标题标识。
#: JS 形式的 replaceRegex 不走 ## 分段，必须先排除，否则切分只会误伤。
_TITLE_IN_MATCH_PATTERN = re.compile(
    r"chapterName|chapterTitle|章节名|\btitle\b|\bh1\b",
    re.IGNORECASE,
)

#: §3.4 正文点名的四个敏感键名。工具只比对**键名**，永不读取或输出取值。
STRICT_SENSITIVE_TOKENS = frozenset({"cookie", "authorization", "token", "session"})
#: 更宽的凭据类键名，单独计一列，不与 §3.4 的读数混用。
BROAD_SENSITIVE_TOKENS = frozenset(
    {"secret", "key", "apikey", "api_key", "auth", "bearer", "signature", "sign", "sid"}
)

#: §3.4 用的是「原始请求头字符串里出现敏感词」这一更宽的口径（命中 239 条）。
#: 工具同时给出这个读数，以便与 §3.4 对照，但不把它当作键名结论。
SENSITIVE_SUBSTRING_PATTERN = re.compile(
    r"cookie|authorization|token|session", re.IGNORECASE
)

BACKSLASH = chr(92)

#: match 段里是否会出现 HTML 标记：直指标签名，或常见的“去标签”通配。
_MARKUP_HIT_PATTERN = re.compile(
    r"<\s*/?\s*(?:img|a|p|div|br|span|script)\b|<[^>]*>", re.IGNORECASE
)

#: §6 B3.3 点名的「明显灾难性回溯」形态：量词套在可重复分组外面。
_CATASTROPHIC_PATTERN = re.compile(r"\([^()]*[+*][^()]*\)[+*]")


def normalize_rule(value: Any) -> dict[str, str]:
    """按 ``entities/rule/*.kt`` 的 jsonDeserializer 语义归一化规则段。

    实体类同时接受对象与 JSON 字符串两种写法（ContentRule.kt:30-38），工具照做。
    """
    if value is None:
        return {}
    if isinstance(value, dict):
        raw = value
    elif isinstance(value, str):
        text = value.strip()
        if not text:
            return {}
        try:
            parsed = json.loads(text)
        except (ValueError, TypeError):
            return {"__unparsed__": value}
        if not isinstance(parsed, dict):
            return {"__unparsed__": value}
        raw = parsed
    else:
        return {}
    return {str(k): v for k, v in raw.items() if isinstance(v, str) and v}


def parse_header_keys(header: Any) -> list[str]:
    """只返回请求头的**键名**。取值一律丢弃，不进入任何输出。"""
    if not header:
        return []
    if isinstance(header, str):
        try:
            parsed = json.loads(header)
        except (ValueError, TypeError):
            return []
        if not isinstance(parsed, dict):
            return []
    elif isinstance(header, dict):
        parsed = header
    else:
        return []
    return sorted({str(k) for k in parsed})


def _tokenize_key(name: str) -> set[str]:
    return {part for part in re.split(r"[^A-Za-z0-9]+", name.lower()) if part}


def sensitive_key_names(header_keys: list[str]) -> tuple[list[str], list[str]]:
    """返回 ``(strict, broad)`` 两组命中的键名。

    strict 对应 §3.4 点名的 Cookie/Authorization/Token/Session；broad 补上 key/secret 一类。
    两组分开计数，避免与 §3.4 的快照读数口径混淆。
    """
    strict: list[str] = []
    broad: list[str] = []
    for key in header_keys:
        tokens = _tokenize_key(key)
        if tokens & STRICT_SENSITIVE_TOKENS:
            strict.append(key)
        elif tokens & BROAD_SENSITIVE_TOKENS:
            broad.append(key)
    return sorted(set(strict)), sorted(set(broad))


# —— JS 结构检查：线性扫描，绝不对规则求值 ——————————————————————————————————

#: 这些字符之后出现的 ``/`` 更可能是正则字面量的开头，而不是除法。
_REGEX_PRECEDER_CHARS = set("(,=:[!&|?{};+-*%~^<>")


def strip_js_noise(source: str) -> str:
    """剥离注释、字符串、模板串，并把正则字面量替换成 ``/x/``。

    线性扫描，不使用回溯型正则——在数千字符的脚本上，回溯写法会慢到不可接受。
    """
    out: list[str] = []
    index = 0
    length = len(source)
    last_significant = ""
    while index < length:
        char = source[index]
        nxt = source[index + 1] if index + 1 < length else ""
        if char == "/" and nxt == "/":
            end = source.find("\n", index)
            index = length if end < 0 else end
            continue
        if char == "/" and nxt == "*":
            end = source.find("*/", index + 2)
            index = length if end < 0 else end + 2
            continue
        if char in "'\"`":
            quote = char
            index += 1
            while index < length:
                if source[index] == BACKSLASH:
                    index += 2
                elif source[index] == quote:
                    index += 1
                    break
                else:
                    index += 1
            out.append("''")
            last_significant = "'"
            continue
        if char == "/" and (last_significant == "" or last_significant in _REGEX_PRECEDER_CHARS):
            index += 1
            in_class = False
            while index < length:
                cur = source[index]
                if cur == BACKSLASH:
                    index += 2
                    continue
                if cur == "[":
                    in_class = True
                elif cur == "]":
                    in_class = False
                elif cur == "/" and not in_class:
                    index += 1
                    break
                elif cur == "\n":
                    break
                index += 1
            out.append("/x/")
            last_significant = "/"
            continue
        out.append(char)
        if not char.isspace():
            last_significant = char
        index += 1
    return "".join(out)


def check_js_balanced(source: str) -> bool:
    """JS 片段体的括号嵌套是否配对。

    **低置信度**：这是纯结构启发式，不认识 JS 语法（例如标签模板、``?.``、装饰器），
    也不能替代真实执行。它只用来给 P1 的 JS 契约测试挑候选，不能当作书源缺陷结论。
    """
    cleaned = strip_js_noise(source)
    stack: list[str] = []
    pairs = {"}": "{", "]": "[", ")": "("}
    index = 0
    length = len(cleaned)
    while index < length:
        char = cleaned[index]
        if char == BACKSLASH:
            index += 2
            continue
        if char in "{[(":
            stack.append(char)
        elif char in pairs:
            if not stack or stack.pop() != pairs[char]:
                return False
        index += 1
    return not stack


def structure_fingerprint(rule_sections: dict[str, dict[str, str]]) -> str:
    """规则结构指纹：``{段.字段: 语法种类}`` 排序后取 SHA-256 前 12 位。

    指纹只描述**解析策略的形状**，不含任何选择器原文、URL 或站点信息，
    因此可以安全地用于分群与聚合报告。
    """
    shape: list[str] = []
    for section in sorted(rule_sections):
        fields = rule_sections[section]
        for field in sorted(fields):
            kinds = classify(fields[field])
            if kinds:
                shape.append(f"{section}.{field}={'+'.join(kinds)}")
    digest = hashlib.sha256("\n".join(shape).encode("utf-8")).hexdigest()
    return digest[:12]


def url_hash(url: str) -> str:
    """逐源脱敏标识：bookSourceUrl 的 SHA-256 前 12 位。"""
    return hashlib.sha256(url.encode("utf-8")).hexdigest()[:12]


def _replace_regex_checks(pattern: str, checks: list[str]) -> None:
    """§6 B3.3 的正则预检测。只做可证明的结构判定，不试编译、不试匹配。"""
    if not pattern:
        return
    # 转义过的 \| 不算分支分隔符
    unescaped = pattern.replace(BACKSLASH + "|", "")
    if unescaped.endswith("|"):
        checks.append("replace_regex_trailing_pipe")
    if "||" in unescaped:
        checks.append("replace_regex_empty_alternative")
    if _CATASTROPHIC_PATTERN.search(pattern):
        checks.append("replace_regex_catastrophic_backtracking")


def _split_selector_and_tail(content: dict[str, Any]) -> tuple[str, str]:
    """把 content/subContent 拆成「选择器部分」与「其余（JS 片段 + ## 尾段）」。

    判定「正文里带标题」时这两部分的可信度完全不同：
    选择器里点名 title/h1 是作者**明确要去选那个节点**；
    而 JS 片段里出现 title 可能只是 `document.title` 或无关变量。

    必须走 ``split_segments`` 而不是简单 ``partition("##")`` ——
    JS/WebJS 片段**先于**其它前缀被摘出（AnalyzeRule.kt:544-566），
    所以 ``@js:…##…`` 里 `##` 之前的整段其实是 JS，不是选择器。
    按 `##` 硬切会把这批 JS 误算成「选择器点名了标题」（实测 S 口径 96 → 应为 12）。
    """
    selector_parts: list[str] = []
    tail_parts: list[str] = []
    for field in ("content", "subContent"):
        body = str(content.get(field) or "")
        if not body:
            continue
        # 先按 AnalyzeRule 的真实顺序摘出 JS/WebJS 片段
        js_fragments = [text for text, is_js in split_segments(body) if is_js]
        tail_parts.extend(js_fragments)
        # 再把剩下的非 JS 部分按 ## 切成「选择器 / 尾段」
        for text, is_js in split_segments(body):
            if is_js:
                continue
            head, sep, tail = text.partition("##")
            selector_parts.append(head)
            if sep:
                tail_parts.append(tail)
    return "\n".join(selector_parts), "\n".join(tail_parts)


def _has_title_dedup_in_replace_regex(content: dict[str, Any]) -> bool:
    """该源是否**已经**用 replaceRegex 去掉标题行。

    判据对齐 `AnalyzeRule.makeUpRule`（AnalyzeRule.kt:762-779）的真实语义：
    规则串 `split("##")` 后 **parts[1] 才是匹配段**。生效的「内容替换正则」有两个来源：

    - (a) `ruleContent.replaceRegex` 字段本身；
    - (b) `content` / `subContent` 里内嵌的 `##…` 尾段（AnalyzeRule.kt:335,350-351）。

    **JS 形式的 replaceRegex 必须排除** —— 整串交给 JS 求值，不走 `##` 分段，
    对它切分只会误伤（快照里这类有 54 条 / 归并 55 条）。
    """
    candidates: list[str] = []

    replace_regex = str(content.get("replaceRegex") or "")
    if replace_regex and not (set(classify(replace_regex)) & {KIND_JS, KIND_WEBJS}):
        parts = replace_regex.split("##")
        if len(parts) >= 2:
            candidates.append(parts[1])

    for field in ("content", "subContent"):
        body = str(content.get(field) or "")
        for text, is_js in split_segments(body):
            if is_js:
                # JS 体里的 ## 只是字符串内容，不是分段符。
                continue
            _head, sep, tail = text.partition("##")
            if sep and tail:
                embedded = tail.split("##")[0]
                if embedded:
                    candidates.append(embedded)

    return any(_TITLE_IN_MATCH_PATTERN.search(item) for item in candidates)


def analyze_source(
    raw: dict[str, Any],
    *,
    stale_before_ms: int,
    slow_respond_ms: int,
    duplicate_urls: set[str],
) -> dict[str, Any]:
    """把一条原始书源记录变成一条脱敏的清单记录（含特征旗标与检查结果）。"""
    url = str(raw.get("bookSourceUrl") or "")
    source_type = raw.get("bookSourceType")
    type_name = SOURCE_TYPES.get(source_type, "unknown") if isinstance(source_type, int) else "unknown"

    sections = {section: normalize_rule(raw.get(section)) for section in RULE_SECTIONS}
    content = sections["ruleContent"]

    header_keys = parse_header_keys(raw.get("header"))
    strict_keys, broad_keys = sensitive_key_names(header_keys)

    all_kinds: set[str] = set()
    for fields in sections.values():
        for value in fields.values():
            all_kinds.update(classify(value))
    content_kinds: set[str] = set()
    for value in content.values():
        content_kinds.update(classify(value))
    # 正文规则体（content/subContent）单独判定，口径与 §3.4 的「正文规则使用 JS/WebJS 1010」一致。
    body_kinds: set[str] = set()
    for key in ("content", "subContent"):
        if content.get(key):
            body_kinds.update(classify(content[key]))

    uses_js = KIND_JS in all_kinds
    uses_webjs = KIND_WEBJS in all_kinds or bool(content.get("webJs"))
    content_uses_js = bool(body_kinds & {KIND_JS, KIND_WEBJS})
    content_section_uses_js = bool(content_kinds & {KIND_JS, KIND_WEBJS})

    image_style = str(content.get("imageStyle") or "").upper()

    checks: list[str] = []
    if not url:
        checks.append("url_empty")
    elif not url.lower().startswith(("http://", "https://")):
        checks.append("url_not_http")
    if url and url in duplicate_urls:
        checks.append("url_duplicate")
    if type_name == "unknown":
        checks.append("type_unknown")

    has_content_rule = bool(content.get("content") or content.get("subContent"))
    if not has_content_rule:
        if type_name in {"audio", "image", "file", "video"}:
            checks.append("no_content_rule_download_type")
        else:
            checks.append("no_content_rule")

    has_search = bool(str(raw.get("searchUrl") or "").strip())
    has_explore = bool(str(raw.get("exploreUrl") or "").strip())
    enabled_explore = bool(raw.get("enabledExplore"))
    if not has_search and not has_explore:
        checks.append("no_discovery")
    if has_search and not sections["ruleSearch"].get("bookList", "").strip():
        checks.append("search_book_list_empty")
    # 发现被关掉时，ruleExplore 为空不影响可用性，不计入。
    if has_explore and enabled_explore and not sections["ruleExplore"].get("bookList", "").strip():
        checks.append("explore_book_list_empty")

    prefix_empty = False
    bracket_mismatch = False
    for fields in sections.values():
        for value in fields.values():
            if find_empty_prefix(value):
                prefix_empty = True
            for fragment in detect_js_fragments(value):
                if not check_js_balanced(fragment):
                    bracket_mismatch = True
    if prefix_empty:
        checks.append("rule_prefix_empty")
    if bracket_mismatch:
        checks.append("js_bracket_mismatch")

    replace_regex = content.get("replaceRegex", "")
    # replaceRegex 允许写成 JS（`<js>…</js>` / `@js:`），此时整串交给 JS 求值，
    # 不走 `##` 分段。对这类值做 `##` 切分与正则结构判定只会误伤，必须先排除。
    is_js_replace_regex = bool(replace_regex) and bool(
        set(classify(replace_regex)) & {KIND_JS, KIND_WEBJS}
    )
    if replace_regex:
        parts = replace_regex.split("##")
        if not is_js_replace_regex and len(parts) < 2:
            # 既不是 JS、又没有 ## 段：AnalyzeRule.makeUpRule 只会得到一个非空的
            # rule 选择器，replaceRegex 保持默认空串，替换逻辑整个不生效。
            checks.append("replace_regex_unparsable")
        elif not is_js_replace_regex:
            # parts[1] 即 makeUpRule 里的 replaceRegex（匹配段）。
            # 注意 `##pattern`（两段）是**正常写法**：replacement 取默认空串，
            # 语义是「删除所有命中」，书源里大量用于去广告，不是缺段。
            if _MARKUP_HIT_PATTERN.search(parts[1]):
                checks.append("replace_regex_hits_markup")
            _replace_regex_checks(parts[1], checks)

    if content.get("nextContentUrl") and content.get("nextContentUrl") == content.get("content"):
        checks.append("next_content_url_self_ref")

    # —— 「正文里出现标题」风险（诊断卡 06）———————————————————————————————
    # 三层证据，置信度递减；只有第一层是正向证据。
    title_rule = str(content.get("title") or "").strip()
    has_title_dedup = _has_title_dedup_in_replace_regex(content)
    # 注意：去标题正则与 ruleContent.title **互相独立** —— 快照里绝大多数
    # 作者只用前者（{{chapter.title}} 插值），根本没写 title 字段。
    # 先判去重，再判 title，否则「有去重、无 title」的 160 条会被误记成 no_title_rule。
    if has_title_dedup:
        dedup_evidence = "replace_regex"
    elif title_rule:
        checks.append("title_rule_no_dedup")
        dedup_evidence = "none"
    else:
        dedup_evidence = "no_title_rule"

    selector_part, tail_part = _split_selector_and_tail(content)
    named_in_selector = bool(_TITLE_TOKEN_PATTERN.search(selector_part))
    named_in_js = bool(_TITLE_TOKEN_PATTERN.search(tail_part))
    if named_in_selector:
        checks.append("title_selector_named")
    elif named_in_js:
        # 只在 JS 片段里出现：弱证据，凡是同时命中 title_rule_no_dedup 的就不再重复计。
        if not title_rule:
            checks.append("title_selector_named_js_only")

    if image_style == "TEXT":
        checks.append("image_style_text")
    if strict_keys:
        checks.append("sensitive_header_keys")
    if (
        str(raw.get("loginUrl") or "").strip()
        or str(raw.get("loginUi") or "").strip()
        or str(raw.get("loginCheckJs") or "").strip()
    ):
        checks.append("login_configured")

    last_update = raw.get("lastUpdateTime")
    last_update_ms = last_update if isinstance(last_update, (int, float)) else None
    if last_update_ms is not None and last_update_ms < stale_before_ms:
        checks.append("last_update_stale")

    respond_time = raw.get("respondTime")
    respond_time_ms = respond_time if isinstance(respond_time, (int, float)) else None
    if not respond_time_ms:
        checks.append("respond_time_missing")
    elif respond_time_ms > slow_respond_ms:
        checks.append("respond_time_slow")

    raw_header = raw.get("header")
    return {
        "url_hash": url_hash(url) if url else "0" * 12,
        "type": type_name,
        "enabled": bool(raw.get("enabled")),
        "enabled_explore": enabled_explore,
        "enabled_cookie_jar": bool(raw.get("enabledCookieJar")),
        "enabled_review": bool(raw.get("enabledReview")),
        "fingerprint": structure_fingerprint(sections),
        "checks": sorted(checks),
        # —— 特征旗标：全部是布尔或枚举，不含站点信息 ——————————————————————
        "has_search_url": has_search,
        "has_explore_url": has_explore,
        "has_content_rule": has_content_rule,
        "has_replace_regex": bool(replace_regex),
        "has_js_replace_regex": is_js_replace_regex,
        "has_source_regex": bool(content.get("sourceRegex")),
        "has_next_content_url": bool(content.get("nextContentUrl")),
        "has_webjs": bool(content.get("webJs")),
        "has_image_decode": bool(content.get("imageDecode")),
        "has_pay_action": bool(content.get("payAction")),
        "has_callback_js": bool(content.get("callBackJs")),
        "has_title_rule": bool(content.get("title")),
        # 「正文里出现标题」风险分层（诊断卡 06）。三层证据置信度递减：
        #   T  = ruleContent.title 非空（正向证据：作者明说标题要从正文页取）
        #   N  = 选择器点名了标题节点（启发式，召回低）
        #   RR = 已用 replaceRegex 去标题（作者自己承认过，但已缓解）
        # 静态判不出「某个源是否真的把标题渲染成可见行」，因此这些旗标
        # **不得**单独当作缺陷结论；见 README「判定口径」与诊断卡 06 的误差来源。
        "title_dedup_evidence": dedup_evidence,
        "title_named_in_selector": named_in_selector,
        "title_named_in_js_only": named_in_js and not named_in_selector,
        "title_risk_layer": (
            "T" if title_rule else "N" if named_in_selector else "RR" if has_title_dedup else "none"
        ),
        "image_style": image_style or "none",
        "uses_js": uses_js,
        "uses_webjs": uses_webjs,
        "content_uses_js": content_uses_js,
        "content_section_uses_js": content_section_uses_js,
        "has_login": "login_configured" in checks,
        "has_cookie_jar": bool(raw.get("enabledCookieJar")),
        "has_concurrent_rate": bool(str(raw.get("concurrentRate") or "").strip()),
        "has_book_url_pattern": bool(str(raw.get("bookUrlPattern") or "").strip()),
        "has_variable_comment": bool(str(raw.get("variableComment") or "").strip()),
        "has_sensitive_header_substring": bool(
            raw_header and SENSITIVE_SUBSTRING_PATTERN.search(str(raw_header))
        ),
        "header_key_count": len(header_keys),
        "header_sensitive_keys": strict_keys,
        "header_secret_keys": broad_keys,
        "last_update_ms": last_update_ms,
        "respond_time_ms": respond_time_ms,
        "custom_order": raw.get("customOrder") if isinstance(raw.get("customOrder"), int) else None,
        "weight": raw.get("weight") if isinstance(raw.get("weight"), (int, float)) else None,
        "rule_field_count": sum(len(fields) for fields in sections.values()),
    }


def load_records(payload: Any) -> list[dict[str, Any]]:
    """接受书源数组，或 ``{"bookSources": [...]}`` 形式的对象。"""
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    if isinstance(payload, dict):
        for key in ("bookSources", "data", "sources"):
            value = payload.get(key)
            if isinstance(value, list):
                return [item for item in value if isinstance(item, dict)]
    return []
