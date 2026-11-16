#!/usr/bin/env python3
"""Writes reference cases for CtcWords.java: each stroke's points, the Python features, letter chances and the word list
from ck_core, so the Java version can be checked step for step. Usage: ck_parity.py model.onnx english.combined strokes.jsonl out.jsonl [n]"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ck_core as C

model, dictionary, strokes_path, out_path = sys.argv[1:5]
limit = int(sys.argv[5]) if len(sys.argv) > 5 else 200
root, vocab = C.load_lexicon(dictionary)
grid = {}
for r, row in enumerate(['qwertyuiop', 'asdfghjkl', 'zxcvbnm']):
    for c, ch in enumerate(row):
        grid[ch] = ((c + (1 if r == 2 else 0) + .5) / 10, (r + .5) / 3)
keys, mask = C.layout_arrays(grid)
sess = C.session(model)
with open(out_path, 'w', encoding='utf-8') as out:
    for n, line in enumerate(open(strokes_path, encoding='utf-8')):
        if n >= limit:
            break
        s = json.loads(line)
        f = C.featurize(s['x'], s['y'], s['t'])
        e = sess.run(['log_emissions'], {'features': f, 'layout_keys': keys, 'layout_mask': mask})[0][0].astype(np.float64)
        lp = np.concatenate([e[:, :26], e[:, [64]]], 1)
        lp = lp - (lp.max(1, keepdims=True) + np.log(np.exp(lp - lp.max(1, keepdims=True)).sum(1, keepdims=True)))
        best = C.decode(lp, root)
        scored = sorted(((best[w] / max(len(w), 1) ** C.GAMMA + C.BETA * len(w) + C.LAMBDA * math.log(vocab[w]), w, best[w]) for w in best), reverse=True)[:80]
        out.write(json.dumps({'x': s['x'], 'y': s['y'], 't': s['t'], 'f': f[0].ravel().tolist(), 'lp': lp.ravel().tolist(),
                              'c': [[w, p, sc] for sc, w, p in scored]}) + '\n')
print('wrote', min(limit, n + 1), 'cases')
