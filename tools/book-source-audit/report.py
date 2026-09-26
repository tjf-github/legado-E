"""聚合统计与报告输出（Markdown / JSON / CSV）。

所有输出都是**聚合口径**：不含书源名称、URL、选择器原文、请求头取值或正文。
逐源清单由 audit.py 单独写入本机目录，不属于本模块。
"""

from __future__ import annotations

import csv
import io
import json
from typing import Any, Iterable

from checks import CHECK_CATALOG, LOW_CONFIDENCE_CHECKS

#: 报告中逐源清单不会出现的字段——用于自检，防止将来误把身份字段带进聚合输出。
IDENTITY_FIELDS = ("bookSourceUrl", "bookSourceName", "bookSourceComment", "header", "url")

#: 结构指纹榜里展示的集群数量上限。
TOP_CLUSTERS = 20


def _counts(records: Iterable[dict[str, Any]], key) -> dict[str, dict[str, int]]:
    """按 ``key`` 分桶，每桶同时给出 total 与 enabled 两个数。"""
    buckets: dict[str, dict[str, int]] = {}
    for record in records:
        bucket = str(key(record))
        entry = buckets.setdefault(bucket, {"total": 0, "enabled": 0})
        entry["total"] += 1
        if record.get("enabled"):
            entry["enabled"] += 1
    return buckets


def summarize(
    records: list[dict[str, Any]],
    *,
    run: dict[str, Any],
    reference_readings: dict[str, Any],
) -> dict[str, Any]:
    """产出机器可读的聚合读数（summary.json 的内容）。"""
    total = len(records)
    enabled = sum(1 for record in records if record.get("enabled"))

    checks_total: dict[str, int] = {name: 0 for name in CHECK_CATALOG}
    checks_enabled: dict[str, int] = {name: 0 for name in CHECK_CATALOG}
    for record in records:
        for name in record["checks"]:
            checks_total[name] = checks_total.get(name, 0) + 1
            if record.get("enabled"):
                checks_enabled[name] = checks_enabled.get(name, 0) + 1

    flag_names = [
        "has_search_url", "has_explore_url", "has_content_rule", "has_replace_regex",
        "has_js_replace_regex", "has_source_regex", "has_next_content_url", "has_webjs",
        "has_image_decode",
        "has_pay_action", "has_callback_js", "has_title_rule", "uses_js", "uses_webjs",
        "content_uses_js", "content_section_uses_js", "has_login", "has_cookie_jar",
        "has_concurrent_rate", "has_book_url_pattern", "has_variable_comment",
        "has_sensitive_header_substring",
    ]
    empty = {"total": 0, "enabled": 0}
    flags = {
        name: _counts(records, lambda r, n=name: "true" if r.get(n) else "false").get("true", empty)
        for name in flag_names
    }

    clusters = _counts(records, lambda r: r["fingerprint"])
    cluster_rows = sorted(
        (
            {
                "fingerprint": fingerprint,
                "total": entry["total"],
                "enabled": entry["enabled"],
            }
            for fingerprint, entry in clusters.items()
        ),
        key=lambda row: (-row["total"], row["fingerprint"]),
    )

    image_styles = _counts(records, lambda r: r["image_style"])

    return {
        "run": run,
        "totals": {
            "sources": total,
            "enabled": enabled,
            "disabled": total - enabled,
            "distinct_fingerprints": len(clusters),
        },
        "by_type": _counts(records, lambda r: r["type"]),
        "by_image_style": image_styles,
        "by_enabled_explore": _counts(records, lambda r: "true" if r["enabled_explore"] else "false"),
        "by_cookie_jar": _counts(records, lambda r: "true" if r["enabled_cookie_jar"] else "false"),
        "features": flags,
        "checks_total": checks_total,
        "checks_enabled": checks_enabled,
        "top_fingerprints": cluster_rows[:TOP_CLUSTERS],
        "reference_readings": reference_readings,
    }


