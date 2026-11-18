#!/usr/bin/env bash
# Checks the Debug feed's lifetime logic without Android or a phone.
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$work" \
  android/debug/recorder/FeedFocusLease.kt tests/debug-feed-focus/FeedFocusChecks.kt
java -cp "$work:$stdlib" com.titankeys.keyboard.FeedFocusChecksKt
