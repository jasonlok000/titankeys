#!/usr/bin/env python3
"""Does contact size (touch major, logged from swipe engine 2.0 on) tell accidental strokes from real words? Uses the
same accident labels as accident_rules.py: strokes deleted with nothing kept in their place, against kept or
hand-corrected words, outside the known test windows. Reports each size signal's AUC alone and, with stroke time and
length, in a small classifier judged by cross-validation, plus the best rule that costs at most 1% of real words.
Usage: contact_size.py private-dir out.json"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import accident_rules_base as A  # noqa: E402  (the accident labels, shared with accident_rules.py)

P, out_path = sys.argv[1:3]
gone, kept = A.labelled(P)
rows = [(r, 1) for r in gone] + [(r, 0) for r in kept]
rows = [(r, y) for r, y in rows if r.get('sizes')]
if len(rows) < 40 or sum(y for _, y in rows) < 10:
    print(f'Only {len(rows)} labelled strokes have contact sizes ({sum(y for _, y in rows)} accidental). Log a few more days first.')
    sys.exit(0)


def feats(r):
    s = np.array(r['sizes'], dtype=float)
    length, dur, margin = A.info(r)
    head = s[:max(1, len(s) // 4)]
    return [s.mean(), s.max(), s.min(), s.std(), head.mean(), s[-1], length, dur]


NAMES = ['mean size', 'largest size', 'smallest size', 'size spread', 'size at the start', 'size at the end', 'length in keys', 'time']
X = np.array([feats(r) for r, _ in rows])
y = np.array([y for _, y in rows])


def auc(score, labels):
    order = np.argsort(score)
    ranks = np.empty(len(score))
    ranks[order] = np.arange(1, len(score) + 1)
    pos = labels == 1
    return (ranks[pos].sum() - pos.sum() * (pos.sum() + 1) / 2) / (pos.sum() * (~pos).sum())


out = {'labelled': int(len(y)), 'accidental': int(y.sum()), 'signals': {}}
for i, name in enumerate(NAMES):
    a = auc(X[:, i], y)
    out['signals'][name] = round(float(max(a, 1 - a)), 3)
    print(f'{name:<20} AUC {max(a, 1 - a):.3f} ({"larger" if a >= .5 else "smaller"} means accidental)')
from sklearn.ensemble import GradientBoostingClassifier  # noqa: E402
from sklearn.model_selection import cross_val_predict  # noqa: E402

for label, cols in (('time and length only', [6, 7]), ('with contact size', list(range(8)))):
    p = cross_val_predict(GradientBoostingClassifier(n_estimators=150, max_depth=2, random_state=1), X[:, cols], y, cv=5, method='predict_proba')[:, 1]
    a = auc(p, y)
    threshold = np.quantile(p[y == 0], .99)
    caught = float((p[y == 1] > threshold).mean())
    out[label] = {'auc': round(float(a), 3), 'caught at 1% of real words': round(100 * caught, 1)}
    print(f'{label:<22} AUC {a:.3f}, catches {100 * caught:.1f}% of accidents when 1% of real words may be held back')
json.dump(out, open(out_path, 'w'), indent=1)
