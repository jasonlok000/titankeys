#!/usr/bin/env bash
# The touch simulator's offline checks: no phone. Needs a JDK, a C compiler and python3.
set -euo pipefail
cd "$(dirname "$0")/../../../.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
javac -d "$work" android/kotlin/com/titankeys/engine/TouchAlignment.java tools/dev/touch/test/AlignmentDump.java
java -cp "$work" com.titankeys.engine.AlignmentDump android/assets/calibration/titan-2-saved.profile > "$work/alignment.txt"
cc -O2 -Wall -Wextra -Werror -o "$work/tk-inject" tools/dev/touch/inject.c
python3 tools/dev/touch/test/test_touch.py "$work/alignment.txt" "$work/tk-inject"

# TK_KEYFEED's scripts: gestures.py writes the keys by Android's names for the key feed, and leaves the injector none.
python3 tools/dev/touch/gestures.py --keyfeed-out "$work/k.keys" --feed-out "$work/k.feed" --out "$work/k.tki" chord:shift+q +100 key:space:60:none
[ "$(cat "$work/k.keys")" = "D 0 SHIFT_RIGHT;D 30 Q;U 110 Q;U 130 SHIFT_RIGHT;D 240 SPACE;U 300 SPACE" ] \
  || { echo "FAIL the key feed's keys: $(cat "$work/k.keys")"; exit 1; }
grep -q TitanKey "$work/k.tki" && { echo "FAIL the injector still got keys with TK_KEYFEED"; exit 1; }
echo "Key feed scripts checks passed"

# The key feed's script (TitanKeys Debug's KeyFeed): Shift's meta state, Android's repeats, bad scripts refused.
if [ -n "${KOTLIN_HOME:-}" ]; then
  "$KOTLIN_HOME/bin/kotlinc" -nowarn -d "$work/keyfeed" android/debug/recorder/KeyFeedPlan.kt tools/dev/touch/test/KeyFeedChecks.kt 2>&1 | grep -v '^warning' || true
  java -cp "$work/keyfeed:${KOTLIN_STDLIB:-$KOTLIN_HOME/lib/kotlin-stdlib.jar}" com.titankeys.keyboard.KeyFeedChecksKt
else
  echo "Key feed checks NOT run: set KOTLIN_HOME to your Kotlin compiler directory"
fi

# The phone lock: a tool holds it for its whole run and gives it back at the end, and one it starts doesn't wait for it.
# On a lock file of its own, never the real one.
export TK_BUILD="$work/build"
export TK_NO_PHONE=1
mkdir -p "$TK_BUILD/tk-tools"
lock="$TK_BUILD/tk-tools/.phone.lock"
held() { mkdir -p "$TK_BUILD/tk-tools"; ! flock -n "$lock" true; }
bash -c '. tools/dev/env.sh; . tools/dev/phone/safety.sh; . tools/dev/touch/touchlib.sh; tk_touch_lock
  bash -c ". tools/dev/env.sh; . tools/dev/phone/safety.sh; . tools/dev/touch/touchlib.sh; tk_touch_lock; echo inner ran"
  touch "$TK_BUILD/held"; sleep 2' > "$work/lock.out" &
for _ in $(seq 50); do [ -e "$TK_BUILD/held" ] && break; sleep .1; done
held || { echo "FAIL the phone lock isn't held during a run"; exit 1; }
wait
grep -q "inner ran" "$work/lock.out" || { echo "FAIL a tool started inside a run waited for the lock"; exit 1; }
held && { echo "FAIL the phone lock is still held after the run"; exit 1; }
echo "Phone lock checks passed"

# A runner holding Debug on gets it back when a failed check on the way put release TitanKeys back (2026-11-16), and only then.
ready() {
  NOW=$1 TK_IME_HELD=1 bash -c '. tools/dev/env.sh; . tools/dev/phone/safety.sh; . tools/dev/touch/touchlib.sh
    tk_touch_check() { return 0; }; tk_ime_now() { echo "$NOW"; }; tk_ime_debug() { echo "debug picked"; }
    tk_touch_ready'
}
[ "$(ready com.titankeys/com.atlas.keyboard.AtlasIme)" = "debug picked" ] || { echo "FAIL a held Debug wasn't picked again after release came back"; exit 1; }
[ -z "$(ready com.titankeys.debug/com.titankeys.keyboard.TitanKeysIme)" ] || { echo "FAIL Debug was picked again while it was already the keyboard"; exit 1; }
echo "Held Debug checks passed"

