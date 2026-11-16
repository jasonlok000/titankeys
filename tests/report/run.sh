#!/usr/bin/env bash
# Checks the report form's upload (#97) without Android: pack, split, seal, open and join. Then the owner's tool must open
# a report the app sealed.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
connected=android/kotlin/com/atlas/connected
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$sdk/platforms/android-36/android.jar" -d "$work" \
  "$connected/Json.kt" "$connected/Seal.kt" "$connected/Reports.kt" tests/report/ReportChecks.kt
java -cp "$work:$stdlib" com.atlas.connected.ReportChecksKt "$work/handover"
if command -v bun >/dev/null; then
  { read -r dir; read -r id; } < "$work/handover"
  bun tools/contributions/reports.ts open "$dir/key.pem" "$dir" >/dev/null
  cmp "$dir/$id.zip" "$dir/expected.zip"
  rm -rf "$dir"
  echo "The owner's tool opens a report that the app sealed"
else
  echo "SKIP the owner's tool: bun is not installed"
fi
