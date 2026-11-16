#!/usr/bin/env bash
# The touch simulator's offline checks: no phone. Needs a JDK, a C compiler and python3.
set -euo pipefail
cd "$(dirname "$0")/../../../.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
javac -d "$work" android/kotlin/com/titankeys/engine/TouchAlignment.java tools/dev/touch/test/AlignmentDump.java
java -cp "$work" com.titankeys.engine.AlignmentDump android/assets/calibration/titan-2-saved.profile > "$work/alignment.txt"
cc -O2 -Wall -Wextra -Werror -o "$work/tk-inject" tools/dev/touch/inject.c
python3 tools/dev/touch/test/test_touch.py "$work/alignment.txt" "$work/tk-inject"

# The phone lock: a tool holds it for its whole run and gives it back at the end, and one it starts doesn't wait for it.
# On a lock file of its own, never the real one.
export TK_BUILD="$work/build"
export TK_NO_PHONE=1
mkdir -p "$TK_BUILD/tk-tools"
lock="$TK_BUILD/tk-tools/.phone.lock"
held() { mkdir -p "$TK_BUILD/tk-tools"; ! flock -n "$lock" true; }
bash -c '. tools/dev/env.sh; . tools/dev/phone/safety.sh; . tools/dev/touch/touchlib.sh; tk_touch_lock
  bash -c ". tools/dev/env.sh; . tools/dev/phone/safety.sh; . tools/dev/touch/touchlib.sh; tk_touch_lock; echo inner ran"
  touch "$TK_BUILD/held"; sleep 2' > "$work/lock.out" &
for _ in $(seq 50); do [ -e "$TK_BUILD/held" ] && break; sleep .1; done
held || { echo "FAIL the phone lock isn't held during a run"; exit 1; }
wait
grep -q "inner ran" "$work/lock.out" || { echo "FAIL a tool started inside a run waited for the lock"; exit 1; }
held && { echo "FAIL the phone lock is still held after the run"; exit 1; }
echo "Phone lock checks passed"

# A runner holding Debug on gets it back when a failed check on the way put release TitanKeys back (2026-11-16), and only then.
ready() {
  NOW=$1 TK_IME_HELD=1 bash -c '. tools/dev/env.sh; . tools/dev/phone/safety.sh; . tools/dev/touch/touchlib.sh
    tk_touch_check() { return 0; }; tk_ime_now() { echo "$NOW"; }; tk_ime_debug() { echo "debug picked"; }
    tk_touch_ready'
}
[ "$(ready com.titankeys/com.atlas.keyboard.AtlasIme)" = "debug picked" ] || { echo "FAIL a held Debug wasn't picked again after release came back"; exit 1; }
[ -z "$(ready com.titankeys.debug/com.titankeys.keyboard.TitanKeysIme)" ] || { echo "FAIL Debug was picked again while it was already the keyboard"; exit 1; }
echo "Held Debug checks passed"

# A batch: every named one loads, its gestures build, and its report says each result once, with what's left for hands.
TK_OUT="$work/out" python3 - <<'PY'
import os, sys
sys.path.insert(0, "tools/dev/touch")
import checks
sys.modules["checks"] = checks
for name, module_name in checks.BATCHES.items():
    module = __import__(module_name)
    assert module.BATCH, f"{name}: no checks"
    assert module.duration(*module.code("hold:p:800")) > 1400, f"{name}: the code and a held P take too little time"
checks.record("one", True, "fine", "one")
checks.record("two", None, "look", "two")
path = checks.report("test", "now", ["by hand"])
text = open(path, encoding="utf-8").read()
for want in ("1 YES, 0 NO, 1 to look at", "- **YES** one: fine (`shots/one.png`)", "- **LOOK** two: look", "- by hand"):
    assert want in text, f"the report lacks {want!r}:\n{text}"
print("Batch checks passed")
PY
