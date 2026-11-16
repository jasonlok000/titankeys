#!/usr/bin/env bash
# Records the engine evidence the ranker reads, from the given source tree, on the phone. CPU only: it adds no input and
# does not restart the keyboard. Holds the shared phone lock. Usage: evidence.sh SOURCE_TREE CORPUS OUT_FILE
set -euo pipefail
. "$(dirname "$0")/../../env.sh"
TREE=$1; CORPUS=$2; OUT=$3
BASE=$(cd "$(dirname "$0")/.." && pwd)
sdk=$ANDROID_HOME; kotlin=$TK_BUILD/kotlin-2.1.20/kotlinc; android=$sdk/platforms/android-36/android.jar; stdlib=$kotlin/lib/kotlin-stdlib.jar
PATH=$sdk/platform-tools:$PATH
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
mkdir "$work/classes" "$work/src"
cp "$TREE/android/kotlin/com/titankeys/keyboard/Layout.kt" "$work/src/"
tk_compile Kotlin "$kotlin/bin/kotlinc" -language-version 2.2 -no-stdlib -no-reflect -classpath "$stdlib" -d "$work/classes" "$work/src/Layout.kt"
# ONNX Runtime is only on the compile path: this runner never loads the classes that use it.
ort=$TK_ORT/onnxruntime-android-1.20.0-classes.jar
[ -f "$ort" ] || { echo "no ONNX Runtime jar at $ort: set TK_ORT (tools/dev/env.sh)" >&2; exit 1; }
tk_compile javac javac -nowarn --release 17 -cp "$work/classes:$android:$stdlib:$ort" -d "$work/classes" \
  "$TREE"/android/kotlin/com/titankeys/engine/*.java "$TREE"/android/kotlin/org/futo/ml/inference/*.java "$BASE/tools/EvidenceRunner.java"
jar cf "$work/classes.jar" -C "$work/classes" .
tk_compile d8 "$sdk/build-tools/36.0.0/d8" --min-api 34 --lib "$android" --output "$work/e.zip" "$work/classes.jar" "$stdlib"
python3 -I "$BASE/tools/make_readings.py" "$CORPUS" "$work/readings.jsonl" plain
device=/data/local/tmp/tk-baseline
TK_PHONE_LOCK_WAIT=1800 . "$TK_REPO/tools/phone-lock.sh"
tk_adb push -q "$work/e.zip" "$device/e.zip" >/dev/null
tk_adb push -q "$work/readings.jsonl" "$device/evidence-in.jsonl" >/dev/null
tk_adb shell "ls $device/models/en_triples.txt >/dev/null 2>&1" || { echo "models missing on the phone"; exit 4; }
tk_adb shell "cd $device && LD_LIBRARY_PATH=$device/lib CLASSPATH=$device/e.zip app_process -Djava.library.path=$device/lib $device com.titankeys.engine.EvidenceRunner $device/models $device/evidence-in.jsonl $device/evidence.jsonl" | tr -d '\r' | tail -3
tk_adb pull -q "$device/evidence.jsonl" "$OUT" >/dev/null
tk_adb shell rm -f "$device/evidence-in.jsonl" "$device/evidence.jsonl" "$device/e.zip"
echo "wrote $OUT"
