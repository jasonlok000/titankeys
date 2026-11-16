#!/usr/bin/env python3
"""Issue #30, broad test, parts C and D, on kept swipes recorded after engine 2.0's training data (no model saw them).
C. Pressure-aware reading: the shipped CleverKeys encoder reads each stroke as it is and reshaped by pressure:
   - drop the light lift-off tail (last points whose size fell below the stroke's level)
   - resample in "pressure time", so heavier stretches get more of the 64 points
   - the opposite (lighter stretches get more points)
   - drop the light landing head
   Scored on CleverKeys' own top 1 / top 3 / found, paired against the plain reading (sign test).
D. Pressure features for ranking: a ranker over CleverKeys' candidates and the predictor, with and without pressure
   features (the stroke's level, its spread, and for each candidate the pressure at the points nearest its letters),
   judged by 5 folds in time order (train on the other folds, test on one). Counts only.
Usage: size_reading.py attempts.jsonl corpus.jsonl ck-titan.onnx english.combined lm-dir out.json"""
import json
import math
import os
import re
import sys

import lightgbm as lgb
import numpy as np
import onnxruntime as ort
from transformers import AutoTokenizer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ck_core as C
from size_battery import KEY, corrected, letter_points, load, mean

attempts_path, corpus_path, ck_path, lex_path, lm_dir, out_path = sys.argv[1:7]
cut9 = 1794263700000  # the last stroke of the 2026-11-09 log (20:34): engine 2.0 trained on nothing after it
data = [d for d in corrected(load(attempts_path, corpus_path)) if d[0]['wall'] > cut9]
data.sort(key=lambda d: d[0]['wall'])
print('kept new strokes with sizes', len(data), flush=True)
root, vocab = C.load_lexicon(lex_path)
keys, mask = C.layout_arrays({ch: KEY[ch] for ch in KEY})
sess = C.session(ck_path)


def read(xs, ys, ts):
    return C.recognise(sess, root, vocab, xs, ys, ts, keys, mask)


def pressure_time(pts, rel, power):
    """Time stretched by pressure: each step lasts longer where the finger presses harder (power > 0)."""
    t, out = 0.0, []
    for i, p in enumerate(pts):
        if i:
            dt = p[2] - pts[i - 1][2]
            t += dt * math.exp(power * (rel[i] + rel[i - 1]) / 2)
        out.append(t)
    return out


variants = {}
for d in data:
    r, pts, word, shown, pos, rel, level = d
    xs, ys, ts = [p[0] for p in pts], [p[1] for p in pts], [p[2] - pts[0][2] for p in pts]
    v = {'plain': (xs, ys, ts)}
    n = len(pts)
    tail = n
    while tail > n * .8 and rel[tail - 1] < 0:
        tail -= 1
    head = 0
    while head < n * .2 and rel[head] < 0:
        head += 1
    v['drop light tail'] = (xs[:tail], ys[:tail], ts[:tail])
    v['drop light head'] = (xs[head:], ys[head:], ts[head:])
    v['heavier stretches get more points'] = (xs, ys, pressure_time(pts, rel, 1.0))
    v['lighter stretches get more points'] = (xs, ys, pressure_time(pts, rel, -1.0))
    for name, (x, y, t) in v.items():
        ranked = read(x, y, t)
        variants.setdefault(name, []).append(C.position(ranked, word))
report = {'strokes': len(data), 'C pressure-aware reading (CleverKeys alone)': {}}
base = variants['plain']
for name, ranks in variants.items():
    t1 = 100 * mean([r == 1 for r in ranks])
    t3 = 100 * mean([1 <= r <= 3 for r in ranks])
    found = 100 * mean([r >= 1 for r in ranks])
    better = sum((r == 1) and (b != 1) for r, b in zip(ranks, base))
    worse = sum((r != 1) and (b == 1) for r, b in zip(ranks, base))
    report['C pressure-aware reading (CleverKeys alone)'][name] = {'first': round(t1, 1), 'top3': round(t3, 1), 'found': round(found, 1),
                                                                  'first-choice wins / losses vs plain': [better, worse]}
    print(f'C {name:<36} first {t1:5.1f} top3 {t3:5.1f} found {found:5.1f}  wins/losses vs plain {better}/{worse}', flush=True)

# ---- D: pressure features in a ranker ---------------------------------------------------------------------------------
tok = AutoTokenizer.from_pretrained(lm_dir)
lm = ort.InferenceSession(os.path.join(lm_dir, 'model-int8pc.onnx'), providers=['CPUExecutionProvider'])
start = int(re.search(r'start_token=(\d+)', open(os.path.join(lm_dir, 'lm-info.txt')).read()).group(1))
contexts = {}
for line in open(corpus_path, encoding='utf-8'):
    e = json.loads(line)
    if e['id'].split('-')[0] in ('log', 'hand'):
        contexts[int(e['id'].split('-')[1])] = e['context']
