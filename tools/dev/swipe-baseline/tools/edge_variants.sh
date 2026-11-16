#!/usr/bin/env bash
# Replays the corpus through copies of the engine with extra readings aimed at the sensor edges. CPU only.
# Usage: edge_variants.sh [SOURCE_TREE]   (default: this repo)
set -u
. "$(dirname "$0")/../../env.sh"
B=$(cd "$(dirname "$0")/.." && pwd)
SRC=${1:-$TK_REPO}
for v in control shift edges both; do
  T=$TK_OUT/tk-exp/$v
  rm -rf "$T"; mkdir -p "$T/android"
  cp -r "$SRC/android/kotlin" "$T/android/"
  python3 -I - "$T/android/kotlin/com/atlas/engine/SwipeRecognizer.java" "$v" <<'PY'
import sys
path, variant = sys.argv[1:3]
text = open(path, encoding='utf-8').read()
old = 'Swipe[] readings = speed < MIN_STRETCH_SPEED ? new Swipe[] { gesture } : new Swipe[] { stretched(gesture, speed), gesture };'
assert old in text
extra = {'control': '', 'shift': 'shifted(gesture), ', 'edges': 'edges(gesture), ', 'both': 'shifted(gesture), edges(gesture), '}[variant]
new = ('Swipe[] readings = speed < MIN_STRETCH_SPEED ? new Swipe[] { ' + extra + 'gesture } : new Swipe[] { ' + extra + 'stretched(gesture, speed), gesture };')
helpers = '''
    private static Swipe shifted(Swipe gesture) {
        float[] x = gesture.x.clone();
        for (int i = 0; i < x.length; i++) x[i] = Math.min(1, x[i] + .06f);
        return new Swipe(x, gesture.y, gesture.time);
    }

    /** The sensor stops near the centre of the outer keys, so readings near its edges are spread out to the key centres and past them. */
    private static Swipe edges(Swipe gesture) {
        float[] x = gesture.x.clone();
        for (int i = 0; i < x.length; i++) {
            if (x[i] > .8f) x[i] = Math.min(1, .8f + (x[i] - .8f) * 1.342f);
            else if (x[i] < .2f) x[i] = Math.max(0, .2f - (.2f - x[i]) * 1.389f);
        }
        return new Swipe(x, gesture.y, gesture.time);
    }

    private org.futo.ml.inference.SwipeDecoder.Result[] read('''
text = text.replace(old, new)
marker = '    private org.futo.ml.inference.SwipeDecoder.Result[] read('
assert marker in text
text = text.replace(marker, helpers.lstrip('\n') if False else helpers[1:], 1)
open(path, 'w', encoding='utf-8').write(text)
PY
  echo "== $v $(date +%T)"
  bash "$B/tools/replay.sh" "$T" "$TK_PRIVATE/corpus.jsonl" "$TK_PRIVATE/variant-$v.jsonl" 2>&1 | tail -2 | cut -c1-120
done
python3 -I "$B/tools/edge_score.py" "$TK_PRIVATE"
