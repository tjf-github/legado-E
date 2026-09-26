"""S9 正文一致性核对：从三份 uiautomator dump 里取 content_text_view 的 content-desc 逐字比较。"""
import re
import pathlib

EV = pathlib.Path(r"D:\vsproject\legado-E\.agent-protocol\tasks\book-source-p1-device\33-device-evidence")
PATTERN = re.compile(
    r'resource-id="io\.legado\.app\.tjf\.debug:id/content_text_view"[^>]*content-desc="([^"]*)"'
)
NAMES = [
    "33-reading-chapter-one-diag-off",
    "40-S4-reading-chapter-one",
    "50-S6-reading-after-session-closed",
]


def content_desc(name: str):
    xml = (EV / f"{name}.xml").read_text(encoding="utf-8", errors="replace")
    match = PATTERN.search(xml)
    return match.group(1) if match else None


values = {name: content_desc(name) for name in NAMES}
for name, value in values.items():
    print(f"{name}: {value!r}")
base = values[NAMES[0]]
print("parity(all three equal):", all(v == base for v in values.values()))
