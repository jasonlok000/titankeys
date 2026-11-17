"""E's delete animation and dominoes (#89, main 86c83c94), for checks.py --batch delete-animation, in English in Debug's Space
gestures field. What the animation shows is read from TitanKeys Debug's own counts, never text, under the log tag TitanKeysDelete:
"delete animation: dots N[ as dominoes, W words]" when a delete's dots start, and "dominoes run W words" when a streak reaches
the dominoes (100 words, each within 2 s of the last, flicks and held deletes, no typing between).

1. A flick at the end of 2,000 characters: dots N with N <= 60 (O); a shot at about 300 ms (J).
2. A full 10 s hold: dots 60 at the lift (O); a shot at about 5 s: the pills, the orange word, the glow and the caret (J).
3. Select-all, then a flick: no "dominoes" in the log (O).
4. Full holds, each flicked again within 2 s of the last: "dominoes run 100 words" (O), and "as dominoes" at the last lift
   (O); a shot about 500 ms after it (J).
5. A streak of 99 words, one flick a word: no dominoes (O); the 100th flick then plays them, which shows the streak held.
6. A letter key while the tiles fall: they're gone at once (J, a shot).
7. The held delete uncapped: a 14 s hold deletes clearly more than a 10 s one (O).
8. The dominoes on every 100-word streak, longer, never blocking a delete (O and J).

One touch call plays at most 15 s, so a streak spans calls, and the gap between two calls (the gate's adb calls) counts against
the streak's 2 s. Each call's time past its script goes in the report; when it's over 2 s, a missing dominoes line is a LOOK.
"""
import os
import re
import subprocess
import time

import checks
import gesture_batch
from checks import APP, HERE, Stop, adb, record

PHRASE = "the quick brown fox jumps over the lazy dog"
TEXT = " ".join([PHRASE] * 45)  # 1,979 characters, 405 words
HOLD = "flick:3.2:90:10000"  # a full hold: the long delete stops at 10 s
HOLD_MS = 10090
GAP_MS = 2000  # GestureDisplay.DOMINO_GAP_MILLIS
FLICK_EVERY = 300


def delete_lines():
    return [line.split("TitanKeysDelete:", 1)[-1].strip() for line in adb("logcat", "-d", "-s", "TitanKeysDelete").splitlines()
            if "TitanKeysDelete:" in line]


def dots(lines):
    return [int(m.group(1)) for line in lines for m in [re.match(r"delete animation: dots (\d+)", line)] if m]


def long_text(name):
    """English, the 2,000 characters typed with the caret at their end, the log cleared. False (recorded) if setup fell short."""
    gesture_batch.english()
    before = checks.set_text(TEXT)
    time.sleep(2.5)  # past the streak's gap: setup's keys started it over anyway
    adb("logcat", "-c")
    if len(before) < 1500:
        record(name, None, f"setup typed only {len(before)} characters of {len(TEXT)}")
        return None
    return before


def play_now(*gestures, shot=None, at=0, script_ms=0):
    """checks.play without its pause after, for a streak that spans calls. Returns the ms the call took past its script."""
    env = dict(os.environ)
    if shot:
        env.update(TK_TOUCH_SHOT=shot, TK_TOUCH_SHOT_AT=str(at))
    started = time.monotonic()
    result = subprocess.run(["bash", os.path.join(HERE, "touch.sh"), *gestures], capture_output=True, text=True, env=env)
    out = (result.stdout + result.stderr).strip()
    if result.returncode == 1 or "STOP" in out:
        raise Stop(out)
    if result.returncode != 0:
        raise RuntimeError(out)
    return int((time.monotonic() - started) * 1000) - script_ms


def words(text):
    return len(text.split())


def check_flick_dots():
    """1. A flick at the end of 2,000 characters: dots N, N at most 60."""
    if long_text("delete-flick-dots") is None:
        return
    checks.play("flick", "+800", shot="delete-flick-dots", at=300)
    found = dots(delete_lines())
    record("delete-flick-dots", bool(found) and max(found) <= 60, f"dots {found or 'none logged'} (60 at most)", "delete-flick-dots")


