#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
python3 -B -m unittest discover -s tests/home-run-queue
bash tests/owner-wait/run.sh
