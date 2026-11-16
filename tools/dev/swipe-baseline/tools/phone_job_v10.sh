#!/usr/bin/env bash
# Phone job for engine 2.1 (#31). CPU only through app_process: no input, nothing installed, the keyboard is not touched.
# Both parts hold the shared phone lock and clean up /data/local/tmp afterwards (the runner's FUTO models stay cached).
# 1. Engine 2.0 and the FUTO order on the same 735 strokes recorded after engine 2.0's training data (paired test).
# 2. The engine evidence of every labelled stroke in raw-log-10, for retraining the ranker.
# Usage: phone_job_v10.sh [SOURCE_TREE]   (default: this repo)
set -u
. "$(dirname "$0")/../../env.sh"
B=$(cd "$(dirname "$0")/.." && pwd)
TREE=${1:-$TK_REPO}
V=$TK_PRIVATE/v10
echo "== 1. engine 2.0 on the new strokes $(date +%T)"
bash "$B/tools/engine2_phone.sh" "$TREE" "$V/new-corpus.jsonl" "$V/engine2-phone-new.jsonl" 2>&1 | tail -14
echo "== 2. evidence for all labelled strokes $(date +%T)"
bash "$B/tools/evidence.sh" "$TREE" "$V/corpus.jsonl" "$V/evidence.jsonl" 2>&1 | tail -5
echo "== done $(date +%T)"
ls -la "$V/engine2-phone-new.jsonl" "$V/evidence.jsonl"
