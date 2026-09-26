"""P1 真机验收设备操作助手（PC 侧）。只做 adb 只读查询与 UI 交互，证据落 33-device-evidence/。

用法：
  python p1dev.py devices
  python p1dev.py dump <name>            # uiautomator dump -> 证据目录/<name>.xml，并打印可交互节点
  python p1dev.py find <name> <keyword>  # 在最新 dump 里找含关键字的节点（打印 text/resource-id/bounds）
  python p1dev.py tap-text <text>        # 按 text 或 content-desc 精确匹配并点击中心
  python p1dev.py tap-id <resid-suffix>  # 按 resource-id 后缀匹配并点击中心
  python p1dev.py tap <x> <y>
  python p1dev.py shot <name>            # screencap -> 证据目录/<name>.png
  python p1dev.py start <component> [extra ...]
  python p1dev.py key <code>
  python p1dev.py shell <cmd...>
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

SERIAL = "10CE5P1M1Z001P9"
EV = Path(__file__).resolve().parents[3] / ".agent-protocol" / "tasks" / "book-source-p1-device" / "33-device-evidence"


def adb(*args: str, binary: bool = False):
    result = subprocess.run(["adb", "-s", SERIAL, *args], capture_output=True)
    if binary:
        return result.stdout
    return result.stdout.decode("utf-8", "replace")


def dump_tree(name: str):
    adb("shell", "uiautomator", "dump", f"/sdcard/p1-{name}.xml")
    adb("pull", f"/sdcard/p1-{name}.xml", str(EV / f"{name}.xml"))
    text = (EV / f"{name}.xml").read_text(encoding="utf-8", errors="replace")
    return ET.fromstring(text)


def bounds_of(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        return None
    x1, y1, x2, y2 = (int(v) for v in m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def describe(node):
    return (f"text={node.get('text')!r} id={node.get('resource-id')} "
            f"desc={node.get('content-desc')!r} clickable={node.get('clickable')} bounds={node.get('bounds')}")


def main() -> int:
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return 1
    cmd = args[0]
    if cmd == "devices":
        print(adb("devices", "-l"))
    elif cmd == "dump":
        root = dump_tree(args[1])
        for node in root.iter("node"):
            if node.get("text") or node.get("content-desc") or node.get("clickable") == "true":
                print(describe(node))
    elif cmd == "state":
        root = dump_tree("state-probe")
        ids = {n.get("resource-id", "") for n in root.iter("node")}
        desc = " ".join((n.get("content-desc") or "") for n in root.iter("node"))
        where = "unknown"
        if any(i.endswith("tv_chapter_item") for i in ids):
            where = "drawer"
        elif any(i.endswith("ll_catalog") for i in ids):
            where = "menu"
        elif any(i.endswith("content_text_view") for i in ids):
            where = "content"
        chapter = re.search(r"(Chapter \w+)", desc)
        print(f"state={where} chapter={chapter.group(1) if chapter else None}")
    elif cmd == "find":
        root = dump_tree(args[1])
        needle = args[2]
        for node in root.iter("node"):
            hay = (node.get("text") or "") + "|" + (node.get("content-desc") or "") + "|" + (node.get("resource-id") or "")
            if needle in hay:
                print(describe(node))
    elif cmd == "tap-text":
        target = args[1]
        root = dump_tree("tap-probe")
        for node in root.iter("node"):
            if node.get("text") == target or node.get("content-desc") == target:
                point = bounds_of(node)
                print(f"tap {target!r} at {point} ({describe(node)})")
                adb("shell", "input", "tap", str(point[0]), str(point[1]))
                return 0
        print(f"NOT FOUND text={target!r}")
        return 2
    elif cmd == "tap-id":
        suffix = args[1]
        root = dump_tree("tap-probe")
        for node in root.iter("node"):
            if (node.get("resource-id") or "").endswith(suffix):
                point = bounds_of(node)
                print(f"tap id~{suffix!r} at {point} ({describe(node)})")
                adb("shell", "input", "tap", str(point[0]), str(point[1]))
                return 0
        print(f"NOT FOUND id~{suffix!r}")
        return 2
    elif cmd == "tap":
        adb("shell", "input", "tap", args[1], args[2])
    elif cmd == "shot":
        data = adb("exec-out", "screencap", "-p", binary=True)
        (EV / f"{args[1]}.png").write_bytes(data)
        print(f"saved {EV / (args[1] + '.png')} {len(data)}B")
    elif cmd == "start":
        adb("shell", "am", "start", "-n", args[1], *args[2:])
    elif cmd == "key":
        adb("shell", "input", "keyevent", args[1])
    elif cmd == "shell":
        print(adb("shell", *args[1:]))
    else:
        print(__doc__)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
