#!/usr/bin/env bash
# Checks the language framework without a phone: the input method file, the registry, the switch overlay, the accent picker,
# the word lists and the engine interface, then every input method in android/assets/im (docs/languages.md).
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/titankeys/keyboard
# The Sym panel's paging direction, which the choices share.
javac --release 17 -d "$work" android/kotlin/com/titankeys/engine/EmojiPages.java
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$work:$stdlib" -d "$work" \
  "$keyboard/InputEngine.kt" "$keyboard/InputMethod.kt" "$keyboard/InputMethods.kt" "$keyboard/WordList.kt" "$keyboard/Layout.kt" \
  "$keyboard/Hangul.kt" "$keyboard/Telex.kt" "$keyboard/Rules.kt" "$keyboard/LanguageLearning.kt" "$keyboard/Favourites.kt" \
  "$keyboard/LanguageSettings.kt" "$keyboard/QuickSetting.kt" "$keyboard/EngineWait.kt" "$keyboard/HoldHint.kt" "$keyboard/EngineSlips.kt" "$keyboard/BarLog.kt" android/kotlin/com/titankeys/autocorrect/KeyNeighbours.kt \
  android/kotlin/com/titankeys/autocorrect/PairCounts.kt \
  android/kotlin/com/titankeys/connected/Json.kt tests/languages/LanguageChecks.kt
java -cp "$work:$stdlib" com.titankeys.keyboard.LanguageChecksKt android/assets/im
