"""Simulated sloppy swipes of chosen words, at shortfalls measured from the real session. NOT real data.
Usage: python synth_word.py stats
       python synth_word.py make word [word...]
       python synth_word.py report run [run...]   (names in $TK_DATA/replay)
make writes to $SYNTH_OUT, or $TK_DATA/synth-words2.jsonl. jason_gif.py makes its swipe with simulated() and needs no file."""
import json
import os
import random
import sys
from collections import Counter, defaultdict

from swipes import CENTRES, SESSION, contacts, data_path, distance, load, med, quantiles, rejoined

LEVELS = [float(v) for v in os.environ.get("SYNTH_LEVELS", "0,0.25,0.5,0.75,1.0,1.25,1.5").split(",")]
SEEDS = int(os.environ.get("SYNTH_SEEDS", "6"))


def real_stats():
    _, attempts, _, _ = load(SESSION)
    swiped = [a for a in attempts if a["pace"] != "typed"]
    right, left, up = defaultdict(list), defaultdict(list), defaultdict(list)
    lengths, gaps = [], []
    for attempt in swiped:
        found = [c for c in contacts(attempt) if len(c.points) >= 4]
        if len(found) != 1:
            continue
        points = found[0].points
        letters = [CENTRES[c] for c in rejoined(attempt["prompt"]) if c in CENTRES]
        pace = attempt["pace"]
        right[pace].append((max(p[0] for p in letters) - max(p[0] for p in points)) * 10)
        left[pace].append((min(p[0] for p in points) - min(p[0] for p in letters)) * 10)
        if any(p[1] < 0.25 for p in letters):
            up[pace].append((min(p[1] for p in points) - 0.125) * 4)
        duration = points[-1][2] - points[0][2]
        if duration > 0:
            lengths.append(found[0].path / duration)
        gaps += [b[2] - a[2] for a, b in zip(points, points[1:])]
    return right, left, up, lengths, gaps


def stats():
    right, left, up, lengths, gaps = real_stats()
    cuts = [0.1, 0.5, 0.9]
    for name, table in (("short of the rightmost letter", right), ("short of the leftmost letter", left), ("short of the top row centre", up)):
        for pace, values in sorted(table.items()):
            print(f"{name:32} {pace:8} n={len(values):3} 10/50/90%: {[round(v, 2) for v in quantiles(values, cuts)]} keys")
    print("speed, key widths per ms, 10/50/90%:", [round(v, 4) for v in quantiles(lengths, cuts)])
    print("ms between samples, 50/90%:", quantiles(gaps, [0.5, 0.9]))
    _, attempts, _, _ = load(SESSION)
    print("first events of a real swipe:", json.dumps(attempts[0]["events"][:3])[:400])


def waypoints(word, short, rng):
    letters = [list(CENTRES[c]) for c in rejoined(word)]
    points = [p[:] for p in letters]
    for i, p in enumerate(letters):
        before = letters[i - 1][0] if i else p[0]
        after = letters[i + 1][0] if i + 1 < len(letters) else p[0]
        if p[0] >= before and p[0] >= after and (i and i + 1 < len(letters)):
            points[i][0] -= short * 0.1
        elif p[0] <= before and p[0] <= after and (i and i + 1 < len(letters)):
            points[i][0] += short * 0.1
        if p[1] < 0.25:
            points[i][1] += 0.1 * 0.25  # the real median, whatever the sideways shortfall
        points[i][0] += rng.gauss(0, 0.012)
        points[i][1] += rng.gauss(0, 0.015)
    return points


def sampled(points, speed, step):
    out = []
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        count = max(2, int(distance(ax, ay, bx, by) / (speed * step)))
        out += [(ax + (bx - ax) * i / count, ay + (by - ay) * i / count) for i in range(count)]
    out.append(tuple(points[-1]))
    radius = 2
    smooth = []
    for i in range(len(out)):
        part = out[max(0, i - radius):i + radius + 1]
        smooth.append((sum(p[0] for p in part) / len(part), sum(p[1] for p in part) / len(part)))
    return smooth


def simulated(words):
    """The simulated swipes of [words], in order, the same every time (fixed seeds), with the speed and sample step used."""
    _, _, _, lengths, gaps = real_stats()
    speed, step = med(lengths), max(1, round(med(gaps)))
    entries = []
    index = 0
    for word in words:
        for level in LEVELS:
            for seed in range(SEEDS):
                rng = random.Random(f"{word}-{level}-{seed}")
                path = sampled(waypoints(word, level, rng), speed, step)
                start = 1_000_000 + index * 10_000
                times = [start + i * step for i in range(len(path))]
                events = [{"k": "m", "a": 0, "t": times[0], "s": [[path[0][0], path[0][1], times[0]]]}]
                events += [{"k": "m", "a": 2, "t": t, "s": [[p[0], p[1], t]]} for p, t in zip(path[1:-1], times[1:-1])]
                events.append({"k": "m", "a": 1, "t": times[-1], "s": [[path[-1][0], path[-1][1], times[-1]]]})
                entries.append({"index": index, "prompt": word, "pace": f"short{level}", "events": events})
                index += 1
    return entries, speed, step


def make(words):
    entries, speed, step = simulated(words)
    target = os.environ.get("SYNTH_OUT") or data_path("synth-words2.jsonl")
    with open(target, "w", encoding="utf-8", newline="\n") as out:
        out.write(json.dumps({"format": 1, "synthetic": True}) + "\n")
        for entry in entries:
            out.write(json.dumps(entry) + "\n")
    print("wrote", len(entries), "simulated swipes at", speed, "keys/ms,", step, "ms per sample ->", target)


def report(names):
    for name in names:
        rows = defaultdict(list)
        for line in open(data_path("replay", f"{name}.jsonl"), encoding="utf-8"):
            entry = json.loads(line)
            if "index" in entry:
                rows[(entry["prompt"], float(entry["pace"][5:]))].append(entry)
        print(f"--- {name} (simulated swipes)")
        for (word, level), entries in sorted(rows.items()):
            first = sum(1 for e in entries if e["top"] and e["top"][0].lower() == word)
            three = sum(1 for e in entries if word in [w.lower() for w in e["top"][:3]])
            shown = Counter(e["top"][0].lower() if e["top"] else "(none)" for e in entries).most_common(3)
            print(f"{word:8} short by {level:4} keys: first {first}/{len(entries)}  top 3 {three}/{len(entries)}  usually {shown}")


if __name__ == "__main__":
    if sys.argv[1] == "stats":
        stats()
    elif sys.argv[1] == "make":
        make(sys.argv[2:])
    else:
        report(sys.argv[2:])
