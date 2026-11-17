"""The keyboard's heap as LeakCheck reads it, one line, for checks.py --batch heap: before and after a batch to watch it grow
(2026-11-17's OutOfMemoryError in the languages batch). LeakCheck collects first; no dump."""
from checks import APP, adb, record


def check_heap():
    out = adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.LeakCheck")
    line = out.strip().split("data=")[-1].strip('"')
    heap = next((part.strip() for part in line.split(",") if "heap" in part), "no heap in the answer")
    record("heap", None, f"{heap}; LeakCheck: {line[:300]}")


BATCH = [check_heap]
