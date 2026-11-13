#!/usr/bin/env bash
# Checks how the strip fits long words: shrink, borrow room, then cut where the words differ. It doesn't need Android.
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
# FixedScale gives the Size factors, so it needs Android's classes to compile.
android=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}/platforms/android-36/android.jar
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$android" -d "$work" \
  android/kotlin/com/atlas/keyboard/StripFit.kt android/kotlin/com/atlas/keyboard/FixedScale.kt tests/strip-fit/StripFitChecks.kt
java -cp "$work:$stdlib:$android" com.atlas.keyboard.StripFitChecksKt
