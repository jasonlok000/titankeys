#!/usr/bin/env python3
"""Does the model do better when told where your keys really are? Compares the calibrated grid with the skewed key
positions the calibration measured, and with key centres learned from your own strokes.
Usage: skew_check.py baseline-dir model lexicon"""
import json
import os
import sys
from collections import defaultdict

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ck_core as C

BASE, MODEL, LEXICON = sys.argv[1:4]
root, vocab = C.load_lexicon(LEXICON)
sess = C.session(MODEL)
rows = [json.loads(l) for l in open(os.path.join((os.environ.get('TK_PRIVATE') or sys.exit('TK_PRIVATE is not set: source tools/dev/env.sh first')), 'corpus-raw.jsonl'), encoding='utf-8')]
print('strokes', len(rows))

# Measured by the Titan calibration profile: where each letter sits on the sensor, 0-1 across and down.
SENSOR_ROWS = {'qwertyuiop': (.0134, [0.0, .14643, .26969, .34198, .45505, .56812, .66172, .76738, .8823, 1.0]),
               'asdfghjkl': (.3773, [0.0, .14365, .28452, .39203, .46525, .56163, .6645, .78869, .95459]),
               'zxcvbnm': (.6628, [.13346, .27525, .37071, .47637, .56348, .65987, .79703])}
Y_SCALE = .82  # the letter rows end about here on the sensor
GRID = {}
for r, row in enumerate(['qwertyuiop', 'asdfghjkl', 'zxcvbnm']):
    for c, ch in enumerate(row):
        GRID[ch] = ((c + (1 if r == 2 else 0) + .5) / 10, (r + .5) / 3)
PHYSICAL = {}
for row, (y, xs) in SENSOR_ROWS.items():
    for ch, x in zip(row, xs):
        PHYSICAL[ch] = (x, y / Y_SCALE)
print('Grid vs physical, sample:', {c: (round(GRID[c][0], 2), round(PHYSICAL[c][0], 2)) for c in 'qptlzm'})


def fit_offsets(items):
    """Where this person's strokes actually start and end around each letter, on the calibrated grid."""
    bag = defaultdict(list)
    for e in items:
        w = C.key(e['expected'])
        if not w or w[0] not in GRID or w[-1] not in GRID:
            continue
        a, b = e['cal'][0], e['cal'][-1]
        bag[w[0]].append((a[0] - GRID[w[0]][0], a[1] - GRID[w[0]][1]))
        bag[w[-1]].append((b[0] - GRID[w[-1]][0], b[1] - GRID[w[-1]][1]))
    out = dict(GRID)
    for ch, v in bag.items():
        if len(v) >= 5:
            shrink = len(v) / (len(v) + 10)
            out[ch] = (GRID[ch][0] + shrink * float(np.mean([p[0] for p in v])), GRID[ch][1] + shrink * float(np.mean([p[1] for p in v])))
    return out


tune = [e for e in rows if int(e['slot']) % 3 != 2]
hold = [e for e in rows if int(e['slot']) % 3 == 2]
FITTED = fit_offsets(tune)


def run(items, frame, layout, name):
    keys, mask = C.layout_arrays(layout)
    res = defaultdict(list)
    for e in items:
        pts = e[frame]
        if frame == 'pad':
            x, y, t = [p[0] for p in pts], [min(max(p[1], 0) / Y_SCALE, 1) for p in pts], [p[2] for p in pts]
        else:
            x, y, t = [p[0] for p in pts], [p[1] for p in pts], [p[2] for p in pts]
        res[e['set']].append(C.position(C.recognise(sess, root, vocab, x, y, t, keys, mask), C.key(e['expected'])))
    out = {s: C.tally(v) for s, v in res.items()}
    print(f'{name:<58}', out, flush=True)
    return out


out = {'all': {}, 'held_back': {}}
out['all']['calibrated stroke, grid keys (today)'] = run(rows, 'cal', GRID, 'calibrated stroke, grid keys (today)')
out['all']['raw sensor stroke, measured skewed keys'] = run(rows, 'pad', PHYSICAL, 'raw sensor stroke, measured skewed keys')
out['held_back']['calibrated, grid keys'] = run(hold, 'cal', GRID, 'held-back: calibrated, grid keys')
out['held_back']['calibrated, keys learned from your strokes'] = run(hold, 'cal', FITTED, 'held-back: calibrated, keys learned from your strokes')
json.dump(out, open(os.path.join(BASE, 'report', 'skew-check.json'), 'w'), indent=1)
