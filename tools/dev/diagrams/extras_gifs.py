"""The smaller changes for two-handed typing (space-before/after, pick-before/after, lockout-before/after), in TitanKeys 2.1's
look. Drawings of the behaviour, not recordings. Before is Atlas, after is TitanKeys.
Usage: python extras_gifs.py"""
from matplotlib.patches import Circle, Rectangle

from look21 import (CREAM, DIVIDER, FAINT, KEY, KEY_EDGE, KEY_LETTER, KEY_LIT, KEY_LIT_EDGE, MARGIN, MUTED, ORANGE, ROWS, SURFACE,
                    TEXT_SIDE, WIDTH, Board, Canvas, Header, Reel, Sizes, blend, measure)

SHOWN = 400
OLD, NEW, OLD_PAUSE, NEW_PAUSE = "Atlas", "TitanKeys", "Atlas: 150 ms pause", "TitanKeys"
size = Sizes(SHOWN)
U = (WIDTH - 2 * MARGIN) / 10  # one key, the unit the old drawings were laid out in
TEXT_PX = size.text * 1.15
# Text the thumb selected: the field's colour, lit.
SELECTION = blend(SURFACE, CREAM, .3)


class Scene:
    """A header over a drawing laid out in key units, [rows] of them tall."""

    def __init__(self, title, details, rows):
        self.header = Header(size, [OLD, NEW, OLD_PAUSE, NEW_PAUSE], details)
        self.top = self.header.height + size.text * 0.3
        self.canvas = Canvas(SHOWN, round(self.top + rows * U + size.text * 0.45))
        _, self.detail = self.header.draw(self.canvas, title)
        self.reel = Reel(self.canvas)

    def x(self, units):
        return MARGIN + units * U

    def y(self, units):
        return self.top + units * U

    def box(self, x, y, w, h, colour=KEY, edge=KEY_EDGE, z=1, lw=1.3):
        return self.canvas.box(self.x(x), self.y(y), w * U, h * U, colour, U * 0.15, edge, lw, z)

    def field(self, text):
        """A text field across the top, with [text] and the cursor after it."""
        self.box(0.15, 0.1, 9.7, 0.82, SURFACE, DIVIDER)
        self.text_x = self.x(0.45)
        self.base = self.y(0.51) + TEXT_PX * 0.36
        label = self.canvas.text(self.text_x, self.base, text, TEXT_PX, colour=CREAM, z=4)
        caret = self.canvas.plot([], [], CREAM, 2.6, z=4, cap="butt")
        self.caret_at(caret, text)
        return label, caret

    def caret_at(self, caret, text):
        x = self.text_x + measure(text, TEXT_PX) + 2
        caret.set_data([x, x], [self.y(0.51) - TEXT_PX * 0.62, self.y(0.51) + TEXT_PX * 0.5])

    def thumb(self, x, y):
        """The thumb on a key: orange, as everywhere the finger is."""
        spot = Circle((self.x(x), self.y(y)), 0.42 * U, facecolor=ORANGE, edgecolor="none", alpha=0, zorder=3)
        self.canvas.ax.add_patch(spot)
        return spot


SPACE_DETAILS = ["cursor at the end of the text", "thumb rests on Space", "thumb pushes Space", "selects the word, no space typed",
                 "types a space"]


def space(title, name, selects):
    scene = Scene(title, SPACE_DETAILS, 3.4)
    text = "see you"
    label, caret = scene.field(text)
    Board(scene.canvas, MARGIN, scene.y(1.15), U, rows=[ROWS[2]])
    bar = scene.box(2.5, 2.3, 5, 1.05)
    scene.canvas.text(scene.x(5), scene.y(2.825), "Space", size.key, "medium", KEY_LETTER, ha="center", va="center_baseline", z=2)
    thumb = scene.thumb(5, 2.825)
    reel, detail = scene.reel, scene.detail
    detail.set_text("cursor at the end of the text")
    reel.grab(1000)
    thumb.set_alpha(0.35)
    detail.set_text("thumb rests on Space")
    reel.grab(1300)
    thumb.set_alpha(0.8)
    bar.set_facecolor(KEY_LIT)
    bar.set_edgecolor(KEY_LIT_EDGE)
    detail.set_text("thumb pushes Space")
    reel.grab(800)
    thumb.set_alpha(0)
    bar.set_facecolor(KEY)
    bar.set_edgecolor(KEY_EDGE)
    if selects:
        start = scene.text_x + measure("see ", TEXT_PX)
        scene.canvas.ax.add_patch(Rectangle((start - 1, scene.y(0.51) - TEXT_PX * 0.66), measure("you", TEXT_PX) + 3, TEXT_PX * 1.2,
                                            facecolor=SELECTION, edgecolor="none", zorder=3))
        caret.set_alpha(0)
        detail.set_text("selects the word, no space typed")
    else:
        label.set_text(text + " ")
        scene.caret_at(caret, text + " ")
        caret.set_linewidth(3.4 * 72 / 150)
        detail.set_text("types a space")
    reel.grab(3200)
    return reel.save(name)


PICK_DETAILS = ["the strip shows this swipe's options", "thumb picks good", "the strip moves on to next words",
                "the strip keeps the same options"]