def check_full_hold():
    """2. A full 10 s hold: dots 60 at the lift, and a shot at about 5 s."""
    if long_text("delete-hold-dots") is None:
        return
    checks.play(HOLD, "+800", shot="delete-hold", at=5000)
    found = dots(delete_lines())
    record("delete-hold-dots", found[-1:] == [60], f"dots at the lift {found or 'none logged'} (wanted 60)")
    record("delete-hold-look", None, "at about 5 s: the pills, the orange word, the glow and the caret", "delete-hold")


def check_select_all():
    """3. Select-all, then a flick: an instant delete with the usual animation, never dominoes."""
    if long_text("delete-select-all") is None:
        return
    checks.guarded("keycombination", "113", "29")  # Ctrl+A
    checks.play("flick", "+800")
    lines = delete_lines()
    left = checks.field()
    record("delete-select-all", not any("dominoes" in line for line in lines),
           f"{len(left)} characters left; the log: {lines or 'nothing'}")


def holds_to_dominoes(last=(), last_ms=0, shot=None, shot_after=0):
    """Full holds, each started as soon as the last call returns, until "dominoes run" shows (four at most). [last] plays after
    the hold that reaches them, in the same call. Returns (lines, ms past each call's script)."""
    over = []
    for hold in range(4):
        reached_before = any("dominoes run" in line for line in delete_lines()) if hold else False
        if reached_before:
            break
        final = hold >= 2  # the third hold should reach 100 words; [last] rides on it and on a fourth
        moves = [HOLD, *(last if final else ())]
        over.append(play_now(*moves, shot=shot if final else None, at=HOLD_MS + shot_after,
                             script_ms=HOLD_MS + (last_ms if final else 0)))
    time.sleep(1.5)
    return delete_lines(), over


def check_dominoes():
    """4. Holds flicked again within 2 s: "dominoes run 100 words", "as dominoes" at the last lift, a shot 500 ms after it."""
    if long_text("delete-dominoes") is None:
        return
    lines, over = holds_to_dominoes(last=("+1500",), last_ms=1500, shot="delete-dominoes", shot_after=500)
    ran = [line for line in lines if line.startswith("dominoes run")]
    lifts = [line for line in lines if line.startswith("delete animation:")]
    slow = any(ms > GAP_MS for ms in over)
    detail = f"{len(over)} holds; each call's ms past its script {over}; the log: {lines}"
    record("delete-dominoes-run", True if ran == ["dominoes run 100 words"] else (None if slow and not ran else False), detail)
    record("delete-dominoes-lift", bool(lifts) and "as dominoes" in lifts[-1] if ran else (None if slow else False),
           f"the last lift: {lifts[-1] if lifts else 'none logged'}")
    record("delete-dominoes-look", None, "500 ms after the last lift: the dominoes' tiles and their count", "delete-dominoes")


def check_99_words():
    """5. 99 flicks, one word each, in one streak: no dominoes; the 100th flick then plays them."""
    before = long_text("delete-99")
    if before is None:
        return
    flick = ["flick", f"+{FLICK_EVERY}"]
    per_call = 33  # 33 x 390 ms: 12.9 s a call
    over = []
    for _ in range(3):
        over.append(play_now(*flick * per_call, script_ms=per_call * (90 + FLICK_EVERY)))
    at_99 = delete_lines()
    over.append(play_now(*flick, script_ms=90 + FLICK_EVERY))
    time.sleep(1.5)
    after_100 = delete_lines()
    gone = words(before) - words(checks.field())
    early = [line for line in at_99 if "dominoes" in line]
    ran = [line for line in after_100 if line.startswith("dominoes run")]
    detail = f"{gone} words deleted in 100 flicks; each call's ms past its script {over}; at 99: {early or 'no dominoes'}; " \
             f"after the 100th: {ran or 'no dominoes'}"
    if early:
        record("delete-99", False, detail)
    else:
        # No dominoes at 99 only means something if the streak held: 100 words went and the 100th played them.
        record("delete-99", True if gone == 100 and ran == ["dominoes run 100 words"] else None, detail)


