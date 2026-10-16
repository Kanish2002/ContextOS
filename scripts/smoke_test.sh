#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT
kotlinc app/src/main/java/dev/contextos/app/model/ChatModels.kt \
   app/src/main/java/dev/contextos/app/model/StreamAccumulator.kt \
   scripts/SmokeCheck.kt -include-runtime -d "$tmpdir/smoke.jar"
java -jar "$tmpdir/smoke.jar"
