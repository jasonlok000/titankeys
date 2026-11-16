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
# Every tool also holds the shared phone lock, $TK_BUILD/tk-tools/.lock, for its whole run, so no other phone run overlaps it.
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

# What every tool does on its way out: release TitanKeys back as the keyboard unless a runner holds Debug on, then the lock.
tk_touch_exit() {
  # TK_NO_PHONE=1: the offline tests, which never reach a phone.
  [ "${TK_IME_HELD:-}" = 1 ] || [ "${TK_NO_PHONE:-}" = 1 ] || tk_ime_release >/dev/null
  [ "${TK_LOCK_MINE:-}" = 1 ] && { flock -u 9 2>/dev/null; exec 9>&-; }
}
trap tk_touch_exit EXIT

# Holds the shared phone lock until this process ends. A tool started by one that holds it (TK_PHONE_LOCKED=1) doesn't take it
# again, which would wait for itself.
tk_touch_lock() {
  [ "${TK_PHONE_LOCKED:-}" = 1 ] && return 0
  # The adb server is started first: one started while the lock is open would inherit it, and hold it after this run.
  command -v adb >/dev/null && adb start-server >/dev/null 2>&1
  mkdir -p "$TK_BUILD/tk-tools"
  exec 9>"$TK_BUILD/tk-tools/.lock"
  flock -n 9 || { echo "waiting for the phone lock (another phone run holds it)"; flock 9; }
  export TK_PHONE_LOCKED=1
  TK_LOCK_MINE=1
}

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

# The screenshot without the status bar, which can show notifications. The keyboard stays: it is what these checks look at,
# and with TitanKeys Debug's own field in front it shows only the test's text.
tk_touch_shot() {
  local sb
  sb=$(tk_adb shell dumpsys window | grep -m1 -oE "statusBars.*frame=\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]" | grep -oE "\]\[[0-9]+,[0-9]+\]" | grep -oE ",[0-9]+" | tr -d ,)
  "${TK_PIL_PYTHON:-python3}" "$TK_DEV/phone/crop.py" "$1" "${sb:-40}" 0 mInputShown=false >/dev/null \
    || { rm -f "$1"; echo "Crop failed, raw screenshot deleted. Install Pillow: sudo apt install python3-pil"; return 1; }
  echo "shot: ${1/raw-/}"
}
