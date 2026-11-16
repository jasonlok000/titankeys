#!/usr/bin/env bash
# keymap.sh: reads, without changing anything, which key codes the phone's keyboard ("TitanKey") sends for Sym, fn and the rest,
# from its key layout file, into $TK_OUT/touch/keys.txt for gestures.py. Also checks the touchpad's range against gestures.py.
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"; . "$H/../phone/safety.sh"; . "$H/touchlib.sh"
tk_touch_lock
mkdir -p "$TK_TOUCH_DIR"
input=$(tk_adb shell dumpsys input | tr -d '\r')
layout=$(awk '/^ *[0-9]+: TitanKey$/{f=1; next} f && /KeyLayoutFile:/{print $2; exit} f && /^ *[0-9]+: /{exit}' <<<"$input")
[ -n "$layout" ] || { echo "No key layout file for TitanKey in dumpsys input"; exit 1; }
echo "TitanKey's layout: $layout"
tk_adb shell cat "$layout" | tr -d '\r' | awk '
  BEGIN { split("SPACE=space ENTER=enter DEL=del SHIFT_LEFT=shift ALT_LEFT=alt SYM=sym FUNCTION=fn", pairs, " ")
          for (i in pairs) { split(pairs[i], kv, "="); names[kv[1]] = kv[2] } }
  $1 == "key" && $2 ~ /^[0-9]+$/ {
    name = $3
    if (name ~ /^[A-Z]$/) out = tolower(name); else if (name in names) out = names[name]; else next
    if (!(out in seen)) { seen[out] = 1; print out "=" $2 } }' > "$TK_TOUCH_DIR/keys.txt"
echo "$(wc -l < "$TK_TOUCH_DIR/keys.txt") keys in $TK_TOUCH_DIR/keys.txt"
for k in sym fn space; do grep -q "^$k=" "$TK_TOUCH_DIR/keys.txt" || echo "warning: no $k in the layout"; done
ranges=$(tk_adb shell getevent -pl | tr -d '\r' | awk '/^add device/{d=$NF} /name:/{n=$0} /ABS_MT_POSITION_[XY]/ && n ~ /"touchPad"/ {print}')
echo "$ranges" | sed 's/^ */touchPad: /'
grep -q "ABS_MT_POSITION_X.*max 1079" <<<"$ranges" && grep -q "ABS_MT_POSITION_Y.*max 599" <<<"$ranges" \
  || echo "warning: the touchpad's range isn't 0-1079 by 0-599, which gestures.py assumes"
