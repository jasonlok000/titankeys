"""TitanKeys 2.1's look for the paper's animated diagrams (docs/paper.md): pure black, one orange, Inter.

The rule for orange, the same in all 18: orange is the finger. It marks what a thumb drew or touched (a swipe's trail, a flick,
a thumb on a key) and, on the lockout timelines, the part of a stroke that the keyboard takes. Everything the keyboard compares
a swipe with or makes of it is cream or grey: the word's path, outlines, a second reading, gaps and every label. A second touch
of the same swipe is the same orange, lighter.

Every diagram is 720 px wide. Text is sized for the width the paper shows it at (the <img> width), so a legend reads at about
11.5 px and a title at about 14.5 px there. Every script in this folder uses this; they write to the repo's assets/readme unless
GIF_OUT says otherwise.
"""
import os
import pathlib

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.font_manager import FontProperties
from matplotlib.lines import Line2D
from matplotlib.patches import Circle, FancyBboxPatch, Polygon
from PIL import Image, ImageFont

# This file is tools/dev/diagrams/look21.py, so the repo is three folders up.
REPO = pathlib.Path(__file__).resolve().parents[3]
OUT = pathlib.Path(os.environ.get("GIF_OUT", REPO / "assets/readme"))
OUT.mkdir(parents=True, exist_ok=True)
FONTS = {
    "regular": REPO / "assets/fonts/Inter-Regular.otf",
    "medium": REPO / "assets/fonts/Inter-Medium.otf",
    # The real Inter Bold the app's titles and the guide's key letters use (GuideFonts.heavy).
    "bold": REPO / "android/src/main/res/font/inter_bold.otf",
}
DPI = 150
WIDTH = 720

# lib/brand.ts
BLACK = "#000000"  # every background
CREAM = "#FFF1E6"  # primary text
MUTED = "#99918A"  # secondary text: cream at 60% over black
DIVIDER = "#2A2623"  # hairlines and outlines: cream at about 15% over black
ORANGE = "#EF804E"  # the one accent
# GuideColours.SURFACE: fields, cards and the drawn keys.
SURFACE = "#161210"


