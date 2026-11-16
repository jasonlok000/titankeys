#!/usr/bin/env bash
# Checks the Shift, Alt, Sym and Ctrl logic, which doesn't need Android.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$sdk/platforms/android-36/android.jar" -d "$work" \
  android/kotlin/com/titankeys/keyboard/Modifiers.kt android/kotlin/com/titankeys/keyboard/TakenKeys.kt android/kotlin/com/titankeys/keyboard/QuickSetting.kt \
  android/kotlin/com/titankeys/keyboard/Layout.kt android/kotlin/com/titankeys/keyboard/ModifierBar.kt android/kotlin/com/titankeys/keyboard/SteadyKey.kt android/kotlin/com/titankeys/keyboard/Corners.kt android/kotlin/com/titankeys/engine/EmojiPages.java \
  tests/modifiers/ModifiersChecks.kt
java -cp "$work:$stdlib" com.titankeys.keyboard.ModifiersChecksKt
