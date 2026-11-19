"""B's steps for #85 after the fix (main a329c913, Composer's Identity holds the connection weakly), for checks.py --batch leak-85:
a LeakCheck after the playground checks, after 15 guide runs and after 15 more. Only if
something is held: a heap dump, pulled to the computer, tools/leak-paths.py on it, and the dump deleted on both sides. The dump is of the keyboard's process and can hold typed text: nothing here prints, greps or reads a string from it. Only
leak-paths.py's output, class and field names, is kept: $TK_OUT/touch/leak-paths-TIME.txt.
"""
import os
import re
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


# Whether any LeakCheck so far found a guide or playground still held: only then the dump.
held_seen = False
CLEAN = "no guide or playground in memory"
ACTIVITY = re.compile(r"(guide|playground) [0-9a-f]+: (destroyed, still held|finishing|open)(?:, held (?:by |from ).+)?")


def verdict(line, allow_open_playground=False):
    """Require a classified inventory. An open instance is evidence of lifecycle state, not a retained corpse."""
    inventory, marker, _ = line.partition("; input methods:")
    if not marker:
        return None
    if inventory == CLEAN:
        return True
    entries = [ACTIVITY.fullmatch(item) for item in inventory.split("; ")]
    if not all(entries):
        return None
    if any(item[2] == "destroyed, still held" for item in entries):
        return False
    if allow_open_playground and len(entries) == 1 and entries[0].groups() == ("playground", "open"):
        return True
    return None


def leakcheck(name, after, *, allow_open_playground=False):
    """A plain LeakCheck (no dump): YES when nothing is held. LeakCheck collects (gc and runFinalization, three times) before it
    answers, yet a guide it saw held at 12:35 on 2026-11-17 was gone by the dump a moment later: late finalization. So a held
    answer gets a second read 3 s on, after its own collection, and only one still held there is a NO."""
    global held_seen
    line = answer_of(adb("shell", "am", "broadcast", "-n", RECEIVER))
    first = line
    outcome = verdict(line, allow_open_playground)
    if outcome is not True:
        time.sleep(3)
        line = answer_of(adb("shell", "am", "broadcast", "-n", RECEIVER))
        outcome = verdict(line, allow_open_playground)
    held_seen = held_seen or outcome is False
    again = "" if first == line else f" (the first read, 3 s before: {first[:160]})"
    record(name, outcome, f"LeakCheck after {after}: {line[:400]}{again}")


def open_fixture():
    if not os.environ.get("TK_SAFE_RUNNER"):
        return retest_batch.own_screen()
    # Home focus deliberately leaves Playground open. Reorder Debug's known fixture.
    if not languages.in_field():
        raise Stop("the Debug test editor did not come to the front for the guide leak check")


def guide_runs(count=15):
    open_fixture()
    for _ in range(count):
        guide_checks.guide("steer")
        guide_checks.leave_guide()
        open_fixture()
    time.sleep(2)


def check_playground_then_leakcheck():
    """After B's fix (main a329c913): About, feedback and home-focus, then a LeakCheck: nothing may be held (O)."""
    retest_batch.check_phone()
    time.sleep(2)
    leakcheck("85-after-playground", "the playground checks", allow_open_playground=True)


def check_guides():
    """15 guide runs, a LeakCheck; 15 more, a LeakCheck again. Nothing held both times closes #85 (O)."""
    guide_runs()
    leakcheck("85-after-15-guides", "15 guide runs")
    guide_runs()
    leakcheck("85-after-30-guides", "a second 15 guide runs")


def check_dump_if_held():
    """Only if a LeakCheck found something held: a dump, pulled, leak-paths.py on it (names only), the dump deleted on both
    sides and confirmed."""
    if not held_seen:
        return record("85-dump", True, "no destroyed activity was confirmed retained: no dump taken")
    line = answer_of(adb("shell", "am", "broadcast", "-n", RECEIVER, "--ez", "dump", "true"))
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
        record("85-dump", None, f"LeakCheck: {line.split('; heap dump')[0][:300]}; leak-paths.py's output (exit "
               f"{result.returncode}) in {paths}")
    finally:
        shutil.rmtree(local, ignore_errors=True)
        adb("shell", "rm", "-r", LEAKS)
        phone_left = adb("shell", f"ls {LEAKS} >/dev/null 2>&1 && echo still there || echo gone").strip()
        record("85-dump-deleted", not os.path.exists(local) and phone_left == "gone",
               f"the dump on the phone: {phone_left}; on the computer: {'still there' if os.path.exists(local) else 'gone'}")


BATCH = [check_playground_then_leakcheck, check_guides, check_dump_if_held]