# A batch: every named one loads, its gestures build, and its report says each result once, with what's left for hands.
TK_OUT="$work/out" python3 - <<'PY'
import os, sys
sys.path.insert(0, "tools/dev/touch")
import checks
sys.modules["checks"] = checks
import guide_checks
for name, module_name in checks.BATCHES.items():
    module = __import__(module_name)
    assert all(callable(check) for check in module.BATCH), f"{name}: a check that can't run"
    assert module.BATCH, f"{name}: no checks"
    if hasattr(module, "code"):
        assert module.duration(*module.code("hold:p:800")) > 1400, f"{name}: the code and a held P take too little time"
checks.record("one", True, "fine", "one")
checks.record("two", None, "look", "two")
path = checks.report("test", "now", ["by hand"])
text = open(path, encoding="utf-8").read()
for want in ("1 YES, 0 NO, 1 to look at", "- **YES** one: fine (`shots/one.png`)", "- **LOOK** two: look", "- by hand"):
    assert want in text, f"the report lacks {want!r}:\n{text}"
# The languages batch: every input method in index.json has a test, and the expected text comes from its im.json.
import language_checks
ids = language_checks.all_ids()
assert "ja" in ids and "ko" in ids, ids
for im in ids:
    if im not in language_checks.LOOK and im not in language_checks.OWN:
        assert language_checks.expected(im, list(language_checks.PLAIN)), im
assert language_checks.expected("ru", list("qwe")) == "йцу", language_checks.expected("ru", list("qwe"))
# #98's batch: the gestures it plays build (a capital through Shift, Space, a swipe, Backspace).
import subprocess, space_batch
moves = [*space_batch.keys("^teh"), "+90", "key:space:60:none", "+300", "key:del:60:none", "+90", "swipe:hello", "+400", "key:space"]
built = subprocess.run([sys.executable, "tools/dev/touch/gestures.py", "--out", os.path.join(os.environ["TK_OUT"], "98.tki"), *moves],
                       capture_output=True, text=True)
assert built.returncode == 0, f"#98's gestures don't build: {built.stderr[-300:]}"
# The morning cutoff: off without TK_CUTOFF_EPOCH, on once its time has come, off before it.
os.environ.pop("TK_CUTOFF_EPOCH", None)
assert not checks.past_cutoff()
os.environ["TK_CUTOFF_EPOCH"] = "0"
assert checks.past_cutoff()
os.environ["TK_CUTOFF_EPOCH"] = str(10 ** 12)
assert not checks.past_cutoff()
os.environ.pop("TK_CUTOFF_EPOCH")
# A tap that worked, as TitanKeysTap logged it on 2026-11-17 (after the tag): it passes; one taken by nothing doesn't.
import switcher_checks
assert switcher_checks.tapped(["tap at 161,970 on b6", "screen_tap played"])
assert not switcher_checks.tapped(["tap at 161,970 taken by nothing", "screen_tap played"])
# The heat wait, with adb played by a stand-in: 51.2 °C and MediaTek's warning in front stop after the waits; 30.0 °C passes.
real_adb, real_steps, real_seconds = checks.adb, checks.HEAT_WAIT_STEPS, checks.HEAT_STEP_SECONDS
checks.HEAT_WAIT_STEPS, checks.HEAT_STEP_SECONDS = 1, 0
def stand_in(temperature, front):
    return lambda *args: f"  temperature: {temperature}" if "battery" in args else f"  mCurrentFocus=Window{{1 u0 {front}}}"
checks.adb = stand_in(512, "com.mediatek.batterywarning/com.mediatek.batterywarning.BatteryWarningActivity")
assert checks.phone_heat() == (51.2, True), checks.phone_heat()
try:
    checks.wait_cool()
    raise AssertionError("a hot phone didn't stop the run")
except checks.Stop:
    pass
checks.adb = stand_in(300, "com.titankeys.debug/com.titankeys.keyboard.SpaceGestureActivity")
assert checks.phone_heat() == (30.0, False)
checks.wait_cool()
checks.adb, checks.HEAT_WAIT_STEPS, checks.HEAT_STEP_SECONDS = real_adb, real_steps, real_seconds
print("Batch checks passed")
PY
