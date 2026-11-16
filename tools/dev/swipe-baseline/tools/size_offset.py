#!/usr/bin/env python3
"""Issue #30, the owner's flat-finger idea: when the finger is flat, the sensor reads the middle of the contact, not the
part the owner aims with. If so, the gap between the intended key and the read point should grow with the touch size,
in a steady direction. G1 measures it (all intended letters, end letters, edge keys). G2 tries the fix on swipes no
model saw: shift each point by size, and stretch the ends outward by their size, read with the shipped encoder. The
shift is fitted on the earlier half by time and judged once on the later half. Counts only.
Usage: size_offset.py attempts.jsonl corpus.jsonl ck-titan.onnx english.combined out.json"""
import json
import math
import os
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ck_core as C
from size_battery import KEY, letter_points, load, mean

attempts_path, corpus_path, ck_path, lex_path, out_path = sys.argv[1:6]
cut9 = 1794263700000
strokes = sorted([d for d in load(attempts_path, corpus_path) if d[0]['wall'] > cut9], key=lambda d: d[0]['wall'])
EDGE = set('qaplzm')
out = {'strokes': len(strokes)}

# ---- G1: offset of the read point from the intended key, by touch size ------------------------------------------------
groups = defaultdict(lambda: defaultdict(list))
for r, pts, word, shown in strokes:
    idx = letter_points(pts, word)
    for k, (j, c) in enumerate(zip(idx, word)):
        x, y, t, s = pts[j]
        dx, dy = (x - KEY[c][0]) * 10, (y - KEY[c][1]) * 3
        kinds = ['all']
        kinds.append('first letter' if k == 0 else 'last letter' if k == len(word) - 1 else 'inner letter')
        if c in EDGE:
            kinds.append('edge key ' + c)
        if c in 'qwertyuiop':
            kinds.append('top row')
        for kind in kinds:
            groups[kind][min(s, 4)].append((dx, dy))
g1 = {}
for kind in sorted(groups):
    g1[kind] = {}
    for s in sorted(groups[kind]):
        v = groups[kind][s]
        g1[kind][f'step {s}{"+" if s == 4 else ""}'] = {'n': len(v), 'x offset (keys, + right)': round(mean([a for a, _ in v]), 3),
                                                       'y offset (rows, + down)': round(mean([b for _, b in v]), 3)}
out['G1 read point minus intended key, by touch size'] = g1
for kind in ('all', 'first letter', 'last letter', 'inner letter', 'top row', 'edge key p', 'edge key q', 'edge key a'):
    if kind in g1:
        print(kind, {k: (v['n'], v['x offset (keys, + right)'], v['y offset (rows, + down)']) for k, v in g1[kind].items()})

# ---- G2: correct points by size, read with the encoder --------------------------------------------------------------
root, vocab = C.load_lexicon(lex_path)
keys, mask = C.layout_arrays({ch: KEY[ch] for ch in KEY})
sess = C.session(ck_path)


def variant(pts, ax, ay, ext):
    """Shift each point by (ax, ay) keys per step above 2, and push each end outward by ext keys per step above 2."""
    xs = [p[0] + ax * (p[3] - 2) / 10 for p in pts]
    ys = [p[1] + ay * (p[3] - 2) / 3 for p in pts]
    ts = [p[2] - pts[0][2] for p in pts]
    if ext:
        for end in (0, -1):
            other = 3 if end == 0 else -4
            dx, dy = (xs[end] - xs[other]) * 10, (ys[end] - ys[other]) * 3
            n = math.hypot(dx, dy)
            if n > .05:
                push = ext * max(0, pts[end][3] - 1)
                ex, ey = xs[end] + dx / n * push / 10, ys[end] + dy / n * push / 3
                if end == 0:
                    xs, ys, ts = [ex] + xs, [ey] + ys, [0] + [t + 30 for t in ts]
                else:
                    xs, ys, ts = xs + [ex], ys + [ey], ts + [ts[-1] + 30]
    xs = [min(1, max(0, v)) for v in xs]
    ys = [min(1, max(0, v)) for v in ys]
    return xs, ys, ts


def score(part, ax, ay, ext):
    ranks = []
    for r, pts, word, shown in part:
        ranked = C.recognise(sess, root, vocab, *variant(pts, ax, ay, ext), keys, mask)
        ranks.append(C.position(ranked, word))
    return ranks


half = len(strokes) // 2
early, late = strokes[:half], strokes[half:]
grid = [(0, 0, 0)] + [(ax, ay, 0) for ax in (-.15, .15) for ay in (-.15, .15)] + [(0, 0, e) for e in (.15, .3)] + [(.15, -.15, .15)]
fits = {}
for g in grid:
    rk = score(early, *g)
    fits[g] = (mean([x == 1 for x in rk]), mean([1 <= x <= 3 for x in rk]))
    print('fit on earlier half', g, f'first {100 * fits[g][0]:.1f} top3 {100 * fits[g][1]:.1f}', flush=True)
best = max(fits, key=lambda g: fits[g])
base_late, best_late = score(late, 0, 0, 0), score(late, *best)
wins = sum(b == 1 and a != 1 for a, b in zip(base_late, best_late))
losses = sum(b != 1 and a == 1 for a, b in zip(base_late, best_late))
out['G2 size correction, fitted on the earlier half'] = {str(g): [round(100 * v[0], 1), round(100 * v[1], 1)] for g, v in fits.items()}
out['G2 judged on the later half'] = {'chosen (x per step, y per step, end push per step)': list(best), 'n': len(late),
                                      'plain first / top3': [round(100 * mean([x == 1 for x in base_late]), 1), round(100 * mean([1 <= x <= 3 for x in base_late]), 1)],
                                      'corrected first / top3': [round(100 * mean([x == 1 for x in best_late]), 1), round(100 * mean([1 <= x <= 3 for x in best_late]), 1)],
                                      'first-choice wins / losses': [wins, losses]}
print('later half', out['G2 judged on the later half'])
json.dump(out, open(out_path, 'w'), indent=1)
