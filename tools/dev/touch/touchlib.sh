# The touch simulator's phone side, sourced by touch.sh, keymap.sh and capture.sh after ../env.sh and ../phone/safety.sh.
#
# The injected events are the phone's own hardware: if another app is in front, the keys type there. So before every gesture:
# - TK_APP must be a Debug build (it ends in .debug), and the release app is never a target.
# - The screen is awake and not locked: a locked screen would take the keys as its PIN.
# - No navigation app is running in the foreground.
# - TK_APP's window is in front, and the keyboard is connected to one of TK_APP's own text fields.
# The owner may be using the phone meanwhile ("ignore my touches. feel free to interrupt me. continue testing."): when another
# app is in front or no Debug field is focused, TK_TOUCH_ACTIVITY is brought back with am start, and everything is checked
# again before anything is sent. A screen that is off or locked, or a navigation app, is never worked around: after a second
# try a second apart, the run stops. So does a field that two am starts didn't bring back.
#
# Every tool also holds the phone lock (tools/phone-lock.sh, through ../phone/safety.sh) for its whole run, so no other phone
# run overlaps it. Builds use their own lock, never this one.
TK_APP="${TK_APP:-com.titankeys.debug}"
# TitanKeys Debug's Space gestures screen: its field holds only sample text, and it opens with the field focused.
TK_TOUCH_ACTIVITY="${TK_TOUCH_ACTIVITY:-com.titankeys.keyboard.SpaceGestureActivity}"
TK_TOUCH_DIR="$TK_OUT/touch"
TK_PHONE_INJECT=/data/local/tmp/tk-inject
TK_PHONE_SCRIPT=/data/local/tmp/tk-touch.tki

# Apps whose screen is never taken over, nor anything sent while they are in front: banking, payment and other finance apps,
# and maps. Matched on the front window's package name; TK_HANDS_OFF adds a regex of your own (a bank whose name says nothing).
TK_HANDS_OFF_DEFAULT='bank|banking|finance|financial|wallet|pay|money|credit|invest|trading|trade|broker|securities|crypto|coin|cash|venmo|revolut|monzo|klarna|wise|chase|hsbc|citi|wellsfargo|schwab|fidelity|etrade|robinhood|nubank|maps|waze'
tk_hands_off() {
  local package
  package=$(grep -oE "u0 [^/ ]+/" <<<"$1" | head -1 | sed 's/^u0 //; s#/$##')
  [ -n "$package" ] && grep -qiE "$TK_HANDS_OFF_DEFAULT${TK_HANDS_OFF:+|$TK_HANDS_OFF}" <<<"$package"
}

# Waits until the front window is TitanKeys Debug or the launcher, checking every 30 s for up to MINUTES (default 30). Sends
# nothing meanwhile. Fails if a finance or maps app (or anything else) is still in front at the end.
tk_wait_safe() {
  local minutes="${1:-30}" home front tries
  home=$(tk_adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1 | cut -d/ -f1)
  tries=$((minutes * 2))
  for ((i = 0; i <= tries; i++)); do
    front=$(tk_adb shell dumpsys window | tr -d '\r' | grep -m1 mCurrentFocus)
    if grep -qE "u0 ($TK_APP|${home:-launcher-unknown})/" <<<"$front" && ! tk_hands_off "$front"; then
      echo "front is safe: ${front## }"; return 0
    fi
    [ $i = 0 ] && echo "waiting: front is ${front## } (checking every 30 s for up to $minutes min, sending nothing)"
    [ $i -lt $tries ] && sleep 30
  done
  echo "STOP: after $minutes min the front is still ${front## }"
  return 1
}

tk_touch_check_once() {
  case "$TK_APP" in *.debug) ;; *) echo "STOP: TK_APP is $TK_APP; only a Debug build may be the target"; return 2 ;; esac
  local power window ime front
  power=$(tk_adb shell dumpsys power | tr -d '\r')
  grep -q "mWakefulness=Awake" <<<"$power" || { echo "not ready: the screen is off"; return 1; }
  window=$(tk_adb shell dumpsys window | tr -d '\r')
  if grep -qE "mShowingLockscreen=true|mDreamingLockscreen=true|isKeyguardShowing=true|mKeyguardShowing=true" <<<"$window"; then
    echo "not ready: the phone is locked"; return 1
  fi
  tk_no_navigation || return 1
  front=$(grep -m1 mCurrentFocus <<<"$window")
  if tk_hands_off "$front"; then echo "a finance or maps app is in front: ${front## }"; return 4; fi
  grep -q "{[^ ]* [^ ]* $TK_APP/" <<<"$front" || { echo "not ready: front is ${front## }"; return 3; }
  ime=$(tk_adb shell dumpsys input_method | tr -d '\r' | grep -A12 "mCurrentEditorInfo")
  grep -qE "packageName=$TK_APP( |$)" <<<"$ime" || { echo "not ready: no $TK_APP field is focused"; return 3; }
  if grep -q "inputType=0x0 " <<<"$ime"; then echo "not ready: the focused editor isn't a text field"; return 3; fi
  return 0
}

