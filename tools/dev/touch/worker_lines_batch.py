"""The workers' phone lines for main 561eb1de (the owner's notes of 2026-11-17, B, C, D), for checks.py --batch worker-lines.
Yale in Debug's Space gestures field; the bar from TitanKeysBar (D's BarLog), the bigger menu from TitanKeysChoices.

1. Enter with a code pending (neihou): the field holds "neihou", typed as letters, and nothing is sent (O).
2. Enter with the bigger menu open: it turns the page, the field stays empty (O), a shot (J).
3. Sym with a code pending: the code goes, the field stays empty and no panel opens (O for the field, J the shot); the next
   Sym opens the panel (J).
4. Backspace held 2 s repeats, as on a PC: in English it takes many characters (O); in Yale with a code pending the code
   goes first (O), the shot shows where it stopped (J).
5. Chinese word units on 我哋唔該晒: a flick takes 唔該晒 (O); three Space taps select 唔該晒 (J); a slide selects one unit (J).
6. No "Warming up" on the bar after a switch to Yale (O).
7. Shift with nothing more (no code, then after a pick): the bigger menu doesn't open (O).
8. The language mark over Q: a shot in English (with its auto-capital line) and in Yale (J).
"""
import os
import time

import checks
import guide_checks
import interactions_batch as ib
import language_checks
import switcher_checks
from checks import APP, Stop, adb, play, record

YALE = "yue-yale"
CHOICES = "TitanKeysChoices"


def read():
    try:
        return checks.field()
    except Stop:
        return None


def yale():
    ib.start(["en", YALE], YALE)


def choices():
    return [line.split(f"{CHOICES}:", 1)[-1].strip() for line in adb("logcat", "-d", "-s", CHOICES).splitlines()
            if f"{CHOICES}:" in line]


def check_enter_with_code():
    yale()
    play(*ib.keys("neihou"), "+500", "key:enter", "+700", shot="enter-code", at=1400)
    text = read()
    record("enter-with-code", text == "neihou", f"neihou then Enter: the field {text!r} (wanted 'neihou', nothing sent)", "enter-code")


def check_enter_menu_open():
    yale()
    play(*ib.keys("neihou"), "+500", "swipeup:b", "+700", "key:enter", "+700", shot="enter-menu", at=2300)
    text = read()
    record("enter-menu-open", text == "", f"the bigger menu open, Enter: the field {text!r} (wanted empty: the page turns)", "enter-menu")
    play("key:sym", "+400", *[g for _ in range(10) for g in ("key:del", "+60")])


def check_sym_with_code():
    yale()
    play(*ib.keys("neihou"), "+500", "key:sym", "+700", shot="sym-code", at=1300)
    text = read()
    record("sym-with-code", text == "", f"neihou then Sym: the field {text!r}; the shot: no panel, no code", "sym-code")
    play("key:sym", "+700", shot="sym-again", at=500)
    record("sym-again", None, "the next Sym: the Sym panel opens", "sym-again")
    checks.guarded("keyevent", "4")
    time.sleep(.6)


def check_held_backspace():
    ib.start(["en"], "en")
    before = checks.set_text("the quick brown fox jumps over the lazy dog " * 3)
    play("key:del:2000:none", "+500")
    after = read() or ""
    gone = len(before) - len(after)
    record("held-backspace-en", gone >= 10 and before.startswith(after), f"2 s held: {gone} characters deleted of {len(before)}")
    yale()
    play(*ib.keys("ngo"), "+500", "key:space", "+600")  # a word in the field first
    kept = read() or ""
    play(*ib.keys("neihou"), "+500", "key:del:2000:none", "+500", shot="held-backspace-yale", at=1500)
    after = read() or ""
    record("held-backspace-yale", not any("a" <= ch <= "z" for ch in after),
           f"{kept!r} then neihou, Backspace held 2 s: the field {after!r} (the code gone first)", "held-backspace-yale")


def check_word_units():
    yale()
    play(*ib.keys("ngodei"), "+500", "key:space", "+600", *ib.keys("mgoisaai"), "+500", "key:space", "+700")
    text = read()
    if text != "我哋唔該晒":
        return record("units-setup", None, f"ngodei and mgoisaai typed {text!r}, not 我哋唔該晒: the unit checks need it")
    play("+600", "flick", "+700")
    after = read()
    record("units-flick", after == "我哋", f"a flick on 我哋唔該晒 left {after!r} (wanted 我哋)")
    play(*ib.keys("mgoisaai"), "+500", "key:space", "+700", "space:3", "+700", shot="units-triple", at=2600)
    record("units-triple", None, "three Space taps: 唔該晒 selected, as picked", "units-triple")
    play("key:space", "+300")
    play("spacehold:2:-1", "+600", shot="units-slide", at=900)
    record("units-slide", None, "a slide left from the word: one unit at a time", "units-slide")


def check_no_warming():
    ib.start(["en", YALE], "en")
    play(*switcher_checks.HOLD_SYM, *ib.keys("nei"), "+800")
    lines = ib.bar_lines()
    warming = [l[1] for l in lines if l[0] == "notice" and "Warming up" in l[1]]
    record("no-warming-up", not warming if lines else None, f"a switch to Yale: notices {warming or 'none'}; the bar {ib.shown(lines)}")


def check_shift_nothing_more():
    os.environ["TK_KEYFEED"] = "1"  # the twin's Shift takes the field's focus on this phone
    try:
        yale()
        adb("logcat", "-c")
        play("chord:shift", "+600")
        empty = [line for line in choices() if "opens" in line]
        play(*ib.keys("ngo"), "+500", "key:space", "+700")
        adb("logcat", "-c")
        play("chord:shift", "+600")
        after_pick = [line for line in choices() if "opens" in line]
        record("shift-nothing-more", not empty and not after_pick,
               f"Shift with no code: {empty or 'no menu'}; after a pick: {after_pick or 'no menu'}")
    finally:
        os.environ.pop("TK_KEYFEED", None)


def check_language_mark():
    for im in ("en", YALE):
        ib.start(["en", YALE], im)
        record(f"mark-{im}", None, "the language mark over Q, centred on the auto-capital line", guide_checks.shot(f"mark-{im}"))


BATCH = [check_enter_with_code, check_enter_menu_open, check_sym_with_code, check_held_backspace, check_word_units,
         check_no_warming, check_shift_nothing_more, check_language_mark]


def finish():
    adb("shell", "setprop", f"log.tag.{ib.TAG}", "''")
    checks.set_text("")
    adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
        "en,zh-pinyin,yue-jyutping,yue-yale,af", "--es", "current", "en")
