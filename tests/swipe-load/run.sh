#!/usr/bin/env bash
# Checks that a failed swipe engine load is tried again and said on the bar (bug hunt 2.1, #22), which doesn't need Android.
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/atlas/keyboard
# The keyboard retries on a new text box and shows the notice for a swipe it couldn't take; the engine has the retry.
grep -qF "SwipeLoad.retryNow(" "$keyboard/AtlasIme.kt" || { echo "AtlasIme.kt never retries a failed swipe engine load"; exit 1; }
grep -qF "SwipeLoad.notice(" "$keyboard/AtlasIme.kt" || { echo "AtlasIme.kt never says a swipe engine failed on the bar"; exit 1; }
grep -qF "public void retryLoading(" android/kotlin/com/atlas/engine/SwipeTyping.java || { echo "SwipeTyping.java lost retryLoading"; exit 1; }
grep -qF "loadingRetries >= SWIPE_LOAD_TRIES" android/kotlin/com/atlas/engine/SwipeTyping.java || { echo "SwipeTyping.java's retry bound isn't SwipeLoad's"; exit 1; }
grep -qF "Retry loading in settings" android/kotlin/com/atlas/engine/SwipeTyping.java && { echo "SwipeTyping.java still promises a Retry in Settings"; exit 1; }
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$work" "$keyboard/SwipeLoad.kt" tests/swipe-load/SwipeLoadChecks.kt
java -cp "$work:$stdlib" com.atlas.keyboard.SwipeLoadChecksKt
