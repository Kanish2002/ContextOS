# ContextOS — Offline-first personal AI for Android

**Version:** 0.6.0 beta (Phases 1–6 plus personal agent modules). **Branch:** `main`.
The app builds without any bundled Qwen, Gemma, embedding or other AI model weights.
Users import their own compatible model files on their Android devices.

## Features

- **Phase 1:** Local chat powered by Google's LiteRT-LM, user-imported `.litertlm` models, streaming replies, CPU/GPU selection, and local inference benchmarks.
- **Phase 2:** Light/dark/system appearance and Android device context, including consent-controlled foreground sensors, approximate location and on-demand usage reports. Saved snapshots are app-private SQLite (not yet encrypted).
- **Phase 3:** Local rules-based suggestions, on-demand LiteRT-LM explanations, Helpful/Not helpful feedback, reset, optional suggestion reminders, quiet hours, daily notification limits and cooldowns.
- **Phase 4:** AES-GCM encrypted Android Keystore personal memory vault, editable preferences/goals/facts, explicit consent for locally retrieved memory in chat, optional on-device semantic embeddings, and review/approve-before-save memory summary suggestions.

**Privacy:** INTERNET is permitted solely for explicitly authorized Google API previews and confirmed cloud requests. Offline AI and encrypted archives operate without network access. Sensor access is foreground and consent controlled. Notifications do not read sensors or run AI in the background. Plaintext memory exports are explicitly warned about before saving. Device-only usage, memory safety and model performance require physical-device validation.

## Build — main branch only

The **only** GitHub Actions workflow is [`.github/workflows/android.yml`](.github/workflows/android.yml).
It triggers **only on commits pushed to `main`**. Feature-branch pushes, pull requests, and manual workflow dispatches do not build APKs.

The workflow:
1. Verifies there are no model weights in app asset sources.
2. Runs `gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug`.
3. Inspects `app-debug.apk` to verify that it contains no packaged model weights.
4. Uploads the **ContextOS-Debug-APK-main** build artifact for download.

No workflow downloads Qwen, makes a second bundled APK or triggers on completion of another build. Historic GitHub Actions runs may remain in the Actions history.

## Install and load your own local model

1. Download the latest main build from [GitHub Actions](https://github.com/Kanish2002/ContextOS/actions/workflows/android.yml), then extract `app-debug.apk` from the artifact ZIP.
2. Install the APK on an Android device. **Debug signatures from different builders may prevent installing over an existing debug APK**; back up anything important before reinstalling.
3. Separately obtain a compatible LiteRT-LM `.litertlm` model and place it in your phone's Downloads or another location accessible to the Android system file picker.
4. In ContextOS, use **Settings → On-device model → Import**, choose the `.litertlm`, then tap **Load model**. Start with CPU and only switch to GPU if your device and model support it.
5. For Phase 4 semantic recall, optionally import a metadata-bearing MediaPipe Text Embedder `.tflite` using **Memory → Import Text Embedder**. This is a different model from the chat `.litertlm`. The app falls back to local keyword recall without one.

**Model files are not part of the APK.** Expect adequate free disk space and device RAM for your chosen model. Actual memory use and local response latency depend on model/device.

## Local development

Requirements: JDK 17, Android SDK Platform 35 and Gradle 8.14.3.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
python3 scripts/verify_model_free_apk.py app/build/outputs/apk/debug/app-debug.apk
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Detailed phase documentation

- [Phase 2](PHASE2.md)
- [Phase 3 initial scope](PHASE3.md) and [Phase 3 added features](PHASE3-COMPLETION.md)
- [Phase 4 initial scope](PHASE4.md) and [Phase 4 added features](PHASE4-COMPLETION.md)

See [Full integrations and idea coverage](PHASE5-6-ALL-IDEAS.md) for Gmail/Drive, Calendar, Health Connect, selected files/photos, Timeline, notification access, career, commute, voice, proactive agent and cloud fallback. Open **Settings → Integrations & Personal Agent** to use them.

These source-level features are not a production certification. Physical-device verification is still needed for inference, semantic accuracy, notifications, keyboard behavior and app-data upgrades.

## Licenses

Third-party components, model weights downloaded by the user and runtime dependencies have their own licenses. ContextOS does not redistribute AI model weights.
