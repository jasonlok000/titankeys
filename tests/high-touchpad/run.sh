#!/usr/bin/env bash
# Checks when the touchpad window stands above the notification shade, which doesn't need Android.
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$work" \
  android/kotlin/com/atlas/keyboard/HighTouchpad.kt tests/high-touchpad/HighTouchpadChecks.kt
java -cp "$work:$stdlib" com.atlas.keyboard.HighTouchpadChecksKt
