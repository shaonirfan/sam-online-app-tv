#!/usr/bin/env python3
"""Tiny mock of the OpenSubtitles REST API (+ a Range-capable video file) for JVM tests.

Run:  python3 tools/mock_opensubtitles.py 8766
Then: MOCK_OS=http://127.0.0.1:8766 ./gradlew :app:testDebugUnitTest

It enforces what the real API expects: Api-Key + "AppName vX" User-Agent headers,
lowercase alphabetically sorted query parameters, a JWT for downloads, and a 406 when
the daily download quota is used up (file_id 999).
"""
import json
import os
import struct
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qsl, urlsplit

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8766
KEY = "testkey123"
TOKEN = "jwt-abc"
VIDEO = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".mock_video.bin")

if not os.path.exists(VIDEO) or os.path.getsize(VIDEO) != 300_000:
    with open(VIDEO, "wb") as f:
        f.write(bytes((i * 7 + 3) % 256 for i in range(300_000)))


def moviehash(path):
    size = os.path.getsize(path)
    h = size
    with open(path, "rb") as f:
        for off in (0, size - 65536):
            f.seek(off)
            buf = f.read(65536)
            for (v,) in struct.iter_unpack("<Q", buf):
                h = (h + v) & 0xFFFFFFFFFFFFFFFF
    return "%016x" % h


HASH = moviehash(VIDEO)
SRT = "1\r\n00:00:01,000 --> 00:00:03,500\r\nCaf\xe9 is open\r\n\r\n2\r\n00:01:02,250 --> 00:01:04,000\r\nSecond line\r\n".encode("cp1252")


def sub(fid, release, hash_match, downloads, hi=False, lang="en"):
    return {"id": str(fid), "attributes": {"language": lang, "download_count": downloads, "hearing_impaired": hi,
            "release": release, "moviehash_match": hash_match, "files": [{"file_id": fid, "file_name": release + ".srt"}]}}


class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _json(self, code, obj):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _api_ok(self):
        if self.headers.get("Api-Key") != KEY:
            self._json(403, {"message": "bad api key"}); return False
        ua = self.headers.get("User-Agent", "")
        if not ua.startswith("SAMOnlineTV v"):
            self._json(403, {"message": "bad user agent " + ua}); return False
        return True

    def do_HEAD(self):
        if self.path == "/video.mkv":
            self.send_response(200)
            self.send_header("Content-Length", str(os.path.getsize(VIDEO)))
            self.send_header("Accept-Ranges", "bytes")
            self.end_headers()
        else:
            self.send_response(404); self.end_headers()

    def do_GET(self):
        u = urlsplit(self.path)
        if u.path == "/video.mkv":
            data = open(VIDEO, "rb").read()
            rng = self.headers.get("Range")
            if rng:
                a, b = rng.replace("bytes=", "").split("-")
                a, b = int(a), int(b)
                chunk = data[a:b + 1]
                self.send_response(206)
                self.send_header("Content-Range", f"bytes {a}-{b}/{len(data)}")
            else:
                chunk = data
                self.send_response(200)
            self.send_header("Content-Length", str(len(chunk)))
            self.end_headers()
            self.wfile.write(chunk)
            return
        if u.path == "/files/sub.srt":
            self.send_response(200)
            self.send_header("Content-Length", str(len(SRT)))
            self.end_headers()
            self.wfile.write(SRT)
            return
        if u.path == "/api/v1/subtitles":
            if not self._api_ok():
                return
            pairs = parse_qsl(u.query)
            keys = [k for k, _ in pairs]
            if keys != sorted(keys) or any(v != v.lower() for _, v in pairs):
                self._json(400, {"message": "params must be lowercase and sorted: " + u.query}); return
            q = dict(pairs)
            data = []
            if q.get("moviehash") == HASH:
                data.append(sub(1, "Movie.2023.1080p.WEB-DL.EXACT", True, 1200))
            if q.get("query"):
                data.append(sub(2, "Movie.2023.720p.BluRay", False, 50000))
                data.append(sub(3, "Movie.2023.SDH", False, 90000, hi=True))
                data.append(sub(4, "Movie.2023.French", False, 99999, lang="fr"))
            self._json(200, {"total_count": len(data), "data": data, "echo": q})
            return
        self._json(404, {"message": "not found"})

    def do_POST(self):
        u = urlsplit(self.path)
        n = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(n) or b"{}")
        if not self._api_ok():
            return
        if u.path == "/api/v1/login":
            if body.get("username") == "user" and body.get("password") == "pass":
                self._json(200, {"token": TOKEN, "base_url": "api.opensubtitles.com", "user": {"allowed_downloads": 20}})
            else:
                self._json(401, {"message": "invalid credentials"})
            return
        if u.path == "/api/v1/download":
            if self.headers.get("Authorization") != "Bearer " + TOKEN:
                self._json(401, {"message": "token required"}); return
            if body.get("file_id") == 999:
                self._json(406, {"message": "You have downloaded your allowed 20 subtitles for 24h"}); return
            self._json(200, {"link": f"http://127.0.0.1:{PORT}/files/sub.srt", "remaining": 19})
            return
        self._json(404, {"message": "not found"})


ThreadingHTTPServer(("127.0.0.1", PORT), H).serve_forever()
