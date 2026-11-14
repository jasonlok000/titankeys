#!/usr/bin/env bash
# Plays every idle game and way home on the bar's line, at every Bar text size, and checks every frame: nothing that shows within
# 4 px of the screen's outline, nothing cut by the bar's top, no jumps. Given a folder, it also draws keyframes of each there, as
# the screen's bottom for tools/corner_check.py (frames/) and as sheets with the border under the screen (sheets/).
# Usage: bash tests/idle-render/run.sh [folder]
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
android=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}/platforms/android-36/android.jar
out=${1:+$(realpath -m "$1")}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/atlas/keyboard
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$android" -d "$work" \
  "$keyboard/MenuKeys.kt" "$keyboard/StripFit.kt" "$keyboard/MenuMotion.kt" "$keyboard/Layout.kt" "$keyboard/KeyAlignment.kt" "$keyboard/KeyGeometry.kt" \
  "$keyboard/SteadyKey.kt" "$keyboard/MenuPath.kt" "$keyboard/MenuScene.kt" "$keyboard/QuickSetting.kt" "$keyboard/FixedScale.kt" "$keyboard/Accent.kt" \
  "$keyboard/Corners.kt" "$keyboard/IdleGames.kt" tests/word-menu/MenuBar.kt tests/word-menu/QuickSettingsPage.kt tests/menu-render/AwtMenuCanvas.kt \
  tests/menu-render/MenuRender.kt tests/menu-render/BarLine.kt tests/menu-render/BarFrames.kt tests/menu-render/AndroidRow.kt tests/idle-render/IdleRender.kt
java -Djava.awt.headless=true -cp "$work:$stdlib:$android" com.atlas.keyboard.IdleRenderKt android/src/main/res/font $out
