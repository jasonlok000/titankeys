"""The paper's recorded swipes and the demo's trail (strength, recognition, power, village, demo-before, demo-after), in TitanKeys
2.1's look. Usage: python animate.py"""
from look21 import (CREAM, DASH, DOTS, FAINT, MARGIN, MUTED, ORANGE, ORANGE_LIGHT, WIDTH, Board, Canvas, Header, Legend, Reel,
                    Sizes, Trail)
from swipes import CENTRES, SESSION, contacts, load, rejoined

SENSOR_TOP = 0.116 * 4
STEP_MS = 25
FRAME_MS = 40  # each 25 ms of the swipe is shown for 40 ms, so it plays at about 60% speed
HOLD_MS = 2000
_, attempts, _, _ = load(SESSION)


def styles(size):
    word = ("line", dict(colour=MUTED, px=size.line * 0.72, dash=DOTS), "The word")
    top = ("line", dict(colour=FAINT, px=size.line * 0.42, dash=(0, (1.4, 1.4))), "Top of sensor")
    gap = ("line", dict(colour=CREAM, px=size.line * 0.5, dash=DASH), "Gap")
    start = ("dot", dict(colour=ORANGE), "Start")
    return word, top, gap, start


def animate(name, shown, title, detail, letters, layers, colours, legend, outline=None, sensor=False, gaps=False, replace=False,
            legend_rows=None):
    """layers: lists of (x, y, ms) in key units. gaps draws a dashed line between layers; replace clears earlier layers when a
    new one starts."""
    size = Sizes(shown)
    header = Header(size, [title], [detail])
    pitch = (WIDTH - 2 * MARGIN) / 10
    key = Legend(size, legend, rows=legend_rows)
    board_top = header.height + size.text * 0.35
    legend_top = board_top + 3 * pitch + size.text * 0.35
    canvas = Canvas(shown, round(legend_top + key.height + size.text * 0.3))
    board = Board(canvas, MARGIN, board_top, pitch, lit=letters)
    word, top, gap, _ = styles(size)
    if outline:
        canvas.plot(*board.xy(outline), MUTED, word[1]["px"], DOTS, z=3)
    if sensor:
        canvas.plot([board.x(0), board.x(10)], [board.y(SENSOR_TOP)] * 2, FAINT, top[1]["px"], top[1]["dash"], z=3)
    _, detail_text = header.draw(canvas, title)
    detail_text.set_text(detail)
    key.draw(canvas, legend_top)
    start = layers[0][0][2]
    end = layers[-1][-1][2]
    trails = [(layer, Trail(canvas, colour, size.line)) for layer, colour in zip(layers, colours)]
    joins = []
    if gaps:
        for before, after in zip(layers, layers[1:]):
            joins.append((before[-1], after[0], canvas.plot([], [], CREAM, gap[1]["px"], DASH, z=7, cap="butt")))
    reel = Reel(canvas)
    now = start - STEP_MS * 12
    while True:
        latest = -1
        for index, (layer, trail) in enumerate(trails):
            shown_points = [p for p in layer if p[2] <= now]
            if shown_points:
                latest = index
            trail.show(*board.xy(shown_points))
        if replace and latest > 0:
            for _, trail in trails[:latest]:
                trail.clear()
        for before, after, line in joins:
            if after[2] <= now:
                line.set_data(*board.xy([before, after]))
            else:
                line.set_data([], [])
        reel.grab(FRAME_MS)
        if now >= end:
            break
        now = min(now + STEP_MS, end)
    reel.durations[-1] = HOLD_MS
    return reel.save(name)


def real(contact):
    return [(p[0] * 10, p[1] * 4, p[2]) for p in contact.points]


def ideal(word):
    return [(CENTRES[c][0] * 10, CENTRES[c][1] * 4) for c in rejoined(word)]


def touches_of(word):
    attempt = next(a for a in attempts if a["prompt"] == word and a["pace"] != "typed")
    return [c for c in contacts(attempt) if len(c.points) >= 2]


TOUCH_COLOURS = [ORANGE, ORANGE_LIGHT]
# Shown 340 px wide in a table (4.1); all four keep two legend rows, so the table's rows are the same height.
for word in "strength recognition power village".split():
    size = Sizes(340)
    touches = touches_of(word)
    layers = [real(t) for t in touches]
    broken = len(touches) > 1
    word_line, top, gap, start = styles(size)
    if broken:
        gaps = [touches[i + 1].start - touches[i].end for i in range(len(touches) - 1)]
        detail = f"{len(touches)} touches, {'gap' if len(gaps) == 1 else 'gaps'} of " + " and ".join(f"{g} ms" for g in gaps)
        legend = [("line", dict(colour=TOUCH_COLOURS[i], px=size.line), f"Touch {i + 1}") for i in range(len(touches))]
        legend += [gap, top, word_line, start]
    else:
        detail = "1 touch"
        legend = [("line", dict(colour=ORANGE, px=size.line), "My swipe"), word_line, start]
    animate(word, 340, word, detail, rejoined(word), layers, TOUCH_COLOURS[:len(layers)], legend, outline=ideal(word), sensor=broken,
            gaps=broken, legend_rows=2)

# Shown 400 px wide, side by side (4.7).
size = Sizes(400)
touches = touches_of("recognition")
_, top, _, start = styles(size)
trail = ("line", dict(colour=ORANGE, px=size.line), "Trail")
layers = [real(t) for t in touches]
animate("demo-before", 400, "Atlas", "each new touch clears the trail", rejoined("recognition"), layers, [ORANGE] * len(layers),
        [trail, top, start], sensor=True, replace=True)
layers2 = [[p for t in touches for p in real(t)]]
animate("demo-after", 400, "TitanKeys", "the whole stroke stays", rejoined("recognition"), layers2, [ORANGE], [trail, top, start],
        sensor=True)
