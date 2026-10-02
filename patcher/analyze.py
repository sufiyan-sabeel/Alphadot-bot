#!/usr/bin/env python3
"""Analyze an Android APK (or the AlphaDot project) and print a report.

This is designator #1 of the AlphaDot patcher pipeline. It is read-only: it
never modifies the input.

Usage:
    python patcher/analyze.py [path]
    python patcher/analyze.py base.apk
    python patcher/analyze.py            # defaults to base.apk, then project dir

Exit codes:
    0  analysis succeeded
    2  input missing / not analyzable
"""
from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import struct
import subprocess
import sys
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent


def sha256(path: Path, chunk: int = 1 << 20) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for block in iter(lambda: fh.read(chunk), b""):
            h.update(block)
    return h.hexdigest()


def human(n: int) -> str:
    for unit in ("B", "KiB", "MiB", "GiB"):
        if abs(n) < 1024.0:
            return f"{n:.1f} {unit}" if unit != "B" else f"{n} {unit}"
        n /= 1024.0
    return f"{n:.1f} TiB"


# ---------------------------------------------------------------------------
# Binary AndroidManifest fallback parser (no external deps).
# ---------------------------------------------------------------------------
class BinaryManifest:
    """Minimal AXML reader: package name, version and component/permission refs."""

    def __init__(self, data: bytes):
        self.data = data
        self.strings: list[str] = []
        self._parse_string_pool()
        self.elements = self._collect_elements()

    def _parse_string_pool(self):
        d = self.data
        if len(d) < 16:
            return
        root_type, _, _ = struct.unpack_from("<HHI", d, 0)
        if root_type != 0x0003:
            return
        # String pool is the first child chunk.
        off = 8
        try:
            t, hs, _sz = struct.unpack_from("<HHI", d, off)
        except struct.error:
            return
        if t != 0x0001:
            return
        cnt, _sty, flags, _ss, _st = struct.unpack_from("<IIIII", d, off + 8)
        utf8 = bool(flags & 0x100)
        p = off + hs
        out = []
        try:
            for _ in range(cnt):
                if utf8:
                    n = d[p]; p += 1
                    if n & 0x80:
                        n = ((n & 0x7F) << 8) | d[p]; p += 1
                    s = d[p:p + n]; p += n + 1
                    out.append(s.decode("utf-8", "replace"))
                else:
                    n = struct.unpack_from("<H", d, p)[0]; p += 2
                    if n & 0x8000:
                        n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", d, p)[0]; p += 2
                    s = d[p:p + n * 2]; p += n * 2 + 2
                    out.append(s.decode("utf-16le", "replace"))
        except (struct.error, IndexError):
            pass
        self.strings = out

    def _collect_elements(self):
        """Walk START_ELEMENT chunks (0x0102) collecting name/attr string refs."""
        d = self.data
        elems = []
        off = 8
        total = len(d)
        while off + 8 <= total:
            try:
                t, hs, sz = struct.unpack_from("<HHI", d, off)
            except struct.error:
                break
            if sz <= 0:
                break
            if t == 0x0102 and off + hs + 20 <= total:
                # ResXMLTree_node (16B) + attrExt (20B), then attributes
                name_idx, = struct.unpack_from("<i", d, off + hs - 4) if hs >= 20 else (0,)
                # Fallback: name is at node+16 header; safer to read attrExt
                try:
                    _ns, name, attr_start, attr_size, attr_count = struct.unpack_from(
                        "<iiIII", d, off + 16
                    )
                    attrs = {}
                    ap = off + attr_start
                    for _ in range(attr_count):
                        a_ns, a_name, a_raw, _tv_size, a_type, a_data = struct.unpack_from(
                            "<iiiIHI", d, ap
                        ) if False else struct.unpack_from("<iiiIIi", d, ap)
                        attrs.setdefault(a_name, a_data)
                        ap += attr_size
                    elems.append((name, attrs))
                except struct.error:
                    pass
            off += sz
        return elems

    def str(self, idx: int) -> str:
        if -1 <= idx < 0:
            return ""
        if 0 <= idx < len(self.strings):
            return self.strings[idx]
        return ""

    def attrs_of(self, element_name: str) -> list[dict]:
        return [a for (n, a) in self.elements if self.str(n) == element_name]


