"""The Chinese data's first use, for checks.py --batch first-use, right after a slot's install (B, 2026-11-17): Yale, then Pinyin,
then Japanese, each turned on and typed one letter; TitanKeysIM's "Input method ID loaded in N ms" read back as a number (O).
Debug's data size before and after, from Android's `dumpsys diskstats` (TitanKeys Debug isn't debuggable, so run-as and du on its
data folder aren't open; diskstats' sizes are the system's last count). An install keeps Debug's data: whether it was a new APK
decides whether this is the first use; the slot's log says.
"""
import re
import time

import language_checks
from checks import adb, play, record

METHODS = ["yue-yale", "zh-pinyin", "ja"]


def data_size():
    """Debug's line in diskstats' app data sizes, or what diskstats says."""
    out = adb("shell", "dumpsys", "diskstats")
    names = re.search(r'Package Names: \[([^\]]*)\]', out)
    sizes = re.search(r'App Data Sizes: \[([^\]]*)\]', out)
    if not names or not sizes:
        return "diskstats gave no app data sizes"
    names = [n.strip().strip('"') for n in names.group(1).split(",")]
    sizes = [s.strip() for s in sizes.group(1).split(",")]
    for name, size in zip(names, sizes):
        if name == language_checks.checks.APP:
            return f"{int(size) / 1048576:.1f} MiB ({size} bytes)"
    return "Debug not in diskstats' list"


def check_first_use():
    record("first-use-data-before", None, f"Debug's data before: {data_size()}")
    for im in METHODS:
        language_checks.languages.logs_on(True)
        adb("logcat", "-c")
        language_checks.fresh()
        language_checks.turn_on(["en", im], im)
        language_checks.fresh()
        play("key:n", "+500")
        loaded = None
        for _ in range(60):
            lines = [line for line in language_checks.languages.log_lines() if "TitanKeysIM" in line]
            found = next((re.search(rf"Input method {re.escape(im)} loaded in (\d+) ms", line) for line in lines
                          if re.search(rf"Input method {re.escape(im)} loaded in (\d+) ms", line)), None)
            if found:
                loaded = int(found.group(1))
                break
            time.sleep(1)
        record(f"first-use-{im}", loaded is not None,
               f"Input method {im} loaded in {loaded} ms" if loaded is not None else f"no 'loaded in' line for {im} in 60 s")
        play("key:del", "+300", "key:del", "+300")
    record("first-use-data-after", None, f"Debug's data after: {data_size()}")


BATCH = [check_first_use]
