#!/usr/bin/env python3
"""Show what the phone's screen hides on a screenshot, and flag TitanKeys content that it would cut.

The screen's rounded corners and its camera hole hide part of every screenshot. This shades both, draws a safe line a margin
inside the corners, and marks in red any content in a corner or at the hole that falls outside the safe zone.

    python3 tools/corner_check.py IN.png OUT.png [radius] [margin] [--hole L,T,R,B] [--dumpsys FILE]

- radius: the screen's corner radius in pixels. Default 100, what the Titan 2 Elite reports.
- margin: how far inside the outline content should stay. Default 16.
- --hole: the camera hole's bounding box in screenshot pixels. The hole is masked as the ellipse inside that box.
- --dumpsys: a saved `adb shell dumpsys window`. The radius and the hole are read from its RoundedCorner and DisplayCutout lines,
  so another phone needs no numbers. A radius or --hole given on the command line wins.

Needs Pillow (pip install pillow). The background colour is sampled at the bottom middle of the screenshot.
"""
import argparse
import re
from PIL import Image, ImageChops, ImageDraw

parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
parser.add_argument("source")
parser.add_argument("out")
parser.add_argument("radius", nargs="?", type=int)
parser.add_argument("margin", nargs="?", type=int, default=16)
parser.add_argument("--hole", help="the camera hole's bounding box: left,top,right,bottom")
parser.add_argument("--dumpsys", help="a saved `adb shell dumpsys window`, to read the radius and the hole from")
args = parser.parse_args()

radius = args.radius
hole = None
if args.dumpsys:
    text = open(args.dumpsys, encoding="utf-8", errors="replace").read()
    radii = [int(value) for value in re.findall(r"RoundedCorner\{position=\w+, radius=(\d+)", text)]
    if radius is None and radii:
        radius = max(radii)
    bounds = re.search(r"boundingRect=\{Bounds=\[(.*?)\]\}", text)
    if bounds:
        rects = [tuple(map(int, found)) for found in re.findall(r"Rect\((-?\d+), (-?\d+) - (-?\d+), (-?\d+)\)", bounds.group(1))]
        rects = [rect for rect in rects if rect[2] > rect[0] and rect[3] > rect[1]]
        if rects:
            hole = rects[0]
if args.hole:
    hole = tuple(int(value) for value in args.hole.split(","))
if radius is None:
    radius = 100
margin = args.margin

img = Image.open(args.source).convert("RGB")
w, h = img.size

visible = Image.new("L", (w, h), 0)
ImageDraw.Draw(visible).rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=255)
safe = Image.new("L", (w, h), 0)
ImageDraw.Draw(safe).rounded_rectangle([margin, margin, w - 1 - margin, h - 1 - margin], radius=max(radius - margin, 0), fill=255)
if hole:
    left, top, right, bottom = hole
    ImageDraw.Draw(visible).ellipse([left, top, right, bottom], fill=0)
    ImageDraw.Draw(safe).ellipse([left - margin, top - margin, right + margin, bottom + margin], fill=0)

# Content is any pixel that differs clearly from the background colour.
background = img.getpixel((w // 2, h - 2))
content = ImageChops.difference(img, Image.new("RGB", (w, h), background)).convert("L").point(lambda v: 255 if v > 40 else 0)
risky = ImageChops.multiply(content, ImageChops.invert(safe))
areas = {"top left": (0, 0, radius, radius), "top right": (w - radius, 0, w, radius),
         "bottom left": (0, h - radius, radius, h), "bottom right": (w - radius, h - radius, w, h)}
if hole:
    left, top, right, bottom = hole
    areas["camera hole"] = (max(0, left - margin), max(0, top - margin), min(w, right + margin), min(h, bottom + margin))
for name, box in areas.items():
    count = sum(1 for value in risky.crop(box).getdata() if value)
    print(f"{name}: {count} content pixels outside the safe zone" + ("  <-- check" if count > 30 else ""))
print(f"radius {radius}, margin {margin}, hole {hole or 'none'}")

shown = Image.composite(img, Image.blend(img, Image.new("RGB", (w, h), (255, 0, 160)), 0.6), visible)
draw = ImageDraw.Draw(shown)
draw.rounded_rectangle([margin, margin, w - 1 - margin, h - 1 - margin], radius=max(radius - margin, 0), outline=(0, 255, 255), width=3)
if hole:
    left, top, right, bottom = hole
    draw.ellipse([left - margin, top - margin, right + margin, bottom + margin], outline=(0, 255, 255), width=3)
shown = Image.composite(Image.new("RGB", (w, h), (255, 0, 0)), shown, risky)
shown.save(args.out)
print("wrote", args.out)
