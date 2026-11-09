#!/usr/bin/env bash
# Checks which touches on the bottom row start the cursor or select a word, which doesn't need Android.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/atlas/keyboard
engine=android/kotlin/com/atlas/engine
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$sdk/platforms/android-36/android.jar" -d "$work" \
  "$engine/Swipe.java" "$engine/Calibration.java" "$engine/TouchAlignment.java" \
  "$keyboard/Contact.kt" "$keyboard/CursorGesture.kt" "$keyboard/CursorMode.kt" "$keyboard/DeleteSwipe.kt" \
  "$keyboard/Layout.kt" "$keyboard/Touchpad.kt" tests/cursor/CursorChecks.kt
java -cp "$work:$stdlib" com.atlas.keyboard.CursorChecksKt
