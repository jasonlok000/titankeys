#!/usr/bin/env bash
# Checks Settings > Setup's Sliding Mode 1 list: what it trusts, and that the Chrome and Settings misread can't happen again.
# It doesn't need the phone.
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$work" \
  android/kotlin/com/atlas/keyboard/SlidingModeList.kt tests/sliding-mode/SlidingModeChecks.kt
java -cp "$work:$stdlib" com.atlas.keyboard.SlidingModeChecksKt
