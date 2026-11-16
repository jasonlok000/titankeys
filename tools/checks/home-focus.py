#!/usr/bin/env python3
"""Check the playground, the app's front page, on the Titan 2's 1076 x 1200 display: its field takes focus, keeps its text
when you leave the app and come back, Settings opens without the field or the keyboard, and closing Settings brings the
playground back with its field focused.

It opens the front page itself, chooses the app's keyboard only for its own steps and gives the phone its keyboard back after,
types only into the playground's own field, and deletes only what it typed: never a select-all. Every press goes through
tools/checks/phone.py's guards, which stop the check if another app comes to the front. APP=com.titankeys.debug checks the
debug build instead.
"""

import re
import time
from pathlib import Path

from phone import APP, app_keyboard, delete, field_with_hint, guard, hierarchy, home, open_page, shell, tap, type_text

# The playground's hint, read from the app so that a new wording can't leave this check looking for the old one.
GUIDE = Path(__file__).resolve().parents[2] / "android/kotlin/com/titankeys/keyboard/GuideActivity.kt"
HINT = re.search(r'FIELD_HINT = "([^"]+)"', GUIDE.read_text(encoding="utf-8")).group(1)
TYPED = "hello"


def editor():
    """The playground's field, or None."""
    return field_with_hint(HINT)


def wait_for_editor(tries=12):
    for _ in range(tries):
        found = editor()
        if found:
            return found
        time.sleep(1)
    raise SystemExit("FAIL: the playground did not open")


def text_of(field):
    """The field's own text: an empty field reports its hint as its text."""
    return "" if field.get("text") in ("", HINT) else field.get("text", "")


def centre(text):
    """The middle of the first view of the app's that shows [text]."""
    for node in hierarchy().iter("node"):
        if node.attrib.get("text") == text and node.attrib.get("package") == APP:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
            return (x1 + x2) // 2, (y1 + y2) // 2
    raise SystemExit(f"FAIL: nothing on screen shows {text!r}")


def launch():
    """Opens the app as the launcher does, which brings back the screen you left."""
    shell("am", "start", "-a", "android.intent.action.MAIN",
          "-c", "android.intent.category.LAUNCHER", "-n", f"{APP}/com.vandam.ink.MainActivity")
    time.sleep(1)


def check():
    # The front page, which opens the playground. No force-stop: that turns the keyboard and its Accessibility service off.
    open_page("/", wait=3)
    initial = wait_for_editor()
    guard("reading the playground")
    assert initial["focused"] == "true", "The playground's field did not take focus"
    # Its own text only: a field that already holds text is left as it is, and the check types nothing into it.
    typed = TYPED if text_of(initial) == "" else ""
    if typed:
        type_text(typed)
    before = text_of(editor())
    assert not typed or before.lower() == typed, "Test text was not entered"

    home()
    time.sleep(.8)
    launch()
    after = wait_for_editor()
    assert after["focused"] == "true" and text_of(after) == before, "App return lost text or focus"
    print("PASS app return keeps the playground's text and focus", flush=True)
    if typed:
        delete(len(typed))
        assert text_of(editor()) == "", "Could not take out the test text"

    tap(*centre("Settings"))
    time.sleep(1)
    assert not any(node.attrib.get("hint") == HINT for node in hierarchy().iter("node")), "Settings did not open"
    ime = shell("dumpsys", "input_method").partition("mCurrentEditorInfo:")[2]
    assert "inputType=0x0 " in ime, "The playground's editor stayed connected in Settings"
    windows = re.split(r"(?=  Window #)", shell("dumpsys", "window", "windows"))
    window = next((w for w in windows if "u0 InputMethod}:" in w), "")
    assert "isVisible=true" not in window, "TitanKeys stayed visible in Settings"
    print("PASS Settings opens without the playground's field or keyboard", flush=True)
    tap(970, 190)
    assert wait_for_editor()["focused"] == "true", "Closing Settings did not bring back the playground with its field focused"
    print("PASS closing Settings brings back the playground with its field focused", flush=True)

    # A link that opens the app's own screen closes the playground over it. The front page opens it again.
    shell("am", "start", "-W", "-n", f"{APP}/com.vandam.ink.MainActivity")
    assert wait_for_editor()["focused"] == "true", "The playground did not come back after the app's own screen opened"
    print("PASS the playground comes back after the app's own screen opens over it", flush=True)


def main():
    # The app's keyboard only for these steps; the phone's own keyboard comes back after, even when a step fails.
    with app_keyboard():
        check()


if __name__ == "__main__":
    main()
