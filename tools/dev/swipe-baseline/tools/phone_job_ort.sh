#!/usr/bin/env bash
# Phone job (#36 for engine 2.1): the same 550 new strokes through engine 2.0 on ONNX Runtime 1.23.2 (the engine 2.1
# tree), to compare word for word with the 1.20.0 run. CPU only through app_process: no input, nothing installed,
# the keyboard is not touched. Holds the phone lock, and pushes the tree's own runtime libraries.
# Usage: phone_job_ort.sh [SOURCE_TREE]   (default: this repo)
set -u
. "$(dirname "$0")/../../env.sh"
B=$(cd "$(dirname "$0")/.." && pwd)
TREE=${1:-$TK_REPO}
echo "== engine 2.0 on ONNX Runtime 1.23.2 $(date +%T)"
bash "$B/tools/engine2_phone.sh" "$TREE" "$TK_PRIVATE/v10/new-corpus.jsonl" "$TK_PRIVATE/v10/engine2-phone-new-ort123.jsonl" 2>&1 | tail -12
echo "== done $(date +%T)"
