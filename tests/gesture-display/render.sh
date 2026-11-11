#!/usr/bin/env bash
# Renders the delete's dots on the bar to pictures, to look at by eye. Not a check: it writes PNGs to the folder given, or
# render-out. It builds the drawing with stand-ins for Android's graphics, so it runs on a computer.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
out=$(realpath -m "${1:-render-out}")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/atlas/keyboard
engine=android/kotlin/com/atlas/engine
# The stand-ins come first, so they take the place of android.jar's.
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$sdk/platforms/android-36/android.jar" -d "$work" \
  tests/gesture-display/render/Graphics.kt tests/gesture-display/render/Stand.kt tests/gesture-display/render/RenderBar.kt \
  "$engine/Swipe.java" "$engine/Calibration.java" "$engine/TouchAlignment.java" \
  "$keyboard/Contact.kt" "$keyboard/CursorGesture.kt" "$keyboard/CursorMode.kt" "$keyboard/DeleteSwipe.kt" \
  "$keyboard/Layout.kt" "$keyboard/Touchpad.kt" "$keyboard/WordSelection.kt" "$keyboard/GestureDisplay.kt" "$keyboard/TrailThickness.kt" \
  "$keyboard/GestureDrawing.kt"
java -Djava.awt.headless=true -cp "$work:$stdlib" com.atlas.keyboard.RenderBarKt "$out"
