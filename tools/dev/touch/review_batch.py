"""The review fixes on main 6f389de6, for checks.py --batch review-fixes.

1. C's Rime fix (9d26103c): Yale on and used, so Rime is open; then Cangjie on through InputMethodsSet and typing at the next
   field start, which comes from moving Debug's main screen and then its field to the front. hqi + Space types 我 (O), and Debug's process
   is the same one before and after (no keyboard restart).
2. B's quick-settings fix (a2157bef): French alone on, Fix typos off from the Sym panel's quick settings (Sym, Alt, F; the
   haptic says off). Then English back on alone: its Fix typos is still on. Settings' main page row (O where the dump reads its
   switch, else J by the shot), and "teh" + Space typing "the " (O). French's Fix typos is put back as it was, then the owner's
   languages.
"""
import re
import time

import checks
import chinese_checks
import guide_checks
import language_checks
import languages
import retest_batch
import switcher_checks
from checks import APP, Stop, adb, play, record

FIX_TYPOS = "Fix typos as you type"
MAIN_ACTIVITY = "com.vandam.ink.MainActivity"
QUICK_F = ["key:sym", "+700", "key:alt", "+700", "key:f", "+600", "key:sym", "+600"]  # Sym, quick settings, Fix, closed


def debug_pid():
    pid = adb("shell", "pidof", APP).strip()
    return pid if re.fullmatch(r"[1-9][0-9]*", pid) else None


def require_review_activity(activity):
    front = languages.front().strip() if APP.endswith(".debug") else ""
    if not front.endswith(f" u0 {APP}/{activity}}}"):
        raise Stop(f"the review check couldn't confirm Debug's {activity.rsplit('.', 1)[-1]} in front")
    return front


def field_start_again():
    """Move to Debug's main screen and back to its field to start input again."""
    require_review_activity(languages.FIELD_ACTIVITY)
    adb("shell", "am", "start", *languages.REORDER, "-n", f"{APP}/{MAIN_ACTIVITY}")
    time.sleep(2.5)
    require_review_activity(MAIN_ACTIVITY)
    adb("shell", "am", "start", *languages.REORDER, "-n", f"{APP}/{languages.FIELD_ACTIVITY}")
    time.sleep(1)
    require_review_activity(languages.FIELD_ACTIVITY)
    if not languages.focus_field():
        raise Stop("the review check couldn't focus Debug's Space gestures field")
    require_review_activity(languages.FIELD_ACTIVITY)


def review_editor():
    """Read the Debug process and the specific Space gestures window together."""
    try:
        window = require_review_activity(languages.FIELD_ACTIVITY)
    except Stop:
        return None
    pid = debug_pid()
    return (pid, window) if pid else None


def check_cangjie_after_yale():
    name = "cangjie-after-yale"
    yale = ["en", "yue-yale"]
    on = [*yale, "zh-cangjie"]
    language_checks.fresh()
    if not language_checks.turn_on(yale, "yue-yale"):
        return record(name, None, "Yale could not be verified in the sample field")
    language_checks.fresh()
    editor = review_editor()
    if editor is None:
        return record(name, None, "Debug's process and sample field could not be verified before Yale")
    play(*[f"key:{k}" for k in chinese_checks.CODE], "+500", "key:space", "+600")  # Yale used: Rime open
    used = checks.field()
    if not chinese_checks.cjk(used) or review_editor() != editor:
        return record(name, None, "Yale did not commit Chinese in the same Debug field")

    out = adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
              ",".join(on), "--es", "current", "zh-cangjie")
    if "typing zh-cangjie" not in out:
        return record(name, None, "Debug could not enable Cangjie")
    try:
        field_start_again()
    except Stop as error:
        return record(name, None, str(error))

    ids = languages.subtype_ids()
    for _ in range(10):
        if review_editor() != editor:
            return record(name, None, "Debug's process or sample field changed before Cangjie could be verified")
        if language_checks.active_method(on, ids) == "zh-cangjie":
            break
        time.sleep(.5)
    else:
        return record(name, None, "Cangjie could not be verified from the saved methods and Android's active subtype")

    if review_editor() != editor:
        return record(name, None, "Debug's process or sample field changed after Cangjie was verified")
    cleared = checks.set_text("")
    if cleared != "" or review_editor() != editor:
        return record(name, None, "The same Debug field could not be confirmed empty before Cangjie")
    text = chinese_checks.shot_then(name, ["key:h", "key:q", "key:i", "+500"], ["key:space", "+400"])
    if text is None or review_editor() != editor:
        return record(name, None, "The Cangjie result could not be read in the same Debug process and field", name)
    record(name, text.strip() == "我", f"Yale typed {used!r} in the same Debug process and field. "
           f"With Cangjie active after the field restart, hqi + Space typed {text!r} (wanted 我)", name)


