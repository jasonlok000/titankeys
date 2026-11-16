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
lock="$TK_BUILD/tk-tools/.lock"
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
