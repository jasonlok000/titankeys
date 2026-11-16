"""The second, wider reading of a fast swipe (stretch-partner, stretch-perform), in TitanKeys 2.1's look.
Usage: python stretch_gif.py"""
from look21 import CREAM, DOTS, MARGIN, MUTED, ORANGE, WIDTH, Board, Canvas, Header, Legend, Reel, Sizes, Trail
from swipes import CENTRES, SESSION, contacts, load, rejoined

STRETCH = 1.2  # SwipeRecognizer.SWIPE_STRETCH, reached at 16 keys a second; both swipes here are faster (18.3 and 17.9)
SHOWN = 400
_, attempts, _, _ = load(SESSION)


def make(word, first):
    attempt = next(a for a in attempts if a["prompt"] == word and a["pace"] != "typed")
    touch = [c for c in contacts(attempt) if len(c.points) >= 2][0]
    points = [(p[0] * 10, p[1] * 4, p[2]) for p in touch.points]
    letters = rejoined(word)
    outline = [(CENTRES[c][0] * 10, CENTRES[c][1] * 4) for c in letters]
    left, right = min(p[0] for p in points), max(p[0] for p in points)
    centre = (left + right) / 2
    wide = [min(10, max(0, centre + (p[0] - centre) * STRETCH)) for p in points]
    width = (right - left) / (max(p[0] for p in outline) - min(p[0] for p in outline))
    size = Sizes(SHOWN)
    plain = f"{width:.0%} as wide as the word: reads {first}"
    wider = f"{STRETCH:.1f} times wider: reads {word}"
    header = Header(size, [word], [plain, wider])
    legend = Legend(size, [("line", dict(colour=ORANGE, px=size.line), "My swipe"),
                           ("line", dict(colour=CREAM, px=size.line * 0.8), "Read again, wider"),
                           ("line", dict(colour=MUTED, px=size.line * 0.72, dash=DOTS), "The word")])
    pitch = (WIDTH - 2 * MARGIN) / 10
    board_top = header.height + size.text * 0.35
    legend_top = board_top + 3 * pitch + size.text * 0.35
    canvas = Canvas(SHOWN, round(legend_top + legend.height + size.text * 0.3))
    board = Board(canvas, MARGIN, board_top, pitch, lit=letters)
    canvas.plot(*board.xy(outline), MUTED, size.line * 0.72, DOTS, z=3)
    _, detail = header.draw(canvas, word)
    legend.draw(canvas, legend_top)
    trail = Trail(canvas, ORANGE, size.line, start_dot=False)
    reel = Reel(canvas)
    now = points[0][2] - 200
    while True:
        trail.show(*board.xy([p for p in points if p[2] <= now]))
        reel.grab(40)
        if now >= points[-1][2]:
            break
        now = min(now + 25, points[-1][2])
    detail.set_text(plain)
    reel.grab(2000)
    moved = canvas.plot([], [], CREAM, size.line * 0.8, z=7)
    ys = [p[1] for p in points]
    for step in range(1, 17):
        amount = step / 16
        eased = amount * amount * (3 - 2 * amount)
        xs = [p[0] + (w - p[0]) * eased for p, w in zip(points, wide)]
        moved.set_data(*board.xy(list(zip(xs, ys))))
        reel.grab(50)
    detail.set_text(wider)
    reel.grab(3200)
    print(word, f"width {width:.2f}")
    return reel.save(f"stretch-{word}")


# The plain reading, as the keyboard showed it when recorded (the recording's own top word): "owner" and "Oregon".
for word, first in (("partner", "owner"), ("perform", "Oregon")):
    make(word, first)
