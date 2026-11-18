"""Reproduce Sym → Android Back → Yale code → Enter in Debug's Space gestures field.

Keep field and IME state at each step. Only the test's own text and the Sym/IM diagnostic lines go in the report.
"""
import re
import shlex
import time

import bar_observations as observed
import checks
import chinese_checks
import language_checks
import languages
from checks import adb, record

YALE = chinese_checks.YALE
METHODS = ["en", YALE]
TAGS = ("TitanKeysSym", "TitanKeysIM")


def debug_pid():
    pid = adb("shell", "pidof", checks.APP).strip()
    return pid if re.fullmatch(r"[1-9][0-9]*", pid) else None


def trace_lines(raw, marker, pid):
    """Recognize the Android time header and keep only this attempt's Debug messages."""
    tags = "|".join((*TAGS, observed.MARK_TAG))
    header = re.compile(rf"\d{{2}}-\d{{2}} \d{{2}}:\d{{2}}:\d{{2}}\.\d+ [VDIWEFAS]/({tags})\s*\(\s*(\d+)\s*\):\s*(.*)")
    fresh = False
    events = []
    for line in raw.splitlines():
        match = header.fullmatch(line)
        if not match:
            continue
        tag, source, message = match.groups()
        if tag == observed.MARK_TAG and message == marker:
            fresh = True
        elif fresh and source == pid and tag in TAGS:
            events.append((tag, message))
    return events


def lines(attempt, pid):
    marker, since = attempt
    raw = adb("logcat", "-d", "-v", "time", "-T", since, "-s", observed.MARK_TAG + ":I", *TAGS, "*:S")
    return trace_lines(raw, marker, pid) if debug_pid() == pid else []


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
        pid = debug_pid()
        if pid is None:
            return record("zh-back-setup", None, "Debug's process could not be verified")
        try:
            attempt = observed.mark_attempt()
        except observed.Unobservable as error:
            return record("zh-back-setup", None, str(error))
        chinese_checks.play("key:sym", "+700")
        if field_at("after opening Sym", window) != "":
            record("zh-back-setup", None, "Sym changed the sample text before Back")
            return
        sym_lines = [message for tag, message in lines(attempt, pid) if tag == "TitanKeysSym"]
        handled = [message for message in sym_lines if message.startswith("Sym handled: panel now ")]
        opened = f"Sym handled: panel now Symbols, shown true (app {checks.APP}, editing true)"
        if not handled or handled[-1] != opened:
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
        record("zh-back-window", shown if shown_before is True else None,
               f"Android Back left the same Debug field focused. IME shown before: {shown_before}, after: {shown}")
        method = language_checks.active_method(METHODS, ids)
        if method != YALE:
            record("zh-back-method", None, f"Yale could not be verified after Back (active method: {method or 'unavailable'})")
            return
        record("zh-back-field", after_back == "", "the field is empty after Back" if not after_back else "Back changed the empty field")

        if debug_pid() != pid:
            return record("zh-back-method", None, "Debug's process changed before typing the Yale code")
        window_at("before the Yale code", window)
        chinese_checks.play(*chinese_checks.code())
        before_enter = field_at("after the Yale code, before Enter", window)
        record("zh-back-code", before_enter == "", f"the code should stay on the bar until Enter. Field: {before_enter!r}")
        chinese_checks.play("key:enter", "+400")
        text = field_at("after Enter", window)
        record("zh-enter", text == chinese_checks.CODE, f"typed {text!r}, wanted {chinese_checks.CODE!r}")
        trace = [f"{tag}: {message}" for tag, message in lines(attempt, pid)]
        record("zh-back-trace", None, f"from Sym through Enter: {trace}")
        if text == chinese_checks.CODE:
            checks.set_text("")
    finally:
        for tag, value in tags.items():
            adb("shell", "setprop", f"log.tag.{tag}", shlex.quote(value))


BATCH = [check_back_then_enter]
