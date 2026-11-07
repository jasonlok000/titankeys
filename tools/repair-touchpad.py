#!/usr/bin/env python3
"""Repair the Titan 2 firmware's compressed touchpad mapping through ADB.

Open Atlas in portrait, connect the phone, and run this script. Use --check
to inspect the mapping without changing it. Calibration profiles are untouched.
"""

import argparse
import re
import subprocess
import time


def shell(*args):
    return subprocess.check_output(["adb", "shell", *args], text=True)


def mapping_agrees():
    sections = re.split(r"(?=  Device \d+:)", shell("dumpsys", "input"))
    pad = next(section for section in sections if re.match(r"  Device \d+: touchPad", section))
    if "InputDeviceOrientation: Rotation0" not in pad:
        raise SystemExit("Open Atlas in portrait before checking the touchpad")
    reported = float(re.search(r"Y: source=TOUCHPAD, min=0\.000, max=([\d.]+)", pad)[1])
    raw = float(re.search(r"Raw Touch Axes:.*?Y: min=0, max=(\d+)", pad, re.S)[1])
    transform = re.search(r"RawToDisplay Transform:[^\n]+\n\s+([\d. -]+)\n\s+([\d. -]+)", pad)
    mix, scale, offset = map(float, transform[2].split())
    if mix != 0:
        raise SystemExit("Touchpad transform is not in portrait")
    bottom = raw * scale + offset
    print(f"Touchpad bottom: {bottom:.2f}; reported bottom: {reported:.2f}", flush=True)
    return abs(bottom - reported) < 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check without repairing")
    args = parser.parse_args()
    if shell("getprop", "ro.product.device").strip() != "Titan_2":
        raise SystemExit("This repair is for the Titan 2")
    if mapping_agrees():
        print("PASS touchpad mapping is correct; nothing changed")
        return
    if args.check:
        raise SystemExit("FAIL firmware compresses touchpad positions before calibration")

    size = shell("wm", "size")
    override = re.search(r"Override size: (\d+x\d+)", size)
    original = override[1] if override else "reset"
    dimensions = override or re.search(r"Physical size: (\d+x\d+)", size)
    width, height = map(int, dimensions[1].split("x"))
    try:
        # A portrait configuration refresh rebuilds the transform using portrait height.
        shell("wm", "size", f"{width + 1}x{height}")
        time.sleep(.5)
    finally:
        shell("wm", "size", original)
    time.sleep(.5)
    if not mapping_agrees():
        raise SystemExit("FAIL display settings restored, but touchpad repair did not work")
    print("PASS touchpad mapping repaired; original display settings restored")


if __name__ == "__main__":
    main()