first_tok = {}


def lm_scores(context, words):
    c = context[-500:].rstrip(' ')
    ids = tok(c, add_special_tokens=False)['input_ids'] if c.strip() else []
    logp = lm.run(None, {'input_ids': np.array([ids[-32:] or [start]], dtype=np.int64)})[0][0]
    out = {}
    for w in words:
        best = -99.0
        for form in (w, w[:1].upper() + w[1:]):
            if form not in first_tok:
                t = tok(' ' + form, add_special_tokens=False)['input_ids']
                first_tok[form] = t[0] if t else -1
            if first_tok[form] >= 0:
                best = max(best, float(logp[first_tok[form]]))
        out[w] = best
    return out


rows, groups = [], []
for g, (r, pts, word, shown, pos, rel, level) in enumerate(data):
    xs, ys, ts = [p[0] for p in pts], [p[1] for p in pts], [p[2] - pts[0][2] for p in pts]
    f = C.featurize(xs, ys, ts)
    em = sess.run(['log_emissions'], {'features': f[None] if f.ndim == 2 else f, 'layout_keys': keys, 'layout_mask': mask})[0][0]
    lp = np.concatenate([em[:, :26], em[:, [64]]], 1)
    lp = lp - np.log(np.exp(lp).sum(1, keepdims=True))
    best = C.decode(lp, root)
    cands = sorted(best, key=lambda w: -(best[w] / max(len(w), 1) ** C.GAMMA + C.BETA * len(w) + C.LAMBDA * math.log(vocab[w])))[:40]
    lms = lm_scores(contexts.get(r['id'], ''), cands)
    top_lm = max(lms.values())
    spread = math.sqrt(mean([v * v for v in rel]))
    for rank, w in enumerate(cands):
        final = best[w] / max(len(w), 1) ** C.GAMMA + C.BETA * len(w) + C.LAMBDA * math.log(vocab[w])
        if all(c in KEY for c in w) and len(w) >= 1:
            idx = letter_points(pts, w)
            at = mean([rel[j] for j in idx])
            inner = mean([rel[j] for j in idx[1:-1]]) if len(w) > 2 else 0.0
        else:
            at, inner = 0.0, 0.0
        base_f = [rank, best[w], final, lms[w], lms[w] - top_lm, vocab[w], len(w)]
        rows.append((g, int(w == word), base_f, [level, spread, at, inner, at - mean(rel)]))
    groups.append(len(cands))
X0 = np.array([r[2] for r in rows], dtype=np.float32)
X1 = np.array([r[2] + r[3] for r in rows], dtype=np.float32)
y = np.array([r[1] for r in rows])
qid = np.array([r[0] for r in rows])
n = len(data)
folds = [list(range(k * n // 5, (k + 1) * n // 5)) for k in range(5)]


def run(X):
    ranks = {}
    for test in folds:
        tr = ~np.isin(qid, test)
        order = np.argsort(qid[tr], kind='stable')
        counts = np.bincount(qid[tr][order])
        scores = np.zeros(len(y))
        for seed in (1, 2, 3):
            m = lgb.LGBMRanker(objective='lambdarank', n_estimators=200, learning_rate=.05, num_leaves=15, min_child_samples=8,
                               subsample=.85, subsample_freq=1, colsample_bytree=.85, verbose=-1, random_state=seed)
            m.fit(X[tr][order], y[tr][order], group=counts[counts > 0])
            scores += m.predict(X)
        for q in test:
            idx = np.where(qid == q)[0]
            order_q = idx[np.argsort(-scores[idx], kind='stable')]
            hit = [i for i, j in enumerate(order_q) if y[j] == 1]
            ranks[q] = hit[0] + 1 if hit else -1
    return ranks


r0, r1 = run(X0), run(X1)
for name, rk in (('CleverKeys + predictor ranker', r0), ('same + pressure features', r1)):
    t1 = 100 * mean([rk[q] == 1 for q in rk])
    t3 = 100 * mean([1 <= rk[q] <= 3 for q in rk])
    report.setdefault('D ranker, 5 time-ordered folds', {})[name] = {'first': round(t1, 1), 'top3': round(t3, 1)}
    print(f'D {name:<36} first {t1:5.1f} top3 {t3:5.1f}', flush=True)
wins = sum(r1[q] == 1 and r0[q] != 1 for q in r0)
losses = sum(r1[q] != 1 and r0[q] == 1 for q in r0)
report['D ranker, 5 time-ordered folds']['first-choice wins / losses with pressure'] = [wins, losses]
print('D first-choice wins/losses with pressure', wins, losses)
json.dump(report, open(out_path, 'w'), indent=1)
