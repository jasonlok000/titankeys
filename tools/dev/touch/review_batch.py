"""The review fixes on main 6f389de6, for checks.py --batch review-fixes.

1. C's Rime fix (9d26103c): Yale on and used, so Rime is open; then Cangjie on through InputMethodsSet and typing at the next
   field start, which comes from moving Debug's main screen and then its field to the front. hqi + Space types 我 (O), and Debug's process
   is the same one before and after (no keyboard restart).
2. B's quick-settings fix (a2157bef): French alone on, Fix typos off from the Sym panel's quick settings (Sym, Alt, F; the
   haptic says off). Then English back on alone: its Fix typos is still on. Settings' main page row (O where the dump reads its
   switch, else J by the shot), and "teh" + Space typing "the " (O). French's Fix typos is put back as it was, then the owner's
   languages.
"""
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
    return adb("shell", "pidof", APP).strip()


def require_review_activity(activity):
    if not APP.endswith(".debug") or not languages.front().strip().endswith(f" u0 {APP}/{activity}}}"):
        raise Stop(f"the review check couldn't confirm Debug's {activity.rsplit('.', 1)[-1]} in front")


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


def check_cangjie_after_yale():
    language_checks.fresh()
    if not language_checks.turn_on(["en", "yue-yale"], "yue-yale"):
        raise Stop("the field didn't start with Yale")
    language_checks.fresh()
    play(*[f"key:{k}" for k in chinese_checks.CODE], "+500", "key:space", "+600")  # Yale used: Rime open
    used = checks.field()
    pid = debug_pid()
    out = adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
              "en,yue-yale,zh-cangjie", "--es", "current", "zh-cangjie")
    if "typing zh-cangjie" not in out:
        raise Stop(f"InputMethodsSet didn't take zh-cangjie: {out.strip()[-200:]}")
    adb("logcat", "-c")
    field_start_again()
    started = any("field start: zh-cangjie (" in line for line in languages.log_lines())
    checks.set_text("")
    text = chinese_checks.shot_then("cangjie-after-yale", ["key:h", "key:q", "key:i", "+500"], ["key:space", "+400"])
    same = debug_pid() == pid
    record("cangjie-after-yale", (text or "").strip() == "我" and same and started,
           f"Yale typed {used!r} first; field start with zh-cangjie: {started}; hqi + Space typed {text!r} (wanted 我); "
           f"Debug's process {'the same' if same else 'restarted'}", "cangjie-after-yale")


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
