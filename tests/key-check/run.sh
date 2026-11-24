#!/usr/bin/env bash
# Checks how the Sym and fn keys' settings on the phone's Shortcut keys page are judged (KeyCheck, #116).
# It doesn't need the phone.
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$work" \
  android/kotlin/com/titankeys/keyboard/KeyCheck.kt tests/key-check/KeyCheckChecks.kt
java -cp "$work:$stdlib" com.titankeys.keyboard.KeyCheckChecksKt
