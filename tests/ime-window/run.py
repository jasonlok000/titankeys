#!/usr/bin/env python3
"""Check Atlas visibility when returning to an app with a focused text field.

Focus the affected field on a connected Android device, then run:
    python3 tests/ime-window/run.py org.mozilla.firefox
The check does not type, submit forms, or change keyboard settings.
"""

import argparse
import re
import subprocess
import time


def shell(*args):
    return subprocess.check_output(["adb", "shell", *args], text=True)


def state():
    ime = shell("dumpsys", "input_method")
    client = ime.partition("mCurrentEditorInfo:")[2].split("mServedInputConnection=")[0]
    package = re.search(r"packageName=([^\s]+)", client)
    input_type = re.search(r"inputType=0x([0-9a-f]+)", client)
    windows = re.split(r"(?=  Window #)", shell("dumpsys", "window", "windows"))
    window = next((w for w in windows if "u0 InputMethod}:" in w), "")
    return (
        package.group(1) if package else None,
        int(input_type.group(1), 16) if input_type else 0,
        "package=com.atlas " in window and "isVisible=true" in window,
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package", help="App containing the focused text field")
    parser.add_argument("--cycles", type=int, default=12)
    args = parser.parse_args()
    assert args.cycles > 0, "cycles must be positive"
    selected = shell("settings", "get", "secure", "default_input_method").strip()
    assert selected == "com.atlas/.keyboard.AtlasIme", "Select Atlas before running"
    package, input_type, _ = state()
    assert package == args.package and input_type & 0xF == 1, "Focus a text field in the specified app first"
    component = shell(
        "cmd", "package", "resolve-activity", "--brief",
        "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", args.package,
    ).strip().splitlines()[-1]
    assert component.startswith(args.package + "/"), "App has no launcher activity"

    for cycle in range(args.cycles):
        delay = (0, .1, .8)[cycle % 3]
        shell("input", "keyevent", "HOME")
        time.sleep(delay)
        if delay == .8:
            assert not state()[2], "Atlas remained visible on the home screen"
        shell("am", "start", "-a", "android.intent.action.MAIN",
              "-c", "android.intent.category.LAUNCHER", "-n", component)
        time.sleep(1)
        package, actual_type, visible = state()
        assert (package, actual_type) == (args.package, input_type), "The focused field changed during the check"
        if not visible:
            time.sleep(2)
            package, actual_type, visible = state()
            assert (package, actual_type) == (args.package, input_type), "The focused field changed during the check"
        assert visible, f"Atlas window stayed hidden after return {cycle + 1} (home delay {delay}s)"
        print(f"PASS return {cycle + 1}: Atlas visible after {delay}s home delay", flush=True)


if __name__ == "__main__":
    main()
