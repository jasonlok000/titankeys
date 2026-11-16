#!/usr/bin/env bash
# Phone job for engine 2.1 (#31), part 2 again: the engine evidence of every labelled stroke in raw-log-10, for retraining
# the ranker. CPU only through app_process: no input, nothing installed, the keyboard is not touched. Holds the phone lock.
# The first try was killed by low memory: the runner kept every context's next-word table. That is now bounded to 6.
# Usage: phone_job_v10b.sh [SOURCE_TREE]   (default: this repo)
set -u
. "$(dirname "$0")/../../env.sh"
B=$(cd "$(dirname "$0")/.." && pwd)
TREE=${1:-$TK_REPO}
echo "== evidence for all labelled strokes $(date +%T)"
bash "$B/tools/evidence.sh" "$TREE" "$TK_PRIVATE/v10/corpus.jsonl" "$TK_PRIVATE/v10/evidence.jsonl" 2>&1 | tail -5
echo "== done $(date +%T)"
ls -la "$TK_PRIVATE/v10/evidence.jsonl"
