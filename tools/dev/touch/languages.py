#!/usr/bin/env python3
"""The input methods on the phone (#56), typed with the phone's own keys through the touch simulator, each read back.

For each input method asked for, which must already be on in TitanKeys Debug's Settings > Input methods:
1. TitanKeys Debug becomes the keyboard, in its Space gestures field, after the gate (touchlib.sh).
2. Alt+Sym, pressed on the real keyboard, steps through Android's keyboard list until its entry for that input method is the
   current one (`selected_input_method_subtype`, read back after each step; TitanKeys lists one entry per input method that is
   on). Since main 4658247, a pick there is what types.
3. The input method's test is typed, key by key, and the text it put in the field is read back.
4. Release TitanKeys goes back as the keyboard, read back.
The expected text comes from the input method's own keys in android/assets/im/<id>/im.json, or, for an engine, from the phone
checks list (docs: m3-phone-checks.md, section 2). Every key and every switch goes through touch.sh or guarded.sh and their gate.

Usage: languages.py [--list] [ID...]   (default: the ids in TESTS)
       languages.py --expect ID        prints the test's keys and expected text, without the phone
A screenshot of the bar after each test goes to $TK_OUT/touch/shots/lang-ID.png.
"""
import json
import os
import re
import subprocess
import sys
import time
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import checks  # noqa: E402  (the gate, the keyboard, the field and the lock)

REPO = os.path.normpath(os.path.join(HERE, "../../.."))

# What each input method types for its test. "keys" are the keys pressed in order (a letter, "space", "del", or "shift+w");
# "expect" is the text the field must gain, or None to work it out from im.json's keys.
TESTS = {
    "es": {"keys": "n i n o space", "expect": None, "note": "Latin letters stay Latin; ñ is held N's accent (not typed here)"},
    "de": {"keys": "strasse space", "expect": None, "note": "Latin letters stay Latin; ß and umlauts are accents"},
    "ru": {"keys": "qwerty space", "expect": None},
    "el": {"keys": "ertyuiop space", "expect": None},
    "sr": {"keys": "q y z space", "expect": "љзж "},
    "mk": {"keys": "z space", "expect": None},
    "kk": {"keys": "q space", "expect": "й "},
    "ka": {"keys": "q shift+w space", "expect": "ქჭ "},
    "ko": {"keys": "g k s r n r d j space", "expect": "한국어 "},
    "ko-backspace": {"im": "ko", "keys": "g k s del space", "expect": "하 ", "note": "Backspace mid-syllable takes one jamo"},
    "vi": {"keys": "t i e e n g s space", "expect": "tiếng "},
}


def im_json(im):
    with open(os.path.join(REPO, "android/assets/im", im, "im.json"), encoding="utf-8") as file:
        return json.load(file)


def subtype_ids():
    """Android's subtype id for each input method, from input_method.xml."""
    with open(os.path.join(REPO, "android/src/main/res/xml/input_method.xml"), encoding="utf-8") as file:
        text = file.read()
    return {int(sid): im for im, sid in re.findall(r'im=([a-z_-]+)" android:subtypeId="(-?\d+)"', text)}


def keys_of(name):
    """The test's keys, one each: a word like "qwerty" is its letters; space, del and chords like shift+w stay whole."""
    out = []
    for token in TESTS[name]["keys"].split():
        out += [token] if token in ("space", "del") or "+" in token else list(token)
    return out


def expected(name):
    test = TESTS[name]
    if test["expect"] is not None:
        return test["expect"]
    keys = im_json(test.get("im", name))["keys"]
    out = ""
    for key in keys_of(name):
        if key == "space":
            out += " "
        elif key.startswith("shift+"):
            value = keys.get(key[6:], key[6:]) if isinstance(keys, dict) else key[6:]
            out += value.split("/")[1] if "/" in value else value.upper()
        else:
            value = keys.get(key, key) if isinstance(keys, dict) else key
            out += value.split("/")[0]
    return out


