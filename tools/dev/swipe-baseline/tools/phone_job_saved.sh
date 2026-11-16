#!/usr/bin/env bash
# Phone job: saved words and a boost (owner's request, Nov 10). CPU only through app_process: no input, nothing installed.
# 1. Nothing saved, as today. 2. The saved stand-in (27 words kept twice outside the dictionary, plus the name), boosts 0 to 4.
# Strokes: the owner's 5 swipes of one name (name_corpus.py writes name-corpus.jsonl) and the 735 paired-test strokes
# recorded after engine 2.0's training data.
# Usage: phone_job_saved.sh [SOURCE_TREE]   (default: this repo)
set -u
. "$(dirname "$0")/../../env.sh"
B=$(cd "$(dirname "$0")/.." && pwd)
TREE=${1:-$TK_REPO}
V=$TK_PRIVATE/v12
cat "$V/name-corpus.jsonl" "$TK_PRIVATE/v10/new-corpus.jsonl" > "$V/saved-corpus.jsonl"
echo "== 1. nothing saved $(date +%T)"
bash "$B/tools/saved_phone.sh" "$TREE" "$V/saved-corpus.jsonl" "$V/saved-none.jsonl" "" 0 2>&1 | tail -4
echo "== 2. saved stand-in, boosts $(date +%T)"
bash "$B/tools/saved_phone.sh" "$TREE" "$V/saved-corpus.jsonl" "$V/saved-boosts.jsonl" "$V/saved-standin.tsv" 0,0.5,1,2,4 2>&1 | tail -4
echo "== done $(date +%T)"
