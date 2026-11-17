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
# TK_KEYFEED=1: the keys go through TitanKeys Debug's key feed (android/debug/recorder/KeyFeed.kt) into the keyboard itself,
# Shift included, instead of through the injector's twin keyboard, whose Shift takes the field's focus on the Titan 2 Elite.
keyfeed=(); rm -f "$TK_TOUCH_DIR/last.keys"
if [ "${TK_KEYFEED:-0}" = 1 ]; then
  [ -n "${feed[*]}" ] || { echo "TK_KEYFEED=1 needs the touch feed: drop TK_TOUCH_FEED=0"; exit 2; }
  keyfeed=(--keyfeed-out "$TK_TOUCH_DIR/last.keys")
fi
python3 "$H/gestures.py" "${keymap[@]}" "${feed[@]}" "${keyfeed[@]}" --out "$TK_TOUCH_DIR/last.tki" "$@" || exit 2
last=$(tail -1 "$TK_TOUCH_DIR/last.tki" | cut -d' ' -f1)
case "$last" in ''|dev|\#*) last=0 ;; esac
for out in last.feed last.keys; do
  [ -s "$TK_TOUCH_DIR/$out" ] || continue
  out_last=$(tr ';' '\n' < "$TK_TOUCH_DIR/$out" | tail -1 | cut -d' ' -f2)
  awk "BEGIN{exit !(${out_last:-0} > $last)}" && last="$out_last"
done
# TK_TOUCH_MAX_MS raises it for one call that can't be split: a held delete of 250 words (E's dominoes, 2026-11-17).
most="${TK_TOUCH_MAX_MS:-15000}"
awk "BEGIN{exit !($last <= $most)}" || { echo "One call plays at most $((most / 1000)) s, and this is ${last} ms: split it."; exit 2; }
tk_touch_install || { echo "Could not put the injector on the phone"; exit 3; }
if [ -n "${feed[*]}" ] && { [ -s "$TK_TOUCH_DIR/last.feed" ] || [ -s "$TK_TOUCH_DIR/last.keys" ]; }; then
  tk_touch_feed "$TK_TOUCH_DIR/last.tki" "$TK_TOUCH_DIR/last.feed" "$last" "$TK_TOUCH_DIR/last.keys"
# TK_TOUCH_SHOT=NAME (with TK_TOUCH_SHOT_AT=MS, default 200) also takes a screenshot while the gesture plays.
elif [ -n "${TK_TOUCH_SHOT:-}" ]; then
  tk_touch_play "$TK_TOUCH_DIR/last.tki" "${TK_TOUCH_SHOT_AT:-200}" "$TK_TOUCH_SHOT"
else
  tk_touch_play "$TK_TOUCH_DIR/last.tki"
fi
