"""Expecting a thumb to stop short (jason-why), in TitanKeys 2.1's look. The swipe is simulated (synth_word.py, fixed seeds, from
the recorded session's measured shortfalls); the corner rule is SwipeShapes.pullCorners. Usage: python jason_gif.py"""
import math

from matplotlib.patches import Circle, Rectangle

from look21 import (BLACK, CREAM, DOTS, KEY, KEY_EDGE, KEY_LETTER, KEY_LIT, KEY_LIT_EDGE, MARGIN, MUTED, ORANGE, WIDTH, Canvas,
                    Header, Legend, Reel, Sizes, pt)
from swipes import CENTRES, rejoined
from synth_word import simulated

SHOWN = 520
# The same swipe as "python synth_word.py make jason jain" writes: jason, 0.75 keys short, seed 0.
entry = next(e for e in simulated(["jason", "jain"])[0] if e.get("prompt") == "jason" and e.get("pace") == "short0.75")
swipe = [(s[0] * 10, s[1] * 4, s[2]) for event in entry["events"] for s in event["s"]]
turn = max(swipe, key=lambda p: p[0])


def corners_of(word, letter):
    points = [(CENTRES[c][0] * 10, CENTRES[c][1] * 4) for c in rejoined(word)]
    index = rejoined(word).index(letter)
    before = points[index - 1]
    here = points[index]
    dx, dy = here[0] - before[0], here[1] - before[1]
    reach = math.hypot(dx, dy)
    dx, dy = dx / reach, dy / reach
    side = dx * dx * (0.55 if dx > 0 else 0.15) + dy * dy * (0.33 if dy < 0 else 0.18)
    pull = side * min(1.25, max(0.5, reach / 4))
    return here, (here[0] - dx * pull, here[1] - dy * pull)


jain_exact, jain_moved = corners_of("jain", "i")
jason_exact, jason_moved = corners_of("jason", "o")
print("jain", jain_exact, jain_moved, "jason", jason_exact, jason_moved, "turn", turn[:2])

size = Sizes(SHOWN)
titles = ["Exact corners: jain is closer", "Moving each corner back to where a thumb stops", "Predicted corners: jason is closer"]
header = Header(size, titles, [])
# The right word's marks are cream and the other word's grey, as in the matcher's diagrams.
COLOURS = {"jain": MUTED, "jason": CREAM}
legend = Legend(size, [("line", dict(colour=ORANGE, px=size.line), "Lazy swipe of jason (simulated)"),
                       ("dot", dict(colour=COLOURS["jain"], r=size.text * 0.36), "Where jain turns"),
                       ("dot", dict(colour=COLOURS["jason"], r=size.text * 0.36), "Where jason turns")])
LEFT, TOP, BOTTOM = 5.95, -0.42, 1.2  # the part of the top row shown, in key units
pitch = (WIDTH - 2 * MARGIN) / (10.05 - LEFT)
origin_y = header.height + size.text * 0.2 - TOP * pitch
X = lambda keys: MARGIN + (keys - LEFT) * pitch
Y = lambda rows: origin_y + rows * pitch
legend_top = Y(BOTTOM) + size.text * 0.35
canvas = Canvas(SHOWN, round(legend_top + legend.height + size.text * 0.3))
ax = canvas.ax
letter_px = size.text * 1.15
for column, key in zip(range(6, 10), "UIOP"):
    on = key.lower() in "ion"
    canvas.box(X(column + 0.05), Y(0.05), 0.9 * pitch, 0.9 * pitch, KEY_LIT if on else KEY, 0.15 * pitch, KEY_LIT_EDGE if on else KEY_EDGE, 1.5)
    canvas.text(X(column + 0.5), Y(0.3), key, letter_px, "bold" if on else "medium", CREAM if on else KEY_LETTER, ha="center",
                va="center_baseline", z=6.5)
title, _ = header.draw(canvas, titles[0])
legend.draw(canvas, legend_top)
clip = Rectangle((0, Y(TOP)), WIDTH, Y(BOTTOM) - Y(TOP), transform=ax.transData)
xs, ys = [X(p[0]) for p in swipe], [Y(p[1]) for p in swipe]
for colour, width in ((BLACK, size.line * 2.1), (ORANGE, size.line)):
    canvas.plot(xs, ys, colour, width, z=4 if colour == BLACK else 5).set_clip_path(clip)
# Where the swipe turns, carried up to the lanes that measure each word's corner from it.
canvas.plot([X(turn[0])] * 2, [Y(turn[1]), Y(TOP + 0.02)], ORANGE, size.line * 0.45, DOTS, z=3)
LANE = {"jain": -0.13, "jason": -0.32}
parts = {}
for name in ("jain", "jason"):
    colour = COLOURS[name]
    drop = canvas.plot([], [], colour, size.line * 0.5, z=6, cap="butt")
    span = canvas.plot([], [], colour, size.line * 0.95, z=6, cap="butt")
    dot = Circle((0, 0), size.text * 0.36, facecolor=colour, edgecolor=BLACK, linewidth=pt(2), zorder=7)
    ax.add_patch(dot)
    words = canvas.text(0, 0, "", size.text * 1.02, "medium", CREAM, va="center_baseline", z=7)
    parts[name] = (drop, span, dot, words)
reel = Reel(canvas)


def place(amount):
    eased = amount * amount * (3 - 2 * amount)
    for name, start, end in (("jain", jain_exact, jain_moved), ("jason", jason_exact, jason_moved)):
        x, y = start[0] + (end[0] - start[0]) * eased, start[1] + (end[1] - start[1]) * eased
        drop, span, dot, words = parts[name]
        lane = LANE[name]
        drop.set_data([X(x)] * 2, [Y(y), Y(lane)])
        span.set_data([X(min(x, turn[0])), X(max(x, turn[0]))], [Y(lane)] * 2)
        dot.center = (X(x), Y(y))
        words.set_position((X(max(x, turn[0]) + 0.09), Y(lane)))
        words.set_text(f"{name}: {abs(x - turn[0]):.2f} keys away")


title.set_text(titles[0])
place(0)
reel.grab(3000)
title.set_text(titles[1])
for step in range(1, 25):
    place(step / 24)
    reel.grab(50)
title.set_text(titles[2])
place(1)
reel.grab(3600)
reel.save("jason-why", colours=80)
