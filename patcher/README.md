# AlphaDot Patcher

A small, dependency-light Python pipeline for safely preparing the authorized
AlphaDot Android project and verifying the resulting APK.

> **Safety first.** AlphaDot is an independent, open-source chat client. These
> scripts never bypass authentication, billing, licensing or server-side
> authorization, and never extract or expose credentials. See
> [Safety policy](#safety-policy).

## Pipeline

```
patcher/
├── analyze.py   # read-only: hash + metadata + permissions + components
├── patch.py     # assess input; prepare AlphaDot-owned build source
├── verify.py    # fail-closed structural + branding verification
└── README.md
```

Typical use:

```bash
# 1. Analyze the authorized source APK (read-only).
python patcher/analyze.py base.apk

# 2. Assess whether it can be safely rebranded; prepare the build source.
python patcher/patch.py base.apk

# 3. After the Gradle build, verify the produced APK.
python patcher/verify.py app/build/outputs/apk/debug/app-debug.apk
```

`analyze.py` prints size, SHA256, package name, version, SDK levels,
launcher activity and permissions. `patch.py` writes a machine-readable
`patrick/patch-report.json` and refuses to perform a destructive binary
rewrite when the input is not a self-contained APK. `verify.py` exits `0`
only when every structural and branding check passes.

## Why rebranding the provided `base.apk` directly is not performed

The provided `base.apk` is a **React Native / Expo** application. Its
JavaScript is shipped as a compiled/encrypted bundle and its code is split
across `classes*.dex` plus native `lib/` libraries. A "rebrand in place"
would require:

* decrypting / decompiling proprietary third-party bundles,
* merging split APKs,
* replacing native libraries and signatures.

None of that is safe, appropriate, or reliable. Instead, this repository
contains a **clean-room AlphaDot application** (see the repository root
Gradle project) whose branding, UI, permissions and provider interface are
fully AlphaDot-owned. The patcher validates the source APK, records its
provenance (SHA256) and directs the build at the clean-room source.

## Safety policy

`patch.py` refuses (exit code `3` with `--strict`) when it detects:

* missing `classes*.dex` or `resources.arsc`,
* opaque / encrypted JavaScript bundles,
* split-APK configuration sets,
* any other condition that would require defeating protection.

It also scans the repository for accidental secrets and warns if any are
found. It never prints, stores or transmits credential material.

## Requirements

* Python 3.10+
* Optional: `aapt`/`aapt2` for richer metadata (the scripts include a
  dependency-free binary-manifest fallback parser).
