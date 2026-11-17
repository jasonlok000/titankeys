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

# The gate's bring-back moves the running field screen to the front, not a new one on top (2026-11-17: eight stacked).
sent=$(bash -c '. tools/dev/env.sh; . tools/dev/phone/safety.sh; . tools/dev/touch/touchlib.sh
  tk_adb() { echo "$*"; }; tk_bring_back')
case "$sent" in
  *"am start --activity-reorder-to-front -n com.titankeys.debug/com.titankeys.keyboard.SpaceGestureActivity"*) ;;
  *) echo "FAIL the gate's bring-back doesn't reorder the running screen: $sent"; exit 1 ;;
esac
grep -q 'tk_bring_back' tools/dev/touch/touchlib.sh && ! grep -v '^\s*#' tools/dev/touch/touchlib.sh | grep -q 'am start -n' \
  || { echo "FAIL a bring-back in touchlib.sh starts the screen without the reorder"; exit 1; }
echo "Bring-back checks passed"

# TK_WAIT_FOR_OWNER: another app in front is the owner's (the run waits, touching nothing); Debug and the home screen aren't.
owner() {
  FRONT=$1 bash -c '. tools/dev/env.sh; . tools/dev/phone/safety.sh; . tools/dev/touch/touchlib.sh
    tk_adb() { case "$*" in *resolve-activity*) echo "com.android.launcher3/.Launcher";;
               *dumpsys\ window*) echo "  mCurrentFocus=Window{1 u0 $FRONT}";; *) echo "SENT $*";; esac; }
    tk_owner_app && echo owner || echo free; TK_OWNER_WAIT_S=0 tk_wait_for_owner > /dev/null && echo gone || echo stayed'
}
[ "$(owner com.anthropic.claude/com.anthropic.claude.mainactivity.MainActivity)" = "$(printf 'owner\nstayed')" ] ||
  { echo "FAIL the owner's app in front wasn't waited for"; exit 1; }
[ "$(owner com.titankeys.debug/com.vandam.ink.MainActivity)" = "$(printf 'free\ngone')" ] || { echo "FAIL Debug in front was waited for"; exit 1; }
[ "$(owner com.android.launcher3/com.android.launcher3.Launcher)" = "$(printf 'free\ngone')" ] || { echo "FAIL the home screen was waited for"; exit 1; }
[ "$(owner com.titankeys/com.vandam.ink.MainActivity)" = "$(printf 'owner\nstayed')" ] || { echo "FAIL release TitanKeys' screen wasn't left to the owner"; exit 1; }
owner com.anthropic.claude/x | grep -q SENT && { echo "FAIL the wait sent something to the phone"; exit 1; }
# A Settings page TitanKeys Debug opened is the run's (one Back); one the owner opened isn't. As dumpsys prints them (2026-11-17).
opened() {
  FROM=$1 bash -c '. tools/dev/env.sh; . tools/dev/phone/safety.sh; . tools/dev/touch/touchlib.sh
    tk_adb() { printf "%s\n" "  * Hist #0: ActivityRecord{44175795 u0 com.android.settings/.SubSettings t38722}" \
      "      launchedFromUid=10338 launchedFromPackage=$FROM launchedFromFeature=null userId=0" \
      "    topResumedActivity=ActivityRecord{44175795 u0 com.android.settings/.SubSettings t38722}"; }
    tk_debug_opened && echo debug || echo owner'
}
[ "$(opened com.titankeys.debug)" = debug ] || { echo "FAIL a page Debug opened wasn't seen as the run's"; exit 1; }
[ "$(opened com.android.settings)" = owner ] || { echo "FAIL a page the owner opened was taken for the run's"; exit 1; }
echo "Owner wait checks passed"

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
# The rime-files batch reads LeakCheck's "rime data" part, as main 2db7a87b writes it, and its "none copied".
import rime_batch
rime_batch.adb = lambda *args: ('Broadcast completed: result=0, data="no guide or playground in memory; rime data: 53.8 MB in 41 '
                                'files; tables and grammars: jyut6ping3.table.bin, tk_yue.gram"')
