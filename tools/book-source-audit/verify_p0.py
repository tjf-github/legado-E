#!/usr/bin/env python3
"""校验 P0 冻结样本的结构、引用和脱敏边界。"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


CASE_DIR_PATTERN = re.compile(r"case-\d{2}-[a-z0-9-]+$")
INVARIANT_ROW_PATTERN = re.compile(r"^\|\s*`(INV-[A-Z0-9-]+)`\s*\|", re.MULTILINE)
URL_PATTERN = re.compile(r"https?://[^\s<>\"']+", re.IGNORECASE)
RESERVED_HOST_SUFFIXES = (".invalid", ".test", ".example")


def _repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def _load_json(path: Path, errors: list[str]) -> dict[str, Any] | None:
    try:
        payload = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        errors.append(f"{path}: JSON 无法读取：{exc}")
        return None
    if not isinstance(payload, dict):
        errors.append(f"{path}: 顶层必须是 JSON 对象")
        return None
    return payload


def _check_urls(path: Path, text: str, errors: list[str]) -> None:
    for raw_url in URL_PATTERN.findall(text):
        host = (urlparse(raw_url.rstrip(".,;:)]}")).hostname or "").lower()
        if host and not host.endswith(RESERVED_HOST_SUFFIXES):
            errors.append(f"{path}: 出现非保留域名 URL：{host}")


def verify(baseline_dir: Path) -> list[str]:
    errors: list[str] = []
    invariants_path = baseline_dir / "INVARIANTS.md"
    if not invariants_path.is_file():
        return [f"缺少不变量目录：{invariants_path}"]

    invariants_text = invariants_path.read_text(encoding="utf-8-sig")
    defined = set(INVARIANT_ROW_PATTERN.findall(invariants_text))
    if not defined:
        errors.append(f"{invariants_path}: 没有解析到任何 INV-* 表格行")

    case_dirs = sorted(path for path in baseline_dir.iterdir() if path.is_dir())
    if not case_dirs:
        errors.append(f"{baseline_dir}: 没有 case-* 样本目录")

    seen_ids: set[str] = set()
    referenced: set[str] = set()
    for case_dir in case_dirs:
        if not CASE_DIR_PATTERN.fullmatch(case_dir.name):
            errors.append(f"{case_dir}: 目录名不符合 case-NN-slug")
            continue

        case_path = case_dir / "case.json"
        payload = _load_json(case_path, errors)
        if payload is None:
            continue

        case_id = payload.get("id")
        if case_id != case_dir.name:
            errors.append(f"{case_path}: id 必须等于目录名 {case_dir.name}")
        if case_id in seen_ids:
            errors.append(f"{case_path}: id 重复：{case_id}")
        if isinstance(case_id, str):
            seen_ids.add(case_id)

        if payload.get("synthetic") is not True:
            errors.append(f"{case_path}: synthetic 必须显式为 true")
        for field in ("kind", "purpose", "target_stage"):
            if not isinstance(payload.get(field), str) or not payload[field].strip():
                errors.append(f"{case_path}: {field} 必须是非空字符串")

        case_invariants = payload.get("invariants")
        if not isinstance(case_invariants, list) or not case_invariants:
            errors.append(f"{case_path}: invariants 必须是非空数组")
        else:
            for invariant in case_invariants:
                if not isinstance(invariant, str):
                    errors.append(f"{case_path}: invariants 只能包含字符串")
                    continue
                referenced.add(invariant)
                if invariant not in defined:
                    errors.append(f"{case_path}: 引用了未定义不变量 {invariant}")

        for scenario in payload.get("scenarios", []):
            if isinstance(scenario, dict) and isinstance(scenario.get("file"), str):
                referenced_file = case_dir / scenario["file"]
                if not referenced_file.is_file():
                    errors.append(f"{case_path}: 场景文件不存在：{scenario['file']}")

        fixture_files = [path for path in case_dir.iterdir() if path.is_file() and path.name != "case.json"]
        if not fixture_files:
            errors.append(f"{case_dir}: 至少需要一个 case.json 之外的输入样本")

        for path in [case_path, *fixture_files]:
            try:
                _check_urls(path, path.read_text(encoding="utf-8-sig"), errors)
            except (OSError, UnicodeError) as exc:
                errors.append(f"{path}: 文本无法读取：{exc}")

    unreferenced = sorted(defined - referenced)
    if unreferenced:
        errors.append("INVARIANTS.md 中没有被任何 case 引用：" + ", ".join(unreferenced))
    return errors


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="校验书源优化 P0 冻结样本。")
    parser.add_argument(
        "--baseline",
        type=Path,
        default=_repo_root() / "app/src/test/resources/bookSourceBaseline",
        help="冻结样本目录",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    errors = verify(args.baseline.resolve())
    if errors:
        print(f"P0 基线校验失败：{len(errors)} 项", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    case_count = sum(1 for path in args.baseline.iterdir() if path.is_dir())
    print(f"P0 基线校验通过：{case_count} 个合成样本，结构/引用/脱敏边界均有效")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
