#!/usr/bin/env bash
# Send feedback's rows open the report form (#97). The check itself is tools/checks/feedback.py, behind phone.py's guards.
# APP=com.titankeys.debug checks the debug build instead.
set -euo pipefail
exec python3 "$(dirname "$0")/feedback.py"
