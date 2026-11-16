#!/usr/bin/env python3
"""Blind spots at the sensor's edges: Q and A on the left, P on the right, with neighbouring letters for comparison.
Usage: edge_letters.py private-dir out.json"""
import json
import os
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

P, out_path = sys.argv[1:3]
corpus = {}
for line in open(os.path.join(P, 'corpus.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    if e['first'] and e['set'] in ('prompted', 'hand', 'recorder'):
        corpus[e['id']] = e
replay = {json.loads(l)['id']: json.loads(l) for l in open(os.path.join(P, 'replay-4f75dcb-full.jsonl'), encoding='utf-8')}


def key(w):
    return R.norm(w).replace("'", '')


LEFT, RIGHT = .0565, .9485
strokes = []
for i, e in corpus.items():
    if i not in replay:
        continue
    w = key(e['expected'])
    if not w or not all(c in R.KEY for c in w):
        continue
    top = [key(t) for t in replay[i]['top']]
    pts = [(x * 10, y * 3, t) for x, y, t in zip(e['x'], e['y'], e['t'])]
    keys = R.ideal_points(w)
    if len(pts) < 2 or not keys:
        continue
    strokes.append({'w': w, 'ok1': top[:1] == [w], 'ok3': w in top, 'pts': pts, 'keys': keys, 'reach': R.monotone_reach(pts, keys), 'raw': e, 'set': e['set'],
                    'compressed': ''.join(c for n, c in enumerate(w) if n == 0 or c != w[n - 1])})


def nearest(x, y):
    return min(R.KEY, key=lambda c: (R.KEY[c][0] - x) ** 2 + (R.KEY[c][1] - y) ** 2)


def pct(items, name):
    return round(100 * sum(r[name] for r in items) / len(items), 1) if items else None


out = {}
print('letter | first letter: n first% top3% lands-on-key% | last letter: n first% top3% lands-on-key% | middle: n, first%, missed by over 1 key% | contains: n first% top3%')
for ch in 'qapwsoli':
    first = [r for r in strokes if r['compressed'][0] == ch]
    last = [r for r in strokes if r['compressed'][-1] == ch and len(r['compressed']) > 1]
    middle, reach_mid = [], []
    for r in strokes:
        for n, c in enumerate(r['compressed'][1:-1], 1):
            if c == ch:
                middle.append(r)
                reach_mid.append(r['reach'][n])
    any_ = [r for r in strokes if ch in r['compressed']]
    land_f = round(100 * sum(nearest(*r['pts'][0][:2]) == ch for r in first) / len(first), 1) if first else None
    land_l = round(100 * sum(nearest(*r['pts'][-1][:2]) == ch for r in last) / len(last), 1) if last else None
    miss = round(100 * sum(v > 1 for v in reach_mid) / len(reach_mid), 1) if reach_mid else None
    row = {'first': [len(first), pct(first, 'ok1'), pct(first, 'ok3'), land_f], 'last': [len(last), pct(last, 'ok1'), pct(last, 'ok3'), land_l], 'middle': [len(middle), pct(middle, 'ok1'), miss], 'any': [len(any_), pct(any_, 'ok1'), pct(any_, 'ok3')]}
    out[ch] = row
    print(f"  {ch} | {row['first']} | {row['last']} | {row['middle']} | {row['any']}")
base = [r for r in strokes if not set(r['compressed']) & set('qap')]
print('words with none of Q A P:', len(base), pct(base, 'ok1'), pct(base, 'ok3'))
print('\nHow strokes meet the sensor edges (key units: left edge 0.56, right edge 9.49):')
for label, test in (('contains A', lambda r: 'a' in r['compressed']), ('contains Q', lambda r: 'q' in r['compressed']), ('contains P', lambda r: 'p' in r['compressed']), ('none of Q A P', lambda r: not set(r['compressed']) & set('qap'))):
    items = [r for r in strokes if test(r)]
    pin_left = [sum(1 for p in r['pts'] if p[0] <= LEFT * 10) for r in items]
    pin_right = [sum(1 for p in r['pts'] if p[0] >= RIGHT * 10) for r in items]
    print(f"  {label:<14} n={len(items):<4} strokes pinned on left edge {round(100 * sum(v > 0 for v in pin_left) / len(items), 1)}%  right edge {round(100 * sum(v > 0 for v in pin_right) / len(items), 1)}%  median left-most x {R.percentile([min(p[0] for p in r['pts']) for r in items], .5):.2f}  median right-most x {R.percentile([max(p[0] for p in r['pts']) for r in items], .5):.2f}")
print('\nOnly words where A is the first letter or last letter, by whether the stroke touches the left edge:')
for label, group in (('A first', [r for r in strokes if r['compressed'][0] == 'a']), ('A last', [r for r in strokes if r['compressed'][-1] == 'a' and len(r['compressed']) > 1]), ('A middle', [r for r in strokes if 'a' in r['compressed'][1:-1]])):
    pinned = [r for r in group if any(p[0] <= LEFT * 10 for p in r['pts'])]
    not_pinned = [r for r in group if not any(p[0] <= LEFT * 10 for p in r['pts'])]
    print(f"  {label}: touches edge n={len(pinned)} first {pct(pinned,'ok1')} top3 {pct(pinned,'ok3')} | stays inside n={len(not_pinned)} first {pct(not_pinned,'ok1')} top3 {pct(not_pinned,'ok3')}")
json.dump(out, open(out_path, 'w'), indent=1)
