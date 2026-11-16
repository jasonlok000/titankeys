"""The outline matcher (matcher-village, matcher-shape-village), in TitanKeys 2.1's look. Outlines have
16 points, corners pulled back as SwipeShapes.java does. Usage: python matcher_gifs.py"""
import math
import os

os.environ["POINTS"] = "16"
import numpy as np

from look21 import CREAM, DOTS, LONG_DASH, MARGIN, MUTED, ORANGE, WIDTH, Board, Canvas, Header, Legend, Reel, Sizes, Trail
from shape import attempt_path, key_space, normalised, resample
from swipes import CENTRES, SESSION, contacts, load, rejoined

SHOWN = 400
_, attempts, _, _ = load(SESSION)
# The right word's outline is cream and dashed, the engine's grey and dotted, in both GIFs.
RIGHT = dict(colour=CREAM, dash=LONG_DASH, width=0.8)
WRONG = dict(colour=MUTED, dash=DOTS, width=1.0)


def corners(word):
    pts = [(CENTRES[c][0] * 10, CENTRES[c][1] * 4) for c in rejoined(word) if c in CENTRES]
    out = [list(p) for p in pts]
    for i in range(1, len(pts) - 1):
        dx, dy = pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]
        reach = math.hypot(dx, dy)
        if reach < 1.2:
            continue
        dx, dy = dx / reach, dy / reach
        nx, ny = pts[i + 1][0] - pts[i][0], pts[i + 1][1] - pts[i][1]
        if (dx * nx + dy * ny) / math.hypot(nx, ny) >= 0.5:
            continue
        side = dx * dx * (0.55 if dx > 0 else 0.15) + dy * dy * (0.33 if dy < 0 else 0.18)
        pull = side * min(1.25, max(0.5, reach / 4))
        out[i] = [pts[i][0] - dx * pull, pts[i][1] - dy * pull]
    return out


def layout(size, right, wrong, titles, details):
    """Both GIFs share one layout, so they sit level side by side."""
    header = Header(size, titles, details)
    legend = Legend(size, [("line", dict(colour=ORANGE, px=size.line), "My swipe"),
                           ("line", dict(RIGHT, px=size.line * RIGHT["width"]), f"Right word: {right}"),
                           ("line", dict(WRONG, px=size.line * WRONG["width"]), f"Engine's word: {wrong}")])
    pitch = (WIDTH - 2 * MARGIN) / 10
    board_top = header.height + size.text * 0.35
    legend_top = board_top + 3 * pitch + size.text * 0.35
    return header, legend, pitch, board_top, legend_top, round(legend_top + legend.height + size.text * 0.3)


def texts(right, wrong):
    titles = [f"Swiped {right}", "Your swipe and two words", "Same size: only the shape is left"]
    details = [f"the engine picked {wrong}", f"the outline of {right} fits better", "different sizes and positions",
               f"gap: {right} 0.00, {wrong} 0.00"]
    return titles, details


def compare(attempt, right, wrong):
    size = Sizes(SHOWN)
    header, legend, pitch, board_top, legend_top, height = layout(size, right, wrong, *texts(right, wrong))
    touches = [c for c in contacts(attempt) if len(c.points) >= 2]
    points = [(p[0] * 10, p[1] * 4, p[2]) for c in touches for p in c.points]
    canvas = Canvas(SHOWN, height)
    board = Board(canvas, MARGIN, board_top, pitch)
    _, detail = header.draw(canvas, f"Swiped {right}")
    legend.draw(canvas, legend_top)
    trail = Trail(canvas, ORANGE, size.line, start_dot=False)
    reel = Reel(canvas)
    t0, t1 = points[0][2], points[-1][2]
    now = t0
    while True:
        trail.show(*board.xy([p for p in points if p[2] <= now]))
        reel.grab(40)
        if now >= t1:
            break
        now = min(now + 25, t1)
    reel.durations[-1] = 500
    canvas.plot(*board.xy(corners(wrong)), WRONG["colour"], size.line * WRONG["width"], WRONG["dash"], z=7)
    detail.set_text(f"the engine picked {wrong}")
    reel.grab(1000)
    canvas.plot(*board.xy(corners(right)), RIGHT["colour"], size.line * RIGHT["width"], RIGHT["dash"], z=7)
    detail.set_text(f"the outline of {right} fits better")
    reel.grab(2600)
    reel.save(f"matcher-{right}")


def morph(attempt, right, wrong):
    size = Sizes(SHOWN)
    header, legend, pitch, board_top, legend_top, height = layout(size, right, wrong, *texts(right, wrong))
    pts = attempt_path(attempt)
    path = resample(key_space(pts))
    shapes = {"swipe": path, "good": resample(corners(right)), "bad": resample(corners(wrong))}
    scale, centre = 7.6, np.array([5.0, 1.5])
    target = {k: centre + normalised(v) * scale for k, v in shapes.items()}
    gaps = (float(np.hypot(*(normalised(shapes["good"]) - normalised(path)).T).mean()),
            float(np.hypot(*(normalised(shapes["bad"]) - normalised(path)).T).mean()))
    canvas = Canvas(SHOWN, height)
    board = Board(canvas, MARGIN, board_top, pitch)
    title, detail = header.draw(canvas, "Your swipe and two words")
    detail.set_text("different sizes and positions")
    legend.draw(canvas, legend_top)
    bad = canvas.plot([], [], WRONG["colour"], size.line * WRONG["width"], WRONG["dash"], z=4)
    good = canvas.plot([], [], RIGHT["colour"], size.line * RIGHT["width"], RIGHT["dash"], z=5)
    swipe = canvas.plot([], [], ORANGE, size.line, z=6)
    reel = Reel(canvas)

    def show(amount):
        eased = amount * amount * (3 - 2 * amount)
        for artist, key in ((swipe, "swipe"), (good, "good"), (bad, "bad")):
            now = shapes[key] + (target[key] - shapes[key]) * eased
            artist.set_data(*board.xy(now))
        board.fade(1 - eased)

    show(0)
    reel.grab(1800)
    for step in range(1, 21):
        show(step / 20)
        reel.grab(50)
    title.set_text("Same size: only the shape is left")
    detail.set_text(f"gap: {right} {gaps[0]:.2f}, {wrong} {gaps[1]:.2f}")
    reel.grab(3200)
    reel.save(f"matcher-shape-{right}", colours=96)
    return gaps


for right, wrong in (("village", "collar"),):
    attempt = next(a for a in attempts if a["prompt"] == right and a["pace"] != "typed")
    compare(attempt, right, wrong)
    print(right, wrong, "shape gaps", morph(attempt, right, wrong))
