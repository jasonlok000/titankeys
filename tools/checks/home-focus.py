#!/usr/bin/env python3
"""Check home focus, retained text, and Settings on the Titan 2's 1076 x 1200 display.

Opens Atlas home if it isn't on screen. If the field is empty, the check enters a
common word and clears it before navigating to Settings. APP=com.atlas.debug checks
the debug build instead.
"""

import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

HINT = "Swipe a word on the keyboard"
APP = os.environ.get("APP", "com.atlas")


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


def launch(href=None):
    link = ["--es", "com.vandam.ink.notification.HREF", href] if href else []
    shell("am", "start", "-a", "android.intent.action.MAIN",
          "-c", "android.intent.category.LAUNCHER", "-n", f"{APP}/com.vandam.ink.MainActivity", *link)
    time.sleep(1)


def tap(x, y):
    shell("input", "tap", str(x), str(y))
    time.sleep(.5)


def main():
    try:
        editor()
    except StopIteration:
        # Never force-stop Atlas: Android then changes the keyboard and turns off Atlas in Accessibility.
        launch("/")
        for _ in range(10):
            try:
                editor()
                break
            except StopIteration:
                time.sleep(1)
    keyboard = shell("settings", "get", "secure", "default_input_method").strip()
    if not keyboard.startswith(f"{APP}/"):
        raise SystemExit(f"SKIP: {APP} is not the current keyboard ({keyboard}), so Atlas shows setup, not home")
    initial = editor()
    assert initial["focused"] == "true", "Home did not regain focus"
    original = initial["text"]
    inserted = not original
    if inserted:
        shell("input", "text", "hello")
        time.sleep(.8)
    before = editor()["text"]
    assert not inserted or before.lower() == "hello", "Test text was not entered"

    tap(500, 950)
    after = editor()
    assert after["focused"] == "true" and after["text"] == before, "Diagram refocus changed text"
    print("PASS diagram refocus preserves nonempty text", flush=True)

    shell("input", "keyevent", "HOME")
    time.sleep(.8)
    launch()
    after = editor()
    assert after["focused"] == "true" and after["text"] == before, "App return lost text or focus"
    print("PASS app return preserves nonempty text and focus", flush=True)
    if inserted:
        shell("input", "keycombination", "113", "29")
        shell("input", "keyevent", "67")
        time.sleep(.5)
        assert editor()["text"] == original, "Could not restore the original home text"

    tap(955, 190)
    assert not any(node.attrib.get("hint") == HINT for node in hierarchy().iter("node")), "Settings did not open"
    ime = shell("dumpsys", "input_method").partition("mCurrentEditorInfo:")[2]
    assert "inputType=0x0 " in ime, "Home retained its editor connection in Settings"
    windows = re.split(r"(?=  Window #)", shell("dumpsys", "window", "windows"))
    window = next((w for w in windows if "u0 InputMethod}:" in w), "")
    assert "isVisible=true" not in window, "Atlas stayed visible in Settings"
    print("PASS Settings opens without the home editor or keyboard", flush=True)
    tap(970, 190)
    assert editor()["focused"] == "true", "Closing Settings did not focus the home field"
    print("PASS closing Settings restores home field focus", flush=True)


if __name__ == "__main__":
    main()
