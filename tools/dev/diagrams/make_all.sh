#!/bin/sh
# Draws the paper's 18 animated diagrams (docs/paper.md) into the repo's assets/readme, or into GIF_OUT if it is set.
# Needs Python 3 with matplotlib, numpy and Pillow. Every input is in the repo, so no machine settings are needed.
# Usage: sh tools/dev/diagrams/make_all.sh        PYTHON picks the interpreter (default python3).
cd "$(dirname "$0")" || exit 1
for script in animate.py stretch_gif.py matcher_gifs.py jason_gif.py delete_gif.py extras_gifs.py; do
    "${PYTHON:-python3}" -B "$script" || exit 1
done
