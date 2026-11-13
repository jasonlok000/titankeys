#!/usr/bin/env bash
# Checks the word menu from end to end, which doesn't need a phone: what I, O and Back show and do for every kind of word, with
# the real word lists behind them, and that the hover stays on the pressed key after a press in every order the touchpad and the
# key can come in, and that opening the menu plays no step's burst. Prints the ladder as built.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
engine=android/kotlin/com/atlas/engine
keyboard=android/kotlin/com/atlas/keyboard
javac --release 17 -nowarn -cp "$sdk/platforms/android-36/android.jar" -d "$work" \
  "$engine/LanguageHabits.java" "$engine/SwipeWords.java" "$engine/SuggestionPolicy.java" "$engine/BlockedWords.java" "$engine/ForgetWords.java"
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$work:$stdlib:$sdk/platforms/android-36/android.jar" -d "$work" \
  "$keyboard/MenuKeys.kt" "$keyboard/MenuMotion.kt" "$keyboard/SteadyKey.kt" "$keyboard/Layout.kt" "$keyboard/KeyAlignment.kt" "$keyboard/KeyGeometry.kt" \
  tests/word-menu/MenuBar.kt tests/word-menu/WordMenuChecks.kt
java -cp "$work:$stdlib:$sdk/platforms/android-36/android.jar" com.atlas.engine.WordMenuChecksKt
