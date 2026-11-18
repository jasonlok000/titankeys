"""Re-tests for checks.py --batch re-tests: the app screens (check:phone: About, feedback, the playground's focus after the app
reopens), the keyboard's window (tests/ime-window), #85's leak check after a stress run of the guide, the typing log's own
checks, and Settings > Languages as a screenshot. Each tool runs as it does on its own, with Debug held and the lock passed on;
its exit code is the verdict and its last lines the detail."""
import os
import subprocess
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


def open_settings(step):
    """Open Settings through the same app route as notification links.

    Keep the callers' step parameter so existing batches can still pass their input guard.
    Opening this route needs no tap or accessibility label.
    """
    if not APP.endswith(".debug"):
        raise Stop("the Settings checks need TitanKeys Debug")
    if f" {APP}/" not in languages.front():
        raise Stop("TitanKeys Debug is not in front before opening Settings")
    activity = f"{APP}/com.vandam.ink.MainActivity"
    result = adb("shell", "am", "start", "-W", "-n", activity,
                 "--es", "com.vandam.ink.notification.HREF", "/settings")
    if "Status: ok" not in (line.strip() for line in result.splitlines()):
        raise Stop("Android did not confirm the TitanKeys Debug Settings launch")
    time.sleep(1.5)
    if activity not in languages.front().replace("}", " ").split():
        raise Stop("TitanKeys Debug's app window did not stay in front after opening Settings")


def check_languages_page():
    """Settings > Languages as a screenshot: the languages on at the top, then by script. Taps only while Debug is in front."""
    def step(*command):
        if f" {APP}/" not in languages.front():
            raise Stop(f"TitanKeys Debug left the front: {languages.front().strip()}")
        adb("shell", "input", *command)
        time.sleep(1.5)
    own_screen()
    adb("shell", "am", "start", "-n", f"{APP}/com.vandam.ink.MainActivity")
    time.sleep(3)
    # SETTINGS at the top right of the 1076 x 1200 display; then the Languages row, by its text where the screen shows it,
    # or where it sits since Languages came first in Settings (main 99fb0dcd): the first row, under its heading.
    open_settings(step)
    x, y = row_at("Languages") or (540, 452)
    step("tap", str(x), str(y))
    record("settings-languages", None, "Settings > Languages: the ones on at the top, then by script", guide_checks.shot("settings-languages"))
    for _ in range(3):
        if f" {APP}/" in languages.front():
            adb("shell", "input", "keyevent", "4")
            time.sleep(1)


BATCH = [check_phone, check_ime_window, check_rawlog, check_85_leaks, check_languages_page]
HANDS = []
