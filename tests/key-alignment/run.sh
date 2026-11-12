#!/usr/bin/env bash
# Checks how Key calibration moves the drawings of the keys, which doesn't need Android. Set CALIBRATION_PNG to a folder to also
# draw the calibration screen's grid there.
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/atlas/keyboard
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$work" \
  "$keyboard/KeyAlignment.kt" "$keyboard/Layout.kt" tests/key-alignment/KeyAlignmentChecks.kt
java -Djava.awt.headless=true -cp "$work:$stdlib" com.atlas.keyboard.KeyAlignmentChecksKt