def check_key_ends_dominoes():
    """6. A letter key while the dominoes' tiles fall: they're gone at once (the shot, 150 ms after the key)."""
    if long_text("delete-key-ends") is None:
        return
    lines, over = holds_to_dominoes(last=("+300", "key:x:60:none", "+800"), last_ms=1100,
                                    shot="delete-key-ends", shot_after=450)
    ran = any(line.startswith("dominoes run") for line in lines)
    record("delete-key-ends", None, f"{'dominoes ran' if ran else 'no dominoes ran'} ({len(over)} holds, ms past each script "
           f"{over}); X 300 ms after the lift: no tiles 150 ms after it", "delete-key-ends")


# E's fixes after the owner tried 5f4d7224 (owner-decisions-2.1, 2026-11-17), on main since 561eb1de.
def expected_fail(name, ok, detail, shot=None):
    record(name, bool(ok), detail, shot)


def check_uncapped_delete():
    """A held delete deletes as long as the finger stays, only its animation capped at 10 s ("the actual deleting shdnt b
    capped"): a 14 s hold takes clearly more words than a 10 s one, on the same text."""
    gone = []
    for hold_ms in (10000, 14000):
        before = long_text("delete-uncapped")
        if before is None:
            return
        checks.play(f"flick:3.2:90:{hold_ms}", "+800")
        gone.append(words(before) - words(checks.field()))
    expected_fail("delete-uncapped", gone[0] > 0 and gone[1] >= gone[0] * 1.2,
                  f"a 10 s hold took {gone[0]} words, a 14 s hold {gone[1]} (uncapped: 1.2 times or more)")


def check_dominoes_every_streak():
    """The dominoes after the owner tried them: every 100-word streak plays them, not only the first; a delete while they show
    still deletes; they run about 2.5 times longer (the shot, 1.5 s after the lift)."""
    before = long_text("dominoes-every-streak")
    if before is None:
        return
    first, over = holds_to_dominoes(last=("+400",), last_ms=400)
    time.sleep(3)  # past the streak's gap: the next holds are a new streak
    adb("logcat", "-c")
    second, over2 = holds_to_dominoes(last=("+1500",), last_ms=1500, shot="dominoes-long", shot_after=1500)
    ran = [any(line == "dominoes run 100 words" for line in lines) for lines in (first, second)]
    slow = any(ms > GAP_MS for ms in over + over2)
    detail = (f"first streak {'played' if ran[0] else 'no dominoes'}, second {'played' if ran[1] else 'no dominoes'}; "
              f"ms past each call's script {over + over2}")
    record("dominoes-every-streak", True if all(ran) else (None if slow else False), detail)
    record("dominoes-longer", None, "1.5 s after the last lift: the dominoes still falling (about 2.5 times longer)", "dominoes-long")
    # A flick while they may still show: it deletes a word.
    before_flick = words(checks.field())
    checks.play("flick", "+700")
    after_flick = words(checks.field())
    expected_fail("dominoes-dont-block", after_flick == before_flick - 1,
                  f"a flick just after the dominoes: {before_flick} words, then {after_flick}")


BATCH = [check_flick_dots, check_full_hold, check_select_all, check_dominoes, check_99_words, check_key_ends_dominoes,
         check_uncapped_delete]
# check_dominoes_every_streak is out: the owner's call of 2026-11-17 (55e7054e) plays them once a streak, past 100 words, live
# through the hold. batch2_checks' 250-word hold checks that.


def finish():
    """An empty field, and the owner's languages as they were before the batches: English typing, Chinese's three, Afrikaans."""
    checks.set_text("")
    adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
        "en,zh-pinyin,yue-jyutping,yue-yale,af", "--es", "current", "en")
