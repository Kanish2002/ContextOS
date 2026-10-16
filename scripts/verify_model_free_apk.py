#!/usr/bin/env python3
"""Fail a build when local AI model weights are accidentally packaged.

The user imports their own LiteRT-LM model from Android's document picker.
No model download, embedding, or distribution belongs in GitHub Actions.
"""
from pathlib import Path
import sys
import zipfile

ASSETS_DIR = Path("app/src/main/assets")
MODEL_EXTENSIONS = {".litertlm", ".tflite", ".task", ".gguf", ".onnx", ".safetensors", ".pth", ".pt"}

def check_sources():
    if not ASSETS_DIR.exists():
        return
    bad = [
        str(p)
        for p in ASSETS_DIR.rglob("*")
        if p.is_file() and (p.suffix.lower() in MODEL_EXTENSIONS or p.stat().st_size > 10_000_000)
    ]
    if bad:
        raise SystemExit("FAIL: Bundled weights or unexpected large assets found: " + ", ".join(bad))
    print("PASS: No model weights in app source assets.")

def check_apk(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if "AndroidManifest.xml" not in names:
            raise SystemExit("FAIL: Not an Android APK.")
        bad = [
            info.filename for info in archive.infolist()
            if info.filename.startswith("assets/") and
            (Path(info.filename).suffix.lower() in MODEL_EXTENSIONS or info.file_size > 10_000_000)
        ]
        if bad:
            raise SystemExit("FAIL: APK contains model weights or unexpected large assets: " + ", ".join(bad))
    print("PASS: Android APK contains no bundled model weights:", path)

if __name__ == "__main__":
    check_sources()
    if len(sys.argv) == 2 and sys.argv[1] == "--sources-only":
        sys.exit(0)
    if len(sys.argv) != 2:
        raise SystemExit("Usage: verify_model_free_apk.py [--sources-only | path/to/app-debug.apk]")
    check_apk(sys.argv[1])
