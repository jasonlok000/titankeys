"""Main 842717e0's lines (the Master, 2026-11-17 evening), for checks.py --batch evening. Yale in Debug's Space gestures field;
the bar from TitanKeysBar.

1. A tap on a starter (the bar's middle word, empty field) picks it: the field holds that word (O). B, 3140d91f.
2. Space with only predictions on the bar types a plain space in Chinese: 我 picked, then Space gives "我 " (O). B, 3140d91f.
3. Enter types what was picked plus the pending code: 頂你個 picked, fai pending, Enter gives 頂你個fai (O). B, 3140d91f.
4. Korean's how-to in Settings shows no blank jamo (J, a shot). E, 96cbbd01.
5. The owner's MARK in a field whose typing isn't recorded lands in Debug's log (G, 4fe523aa): not automated yet, a LOOK.
"""
import time

import checks
import chinese_checks
import guide_checks
import interactions_batch as ib
import languages
import retest_batch
import switcher_checks
from checks import APP, Stop, adb, play, record


def read():
    try:
        return checks.field()
    except Stop:
        return None


def yale():
    ib.start(["en", "yue-yale"], "yue-yale")


def check_starter_tap():
    yale()
    time.sleep(1)
    bars = [line for line in ib.bar_lines() if line[0] == "bar"]
    middle = bars[-1][2][1] if bars else None
    checks.guarded("tap", "538", str(switcher_checks.BAR_Y))
    time.sleep(.8)
    text = read()
    if middle in (None, "", "-"):
        return record("starter-tap", None, f"no starter logged on the empty bar; the tap typed {text!r}")
    record("starter-tap", (text or "").strip() == middle, f"a tap on the starter {middle!r} typed {text!r}")


def check_space_predictions():
    yale()
    play(*ib.keys("ngo"), "+500", "key:space", "+700")
    picked = read()
    play("key:space", "+600")
    text = read()
    record("space-predictions", bool(picked) and text == picked + " ",
           f"{picked!r} picked, predictions on the bar, Space: {text!r} (wanted {picked!r} and a space)")


def check_enter_picked_code():
    yale()
    play(*ib.keys("dingneigo"), "+600", "key:space", "+700")
    picked = read()
    if picked != "頂你個":
        return record("enter-picked-code", None, f"dingneigo and Space typed {picked!r}, not 頂你個: the check needs it")
    play(*ib.keys("fai"), "+500", "key:enter", "+600")
    text = read()
    record("enter-picked-code", text == "頂你個fai", f"頂你個 picked, fai pending, Enter: {text!r} (wanted 頂你個fai)")


def check_korean_howto():
    def step(*command):
        if f" {APP}/" not in languages.front():
            raise Stop(f"TitanKeys Debug left the front: {languages.front().strip()}")
        adb("shell", "input", *command)
        time.sleep(1.5)
    retest_batch.own_screen()
    adb("shell", "am", "start", "-n", f"{APP}/com.vandam.ink.MainActivity")
    time.sleep(3)
    try:
        retest_batch.open_settings(step)
        x, y = retest_batch.row_at("Languages") or (540, 452)
        step("tap", str(x), str(y))
        row = None
        for _ in range(12):
            row = retest_batch.row_at("한국어")
            if row:
                break
            step("swipe", "540", "950", "540", "350", "400")
        if not row:
            return record("korean-howto", None, "no 한국어 row found", guide_checks.shot("korean-howto"))
        step("tap", str(row[0]), str(row[1]))
        record("korean-howto", None, "Korean's how-to: every jamo drawn, no blank boxes", guide_checks.shot("korean-howto"))
    finally:
        for _ in range(5):
            if f" {APP}/" in languages.front() and not languages.in_field():
                adb("shell", "input", "keyevent", "4")
                time.sleep(1)


def check_mark():
    record("mark-unrecorded-field", None, "the owner's MARK in a no-suggestions field: not automated yet (needs a pull of "
           "Debug's log through LogPull); for the owner's hands")


BATCH = [check_starter_tap, check_space_predictions, check_enter_picked_code, check_korean_howto, check_mark]


def finish():
    adb("shell", "setprop", f"log.tag.{ib.TAG}", "''")
    try:
        chinese_checks.fresh()
    except Stop:
        pass
    adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
        "en,zh-pinyin,yue-jyutping,yue-yale,af", "--es", "current", "en")
