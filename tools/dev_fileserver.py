#!/usr/bin/env python3
"""Serve files from sample_files and print test URLs for each file."""

from __future__ import annotations

import argparse
import functools
import os
import socket
import sys
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Iterable
from urllib.parse import quote, unquote, urlsplit


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SAMPLE_DIR = REPO_ROOT / "sample_files"
TEST_VIDEO_EXTENSIONS = {".mp4", ".mkv"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Serve sample_files for local device testing")
    parser.add_argument("--host", default="0.0.0.0", help="Bind host (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=8788, help="Bind port (default: 8788)")
    parser.add_argument(
        "--directory",
        default=str(DEFAULT_SAMPLE_DIR),
        help=f"Directory to serve (default: {DEFAULT_SAMPLE_DIR})",
    )
    parser.add_argument(
        "--list-only",
        action="store_true",
        help="Print URLs and exit without starting the server",
    )
    return parser.parse_args()


def guess_lan_ip() -> str | None:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.connect(("8.8.8.8", 80))
            ip = sock.getsockname()[0]
            if ip and not ip.startswith("127."):
                return ip
    except OSError:
        pass

    try:
        ip = socket.gethostbyname(socket.gethostname())
        if ip and not ip.startswith("127."):
            return ip
    except OSError:
        pass

    return None


def iter_files(base_dir: Path) -> Iterable[Path]:
    for path in sorted(base_dir.rglob("*")):
        if not path.is_file():
            continue
        if any(part.startswith(".") for part in path.relative_to(base_dir).parts):
            continue
        if path.suffix.lower() not in TEST_VIDEO_EXTENSIONS:
            continue
        yield path


def build_alias_map(base_dir: Path, files: list[Path]) -> dict[str, str]:
    alias_map: dict[str, str] = {}
    for idx, file_path in enumerate(files, start=1):
        rel = file_path.relative_to(base_dir).as_posix()
        ext = file_path.suffix.lstrip(".").lower() or "bin"
        alias_map[f"/{idx}.{ext}"] = rel
    return alias_map


def build_base_urls(bind_host: str, port: int) -> list[str]:
    if bind_host == "0.0.0.0":
        urls = [f"http://127.0.0.1:{port}"]
        lan_ip = guess_lan_ip()
        if lan_ip:
            urls.append(f"http://{lan_ip}:{port}")
        return urls

    return [f"http://{bind_host}:{port}"]


def print_urls(sample_dir: Path, base_urls: list[str], alias_map: dict[str, str]) -> None:
    print(f"Serving directory: {sample_dir}")
    print("Base URLs:")
    for base in base_urls:
        print(f"- {base}/")

    if not alias_map:
        print("\nNo files found to serve.")
        return

    print("\nSimple test URLs (use these in app):")
    for alias, rel in alias_map.items():
        encoded_alias = quote(alias.lstrip("/"))
        for base in base_urls:
            print(f"- {base}/{encoded_alias} -> {rel}")

    print("\nDirect file URLs:")
    for rel in alias_map.values():
        encoded_rel = quote(rel)
        for base in base_urls:
            print(f"- {base}/{encoded_rel}")


class AliasFileHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, alias_map: dict[str, str] | None = None, **kwargs):
        self.alias_map = alias_map or {}
        super().__init__(*args, **kwargs)

    def do_GET(self) -> None:
        self._rewrite_alias_path()
        super().do_GET()

    def do_HEAD(self) -> None:
        self._rewrite_alias_path()
        super().do_HEAD()

    def _rewrite_alias_path(self) -> None:
        parsed = urlsplit(self.path)
        raw_path = unquote(parsed.path)
        rel = self.alias_map.get(raw_path)
        if not rel:
            return
        mapped = "/" + quote(rel)
        if parsed.query:
            mapped += f"?{parsed.query}"
        if parsed.fragment:
            mapped += f"#{parsed.fragment}"
        self.path = mapped


def main() -> int:
    args = parse_args()
    sample_dir = Path(args.directory).expanduser().resolve()

    if not sample_dir.exists() or not sample_dir.is_dir():
        print(f"Directory does not exist or is not a directory: {sample_dir}", file=sys.stderr)
        return 1

    files = list(iter_files(sample_dir))
    alias_map = build_alias_map(sample_dir, files)
    base_urls = build_base_urls(args.host, args.port)
    print_urls(sample_dir, base_urls, alias_map)

    if args.list_only:
        return 0

    print("\nServer running. Press Ctrl+C to stop.")

    handler = functools.partial(
        AliasFileHandler,
        directory=os.fspath(sample_dir),
        alias_map=alias_map,
    )
    server = ThreadingHTTPServer((args.host, args.port), handler)

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down.")
    finally:
        server.server_close()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
