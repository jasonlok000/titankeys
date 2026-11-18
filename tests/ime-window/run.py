#!/usr/bin/env python3
"""Check TitanKeys visibility when returning to an app with a focused text field, and after Back.

Focus the affected field on a connected Android device, then run:
    python3 tests/ime-window/run.py org.mozilla.firefox
The check does not type, submit forms, or change keyboard settings.
"""

import argparse
import os
import re
import subprocess
import sys
import time


DEBUG = "com.titankeys.debug"
SPACE = "com.titankeys.keyboard.SpaceGestureActivity"
KEEP_EDITOR = "com.titankeys.debug.keepEditorOnBack"


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
        "package=com.titankeys" in window and "isVisible=true" in window,
    )


def prepare_debug(package):
    """Use the launcher playground before measuring its return, not the synthetic test editor."""
    if package != "com.titankeys.debug":
        return
    focus = shell("dumpsys", "window")
    current = next((line for line in focus.splitlines() if "mCurrentFocus" in line), "")
    assert f" {package}/" in current, "STOP: Debug must be in front before opening its playground"
    shell("am", "start", "-W", "-n", f"{package}/com.vandam.ink.MainActivity",
          "--es", "com.vandam.ink.notification.HREF", "/")
    for _ in range(12):
        focus = shell("dumpsys", "window")
        current = next((line for line in focus.splitlines() if "mCurrentFocus" in line), "")
        assert f" {package}/" in current, "STOP: Debug lost the foreground during playground setup"
        if "/com.titankeys.keyboard.GuideActivity" in current:
            editor, input_type, _ = state()
            if editor == package and input_type & 0xF == 1:
                return
        time.sleep(.5)
    raise AssertionError("Debug playground did not obtain a focused text editor")


def same_editor(package, actual_type, expected_package, expected_type):
    assert (package, actual_type) == (expected_package, expected_type), (
        "The focused field changed during the check: "
        f"expected {expected_package} type=0x{expected_type:x}, "
        f"got {package} type=0x{actual_type:x}")


def debug_back_stays_in_editor():
    """Use the Debug recorder's one-shot Back fixture to keep the same field in view."""
    def focus():
        return next((line for line in shell("dumpsys", "window").splitlines() if "mCurrentFocus" in line), "")

    assert f" {DEBUG}/" in focus(), "STOP: Debug must be in front before the Back fixture"
    shell("am", "start", "-W", "-n", f"{DEBUG}/{SPACE}", "--ez", KEEP_EDITOR, "true")
    for _ in range(12):
        assert f" {DEBUG}/" in focus(), "STOP: Debug lost the foreground during Back setup"
        package, input_type, visible = state()
        if package == DEBUG and input_type & 0xF == 1 and visible and f"/{SPACE}" in focus():
            break
        time.sleep(.5)
    else:
        raise AssertionError("Back fixture did not obtain a focused editor and visible TitanKeys bar")

    assert f" {DEBUG}/{SPACE}" in focus(), "STOP: Back fixture lost focus"
    shell("input", "keyevent", "BACK")
    time.sleep(2)
    assert f" {DEBUG}/{SPACE}" in focus(), "Back closed the keep-editor fixture"
    package, actual_type, visible = state()
    same_editor(package, actual_type, DEBUG, input_type)
    assert not visible, "TitanKeys remained or came back after Back in the same field"
    print("PASS back: TitanKeys stayed hidden in the same field", flush=True)


def main():
    # The maintainer's phone lock, if there is one (tools/phone_lock.py).
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../tools"))
    import phone_lock
    phone_lock.hold()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package", help="App containing the focused text field")
    parser.add_argument("--cycles", type=int, default=12)
    parser.add_argument("--same-field", action="store_true", help="Check Back with the TitanKeys Debug keep-editor fixture")
    args = parser.parse_args()
    assert args.cycles > 0, "cycles must be positive"
    assert not args.same_field or args.package == DEBUG, "--same-field requires TitanKeys Debug"
    selected = shell("settings", "get", "secure", "default_input_method").strip()
    assert selected.startswith("com.titankeys") and selected.endswith("TitanKeysIme"), "Select TitanKeys before running"
    prepare_debug(args.package)
    package, input_type, _ = state()
    assert package == args.package and input_type & 0xF == 1, "Focus a text field in the specified app first"
    # A Debug build also has an icon for the recorder, so one match can't be asked for. The app's main screen is first.
    components = [line.strip() for line in shell(
        "cmd", "package", "query-activities", "--brief",
        "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", args.package,
    ).splitlines() if line.strip().startswith(args.package + "/")]
    assert components, "App has no launcher activity"
    component = components[0]

    for cycle in range(args.cycles):
        delay = (0, .1, .8)[cycle % 3]
        shell("input", "keyevent", "HOME")
        time.sleep(delay)
        if delay == .8:
            assert not state()[2], "TitanKeys remained visible on the home screen"
        shell("am", "start", "-a", "android.intent.action.MAIN",
              "-c", "android.intent.category.LAUNCHER", "-n", component)
        time.sleep(1)
        # An app can bring its field back a moment later, as TitanKeys does when it opens its playground again.
        for _ in range(6):
            package, actual_type, visible = state()
            if (package, actual_type) == (args.package, input_type):
                break
            time.sleep(.5)
        same_editor(package, actual_type, args.package, input_type)
        if not visible:
            time.sleep(2)
            package, actual_type, visible = state()
            same_editor(package, actual_type, args.package, input_type)
        assert visible, f"TitanKeys window stayed hidden after return {cycle + 1} (home delay {delay}s)"
        print(f"PASS return {cycle + 1}: TitanKeys visible after {delay}s home delay", flush=True)

    if args.same_field:
        debug_back_stays_in_editor()
        return
    # Back also reaches the app, which can close the screen. Only a field that stays can show the result.
    shell("input", "keyevent", "BACK")
    time.sleep(2)
    package, actual_type, visible = state()
    if (package, actual_type) != (args.package, input_type):
        print("SKIP back: the app left the field", flush=True)
        return
    assert not visible, "TitanKeys window came back after Back hid it"
    print("PASS back: TitanKeys stayed hidden in the same field", flush=True)


if __name__ == "__main__":
    main()
