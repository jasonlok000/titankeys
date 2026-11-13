#!/usr/bin/env bash
# Draws the gesture display on the bar's one line with the real drawing code, at every Bar text size, and checks that every
# shape is centred on the line, sized from the words, and clear of the screen's outline (see GestureLine.kt).
# Usage: bash tests/gesture-line/run.sh [folder]. The frames go to folder/frames, one sheet a size to folder.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
out=$(realpath -m "${1:-gesture-line}")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/atlas/keyboard
engine=android/kotlin/com/atlas/engine
# The checks' own copies of the drawing's sizes must still be the drawing's.
for line in "const val HANDLE_KNOB = 4.5f" "const val STEER_CARET = 2f"; do
  grep -qF "$line" "$keyboard/GestureDrawing.kt" || { echo "GestureDrawing.kt changed: $line"; exit 1; }
done
grep -qF "drawing.draw(canvas, gesture, side.toFloat(), (width - side).toFloat(), barHeight, look, line, wordFoot(), wordPixels())" "$keyboard/Strip.kt" \
  || { echo "Strip.kt no longer draws the gestures on the bar's line"; exit 1; }
grep -qF "private const val WORD_SIZE = 20f" "$keyboard/Strip.kt" || { echo "Strip.kt's word size changed"; exit 1; }
# The guide's fade must still be the real one, as tests/gesture-render checks.
fade="fun faded(colour: Int, alpha: Float) = colour and 0x00FFFFFF or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)"
grep -qF "$fade" "$keyboard/GuideKeyboardView.kt" || { echo "GuideKeyboardView.kt changed: $fade"; exit 1; }
# The stand-in Canvas and Paint draw with Java2D. They come before Android on the class path, so the drawing code runs on them.
mkdir -p "$work/fake" "$work/classes"
javac -nowarn -d "$work/fake" tests/gesture-render/fake/android/graphics/*.java
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$work/fake:$stdlib:$sdk/platforms/android-36/android.jar" -d "$work/classes" \
  "$engine/Swipe.java" "$engine/Calibration.java" "$engine/TouchAlignment.java" "$engine/TypingSettings.java" "$keyboard/Look.kt" "$keyboard/Accent.kt" \
  "$keyboard/Contact.kt" "$keyboard/CursorGesture.kt" "$keyboard/DeleteSwipe.kt" "$keyboard/Layout.kt" "$keyboard/Touchpad.kt" \
  "$keyboard/WordSelection.kt" "$keyboard/CursorMode.kt" "$keyboard/GestureDisplay.kt" "$keyboard/GestureDrawing.kt" "$keyboard/StripFit.kt" \
  "$keyboard/TrailThickness.kt" "$keyboard/KeyAlignment.kt" "$keyboard/KeyGeometry.kt" "$keyboard/Corners.kt" "$keyboard/FixedScale.kt" "$keyboard/Radii.kt" \
  tests/gesture-render/GuideStandIns.kt tests/gesture-line/GestureLine.kt
java -Djava.awt.headless=true -cp "$work/fake:$work/classes:$stdlib:$sdk/platforms/android-36/android.jar" com.atlas.keyboard.GestureLineKt "$out" android/src/main/res/font