def fix_typos_toggle():
    """Sym, Alt, F, Sym: the quick setting's haptic says which way it went ('quick_setting' on, 'quick_setting_off' off)."""
    since = switcher_checks.now()
    play(*QUICK_F)
    played, _ = switcher_checks.haptics_since(since)
    names = [name for name, _ in played]
    return "quick_setting_off" in names, "quick_setting" in names, played


def settings_fix_typos():
    """Settings' main page, English alone on: the Fix typos row's switch (True, False, or None if the dump doesn't show it)."""
    retest_batch.own_screen()
    adb("shell", "am", "start", "-n", f"{APP}/com.vandam.ink.MainActivity")
    time.sleep(3)
    try:
        if f" {APP}/" not in languages.front():
            raise Stop(f"TitanKeys Debug left the front: {languages.front().strip()}")
        retest_batch.open_settings(lambda *c: (adb("shell", "input", *c), time.sleep(1.5)))
        time.sleep(1.5)
        for _ in range(6):
            xml = switcher_checks.screen_dump()
            if FIX_TYPOS in xml:
                break
            if f" {APP}/" not in languages.front():
                raise Stop(f"TitanKeys Debug left the front: {languages.front().strip()}")
            adb("shell", "input", "swipe", "540", "950", "540", "450", "400")
            time.sleep(1.2)
        found = switcher_checks.row_switch(xml, FIX_TYPOS) if FIX_TYPOS in xml else None
        shot = None if found and found[2] else guide_checks.shot("english-fix-typos")
        return (found[2] if found else None), shot
    finally:
        for _ in range(4):
            if f" {APP}/" in languages.front() and not languages.in_field():
                adb("shell", "input", "keyevent", "4")
                time.sleep(1)


def check_french_quick_setting():
    language_checks.fresh()
    if not language_checks.turn_on(["fr"], "fr"):
        raise Stop("the field didn't start with French alone")
    language_checks.fresh()
    off, on, played = fix_typos_toggle()
    french_was_on = off
    if on and not off:  # it was off already: once more, so the check turns it off from on
        off, on, played = fix_typos_toggle()
    # No quick setting haptic at all: the Haptics lab's feed is off, so the haptics can't say (a LOOK, not a NO).
    record("french-fix-typos-off", off if (off or on) else None, f"French alone, Sym, Alt, F: haptics {played}")
    try:
        language_checks.fresh()
        if not language_checks.turn_on(["en"], "en"):
            raise Stop("the field didn't start with English alone")
        language_checks.fresh()
        play("key:t", "+90", "key:e", "+90", "key:h", "+90", "key:space", "+600")
        typed = checks.field()
        record("english-fix-typos-typing", typed == "the ", f"English, teh + Space typed {typed!r} (Fix typos on: 'the ')")
        row, shot = settings_fix_typos()
        record("english-fix-typos-row", row if row is not None else None,
               f"Settings' {FIX_TYPOS} row: {'on' if row else 'off' if row is False else 'not in the dump, see the shot'}", shot)
    finally:
        # French's own Fix typos as it was before the check.
        language_checks.fresh()
        if language_checks.turn_on(["fr"], "fr") and french_was_on:
            language_checks.fresh()
            fix_typos_toggle()
        language_checks.fresh()


BATCH = [check_cangjie_after_yale, check_french_quick_setting]


def finish():
    """The owner's languages as they were before the batches: English typing, Chinese's three, Afrikaans."""
    adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
        "en,zh-pinyin,yue-jyutping,yue-yale,af", "--es", "current", "en")
