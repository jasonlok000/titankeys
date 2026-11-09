#!/usr/bin/env bash
# Checks autocorrect with its real word data, including Numen's typo tests. It doesn't need the phone.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
android=$sdk/platforms/android-36/android.jar
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
root=android/kotlin
engine=$root/com/atlas/engine
keyboard=$root/com/atlas/keyboard
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$android" -d "$work" \
  "$root/com/atlas/autocorrect" "$keyboard/Composer.kt" "$keyboard/Layout.kt" "$keyboard/Field.kt" "$keyboard/EnterKey.kt" \
  "$keyboard/RawLogLine.kt" "$keyboard/Recording.kt" "$keyboard/Contact.kt" "$keyboard/DeleteSwipe.kt" "$keyboard/Touchpad.kt" \
  "$keyboard/Haptics.kt" \
  tests/autocorrect/AutocorrectChecks.kt tests/autocorrect/PhoneWords.java $(find "$engine" "$root/org" -name '*.java')
javac --release 17 -nowarn -cp "$work:$stdlib:$android" -d "$work" tests/autocorrect/PhoneWords.java \
  $(find "$engine" "$root/org" -name '*.java')
java -cp "$work:$stdlib:$android" com.atlas.autocorrect.AutocorrectChecksKt
