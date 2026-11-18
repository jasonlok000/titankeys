"""Main 426a478b's lines from B and E (2026-11-17), for checks.py --batch b-lines.

1. A flick with a code pending takes only the code: 我哋 picked, then nei pending, a flick leaves 我哋 (O); a second flick takes
   one character, leaving 我 (O). B, 981a474e and 87e6db26.
2. A Space touch with a code pending picks nothing: neihou pending, a tap in Space's touch zone leaves the field empty (O);
   Space then picks (O, Chinese characters). B, 981a474e.
3. 速成 and 倉頡, B's radical preview in the code's tab (7f5a5a10): a grey dotted 弓 alone on N with nothing typed; 竹, a gap
   and a grey 手; no flash in fast typing; gone on lift (J, shots; YES where TitanKeysBar logs 弓).
4. Quick (速成): hqi and Space type 我 (O).
5. The playground (the app's front page) types in the input methods that are on, and a held Sym switches there: English and
   Russian on, a held Sym, Q types й (O). Only what the check typed is deleted, never a select-all: the field is the owner's.
   E, de807439.
"""
import time

import checks
import chinese_checks
import interactions_batch as ib
import language_checks
import languages
import switcher_checks
from checks import APP, Stop, adb, play, record

# Key N on the touchpad: column 5 of 10 (z x c v b n m starts at 0), the third of four rows.
N_KEY = (0.55, 0.625)


def read():
    try:
        return checks.field()
    except Stop:
        return None


def check_flick_pending_code():
    ib.start(["en", "yue-yale"], "yue-yale")
    play(*ib.keys("ngodei"), "+500", "key:space", "+700")
    picked = read()
    if not chinese_checks.cjk(picked):
        return record("flick-pending-code", None, f"ngodei and Space typed {picked!r}: the check needs Chinese first")
    play(*ib.keys("nei"), "+500", "flick", "+800")
    first = read()
    play("+600", "flick", "+800")
    second = read()
    record("flick-pending-code", first == picked, f"{picked!r} then nei pending, a flick: {first!r} (wanted {picked!r})")
    record("flick-one-character", second == picked[:-1], f"a second flick: {second!r} (wanted {picked[:-1]!r})")


def check_space_touch_keeps_code():
    ib.start(["en", "yue-yale"], "yue-yale")
    play(*ib.keys("neihou"), "+500", "space:1", "+700")
    touched = read()
    play("key:space", "+700")
    picked = read()
    record("space-touch-keeps-code", touched == "", f"neihou pending, a Space touch: the field {touched!r} (wanted empty)")
    record("space-after-touch-picks", chinese_checks.cjk(picked), f"then Space: {picked!r}")


def radical_shown():
    """Whether TitanKeysBar logged 弓 (N's radical) since the log was cleared. The preview draws in the code's tab (B, 7f5a5a10),
    which may not log: then the shot is the judge."""
    return any("弓" in (" ".join(line[2]) if line[0] == "bar" else line[1]) for line in ib.bar_lines())


def check_radical_hover():
    """B's radical preview in the code's tab, in 速成 and 倉頡 (7f5a5a10): after a still rest of 180 ms the rested key's radical
    shows in grey with a dotted underline. Shots judge what the log can't show."""
    for im, name in (("zh-quick", "quick"), ("zh-cangjie", "cangjie")):
        # 1. Nothing typed, right after the switch: a grey dotted 弓 alone on N.
        ib.start(["en", im], im)
        adb("logcat", "-c")
        play(f"touch:{N_KEY[0]},{N_KEY[1]}:1500", "+300", shot=f"radical-{name}-alone", at=900)
        record(f"radical-{name}-alone", True if radical_shown() else None, "the log for a rest on N before typing")
        record(f"radical-{name}-style", None, "A grey dotted 弓 alone in the tab, no glow", f"radical-{name}-alone")
        # 2. 竹 typed (H), resting on Q: 竹, a gap, then a grey 手.
        play("key:h", "+300", "touch:0.05,0.125:1500", "+300", shot=f"radical-{name}-after-code", at=1100)
        record(f"radical-{name}-after-code", None, "竹 typed, resting on Q: 竹, a gap, a grey dotted 手", f"radical-{name}-after-code")
        # 3. Fast typing: no preview flashes between keys (a shot mid-typing).
        play(*[g for k in "abcde" for g in (f"key:{k}:50:none", "+40")], shot=f"radical-{name}-fast", at=300)
        record(f"radical-{name}-fast", None, "typing fast: no grey preview between keys", f"radical-{name}-fast")
        play(*[g for _ in range(10) for g in ("key:del", "+60")])
        # 4. Gone on lift: rest on N, lift, a shot 400 ms after.
        play(f"touch:{N_KEY[0]},{N_KEY[1]}:800", "+600", shot=f"radical-{name}-lift", at=1200)
        record(f"radical-{name}-lift", None, "after the finger lifts from N: no 弓 left", f"radical-{name}-lift")


def check_quick():
    ib.start(["en", "zh-quick"], "zh-quick")
    play(*ib.keys("hqi"), "+500", "key:space", "+700")
    text = read()
    record("quick-hqi", (text or "").strip() == "我", f"速成, hqi and Space: {text!r} (wanted 我)")


def check_playground_switch():
    language_checks.guide_checks.leave_guide()
    if not language_checks.turn_on(["en", "ru"], "en"):
        return record("playground-switch", False, "English and Russian didn't go on")
    adb("shell", "am", "start", "--activity-reorder-to-front", "-n", f"{APP}/com.vandam.ink.MainActivity")
    time.sleep(3)
    before = read()
    if before is None:
        return record("playground-switch", None, "the playground's field wasn't focused on the front page")
    play(*switcher_checks.HOLD_SYM, "key:q", "+500")
    after = read()
    # An empty field may read back as its hint, which typing replaces: then all of it is what this check typed.
    typed = after[len(before):] if after and after.startswith(before) else after
    # The field starts a sentence, so й comes out capital: Й (night run 2026-11-17's shot).
    if typed:
        play(*[g for _ in typed for g in ("key:del", "+90")])  # only what this check typed
    left = read()
    record("playground-switch", (typed or "").lower() == "й", f"a held Sym in the playground, then Q: typed {typed!r} (wanted й); "
           f"the field {'as it was' if left in (before, "") else 'NOT as it was'} after")
    play(*switcher_checks.HOLD_SYM)  # back to English
    languages.in_field()


BATCH = [check_flick_pending_code, check_space_touch_keeps_code, check_radical_hover, check_quick, check_playground_switch]


def finish():
    adb("shell", "setprop", f"log.tag.{ib.TAG}", "''")
    try:
        language_checks.fresh()
    except Stop:
        pass
    adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
        "en,zh-pinyin,yue-jyutping,yue-yale,af", "--es", "current", "en")
