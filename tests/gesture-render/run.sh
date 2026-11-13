#!/usr/bin/env bash
# Draws the strip's steering and selecting displays to PNGs with the real drawing code, to look at without the phone.
# Usage: bash tests/gesture-render/run.sh [folder]. Each moment gets a PNG there, dark and light, and all.png is the sheet.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
out=$(realpath -m "${1:-gesture-render}")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/atlas/keyboard
engine=android/kotlin/com/atlas/engine
# The stand-in for the guide's fade must still be the real one. The orange is the real Accent's, compiled in below.
line="fun faded(colour: Int, alpha: Float) = colour and 0x00FFFFFF or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)"
grep -qF "$line" "$keyboard/GuideKeyboardView.kt" || { echo "GuideKeyboardView.kt changed: $line"; exit 1; }
# The stand-in Canvas and Paint draw with Java2D. They come before Android on the class path, so the drawing code runs on them.
mkdir -p "$work/fake" "$work/classes"
javac -nowarn -d "$work/fake" tests/gesture-render/fake/android/graphics/*.java
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$work/fake:$stdlib:$sdk/platforms/android-36/android.jar" -d "$work/classes" \
  "$engine/Swipe.java" "$engine/Calibration.java" "$engine/TouchAlignment.java" "$engine/TypingSettings.java" "$keyboard/Look.kt" "$keyboard/Accent.kt" \
  "$keyboard/Contact.kt" "$keyboard/CursorGesture.kt" "$keyboard/DeleteSwipe.kt" "$keyboard/Layout.kt" "$keyboard/Touchpad.kt" \
  "$keyboard/WordSelection.kt" \
  "$keyboard/CursorMode.kt" "$keyboard/GestureDisplay.kt" "$keyboard/GestureDrawing.kt" "$keyboard/TrailThickness.kt" "$keyboard/KeyAlignment.kt" "$keyboard/KeyGeometry.kt" \
  tests/gesture-render/GuideStandIns.kt tests/gesture-render/GestureRender.kt
java -Djava.awt.headless=true -cp "$work/fake:$work/classes:$stdlib" com.atlas.keyboard.GestureRenderKt "$out"