def parse_with_aapt(apk: Path) -> dict | None:
    aapt = shutil.which("aapt") or shutil.which("aapt2")
    if not aapt:
        return None
    cmd = [aapt, "dump", "badging", str(apk)]
    if "aapt2" in aapt and "aapt" not in aapt:
        cmd = [aapt, "dump", "badging", str(apk)]
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    except Exception:
        return None
    if out.returncode != 0:
        return None
    info = {"package": None, "versionCode": None, "versionName": None,
            "launchable": None, "permissions": [], "minSdk": None, "targetSdk": None}
    for line in out.stdout.splitlines():
        if line.startswith("package:"):
            for part in line.split():
                if part.startswith("name="):
                    info["package"] = part.split("=", 1)[1].strip("'")
                if part.startswith("versionCode="):
                    info["versionCode"] = part.split("=", 1)[1].strip("'")
                if part.startswith("versionName="):
                    info["versionName"] = part.split("=", 1)[1].strip("'")
        elif line.startswith("launchable-activity:"):
            for part in line.split():
                if part.startswith("name="):
                    info["launchable"] = part.split("=", 1)[1].strip("'")
        elif line.startswith("uses-permission:"):
            info["permissions"].append(line.split("name=", 1)[1].strip("'"))
        elif line.startswith("sdkVersion:"):
            info["minSdk"] = line.split(":", 1)[1].strip().strip("'")
        elif line.startswith("targetSdkVersion:"):
            info["targetSdk"] = line.split(":", 1)[1].strip().strip("'")
    return info


def analyze_apk(apk: Path) -> int:
    print("=== ALPHADOT APK ANALYSIS ===")
    print(f"File        : {apk}")
    print(f"Size        : {human(apk.stat().st_size)} ({apk.stat().st_size} bytes)")
    digest = sha256(apk)
    print(f"SHA256      : {digest}")

    if not zipfile.is_zipfile(apk):
        print("ERROR: not a valid ZIP/APK archive")
        return 2
    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
    has_manifest = "AndroidManifest.xml" in names
    dex = sorted(n for n in names if n.endswith(".dex"))
    native = sorted(n for n in names if n.startswith("lib/"))
    print(f"AndroidManifest: {'present' if has_manifest else 'MISSING'}")
    print(f"DEX files      : {', '.join(dex) if dex else 'MISSING'}")
    print(f"Native libs    : {len(native)} file(s)")
    print(f"Entries        : {len(names)}")

    info = parse_with_aapt(apk)
    if info:
        print(f"Package        : {info['package']}")
        print(f"Version        : {info['versionName']} (code {info['versionCode']})")
        print(f"Min/Target SDK : {info['minSdk']} / {info['targetSdk']}")
        print(f"Launcher       : {info['launchable']}")
        print("Permissions:")
        for p in info["permissions"]:
            print(f"  - {p}")
    else:
        with zipfile.ZipFile(apk) as z:
            try:
                mf = BinaryManifest(z.read("AndroidManifest.xml"))
                print(f"Package (fallback): {_first_pkg(mf)}")
                for el in ("activity", "service", "receiver", "uses-permission"):
                    attrs = mf.attrs_of(el)
                    if attrs:
                        print(f"{el}: {len(attrs)}")
            except KeyError:
                print("WARNING: no AndroidManifest.xml to parse")

    if not dex:
        print("WARNING: no classes.dex found. This APK is not directly rebuildable.")
    print("=== END ANALYSIS ===")
    return 0


def _first_pkg(mf: BinaryManifest) -> str:
    # The package string usually contains dots and is not a resource name.
    for s in mf.strings:
        if s.count(".") >= 2 and " " not in s and not s.startswith("android."):
            return s
    return "<unknown>"


def analyze_project(root: Path) -> int:
    print("=== ALPHADOT PROJECT ANALYSIS ===")
    print(f"Root        : {root}")
    for f in ("settings.gradle.kts", "build.gradle.kts", "gradlew",
              "app/build.gradle.kts", "app/src/main/AndroidManifest.xml"):
        print(f"  {'OK ' if (root / f).exists() else 'MISS'} {f}")
    print("=== END ANALYSIS ===")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="Analyze an APK or the AlphaDot project.")
    ap.add_argument("path", nargs="?", default="base.apk")
    args = ap.parse_args()
    p = Path(args.path)
    if not p.is_absolute():
        # Try cwd, then repo/patcher, then repo root
        for cand in (Path.cwd() / p, REPO_ROOT / p, REPO_ROOT / "patcher" / p):
            if cand.exists():
                p = cand
                break
    if p.is_file():
        return analyze_apk(p)
    if p.is_dir():
        return analyze_project(p)
    print(f"ERROR: input not found: {args.path}")
    print("Provide an APK path, or run from the repository root.")
    return 2


if __name__ == "__main__":
    sys.exit(main())
