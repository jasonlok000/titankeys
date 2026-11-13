#!/usr/bin/env bash
# Checks the interactive guide's steps and how events finish them, which doesn't need Android.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/atlas/keyboard
# The slides' renders draw the real decoration (GuideDecor) on tests/guide-decor's stand-in Canvas, Paint and Path, which draw with
# Java2D and come before Android on the class path.
mkdir -p "$work/fake"
javac -nowarn -d "$work/fake" tests/guide-decor/fake/android/graphics/*.java
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$work/fake:$stdlib:$sdk/platforms/android-36/android.jar" -d "$work" \
  "$keyboard/GuideDecor.kt" "$keyboard/KeyboardEvents.kt" "$keyboard/GuideSteps.kt" "$keyboard/GuideFinale.kt" "$keyboard/GuideHooks.kt" "$keyboard/Modifiers.kt" "$keyboard/QuickSetting.kt" "$keyboard/Layout.kt" "$keyboard/TrailThickness.kt" "$keyboard/WpmMeter.kt" "$keyboard/KeyHover.kt" "$keyboard/MenuKeys.kt" "$keyboard/MenuMotion.kt" "$keyboard/KeyAlignment.kt" "$keyboard/KeyGeometry.kt" "$keyboard/SteadyKey.kt" \
  tests/guide/GuideChecks.kt tests/guide/SlideRenders.kt tests/guide/FrontPageRender.kt
java -Djava.awt.headless=true -cp "$work/fake:$work:$stdlib" com.atlas.keyboard.GuideChecksKt
