#!/usr/bin/env bash
# Checks the real keys' geometry and how Key calibration moves it, which doesn't need Android. Set CALIBRATION_PNG to a folder to
# also draw the calibration screen there, and KEYS_PNG to a folder to draw the guide's keys.
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/atlas/keyboard
# The checks copy the guide's zones and the calibration screen's words: they must still be the real ones.
for pair in "GuideKeyboardView.kt:const val AREA_LEFT = .05f" "GuideKeyboardView.kt:const val AREA_RIGHT = .95f" \
  "GuideKeyboardView.kt:const val AREA_TOP = .5f / 4" "GuideKeyboardView.kt:const val AREA_BOTTOM = 3.54f / 4" \
  "GuideKeyboardView.kt:const val ZONE_BOTTOM = .885f" "GuideKeyboardView.kt:const val ZONE_TAP_ROW = .72f" \
  "GuideKeyboardView.kt:const val HOLD_PAUSE_X = .95f - PAUSE_KEYS / 10f" "GuideKeyboardView.kt:const val HOLD_TOP_X = .95f - REPEAT_LONG_KEYS / 10f" \
  "DeleteSwipe.kt:internal const val PAUSE_KEYS = .8f" "DeleteSwipe.kt:internal const val REPEAT_LONG_KEYS = 5.5f" \
  "CursorGesture.kt:const val ZONE_TOP = .5f" "CursorGesture.kt:const val ZONE_LEFT = .3f" "CursorGesture.kt:const val ZONE_RIGHT = .7f" \
  "KeyCalibrationActivity.kt:const val BANDS = 72f"; do
  grep -qF "${pair#*:}" "$keyboard/${pair%%:*}" || { echo "${pair%%:*} changed: ${pair#*:}"; exit 1; }
done
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$work" \
  "$keyboard/KeyAlignment.kt" "$keyboard/KeyGeometry.kt" "$keyboard/Layout.kt" tests/key-alignment/KeyAlignmentChecks.kt
java -Djava.awt.headless=true -cp "$work:$stdlib" com.atlas.keyboard.KeyAlignmentChecksKt
