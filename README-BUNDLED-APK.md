# ContextOS — Single APK with bundled offline AI

This source package adapts the Phase 1 Android app to *bundle* an on-device Qwen3-0.6B INT4 LiteRT-LM model. The app auto-extracts its model to app-private storage and loads it using CPU inference when first opened. No network or user model import is required after installation.

**IMPORTANT: The model weights are not in this small source ZIP, and this ZIP is NOT an APK.** This execution environment had neither an Android SDK nor reachable model-download servers. No model-inclusive APK or physical-device test could be completed here. The GitHub Actions workflow below downloads, checks, compiles and attaches an APK.

## Recommended model

- Repo: https://huggingface.co/litert-community/Qwen3-0.6B-int4
- File: `qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm` (~347 MB)
- License: Apache 2.0; license and attribution are bundled as text assets.
- SHA256: `2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139`
- Inference runtime: Google's LiteRT-LM, on-device, no Internet permission.

## Build an APK with the model included — GitHub Actions

1. Create a new GitHub repository (private is fine). Upload/extract this source **as files at the repo root**, including `.github/workflows/bundled-apk.yml`.
2. Go to **Actions → Build APK with offline Qwen model → Run workflow**.
3. When the build finishes successfully, open the workflow run and download the artifact **ContextOS-Qwen3-Offline-Debug-APK**.
4. Extract the downloaded artifact ZIP, transfer `app-debug.apk` to your Android phone, and install. You might need to allow installation from that source.
5. Open ContextOS. On its first run it copies the bundled model into app-private storage, verifies integrity and automatically loads it. Keep at least ~1GB free storage beyond the APK; additional working space is required during install/extraction.

The APK is a **debug-signed prototype**, not a production-signed Play Store build. Build/installation and device inference are unverified until the workflow and phone tests succeed. The model takes several seconds to initialize on a phone and CPU inference speed varies widely by hardware.

## Local Android Studio build

1. Download the model from the repository URL and place it at:
   `app/src/main/assets/models/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm`
2. Verify checksum with `sha256sum` (above).
3. Build `:app:assembleDebug` using JDK 17, Android SDK 35, and Gradle 8.14.3.
4. Optionally run `python3 scripts/verify_bundled_apk.py app/build/outputs/apk/debug/app-debug.apk`.
5. Install the resulting APK on your arm64 Android phone.

Existing file-import mode is preserved, so users can optionally import a different LiteRT-LM model later.

## How bundling works

- Gradle includes the `.litertlm` under `assets/models/` without additional compression.
- Android's LiteRT-LM runtime needs a usable filesystem path. On first launch, the app streams the model from APK assets to internal app-private `files/models/`, verifies its SHA256, and atomically completes extraction.
- The app uses the copied file for offline inference (CPU is the safe default); there is no INTERNET permission.
- Expect temporary and installed storage costs higher than the model size because the APK and the extracted copy coexist.
- If app data is cleared, the first-launch extraction runs again.

## If you prefer Gemma 3 1B instead

A LiteRT-LM Gemma `.litertlm` model is larger (~584 MB) and gated behind license acceptance. Its original license imposes additional distribution obligations. This source package currently uses Qwen to make model-inclusive builds feasible without gated credentials. Swapping model assets requires updating the model integrity metadata in `ModelStore.kt` and the build verification script.

## Privacy / limitations

No sensors, maps, calendar, email or health data in Phase 1. Future modules can be added with consent. Build workflow relies on GitHub/Hugging Face connectivity; installation and inference after install can remain offline.
