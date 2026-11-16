#!/usr/bin/env bash
# Checks for joining a swipe to the touch after it (#83). Needs no Android device: runs on the desktop.
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
  "$keyboard/Contact.kt" "$keyboard/DeleteSwipe.kt" "$keyboard/SwipePieces.kt" "$keyboard/Layout.kt" "$keyboard/Touchpad.kt" \
  tests/swipe/JoinChecks.kt
javac --release 17 -cp "$work:$sdk/platforms/android-36/android.jar" -d "$work" "$engine/Swipe.java" "$engine/Calibration.java" "$engine/TouchAlignment.java"
java -cp "$work:$stdlib" com.atlas.keyboard.JoinChecksKt tests/swipe/join-pairs.txt
