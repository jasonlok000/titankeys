#!/usr/bin/env bash
# Checks what a push of Back does with a panel open, which doesn't need Android.
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$work" \
  android/kotlin/com/atlas/keyboard/BackPush.kt tests/back/BackPushChecks.kt
java -cp "$work:$stdlib" com.atlas.keyboard.BackPushChecksKt
