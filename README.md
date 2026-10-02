# AlphaDot

A free, open-source Android AI chatbot.

AlphaDot is an independent, self-hosted chat client. It ships with **no API
keys**, requires **no account**, and starts straight into a local/offline demo
mode. If you want to talk to a real model, you plug in your own provider
credentials in Settings — they never leave your device except to call the
provider *you* chose.

## Features

- 💬 Clean chat interface with on-device conversation history
- 🆕 New conversation + history picker
- ⚙️ Settings screen to configure the AI provider and model
- 🌙 Dark, professional UI
- 🔌 Replaceable provider architecture:
  - `LocalProvider` — fully offline demo (default, no account)
  - `GeminiProvider` — Google Gemini
  - `OpenAICompatibleProvider` — any OpenAI-compatible endpoint
- 🔐 No hard-coded API keys; credentials are user-supplied at runtime
- 🧭 Clear error handling when no provider is configured
- 🪶 Minimal permissions (only `INTERNET` + `ACCESS_NETWORK_STATE`)

## Android requirements

| | |
|---|---|
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |
| Language | Kotlin |
| Build system | Gradle 8.7 + Android Gradle Plugin 8.5.2 |

## Project structure

```
.
├── app/                     # AlphaDot application module
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/alphadot/app/
│       │   ├── AlphaDotApp.kt
│       │   ├── data/        # messages, conversations, settings
│       │   ├── provider/    # AiProvider + implementations
│       │   └── ui/          # MainActivity, SettingsActivity, adapter
│       └── res/             # layouts, theme, icons, strings
├── patcher/                 # APK analyze / patch / verify pipeline
├── .github/workflows/       # CI: build, and manual release
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

## Build instructions

### Local

```bash
# Requires JDK 17 and an Android SDK
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
```

The APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

### Verify the APK

```bash
python patcher/verify.py app/build/outputs/apk/debug/app-debug.apk
```

### Analyze / patch an authorized source APK

```bash
python patcher/analyze.py base.apk
python patcher/patch.py base.apk
```

See [`patcher/README.md`](patcher/README.md) for details and the safety policy.

## GitHub Actions

`.github/workflows/build-alphadot.yml` runs on `push` to `main`, pull
requests and manual dispatch. It analyzes and patches the source, builds the
debug APK, verifies it, and uploads it as the **`AlphaDot-APK`** artifact.

To download it: **Actions → Build AlphaDot APK → latest run → Artifacts →
AlphaDot-APK**.

A separate manual workflow `.github/workflows/release-alphadot.yml` creates a
GitHub Release and attaches `AlphaDot-<version>-debug.apk`. It **only** runs on
`workflow_dispatch` and requires a version input such as `v1.0.0`; it never
publishes on every push.

## Developer information

- **Creator:** Umaiz Sufiyan
- **GitHub:** https://github.com/sufiyan-sabeel
- **Instagram:** https://instagram.com/alphadot.app
  *(placeholder in this repository — replace with the official AlphaDot account)*
- **Repository:** https://github.com/sufiyan-sabeel/Alphadot-bot

## Security note

**Never commit API keys, tokens, cookies, passwords or signing keys.** AlphaDot
does not bundle any credentials. User-supplied keys are stored only in the
app's private `SharedPreferences` on the device, are excluded from cloud
backup, and are sent only to the configured provider endpoint. Any future
release signing must use GitHub Secrets (`ANDROID_KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) — never files committed to
the repository.

## Independence & scope

AlphaDot is an independent project. It is **not affiliated with, endorsed by,
or connected to** Grok, X Corp, Anysphere, or any other AI vendor, and it does
not impersonate them. AlphaDot does not bypass any third-party
authentication, billing, licensing or server-side authorization.

## License

Released under the MIT License. See [`LICENSE`](LICENSE).
