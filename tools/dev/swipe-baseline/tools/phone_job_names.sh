#!/usr/bin/env bash
# Phone job: does a long list of names on this phone steal right words? CPU only through app_process: no input, nothing
# installed. 256 first names (the most SwipeTyping sends, PERSONAL_LIMIT) that the base list lacks, at frequency 104 with no boost, as names on this phone get them.
# Strokes: the same 555 as the saved-word job (5 name swipes, 550 paired-test). Compared with that job's "nothing saved" run.
# Usage: phone_job_names.sh [SOURCE_TREE]   (default: this repo)
set -u
. "$(dirname "$0")/../../env.sh"
B=$(cd "$(dirname "$0")/.." && pwd)
TREE=${1:-$TK_REPO}
V=$TK_PRIVATE/v12
python3 "$B/tools/names_list.py" "$TREE/android/assets/autocorrect/en_names.txt" "$TREE/android/assets/swipe/english.combined" 256 "$V/names-256.tsv"
echo "== 256 names, boost 0 $(date +%T)"
bash "$B/tools/saved_phone.sh" "$TREE" "$V/saved-corpus.jsonl" "$V/names-256.jsonl" "$V/names-256.tsv" 0 2>&1 | tail -4
echo "== score $(date +%T)"
python3 "$B/tools/score_saved.py" "$V/saved-corpus.jsonl" "$V/saved-none.jsonl" "$V/names-256.jsonl" \
  "$V/names-256.tsv" "$B/report/names-256.json"
python3 "$B/tools/saved_detail.py" "$V/saved-corpus.jsonl" "$V/saved-none.jsonl" "$V/names-256.jsonl" \
  "$V/names-256.tsv" | grep '^boost'
