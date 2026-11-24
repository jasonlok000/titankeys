#!/usr/bin/env bash
# Checks when Back is the keyboard's (a replay, a keyboard really on screen), which doesn't need Android.
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$work" \
  android/kotlin/com/titankeys/keyboard/BackRule.kt tests/back-rule/BackRuleChecks.kt
java -cp "$work:$stdlib" com.titankeys.keyboard.BackRuleChecksKt
