#!/usr/bin/env python3
"""书源静态体检与分群工具（本地只读清单工具）。

对应《书源与解析优化计划书》§7 P0 的第 1、2 项交付物：

- 对 ``bookSource.json`` 当前快照按启用状态、书源类型、规则结构指纹、
  JS / 分页 / 替换 / 图片特征分群；
- 对全部书源做离线静态检查；
- **可重复运行**：不写死任何条数，书源新增后重跑即自动纳入（§2.7）。

约束：
- **不联网**：本工具不 import 任何网络模块，也不发起任何请求；
- **只读**：只以只读方式打开输入，绝不写回源文件；
- **脱敏**：聚合报告不含书源名称、URL、选择器原文、请求头取值或正文。

用法见同目录 README.md。
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import sys
import zipfile
from pathlib import Path
from typing import Any, NoReturn

sys.path.insert(0, str(Path(__file__).resolve().parent))

from checks import analyze_source, load_records  # noqa: E402
from report import assert_sanitized, cluster_csv, render_markdown, summarize, to_json  # noqa: E402

TOOL_VERSION = "1.0.0"

#: 本机默认输出目录（已加入 .gitignore）。
DEFAULT_OUT = ".book-source-audit"

#: 聚合报告默认发布目录（可入库）。
DEFAULT_PUBLISH = "docs/book-source-audit"

#: 仓库根目录下自动识别的输入，按顺序取第一个存在的。
AUTO_SOURCE_CANDIDATES = ("阅读书源.zip", "bookSource.json")

#: 未提供 --source 时向上查找仓库根的层数上限。
_MAX_ASCENT = 6

#: 《书源与解析优化计划书》§3.4 的快照读数，**仅用于报告末尾的对照展示**。
#: 这些数字不是本工具的计算依据，也不参与任何判定——§3.4 已明确「数字变化不等于回归」。
REFERENCE_READINGS: dict[str, Any] = {
    "source": "《书源与解析优化计划书》§3.4（2026-09-21 静态读数）",
    "items": [
        {"label": "bookSource.json 书源数", "expected": 6139, "actual_key": "totals.sources",
         "note": "按唯一 bookSourceUrl 计"},
        {"label": "唯一 bookSourceUrl", "expected": 6139, "actual_key": "run.unique_urls", "note": ""},
        {"label": "启用书源", "expected": 4000, "actual_key": "totals.enabled", "note": ""},
        {"label": "文本 / 音频 / 图片 / 文件", "expected": "5805 / 109 / 188 / 37",
         "actual_key": "by_type", "note": "本列展示的是类型分桶，需逐项核对"},
        {"label": "有正文规则", "expected": 6004, "actual_key": "features.has_content_rule.total",
         "note": "口径为 content 或 subContent 非空"},
        {"label": "有书源内 replaceRegex", "expected": 1635,
         "actual_key": "features.has_replace_regex.total", "note": ""},
        {"label": "有正文下一页规则", "expected": 2030,
         "actual_key": "features.has_next_content_url.total", "note": ""},
        {"label": "正文规则使用 JS/WebJS", "expected": 1010,
         "actual_key": "features.content_uses_js.total",
         "note": "口径为 ruleContent 的 content/subContent 体含 JS/WebJS；"
                 "features.content_section_uses_js 是全 ruleContent 段的更宽口径，"
                 "features.uses_js 是任意规则段的更宽口径"},
        {"label": "imageStyle=TEXT（启用）", "expected": "45（启用 21）",
         "actual_key": "by_image_style.TEXT",
         "note": "须大写归一后比较；大小写敏感的朴素计数会少算 4 条（§3.4 为 45，其中启用 21）"},
        {"label": "请求头含敏感键名", "expected": "≥239",
         "actual_key": "features.has_sensitive_header_substring.total",
         "note": "§3.4 口径为「原始请求头字符串出现敏感词」；checks_total.sensitive_header_keys 是更严的键名口径，两者都列出"},
        {"label": "配置登录入口或登录检测", "expected": 790, "actual_key": "checks_total.login_configured",
         "note": "口径为 loginUrl / loginUi / loginCheckJs 任一非空；"
                 "本次读数比 §3.4 多 3 条，按 §3.4「数字变化不等于回归」如实列出，不调整阈值凑数"},
    ],
}


def _fail(message: str) -> NoReturn:
    print(f"错误：{message}", file=sys.stderr)
    raise SystemExit(2)


def _repo_root(start: Path) -> Path | None:
    """向上查找含 .git 的目录，用于解析默认输入路径。"""
    current = start
    for _ in range(_MAX_ASCENT):
        if (current / ".git").exists():
            return current
        if current.parent == current:
            return None
        current = current.parent
    return None


def _resolve_source(explicit: str | None) -> Path:
    if explicit:
        path = Path(explicit).expanduser()
        if not path.exists():
            _fail(f"输入不存在：{path}")
        return path.resolve()

    for base in (Path.cwd(), _repo_root(Path(__file__).resolve().parent)):
        if base is None:
            continue
        for name in AUTO_SOURCE_CANDIDATES:
            candidate = base / name
            if candidate.exists():
                return candidate.resolve()
    _fail(
        "未找到默认输入。请用 --source 指定 bookSource.json 或其所在的压缩包；"
        f"默认会在当前目录与仓库根查找：{'、'.join(AUTO_SOURCE_CANDIDATES)}"
    )


def _decode(raw: bytes) -> str:
    return raw.decode("utf-8-sig", errors="replace")


def _read_book_source_json_from_zip(archive: zipfile.ZipFile) -> tuple[str, list[dict[str, Any]]]:
    matches = [name for name in archive.namelist() if name.endswith("bookSource.json")]
    if not matches:
        _fail("压缩包内没有找到 bookSource.json")
    # 同名多份时取路径最短的一份，结果稳定可复现。
    matches.sort(key=lambda name: (len(name), name))
    payload = json.loads(_decode(archive.read(matches[0])))
    return matches[0], load_records(payload)


def _read_all_collections_from_zip(
    archive: zipfile.ZipFile,
) -> tuple[list[str], list[dict[str, Any]], int, int]:
    """读取压缩包内**所有**书源 JSON 合集并按 ``bookSourceUrl`` 归并去重。

    对应 §3.4：压缩包内多份合集共 16155 条含 ``bookSourceUrl`` 的引用，去重后是同一批
    6139 个唯一 URL。归并时不把重复合集当新书源，避免后续联网抽样重复请求同一站点。

    返回 ``(贡献了书源的合集名, 去重后记录, 扫描过的合集数, 去重前引用条数)``。
    """
    contributed: list[str] = []
    merged: dict[str, dict[str, Any]] = {}
    ordered_urls: list[str] = []
    scanned = 0
    references = 0
    for name in sorted(archive.namelist()):
        if not name.lower().endswith(".json") or name.endswith("/"):
            continue
        try:
            payload = json.loads(_decode(archive.read(name)))
        except (ValueError, KeyError):
            continue
        records = load_records(payload)
        if not records:
            continue
        scanned += 1
        hit = 0
        for record in records:
            url = str(record.get("bookSourceUrl") or "")
            if not url:
                continue
            hit += 1
            if url not in merged:
                ordered_urls.append(url)
                merged[url] = record
            else:
                # 同一 URL 在多份合集里出现：保留 lastUpdateTime 更新的一份，
                # 更新时间相同则保留先出现的，保证结果与读取顺序无关。
                existing = merged[url].get("lastUpdateTime") or 0
                incoming = record.get("lastUpdateTime") or 0
                if isinstance(incoming, (int, float)) and isinstance(existing, (int, float)):
                    if incoming > existing:
                        merged[url] = record
        if hit:
            # 只有真正贡献了书源的合集才计入 collections_read；bookmark/readConfig
            # 一类同样是 JSON 数组，但整份都不含 bookSourceUrl，不算「读了书源合集」。
            contributed.append(name)
            references += hit
    if not merged:
        _fail("压缩包内没有解析出任何书源 JSON 合集")
    return contributed, [merged[url] for url in ordered_urls], scanned, references


def _read_json_file(path: Path) -> list[dict[str, Any]]:
    payload = json.loads(_decode(path.read_bytes()))
    records = load_records(payload)
    if not records:
        _fail(f"未能从 {path.name} 解析出书源数组")
    return records


def _read_directory(path: Path) -> tuple[list[str], list[dict[str, Any]], int]:
    files = sorted(item for item in path.iterdir() if item.suffix.lower() == ".json")
    if not files:
        _fail(f"目录 {path} 下没有 .json 文件")
    merged: dict[str, dict[str, Any]] = {}
    ordered: list[str] = []
    contributed: list[str] = []
    references = 0
    for item in files:
        try:
            records = _read_json_file(item)
        except SystemExit:
            continue
        hit = 0
        for record in records:
            url = str(record.get("bookSourceUrl") or "")
            if url:
                hit += 1
                if url not in merged:
                    ordered.append(url)
                    merged[url] = record
        if hit:
            contributed.append(item.name)
            references += hit
    return contributed, [merged[url] for url in ordered], references


def load_input(source: Path, merge_collections: bool) -> tuple[list[str], list[dict[str, Any]], int]:
    """返回 ``(读取的合集名, 书源记录, 去重前引用条数)``。"""
    if source.is_dir():
        return _read_directory(source)
    if zipfile.is_zipfile(source):
        with zipfile.ZipFile(source) as archive:
            if merge_collections:
                names, records, _scanned, references = _read_all_collections_from_zip(archive)
                return names, records, references
            name, records = _read_book_source_json_from_zip(archive)
            return [name], records, len(records)
    records = _read_json_file(source)
    return [source.name], records, len(records)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="book-source-audit",
        description="书源静态体检与分群工具（离线、只读、脱敏）。",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--source", help="bookSource.json / 压缩包 / 目录；缺省时自动查找仓库根")
    parser.add_argument(
        "--merge-collections",
        action="store_true",
        help="读取压缩包内所有书源 JSON 合集并按 bookSourceUrl 归并去重",
    )
    parser.add_argument("--out", default=DEFAULT_OUT, help=f"本机输出目录（默认 {DEFAULT_OUT}）")
    parser.add_argument(
        "--publish",
        help=f"把聚合报告写到该目录（可入库，默认不写；建议 {DEFAULT_PUBLISH}）",
    )
    parser.add_argument(
        "--label",
        help=(
            "发布文件名的口径标签，写在日期与报告名之间："
            "<date>-<label>-静态分群报告.md。缺省按口径自动取"
            "「单份清单」或「归并合集」——两种口径的读数差别很大"
            "（has_title_rule 0 vs 36、启用 4000 vs 4417），"
            "不区分就无法并存。"
        ),
    )
    parser.add_argument(
        "--full",
        action="store_true",
        help="同时写出逐源清单 inventory.jsonl（只写本机，不要入库）",
    )
    parser.add_argument(
        "--include-identity",
        action="store_true",
        help="逐源清单附带书源名称与 URL（仅本机；需与 --full 同用）",
    )
    parser.add_argument(
        "--generated-at",
        help="覆盖报告里的生成时间（ISO 8601），用于验证重复运行结果一致",
    )
    parser.add_argument("--stale-days", type=int, default=730, help="lastUpdateTime 早于多少天算陈旧")
    parser.add_argument("--slow-respond-ms", type=int, default=15000, help="respondTime 超过多少毫秒算偏慢")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    source = _resolve_source(args.source)
    names, records, raw_entries = load_input(source, args.merge_collections)

    if not records:
        _fail("没有解析出任何书源记录")

    if args.include_identity and not args.full:
        _fail("--include-identity 会写出书源名称与 URL，必须与 --full 同用")

    if args.generated_at:
        try:
            base_dt = dt.datetime.fromisoformat(args.generated_at)
        except ValueError:
            _fail(f"--generated-at 不是合法 ISO 8601 时间：{args.generated_at}")
        generated_at = args.generated_at
        generated_date = base_dt.date().isoformat()
    else:
        now = dt.datetime.now().astimezone()
        generated_at = now.isoformat(timespec="seconds")
        base_dt = now.replace(tzinfo=None)
        generated_date = now.date().isoformat()

    stale_before_ms = int(base_dt.timestamp() * 1000) - args.stale_days * 86400 * 1000

    seen: dict[str, int] = {}
    for record in records:
        url = str(record.get("bookSourceUrl") or "")
        seen[url] = seen.get(url, 0) + 1
    duplicate_urls = {url for url, count in seen.items() if count > 1 and url}
    unique_urls = len([url for url in seen if url])

    # 分析与原始记录成对保存，避免后面按 url_hash 重排时错位。
    pairs = [
        (
            analyze_source(
                record,
                stale_before_ms=stale_before_ms,
                slow_respond_ms=args.slow_respond_ms,
                duplicate_urls=duplicate_urls,
            ),
            record,
        )
        for record in records
    ]
    pairs.sort(key=lambda pair: pair[0]["url_hash"])
    analyzed = [pair[0] for pair in pairs]

    run_info = {
        "tool_version": TOOL_VERSION,
        "generated_at": generated_at,
        "source": source.name,
        "collections_read": len(names),
        "collection_names": sorted(names),
        "raw_entries": raw_entries,
        "unique_urls": unique_urls,
        "duplicate_urls": len(duplicate_urls),
        "merge_collections": bool(args.merge_collections),
        "stale_days": args.stale_days,
        "slow_respond_ms": args.slow_respond_ms,
    }

    summary = summarize(analyzed, run=run_info, reference_readings=REFERENCE_READINGS)
    assert_sanitized(summary)

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "summary.json").write_text(to_json(summary), encoding="utf-8")
    (out_dir / "clusters.csv").write_text(cluster_csv(analyzed), encoding="utf-8")
    (out_dir / "report.md").write_text(render_markdown(summary), encoding="utf-8")

    if args.full:
        lines: list[str] = []
        for item, record in pairs:
            row = dict(item)
            if args.include_identity:
                row["bookSourceName"] = record.get("bookSourceName")
                row["bookSourceUrl"] = record.get("bookSourceUrl")
            lines.append(json.dumps(row, ensure_ascii=False, sort_keys=True))
        (out_dir / "inventory.jsonl").write_text("\n".join(lines) + "\n", encoding="utf-8")

    if args.publish:
        publish_dir = Path(args.publish)
        publish_dir.mkdir(parents=True, exist_ok=True)
        label = args.label or ("归并合集" if args.merge_collections else "单份清单")
        report_name = f"{generated_date}-{label}-静态分群报告.md"
        (publish_dir / report_name).write_text(render_markdown(summary), encoding="utf-8")
        # summary.json 同名会被后一次运行覆盖，所以按口径各存一份。
        (publish_dir / f"{generated_date}-{label}-summary.json").write_text(
            to_json(summary), encoding="utf-8"
        )
        # 不带标签的 summary.json **只在单份清单口径下写**：它是 §3.4 基线读数的对照物，
        # 让归并口径去覆盖它，会让按「全库」说话的文档引用到一个自己标注「未归并」的数字
        # （这件事已经发生过一次，见诊断卡 06 第 3 节）。
        if not args.merge_collections:
            (publish_dir / "summary.json").write_text(to_json(summary), encoding="utf-8")

    totals = summary["totals"]
    print(
        f"书源 {totals['sources']}（启用 {totals['enabled']}），"
        f"唯一 URL {unique_urls}，结构指纹 {totals['distinct_fingerprints']} 种"
    )
    print(f"聚合报告：{out_dir / 'report.md'}")
    if args.full:
        print(f"逐源清单（本机，勿入库）：{out_dir / 'inventory.jsonl'}")
    if args.publish:
        print(f"已发布聚合报告到：{args.publish}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
