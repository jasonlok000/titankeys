#!/usr/bin/env bash
# Exercise the actual phone gate with a simulated clock and foreground app.
set -euo pipefail
cd "$(dirname "$0")/../.."
export TK_APP=com.titankeys.debug TK_NO_PHONE=1 TK_OUT="${TMPDIR:-/tmp}"
. tools/dev/touch/touchlib.sh
unset TK_CUTOFF_EPOCH TK_RUN_STATUS_FILE TK_OWNER_POLL_S
tk_front() { echo owner; }
polls=0
tk_owner_app() { [ "$polls" -lt 95 ]; }
sleep() {
  [ "$1" = 20 ] || exit 1
  polls=$((polls + 1))
}
tk_wait_for_owner
[ "$polls" = 95 ]

# An explicit overnight cutoff still ends the run while another app is open.
polls=0
export TK_CUTOFF_EPOCH=1
if tk_wait_for_owner; then exit 1; fi
[ "$polls" = 0 ]
echo "Owner wait checks passed"
