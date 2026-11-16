#!/usr/bin/env python3
"""crop.py RAW.png STATUSBAR_BOTTOM IME_TOP mInputShown=true|false

Crops the status bar off a raw screenshot, and the keyboard window when it is shown, then writes NAME.png and
NAME-half.png beside it (NAME is RAW without its "raw-" prefix) and deletes the raw file, so it is never kept."""
import os
import sys

from PIL import Image

raw, sb, ime, vis = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
folder, base = os.path.split(raw)
name = os.path.splitext(base)[0].removeprefix("raw-")
image = Image.open(raw)
width, height = image.size
bottom = ime if ("true" in vis and 0 < ime < height) else height
image = image.crop((0, sb, width, bottom))
image.save(os.path.join(folder, f"{name}.png"))
image.resize((image.width // 2, image.height // 2), Image.LANCZOS).save(os.path.join(folder, f"{name}-half.png"))
os.remove(raw)
print(name, "size", width, height, "crop", sb, bottom, vis)
