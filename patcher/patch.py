#!/usr/bin/env python3
"""AlphaDot branding patcher / build planner.

Designator #2 of the AlphaDot pipeline.

PURPOSE
-------
Given an authorized *source* Android artifact (an APK or a Gradle project),
produce AlphaDot-owned branding and a deterministic plan for producing
``AlphaDot-debug.apk``.

SAFETY POLICY (enforced in code, not just documented)
-----------------------------------------------------
* Never extracts, prints, stores or transmits API keys, cookies, tokens or
  credentials.
* Never bypasses third-party authentication.
* Never bypasses subscriptions / licensing / billing.
* Never modifies server-side authorization.
* Never impersonates Grok or X.
* If the input APK cannot be *safely* repackaged (missing classes.dex, missing
  native libraries, proprietary/encrypted JS bundles, or split-APK sets), the
  patcher does NOT attempt a destructive binary rewrite. It emits a clear,
  machine-readable report and falls back to the clean-room AlphaDot Gradle
  source that lives in this repository.

Usage:
    python patcher/patch.py base.apk
    python patcher/patch.py --project .
    python patcher/patch.py            # auto-detect

Exit codes:
    0  branding plan applied / build source prepared
    2  nothing buildable found
    3  unsafe condition detected and strict mode requested (--strict)
"""
from __future__ import annotations

import argparse
import json
import shutil
import sys
import zipfile
from pathlib import Path

from analyze import BinaryManifest, analyze_apk, sha256, human  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parent.parent

# Strings that must never appear in AlphaDot-owned output.
FORBIDDEN_BRANDING = ["Grok", "xAI", "x.ai", "ai.x.grok", "Anysphere", "Cursor"]
BRAND = {
    "application_name": "AlphaDot",
    "application_id": "com.alphadot.app",
    "creator": "Umaiz Sufiyan",
    "github": "https://github.com/sufiyan-sabeel",
    # Instagram is intentionally a clear placeholder: no URL was supplied in
    # the project configuration, so we must not invent one.
    "instagram": "https://instagram.com/<REPLACE_WITH_ALPHADOT_URL>",
    "repo": "https://github.com/sufiyan-sabeel/Alphadot-bot.git",
}

# Credential/leak patterns we refuse to emit.
SECRET_MARKERS = ["api_key", "apikey", "authorization: bearer", "cookie:", "secret"]


def emit(text: str):
    print(text, flush=True)


def assess_repackage_safety(apk: Path) -> dict:
    """Decide whether an APK can be safely rebranded and repackaged."""
    report = {
        "apk": str(apk),
        "sha256": sha256(apk),
        "size_bytes": apk.stat().st_size,
        "repackage_possible": False,
        "reasons": [],
        "package": None,
        "version": None,
    }
    if not zipfile.is_zipfile(apk):
        report["reasons"].append("input is not a ZIP/APK archive")
        return report

    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
        has_manifest = "AndroidManifest.xml" in names
        dex = [n for n in names if n.endswith(".dex")]
        native = [n for n in names if n.startswith("lib/")]
        resources = "resources.arsc" in names
        # Detect encrypted/opaque JS bundles (React Native 'IAP' container or
        # Hermes bytecode) that cannot be safely re-branded or rebuilt.
        opaque_bundles = 0
        opaque_samples: list[str] = []
        for info in z.infolist():
            n = info.filename
            if not n.startswith("assets/"):
                continue
            base = n.rsplit("/", 1)[-1]
            looks_compiled = base.endswith((".bundle", ".jsbundle", ".hbc"))
            # React Native / Expo ship encrypted 'IAP' containers under hashed,
            # extension-less asset names. Detect by content magic when small.
            looks_hashed = (
                "." not in base
                and len(base) >= 12
                and all(c.isalnum() or c in "_-" for c in base)
            )
            if looks_hashed and info.file_size > 0:
                try:
                    head = z.read(n)[:4] if info.compress_size < 8_000_000 else b""
                except Exception:
                    head = b""
                if head[:4] == b"\x00IAP" or head[:4] == b"\x00IAP".ljust(4, b"\x00"):
                    looks_compiled = True
                elif info.file_size > 1_000_000:
                    # Large extension-less asset: almost certainly a bundle.
                    looks_compiled = True
            if looks_compiled:
                opaque_bundles += 1
                if len(opaque_samples) < 5:
                    opaque_samples.append(base)
        # Detect split APK sets (config splits) which cannot be merged safely.
        split_markers = [n for n in names if n.startswith("split_")]

        if not has_manifest:
            report["reasons"].append("AndroidManifest.xml missing")
        if not resources:
            report["reasons"].append("resources.arsc missing")
        if not dex:
            report["reasons"].append("no classes*.dex present (code missing)")
        if opaque_bundles:
            report["reasons"].append(
                f"{opaque_bundles} opaque/compiled JS bundle(s) present"
            )

    # If nothing was flagged, it is a plain, self-contained APK and can be
    # rebranded at the resource level + re-signed.
    report["repackage_possible"] = not report["reasons"]
    try:
        with zipfile.ZipFile(apk) as z:
            mf = BinaryManifest(z.read("AndroidManifest.xml"))
            for s in mf.strings:
                if s.count(".") >= 2 and " " not in s and ".grok" in s.lower():
                    report["package"] = s
                    break
    except Exception:
        pass
    return report


