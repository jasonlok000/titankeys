#!/usr/bin/env python3
"""Issue #30: can the pressure fade at the end of a swipe tell a broken swipe (the finger slid off the sensor) from a
finished word? Looks at every pair of swipes close enough to be joined (the second starts within 400 ms, near the top
edge, within 3.5 keys, after the first ended on the top row), and labels each from what the owner kept:
  right join     the keyboard joined them and the joined word was kept
  wrong join     the keyboard joined them and the joined word was deleted
  missed break   not joined, and both words were deleted (the owner retyped)
  right separate not joined, and both words were kept
Then compares the first swipe's end fade (position-corrected size, last 100 ms minus the 200 ms before) across the
labels. Counts only. Usage: join_pressure.py attempts.jsonl out.json"""
import json
import math
import sys
from collections import defaultdict

import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from size_battery import cell, mean, spearman

path, out_path = sys.argv[1:3]
rows = sorted((json.loads(l) for l in open(path, encoding='utf-8')), key=lambda r: r['start'])
cells = defaultdict(list)
for r in rows:
    for x, y, t in [p for p in r['points'] if p[0] is not None][:0]:
        pass
for r in rows:
    sp = {t: s for t, s in (r.get('size_points') or [])}
    for x, y, t in [p for p in r['points'] if p[0] is not None]:
        if t in sp:
            cells[cell(x, y * 4 / 3)].append(sp[t])
usual = {k: mean(v) for k, v in cells.items()}


def fade(r):
    sp = r.get('size_points') or []
    pts = [p for p in r['points'] if p[0] is not None]
    if not sp or len(pts) < 4:
        return float('nan')
    times = sorted(t for t, _ in sp)
    size = dict((t, s) for t, s in sp)
    end = pts[-1][2]
    last, before = [], []
    for x, y, t in pts:
        near = min(times, key=lambda u: abs(u - t))
        if abs(near - t) > 30:
            continue
        v = size[near] - usual.get(cell(x, y * 4 / 3), 2)
        (last if end - t <= 100 else before if end - t <= 300 else []).append(v)
    return mean(last) - mean(before) if last and before else float('nan')


def key(w):
    return (w or '').lower().replace("'", '').replace('’', '')


by_id = {r['id']: r for r in rows}
labels = defaultdict(list)
for a, b in zip(rows, rows[1:]):
    pa = [p for p in a['points'] if p[0] is not None]
    pb = [p for p in b['points'] if p[0] is not None]
    if not pa or not pb:
        continue
    gap = b['start'] - a['end']
    joined = b.get('joined_from') == a['id']
    jump = math.hypot((pb[0][0] - pa[-1][0]) * 10, (pb[0][1] - pa[-1][1]) * 4)
    near_edge = pa[-1][1] * 4 / 3 < 1 / 3 and pb[0][1] * 4 / 3 < .267
    if not joined and not (0 <= gap <= 400 and near_edge and jump <= 3.5):
        continue
    kept = lambda r: r['hand'] is not None and r['words'] and key(r['hand']) == key(r['words'][0])
    if joined:
        label = 'right join' if b['hand'] is not None else 'wrong join'
    elif a['hand'] is None and b['hand'] is None:
        label = 'missed break'
    elif kept(a) and kept(b):
        label = 'right separate'
    else:
        continue
    f = fade(a)
    if f == f:
        labels[label].append((f, gap))
out = {}
for label, v in sorted(labels.items()):
    out[label] = {'n': len(v), 'end fade (steps)': round(mean([f for f, _ in v]), 3), 'gap (ms)': round(mean([g for _, g in v]), 1),
                  'share with a fade of 0.5 step or more': round(mean([f <= -.5 for f, _ in v]), 3)}
    print(f"{label:<15} n={len(v):<4} fade {out[label]['end fade (steps)']:+.3f}  gap {out[label]['gap (ms)']:6.1f} ms  "
          f"fade of 0.5+ step: {100 * out[label]['share with a fade of 0.5 step or more']:.0f}%")
should = [(f, 1.0) for f, _ in labels['right join'] + labels['missed break']] + [(f, 0.0) for f, _ in labels['right separate'] + labels['wrong join']]
rho, z = spearman([f for f, _ in should], [y for _, y in should])
out['fade against "should have been joined"'] = {'rho': round(rho, 3), 'z': round(z, 1), 'n': len(should)}
print('fade against should-join: rho', round(rho, 3), 'z', round(z, 1), 'n', len(should))
json.dump(out, open(out_path, 'w'), indent=1)
