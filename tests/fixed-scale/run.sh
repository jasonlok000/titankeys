#!/usr/bin/env bash
# Checks FixedScale's sizes: the same dp and sp in pixels at every font size and display size, each Settings > Size factor
# growing them alike, and the keys' places never moving. It doesn't need the phone.
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
android=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}/platforms/android-36/android.jar
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$android" -d "$work" \
  android/kotlin/com/atlas/keyboard/FixedScale.kt android/kotlin/com/atlas/keyboard/Accent.kt android/kotlin/com/atlas/keyboard/KeyGeometry.kt \
  android/kotlin/com/atlas/keyboard/KeyAlignment.kt android/kotlin/com/atlas/keyboard/Layout.kt tests/fixed-scale/FixedScaleChecks.kt
java -cp "$work:$stdlib:$android" com.atlas.keyboard.FixedScaleChecksKt
