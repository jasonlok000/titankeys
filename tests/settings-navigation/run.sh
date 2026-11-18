#!/usr/bin/env bash
# Check Settings navigation with mocked foreground and Android launch results.
set -euo pipefail
cd "$(dirname "$0")/../.."
python3 -B tests/settings-navigation/SettingsNavigationChecks.py