def cluster_csv(records: list[dict[str, Any]]) -> str:
    """逐指纹的集群表（聚合，可入库）。"""
    clusters = _counts(records, lambda r: r["fingerprint"])
    cluster_flags = (
        "uses_js", "uses_webjs", "content_uses_js",
        "has_next_content_url", "has_replace_regex",
    )
    per_cluster_flags: dict[str, dict[str, int]] = {}
    for record in records:
        bucket = per_cluster_flags.setdefault(record["fingerprint"], {})
        for name in cluster_flags:
            if record.get(name):
                bucket[name] = bucket.get(name, 0) + 1
    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow(["fingerprint", "total", "enabled", *cluster_flags])
    for fingerprint in sorted(clusters, key=lambda f: (-clusters[f]["total"], f)):
        entry = clusters[fingerprint]
        flags = per_cluster_flags.get(fingerprint, {})
        writer.writerow(
            [fingerprint, entry["total"], entry["enabled"]]
            + [flags.get(name, 0) for name in cluster_flags]
        )
    return buffer.getvalue()


def _cell(value: Any) -> str:
    """Markdown 表格单元格：转义竖线与换行，避免口径说明里的 `||` 之类的字符撑破表格。"""
    return str(value).replace("|", "\\|").replace("\n", " ")


def _table(headers: list[str], rows: list[list[Any]]) -> str:
    lines = ["| " + " | ".join(headers) + " |", "| " + " | ".join("---" for _ in headers) + " |"]
    for row in rows:
        lines.append("| " + " | ".join(_cell(cell) for cell in row) + " |")
    return "\n".join(lines)


def _pair_table(title: str, buckets: dict[str, dict[str, int]] | dict[str, int]) -> str:
    """``{桶: {total, enabled}}`` 或 ``{桶: int}`` → Markdown 表。"""
    rows: list[list[Any]] = []
    for key in sorted(buckets):
        value = buckets[key]
        if isinstance(value, dict):
            rows.append([key, value["total"], value["enabled"]])
        else:
            rows.append([key, value])
    if isinstance(next(iter(buckets.values()), 0), dict):
        return f"#### {title}\n\n" + _table(["分桶", "全部", "启用"], rows)
    return f"#### {title}\n\n" + _table(["分桶", "计数"], rows)


