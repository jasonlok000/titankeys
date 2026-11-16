#!/usr/bin/env python3
"""Each try in a Space gestures export, touch by touch: where it landed (row, column), how long it lasted, how far it went
and how far sideways and up, the gap before it, key presses, and what happened. Numbers only.
Usage: space_tries.py export.jsonl"""
import json
import math
import sys

rows = [json.loads(l) for l in open(sys.argv[1], encoding='utf-8')]
for n, attempt in enumerate(rows[1:]):
    touches, current = [], None
    keys = []
    for kind, t, data in attempt['events']:
        if kind == 'key' and data['d'] and data['repeat'] == 0:
            keys.append((t, data['c']))
        if kind != 'm':
            continue
        pts = [p for p in data['p'] if p[0] is not None]
        if data['a'] == 0:
            current = {'down': t, 'pts': list(pts)}
        elif current is not None:
            current['pts'] += pts
            if data['a'] in (1, 3):
                current['up'] = t
                touches.append(current)
                current = None
    mark = 'ok ' if attempt['worked'] else 'BAD'
    print(f"#{n:<2} {mark} {attempt['kind']:<6} -> {attempt['result']:<30} keys {[(t - attempt['start'], c) for t, c in keys]}")
    last_up = None
    for c in touches:
        if not c['pts']:
            print('      touch with no point')
            continue
        land = c['pts'][0]
        across = max(abs(p[0] - land[0]) * 10 for p in c['pts'])
        up = max((land[1] - p[1]) * 4 for p in c['pts'])
        travel = max(math.hypot((p[0] - land[0]) * 10, (p[1] - land[1]) * 4) for p in c['pts'])
        gap = '' if last_up is None else f"gap {c['down'] - last_up:>4} ms"
        print(f"      row {land[1] * 4:4.2f} col {land[0] * 10:4.2f}  {c['up'] - c['down']:>4} ms  travel {travel:4.2f}"
              f"  across {across:4.2f} up {up:4.2f}  size {max(p[2] for p in c['pts'])}  {gap}")
        last_up = c['up']
