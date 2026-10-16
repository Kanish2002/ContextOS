# ContextOS — Phase 1 · On-device AI for Android

**Status:** Source-code starter, ready for an Android SDK/Gradle build and physical-device validation. Not yet a verified APK.

ContextOS is an offline-first Android personal AI prototype. Phase 1 is a **local model chat and benchmark harness**. Phone sensors, Google Maps, Health Connect, calendar, personal memory, and background agents are intentionally deferred to later phases. The app does not request internet, location, microphone, camera or broad storage permissions.

## What is implemented

- Android app in Kotlin + Jetpack Compose, dark-themed local chat UI.
- User-initiated model file picker (`ACTION_OPEN_DOCUMENT`); streamed copy to **app-private storage** with progress, size validation, partial-file cleanup, and selected-model persistence.
- LiteRT-LM engine with selectable CPU or GPU backends, lifecycle cleanup, reusable multi-turn conversation, streamed generation, and "New chat" reset.
- Displayed model load time, time to first streamed text, total response time and **character** throughput (no pretending to have exact token counts).
- Offline privacy boundary: no INTERNET permission; no uploads, model APIs, background monitoring, sensors or cloud accounts.
- Kotlin unit tests and stand-alone smoke test; GitHub Actions debug build workflow.

## Requirements

- Android Studio with JDK 17, Android SDK Platform 35, and Gradle 8.14.3.
- Android 8.0+ (API 26); an arm64 real phone is recommended.
- A supported `.litertlm` model; ~1–3 GB free RAM may be needed depending on the model and backend, plus model file storage.
- Internet is needed **outside the app** to fetch build dependencies and download a model file beforehand.

## Get a model

