#!/usr/bin/env bash
# Replays the labelled corpus through the current engine on the phone. CPU only: it adds no input and does not
# restart the keyboard. It holds the shared phone lock so it stays in series with other phone work.
# Usage: replay.sh SOURCE_TREE CORPUS OUT_FILE [limit]
set -euo pipefail
. "$(dirname "$0")/../../env.sh"
TREE=$1; CORPUS=$2; OUT=$3; LIMIT=${4:-0}
BASE=$(cd "$(dirname "$0")/.." && pwd)
sdk=$ANDROID_HOME
kotlin=$TK_BUILD/kotlin-2.1.20/kotlinc
android=$sdk/platforms/android-36/android.jar
stdlib=$kotlin/lib/kotlin-stdlib.jar
PATH=$sdk/platform-tools:$PATH
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir "$work/classes" "$work/src"
cp "$TREE/android/kotlin/com/atlas/keyboard/Layout.kt" "$work/src/"
"$kotlin/bin/kotlinc" -language-version 2.2 -no-stdlib -no-reflect -classpath "$stdlib" -d "$work/classes" "$work/src/Layout.kt" 2>/dev/null
javac -nowarn --release 17 -cp "$work/classes:$android:$stdlib:$TK_ORT/onnxruntime-android-1.20.0-classes.jar" -d "$work/classes" \
  "$TREE"/android/kotlin/com/atlas/engine/*.java "$TREE"/android/kotlin/org/futo/ml/inference/*.java "$BASE/tools/BaselineRunner.java" 2>&1 | grep -v '^Note' || true
jar cf "$work/classes.jar" -C "$work/classes" .
"$sdk/build-tools/36.0.0/d8" --min-api 34 --lib "$android" --output "$work/checks.zip" "$work/classes.jar" "$stdlib" 2>&1 | grep -v 'warning\|Warning' | head -5 || true
device=/data/local/tmp/tk-baseline
mkdir -p "$TK_BUILD/tk-tools"; exec 9>"$TK_BUILD/tk-tools/.lock"
flock -w 900 9 || { echo "phone busy"; exit 3; }
tk_adb shell mkdir -p "$device/models"
tk_adb push -q "$work/checks.zip" "$device/" >/dev/null
tk_adb shell "ls $device/lib/libswipe_jni.so >/dev/null 2>&1" || tk_adb push -q "$TREE/android/jniLibs/arm64-v8a" "$device/lib" >/dev/null
if ! tk_adb shell "ls $device/models/english.combined >/dev/null 2>&1"; then
  for model in english.combined honorable_sturgeon magic_macaw hungry_jellyfish; do
    tk_adb push -q "$TREE/android/assets/swipe/$model" "$device/models/" >/dev/null
  done
  tk_adb push -q "$TREE/android/assets/autocorrect/en_triples.txt" "$device/models/en_triples.txt" >/dev/null
fi
input=$work/corpus.jsonl
if [ "$LIMIT" != 0 ]; then head -n "$LIMIT" "$CORPUS" > "$input"; else cp "$CORPUS" "$input"; fi
tk_adb push -q "$input" "$device/corpus.jsonl" >/dev/null
tk_adb shell "cd $device && LD_LIBRARY_PATH=$device/lib CLASSPATH=$device/checks.zip app_process -Djava.library.path=$device/lib $device com.atlas.engine.BaselineRunner $device/models $device/corpus.jsonl $device/out.jsonl" | tr -d '\r' | tail -3
tk_adb pull -q "$device/out.jsonl" "$OUT" >/dev/null
tk_adb shell rm -f "$device/corpus.jsonl" "$device/out.jsonl"
echo "wrote $OUT"
