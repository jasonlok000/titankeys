#!/usr/bin/env python3
"""Worth-it check for a Titan model. How does the open encoder do on FUTO's phone swipes, how do those differ from Titan
strokes, and which touchpad effects explain the drop? Usage: futo_check.py baseline-dir model sample.jsonl qwerty.json lexicon"""
import json
import os
import random
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ck_core as C

BASE, MODEL, SAMPLE, LAYOUT, LEXICON = sys.argv[1:6]
SIZE = 900
root, vocab = C.load_lexicon(LEXICON)
sess = C.session(MODEL)
layout = json.load(open(LAYOUT))
centres = {k['letter']: (k['cx'], k['cy']) for k in layout['keys']}
keys, mask = C.layout_arrays(centres)
strokes = []
with open(SAMPLE, encoding='utf-8') as f:
    lines = f.readlines()[:-1]
rng = random.Random(20261109)
rng.shuffle(lines)
for line in lines:
    e = json.loads(line)
    w = C.key(e['word'])
    if not w.isalpha() or len(w) < 2 or w not in vocab or e.get('potentially_invalid_sentence') or not isinstance(e['data'], list) or len(e['data']) < 3:
        continue
    x, y, t = [p['x'] for p in e['data']], [p['y'] for p in e['data']], [p['t'] for p in e['data']]
    t = [v - t[0] for v in t]
    if t[-1] <= 0 or t != sorted(t):
        continue
    strokes.append((w, np.array(x), np.array(y), np.array(t, dtype=float)))
    if len(strokes) == SIZE:
        break
print('FUTO strokes', len(strokes))


def key_path(w):
    out = []
    for ch in w:
        p = (centres[ch][0] * 10, centres[ch][1] * 3)
        if not out or out[-1] != p:
            out.append(p)
    return np.array(out)


def plen(p):
    return float(np.sqrt(((p[1:] - p[:-1]) ** 2).sum(1)).sum()) if len(p) > 1 else 0.0


# ---- how FUTO strokes differ from Titan strokes -------------------------------------------------------------------------
ratios, starts, ends, speeds, durs, dts, reach_over = [], [], [], [], [], [], []
for w, x, y, t in strokes:
    pts = np.stack([x * 10, y * 3], 1)
    ideal = key_path(w)
    if len(ideal) < 2:
        continue
    L = plen(pts)
    ratios.append(L / plen(ideal))
    starts.append(float(np.hypot(*(pts[0] - ideal[0]))))
    ends.append(float(np.hypot(*(pts[-1] - ideal[-1]))))
    speeds.append(L * 1000 / max(t[-1], 1))
    durs.append(t[-1])
    dts += list(np.diff(t))
    for k in ideal:
        reach_over.append(float(np.min(np.hypot(pts[:, 0] - k[0], pts[:, 1] - k[1]))) > 1.0)
stats = {'length_ratio_p50': round(float(np.median(ratios)), 3), 'length_ratio_p10': round(float(np.percentile(ratios, 10)), 3), 'start_over_1key_pct': round(100 * float(np.mean(np.array(starts) > 1)), 1), 'end_over_1key_pct': round(100 * float(np.mean(np.array(ends) > 1)), 1),
         'letter_missed_over_1key_pct': round(100 * float(np.mean(reach_over)), 1), 'speed_p50': round(float(np.median(speeds)), 1), 'duration_p50': float(np.median(durs)), 'sample_ms_p50': float(np.median(dts))}
print('FUTO stroke shape:', stats)
print('Titan prompted for comparison: length_ratio p50 0.88 p10 0.69; start over 1 key ~6%; last letter missed 18%; speed p50 15.6; duration 486; sample 10 ms')

# ---- Titan-like damage, added one effect at a time -----------------------------------------------------------------------


def sim(w, x, y, t, effects, r):
    kx, ky, tt = x * 10, y * 3, t.copy()
    if 'clip' in effects:
        kx, ky = np.clip(kx, .56, 9.49), np.clip(ky, .46, 3)
    if 'short' in effects and len(kx) > 6:
        cut = max(0.0, r.uniform(0, .24))
        d = np.sqrt(np.diff(kx) ** 2 + np.diff(ky) ** 2)
        along = np.concatenate([[0], np.cumsum(d)])
        total = along[-1]
        lo, hi = total * cut * .33, total * (1 - cut * .67)
        keep = (along >= lo) & (along <= hi)
        if keep.sum() >= 3:
            kx, ky, tt = kx[keep], ky[keep], tt[keep] - tt[keep][0]
    if 'right' in effects:
        kx = kx - .33 * np.maximum(0, kx - 6.5)
    if 'hand' in effects:
        kx = kx + r.gauss(0, .35)
        ky = ky + r.gauss(.1, .15)
    if 'drop' in effects and r.random() < .33:
        where = np.where(ky < .85)[0]
        if len(where) > 4:
            s = where[r.randrange(len(where) // 2 + 1)]
            cut = tt[s] + r.uniform(40, 120)
            keep = (tt < tt[s]) | (tt > cut)
            if keep.sum() >= 3:
                kx, ky, tt = kx[keep], ky[keep], tt[keep]
    return kx / 10, ky / 3, tt


variants = [('as recorded on phones', set()), ('+ sensor edges', {'clip'}), ('+ shortened strokes', {'clip', 'short'}), ('+ right side reads left', {'clip', 'short', 'right'}),
            ('+ hand offset', {'clip', 'short', 'right', 'hand'}), ('+ top-row dropouts', {'clip', 'short', 'right', 'hand', 'drop'})]
out = {'stroke_shape': stats, 'sim': {}}
for name, effects in variants:
    r = random.Random(7)
    ranks = []
    for w, x, y, t in strokes:
        sx, sy, st = sim(w, x, y, t, effects, r)
        ranks.append(C.position(C.recognise(sess, root, vocab, sx, sy, st, keys, mask), w))
    out['sim'][name] = C.tally(ranks)
    print(f'{name:<32}', out['sim'][name], flush=True)
json.dump(out, open(os.path.join(BASE, 'report', 'futo-check.json'), 'w'), indent=1)
