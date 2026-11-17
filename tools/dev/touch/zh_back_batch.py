"""B's re-run of zh-enter's '~eihou' (2026-11-17), for checks.py --batch zh-back: check_sym_panel, then check_enter alone,
with TitanKeysSym and TitanKeysIM kept (no log clearing between them) and Back on adb's `input keyevent 4`, a phone's own Back.
Right after the Back: input_method's window state and a shot say whether the panel is still drawn. Only TitanKeysSym and
TitanKeysIM lines are kept: key names and panel events, never typed text."""
import time

import checks
import chinese_checks
import guide_checks
from checks import adb, record

TAGS = ["TitanKeysSym", "TitanKeysIM"]


def lines():
    return [line for line in adb("logcat", "-d", "-v", "time", "-s", *TAGS).splitlines() if "TitanKeys" in line]


def check_back_then_enter():
    for tag in TAGS:
        adb("shell", "setprop", f"log.tag.{tag}", "D")
    chinese_checks.fresh()
    adb("logcat", "-c")
    chinese_checks.play("key:sym", "+700")
    checks.guarded("keyevent", "4")  # the phone's own Back, as a user's
    time.sleep(.8)
    ime = adb("shell", "dumpsys", "input_method")
    shown = [l.strip() for l in ime.splitlines() if "mInputShown" in l or "mWindowVisible" in l or "mDecorViewVisible" in l][:4]
    shot = guide_checks.shot("zh-back-after")
    after_back = lines()
    record("zh-back-panel", None, f"after Sym then Back: input_method {shown}; TitanKeysSym/IM: {after_back}", shot)
    chinese_checks.check_enter()
    record("zh-back-enter-log", None, f"through zh-enter: {lines()[len(after_back):]}")


BATCH = [check_back_then_enter]


def finish():
    for tag in TAGS:
        adb("shell", "setprop", f"log.tag.{tag}", "''")
    chinese_checks.finish()
