#!/usr/bin/env bash
# Checks the connected features' parts that don't need Android: JSON, the feed's signature and rules, sealing,
# the contribution queue and the example format. Then the owner's tool must open a batch that the app sealed.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
connected=android/kotlin/com/atlas/connected
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$sdk/platforms/android-36/android.jar" -d "$work" \
  "$connected/Json.kt" "$connected/Ed25519.kt" "$connected/Feed.kt" "$connected/Seal.kt" "$connected/ContributionQueue.kt" \
  "$connected/Example.kt" tests/connected/ConnectedChecks.kt
java -cp "$work:$stdlib" com.atlas.connected.ConnectedChecksKt "$work/sealed"
if command -v bun >/dev/null; then
  batch=$(cat "$work/sealed")
  bun tools/contributions/open.ts "$batch/key.pem" "$batch/batch.tkc" > "$work/opened.json"
  bun -e 'const [a, b] = process.argv.slice(1).map(f => JSON.stringify(JSON.parse(require("fs").readFileSync(f, "utf8")))); if (a !== b) { console.error("The owner tool opened something else"); process.exit(1); }' \
    "$work/opened.json" "$batch/expected.json"
  echo "The owner's tool opens a batch sealed by the app"
else
  echo "SKIP the owner's tool: bun is not installed"
fi
