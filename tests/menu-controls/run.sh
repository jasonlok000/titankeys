#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
python3 tests/menu-controls/test_checks.py
python3 tests/menu-controls/touch.py
python3 tests/menu-controls/actions.py
