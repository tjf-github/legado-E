"""P1 真机验收用合成夹具服务（PC 侧）。

只服务本目录的静态夹具，行为由 mode.txt 控制（每次请求重新读取，可在线切换）：

  normal                      正常返回文件（默认）
  hang                        接受连接后不返回（制造「抓取仍在飞行中」）
  hang:<path>                 仅对路径包含 <path> 的请求不返回
  hang-once:<path>            仅对第一条匹配 <path> 的请求不返回，并立即把 mode.txt 改回 normal
                              （用于让「调试链路的抓取」停在空中，而随后阅读链路的同 URL 抓取正常返回）
  reset[:<path>]              接受连接后立即关闭（链路失败，用于错误分类对照）
  delay:<秒>[:<path>]         先 sleep 再正常返回

每次请求追加一行到 server.log（时间戳、方法、路径、模式、结果），供验收复核。
"""
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MODE_FILE = ROOT / "mode.txt"
LOG_FILE = ROOT / "server.log"
PORT = 8765


def read_mode() -> str:
    try:
        return (MODE_FILE.read_text(encoding="utf-8").strip() or "normal")
    except FileNotFoundError:
        return "normal"


def write_mode(value: str) -> None:
    MODE_FILE.write_text(value, encoding="utf-8")


def parse_mode(mode: str):
    """返回 (kind, arg, path_filter)。"""
    parts = mode.split(":")
    kind = parts[0]
    if kind == "delay":
        delay = float(parts[1]) if len(parts) > 1 else 0.0
        path_filter = parts[2] if len(parts) > 2 else None
        return kind, delay, path_filter
    path_filter = parts[1] if len(parts) > 1 and parts[1] else None
    return kind, None, path_filter


def log(line: str) -> None:
    stamp = time.strftime("%H:%M:%S")
    with LOG_FILE.open("a", encoding="utf-8") as handle:
        handle.write(f"[{stamp}] {line}\n")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "P1Fixture/2.0"

    def do_GET(self):  # noqa: N802
        path = self.path.split("?")[0]
        mode = read_mode()
        kind, arg, path_filter = parse_mode(mode)
        applies = path_filter is None or path_filter in path
        if kind == "delay" and applies:
            log(f"GET {path} mode={mode} sleeping {arg}s")
            time.sleep(arg)
            kind = "normal"
        if kind in ("hang", "hang-once") and applies:
            if kind == "hang-once":
                write_mode("normal")
                log(f"GET {path} mode={mode} consumed -> mode.txt reset to normal, holding-without-response")
            else:
                log(f"GET {path} mode={mode} holding-without-response")
            time.sleep(600)
            return
        if kind == "reset" and applies:
            log(f"GET {path} mode={mode} closing-without-response")
            self.close_connection = True
            try:
                self.connection.shutdown(2)
            except OSError:
                pass
            return
        target = ROOT / path.lstrip("/") if path not in ("", "/") else ROOT / "search.html"
        if not target.is_file():
            log(f"GET {path} 404")
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        body = target.read_bytes()
        ctype = "text/html; charset=utf-8"
        if target.suffix == ".png":
            ctype = "image/png"
        elif target.suffix == ".json":
            ctype = "application/json"
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)
        log(f"GET {path} mode={mode} -> 200 {len(body)}B")

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    log(f"server start port={PORT} pid={os.getpid()}")
    httpd = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    httpd.daemon_threads = True
    httpd.serve_forever()
