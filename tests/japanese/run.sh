#!/usr/bin/env bash
# Checks Japanese through Mozc (docs/languages.md, "Japanese"): the engine's keys on the bar, with a stand-in for Mozc, and
# the romaji readings turn into. No phone and no native library: native/mozc/build.sh checks the real Mozc, and
# tests/japanese/corpus.sh measures it on public sentences.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mozc=android/kotlin/com/titankeys/mozc
keyboard=android/kotlin/com/titankeys/keyboard
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$sdk/platforms/android-36/android.jar" -d "$work" \
  "$mozc/MozcCalls.kt" "$mozc/MozcEngine.kt" "$mozc/KanaRomaji.kt" android/kotlin/com/titankeys/rime/RimeData.kt \
  "$keyboard/InputEngine.kt" "$keyboard/InputMethod.kt" "$keyboard/Chinese.kt" "$keyboard/Layout.kt" "$keyboard/InputMethods.kt" \
  android/kotlin/com/titankeys/connected/Json.kt tests/japanese/JapaneseChecks.kt
java -cp "$work:$stdlib" com.titankeys.mozc.JapaneseChecksKt
