#!/usr/bin/env bash
# #31 FUTO parameter sweep on the phone, CPU only through app_process: no input, nothing installed, the keyboard is not
# touched. Holds the shared phone lock. Compiles a copy of SwipeRecognizer whose swept constants are not final; the tree
# itself is not changed. One run reads every labelled first try under each setting below.
# Usage: sweep_phone.sh TREE CORPUS OUT_PREFIX
set -euo pipefail
. "$(dirname "$0")/../../env.sh"
TREE=$1; CORPUS=$2; OUT=$3
BASE=$(cd "$(dirname "$0")/.." && pwd)
sdk=$ANDROID_HOME; kotlin=$TK_BUILD/kotlin-2.1.20/kotlinc; android=$sdk/platforms/android-36/android.jar; stdlib=$kotlin/lib/kotlin-stdlib.jar
PATH=$sdk/platform-tools:$PATH
ort=$(ls "$TREE"/android/libs/onnxruntime-android-*.jar | head -1)
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
mkdir "$work/classes" "$work/src" "$work/engine"
cp "$TREE/android/kotlin/com/titankeys/keyboard/Layout.kt" "$work/src/"
"$kotlin/bin/kotlinc" -language-version 2.2 -no-stdlib -no-reflect -classpath "$stdlib" -d "$work/classes" "$work/src/Layout.kt" 2>/dev/null
cp "$TREE"/android/kotlin/com/titankeys/engine/*.java "$work/engine/"
sed -i -E 's/private static final (int|float) (CANDIDATES|SAVED_WORD_CANDIDATES|SWIPE_STRETCH|SHIFT_RIGHT|FIRST_WORD_CONTEXT) =/static \1 \2 =/' "$work/engine/SwipeRecognizer.java"
grep -c "^    static \(int\|float\) " "$work/engine/SwipeRecognizer.java"
javac -nowarn --release 17 -cp "$work/classes:$android:$stdlib:$ort" -d "$work/classes" \
  "$work"/engine/*.java "$TREE"/android/kotlin/org/futo/ml/inference/*.java "$BASE/tools/SweepRunner.java" 2>&1 | grep -v '^Note' || true
jar cf "$work/classes.jar" -C "$work/classes" .
"$sdk/build-tools/36.0.0/d8" --min-api 34 --lib "$android" --output "$work/s.zip" "$work/classes.jar" "$stdlib" 2>&1 | grep -iv 'warning' | head -5 || true
python3 -I "$BASE/tools/make_readings.py" "$CORPUS" "$work/readings.jsonl" plain
device=/data/local/tmp/tk-baseline
TK_PHONE_LOCK_WAIT=3600 . "$TK_REPO/tools/phone-lock.sh"
tk_adb push -q "$work/s.zip" "$device/s.zip" >/dev/null
tk_adb push -q "$work/readings.jsonl" "$device/sweep-in.jsonl" >/dev/null
tk_adb shell "ls $device/models/en_triples.txt >/dev/null 2>&1" || { echo "models missing on the phone"; exit 4; }
SETS="base: cand64:CANDIDATES=64 cand96:CANDIDATES=96 shift04:SHIFT_RIGHT=.04 shift08:SHIFT_RIGHT=.08 stretch13:SWIPE_STRETCH=1.3 firstctx0:FIRST_WORD_CONTEXT=0"
tk_adb shell "cd $device && LD_LIBRARY_PATH=$device/lib CLASSPATH=$device/s.zip app_process -Djava.library.path=$device/lib $device com.titankeys.engine.SweepRunner $device/models $device/sweep-in.jsonl $device/sweep $SETS" 2>&1 | tr -d '\r' | grep -v '^WARNING' | tail -10 || true
for s in $SETS; do
  name=${s%%:*}
  tk_adb pull -q "$device/sweep-$name.jsonl" "$OUT-$name.jsonl" >/dev/null && echo "wrote $OUT-$name.jsonl"
done
tk_adb shell "rm -f $device/sweep-*.jsonl $device/s.zip"
