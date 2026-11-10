#!/usr/bin/env python3
"""Check the playground, the app's front page, on the Titan 2's 1076 x 1200 display: its field takes focus, keeps its text
when you leave the app and come back, Settings opens without the field or the keyboard, and closing Settings brings the
playground back with its field focused.

Opens TitanKeys if the playground isn't on screen. APP=com.titankeys.debug checks the debug build instead.
"""

import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

HINT = "Your words go here."
APP = os.environ.get("APP", "com.titankeys")


def shell(*args):
    return subprocess.check_output(["adb", "shell", *args], text=True)


def hierarchy():
    shell("uiautomator", "dump", "/sdcard/atlas-home-focus-check.xml")
    return ET.fromstring(shell("cat", "/sdcard/atlas-home-focus-check.xml"))


def editor():
    return next(
        node.attrib for node in hierarchy().iter("node")
        if node.attrib.get("class") == "android.widget.EditText"
        and node.attrib.get("hint") == HINT
    )


def wait_for_editor(tries=12):
    for _ in range(tries):
        try:
            return editor()
        except StopIteration:
            time.sleep(1)
    raise SystemExit("FAIL: the playground did not open")


def centre(text):
    """The middle of the first view that shows [text]."""
    for node in hierarchy().iter("node"):
        if node.attrib.get("text") == text:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
            return (x1 + x2) // 2, (y1 + y2) // 2
    raise SystemExit(f"FAIL: nothing on screen shows {text!r}")


def launch():
    shell("am", "start", "-a", "android.intent.action.MAIN",
          "-c", "android.intent.category.LAUNCHER", "-n", f"{APP}/com.vandam.ink.MainActivity")
    time.sleep(1)


def tap(x, y):
    shell("input", "tap", str(x), str(y))
    time.sleep(.5)


def main():
    try:
        editor()
    except StopIteration:
        # No force-stop: that turns off the keyboard and its Accessibility service until the user turns them on again.
        launch()
        time.sleep(2)
    # A force-stopped keyboard hands the phone to another keyboard until its app runs again, so this is read after the launch.
    keyboard = shell("settings", "get", "secure", "default_input_method").strip()
    if not keyboard.startswith(f"{APP}/"):
        raise SystemExit(f"SKIP: {APP} is not the current keyboard ({keyboard}), so TitanKeys shows setup, not the playground")
    initial = wait_for_editor()
    assert initial["focused"] == "true", "The playground's field did not take focus"
    original = initial["text"]
    inserted = not original
    if inserted:
        shell("input", "text", "hello")
        time.sleep(.8)
    before = editor()["text"]
    assert not inserted or before.lower() == "hello", "Test text was not entered"

    shell("input", "keyevent", "HOME")
    time.sleep(.8)
    launch()
    after = wait_for_editor()
    assert after["focused"] == "true" and after["text"] == before, "App return lost text or focus"
    print("PASS app return keeps the playground's text and focus", flush=True)
    if inserted:
        shell("input", "keycombination", "113", "29")
        shell("input", "keyevent", "67")
        time.sleep(.5)
        assert editor()["text"] == original, "Could not restore the original playground text"

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


if __name__ == "__main__":
    main()
