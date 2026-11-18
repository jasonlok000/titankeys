#!/usr/bin/env bash
# Check the zh-back probe with mocked input and focus state. No phone is required.
set -euo pipefail
cd "$(dirname "$0")/../.."
python3 -B -m unittest discover -s tools/dev/touch/test -p test_zh_back_batch.py
