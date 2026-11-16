"""The delete flick (delete-swipe) in TitanKeys 2.1's look. A diagram, not a recording. Drawn to match the
paper (4.10, 9.5) and DeleteSwipe.kt: a quick, straight flick left from around Backspace, at any length a flick allows (1 to 5.5
keys, within 600 ms), takes the word before the cursor. The word lifts off as a dot for each letter, and the dots go into the
caret, as the bar shows them. Two flicks of different lengths, each taking one word. Usage: python delete_gif.py"""
from matplotlib.patches import Circle

from look21 import (BLACK, CREAM, MARGIN, MUTED, ORANGE, SURFACE, DIVIDER, WIDTH, Board, Canvas, Header, Legend, Reel, Sizes,
                    TEXT_SIDE, Trail, measure)

SHOWN = 400
size = Sizes(SHOWN)
DETAIL = "deletes the previous word"
header = Header(size, ["Delete a word"], [DETAIL])
legend = Legend(size, [("line", dict(colour=ORANGE, px=size.line), "Flick left from Backspace (diagram)")])
pitch = (WIDTH - 2 * MARGIN) / 10
TEXT_PX = size.text * 1.15
field_top = header.height + size.text * 0.3
field_height = TEXT_PX * 2.1
board_top = field_top + field_height + size.text * 0.55
legend_top = board_top + 3 * pitch + size.text * 0.35
canvas = Canvas(SHOWN, round(legend_top + legend.height + size.text * 0.3))
ax = canvas.ax
canvas.box(TEXT_SIDE, field_top, WIDTH - 2 * TEXT_SIDE, field_height, SURFACE, field_height * 0.22, DIVIDER, 1.3)
board = Board(canvas, MARGIN, board_top, pitch, lit=["backspace"])
_, detail = header.draw(canvas, "Delete a word")
legend.draw(canvas, legend_top)

TEXT_X = TEXT_SIDE + TEXT_PX * 0.75
BASE = field_top + field_height / 2 + TEXT_PX * 0.36
CARET = (field_top + field_height / 2 - TEXT_PX * 0.62, field_top + field_height / 2 + TEXT_PX * 0.5)
kept = canvas.text(TEXT_X, BASE, "", TEXT_PX, colour=CREAM)
going = canvas.text(TEXT_X, BASE, "", TEXT_PX, colour=CREAM)
caret = canvas.plot([], [], CREAM, 2.6, z=9, cap="butt")
DOT_R = TEXT_PX * 0.13
dots = [Circle((0, 0), DOT_R, facecolor=CREAM, edgecolor="none", zorder=9, visible=False) for _ in range(8)]
for dot in dots:
    ax.add_patch(dot)
trail = Trail(canvas, ORANGE, size.line)
reel = Reel(canvas)


def at(text):
    return TEXT_X + measure(text, TEXT_PX)


def field(before, word=""):
    """The text before the cursor, with [word] still to go after it."""
    kept.set_text(before)
    going.set_text(word)
    going.set_position((at(before), BASE))
    going.set_alpha(1)
    x = at(before + word)
    caret.set_data([x, x], CARET)


def flick(start, end, frames):
    """A quick, straight stroke from [start] to [end] in key units, drawn over a few frames, easing out as a flick does."""
    for i in range(1, frames + 1):
        t = i / frames
        t = 1 - (1 - t) ** 2
        xs = [start[0] + (end[0] - start[0]) * t * k / 12 for k in range(13)]
        ys = [start[1] + (end[1] - start[1]) * t * k / 12 for k in range(13)]
        trail.show(*board.xy(list(zip(xs, ys))))
        reel.grab(40)


def dissolve(before, word, after):
    """[word] lifts off the text as a dot for each letter, and the dots go into the caret, nearest first."""
    # A dot for each letter that goes, not for a space.
    centres = [at(before + word[:i]) + measure(word[i], TEXT_PX) / 2 for i in range(len(word)) if word[i] != " "]
    centre_y = field_top + field_height / 2
    home = at(after)
    caret.set_data([home, home], CARET)
    detail.set_text(DETAIL)
    frames = []
    pop, travel, spread = 3, 6, 2
    total = pop + spread * (len(centres) - 1) + travel
    for f in range(1, total + 1):
        lift = min(1, f / pop)
        going.set_position((at(before), BASE - lift * TEXT_PX * 0.35))
        going.set_alpha(max(0, 1 - lift))
        trail.line.set_alpha(max(0, 1 - f / pop))
        trail.halo.set_alpha(max(0, 1 - f / pop))
        trail.dot.set_alpha(max(0, 1 - f / pop))
        for i, x in enumerate(centres):
            dot = dots[i]
            order = len(centres) - 1 - i  # the dot nearest the caret goes first
            if f <= pop:
                dot.set_visible(True)
                dot.center = (x, centre_y - lift * TEXT_PX * 0.35)
                dot.set_radius(DOT_R * lift)
                continue
            t = (f - pop - spread * order) / travel
            if t <= 0:
                dot.center = (x, centre_y - TEXT_PX * 0.35)
            elif t < 1:
                eased = t * t
                dot.center = (x + (home - x) * eased, centre_y - TEXT_PX * 0.35 * (1 - eased))
            else:
                dot.set_visible(False)
        reel.grab(40)
    going.set_text("")
    trail.clear()
    for line in (trail.line, trail.halo, trail.dot):
        line.set_alpha(1)
    field(after)


field("see you ", "soon")
reel.grab(700)
# A short flick, about 2 keys.
flick((9.35, 1.55), (7.35, 1.5), 4)
dissolve("see you ", "soon", "see you ")
reel.durations[-1] = 1300
# A long one, about 4.5 keys, takes the next word the same way.
field("see ", "you ")
detail.set_text("")
flick((9.4, 1.52), (4.9, 1.46), 5)
dissolve("see ", "you ", "see ")
reel.durations[-1] = 2600
reel.save("delete-swipe")