# Taps the middle of TK_APP's own text field, only while TK_APP's window is in front and the field doesn't have the keyboard.
tk_focus_field() {
  local xml node x0 y0 x1 y1
  grep -q " $TK_APP/" <<<"$(tk_front)" || return 1
  tk_adb shell uiautomator dump /data/local/tmp/tk-ui.xml >/dev/null 2>&1
  xml=$(tk_adb shell cat /data/local/tmp/tk-ui.xml | tr -d '\r')
  tk_adb shell rm -f /data/local/tmp/tk-ui.xml
  node=$(grep -oE "<node [^>]*class=\"android.widget.EditText\"[^>]*package=\"$TK_APP\"[^>]*>" <<<"$xml" | head -1)
  [ -n "$node" ] || return 1
  grep -q 'focused="true"' <<<"$node" && return 0
  read -r x0 y0 x1 y1 < <(grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' <<<"$node" | grep -oE '[0-9]+' | tr '\n' ' ')
  grep -q " $TK_APP/" <<<"$(tk_front)" || return 1
  tk_adb shell input tap $(((x0 + x1) / 2)) $(((y0 + y1) / 2))
  sleep 1
}

# Fails, with a STOP line, unless the checks pass. Another app in front, or no Debug field: TitanKeys Debug's screen is brought
# back (twice at most) and checked again. Screen off, locked or navigation: one more try a second later.
tk_touch_check() {
  local why status starts=0 waits=0
  while true; do
    why=$(tk_touch_check_once) && return 0
    status=$?
    # Whatever is wrong, release TitanKeys is the keyboard again first: the owner may be in another app.
    tk_ime_release >/dev/null
    [ $status = 2 ] && { echo "$why"; return 1; }
    # Never take the screen over from a finance or maps app (the Master's rule, 2026-11-16): stop, and send nothing.
    [ $status = 4 ] && { echo "STOP: $why"; return 1; }
    if [ $status = 3 ] && [ $starts -lt 2 ]; then
      starts=$((starts + 1))
      echo "$why: bringing $TK_APP back"
      tk_adb shell am start -n "$TK_APP/$TK_TOUCH_ACTIVITY" >/dev/null 2>&1
      sleep 2
      # Its screen in front, but no field of its own has the keyboard (as after Settings or a list): a tap on that field.
      tk_focus_field
    elif [ $status = 1 ] && [ $waits -lt 1 ]; then
      waits=$((waits + 1))
      sleep 1
    else
      echo "STOP: $why"
      return 1
    fi
  done
}

# The keyboard (the Master's rule, 2026-11-16): TitanKeys Debug is the keyboard only while a check types in Debug's own field,
# and release TitanKeys goes back right after, read back each time. If the front stops being Debug while Debug is the keyboard,
# release goes back at once, before anything else (tk_touch_check does it). A runner that keeps Debug on for a whole check, like
# checks.py, sets TK_IME_HELD=1, and touch.sh and guarded.sh then leave the keyboard to it.
TK_RELEASE_IME="${TK_RELEASE_IME:-com.titankeys/com.atlas.keyboard.AtlasIme}"
TK_DEBUG_IME="${TK_DEBUG_IME:-$TK_APP/com.titankeys.keyboard.TitanKeysIme}"
tk_ime_now() { tk_adb shell settings get secure default_input_method | tr -d '\r'; }
tk_ime_release() {
  local now
  now=$(tk_ime_now)
  [ "$now" = "$TK_RELEASE_IME" ] && { echo "keyboard: release TitanKeys"; return 0; }
  tk_adb shell ime set "$TK_RELEASE_IME" >/dev/null 2>&1
  now=$(tk_ime_now)
  if [ "$now" = "$TK_RELEASE_IME" ]; then echo "keyboard: release TitanKeys"; else echo "WARNING: the keyboard is $now, not release TitanKeys"; return 1; fi
}
# Debug as the keyboard, read back. Only call it after the gate has passed.
tk_ime_debug() {
  local now
  tk_adb shell ime enable "$TK_DEBUG_IME" >/dev/null 2>&1
  tk_adb shell ime set "$TK_DEBUG_IME" >/dev/null 2>&1
  now=$(tk_ime_now)
  [ "$now" = "$TK_DEBUG_IME" ] || { echo "the keyboard is $now, not $TK_DEBUG_IME"; tk_ime_release; return 1; }
  # The field takes a moment to connect to the new keyboard.
  sleep 1
}

# What every tool does on its way out: release TitanKeys back as the keyboard unless a runner holds Debug on. The phone lock goes
# by itself when the tool ends.
tk_touch_exit() {
  # TK_NO_PHONE=1: the offline tests, which never reach a phone.
  [ "${TK_IME_HELD:-}" = 1 ] || [ "${TK_NO_PHONE:-}" = 1 ] || tk_ime_release >/dev/null
}
trap tk_touch_exit EXIT

# The phone lock is already held: ../phone/safety.sh took it (tools/phone-lock.sh). Kept as a name the tools call.
tk_touch_lock() { tk_phone_lock; }

# Puts the injector on the phone, unless the same one is there. Builds it first if needed.
tk_touch_install() {
  local local_bin="$TK_TOUCH_DIR/tk-inject" here there
  [ -x "$local_bin" ] && [ "$local_bin" -nt "$TK_DEV/touch/inject.c" ] || bash "$TK_DEV/touch/build.sh" >/dev/null || return 1
  here=$(md5sum "$local_bin" | cut -d' ' -f1)
  there=$(tk_adb shell "md5sum $TK_PHONE_INJECT 2>/dev/null" | tr -d '\r' | cut -d' ' -f1)
  if [ "$here" != "$there" ]; then
    tk_adb push -q "$local_bin" "$TK_PHONE_INJECT" >/dev/null && tk_adb shell chmod 755 "$TK_PHONE_INJECT" || return 1
  fi
}

# The gate, then Debug as the keyboard (unless a runner holds it on), then the gate again with it. On the way out, release.
tk_touch_ready() {
  tk_touch_check || return 1
  [ "${TK_IME_HELD:-}" = 1 ] && return 0
  tk_ime_debug || return 1
  tk_touch_check
}

# tk_touch_play SCRIPT [DELAY_MS SHOT_NAME]: checks, then plays SCRIPT. With DELAY_MS and SHOT_NAME, it also takes a screenshot
# (DELAY_MS counts from the script's start, after the injector's 800 ms for its uinput twins to be added: TK_TOUCH_SETTLE_MS)
# DELAY_MS after the start, while the gesture is still on screen (see tk_touch_shot).
tk_touch_play() {
  tk_touch_ready || return 1
  [ -f "$TK_TOUCH_DIR/serving" ] && { tk_serve_play "$@"; return; }
  tk_adb push -q "$1" "$TK_PHONE_SCRIPT" >/dev/null || return 3
  if [ -n "${3:-}" ]; then
    local raw="$TK_TOUCH_DIR/shots/raw-$3.png"
    mkdir -p "$TK_TOUCH_DIR/shots"
    tk_adb shell "$TK_PHONE_INJECT $TK_PHONE_SCRIPT > /data/local/tmp/tk-touch.log 2>&1 & sleep $(awk "BEGIN{print ($2 + ${TK_TOUCH_SETTLE_MS:-800})/1000}"); screencap -p /data/local/tmp/tk-touch.png; wait; cat /data/local/tmp/tk-touch.log"
    local status=$?
    tk_adb pull -q /data/local/tmp/tk-touch.png "$raw" >/dev/null
    tk_adb shell rm -f /data/local/tmp/tk-touch.png /data/local/tmp/tk-touch.log "$TK_PHONE_SCRIPT"
    tk_touch_shot "$raw"
    return $status
  fi
  tk_adb shell "$TK_PHONE_INJECT $TK_PHONE_SCRIPT; s=\$?; rm -f $TK_PHONE_SCRIPT; exit \$s"
}

# tk_touch_feed KEYS_SCRIPT FEED LAST_MS: the gate, then the touches through TitanKeys Debug's touch feed and the keys through the
# injector, started together. The feed waits as long as the injector's keyboard twin takes to come (TK_TOUCH_SETTLE_MS) when there
# are keys, so the two stay in step. TK_TOUCH_SHOT=NAME (TK_TOUCH_SHOT_AT=MS) takes a screenshot meanwhile.
tk_touch_feed() {
  local keys_script="$1" events delay=0 keys sleep_s shot_cmd=""
  events=$(cat "$2")
  tk_touch_ready || return 1
  keys=$(grep -c '^[0-9]' "$keys_script")
  [ "$keys" -gt 0 ] && delay="${TK_TOUCH_SETTLE_MS:-800}"
  sleep_s=$(awk "BEGIN{print ($3 + $delay)/1000 + .4}")
  if [ -n "${TK_TOUCH_SHOT:-}" ]; then
    shot_cmd="sleep $(awk "BEGIN{print (${TK_TOUCH_SHOT_AT:-200} + $delay)/1000}"); screencap -p /data/local/tmp/tk-touch.png;"
  fi
  [ "$keys" -gt 0 ] && { tk_adb push -q "$keys_script" "$TK_PHONE_SCRIPT" >/dev/null || return 3; }
  tk_adb shell "$( [ "$keys" -gt 0 ] && echo "$TK_PHONE_INJECT $TK_PHONE_SCRIPT > /data/local/tmp/tk-touch.log 2>&1 &" ) \
    am broadcast -n $TK_APP/com.titankeys.keyboard.TouchFeed --es events '$events' --el delay $delay | grep -o 'data=.*'; \
    $shot_cmd sleep $sleep_s; wait; cat /data/local/tmp/tk-touch.log 2>/dev/null; rm -f /data/local/tmp/tk-touch.log $TK_PHONE_SCRIPT"
  if [ -n "${TK_TOUCH_SHOT:-}" ]; then
    mkdir -p "$TK_TOUCH_DIR/shots"
    tk_adb pull -q /data/local/tmp/tk-touch.png "$TK_TOUCH_DIR/shots/raw-$TK_TOUCH_SHOT.png" >/dev/null
    tk_adb shell rm -f /data/local/tmp/tk-touch.png
    tk_touch_shot "$TK_TOUCH_DIR/shots/raw-$TK_TOUCH_SHOT.png"
  fi
}

# tk_touch_play through serve.sh's injector, whose twins already exist (so no settle time). Waits for its "played" line.
tk_serve_play() {
  local in=/data/local/tmp/tk-serve.in log=/data/local/tmp/tk-serve.log path="/data/local/tmp/tk-serve-$$.tki"
  local last seconds before
  last=$(tail -1 "$1" | cut -d' ' -f1)
  seconds=$(awk "BEGIN{print int($last/1000) + 5}")
  tk_adb push -q "$1" "$path" >/dev/null || return 3
  before=$(tk_adb shell "grep -c '^played' $log" | tr -d '\r')
  if [ -n "${3:-}" ]; then
    mkdir -p "$TK_TOUCH_DIR/shots"
    tk_adb shell "echo 'play $path' >> $in; sleep $(awk "BEGIN{print $2/1000}"); screencap -p /data/local/tmp/tk-touch.png"
    tk_adb pull -q /data/local/tmp/tk-touch.png "$TK_TOUCH_DIR/shots/raw-$3.png" >/dev/null
    tk_adb shell rm -f /data/local/tmp/tk-touch.png
    tk_touch_shot "$TK_TOUCH_DIR/shots/raw-$3.png"
  else
    tk_adb shell "echo 'play $path' >> $in"
  fi
  for ((i = 0; i < seconds * 10; i++)); do
    [ "$(tk_adb shell "grep -c '^played' $log" | tr -d '\r')" -gt "$before" ] && break
    sleep .1
  done
  tk_adb shell "grep '^played\|^error\|failed\|stopped' $log | tail -1; rm -f $path" | tr -d '\r'
}

# The screenshot without the status bar, which can show notifications. The keyboard stays: it is what these checks look at,
# and with TitanKeys Debug's own field in front it shows only the test's text.
tk_touch_shot() {
  local sb
  sb=$(tk_adb shell dumpsys window | grep -m1 -oE "statusBars.*frame=\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]" | grep -oE "\]\[[0-9]+,[0-9]+\]" | grep -oE ",[0-9]+" | tr -d ,)
  "${TK_PIL_PYTHON:-python3}" "$TK_DEV/phone/crop.py" "$1" "${sb:-40}" 0 mInputShown=false >/dev/null \
    || { rm -f "$1"; echo "Crop failed, raw screenshot deleted. Install Pillow: sudo apt install python3-pil"; return 1; }
  echo "shot: ${1/raw-/}"
}