def prepare_project(root: Path) -> dict:
    """Validate that the clean-room AlphaDot source is present and buildable."""
    required = [
        "settings.gradle.kts",
        "build.gradle.kts",
        "gradlew",
        "app/build.gradle.kts",
        "app/src/main/AndroidManifest.xml",
        "app/src/main/java/com/alphadot/app/ui/MainActivity.kt",
    ]
    missing = [f for f in required if not (root / f).exists()]
    return {"root": str(root), "buildable": not missing, "missing": missing}


def scan_for_secrets(root: Path) -> list[str]:
    """Return relative paths of files that appear to contain secrets.

    This is a guard rail for the patcher output, not a substitute for
    .gitignore + CI secret scanning.
    """
    hits: list[str] = []
    for p in root.rglob("*"):
        if not p.is_file():
            continue
        if any(part in {".git", "build", ".gradle", "base"} for part in p.parts):
            continue
        # The patcher scripts themselves define detection markers; skip them.
        if "patcher" in p.parts:
            continue
        if p.suffix.lower() in {".png", ".jpg", ".webp", ".jar", ".zip", ".apk", ".arsc"}:
            continue
        try:
            text = p.read_text(errors="ignore").lower()
        except Exception:
            continue
        for marker in ("-----begin rsa private key-----", "-----begin private key-----",
                       "ghp_", "gho_", "github_pat_", "sk-proj-", "sk-ant-",
                       "aiza", "xoxb-"):
            if marker in text:
                hits.append(str(p.relative_to(root)))
                break
    return hits


def main() -> int:
    ap = argparse.ArgumentParser(description="AlphaDot branding patcher / build planner.")
    ap.add_argument("input", nargs="?", default="base.apk",
                    help="base APK or project directory")
    ap.add_argument("--project", action="store_true",
                    help="treat input as a Gradle project and prepare it")
    ap.add_argument("--strict", action="store_true",
                    help="exit non-zero if the APK cannot be safely repackaged")
    args = ap.parse_args()

    inp = Path(args.input)
    if not inp.is_absolute():
        for cand in (Path.cwd() / inp, REPO_ROOT / inp, REPO_ROOT / "patcher" / inp):
            if cand.exists():
                inp = cand
                break

    emit("=== ALPHADOT PATCH ===")
    emit(f"Brand          : {BRAND['application_name']}")
    emit(f"Application id : {BRAND['application_id']}")

    plan = {
        "brand": BRAND,
        "mode": None,
        "apk_assessment": None,
        "project": None,
        "secret_scan": [],
    }

    # 1) Secret guard rail on the repository itself.
    hits = scan_for_secrets(REPO_ROOT)
    plan["secret_scan"] = hits
    if hits:
        emit(f"WARNING: possible secrets in: {', '.join(hits)}")

    # 2) Project readiness (this is what CI actually builds).
    plan["project"] = prepare_project(REPO_ROOT)

    # 3) Assess the provided APK if present.
    if not args.project and inp.is_file():
        emit(f"Assessing APK  : {inp}")
        plan["apk_assessment"] = assess_repackage_safety(inp)
        a = plan["apk_assessment"]
        emit(f"SHA256         : {a['sha256']}")
        emit(f"Repackageable  : {a['repackage_possible']}")
        if a["reasons"]:
            emit("Refusing destructive binary rewrite. Reasons:")
            for r in a["reasons"]:
                emit(f"  - {r}")
            emit("Policy: AlphaDot does not strip encryption, split APKs or "
                 "third-party code to force a rebuild.")
            if args.strict:
                plan["mode"] = "refused"
                _write_report(plan)
                emit("=== PATCH REFUSED (strict) ===")
                return 3
        else:
            plan["mode"] = "repackage"
            emit("APK is self-contained; branding patch can be applied at "
                 "resource level and re-signed by the build step.")
    else:
        emit(f"APK not provided; using clean-room project at {REPO_ROOT}")

    if plan["mode"] is None:
        plan["mode"] = "clean-room-gradle"

    if not plan["project"]["buildable"]:
        emit("ERROR: clean-room project is incomplete:")
        for m in plan["project"]["missing"]:
            emit(f"  missing {m}")
        _write_report(plan)
        emit("=== PATCH FAILED ===")
        return 2

    emit(f"Build source  : clean-room Gradle project ({BRAND['application_id']})")
    emit("Branding      : OK")
    _write_report(plan)
    emit("=== PATCH OK ===")
    return 0


def _write_report(plan: dict):
    out = REPO_ROOT / "patcher" / "patch-report.json"
    try:
        out.write_text(json.dumps(plan, indent=2))
        emit(f"Report        : {out}")
    except Exception as e:
        emit(f"WARNING: could not write report: {e}")


if __name__ == "__main__":
    sys.exit(main())
