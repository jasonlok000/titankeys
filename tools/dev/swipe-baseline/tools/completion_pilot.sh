#!/usr/bin/env bash
# Issue #39 pilot on the PC (WSL): completions after typed letters, several rankings. No phone.
# Usage: completion_pilot.sh TREE CORPUS OUT
set -euo pipefail
. "$(dirname "$0")/../../env.sh"
TREE=$1; CORPUS=$2; OUT=$3
B=$(cd "$(dirname "$0")/.." && pwd)
android=$ANDROID_HOME/platforms/android-36/android.jar
ort=$TK_BUILD/ort-desktop/onnxruntime-1.20.0.jar
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
kotlin=$TK_BUILD/kotlin-2.1.20/kotlinc
"$kotlin/bin/kotlinc" -language-version 2.2 -no-stdlib -no-reflect -classpath "$kotlin/lib/kotlin-stdlib.jar" -d "$work" "$TREE/android/kotlin/com/atlas/keyboard/Layout.kt" 2>/dev/null
javac -nowarn --release 17 -cp "$work:$android:$ort:$kotlin/lib/kotlin-stdlib.jar" -d "$work" "$TREE"/android/kotlin/com/atlas/engine/*.java \
  "$TREE"/android/kotlin/org/futo/ml/inference/*.java "$B/tools/CompletionPilot.java" 2>&1 | grep -v '^Note' || true
java -cp "$work:$ort:$android" com.atlas.engine.CompletionPilot "$TREE/android/assets/swipe2/predictor" \
  "$TREE/android/assets/swipe/hungry_jellyfish/vocab.txt" "$TREE/android/assets/swipe/english.combined" "$CORPUS" "$OUT" ${HISTORY:-}
