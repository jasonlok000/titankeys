#!/usr/bin/env python3
"""rec-frames.py: turns the pieces rec-start.sh recorded into contact sheets, one frame every 3 s.

Only frames taken while TitanKeys was in front (by focus.log) are kept; every other frame is deleted unseen, so other
apps' screens are never looked at. The status bar (top 123 px on the Titan 2 Elite) is cropped off.
Reads and writes $TK_OUT/rec. Needs ffmpeg on PATH (or TK_FFMPEG) and Pillow."""
import bisect
import glob
import os
import pathlib
import subprocess

from PIL import Image, ImageDraw

FFMPEG = os.environ.get("TK_FFMPEG", "ffmpeg")
D = pathlib.Path(os.environ.get("TK_OUT") or pathlib.Path.home() / "tk-out") / "rec"
F = D / "frames"
F.mkdir(parents=True, exist_ok=True)
STATUS_BAR = 123
STEP = 3
PER_SHEET = 16

pieces = {int(a): int(b) for a, b in (line.split() for line in open(D / "pieces.log"))}
focus = []
for line in open(D / "focus.log"):
    parts = line.split()
    focus.append((int(parts[0]), parts[1] if len(parts) > 1 else ""))
times = [f[0] for f in focus]
t0 = pieces[1]


def titankeys_in_front(t):
    i = bisect.bisect_right(times, t) - 1
    if i < 0 or i + 1 >= len(focus):
        return False
    return focus[i][1].startswith("com.titankeys") and focus[i + 1][1].startswith("com.titankeys")


kept = []
for n, start in sorted(pieces.items()):
    mp4 = D / f"tk-rec-{n}.mp4"
    if not mp4.exists():
        continue
    out = str(F / f"p{n}-%04d.png")
    crop = f"fps=1/{STEP},crop=iw:ih-{STATUS_BAR}:0:{STATUS_BAR},scale=240:-1"
    subprocess.run([FFMPEG, "-v", "error", "-i", str(mp4), "-vf", crop, out], check=True)
    for path in sorted(glob.glob(str(F / f"p{n}-*.png"))):
        k = int(path[-8:-4]) - 1
        t = start + 700 + int((k + 0.5) * STEP * 1000)
        if titankeys_in_front(t):
            kept.append(((t - t0) / 1000, path))
        else:
            os.remove(path)  # never looked at: not TitanKeys
print("kept", len(kept))
for s in range(0, len(kept), PER_SHEET):
    group = kept[s:s + PER_SHEET]
    w, h = Image.open(group[0][1]).size
    sheet = Image.new("RGB", (4 * (w + 4), 4 * (h + 18)), "white")
    draw = ImageDraw.Draw(sheet)
    for i, (sec, path) in enumerate(group):
        x, y = (i % 4) * (w + 4), (i // 4) * (h + 18)
        sheet.paste(Image.open(path), (x, y + 16))
        draw.text((x + 2, y + 2), f"{sec:.0f}s", fill="black")
    sheet.save(D / f"sheet-{s // PER_SHEET:02d}.png")
print("sheets", (len(kept) + PER_SHEET - 1) // PER_SHEET)
