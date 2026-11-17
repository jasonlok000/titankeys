#!/usr/bin/env python3
"""Finds what grows in TitanKeys Debug's heap (2026-11-17: OutOfMemoryError twice in the languages batch). Runs the languages
batch in the background and reads LeakCheck's heap line every 30 s. Past --dump-mb it stops the batch, takes a heap dump, pulls
it, prints tools/heap-histogram.py's top classes (names and numbers only) and deletes the dump on both sides at once.

    python3 tools/dev/touch/heap_watch.py --label log-on --minutes 6 --dump-mb 150
    python3 tools/dev/touch/heap_watch.py --label log-off --minutes 3 --typing-log off   # the log off first, back on after

--typing-log off|on sets TitanKeys Debug's own Settings > Typing log > Record my typing (an app screen, not a system setting)
through taps while Debug is in front, read back from the screen. Run inside the phone lock, Debug held as the keyboard.
"""
import argparse
import os
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import checks  # noqa: E402
import languages  # noqa: E402
import retest_batch  # noqa: E402
import switcher_checks  # noqa: E402
from checks import APP, HERE, adb  # noqa: E402

RECEIVER = f"{APP}/com.titankeys.keyboard.LeakCheck"
LEAKS = f"/sdcard/Android/data/{APP}/files/leaks"
REPO = os.path.abspath(os.path.join(HERE, "../../.."))


def heap_mb():
    out = adb("shell", "am", "broadcast", "-n", RECEIVER)
    found = re.search(r"heap (\d+) of (\d+) MB", out)
    return int(found.group(1)) if found else None


def typing_log(on):
    """Settings > Typing log > Record my typing set to [on], read back. Taps only while Debug is in front."""
    def step(*command):
        if f" {APP}/" not in languages.front():
            raise checks.Stop(f"TitanKeys Debug left the front: {languages.front().strip()}")
        adb("shell", "input", *command)
        time.sleep(1.5)
    retest_batch.own_screen()
    adb("shell", "am", "start", "-n", f"{APP}/com.vandam.ink.MainActivity")
    time.sleep(3)
    try:
        retest_batch.open_settings(step)
        row = None
        for _ in range(8):
            row = retest_batch.row_at("Typing log")
            if row:
                break
            step("swipe", "540", "950", "540", "350", "400")
        if not row:
            return f"no Typing log row"
        step("tap", str(row[0]), str(row[1]))
        found = switcher_checks.row_switch(switcher_checks.screen_dump(), "Record my typing")
        if not found:
            return "no Record my typing switch in the dump"
        if found[2] != on:
            step("tap", str(found[0]), str(found[1]))
            found = switcher_checks.row_switch(switcher_checks.screen_dump(), "Record my typing")
        return f"Record my typing {'on' if found and found[2] else 'off'}"
    finally:
        for _ in range(4):
            if f" {APP}/" in languages.front() and not languages.in_field():
                adb("shell", "input", "keyevent", "4")
                time.sleep(1)


def dump_histogram():
    """A heap dump, pulled, histogram printed, deleted on both sides. Returns the histogram's text."""
    adb("shell", "rm", "-rf", LEAKS)
    adb("shell", "am", "broadcast", "-n", RECEIVER, "--ez", "dump", "true")
    time.sleep(20)  # the dump is written off the main thread
    local = tempfile.mkdtemp(prefix="tk-heap-")
    try:
        adb("pull", LEAKS, local)
        dumps = [os.path.join(root, f) for root, _, files in os.walk(local) for f in files if f.endswith(".hprof")]
        if not dumps:
            return "no .hprof came"
        out = subprocess.run([sys.executable, os.path.join(REPO, "tools/heap-histogram.py"), dumps[0], "--top", "25"],
                             capture_output=True, text=True)
        return out.stdout + out.stderr[-300:]
    finally:
        shutil.rmtree(local, ignore_errors=True)
        adb("shell", "rm", "-rf", LEAKS)
        left = adb("shell", "ls", LEAKS).strip()
        print(f"the dump deleted: phone {'gone' if not left or 'No such' in left else 'STILL THERE'}, "
              f"computer {'gone' if not os.path.exists(local) else 'STILL THERE'}", flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--label", required=True)
    parser.add_argument("--minutes", type=float, default=3)
    parser.add_argument("--dump-mb", type=int, default=0)
    parser.add_argument("--typing-log", choices=["on", "off"])
    args = parser.parse_args()
    if args.typing_log:
        print(f"{time.strftime('%T')} typing log: {typing_log(args.typing_log == 'on')}", flush=True)
    batch = subprocess.Popen([sys.executable, os.path.join(HERE, "checks.py"), "--batch", "languages"],
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
    start, samples = time.time(), []
    try:
        while time.time() - start < args.minutes * 60 and batch.poll() is None:
            time.sleep(30)
            mb = heap_mb()
            samples.append((int(time.time() - start), mb))
            print(f"{time.strftime('%T')} {args.label} +{samples[-1][0]} s: heap {mb} MB", flush=True)
            if args.dump_mb and mb and mb >= args.dump_mb:
                break
    finally:
        if batch.poll() is None:
            os.killpg(batch.pid, signal.SIGTERM)
            batch.wait(30)
    known = [(t, mb) for t, mb in samples if mb is not None]
    if len(known) >= 2:
        slope = (known[-1][1] - known[0][1]) / ((known[-1][0] - known[0][0]) / 60)
        print(f"{args.label} slope: {slope:.1f} MB a minute ({known[0][1]} MB at +{known[0][0]} s to {known[-1][1]} MB at "
              f"+{known[-1][0]} s)", flush=True)
    if args.dump_mb and known and known[-1][1] >= args.dump_mb:
        print(dump_histogram(), flush=True)


if __name__ == "__main__":
    main()