def strip(title, name, moves_on):
    scene = Scene(title, PICK_DETAILS, 3.45)
    scene.field("that sounds good")
    chips, words = [], []
    for i, word in enumerate(("good", "food", "goods")):
        chips.append(scene.box(0.15 + i * 3.28, 1.2, 3.14, 0.92, SURFACE, DIVIDER))
        words.append(scene.canvas.text(scene.x(1.72 + i * 3.28), scene.y(1.66), word, TEXT_PX * 0.95, colour=CREAM, ha="center",
                                       va="center_baseline", z=4))
    Board(scene.canvas, MARGIN, scene.y(2.5), U, rows=[ROWS[0]])
    thumb = scene.thumb(1.72, 1.66)
    reel, detail = scene.reel, scene.detail
    detail.set_text("the strip shows this swipe's options")
    reel.grab(1500)
    thumb.set_alpha(0.6)
    chips[0].set_facecolor(KEY_LIT)
    chips[0].set_edgecolor(KEY_LIT_EDGE)
    detail.set_text("thumb picks good")
    reel.grab(1000)
    thumb.set_alpha(0)
    chips[0].set_facecolor(SURFACE)
    # What the strip shows now: outlined in cream, whether it moved on or not.
    for chip in chips:
        chip.set_edgecolor(CREAM)
        chip.set_linewidth(2.2 * 72 / 150)
    if moves_on:
        for word, new in zip(words, ("idea", "to", "for")):
            word.set_text(new)
        detail.set_text("the strip moves on to next words")
    else:
        detail.set_text("the strip keeps the same options")
    reel.grab(3400)
    return reel.save(name)


END = 700
X0, X1 = TEXT_SIDE + 22, 540  # 0 and 700 ms; the room on the right is for what became of each stroke


def at(ms):
    return X0 + (X1 - X0) * ms / END


def timeline(title, name, pause, counts_after):
    scene = Scene(title, ["while typing steadily"], 3.75)
    canvas, y = scene.canvas, scene.y
    scene.detail.set_text("while typing steadily")
    small = size.text * 0.95
    top, axis = y(0.42), y(2.75)
    canvas.ax.add_patch(Rectangle((at(0), top), at(pause) - at(0), axis - top, facecolor=blend(SURFACE, CREAM, .07), edgecolor="none",
                                  zorder=1))
    canvas.text(at(pause / 2) if pause > 200 else at(0) + 8, y(0.26), f"{pause} ms pause", small, colour=MUTED,
                ha="center" if pause > 200 else "left", va="center_baseline", z=2)
    canvas.plot([at(0), at(END)], [axis, axis], FAINT, 2, z=2, cap="butt")
    for ms in sorted({0, pause, END}):
        canvas.plot([at(ms)] * 2, [axis, axis + 7], FAINT, 2, z=2, cap="butt")
        canvas.text(at(ms), axis + 7 + small * 1.15, f"{ms} ms", small, colour=MUTED, ha="center", z=2)
    canvas.plot([at(0)] * 2, [top, axis], CREAM, 2.6, z=3, cap="butt")
    canvas.text(at(0), y(3.62), "key pressed at 0 ms", small, colour=CREAM, z=3)
    stroke, brush = (100, 500), (250, 310)
    lane_one, lane_two = y(1.18), y(2.18)
    canvas.text(at(stroke[0]), lane_one - small * 0.95, "A real swipe starts", small * 1.05, colour=CREAM, z=4)
    canvas.text(at(brush[0]), lane_two - small * 0.95, "A brush on the keys", small * 1.05, colour=CREAM, z=4)
    # A stroke is grey until the keyboard takes it; the part it takes is orange, the finger's colour.
    idle = blend(SURFACE, MUTED, .55)
    stroke_line = canvas.plot([], [], idle, 10, z=4, cap="butt")
    counted_line = canvas.plot([], [], ORANGE, 10, z=5, cap="butt")
    brush_line = canvas.plot([], [], idle, 10, z=4, cap="butt")
    head = canvas.plot([], [], CREAM, 1.6, z=6, cap="butt")
    first = canvas.text(at(stroke[1]) + 12, lane_one, "", small * 1.05, "medium", CREAM, va="center_baseline", z=6)
    second = canvas.text(at(brush[1]) + 12, lane_two, "", small * 1.05, "medium", CREAM, va="center_baseline", z=6)
    reel = scene.reel
    swipe_counts = counts_after is not None
    brush_counts = brush[0] >= pause
    now = 0
    while True:
        stroke_end = min(now, stroke[1])
        if stroke_end > stroke[0]:
            stroke_line.set_data([at(stroke[0]), at(stroke_end)], [lane_one] * 2)
            if swipe_counts and stroke_end > counts_after:
                counted_line.set_data([at(counts_after), at(stroke_end)], [lane_one] * 2)
        brush_end = min(now, brush[1])
        if brush_end > brush[0]:
            brush_line.set_data([at(brush[0]), at(brush_end)], [lane_two] * 2)
            brush_line.set_color(ORANGE if brush_counts else idle)
        head.set_data([at(now)] * 2, [top, axis])
        if now >= brush[1]:
            second.set_text("read as a tap" if brush_counts else "ignored")
        if now >= stroke[1]:
            first.set_text("counts after 1.5 keys" if swipe_counts else "lost")
        reel.grab(40)
        if now >= END:
            break
        now = min(now + 20, END)
    head.set_data([], [])
    reel.grab(3200)
    return reel.save(name)


space(OLD, "space-before", True)
space(NEW, "space-after", False)
strip(OLD, "pick-before", False)
strip(NEW, "pick-after", True)
timeline(OLD_PAUSE, "lockout-before", 150, None)
timeline(NEW_PAUSE, "lockout-after", 500, 220)
