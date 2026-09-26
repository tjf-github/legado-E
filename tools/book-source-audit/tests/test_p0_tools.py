from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


TOOL_DIR = Path(__file__).resolve().parents[1]
REPO_ROOT = TOOL_DIR.parents[1]
sys.path.insert(0, str(TOOL_DIR))

from checks import analyze_source, parse_header_keys, sensitive_key_names  # noqa: E402
from report import assert_sanitized, to_json  # noqa: E402
from rule_syntax import classify, split_segments  # noqa: E402
from verify_p0 import verify  # noqa: E402


class RuleSyntaxTest(unittest.TestCase):
    def test_mode_escalation_matches_analyze_rule(self) -> None:
        self.assertEqual(("get", "regex"), classify("@get:{chapter}"))
        self.assertEqual(("jsonpath",), classify("$.data.content"))
        self.assertEqual(("allinone", "css"), classify("@@div.content"))

    def test_split_order_is_frozen_to_current_app_behavior(self) -> None:
        # AnalyzeRule.splitSourceRule currently scans JS first, then WebJS. This is
        # intentionally counter-intuitive; the audit tool must describe the App,
        # not silently normalize it into a different parser.
        rule = ".content<js>return result</js>@webjs:window.done()"
        self.assertEqual(
            [(".content", False), ("return result", True), ("window.done()", True)],
            split_segments(rule),
        )


class AuditContractTest(unittest.TestCase):
    def test_sensitive_header_check_uses_key_names_not_values(self) -> None:
        keys = parse_header_keys({"Cookie": "secret", "X-Label": "Authorization"})
        strict, broad = sensitive_key_names(keys)
        self.assertEqual(["Cookie"], strict)
        self.assertEqual([], broad)

    def test_summary_sanitizer_rejects_identity_fields(self) -> None:
        with self.assertRaisesRegex(ValueError, "身份字段"):
            assert_sanitized({"nested": {"bookSourceUrl": "https://example.invalid"}})

    def test_analyzed_record_is_deterministic_and_has_no_identity(self) -> None:
        source = {
            "bookSourceUrl": "https://novel.test",
            "bookSourceType": 0,
            "enabled": True,
            "ruleContent": {"content": "#content@html"},
        }
        first = analyze_source(source, stale_before_ms=0, slow_respond_ms=15000, duplicate_urls=set())
        second = analyze_source(source, stale_before_ms=0, slow_respond_ms=15000, duplicate_urls=set())
        self.assertEqual(to_json(first), to_json(second))
        self.assertNotIn("bookSourceUrl", first)


class BaselineVerifierTest(unittest.TestCase):
    def test_repository_baseline_passes(self) -> None:
        baseline = REPO_ROOT / "app/src/test/resources/bookSourceBaseline"
        self.assertEqual([], verify(baseline))

    def test_rejects_real_domain_and_unknown_invariant(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            baseline = Path(temp)
            (baseline / "INVARIANTS.md").write_text(
                "| id | 正例 | 反例 | 状态 |\n| --- | --- | --- | --- |\n"
                "| `INV-OK-1` | x | y | z |\n",
                encoding="utf-8",
            )
            case_dir = baseline / "case-01-demo"
            case_dir.mkdir()
            (case_dir / "case.json").write_text(
                json.dumps(
                    {
                        "id": "case-01-demo",
                        "kind": "html",
                        "synthetic": True,
                        "target_stage": "P3",
                        "purpose": "demo",
                        "invariants": ["INV-MISSING-1"],
                    }
                ),
                encoding="utf-8",
            )
            (case_dir / "input.html").write_text(
                '<img src="https://real.example.org/a.png">', encoding="utf-8"
            )
            errors = verify(baseline)
            self.assertTrue(any("未定义不变量" in error for error in errors))
            self.assertTrue(any("非保留域名" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
