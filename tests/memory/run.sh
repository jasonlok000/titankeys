#!/usr/bin/env bash
# The heap across input method switches, without the phone: every input method with a word list, three times through, loaded
# the way Languages and LanguageCorrection load them (its list, its next words, its corrector), only the current one kept. The
# heap kept after each pass must stay flat, and corrector builds run one at a time (LatestOnly).
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
  "$keyboard/LanguageCorrections.kt" "$keyboard/LanguageLearning.kt" "$keyboard/Favourites.kt" "$keyboard/EngineSlips.kt" "$keyboard/InputEngine.kt" \
  "$keyboard/Telex.kt" "$keyboard/Rules.kt" "$keyboard/LatestOnly.kt" "$root/com/titankeys/connected/Json.kt" tests/memory/MemoryChecks.kt
# The phone's keyboard has 256 MB: so does this.
timeout 900 java -Xmx256m -cp "$work:$stdlib:$sdk/platforms/android-36/android.jar" com.titankeys.keyboard.MemoryChecksKt "${IM_FOLDER:-android/assets/im}"
