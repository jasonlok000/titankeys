"""B's steps for #85, for checks.py --batch leak-85 (2026-11-17): the playground checks, a plain LeakCheck, 15 guide runs as in
the batch that found the leak, then a heap dump, pulled to the computer, tools/leak-paths.py on it, and the dump deleted on both
sides. The dump is of the keyboard's process and can hold typed text: nothing here prints, greps or reads a string from it. Only
leak-paths.py's output, class and field names, is kept: $TK_OUT/touch/leak-paths-TIME.txt.
"""
import os
import shutil
import subprocess
import time

import guide_checks
import languages
import retest_batch
from checks import APP, Stop, adb, record

LEAKS = f"/sdcard/Android/data/{APP}/files/leaks"
RECEIVER = f"{APP}/com.titankeys.keyboard.LeakCheck"


def answer_of(out):
    return out.strip().split("data=")[-1].strip('"')


def check_playground_then_leakcheck():
    """1. About, feedback and home-focus, as the batch that found the leak ran them; then a plain LeakCheck, its line saved."""
    retest_batch.check_phone()
    time.sleep(2)
    line = answer_of(adb("shell", "am", "broadcast", "-n", RECEIVER))
    record("85-after-playground", None, f"LeakCheck after the playground checks: {line[:400]}")


def check_guides_then_dump():
    """2-7. 15 guide runs; a dump; pulled; leak-paths.py; the dump deleted on the phone and on the computer, both confirmed."""
    for _ in range(15):
        retest_batch.own_screen()
        guide_checks.guide("steer")
        guide_checks.leave_guide()
    time.sleep(2)
    line = answer_of(adb("shell", "am", "broadcast", "-n", RECEIVER, "--ez", "dump", "true"))
    # The answer names the dump's file; only its name is used, never its contents.
    stamp = time.strftime("%Y%m%d-%H%M%S")
    out = os.path.join(os.environ.get("TK_OUT", os.path.expanduser("~/tk-out")), "touch")
    local = os.path.join(out, f"leaks-{stamp}")
    paths = os.path.join(out, f"leak-paths-{stamp}.txt")
    try:
        adb("pull", LEAKS, local)
        dumps = [os.path.join(root, f) for root, _, files in os.walk(local) for f in files if f.endswith(".hprof")]
        if not dumps:
            return record("85-dump", None, f"no .hprof came: LeakCheck said {line.split(';')[0][:300]}")
        result = subprocess.run(["python3", os.path.join(languages.REPO, "tools/leak-paths.py"), dumps[0]],
                                capture_output=True, text=True)
        with open(paths, "w", encoding="utf-8") as f:
            f.write(result.stdout + result.stderr)
        record("85-dump", None, f"after 15 guides, LeakCheck: {line.split('; heap dump')[0][:300]}; leak-paths.py's "
               f"output (exit {result.returncode}) in {paths}")
    finally:
        shutil.rmtree(local, ignore_errors=True)
        adb("shell", "rm", "-r", LEAKS)
        phone_left = adb("shell", f"ls {LEAKS} >/dev/null 2>&1 && echo still there || echo gone").strip()
        record("85-dump-deleted", not os.path.exists(local) and phone_left == "gone",
               f"the dump on the phone: {phone_left}; on the computer: {'still there' if os.path.exists(local) else 'gone'}")


BATCH = [check_playground_then_leakcheck, check_guides_then_dump]
