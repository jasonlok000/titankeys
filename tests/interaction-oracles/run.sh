#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
python3 -B tests/interaction-oracles/checks.py