assert rime_batch.rime_data() == (53.8, 41, ["jyut6ping3.table.bin", "tk_yue.gram"]), rime_batch.rime_data()
rime_batch.adb = lambda *args: 'Broadcast completed: result=0, data="no guide; rime data: none copied"'
assert rime_batch.rime_data() == (0.0, 0, [])
rime_batch.adb = lambda *args: 'Broadcast completed: result=0, data="no guide"'
assert rime_batch.rime_data() is None
# E's delete animation batch: its counts parse, and its longest calls build within one call's 15 s.
import delete_batch
assert delete_batch.dots(["delete animation: dots 41", "dominoes run 100 words", "delete animation: dots 60 as dominoes, 104 words"]) == [41, 60]
for moves in (["flick", "+300"] * 33, [delete_batch.HOLD, "+1500"], [delete_batch.HOLD, "+300", "key:x:60:none", "+800"]):
    built = subprocess.run([sys.executable, "tools/dev/touch/gestures.py", "--out", os.path.join(os.environ["TK_OUT"], "del.tki"), *moves],
                           capture_output=True, text=True)
    assert built.returncode == 0, f"the delete batch's gestures don't build: {built.stderr[-300:]}"
# languages.in_field's bring-back carries the reorder too: Debug in front on another screen, the field's screen comes up.
import languages
sent = []
fronts = iter(["  mCurrentFocus=Window{1 u0 com.titankeys.debug/com.vandam.ink.MainActivity}"] * 2 + [""] * 9)
real_languages_adb, real_sleep = languages.adb, languages.time.sleep
languages.adb = lambda *args: sent.append(args) or (next(fronts) if "dumpsys" in args else "")
languages.time.sleep = lambda seconds: None
languages.in_field()
languages.adb, languages.time.sleep = real_languages_adb, real_sleep
starts = [args for args in sent if args[:2] == ("shell", "am")]
assert starts and all("--activity-reorder-to-front" in args for args in starts), starts
# The interactions batch reads TitanKeysBar's lines (the format it was written for, D's line) and builds its longest calls.
import interactions_batch as ib
ib.adb = lambda *args: ("11-17 08:00:00.000 1 2 D TitanKeysBar: bar: yue-yale 你 | 呢 | 尼\n"
                        "11-17 08:00:00.100 1 2 D TitanKeysBar: bar notice: Warming up Yale…\n"
                        "11-17 08:00:00.200 1 2 D TitanKeysBar: bar: en - | did | -")
assert ib.bar_lines() == [("bar", "yue-yale", ["你", "呢", "尼"]), ("notice", "Warming up Yale…"), ("bar", "en", ["-", "did", "-"])], ib.bar_lines()
# Sym has no Linux code (keymap.sh reads it from the phone), so offline only the moves without it.
for moves in ([*ib.keys("neihou"), "+500", "swipeup:b", "+700", "key:s", "+500"],
              [*ib.keys("hello"), "key:space", "+90", *ib.keys("there"), "+800", "flick", "+600"]):
    built = subprocess.run([sys.executable, "tools/dev/touch/gestures.py", "--out", os.path.join(os.environ["TK_OUT"], "ib.tki"), *moves],
                           capture_output=True, text=True)
    assert built.returncode == 0, f"the interactions batch's gestures don't build: {built.stderr[-300:]}"
# The key feed's script reaches the phone as one quoted argument: the phone's shell splits on spaces and ";" (2026-11-17: "bad keys").
import keyfeed_batch
keyfeed_batch.adb = lambda *args: args
sent = keyfeed_batch.feed("D 0 SHIFT_RIGHT;D 60 Q;U 140 Q;U 200 SHIFT_RIGHT")
assert sent[-1] == "'D 0 SHIFT_RIGHT;D 60 Q;U 140 Q;U 200 SHIFT_RIGHT'", sent
print("Batch checks passed")
PY
