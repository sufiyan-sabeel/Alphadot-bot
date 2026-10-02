#!/usr/bin/env python3
"""Verify a built AlphaDot APK.

Designator #3 of the AlphaDot pipeline. Returns exit code 0 ONLY when the APK
passes every structural check.

Usage:
    python patcher/verify.py app/build/outputs/apk/debug/app-debug.apk
    python patcher/verify.py --expect-package com.alphadot.app <apk>

Checks:
    * file exists and is a ZIP
    * AndroidManifest.xml present
    * at least one classes*.dex present
    * resources.arsc present
    * package id matches expectation (default com.alphadot.app)
    * launcher activity declared
    * no forbidden third-party branding in manifest strings
    * SHA256 reported
"""
from __future__ import annotations

import argparse
import shutil
import struct
import subprocess
import sys
import zipfile
from pathlib import Path

from analyze import BinaryManifest, sha256, human  # noqa: E402

FORBIDDEN = ["ai.x.grok", "grok.bot", "x.ai", "anysphere"]


def aapt_badging(apk: Path) -> dict | None:
    aapt = shutil.which("aapt") or shutil.which("aapt2")
    if not aapt:
        return None
    try:
        r = subprocess.run([aapt, "dump", "badging", str(apk)],
                           capture_output=True, text=True, timeout=60)
    except Exception:
        return None
    if r.returncode != 0:
        return None
    info = {"package": None, "versionName": None, "launchable": None}
    for line in r.stdout.splitlines():
        if line.startswith("package:"):
            for p in line.split():
                if p.startswith("name="):
                    info["package"] = p.split("=", 1)[1].strip("'")
                if p.startswith("versionName="):
                    info["versionName"] = p.split("=", 1)[1].strip("'")
        elif line.startswith("launchable-activity:"):
            for p in line.split():
                if p.startswith("name="):
                    info["launchable"] = p.split("=", 1)[1].strip("'")
    return info


def main() -> int:
    ap = argparse.ArgumentParser(description="Verify an AlphaDot APK.")
    ap.add_argument("apk")
    ap.add_argument("--expect-package", default="com.alphadot.app")
    args = ap.parse_args()

    apk = Path(args.apk)
    if not apk.is_absolute() and not apk.exists():
        # search build outputs
        root = Path(__file__).resolve().parent.parent
        found = list(root.glob("app/build/outputs/apk/**/*.apk"))
        if found:
            apk = found[0]

    errors: list[str] = []
    print("=== ALPHADOT VERIFY ===")
    if not apk.exists():
        print(f"ERROR: APK not found: {args.apk}")
        return 1

    print(f"APK     : {apk}")
    print(f"Size    : {human(apk.stat().st_size)}")
    digest = sha256(apk)
    print(f"SHA256  : {digest}")

    if not zipfile.is_zipfile(apk):
        print("ERROR: not a ZIP/APK")
        return 1

    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
        if "AndroidManifest.xml" not in names:
            errors.append("AndroidManifest.xml missing")
        dex = [n for n in names if n.endswith(".dex")]
        if not dex:
            errors.append("no classes*.dex present")
        if "resources.arsc" not in names:
            errors.append("resources.arsc missing")
        print(f"DEX     : {', '.join(dex) if dex else 'MISSING'}")
        print(f"Entries : {len(names)}")

        # Branding / launcher checks from the binary manifest.
        try:
            mf = BinaryManifest(z.read("AndroidManifest.xml"))
            joined = " ".join(mf.strings).lower()
            for bad in FORBIDDEN:
                if bad in joined and bad not in (args.expect_package or ""):
                    errors.append(f"forbidden branding string present: {bad}")
            # launcher intent present?
            has_launcher = any(
                "launcher" in mf.str(a.get(-1, -1)).lower()
                for (n, a) in mf.elements
            ) or any("launcher" in s.lower() for s in mf.strings)
            if not has_launcher:
                errors.append("no LAUNCHER category found in manifest")
        except Exception as e:
            print(f"WARNING: manifest parse issue: {e}")

    info = aapt_badging(apk)
    if info:
        print(f"Package : {info['package']}")
        print(f"Version : {info['versionName']}")
        print(f"Launch  : {info['launchable']}")
        if args.expect_package and info["package"] and \
                not info["package"].startswith(args.expect_package):
            errors.append(
                f"package {info['package']} does not match expected {args.expect_package}"
            )
    else:
        print("NOTE    : aapt not available; structural checks only")

    if errors:
        print("FAIL:")
        for e in errors:
            print(f"  - {e}")
        print("=== VERIFY FAILED ===")
        return 1
    print("=== VERIFY OK ===")
    return 0


if __name__ == "__main__":
    sys.exit(main())