def gestures(name):
    out = []
    for key in keys_of(name):
        out += ([f"chord:{key}"] if "+" in key else [f"key:{key}:60:none"]) + ["+90"]
    return out[:-1]


def adb(*args):
    return checks.adb(*args).strip()


def current_subtype():
    value = adb("shell", "settings", "get", "secure", "selected_input_method_subtype")
    try:
        return int(value)
    except ValueError:
        return None


def inserted(before, after):
    """The text the test added: what's left of `after` once the text it shares with `before` at each end is taken off."""
    start = 0
    while start < min(len(before), len(after)) and before[start] == after[start]:
        start += 1
    end = 0
    while end < min(len(before), len(after)) - start and before[-1 - end] == after[-1 - end]:
        end += 1
    return after[start:len(after) - end]


PICKER = "Select input method"
DEBUG_LABEL = "TitanKeys Debug"


def picker_entries():
    """Android's input method list, read from its views: [(label, keyboard, (x, y))] in order. The dump is deleted at once."""
    path = "/data/local/tmp/tk-ui.xml"
    adb("shell", "uiautomator", "dump", path)
    xml = adb("shell", "cat", path)
    adb("shell", "rm", "-f", path)
    texts = []
    for text, x0, y0, x1, y1 in re.findall(r'text="([^"]+)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        texts.append((text, ((int(x0) + int(x1)) // 2, (int(y0) + int(y1)) // 2)))
    return [(label, texts[i + 1][0], at) for i, (label, at) in enumerate(texts[:-1])]


def switch_to(im, ids):
    """Picks im's entry in Android's input method list, as a person does: TitanKeys opens the list for Alt+Sym on this phone
    (not the next keyboard). Only an entry of TitanKeys Debug's own is tapped, only while the list is in front, and Android's
    current entry is read back after."""
    if ids.get(current_subtype()) == im:
        return True
    # Android's list and im.json can write the same name in different Unicode forms ("Tiếng Việt"): compared in one form.
    info = im_json(im)
    names = {unicodedata.normalize("NFC", info["name"]), unicodedata.normalize("NFC", info.get("nameEnglish", ""))}
    checks.play("chord:alt+sym")
    time.sleep(1.2)
    for _ in range(4):
        if PICKER not in front():
            return False
        match = [at for label, keyboard, at in picker_entries()
                 if unicodedata.normalize("NFC", label) in names and keyboard == DEBUG_LABEL]
        if match:
            if PICKER not in front():
                return False
            adb("shell", "input", "tap", str(match[0][0]), str(match[0][1]))
            time.sleep(1.5)
            return ids.get(current_subtype()) == im
        adb("shell", "input", "swipe", "540", "1000", "540", "550", "400")
        time.sleep(1)
    if PICKER in front():
        adb("shell", "input", "keyevent", "4")
    return False


# The field the tests type in: TitanKeys Debug's Space gestures screen. Never the guide's or the playground's, which type English
# whatever input method is on (englishOnly = inGuide || inPractice), and never another app's. The gate brings it back too.
FIELD_ACTIVITY = "com.titankeys.keyboard.SpaceGestureActivity"
os.environ["TK_TOUCH_ACTIVITY"] = FIELD_ACTIVITY
APP = os.environ.get("TK_APP", "com.titankeys.debug")
# TitanKeys Debug's own log lines about input methods ("Input method at field start: ru (the saved setting)", "… loaded: N words,
# engine none", "… didn't load (…): typing English") and about Sym. Ids and counts only, never text.
LOG_TAGS = ["TitanKeysIM", "TitanKeysSym"]


def front():
    return next((line for line in adb("shell", "dumpsys", "window").splitlines() if "mCurrentFocus" in line), "")


def in_field():
    """The Space gestures screen is in front: opened when TitanKeys Debug is, never over another app."""
    if FIELD_ACTIVITY in front():
        return True
    if f" {APP}/" in front():
        adb("shell", "am", "start", "-n", f"{APP}/{FIELD_ACTIVITY}")
        time.sleep(2)
    return FIELD_ACTIVITY in front() and focus_field()


def focus_field():
    """The screen's text field has the keyboard: tapped if it lost it, as after a list was open."""
    path = "/data/local/tmp/tk-ui.xml"
    adb("shell", "uiautomator", "dump", path)
    xml = adb("shell", "cat", path)
    adb("shell", "rm", "-f", path)
    for node in re.findall(r"<node [^>]*>", xml):
        if f'package="{APP}"' in node and "EditText" in node:
            if 'focused="true"' in node:
                return True
            x0, y0, x1, y1 = (int(v) for v in re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
            if FIELD_ACTIVITY not in front():
                return False
            # Its middle: a corner can be padding, which takes no tap.
            adb("shell", "input", "tap", str((x0 + x1) // 2), str((y0 + y1) // 2))
            time.sleep(1)
            return True
    return False


def log_lines():
    return [line for line in adb("logcat", "-d", "-v", "brief", "-s", *LOG_TAGS).splitlines() if "TitanKeys" in line]


def logs_on(on):
    """This firmware keeps apps' info lines out of the log (log.tag is M); each tag's own property lets it through. A debug
    property, not a setting: gone at the next restart, and cleared at the end of the run."""
    for tag in LOG_TAGS:
        adb("shell", "setprop", f"log.tag.{tag}", "I" if on else "''")
    return all(adb("shell", "getprop", f"log.tag.{tag}") == "I" for tag in LOG_TAGS) if on else True


def run(name, ids):
    im = TESTS[name].get("im", name)
    want = expected(name)
    checks.keyboard("debug")
    os.environ["TK_IME_HELD"] = "1"
    seen = len(log_lines())
    try:
        if not in_field():
            return checks.record(f"lang-{name}", None, f"not in the Space gestures field: front is {front().strip()}")
        if not switch_to(im, ids):
            now = ids.get(current_subtype(), current_subtype())
            return checks.record(f"lang-{name}", None, f"Alt+Sym didn't reach {im} (Android's entry: {now}): is it on?")
        if not in_field():
            return checks.record(f"lang-{name}", None, f"left the Space gestures field while switching: {front().strip()}")
        before = checks.field()
        checks.play(*gestures(name), shot=f"lang-{name}", at=len(gestures(name)) * 75)
        time.sleep(.8)
        got = inserted(before, checks.field())
        note = f" ({TESTS[name]['note']})" if "note" in TESTS[name] else ""
        said = [line.split(":", 1)[-1].strip() for line in log_lines()[seen:] if "TitanKeysIM" in line][-3:]
        log = "; the log: " + " | ".join(said) if said else "; the log said nothing about input methods"
        checks.record(f"lang-{name}", got == want, f"typed {got!r}, wanted {want!r}{note}{log}", f"lang-{name}")
    finally:
        os.environ.pop("TK_IME_HELD", None)
        checks.keyboard("release")


def main():
    args = sys.argv[1:]
    if args == ["--list"]:
        for name in TESTS:
            print(f"{name:14} keys {TESTS[name]['keys']!r:28} expect {expected(name)!r}")
        return
    if args[:1] == ["--expect"]:
        for name in args[1:]:
            print(name, gestures(name), repr(expected(name)))
        return
    ids = subtype_ids()
    if os.environ.get("TK_PHONE_LOCKED") != "1":
        held = checks.lock()  # noqa: F841 (kept open until the end)
    if not logs_on(True):
        print("note: the shell couldn't let TitanKeysIM and TitanKeysSym through the log; the lines will say nothing", flush=True)
    try:
        for name in args or TESTS:
            run(name, ids)
    except checks.Stop as stop:
        print(f"STOP: the run ended: {stop}", flush=True)
        sys.exit(1)
    finally:
        logs_on(False)
    verdicts = [v for _, v in checks.results]
    print(f"{verdicts.count('YES')} YES, {verdicts.count('NO')} NO, {verdicts.count('LOOK')} UNSURE")
    sys.exit(1 if "NO" in verdicts else 0)


if __name__ == "__main__":
    main()
