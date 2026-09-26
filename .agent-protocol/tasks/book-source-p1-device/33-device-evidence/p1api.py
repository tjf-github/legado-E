"""P1 真机验收：通过 App 自带 Web API 读取/改写合成书源（PC 侧，经 adb forward tcp:1122）。

  python p1api.py get                 # GET /getBookSource 打印完整 JSON
  python p1api.py set-key <key>       # 只改 ruleSearch.checkKeyWord 后 POST /saveBookSource
  python p1api.py sources             # GET /getBookSources
"""
import json
import sys
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:1122"
SOURCE_URL = "http://127.0.0.1:8765/"


def get(path: str):
    with urllib.request.urlopen(BASE + path, timeout=10) as resp:
        return json.loads(resp.read().decode("utf-8"))


def post(path: str, payload: str):
    req = urllib.request.Request(BASE + path, data=payload.encode("utf-8"),
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main() -> int:
    cmd = sys.argv[1] if len(sys.argv) > 1 else "get"
    if cmd == "get":
        data = get("/getBookSource?url=" + urllib.parse.quote(SOURCE_URL, safe=""))
        print(json.dumps(data, ensure_ascii=False, indent=1))
    elif cmd == "sources":
        data = get("/getBookSources")
        print(json.dumps(data, ensure_ascii=False)[:2000])
    elif cmd == "set-key":
        key = sys.argv[2]
        data = get("/getBookSource?url=" + urllib.parse.quote(SOURCE_URL, safe=""))
        source = data["data"]
        source.setdefault("ruleSearch", {})["checkKeyWord"] = key
        result = post("/saveBookSource", json.dumps(source, ensure_ascii=False))
        print("save result:", json.dumps(result, ensure_ascii=False))
        back = get("/getBookSource?url=" + urllib.parse.quote(SOURCE_URL, safe=""))
        print("checkKeyWord now:", back["data"]["ruleSearch"]["checkKeyWord"])
    else:
        print(__doc__)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
