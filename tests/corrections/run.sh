#!/usr/bin/env bash
# Measures corrections in every language with a word list: Space's right and wrong changes, and the bar, on made-up slips of
# each list's commonest words (LanguageCorrections). It doesn't need the phone.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
root=android/kotlin
keyboard=$root/com/titankeys/keyboard
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$sdk/platforms/android-36/android.jar" -d "$work" \
  "$root/com/titankeys/autocorrect" "$keyboard/Layout.kt" "$keyboard/InputMethod.kt" "$keyboard/InputMethods.kt" "$keyboard/WordList.kt" \
  "$keyboard/LanguageCorrections.kt" "$keyboard/EngineSlips.kt" "$keyboard/InputEngine.kt" "$keyboard/Telex.kt" "$keyboard/Rules.kt" "$root/com/titankeys/connected/Json.kt" tests/corrections/CorrectionChecks.kt
java -Xmx2g -cp "$work:$stdlib:$sdk/platforms/android-36/android.jar" com.titankeys.keyboard.CorrectionChecksKt "${IM_FOLDER:-android/assets/im}" "$@"
