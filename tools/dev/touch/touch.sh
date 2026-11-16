#!/usr/bin/env bash
# touch.sh GESTURE...: plays gestures on the phone's own touchpad and keys, after the safety checks in touchlib.sh.
# GESTUREs are gestures.py's (swipe:go +120 swipe:on, flick, space:2, key:sym:600, ...). One call is checked once, so it
# plays at most 15 s; split longer runs into several calls. Exits 1 on a STOP, which ends a run.
# The touches play through TitanKeys Debug's touch feed (android/debug/recorder/TouchFeed.kt), the keys through the injector;
# see README, "touch".
# Example: bash tools/dev/touch/touch.sh swipe:hello
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"; . "$H/../phone/safety.sh"; . "$H/touchlib.sh"
tk_touch_lock
mkdir -p "$TK_TOUCH_DIR"
keymap=(); [ -f "$TK_TOUCH_DIR/keys.txt" ] && keymap=(--keymap "$TK_TOUCH_DIR/keys.txt")
# Touches always go through the touch feed: a twin of the touchpad would upset the real one (inject.c, open_twin). Only
# TK_TOUCH_FEED=0 asks for the old way, which the injector now refuses where it would make that twin.
feed=(); [ "${TK_TOUCH_FEED:-1}" = 1 ] && feed=(--feed-out "$TK_TOUCH_DIR/last.feed")
python3 "$H/gestures.py" "${keymap[@]}" "${feed[@]}" --out "$TK_TOUCH_DIR/last.tki" "$@" || exit 2
last=$(tail -1 "$TK_TOUCH_DIR/last.tki" | cut -d' ' -f1)
case "$last" in ''|dev|\#*) last=0 ;; esac
if [ -n "${feed[*]}" ]; then
  feed_last=$(tr ';' '\n' < "$TK_TOUCH_DIR/last.feed" | tail -1 | cut -d' ' -f2)
  awk "BEGIN{exit !(${feed_last:-0} > $last)}" && last="$feed_last"
fi
awk "BEGIN{exit !($last <= 15000)}" || { echo "One call plays at most 15 s, and this is ${last} ms: split it."; exit 2; }
tk_touch_install || { echo "Could not put the injector on the phone"; exit 3; }
if [ -n "${feed[*]}" ] && [ -s "$TK_TOUCH_DIR/last.feed" ]; then
  tk_touch_feed "$TK_TOUCH_DIR/last.tki" "$TK_TOUCH_DIR/last.feed" "$last"
# TK_TOUCH_SHOT=NAME (with TK_TOUCH_SHOT_AT=MS, default 200) also takes a screenshot while the gesture plays.
elif [ -n "${TK_TOUCH_SHOT:-}" ]; then
  tk_touch_play "$TK_TOUCH_DIR/last.tki" "${TK_TOUCH_SHOT_AT:-200}" "$TK_TOUCH_SHOT"
else
  tk_touch_play "$TK_TOUCH_DIR/last.tki"
fi
