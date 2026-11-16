#!/usr/bin/env python3
"""Can a touch be classified as meant or accidental from its own features? And can cut-short strokes be saved?
Usage: intent_experiment.py baseline-dir"""
import json
import os
import sys
import zlib
from collections import Counter

import lightgbm as lgb
import numpy as np

BASE = sys.argv[1]
P = (os.environ.get('TK_PRIVATE') or sys.exit('TK_PRIVATE is not set: source tools/dev/env.sh first'))
rows = [json.loads(l) for l in open(os.path.join(P, 'attempts.jsonl'), encoding='utf-8')]
by_id = {r['id']: r for r in rows}
replay = {}
corpus = {}
for line in open(os.path.join(P, 'corpus.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    corpus[e['id']] = e
for line in open(os.path.join(P, 'replay-4f75dcb-full.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    replay[e['id']] = e
ordered = sorted(rows, key=lambda r: r['start'])
index = {r['id']: i for i, r in enumerate(ordered)}


def units(points):
    return np.array([[p[0] * 10, p[1] * 4, p[2]] for p in points if p[0] is not None and p[1] is not None], dtype=np.float64)


def feats(r):
    pts = units(r['chain_points'])
    if len(pts) < 2:
        return None
    d = np.hypot(np.diff(pts[:, 0]), np.diff(pts[:, 1]))
    dt = np.maximum(np.diff(pts[:, 2]), 1)
    speed = d / dt * 1000
    length = d.sum()
    chord = np.hypot(*(pts[-1, :2] - pts[0, :2]))
    i = index[r['id']]
    prev_gap = r['start'] - ordered[i - 1]['end'] if i else 1e6
    next_gap = ordered[i + 1]['start'] - r['end'] if i + 1 < len(ordered) else 1e6
    scores = r['scores'] or []
    margin = (scores[0][1] - scores[1][1]) if len(scores) > 1 else 99.0
    top = scores[0] if scores else [0, 0, 0, 0]
    wlen = len(r['words'][0]) if r['words'] else 0
    return [len(pts), pts[-1, 2] - pts[0, 2], length, chord, chord / max(length, .01), speed.mean(), speed.max(), pts[0, 0], pts[0, 1], pts[-1, 0], pts[-1, 1],
            prev_gap, next_gap, min(prev_gap, 5000), min(next_gap, 5000), margin, top[1], top[2], top[3], wlen, len(r['chain']), r['contacts'], min(len(r['context']), 200), float(r['context'].strip() == '')]


X, y, groups, ids = [], [], [], []
for r in rows:
    if r['superseded_by'] is not None:
        continue
    f = feats(r)
    if f is None:
        continue
    gone = r['hand'] is None and any(e.startswith('backspace') for e in r['slot_events'])
    X.append(f)
    y.append(int(gone))
    groups.append(r['slot'])
    ids.append(r['id'])
X, y = np.array(X, dtype=np.float32), np.array(y)
print('strokes', X.shape, 'possible accidents', y.sum())
folds = np.array([zlib.crc32(str(g).encode()) % 5 for g in groups])
pred = np.zeros(len(y))
for k in range(5):
    tr, te = folds != k, folds == k
    m = lgb.LGBMClassifier(n_estimators=200, learning_rate=.05, num_leaves=15, min_child_samples=8, subsample=.85, subsample_freq=1, colsample_bytree=.85, scale_pos_weight=4, verbose=-1, random_state=5)
    m.fit(X[tr], y[tr])
    pred[te] = m.predict_proba(X[te])[:, 1]
print('\nWords later removed with nothing in their place: how well can they be told apart?')
order = np.argsort(-pred)
tp = np.cumsum(y[order])
fp = np.cumsum(1 - y[order])
P_, N_ = y.sum(), (1 - y).sum()
out = {}
for recall in (.25, .5, .75, .9, .95):
    i = int(np.searchsorted(tp, recall * P_))
    out[str(recall)] = {'catch_pct': round(100 * tp[i] / P_, 1), 'genuine_words_hit_pct': round(100 * fp[i] / N_, 1), 'genuine_words_hit': int(fp[i])}
    print(f"  catch {100 * tp[i] / P_:.0f}% of them -> also hits {100 * fp[i] / N_:.1f}% of the other strokes ({int(fp[i])})")
auc_num = 0.0
ranks = np.argsort(np.argsort(pred)) + 1
auc = (ranks[y == 1].sum() - P_ * (P_ + 1) / 2) / (P_ * N_)
print(' AUC', round(float(auc), 3))
out['auc'] = round(float(auc), 3)

# ---- cut-short strokes -----------------------------------------------------------------------------------------------
print('\nCut-short first tries: what came next?')
words = json.load(open(os.path.join(P, 'words.json'))) + json.load(open(os.path.join(P, 'words-hand.json')))
cut = [w for w in words if w['gclass'].startswith('stroke cut short')]
next_gaps = []
stats = Counter()
for w in cut:
    r = by_id[w['id']]
    i = index[r['id']]
    nxt = ordered[i + 1] if i + 1 < len(ordered) else None
    gap = nxt['start'] - r['end'] if nxt else None
    next_gaps.append(gap)
    if gap is None:
        stats['last stroke'] += 1
    elif gap <= 150:
        stats['next stroke within 150 ms'] += 1
    elif gap <= 400:
        stats['next stroke 150-400 ms'] += 1
    elif gap <= 1500:
        stats['next stroke 0.4-1.5 s'] += 1
    else:
        stats['next stroke over 1.5 s'] += 1
    last = r['points'][-1]
    stats['ended on top row' if last[1] < .25 else 'ended on another row'] += 1
    stats[f"duration under 200 ms" if w['g']['dur'] < 200 else "duration 200 ms or more"] += 1
out['cut_short'] = dict(stats) | {'n': len(cut)}
print(' ', dict(stats), 'n', len(cut))
json.dump(out, open(os.path.join(BASE, 'report', 'intent-experiment.json'), 'w'), indent=1)
