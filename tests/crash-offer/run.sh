#!/usr/bin/env bash
# Draws the crash offer to PNGs with its real sizes and words, to look at without the phone, and checks in pixels that the card
# is most of the screen wide and fits on it. Usage: bash tests/crash-offer/run.sh [folder].
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
out=$(realpath -m "${1:-crash-offer-render}")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$work" tests/crash-offer/CrashOfferRender.kt
java -Djava.awt.headless=true -cp "$work:$stdlib" com.titankeys.keyboard.CrashOfferRenderKt "$out" \
  android/kotlin/com/titankeys/keyboard/CrashOffer.kt android/src/main/res/font
echo "Crash offer renders in $out"
