#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if command -v ./gradlew >/dev/null 2>&1 && [ -f ./gradlew ]; then
  ./gradlew clean test assembleDebug
elif command -v gradle >/dev/null 2>&1; then
  gradle clean test assembleDebug
else
  echo "Gradle is not installed. Open this project in Android Studio or install Gradle 9.6+ and generate the wrapper." >&2
  exit 2
fi
printf '\nAPK: %s\n' "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