def render_markdown(summary: dict[str, Any]) -> str:
    """把聚合读数渲染成可直接阅读的 Markdown 报告。"""
    run = summary["run"]
    totals = summary["totals"]
    out: list[str] = []
    add = out.append

    add("# 书源静态体检与分群报告")
    add("")
    add("> 本报告由 `tools/book-source-audit/` 生成，**只读、离线、不执行任何书源规则**。")
    add("> 全部数字都是**聚合读数**，不含书源名称、URL、选择器、请求头取值或正文。")
    add("> 对照《书源与解析优化计划书》§7 P0：本报告对应「建立可重复运行的本地只读清单工具」与「全量离线静态检查并产出分群报告」。")
    add("")
    add("## 1. 运行信息")
    add("")
    add(_table(["项", "值"], [
        ["生成时间", run.get("generated_at", "")],
        ["输入", run.get("source", "")],
        ["读取的合集数（贡献了书源的）", run.get("collections_read", 0)],
        ["去重前 bookSourceUrl 引用条数", run.get("raw_entries", 0)],
        ["唯一 bookSourceUrl", run.get("unique_urls", 0)],
        ["重复 URL 条目", run.get("duplicate_urls", 0)],
        ["归并模式", "按 bookSourceUrl 归并" if run.get("merge_collections") else "单份清单，未归并"],
        ["陈旧阈值 / 偏慢阈值", f"{run.get('stale_days', '')} 天 / {run.get('slow_respond_ms', '')} ms"],
        ["工具版本", run.get("tool_version", "")],
    ]))
    add("")
    add("## 2. 总量")
    add("")
    add(_table(["项", "计数"], [
        ["书源总数", totals["sources"]],
        ["启用", totals["enabled"]],
        ["未启用", totals["disabled"]],
        ["不同规则结构指纹", totals["distinct_fingerprints"]],
    ]))
    add("")
    add("## 3. 分群")
    add("")
    add("### 3.1 书源类型")
    add("")
    add(_table(["类型", "全部", "启用"], [
        [name, bucket["total"], bucket["enabled"]]
        for name, bucket in sorted(summary["by_type"].items())
    ]))
    add("")
    add("### 3.2 启用状态与图片样式")
    add("")
    add(_pair_table("imageStyle（大写归一）", summary["by_image_style"]))
    add("")
    add(_pair_table("enabledExplore", summary["by_enabled_explore"]))
    add("")
    add(_pair_table("enabledCookieJar", summary["by_cookie_jar"]))
    add("")
    add("### 3.3 能力特征")
    add("")
    add(_table(["特征", "全部", "启用"], [
        [name, bucket["total"], bucket["enabled"]]
        for name, bucket in sorted(summary["features"].items())
    ]))
    add("")
    add("### 3.4 规则结构指纹 Top %d" % TOP_CLUSTERS)
    add("")
    add("指纹 = `{段.字段: 语法种类}` 排序后的 SHA-256 前 12 位，只描述解析策略的形状，不含选择器原文。")
    add("同指纹 = 同一套解析策略，可用于识别批量生成的同模板源。")
    add("")
    add(_table(["指纹", "全部", "启用"], [
        [row["fingerprint"], row["total"], row["enabled"]]
        for row in summary["top_fingerprints"]
    ]))
    add("")
    add("## 4. 离线静态检查")
    add("")
    add("每条检查的判定口径见 `tools/book-source-audit/README.md`。**零命中的检查同样列出**，便于确认口径生效。")
    add("「低置信度」= 纯结构启发式的线索，供 P1 挑候选，**不构成书源缺陷结论**（§4.2）。")
    add("")
    add(_table(["检查", "全部", "启用", "置信度", "口径"], [
        [name, summary["checks_total"].get(name, 0), summary["checks_enabled"].get(name, 0),
         "低置信度" if name in LOW_CONFIDENCE_CHECKS else "确定性",
         CHECK_CATALOG.get(name, "")]
        for name in sorted(CHECK_CATALOG)
    ]))
    add("")
    add("## 5. 与计划书 §3.4 快照读数对照")
    add("")
    add("§3.4 的读数是 2026-09-21 的一次性人工盘点，**不是验收基数**；数字变化不等于回归。")
    add("下表的「本次读数」由本工具按自己的口径计算，口径不同的项已在备注里写明。")
    add("")
    rows = []
    for item in summary["reference_readings"].get("items", []):
        actual = _resolve_actual(summary, item.get("actual_key"))
        rows.append([item["label"], item["expected"], actual, item.get("note", "")])
    add(_table(["项", "§3.4 读数", "本次读数", "备注"], rows))
    add("")
    add("## 6. 下一步")
    add("")
    add("本报告只做诊断。按 §7，联网测试只从本报告的分群中选代表样本，")
    add("不对全部书源发起请求；P2—P5 不在本批范围内。")
    add("")
    return "\n".join(out) + "\n"


def _resolve_actual(summary: dict[str, Any], key: str | None) -> Any:
    """把 §3.4 对照表的 ``actual_key`` 解析成本次运行的实际读数，渲染成可读的一格。"""
    if not key:
        return ""
    node: Any = summary
    for part in key.split("."):
        if not isinstance(node, dict) or part not in node:
            return "（本次运行未产出该读数）"
        node = node[part]
    if isinstance(node, dict):
        if "total" in node and "enabled" in node:
            return f"{node['total']}（启用 {node['enabled']}）"
        # 分桶表（如 by_type / by_image_style）：渲染成 k=v 列表
        buckets = []
        for name in sorted(node):
            value = node[name]
            buckets.append(f"{name}={value['total']}" if isinstance(value, dict) else f"{name}={value}")
        return "、".join(buckets) if buckets else "（无分桶）"
    return node


def assert_sanitized(summary: dict[str, Any]) -> None:
    """自检：聚合读数里不得出现任何身份字段键名。

    这是防呆，不是形式检查——将来若有人往聚合里塞进书源名称或 URL，这里会直接失败。
    """
    def walk(node: Any, path: str) -> None:
        if isinstance(node, dict):
            for key, value in node.items():
                if key in IDENTITY_FIELDS:
                    raise ValueError(f"聚合读数出现身份字段：{path}.{key}")
                walk(value, f"{path}.{key}")
        elif isinstance(node, list):
            for index, value in enumerate(node):
                walk(value, f"{path}[{index}]")

    walk(summary, "summary")


def to_json(payload: Any) -> str:
    """确定性 JSON：键排序、固定缩进、结尾换行。"""
    return json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
