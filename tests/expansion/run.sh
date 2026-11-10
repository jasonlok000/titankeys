#!/usr/bin/env bash
# Checks snippets, emoji shortcodes, exact typing and the fields with no suggestions, which don't need Android.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$sdk/platforms/android-36/android.jar" -d "$work" \
  android/kotlin/com/atlas/keyboard/Expansions.kt android/kotlin/com/atlas/keyboard/ExactTyping.kt android/kotlin/com/atlas/keyboard/Field.kt \
  tests/expansion/ExpansionChecks.kt
java -cp "$work:$stdlib" com.atlas.keyboard.ExpansionChecksKt
