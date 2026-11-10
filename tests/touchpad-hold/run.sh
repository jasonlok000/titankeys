#!/usr/bin/env bash
# Checks when the keyboard keeps its reported status on with no text field, which doesn't need Android.
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$work" \
  android/kotlin/com/atlas/keyboard/TouchpadHold.kt tests/touchpad-hold/TouchpadHoldChecks.kt
java -cp "$work:$stdlib" com.atlas.keyboard.TouchpadHoldChecksKt