def blend(a, b, amount):
    first = [int(a[i:i + 2], 16) for i in (1, 3, 5)]
    second = [int(b[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join(f"{round(x + (y - x) * amount):02X}" for x, y in zip(first, second))


KEY = SURFACE
KEY_EDGE = DIVIDER
KEY_LETTER = MUTED
# A key of the word: a step lighter, its letter cream.
KEY_LIT = blend(SURFACE, CREAM, .13)
KEY_LIT_EDGE = blend(SURFACE, CREAM, .26)
# Labels on alt, sym and Enter, which no diagram is about.
KEY_QUIET = blend(BLACK, MUTED, .55)
# Quiet lines: the top of the sensor, a timeline's axis.
FAINT = blend(BLACK, MUTED, .75)
# The second touch of a broken swipe: the same orange, lighter.
ORANGE_LIGHT = blend(ORANGE, CREAM, .5)

# The Titan 2 Elite's three letter rows, as its keys are printed.
PALETTE = [BLACK, CREAM, MUTED, ORANGE, ORANGE_LIGHT, KEY, KEY_EDGE, KEY_LIT, KEY_LIT_EDGE, KEY_QUIET, FAINT]
ROWS = [list("QWERTYUIOP"), list("ASDFGHJKL") + ["Backspace"], ["alt"] + list("ZXCVBNM") + ["sym", "Enter"]]
QUIET_KEYS = {"alt", "sym"}
# Round dots: a near-zero dash with round caps is a dot as wide as the line.
DOTS = (0, (0.01, 2.3))
DASH = (0, (3, 2))
LONG_DASH = (0, (4, 1.6))
MARGIN = 8
TEXT_SIDE = 14


def pt(px):
    """Matplotlib sizes in points, from pixels at the GIFs' resolution."""
    return px * 72 / DPI


class Sizes:
    """Text sizes in pixels for a GIF that the paper shows [shown] px wide."""

    def __init__(self, shown):
        scale = shown / WIDTH
        self.title = 14.5 / scale
        self.text = 11.5 / scale
        self.key = 9.6 / scale
        self.line = 2.3 / scale  # a trail's width


_fonts = {}


def font(weight, px):
    return FontProperties(fname=str(FONTS[weight]), size=pt(px))


def measure(text, px, weight="regular"):
    """The width of [text] in pixels, as FreeType sets it."""
    key = (weight, round(px * 4))
    if key not in _fonts:
        _fonts[key] = ImageFont.truetype(str(FONTS[weight]), size=round(px * 4))
    return _fonts[key].getlength(text) / 4


class Canvas:
    """A figure whose one axes is in pixels, y down, so every layout number is a pixel."""

    def __init__(self, shown, height):
        self.size = Sizes(shown)
        self.height = height
        self.fig = plt.figure(figsize=(WIDTH / DPI, height / DPI), dpi=DPI, facecolor=BLACK)
        self.ax = self.fig.add_axes([0, 0, 1, 1])
        self.ax.set_xlim(0, WIDTH)
        self.ax.set_ylim(height, 0)
        self.ax.set_facecolor(BLACK)
        self.ax.axis("off")

    def text(self, x, y, s, px, weight="regular", colour=MUTED, ha="left", va="baseline", z=8, **kw):
        return self.ax.text(x, y, s, fontproperties=font(weight, px), color=colour, ha=ha, va=va, zorder=z, **kw)

    def box(self, x, y, w, h, colour, radius, edge="none", lw=0, z=1):
        patch = FancyBboxPatch((x, y), w, h, boxstyle=f"round,pad=0,rounding_size={radius}", facecolor=colour, edgecolor=edge,
                               linewidth=pt(lw), zorder=z)
        self.ax.add_patch(patch)
        return patch

    def plot(self, xs, ys, colour, px, dash=None, z=4, cap="round", **kw):
        style = dict(linestyle=dash) if dash else {}
        line, = self.ax.plot(xs, ys, color=colour, linewidth=pt(px), solid_capstyle=cap, solid_joinstyle="round",
                             dash_capstyle="round" if dash == DOTS else "butt", zorder=z, **style, **kw)
        return line


class Header:
    """A title, and a detail that changes as the diagram plays: on the title's line, right-aligned, where every detail fits,
    or on a line of its own under it."""

    def __init__(self, size, titles, details, two_lines=None):
        self.size = size
        room = WIDTH - 2 * TEXT_SIDE
        widest = max([measure(t, size.title, "bold") for t in titles] or [0])
        detail = max([measure(d, size.text) for d in details] or [0])
        self.two_lines = two_lines if two_lines is not None else widest + size.text * 1.2 + detail > room
        self.title_base = size.title * 1.32
        self.detail_base = self.title_base + size.text * 1.45 if self.two_lines else self.title_base
        self.height = self.detail_base + size.text * (0.62 if self.two_lines else 0.5)

    def draw(self, canvas, title):
        size = self.size
        t = canvas.text(TEXT_SIDE, self.title_base, title, size.title, "bold", CREAM)
        if self.two_lines:
            d = canvas.text(TEXT_SIDE, self.detail_base, "", size.text, colour=MUTED)
        else:
            d = canvas.text(WIDTH - TEXT_SIDE, self.detail_base, "", size.text, colour=MUTED, ha="right")
        return t, d


class Legend:
    """Items as (kind, style, label), packed into as few centred rows as fit. kind is "line", "dot" or "ring"."""

    def __init__(self, size, items, rows=None):
        self.size, self.items = size, items
        px = size.text
        self.handle = px * 2.0
        self.inner = px * 0.45
        self.space = px * 1.25
        widths = [self.handle + self.inner + measure(label, px) if kind == "line" else px * 0.9 + self.inner + measure(label, px)
                  for kind, _, label in items]
        self.widths = widths
        room = WIDTH - 2 * TEXT_SIDE
        for count in range(1, len(items) + 1):
            groups = balanced(widths, count, self.space)
            if all(sum(widths[i] for i in g) + self.space * (len(g) - 1) <= room for g in groups):
                break
        self.groups = groups
        self.rows = max(len(groups), rows or 0)
        self.row = px * 1.55
        self.height = self.row * self.rows

    def draw(self, canvas, top):
        px = self.size.text
        offset = (self.rows - len(self.groups)) * self.row / 2
        for r, group in enumerate(self.groups):
            y = top + offset + self.row * (r + 0.5)
            total = sum(self.widths[i] for i in group) + self.space * (len(group) - 1)
            x = (WIDTH - total) / 2
            for i in group:
                kind, style, label = self.items[i]
                if kind == "line":
                    canvas.plot([x, x + self.handle], [y, y], style["colour"], style.get("px", 4), style.get("dash"), z=9,
                                cap="butt" if style.get("dash") else "round")
                    lx = x + self.handle + self.inner
                else:
                    r_px = style.get("r", px * 0.3)
                    canvas.ax.add_patch(Circle((x + px * 0.45, y), r_px, facecolor=style["colour"] if kind == "dot" else "none",
                                               edgecolor=style["colour"] if kind == "ring" else BLACK, linewidth=pt(1.5), zorder=9))
                    lx = x + px * 0.9 + self.inner
                canvas.text(lx, y + px * 0.36, label, px, colour=MUTED)
                x += self.widths[i] + self.space


def balanced(widths, count, space):
    """Splits items, in order, into [count] rows as even in width as can be."""
    n = len(widths)
    if count >= n:
        return [[i] for i in range(n)]
    best = None
    def splits(start, left):
        if left == 1:
            yield [list(range(start, n))]
            return
        for end in range(start + 1, n - left + 2):
            for rest in splits(end, left - 1):
                yield [list(range(start, end))] + rest
    for groups in splits(0, count):
        widest = max(sum(widths[i] for i in g) + space * (len(g) - 1) for g in groups)
        if best is None or widest < best[0]:
            best = (widest, groups)
    return best[1]


class Board:
    """The Titan 2 Elite's three letter rows, in key units, drawn at [left], [top] with keys [pitch] px apart."""

    def __init__(self, canvas, left, top, pitch, lit=(), rows=ROWS, letter_px=None, columns=None):
        self.canvas, self.left, self.top, self.pitch = canvas, left, top, pitch
        self.items = []
        size = canvas.size
        letter_px = letter_px or min(size.key, pitch * 0.34)
        for r, keys in enumerate(rows):
            for c, name in enumerate(keys):
                if columns and c not in columns:
                    continue
                on = name.lower() in lit
                x, y = self.x(c + 0.045), self.y(r + 0.045)
                self.items.append(canvas.box(x, y, pitch * 0.91, pitch * 0.91, KEY_LIT if on else KEY, pitch * 0.15,
                                             KEY_LIT_EDGE if on else KEY_EDGE, 1.3))
                cx, cy = self.x(c + 0.5), self.y(r + 0.5)
                if name in QUIET_KEYS:
                    self.items.append(canvas.text(cx, cy, name, letter_px * 0.72, "medium", KEY_QUIET, ha="center", va="center_baseline"))
                elif name == "Backspace":
                    self.items += backspace(canvas, cx, cy, letter_px, CREAM if on else KEY_QUIET)
                elif name == "Enter":
                    self.items += enter(canvas, cx, cy, letter_px, KEY_QUIET)
                elif name.strip():
                    self.items.append(canvas.text(cx, cy, name, letter_px, "bold" if on else "medium", CREAM if on else KEY_LETTER,
                                                  ha="center", va="center_baseline"))

    def x(self, keys):
        return self.left + keys * self.pitch

    def y(self, rows):
        return self.top + rows * self.pitch

    def xy(self, points):
        """Points in key units (x 0 to 10, y 0 to 3 rows) as two lists of pixels."""
        return [self.x(p[0]) for p in points], [self.y(p[1]) for p in points]

    def fade(self, alpha):
        for item in self.items:
            item.set_alpha(alpha)


def backspace(canvas, cx, cy, px, colour):
    """Backspace's mark, drawn as the guide draws its icons: a key shape pointing left with a cross in it."""
    w, h = px * 1.15, px * 0.78
    left, right, top, bottom = cx - w / 2, cx + w / 2, cy - h / 2, cy + h / 2
    shape = Polygon([(left, cy), (left + h * 0.45, top), (right, top), (right, bottom), (left + h * 0.45, bottom)], closed=True,
                    facecolor="none", edgecolor=colour, linewidth=pt(max(1.6, px * 0.09)), joinstyle="round", zorder=2)
    canvas.ax.add_patch(shape)
    mx, s = cx + w * 0.12, h * 0.22
    a = canvas.plot([mx - s, mx + s], [cy - s, cy + s], colour, max(1.6, px * 0.09), z=2)
    b = canvas.plot([mx - s, mx + s], [cy + s, cy - s], colour, max(1.6, px * 0.09), z=2)
    return [shape, a, b]


def enter(canvas, cx, cy, px, colour):
    w, h = px * 0.9, px * 0.55
    lw = max(1.6, px * 0.09)
    a = canvas.plot([cx + w / 2, cx + w / 2, cx - w / 2], [cy - h / 2, cy + h / 3, cy + h / 3], colour, lw, z=2)
    b = canvas.plot([cx - w / 2 + h * 0.35, cx - w / 2, cx - w / 2 + h * 0.35], [cy + h / 3 - h * 0.35, cy + h / 3, cy + h / 3 + h * 0.35],
                    colour, lw, z=2)
    return [a, b]


class Trail:
    """A swipe's trail: a line on a black halo, so it lifts off the keys, and a dot where it started."""

    def __init__(self, canvas, colour, px, start_dot=True, z=4):
        self.halo = canvas.plot([], [], BLACK, px * 2.1, z=z)
        self.line = canvas.plot([], [], colour, px, z=z + 1)
        self.dot = canvas.ax.plot([], [], marker="o", markersize=pt(px * 2.6), color=colour, markeredgecolor=BLACK,
                                  markeredgewidth=pt(1.6), linestyle="none", zorder=z + 2)[0] if start_dot else None

    def show(self, xs, ys):
        self.halo.set_data(xs, ys)
        self.line.set_data(xs, ys)
        if self.dot is not None:
            self.dot.set_data(xs[:1], ys[:1])

    def clear(self):
        self.show([], [])


class Reel:
    """Frames and their times, saved as one GIF with one palette for every frame, so no frame's colours drift."""

    def __init__(self, canvas):
        self.fig, self.frames, self.durations = canvas.fig, [], []

    def grab(self, ms):
        self.fig.canvas.draw()
        self.frames.append(Image.frombuffer("RGBA", self.fig.canvas.get_width_height(), self.fig.canvas.buffer_rgba()).convert("RGB"))
        self.durations.append(ms)

    def save(self, name, colours=64, keep=()):
        """The palette is the look's own colours, exactly, then the edges' in-between shades, picked from the frames."""
        picks = sorted(set(list(range(0, len(self.frames), max(1, len(self.frames) // 10))) + [len(self.frames) - 1]))
        w, h = self.frames[0].size
        sample = Image.new("RGB", (w, h * len(picks)))
        for i, index in enumerate(picks):
            sample.paste(self.frames[index], (0, h * i))
        fixed = []
        for colour in dict.fromkeys(list(PALETTE) + list(keep)):
            fixed += [int(colour[i:i + 2], 16) for i in (1, 3, 5)]
        shades = sample.quantize(colors=colours - len(fixed) // 3, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
        values = fixed + shades.getpalette()[:3 * (colours - len(fixed) // 3)]
        palette = Image.new("P", (1, 1))
        palette.putpalette(values + values[:3] * (256 - len(values) // 3))
        frames = [f.quantize(palette=palette, dither=Image.Dither.NONE) for f in self.frames]
        path = OUT / f"{name}.gif"
        frames[0].save(path, save_all=True, append_images=frames[1:], duration=self.durations, loop=0, optimize=False, disposal=1)
        plt.close(self.fig)
        print(f"{name}.gif {w}x{h} {len(frames)} frames {sum(self.durations)} ms {path.stat().st_size // 1024} KB")
        return self.frames[-1]