Open [LiteRT Community Gemma 3 1B IT](https://huggingface.co/litert-community/Gemma3-1B-IT/tree/main), sign in and accept its Gemma license. Download `gemma3-1b-it-int4.litertlm` (approximately 584 MB, subject to host changes) or another compatible `.litertlm`. **Do not use `.task` or `.tflite` variants.** Copy the downloaded file into a location Android's file picker can access, such as Downloads.

The model is **not bundled** because of its size and license. Some LiteRT-LM model builds target particular accelerators; start with CPU and switch to GPU only if the selected build and device support it.

## Run

1. Unzip the project and open its **root folder** in Android Studio.
2. Sync Gradle. If the IDE requires a Gradle wrapper, run `gradle wrapper --gradle-version 8.14.3` using a local Gradle installation. A wrapper binary is not included in this source archive.
3. Connect an ARM64 Android phone with USB debugging enabled.
4. Select the `app` run configuration and install the debug build.
5. In ContextOS, tap **Import**, select the downloaded `.litertlm` file, and wait for copying to finish.
6. Keep **CPU** selected initially and tap **Load model**. Once the status reads "Model ready", start chatting.
7. Try a few prompts and record latency and temperature on your device. Use **New chat** to reset model conversation context.

CLI build (with configured Android SDK + Gradle):

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Current limitations / things to verify on a phone

- No Android SDK, Gradle distribution, model weights, or Android device were available in the authoring environment, so a full APK build and live inference were **not** performed here. CI is defined but not yet run.
- `litertlm-android:latest.release` follows Google's current documentation; pin the exact resolved version after the first successful build to ensure reproducibility.
- Model CPU/GPU backend compatibility varies by file and chipset. Unsupported GPU may fail to load, in which case switch to CPU; there is no silent fallback.
- Backgrounding during inference and low-memory process termination need device-level testing. The session is in memory and cleared when the process exits; the imported model survives.
- Benchmark metrics are client-side wall times and approximate character throughput, not model-reported token metrics or scientific benchmarking. First streamed chunk includes prompt processing.
- UI is an initial working design; accessibility, tablet landscape, session export, persisted chat, and hard performance safeguards remain future work.

## Project layout

```text
app/src/main/java/dev/contextos/app/
  MainActivity.kt                 # Compose screens + model selection
  AssistantViewModel.kt          # State, import, load, chat, metrics
  inference/LocalInferenceEngine.kt  # Native LiteRT-LM engine & conversation
  data/ModelStore.kt            # App-private model import
  model/ChatModels.kt           # UI and performance state
  model/StreamAccumulator.kt    # Streaming fragments
app/src/test/.../StreamAccumulatorTest.kt
scripts/smoke_test.sh
.github/workflows/android.yml
```

## Privacy and design decisions

Device input is **explicitly selected** by the user. A local file import is copied into private app storage. No personal data is collected in Phase 1. There are no network permissions or analytics SDKs. Later versions should implement separate opt-in permission controls, encrypted local memory, deletion/export controls, and a minimum-data policy.

## Next milestone

Once the device runs model load and generation reliably: add activity context (walking/stationary), permissions UI, and local event summaries. Do **not** send raw accelerometer samples directly to the language model.

## Authoritative references

- [LiteRT-LM Android Kotlin SDK](https://developers.google.com/edge/litert-lm/android)
- [LiteRT-LM source](https://github.com/google-ai-edge/LiteRT-LM)
- [Official mobile examples](https://github.com/google-ai-edge/litert-samples)
- [Gemma 3 1B model variants](https://huggingface.co/litert-community/Gemma3-1B-IT)

## Licensing

This starter's original code may be used as a foundation for your app. Third-party dependencies and Google Gemma model weights retain their own licenses and terms; the downloaded model isn't redistributed here.


## Phase 2 — context & polished interface

The Phase 2 preview adds:
- Home, Chat, Context, and Settings tabs with custom light/dark palettes, system-following appearance, and optional Android 12+ dynamic color.
- Foreground-only accelerometer and ambient light readings when the Context tab is visible AND its sensor toggle is enabled.
- Step counter readings after granting the Android Activity Recognition permission. **Steps shown are since this app session's first observation, not a whole-day count.** Some phones don't expose the sensor.
- One-shot approximate location lookup on user request (rounded to 0.01 degrees, not a route or automatic history).
- Estimated rolling 24h foreground usage (requires granting special Usage Access in Android Settings, then tapping Refresh).
- User-triggered snapshot saves into app-private SQLite (maximum 100 records; last 20 shown); Delete all clears saved records.
- Separate, initially-disabled setting to include currently available context in the local AI prompt.

**Privacy constraints:** ContextOS does not request Internet, fine location, or background location permission. It doesn't continuously query location or usage, and live motion/light listeners stop when the Context tab closes. The SQLite snapshots are not yet encrypted; Android app-private storage and disabled backup provide basic protection. Removing granted permissions must be done in Android Settings. Chat messages and signal histories are not synced or backed up by this app.

**Scope:** This phase collects first-party phone signals only. No Google Maps Timeline access, Google accounts, health-data sync, passive background activity analysis, or automated recommendation ranking. Android Usage Stats is an estimate and may double-count concurrent foreground time across multiple apps.

**Build:** Pushes/PRs run the source-only workflow. For a single installable APK including the Qwen model, manually run **Actions → Build APK with offline Qwen model → Run workflow**, selecting the updated branch. If an existing debug APK was signed with a different CI-generated debug keystore, Android may require uninstalling it first (which deletes the old app's local data).

**Tests needed on a device:** permissions, switching to Android settings and returning, theme persistence across app restarts, live sensor lifecycle, step counter availability, approximate location on different providers, and local inference with optional context.

The bundled-model workflow also runs automatically on the Phase 2 feature branch to produce a test APK for the redesigned app.


## Phase 3 preview — explainable local insights and keyboard fixes

Phase 3's first slice is implemented in `feature/phase3-insights-keyboard-fixes`.
The opt-in Insights section on Home evaluates explicitly collected, recent phone
signals through a deterministic local rule engine. It shows why each suggestion
appeared; there is no AI inference for insights, background tracking, or network.
Android edge-to-edge/IME fixes keep the chat composer visible while Gboard is open
and prevent the toolbar overlapping status bar content on Android 15.
See [PHASE3.md](PHASE3.md) for the detailed scope, remaining work and device QA.


## Phase 4 preview — encrypted personal memory

Phase 4 starts on the `feature/phase4-encrypted-personal-memory` branch.
Save private user-authored notes in an Android Keystore-backed AES-GCM vault,
inspect/delete/export them through the Memory tab, and *separately opt in* to
retrieving only matching notes when you send local AI chat messages.
Retrieval is deterministic, keyword-based, and bounded; **semantic embeddings and
automatic summaries are not yet implemented**. No network permissions were added.
See [PHASE4.md](PHASE4.md) for exact guarantees, limitations and QA.
