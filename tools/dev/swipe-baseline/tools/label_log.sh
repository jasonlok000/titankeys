#!/usr/bin/env bash
# Labels one raw log into its own folder: attempts.jsonl, corpus.jsonl and the build report.
# Usage: label_log.sh RAW_LOG OUT_DIR [TREE]
set -euo pipefail
. "$(dirname "$0")/../../env.sh"
LOG=$1; OUT=$2; TREE=${3:-$TK_REPO}
B=$(cd "$(dirname "$0")/.." && pwd)
mkdir -p "$OUT"
cd "$B/tools"
python3 -I build.py "$LOG" "$TREE/android/debug/reference/java/com/titankeys/reference/Passages.java" "$OUT" > "$OUT/build.out" 2>&1 || { tail -20 "$OUT/build.out"; exit 1; }
tail -2 "$OUT/build.out"
python3 -I export_corpus.py "$OUT" "$TREE/android/debug/recorder/samples/session-20261108-114501.jsonl" "$OUT/corpus.jsonl" 2>&1 | tail -3
