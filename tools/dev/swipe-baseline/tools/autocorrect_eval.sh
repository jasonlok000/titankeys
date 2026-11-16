#!/usr/bin/env bash
# Tunes the next-word model's weight in typed autocorrect on the PC. Usage: autocorrect_eval.sh SOURCE_TREE CASES
set -euo pipefail
. "$(dirname "$0")/../../env.sh"
TREE=$1; CASES=$2
BASE=$(cd "$(dirname "$0")/.." && pwd)
kotlin=$TK_BUILD/kotlin-2.1.20/kotlinc
stdlib=$kotlin/lib/kotlin-stdlib.jar
android=$ANDROID_HOME/platforms/android-36/android.jar
ort=$TREE/android/libs/onnxruntime-android-1.20.0.jar
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cd "$TREE"
root=android/kotlin
engine=$root/com/titankeys/engine
keyboard=$root/com/titankeys/keyboard
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$android:$ort" -d "$work" \
  "$root/com/titankeys/autocorrect" "$keyboard/Layout.kt" tests/autocorrect/PhoneWords.java "$BASE/tools/AutocorrectEval.kt" \
  $(find "$engine" "$root/org" -name '*.java') 2>&1 | grep -v '^warning' | head -20 || true
javac --release 17 -nowarn -cp "$work:$stdlib:$android:$ort" -d "$work" tests/autocorrect/PhoneWords.java $(find "$engine" "$root/org" -name '*.java')
java -cp "$work:$stdlib:$android:$ort" com.titankeys.autocorrect.AutocorrectEvalKt "$CASES"
