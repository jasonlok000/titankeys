#!/usr/bin/env bash
# ime.sh debug|release: TitanKeys Debug as the keyboard (after the gate), or release TitanKeys back, read back either way.
# checks.py calls it around each check. release never needs the gate: it's always safe.
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"; . "$H/../phone/safety.sh"; . "$H/touchlib.sh"
tk_touch_lock
case "${1:-}" in
  debug) export TK_IME_HELD=1; tk_touch_check && tk_ime_debug && tk_touch_check || { TK_IME_HELD=; exit 1; } ;;
  release) tk_ime_release ;;
  *) echo "usage: ime.sh debug|release"; exit 2 ;;
esac
