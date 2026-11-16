#!/usr/bin/env bash
# Builds and tests the repo this script sits in, in a throwaway copy, so the repo itself is never edited. Runs in WSL.
# Usage: verify.sh [build|phone]
#   build  bun check, JVM tests and a Debug build. Doesn't touch the phone.
#   phone  everything in build, then installs on the phone and runs the phone tests.
# TK_WORKTREE=<path> builds another checkout instead (for example a git worktree).
# The last line is always "RESULT: PASS", "RESULT: FAIL" or "RESULT: PHONE-UNAVAILABLE".
MODE="${1:-phone}"
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"
export KOTLIN_HOME="$TK_BUILD/kotlin-2.1.20/kotlinc"
export PATH="$H:$HOME/android-sdk/build-tools/36.0.0:$PATH"
W="${TK_WORKTREE:-$TK_REPO}"
# The build copy and the seed it takes node_modules, .ink and signing from. The seed is a checkout that has built once.
B="${TK_VERIFY_COPY:-$TK_BUILD/tk-overnight}"
SEED="${TK_SEED:-$TK_BUILD/titankeys}"
# One build at a time on this machine: the build lock and logs live beside the build toolchain, not in the repo. The phone has
# a lock of its own (tools/phone-lock.sh), taken only for the phone steps, so a phone run elsewhere doesn't hold up a build.
S="$TK_BUILD/tk-tools"
L="$S/logs"
mkdir -p "$L" "$B"
exec 9>"$S/.lock"
flock 9
rm -f "$L"/*.log

for f in node_modules .ink signing; do
  [ -e "$B/$f" ] || cp -a "$SEED/$f" "$B/"
done
find "$B" -mindepth 1 -maxdepth 1 ! -name node_modules ! -name .ink ! -name signing ! -name dist -exec rm -rf {} +
tar -C "$W" --exclude=.git --exclude=node_modules --exclude=dist --exclude=.ink --exclude=signing --exclude=tools/dev/local.env -cf - . | tar -xf - -C "$B"
cd "$B" || { echo "RESULT: FAIL"; exit 1; }
python3 "$H/compat.py" || { echo "FAIL public Ink edits"; echo "RESULT: FAIL"; exit 1; }
# TitanKeys needs a few additions to Ink (tools/ink/titankeys.patch): the theme accent, Text tone, setRouteParents and more.
# They go into the shared Ink checkout, which every build copy uses as ../ink. A changed patch starts again from clean Ink.
P="$B/tools/ink/titankeys.patch"
I="$TK_BUILD/ink"
if [ -f "$P" ]; then
  want=$(sha256sum < "$P")
  if [ "$(cat "$I/.titankeys-patch" 2>/dev/null)" != "$want" ]; then
    { git -C "$I" checkout -q -- . && git -C "$I" apply "$P" && echo "$want" > "$I/.titankeys-patch"; } \
      || { echo "FAIL Ink patch"; echo "RESULT: FAIL"; exit 1; }
    echo "Ink patch applied to $I"
  fi
fi

failed=0
step() {
  local name="$1"; shift
  local log="$L/$name.log" start=$SECONDS
  if timeout 900 "$@" > "$log" 2>&1; then
    echo "PASS $name ($((SECONDS-start))s)"
  else
    failed=1
    echo "FAIL $name ($((SECONDS-start))s), full log: $log"
    tail -n 30 "$log" | cut -c1-200
  fi
}

step check bun run check
for t in tests/*/run.sh; do
  name=$(basename "$(dirname "$t")")
  case "$name" in swipe|preview) continue ;; esac
  step "$name" bash "$t"
done
step build bash tools/debug.sh
[ -f tools/reference.sh ] && step reference-build bash tools/reference.sh

if [ "$MODE" != phone ] || [ "$failed" != 0 ]; then
  [ "$failed" = 0 ] && echo "RESULT: PASS" || echo "RESULT: FAIL"
  exit "$failed"
fi

# From here on the phone: its lock, after the build lock, never the other way round.
. "$TK_REPO/tools/phone-lock.sh"
# The phone tests use plain adb, so pick the phone for them.
[ -n "$TK_ADB_SERIAL" ] && export ANDROID_SERIAL="$TK_ADB_SERIAL"
if ! timeout 20 adb get-state >/dev/null 2>&1 && [ -n "$TK_ADB_SERIAL" ]; then
  timeout 20 adb connect "$TK_ADB_SERIAL" >/dev/null 2>&1
  sleep 2
fi
if ! timeout 20 adb get-state >/dev/null 2>&1; then
  echo "Phone isn't reachable over adb."
  echo "RESULT: PHONE-UNAVAILABLE"
  exit 2
fi
power=$(timeout 20 adb shell dumpsys power | tr -d '\r')
window=$(timeout 20 adb shell dumpsys window | tr -d '\r')
if ! grep -q 'mWakefulness=Awake' <<<"$power" || grep -q 'isKeyguardShowing=true' <<<"$window"; then
  echo "Phone is asleep or locked, so phone tests can't run."
  echo "RESULT: PHONE-UNAVAILABLE"
  exit 2
fi

apk=$(ls -t dist/titankeys-debug-*.apk | head -1)
step install adb install -r "$apk"
if [ -f tools/reference.sh ]; then
  ref=$(ls -t dist/*reference*.apk 2>/dev/null | head -1)
  [ -n "$ref" ] && step reference-install adb install -r "$ref"
fi
step keyboard bash "$H/keyboard.sh"
# Until Accessibility is on, the app stops at its setup step and has no home screen for these tests.
services=$(timeout 20 adb shell settings get secure enabled_accessibility_services | tr -d '\r')
step swipe bash tests/swipe/run.sh
if grep -q 'com.titankeys.debug' <<<"$services"; then
  step ime-window bash -c 'adb shell am start -W -n com.titankeys.debug/com.vandam.ink.MainActivity >/dev/null && sleep 4 && python3 tests/ime-window/run.py com.titankeys.debug --cycles 6'
  # Force-stopping the keyboard's own app turns its Accessibility off and moves the phone to another keyboard.
  # The about, feedback, home-focus and preview checks all do that, so they run only when asked for.
  if [ -n "$TK_FORCE_STOP_CHECKS" ]; then
    step screens env APP=com.titankeys.debug bun run check:phone
    step preview bash tests/preview/run.sh
    apk=$(ls -t dist/titankeys-debug-*.apk | head -1)
    step reinstall adb install -r "$apk"
    step keyboard-again bash "$H/keyboard.sh"
  else
    echo "SKIP about, feedback, home-focus and preview: they turn Accessibility off"
  fi
else
  echo "SKIP ime-window, preview and home-focus: TitanKeys Debug isn't turned on in Accessibility"
  # A page can open slowly behind whatever else is on screen, so give this check a second try. Home is pressed only while
  # TitanKeys Debug is in front (tools/checks/phone.py), never into another app.
  step screens bash -c 'export APP=com.titankeys.debug; (bash tools/checks/about.sh && bash tools/checks/feedback.sh) || (python3 tools/checks/phone.py home || true; sleep 4; bash tools/checks/about.sh && bash tools/checks/feedback.sh)'
fi
bash "$H/keyboard.sh" >/dev/null 2>&1

[ "$failed" = 0 ] && echo "RESULT: PASS" || echo "RESULT: FAIL"
exit "$failed"
