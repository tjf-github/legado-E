"""P1 真机验收用 Web 端书源调试客户端（PC 侧）。

连接 App 的 WebSocketServer（webPort+1，默认 1123）路径 /bookSourceDebug，
发送合成书源的调试键，把收到的每一帧按到达顺序打上时间戳写入 ws-frames.log。
用于 S4/S7（会话存活窗口里的真实阶段四读数）与 S8（四行摘要是否先于关闭到达）。

用法：python ws_p1_debug.py <key> [timeout_seconds]
"""
import json
import sys
import time
from pathlib import Path

import websocket

HERE = Path(__file__).resolve().parent
LOG = HERE / "ws-frames.log"
URL = "ws://127.0.0.1:1123/bookSourceDebug"
TAG = "http://127.0.0.1:8765/"


def log(line: str) -> None:
    # 每行都带墙钟（本地时间），便于与 App logcat、夹具服务日志按绝对时刻对齐
    stamp = time.strftime("%H:%M:%S")
    with LOG.open("a", encoding="utf-8") as handle:
        handle.write(f"[{stamp}] {line}\n")
    print(f"[{stamp}] {line}", flush=True)


def main() -> int:
    key = sys.argv[1] if len(sys.argv) > 1 else "--http://127.0.0.1:8765/chapter.html"
    timeout = float(sys.argv[2]) if len(sys.argv) > 2 else 90.0
    start = time.time()
    log(f"=== connect {URL} key={key} timeout={timeout}s ===")
    ws = websocket.create_connection(URL, timeout=10)
    log("opened")
    ws.send(json.dumps({"tag": TAG, "key": key}, ensure_ascii=False))
    log("sent " + json.dumps({"tag": TAG, "key": key}, ensure_ascii=False))
    ws.settimeout(2.0)
    frames = 0
    try:
        while time.time() - start < timeout:
            try:
                frame = ws.recv()
            except websocket.WebSocketTimeoutException:
                continue
            except websocket.WebSocketConnectionClosedException:
                log(f"closed by server at t+{time.time() - start:.2f}s")
                break
            frames += 1
            if frame == "":
                # App 端每 30s 发一次 ping；websocket-client 会把控制帧处理掉，
                # 偶尔仍会表面化成一个空负载帧——它不是关闭信号，不能据此退出。
                log(f"t+{time.time() - start:.2f}s frame#{frames}: <empty payload frame, treated as ping>")
                continue
            log(f"t+{time.time() - start:.2f}s frame#{frames}: {frame}")
    finally:
        try:
            ws.close()
        except Exception:
            pass
    log(f"=== done frames={frames} elapsed={time.time() - start:.2f}s ===")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
