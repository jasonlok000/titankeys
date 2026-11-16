#!/usr/bin/env bash
# Checks Chinese on the bar (docs/languages.md, "Chinese on the bar"): the row's places, the bigger menu's pages, the keys
# that pick, the marks and the English word. Plain Kotlin, no phone: keyboard/Chinese.kt.
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/titankeys/keyboard
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$work" \
  "$keyboard/Chinese.kt" "$keyboard/Layout.kt" "$keyboard/InputMethods.kt" "$keyboard/InputMethod.kt" \
  android/kotlin/com/titankeys/connected/Json.kt tests/chinese/ChineseChecks.kt
java -cp "$work:$stdlib" com.titankeys.keyboard.ChineseChecksKt
