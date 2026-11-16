"""Shared loading and geometry for studying a recorder session. Nothing here touches the keyboard itself."""
import json
import math
import os
import pathlib
import sys
from statistics import median

# The repo (three folders up from tools/dev/diagrams) and the recorded session the paper's swipes come from, which is in it.
REPO = pathlib.Path(__file__).resolve().parents[3]
SESSION = REPO / "android/debug/recorder/samples/session-20261108-114501.jsonl"


def data_path(*parts):
    """A path under $TK_DATA, where replay results and caches live. They stay on the maintainer's machine, never in the repo."""
    root = os.environ.get("TK_DATA")
    if not root:
        sys.exit("TK_DATA is not set. Run from a shell that sourced tools/dev/env.sh, or set it to your data folder.")
    return pathlib.Path(root).joinpath(*parts)

ROWS = {"qwertyuiop": 0, "asdfghjkl": 1}
CENTRES = {}
for letters, row in ROWS.items():
    for column, letter in enumerate(letters):
        CENTRES[letter] = ((column + 0.5) / 10, (row + 0.5) / 4)
for column, letter in enumerate("zxcvbnm", start=1):
    CENTRES[letter] = ((column + 0.5) / 10, 2.5 / 4)

DOWN, UP, MOVE, CANCEL, POINTER_DOWN = 0, 1, 2, 3, 5
BACKSPACE, ENTER, SPACE = 67, 66, 62


def distance(ax, ay, bx, by):
    """Distance in key widths, the same scale Contact.kt uses."""
    return math.hypot((bx - ax) * 10, (by - ay) * 4)


def rejoined(word):
    """The letters with immediate repeats removed, since a swipe can't show a double letter."""
    out = []
    for letter in word:
        if not out or out[-1] != letter:
            out.append(letter)
    return out


def ideal_length(word):
    points = [CENTRES[c] for c in rejoined(word) if c in CENTRES]
    return sum(distance(*a, *b) for a, b in zip(points, points[1:]))


def row_of(y):
    if y < 0.25:
        return "top"
    if y < 0.5:
        return "middle"
    if y < 0.75:
        return "bottom"
    return "spacebar"


class Contact:
    def __init__(self, start):
        self.start = start
        self.end = start
        self.points = []
        self.ended_by = None

    @property
    def duration(self):
        return self.end - self.start

    @property
    def path(self):
        return sum(distance(a[0], a[1], b[0], b[1]) for a, b in zip(self.points, self.points[1:]))

    @property
    def reach(self):
        if not self.points:
            return 0.0
        first = self.points[0]
        return max(distance(first[0], first[1], p[0], p[1]) for p in self.points)


def contacts(attempt):
    """Rebuilds each finger contact from the raw events."""
    found, current = [], None
    for event in attempt["events"]:
        if event["k"] != "m":
            continue
        action = event["a"]
        if action == DOWN or current is None:
            current = Contact(event["t"])
            found.append(current)
        for x, y, t in event["s"]:
            if x is not None and y is not None:
                current.points.append((x, y, t))
        current.end = event["t"]
        if action in (UP, CANCEL, POINTER_DOWN):
            current.ended_by = action
            current = None
    return found


def load(path):
    """Returns the header, the final attempt per prompt, attempts that Redo threw away, and skipped prompts."""
    header, final, discarded, skipped = None, {}, [], set()
    for line in open(path, encoding="utf-8"):
        entry = json.loads(line)
        if "format" in entry:
            header = entry
        elif "redo" in entry:
            if entry["redo"] in final:
                discarded.append(final.pop(entry["redo"]))
        elif "skip" in entry:
            skipped.add(entry["skip"])
        else:
            if entry["index"] in final:
                discarded.append(final[entry["index"]])
            final[entry["index"]] = entry
    return header, [final[i] for i in sorted(final)], discarded, skipped


def right_first(attempt):
    return bool(attempt["top"]) and attempt["top"][0].lower() == attempt["prompt"]


def right_top3(attempt):
    return attempt["prompt"] in [w.lower() for w in attempt["top"][:3]]


def share(items, test):
    items = list(items)
    if not items:
        return "n/a"
    hits = sum(1 for item in items if test(item))
    return f"{hits}/{len(items)} ({100 * hits / len(items):.0f}%)"


def quantiles(values, cuts):
    ordered = sorted(values)
    return [ordered[min(len(ordered) - 1, int(cut * len(ordered)))] for cut in cuts]


def med(values):
    values = list(values)
    return median(values) if values else float("nan")
