#!/usr/bin/env bash
# Checks the idle games on the suggestion bar, an Easter egg, which doesn't need Android.
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/atlas/keyboard
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$work" "$keyboard/IdleGames.kt" tests/idle/IdleGamesChecks.kt
java -cp "$work:$stdlib" com.atlas.keyboard.IdleGamesChecksKt
