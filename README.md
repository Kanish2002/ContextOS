# ContextOS — Phase 1

Offline-first personal AI for Android. This repository contains a Kotlin/Jetpack Compose app powered by Google LiteRT-LM. Phase 1 includes **on-device Qwen3-0.6B chat** with a model bundled into a debug APK.

> **Status:** Prototype. The Android build and inference must be verified by GitHub Actions and then on a physical device. No APK is committed to git.

## Build the model-inclusive APK

1. Go to **Actions → Build offline ContextOS APK → Run workflow**.
2. The workflow downloads the [Qwen3 0.6B INT4 no-think LiteRT-LM](https://huggingface.co/litert-community/Qwen3-0.6B-int4) model (~347 MB), verifies SHA-256, compiles the Android app, and checks the APK asset.
3. Download the **ContextOS-Qwen3-APK** artifact from the completed workflow run.
4. Extract `app-debug.apk`, transfer it to your Android phone and install. It requires Android 8+ and ARM64 is recommended.

The **app does not use the internet**, and its model is extracted once into app-private storage at first launch. Leave at least 1 GB of available space in addition to the downloaded APK for installation and extraction. Debug APKs are for testing and are not Play Store releases.

## Stack

- Kotlin / Jetpack Compose, Android SDK 35, JDK 17
- Google `com.google.ai.edge.litertlm:litertlm-android`
- Quantized Qwen3 0.6B, CPU by default
- GitHub Actions Android build with reproducible model digest check

## Privacy and limitations

Phase 1 does not request network, location, contacts, camera, microphone, sensor, or storage permissions. It does **not** have access to personal device data; context awareness will be a later opt-in feature. CPU-only inference can be slow on some devices. The model is Apache-2.0 licensed; see [the model card](https://huggingface.co/litert-community/Qwen3-0.6B-int4) and [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0).

Model weights are fetched during CI; they are not stored in the repo. You can build locally with a supported model at `app/src/main/assets/models/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm`.

## References
- [LiteRT-LM Android documentation](https://developers.google.com/edge/litert-lm/android)
- [LiteRT-LM runtime](https://github.com/google-ai-edge/LiteRT-LM)

## Future phases

Context providers (activity, location, calendar), private memory, and proactive recommendations. These features will require explicit user consent.
