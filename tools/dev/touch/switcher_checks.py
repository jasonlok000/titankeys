"""The held Sym's switcher and the screen's taps (C's work on main e133d51a), for checks.py --batch switcher.

1. Three languages on, a held Sym: the tiles over E-R, T-Y and U-I, centred within W to O (the screenshot's margins).
2. A resting finger slid across the switcher: one menu_mark per tile it enters, not one per key (Debug's HapticsDump).
3. A tap on the screen on a Sym panel item and on a switcher tile: one screen_tap each; on a bar word: bar_word, no screen_tap.
4. A fifth language can't go on: Settings keeps four (InputMethodsSet's answer; the line Settings shows is for the eye).
5. Two languages on: a held Sym switches straight to the other, and back.
6. The mark at Q: Chinese with only Yale on shows 中; with Pinyin on too, 耶.

The input methods go on through InputMethodsSet, and each check types in Debug's Space gestures field.
"""
import time

import checks
import language_checks
import languages
from checks import APP, Stop, adb, play, record

HOLD_SYM = ["key:sym:900:none", "+500"]
# The 1076 x 1200 display: the bar's row, and the middle of key column c (0 is Q).
BAR_Y = 1170


def column_x(column):
    return int((column + .5) * 1076 / 10)


def haptics_since(since):
    """The haptics played since the uptime [since], as (name, result), and the phone's uptime now."""
    answer = adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.HapticsDump", "--el", "since", str(since))
    data = answer.split('data="', 1)[-1].rsplit('"', 1)[0]
    lines = [line for line in data.split(";") if line]
    if not lines or not lines[0].startswith("now "):
        raise Stop(f"HapticsDump didn't answer: {answer.strip()[-200:]}")
    now = int(lines[0].split()[1])
    if "feed on" not in lines[0]:
        record("haptic-feed", None, "the Haptics lab's feed is off: open Debug's Haptics lab once so it records")
    return [tuple(line.split()[1:3]) for line in lines[1:]], now


def now():
    return haptics_since(1 << 62)[1]


def setup(on, current):
    language_checks.fresh()
    if not language_checks.turn_on(on, current):
        raise Stop(f"the field didn't start with {current}")
    language_checks.fresh()


def check_centred():
    """Three languages on (English, Russian, Greek), a held Sym: three tiles, two keys each, centred within W to O."""
    setup(["en", "ru", "el"], "en")
    play(*HOLD_SYM, shot="switcher-three", at=1300)
    record("switcher-three", None, "tiles over E-R, T-Y and U-I, equal margins left and right", "switcher-three")
    play("key:sym", "+500")  # Sym closes it with no change


def check_one_mark_per_tile():
    """A resting finger slid from W to O over the switcher's second row (Serbian's two, four keys each): one menu_mark per
    tile it enters, two in all, not one per key."""
    setup(["en", "ru", "sr", "sr-latn"], "en")
    play(*HOLD_SYM, "key:u", "+600")  # SR is over U-I with three languages: a press there opens its second row
    since = now()
    play("rest:0.15,0.125,0.85,0.125:1800", "+400", shot="switcher-marks", at=1200)
    played, _ = haptics_since(since)
    marks = [name for name, result in played if name == "menu_mark"]
    record("switcher-marks", len(marks) == 2, f"{len(marks)} menu_mark across two four-key tiles: {played}", "switcher-marks")
    play("key:sym", "+500")


def check_screen_taps():
    """A tap on a Sym panel item and on a switcher tile: one screen_tap each. A tap on a bar word: bar_word, no screen_tap."""
    setup(["en", "ru", "el"], "en")
    # The Sym panel with no code: its first item, over W on the panel's top row.
    play("key:sym", "+700")
    since = now()
    checks.guarded("tap", str(column_x(1)), "970")
    time.sleep(.8)
    played, _ = haptics_since(since)
    taps = [name for name, _ in played if name == "screen_tap"]
    record("tap-sym-panel", len(taps) == 1, f"{len(taps)} screen_tap for a Sym panel item: {played}")
    checks.guarded("keyevent", "4")
    time.sleep(.6)
    language_checks.fresh()
    # The switcher: Russian's tile, over T-Y.
    play(*HOLD_SYM)
    since = now()
    checks.guarded("tap", str(column_x(4.5)), str(BAR_Y))
    time.sleep(1)
    played, _ = haptics_since(since)
    taps = [name for name, _ in played if name == "screen_tap"]
    record("tap-switcher", len(taps) == 1, f"{len(taps)} screen_tap for a switcher tile: {played}")
    setup(["en", "ru", "el"], "en")
    # A bar word: English's suggestions after "hel".
    play("key:h", "+90", "key:e", "+90", "key:l", "+600")
    since = now()
    checks.guarded("tap", "538", str(BAR_Y))
    time.sleep(.8)
    played, _ = haptics_since(since)
    names = [name for name, _ in played]
    record("tap-bar-word", "bar_word" in names and "screen_tap" not in names, f"a bar word: {played}")
    record("tap-clipboard", None, "a clipboard item's screen_tap needs a clip saved first: for the owner's hands")


def check_fifth_language():
    """Five languages asked for: Settings keeps four on (C's cap). InputMethodsSet says what it kept."""
    answer = adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
                 "en,ru,el,de,fr", "--es", "current", "en")
    kept = answer.split("on ", 1)[-1].split(";")[0].split(",") if "on " in answer else []
    record("fifth-language", 0 < len(kept) <= 4, f"asked for 5 languages, kept {kept}: {answer.strip()[-160:]}")
    record("fifth-language-line", None, "Settings > Languages shows the line when a fifth is tried: for the owner's hands")


def check_two_toggle():
    """Two languages on (English, Russian): a held Sym switches straight to the other, with no overlay, and back."""
    setup(["en", "ru"], "en")
    play(*HOLD_SYM, "key:q", "+500")
    first = checks.field()
    play("key:del", "+300", *HOLD_SYM, "key:q", "+500")
    second = checks.field()
    record("two-toggle", first == "й" and second == "q", f"held Sym then Q: {first!r}; again: {second!r} (wanted й, then q)")


def check_q_mark():
    """C's mark at Q (main 2ef3bf2d): Chinese with only Yale on shows 中, its language; with Pinyin on too, Yale's own 耶."""
    for on, want in ((["en", "yue-yale"], "中"), (["en", "yue-yale", "zh-pinyin"], "耶")):
        setup(on, "yue-yale")
        play("+300", shot=f"q-mark-{len(on)}", at=100)
        record(f"q-mark-{'-'.join(on[1:])}", None, f"{', '.join(on)} on, Yale typing: {want} at Q", f"q-mark-{len(on)}")


BATCH = [check_centred, check_one_mark_per_tile, check_screen_taps, check_fifth_language, check_two_toggle, check_q_mark]
HANDS = ["A tap on a clipboard item (a clip saved first): one screen_tap",
         "Settings > Languages: turning on a fifth language keeps it off and shows the line"]


def finish():
    """The owner's languages as they were before the batches (2026-11-16): English typing, Chinese's three, Afrikaans."""
    adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
        "en,zh-pinyin,yue-jyutping,yue-yale,af", "--es", "current", "en")
