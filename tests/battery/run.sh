#!/usr/bin/env bash
# Checks #85's battery fixes that don't need Android: a burst of name changes is read once, after it settles.
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/titankeys/keyboard
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$work" "$keyboard/Settle.kt" tests/battery/BatteryChecks.kt
java -cp "$work:$stdlib" com.titankeys.keyboard.BatteryChecksKt
