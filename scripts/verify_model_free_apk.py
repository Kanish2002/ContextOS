#!/usr/bin/env python3
"""Verify an Android APK includes no developer-bundled LLM or personal embedding model.

ContextOS uses user-imported .litertlm and .tflite models for its chat/RAG.
Small *third-party* ML Kit OCR and image-labeling runtime assets may legitimately
be included in the app; these are not Qwen or user-imported language models.
"""
from pathlib import Path
import sys
import zipfile

SOURCE_ASSETS = Path("app/src/main/assets")
USER_MODEL_EXT = {".litertlm", ".tflite", ".task", ".gguf", ".onnx", ".safetensors", ".pth", ".pt"}
LLM_EXT = {".litertlm", ".gguf", ".safetensors", ".pth", ".pt"}
MAX_UNEXPECTED_ASSET = 60_000_000


def is_user_model(path: str) -> bool:
    p = Path(path)
    suffix = p.suffix.lower()
    normalized = path.lower().replace("\\", "/")
    return (suffix in LLM_EXT
            or (normalized.startswith("assets/models/") and suffix in USER_MODEL_EXT)
            or (normalized.startswith("app/src/main/assets/models/") and suffix in USER_MODEL_EXT))


def check_sources():
    bad = []
    if SOURCE_ASSETS.exists():
        for path in SOURCE_ASSETS.rglob("*"):
            if not path.is_file():
                continue
            rel = "app/src/main/assets/" + str(path.relative_to(SOURCE_ASSETS)).replace("\\", "/")
            if is_user_model(rel) or path.stat().st_size >= MAX_UNEXPECTED_ASSET:
                bad.append(rel)
    if bad:
        raise SystemExit("FAIL: User AI weights/oversized assets committed: " + ", ".join(bad))
    print("PASS: No bundled personal LLM/embedding weights in source.")


def check_apk(path):
    with zipfile.ZipFile(path) as apk:
        if "AndroidManifest.xml" not in apk.namelist():
            raise SystemExit("FAIL: Not an Android APK.")
        bad = []
        for info in apk.infolist():
            if not info.filename.startswith("assets/"):
                continue
            if is_user_model(info.filename) or info.file_size >= MAX_UNEXPECTED_ASSET:
                bad.append(info.filename)
        if bad:
            raise SystemExit("FAIL: APK contains user model weights: " + ", ".join(bad))
    print("PASS: Model-import APK contains no Qwen or bundled user language models:", path)


if __name__ == "__main__":
    check_sources()
    if len(sys.argv) == 2 and sys.argv[1] == "--sources-only":
        sys.exit(0)
    if len(sys.argv) != 2:
        raise SystemExit("Usage: verify_model_free_apk.py [--sources-only | app-debug.apk]")
    check_apk(sys.argv[1])
