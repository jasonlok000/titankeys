#!/usr/bin/env bash
# Issue #83 joins study. Run in WSL. Everything it writes goes to $OUT, outside the repo, because the attempt table
# holds typed text. What it prints is numbers only.
# Usage: run.sh raw-log.jsonl [out-dir]
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../../.." && pwd)
log=${1:?raw log}
OUT=${2:-$HOME/build/joins83}
tools=${BASELINE_TOOLS:-/mnt/x/Atlas/.dev/swipe-baseline/tools}
mkdir -p "$OUT/private" "$OUT/classes"
if [ ! -s "$OUT/private/attempts.jsonl" ] || [ "$log" -nt "$OUT/private/attempts.jsonl" ]; then
  python3 -I "$tools/build.py" "$log" "$repo/android/debug/reference/java/com/titankeys/reference/Passages.java" "$OUT/private" > "$OUT/private/build.out"
fi
python3 -I "$here/pairs.py" "$OUT/private/attempts.jsonl" "$log" "$tools" "$OUT/private"
javac --release 17 -d "$OUT/classes" "$repo/android/kotlin/com/atlas/engine/Swipe.java" "$here/JoinReplay.java"
java -cp "$OUT/classes" JoinReplay 90 < "$OUT/private/pairs-points.txt" > "$OUT/private/live-rule.txt"
python3 -I "$here/rules.py" "$OUT/private/pairs.jsonl" "$OUT/private/live-rule.txt" "$OUT/report.json"
