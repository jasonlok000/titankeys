#!/usr/bin/env bash
# touch.sh GESTURE...: plays gestures on the phone's own touchpad and keys, after the safety checks in touchlib.sh.
# GESTUREs are gestures.py's (swipe:go +120 swipe:on, flick, space:2, key:sym:600, ...). One call is checked once, so it
# plays at most 15 s; split longer runs into several calls. Exits 1 on a STOP, which ends a run.
# Example: bash tools/dev/touch/touch.sh swipe:hello
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"; . "$H/../phone/safety.sh"; . "$H/touchlib.sh"
tk_touch_lock
mkdir -p "$TK_TOUCH_DIR"
keymap=(); [ -f "$TK_TOUCH_DIR/keys.txt" ] && keymap=(--keymap "$TK_TOUCH_DIR/keys.txt")
python3 "$H/gestures.py" "${keymap[@]}" --out "$TK_TOUCH_DIR/last.tki" "$@" || exit 2
last=$(tail -1 "$TK_TOUCH_DIR/last.tki" | cut -d' ' -f1)
awk "BEGIN{exit !($last <= 15000)}" || { echo "One call plays at most 15 s, and this is ${last} ms: split it."; exit 2; }
tk_touch_install || { echo "Could not put the injector on the phone"; exit 3; }
# TK_TOUCH_SHOT=NAME (with TK_TOUCH_SHOT_AT=MS, default 200) also takes a screenshot while the gesture plays.
if [ -n "${TK_TOUCH_SHOT:-}" ]; then
  tk_touch_play "$TK_TOUCH_DIR/last.tki" "${TK_TOUCH_SHOT_AT:-200}" "$TK_TOUCH_SHOT"
else
  tk_touch_play "$TK_TOUCH_DIR/last.tki"
fi
