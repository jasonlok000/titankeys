"""Reproduce Sym → Android Back → Yale code → Enter in Debug's Space gestures field.

Keep field and IME state at each step. Only the test's own text and the Sym/IM diagnostic lines go in the report.
"""
import re
import shlex
import time

import checks
import chinese_checks
import language_checks
import languages
from checks import adb, record

YALE = chinese_checks.YALE
METHODS = ["en", YALE]
TAGS = ("TitanKeysSym", "TitanKeysIM")


def lines():
    return [line for line in adb("logcat", "-d", "-v", "time", "-s", *TAGS).splitlines()
            if any(tag + ":" in line for tag in TAGS)]


def window_at(step, expected=None):
    if not checks.APP.endswith(".debug"):
        raise checks.Stop(f"{step}: the probe needs TitanKeys Debug")
    front = languages.front()
    if f" {checks.APP}/{languages.FIELD_ACTIVITY}" not in front:
        raise checks.Stop(f"{step}: Debug's Space gestures field isn't in front")
    if expected is not None and front != expected:
        raise checks.Stop(f"{step}: the Debug window changed")
    return front


def field_at(step, window=None):
    window_at(step, window)
    try:
        return checks.field()
    except checks.Stop as error:
        raise checks.Stop(f"{step}: {error}") from error


def input_shown():
    state = set(re.findall(r"\bmInputShown=(true|false)\b", adb("shell", "dumpsys", "input_method")))
    return next(iter(state)) == "true" if len(state) == 1 else None


def check_back_then_enter():
    field_at("before Yale setup")
    tags = {tag: adb("shell", "getprop", f"log.tag.{tag}").strip() for tag in TAGS}
    try:
        language_checks.turn_on(METHODS, YALE)
        ids = languages.subtype_ids()
        for tag in TAGS:
            adb("shell", "setprop", f"log.tag.{tag}", "D")
        method = language_checks.active_method(METHODS, ids)
        if method != YALE:
            record("zh-back-setup", None, f"Yale start could not be verified (active method: {method or 'unavailable'})")
            return
        field_at("after Yale setup")
        chinese_checks.fresh()
        if field_at("after clearing the field") != "":
            record("zh-back-setup", None, "Yale is active, but the sample field did not clear")
            return

        window = window_at("before Sym")
        before_sym = set(lines())
        chinese_checks.play("key:sym", "+700")
        if field_at("after opening Sym", window) != "":
            record("zh-back-setup", None, "Sym changed the sample text before Back")
            return
        sym_lines = [line for line in lines() if line not in before_sym and "TitanKeysSym:" in line]
        handled = [line for line in sym_lines if "Sym handled: panel now " in line]
        if not handled or "panel now Symbols, shown true" not in handled[-1]:
            record("zh-back-setup", None, f"Yale is active, but the visible Symbols panel could not be verified: {sym_lines}")
            return
        record("zh-back-setup", True, f"Yale is active, the field is empty, and Sym opened Symbols: {handled[-1]}")
        shown_before = input_shown()
        window_at("before Android Back", window)
        checks.guarded("keyevent", "4")
        time.sleep(.8)
        try:
            after_back = field_at("after Android Back", window)
        except checks.Stop as error:
            record("zh-back-window", None, str(error))
            raise
        shown = input_shown()
        after_back_log = lines()
        record("zh-back-window", shown if shown_before is True else None,
               f"Android Back left the same Debug field focused. IME shown before: {shown_before}, after: {shown}")
        method = language_checks.active_method(METHODS, ids)
        if method != YALE:
            record("zh-back-method", None, f"Yale could not be verified after Back (active method: {method or 'unavailable'})")
            return
        record("zh-back-field", after_back == "", "the field is empty after Back" if not after_back else "Back changed the empty field")

        window_at("before the Yale code", window)
        chinese_checks.play(*chinese_checks.code())
        before_enter = field_at("after the Yale code, before Enter", window)
        record("zh-back-code", before_enter == "", f"the code should stay on the bar until Enter. Field: {before_enter!r}")
        chinese_checks.play("key:enter", "+400")
        text = field_at("after Enter", window)
        record("zh-enter", text == chinese_checks.CODE, f"typed {text!r}, wanted {chinese_checks.CODE!r}")
        known_by_back = set(after_back_log)
        later = [line for line in lines() if line not in known_by_back]
        after_sym = [line for line in after_back_log if line not in before_sym]
        record("zh-back-trace", None, f"through Android Back: {after_sym}. Through Enter: {later}")
        if text == chinese_checks.CODE:
            checks.set_text("")
    finally:
        for tag, value in tags.items():
            adb("shell", "setprop", f"log.tag.{tag}", shlex.quote(value))


BATCH = [check_back_then_enter]
