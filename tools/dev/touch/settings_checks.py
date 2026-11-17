"""G's system-settings warnings (docs/system-settings.md), for checks.py --batch settings-warnings: screenshots of Debug's Settings
as the phone is, read only. On 2026-11-17 the phone has TitanKeys' Accessibility off, so the Scroll assistant block row shows its
warning and button. Nothing here changes a setting: it opens Settings and scrolls down the middle of the list (the switches sit
at the right), a screenshot a screen, and the rows the screen's accessibility tree names get a shot of their own.
"""
import time

import checks
import guide_checks
import languages
import retest_batch
from checks import APP, Stop, adb, record

ROWS = ["Scroll assistant", "Setup", "Swipe to type words"]
SCREENS = 12


def front():
    return languages.front()


def step(*command):
    if f" {APP}/" not in front():
        raise Stop(f"TitanKeys Debug left the front: {front().strip()}")
    adb("shell", "input", *command)
    time.sleep(1.5)


def check_settings_warnings():
    """Settings, top to bottom, a shot a screen; the three rows when found."""
    retest_batch.own_screen()
    adb("shell", "am", "start", "-n", f"{APP}/com.vandam.ink.MainActivity")
    time.sleep(3)
    retest_batch.open_settings(step)
    found = set()
    for n in range(SCREENS):
        name = guide_checks.shot(f"settings-{n:02d}")
        for row in ROWS:
            if row not in found and retest_batch.row_at(row):
                found.add(row)
                record(f"warning-{row.lower().replace(' ', '-')}", None, f"Settings: the {row} row and its line", name)
        step("swipe", "540", "1000", "540", "500", "400")
    record("settings-screens", None, f"Settings in {SCREENS} screens (settings-00 to -{SCREENS - 1:02d}); rows the screen named: "
           f"{sorted(found) or 'none (the shots show them)'}", "settings-00")
    for _ in range(3):
        if f" {APP}/" in front():
            adb("shell", "input", "keyevent", "4")
            time.sleep(1)


BATCH = [check_settings_warnings]
HANDS = []
