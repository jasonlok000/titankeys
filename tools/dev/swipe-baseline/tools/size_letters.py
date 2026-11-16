#!/usr/bin/env python3
"""Issue #30: is the touch larger at the letters a swipe means than between them? For each kept swipe with touch sizes,
the point nearest each intended letter (in order) is a "letter point"; points more than a key from every intended
letter are "between". Sizes are sensor steps. Compared at the same speed, so slowing down at a letter can't explain it.
Also asks whether size follows how hard the finger turns. Counts only. Usage: size_letters.py attempts.jsonl out.json"""
import bisect
import json
import math
import sys
from collections import defaultdict

ROWS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm']
KEY = {ch: ((c + (1 if r == 2 else 0) + .5) / 10, (r + .5) / 3) for r, row in enumerate(ROWS) for c, ch in enumerate(row)}
path, out_path = sys.argv[1:3]
import os
INNER = os.environ.get('INNER') == '1'
rows = [json.loads(l) for l in open(path, encoding='utf-8')]
pairs, by_speed, turn_rows, per_stroke = [], defaultdict(lambda: [[], []]), [], []
for r in rows:
    word = (r.get('hand') or '').lower()
    pts = [p for p in r['points'] if p[0] is not None]
    sp = r.get('size_points') or []
    if not sp or len(pts) < 6 or len(word) < 2 or not all(c in KEY for c in word) or r.get('superseded_by') is not None:
        continue
    times = [t for t, _ in sp]

    def size_at(t):
        i = bisect.bisect_left(times, t)
        best = min((j for j in (i - 1, i) if 0 <= j < len(sp)), key=lambda j: abs(times[j] - t))
        return sp[best][1] if abs(times[best] - t) <= 30 else None

    x = [p[0] for p in pts]
    y = [p[1] * 4 / 3 for p in pts]  # rows in letter space
    t = [p[2] for p in pts]
    speed = [0.0] + [math.hypot((x[i] - x[i - 1]) * 10, (y[i] - y[i - 1]) * 3) / max(1, t[i] - t[i - 1]) * 1000 for i in range(1, len(x))]
    # Nearest point to each intended letter, in order.
    letter_idx, start = [], 0
    for ch in word:
        kx, ky = KEY[ch]
        d = [math.hypot((x[i] - kx) * 10, (y[i] - ky) * 3) for i in range(start, len(x))]
        j = start + min(range(len(d)), key=d.__getitem__)
        letter_idx.append(j)
        start = j
    near_any = [min(math.hypot((x[i] - KEY[c][0]) * 10, (y[i] - KEY[c][1]) * 3) for c in word) for i in range(len(x))]
    # INNER=1 leaves out the first and last letters and the first and last 80 ms, where the finger lands and lifts.
    inner = INNER and len(word) >= 3
    cut = lambda i: not INNER or (t[i] - t[0] > 80 and t[-1] - t[i] > 80)
    chosen = set(letter_idx[1:-1]) if inner else set(letter_idx)
    letters = [(size_at(t[i]), speed[i]) for i in chosen if cut(i)]
    between = [(size_at(t[i]), speed[i]) for i in range(len(x)) if near_any[i] > 1.0 and cut(i)]
    letters = [(s, v) for s, v in letters if s is not None]
    between = [(s, v) for s, v in between if s is not None]
    if letters and between:
        a = sum(s for s, _ in letters) / len(letters)
        b = sum(s for s, _ in between) / len(between)
        per_stroke.append(a - b)
    for s, v in letters:
        by_speed[min(int(v // 4), 6)][0].append(s)
    for s, v in between:
        by_speed[min(int(v // 4), 6)][1].append(s)
    # How sharply the path turns at each point, against size.
    for i in range(2, len(x) - 2):
        ax, ay = (x[i] - x[i - 2]) * 10, (y[i] - y[i - 2]) * 3
        bx, by = (x[i + 2] - x[i]) * 10, (y[i + 2] - y[i]) * 3
        na, nb = math.hypot(ax, ay), math.hypot(bx, by)
        s = size_at(t[i])
        if na > .2 and nb > .2 and s is not None:
            turn_rows.append((math.degrees(math.acos(max(-1, min(1, (ax * bx + ay * by) / (na * nb))))), s))

out = {'strokes': len(per_stroke)}
mean = lambda v: sum(v) / len(v) if v else float('nan')
if per_stroke:
    larger = sum(d > 0 for d in per_stroke)
    smaller = sum(d < 0 for d in per_stroke)
    out['letter minus between, mean steps'] = round(mean(per_stroke), 3)
    out['strokes letters larger / smaller / equal'] = [larger, smaller, len(per_stroke) - larger - smaller]
    # Sign test, normal approximation.
    n = larger + smaller
    z = (larger - n / 2) / math.sqrt(n / 4) if n else 0
    out['sign test z'] = round(z, 2)
print(f"strokes {len(per_stroke)}; letters minus between: {out.get('letter minus between, mean steps')} steps; "
      f"larger/smaller/equal {out.get('strokes letters larger / smaller / equal')}; sign z {out.get('sign test z')}")
out['by speed (keys per second)'] = {}
for k in sorted(by_speed):
    a, b = by_speed[k]
    out['by speed (keys per second)'][f'{4 * k}-{4 * k + 4}'] = {'letters': round(mean(a), 3), 'between': round(mean(b), 3), 'n': [len(a), len(b)]}
    print(f'  speed {4 * k:>2}-{4 * k + 4:<2} keys/s: letters {mean(a):.2f} (n={len(a)})  between {mean(b):.2f} (n={len(b)})')
bands = [(0, 30), (30, 60), (60, 90), (90, 181)]
out['size by turn'] = {}
for lo, hi in bands:
    v = [s for a, s in turn_rows if lo <= a < hi]
    out['size by turn'][f'{lo}-{hi} degrees'] = {'mean steps': round(mean(v), 3), 'n': len(v)}
    print(f'  turn {lo:>3}-{hi:<3} degrees: mean size {mean(v):.2f} (n={len(v)})')
json.dump(out, open(out_path, 'w'), indent=1)
