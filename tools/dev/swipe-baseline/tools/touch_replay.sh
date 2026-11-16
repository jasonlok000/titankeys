#!/usr/bin/env bash
# Replays the log's touches through the current Contact and SwipePieces. Classification only, on this computer.
# Usage: touch_replay.sh SOURCE_TREE RAW_LOG OUT_DIR [tap keys, - for none]
set -euo pipefail
. "$(dirname "$0")/../../env.sh"
TREE=$1; LOG=$2; OUT=$3; TAP=${4:--}
K="$TK_BUILD/kotlin-2.1.20/kotlinc"
AJ="$ANDROID_HOME/platforms/android-36/android.jar"
S=$TREE/android/kotlin/com/atlas
HERE=$(cd "$(dirname "$0")" && pwd)
REPLAY=$HERE/Replay.kt
mkdir -p "$OUT/cls"
python3 -I - "$LOG" "$OUT/raw.txt" <<'PY'
import json, sys
log, out = sys.argv[1:3]
with open(out, 'w') as w:
    for r in open(log, encoding='utf-8', errors='replace'):
        o = json.loads(r); k = o.get('k')
        if k == 'm':
            pts = o.get('s') or []
            w.write(f"M {o['a'] & 0xff} {o['t']} {len(pts)}")
            for x, y, t in pts:
                w.write(f" {'NaN' if x is None else x} {'NaN' if y is None else y} {t}")
            w.write('\n')
        elif k == 'key':
            w.write(f"K {o['c']} {1 if o['d'] else 0} {o['t']} {1 if o.get('g') else 0}\n")
        elif k in ('start', 'gap'):
            w.write('R\n')
        elif k == 'end':
            w.write('X\n')
        elif k == 'header':
            w.write(f"H {o.get('slipMillis', 90)}\n")
PY
"$K/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$K/lib/kotlin-stdlib.jar:$AJ" -d "$OUT/cls" \
  "$S/engine/Swipe.java" "$S/engine/Calibration.java" "$S/engine/TouchAlignment.java" \
  "$S/keyboard/Contact.kt" "$S/keyboard/SwipePieces.kt" "$S/keyboard/DeleteSwipe.kt" "$S/keyboard/Layout.kt" "$S/keyboard/Touchpad.kt" "$REPLAY" 2>&1 | grep -v "^warning" | head -20 || true
javac -nowarn -cp "$AJ" -d "$OUT/cls" "$S/engine/Swipe.java" "$S/engine/Calibration.java" "$S/engine/TouchAlignment.java" 2>&1 | grep -v "^Note" || true
java -cp "$OUT/cls:$K/lib/kotlin-stdlib.jar:$AJ" com.atlas.keyboard.ReplayKt "$OUT/raw.txt" "$OUT/result.txt" "$TAP" 2>&1 | tail -3
wc -l "$OUT/result.txt"
head -3 "$OUT/result.txt" | cut -c1-120
