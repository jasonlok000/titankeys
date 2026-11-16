#!/usr/bin/env bash
# capture.sh SECONDS [--keys]: records the touchpad's raw events (and with --keys, the keyboard's) for gestures.py's
# replay-getevent. Writes $TK_OUT/touch/capture-TIME.txt. With --keys it holds every key typed meanwhile, so it stays on this
# computer: never in git, an issue or a report.
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"; . "$H/../phone/safety.sh"; . "$H/touchlib.sh"
seconds="${1:?seconds}"; mkdir -p "$TK_TOUCH_DIR"
tk_no_navigation || exit 1
devices=$(tk_adb shell getevent -p | tr -d '\r' | awk '/^add device/{d=$NF} /name:/{gsub(/"/, "", $2); print d, $2}')
want="touchPad"; [ "${2:-}" = "--keys" ] && want="touchPad|TitanKey"
out="$TK_TOUCH_DIR/capture-$(date +%Y%m%d-%H%M%S).txt"
{ grep -E " ($want)$" <<<"$devices" | sed 's/^/# device /'; } > "$out"
paths=$(grep -E " ($want)$" <<<"$devices" | cut -d' ' -f1 | tr '\n' ' ')
echo "Recording $seconds s from $paths"
# getevent reads one device, or every device with each line marked by its device. With --keys it reads every device, and only
# the touchpad's and the keyboard's lines are kept; with one device, replay takes them as the touchpad's.
adb_cmd=(adb); [ -n "$TK_ADB_SERIAL" ] && adb_cmd+=(-s "$TK_ADB_SERIAL")
if [ "${2:-}" = "--keys" ]; then
  timeout $((seconds + 10)) "${adb_cmd[@]}" shell "timeout $seconds getevent -lt" | tr -d '\r' \
    | grep -E "^($(tr ' ' '|' <<<"${paths% }")):" >> "$out"
else
  timeout $((seconds + 10)) "${adb_cmd[@]}" shell "timeout $seconds getevent -lt $paths" | tr -d '\r' >> "$out"
fi
echo "$out: $(grep -c BTN_TOUCH "$out") touch downs and ups"
