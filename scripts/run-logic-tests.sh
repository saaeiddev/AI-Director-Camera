#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/.logic-test"
rm -rf "$OUT" && mkdir -p "$OUT"
kotlinc \
  "$ROOT/app/src/main/java/com/saeid/aidirectorcamera/ai/Models.kt" \
  "$ROOT/app/src/main/java/com/saeid/aidirectorcamera/ai/GuidanceEngine.kt" \
  "$ROOT/app/src/main/java/com/saeid/aidirectorcamera/director/DirectorPlanner.kt" \
  "$ROOT/app/src/main/java/com/saeid/aidirectorcamera/director/StoryboardPlanner.kt" \
  "$ROOT/scripts/logic_smoke_test.kt" \
  -include-runtime -d "$OUT/logic-tests.jar"
java -jar "$OUT/logic-tests.jar"
