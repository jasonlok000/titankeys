#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$work" \
  android/kotlin/com/titankeys/support/SupportRules.kt android/kotlin/com/titankeys/support/SupportWebProtocol.kt   android/kotlin/com/titankeys/connected/Json.kt tests/support/SupportChecks.kt tests/support/SupportWebChecks.kt
java -cp "$work:$stdlib" com.titankeys.support.SupportChecksKt
