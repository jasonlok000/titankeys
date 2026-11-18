"""Re-tests for checks.py --batch re-tests: the app screens (check:phone: About, feedback, the playground's focus after the app
reopens), the keyboard's window (tests/ime-window), #85's leak check after a stress run of the guide, the typing log's own
checks, and Settings > Languages as a screenshot. Each tool runs as it does on its own, with Debug held and the lock passed on;
its exit code is the verdict and its last lines the detail."""
import os
import subprocess
import re
import time

import checks
import guide_checks
import languages
from checks import APP, Stop, adb, record

REPO = languages.REPO


def own_screen():
    """Debug in front again (a tool may leave the launcher there): the gate brings its screen back, then its field."""
    guide_checks.leave_guide()
    checks.to_field()
    checks.keyboard("debug")
    if not languages.in_field():
        raise Stop(f"not on TitanKeys Debug's own screen: {languages.front().strip()}")


def run_tool(name, *command):
    own_screen()
    build = os.environ.get("TK_BUILD", os.path.expanduser("~/build"))
    # As the build copies have them: rg in tk-tools, Kotlin in the build folder.
    env = dict(os.environ, APP=APP, TK_PHONE_LOCKED="1", TK_IME_HELD="1",
               PATH=os.path.join(build, "tk-tools") + os.pathsep + os.environ.get("PATH", ""))
    env.setdefault("KOTLIN_HOME", os.path.join(build, "kotlin-2.1.20", "kotlinc"))
    result = subprocess.run(list(command), cwd=REPO, env=env, capture_output=True, text=True)
    tail = " | ".join((result.stdout + result.stderr).strip().splitlines()[-3:])[-300:]
    record(name, result.returncode == 0, f"exit {result.returncode}: {tail}")


def check_phone():
    """check:phone, part by part: About, feedback, and the playground's focus after the app reopens."""
    run_tool("about", "bash", "tools/checks/about.sh")
    run_tool("feedback", "bash", "tools/checks/feedback.sh")
    run_tool("home-focus", "python3", "tools/checks/home-focus.py")


def check_ime_window():
    """The keyboard shows and hides with fields (tests/ime-window)."""
    run_tool("ime-window", "python3", "tests/ime-window/run.py", APP)


def check_rawlog():
    """The typing log's own checks (tests/rawlog), on the computer."""
    run_tool("rawlog", "bash", "tests/rawlog/run.sh")


def check_85_leaks():
    """#85: the guide opened and left 15 times, then LeakCheck: no guide or playground may stay in memory."""
    for _ in range(15):
        own_screen()
        guide_checks.guide("steer")
        guide_checks.leave_guide()
    time.sleep(2)
    answer = adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.LeakCheck")
    report = answer.strip().split("data=")[-1].strip('"')[:300]
    record("85-leaks", "no guide or playground in memory" in answer, f"after 15 guides: {report}")


def row_at(text):
    """The middle of the first view whose text is exactly text, or None (the dump is deleted at once)."""
    path = "/data/local/tmp/tk-touch-ui.xml"
    adb("shell", "uiautomator", "dump", path)
    xml = adb("shell", "cat", path)
    adb("shell", "rm", "-f", path)
    import re
    found = re.search(r'text="' + re.escape(text) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    if not found:
        return None
    x0, y0, x1, y1 = (int(v) for v in found.groups())
    return (x0 + x1) // 2, (y0 + y1) // 2


def settings_button():
    """TitanKeys' own SETTINGS on its main screen, found by its text (any case), or None. A tap by position hit another row on
    561eb1de's screen and opened an Android Settings page (2026-11-17)."""
    path = "/data/local/tmp/tk-touch-ui.xml"
    adb("shell", "uiautomator", "dump", path)
    xml = adb("shell", "cat", path)
    adb("shell", "rm", "-f", path)
    for node in re.findall(r"<node [^>]*>", xml):
        if f'package="{APP}"' not in node:
            continue
        label = (re.search(r'text="([^"]*)"', node) or re.search(r'content-desc="([^"]*)"', node))
        if label and label.group(1).strip().casefold() == "settings":
            x0, y0, x1, y1 = (int(v) for v in re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
            return (x0 + x1) // 2, (y0 + y1) // 2
    return None


def open_settings(step):
    """Taps TitanKeys' SETTINGS by its text through [step] (which checks Debug is in front); stops if it isn't there."""
    found = settings_button()
    if not found:
        raise Stop("no SETTINGS on TitanKeys Debug's main screen to tap")
    step("tap", str(found[0]), str(found[1]))


def check_languages_page():
    """Confirm the current accessible Languages page, then keep its visual check."""
    from page_probe import Missing, PageUI, Unobservable
    own_screen()
    try:
        ui = PageUI(checks)
        ui.open_list()
        record('settings-languages-open', True, 'Languages page limit text found after navigation')
        record('settings-languages', None, 'Languages: enabled first, then script; review layout',
               guide_checks.shot('settings-languages'))
    except Missing as error:
        record('settings-languages', False, str(error))
    except Unobservable as error:
        record('settings-languages', None, str(error))


BATCH = [check_phone, check_ime_window, check_rawlog, check_85_leaks, check_languages_page]
HANDS = []
