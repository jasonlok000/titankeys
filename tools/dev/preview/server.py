#!/usr/bin/env python3
"""Serves the quick preview pages to the phone, through adb reverse, and keeps every value a page saves.

Usage: python3 tools/dev/preview/server.py [port]   (default 8765)
Pages are in pages/. The Inter fonts are served from the app's own copies (android/src/main/res/font, OFL:
licences/Inter-OFL.txt), so a page loads them with url(inter_bold.otf). A POST to /save writes the values to
$TK_OUT/preview/values.json and adds them to values.log beside it."""
import http.server
import json
import os
import pathlib
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
PAGES = HERE / "pages"
FONTS = HERE.parents[2] / "android" / "src" / "main" / "res" / "font"
OUT = pathlib.Path(os.environ.get("TK_OUT") or pathlib.Path.home() / "tk-out") / "preview"
OUT.mkdir(parents=True, exist_ok=True)


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(PAGES), **kwargs)

    def translate_path(self, path):
        name = path.split("?", 1)[0].lstrip("/")
        if name.startswith("inter_") and name.endswith(".otf") and "/" not in name:
            return str(FONTS / name)
        return super().translate_path(path)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def do_POST(self):
        if self.path != "/save":
            self.send_error(404)
            return
        length = min(int(self.headers.get("Content-Length", 0)), 4096)
        try:
            values = json.loads(self.rfile.read(length))
        except ValueError:
            self.send_error(400)
            return
        values["saved"] = time.strftime("%H:%M:%S")
        (OUT / "values.json").write_text(json.dumps(values))
        with open(OUT / "values.log", "a") as log:
            log.write(json.dumps(values) + "\n")
        self.send_response(204)
        self.end_headers()

    def log_message(self, *args):
        pass


port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
print(f"serving {PAGES} on 127.0.0.1:{port}, values in {OUT}")
# Bound to this machine only. The phone reaches it through adb reverse, never over the network.
http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
