#!/usr/bin/env python3
"""Issue #30, micro level: ten of the owner's swipes, point by point. Each line shows the key under the read point, the
sensor step (raw and position-corrected), the speed, and marks the points nearest the intended letters (*).
Private: prints typed words, so the output stays on this PC. Usage: size_micro.py attempts.jsonl corpus.jsonl"""
import bisect
import json
import math
import os
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from size_battery import KEY, cell, corrected, letter_points, load, mean

attempts_path, corpus_path = sys.argv[1:3]
cut9 = 1794263700000
data = [d for d in corrected(load(attempts_path, corpus_path)) if d[0]['wall'] > cut9]
rows = {json.loads(l)['id']: json.loads(l) for l in open(attempts_path, encoding='utf-8')}
joined_into = {r['joined_from']: r['id'] for r in rows.values() if r.get('joined_from') is not None}


def nearest_key(x, y):
    return min(KEY, key=lambda c: math.hypot((x - KEY[c][0]) * 10, (y - KEY[c][1]) * 3))


def show(d, why):
    r, pts, word, shown, pos, rel, level = d
    idx = set(letter_points(pts, word))
    n = len(pts)
    print(f'\n=== {why}: "{word}"  shown first: "{shown[0] if shown else ""}"  {"RIGHT" if shown[:1] == [word] else "WRONG"}'
          f'  | level {level:+.2f}  raw mean {mean([p[3] for p in pts]):.2f}  {n} points, {pts[-1][2] - pts[0][2]} ms')
    # Compress runs of points over the same key, keeping each run's sizes.
    runs, cur = [], None
    for i, (x, y, t, s) in enumerate(pts):
        k = nearest_key(x, y)
        sp = 0.0 if i == 0 else math.hypot((x - pts[i - 1][0]) * 10, (y - pts[i - 1][1]) * 3) / max(1, t - pts[i - 1][2]) * 1000
        mark = '*' if i in idx else ''
        if cur and cur[0] == k:
            cur[1].append(f'{s}{mark}')
            cur[2].append(sp)
            cur[3].append(pos[i])
        else:
            cur = [k, [f'{s}{mark}'], [sp], [pos[i]]]
            runs.append(cur)
    line = '   '.join(f'{k}[{" ".join(v)}]' for k, v, _, _ in runs)
    print('  read path, sizes per point (* = nearest an intended letter):')
    for chunk in range(0, len(line), 150):
        print('   ', line[chunk:chunk + 150])
    print('  per key: ' + '  '.join(f'{k} {mean(p):+.1f}@{mean(v):.0f}k/s' for k, _, v, p in runs))
    tail = [pos[i] for i in range(n) if pts[-1][2] - pts[i][2] <= 100]
    before = [pos[i] for i in range(n) if 100 < pts[-1][2] - pts[i][2] <= 300]
    print(f'  end fade (last 100 ms against the 200 ms before): {mean(tail) - mean(before):+.2f} steps' if tail and before else '')
    if r['id'] in joined_into:
        print(f'  BROKEN: the keyboard joined the next piece (attempt {joined_into[r["id"]]}) onto this one')


picks = []
ranked = sorted(data, key=lambda d: d[6])
picks += [(ranked[i], 'one of the lightest') for i in (3, 10)]
picks += [(ranked[-i], 'one of the heaviest') for i in (4, 11)]
broken = [d for d in data if d[0]['id'] in joined_into]
for d in [d for d in data if d[0]['id'] not in joined_into][:0]:
    pass
# broken pieces have no label of their own; show the continuation's word instead
all_rows = [r for r in rows.values() if r['id'] in joined_into and r.get('size_points')]
pw = [d for d in data if 'p' in d[2] and d[2][-1] in 'p' or d[2].startswith('p')]
picks += [(d, 'a P word') for d in pw[:2]]
picks += [(max(data, key=lambda d: math.sqrt(mean([v * v for v in d[5]]))), 'biggest pressure change inside the swipe')]
turny = [d for d in data if len(d[2]) >= 3 and any(KEY[a][1] != KEY[b][1] for a, b in zip(d[2], d[2][1:]))]
picks += [(max(turny, key=lambda d: len(d[2])), 'long word with row changes')]
for d, why in picks:
    show(d, why)
# Broken swipes: the piece before the break, shown raw.
print('\n\n######## two broken swipes (the first piece, which lost the finger)')
usual_cells = defaultdict(list)
for d in data:
    for x, y, t, s in d[1]:
        usual_cells[cell(x, y)].append(s)
usual = {k: mean(v) for k, v in usual_cells.items()}
for r in all_rows[:2]:
    nxt = rows[joined_into[r['id']]]
    sp = r['size_points']
    times = [t for t, _ in sp]
    pts = []
    for x, y, t in [p for p in r['points'] if p[0] is not None]:
        i = min(bisect.bisect_left(times, t), len(sp) - 1)
        pts.append((x, y * 4 / 3, t, sp[i][1]))
    word = (nxt.get('hand') or '').lower()
    print(f'\n=== broken piece, joined word kept as "{word}", gap to the next piece {nxt["start"] - r["end"]} ms')
    line = '   '.join(f'{nearest_key(x, y)}[{s}{"v" if s - usual.get(cell(x, y), 2) < -.5 else ""}]' for x, y, t, s in pts[-14:])
    print('  last 14 points (v = more than half a step below usual there):', line)
    print(f'  ends at row {pts[-1][1] * 3:.2f} (0 = top of the sensor), {pts[-1][2] - pts[0][2]} ms long')
