#!/usr/bin/env python3
"""Shared guards for the phone checks (check:phone). The phone may be someone's daily phone, in use while a check runs, so:

- every tap, key, Back or Home first checks that the app's own window is in front, and stops the check if it isn't;
- every typed key first checks that a field of the app's own is focused;
- the app's keyboard is chosen only around the steps that type in the app, and the keyboard that was chosen before comes back
  right after, even when a step fails.

APP=com.titankeys.debug checks the debug build instead. Run as a script, `phone.py home` presses Home only while the app is in
front, for shell callers such as tools/dev/build/verify.sh.
"""

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from contextlib import contextmanager

APP = os.environ.get("APP", "com.titankeys")
KEYBOARD = f"{APP}/com.titankeys.keyboard.TitanKeysIme"
DUMP = "/sdcard/titankeys-phone-check.xml"


class NotInFront(SystemExit):
    """The app's window isn't in front, so nothing is pressed: the key would reach another app."""


def shell(*args):
    return subprocess.check_output(["adb", "shell", *args], text=True)


def front_window():
    """The focused window's name, such as `com.titankeys.debug/com.vandam.ink.MainActivity`, or "" for none."""
    match = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([^}]*)\}", shell("dumpsys", "window"))
    return match.group(1) if match else ""


def in_front():
    return front_window().startswith(f"{APP}/")


def guard(action):
    """Stops the check unless the app's own window is in front. Waits a moment first, for a window that is still opening."""
    for _ in range(6):
        if in_front():
            return
        time.sleep(.5)
    raise NotInFront(f"STOP before {action}: {front_window() or 'no window'} is in front, not {APP}")


def hierarchy():
    shell("uiautomator", "dump", DUMP)
    try:
        return ET.fromstring(shell("cat", DUMP))
    finally:
        shell("rm", "-f", DUMP)


def fields(tree=None):
    """The app's own text fields on screen, as attribute maps."""
    tree = hierarchy() if tree is None else tree
    return [node.attrib for node in tree.iter("node")
            if node.attrib.get("class") == "android.widget.EditText" and node.attrib.get("package") == APP]


def field_with_hint(hint, tree=None):
    """The app's field showing [hint], or None. An empty field reports its hint as its text on older dumps."""
    return next((field for field in fields(tree) if hint in (field.get("hint"), field.get("text"))), None)


def guard_field(action):
    """Stops the check unless the app is in front with one of its own fields focused, so typing can't reach another app."""
    guard(action)
    if not any(field.get("focused") == "true" for field in fields()):
        raise NotInFront(f"STOP before {action}: no field of {APP} is focused")


def tap(x, y):
    guard(f"tap at {x},{y}")
    shell("input", "tap", str(x), str(y))
    time.sleep(.5)


def key(code):
    guard(f"key {code}")
    shell("input", "keyevent", str(code))


def back():
    key("KEYCODE_BACK")


def home():
    key("KEYCODE_HOME")


def type_text(text):
    """Types [text] into the app's focused field, one guarded key at a time. Letters and digits only, so no shell quoting."""
    assert re.fullmatch(r"[A-Za-z0-9]+", text), "type_text takes letters and digits only"
    for letter in text:
        guard_field(f"typing {letter!r}")
        shell("input", "text", letter)
    time.sleep(.5)


def delete(count):
    """Deletes [count] characters before the cursor: only as many as the check typed itself, never a selection."""
    for _ in range(count):
        guard_field("Backspace")
        shell("input", "keyevent", "KEYCODE_DEL")
    time.sleep(.5)


def open_page(href, wait=2):
    """Opens one of the app's own pages. No force-stop: it turns the keyboard and its Accessibility service off."""
    shell("am", "start", "-W", "-n", f"{APP}/com.vandam.ink.MainActivity", "--es", "com.vandam.ink.notification.HREF", href)
    time.sleep(wait)


def current_keyboard():
    return shell("settings", "get", "secure", "default_input_method").strip()


@contextmanager
def app_keyboard():
    """The app's keyboard for the steps inside, then the keyboard that was chosen before, whatever happens in between."""
    before = current_keyboard()
    if before != KEYBOARD:
        shell("ime", "enable", KEYBOARD)
        shell("ime", "set", KEYBOARD)
    try:
        yield
    finally:
        if before != KEYBOARD and before not in ("", "null"):
            shell("ime", "set", before)


if __name__ == "__main__":
    if sys.argv[1:] == ["home"]:
        home()
    else:
        raise SystemExit("usage: phone.py home")
