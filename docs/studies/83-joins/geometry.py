#!/usr/bin/env python3
"""Issue #83, second look: a richer geometry rule for top-row pairs, with no model at join time.

The pilot (context.py) found that gap, landing speed, carries on, turns back and distance, weighed together, separate one
word from two 0.904 of the time, against 0.807 for today's yes/no Swipe.sameStroke. This fits that weighing
(logistic regression, a weighted sum of numbers the keyboard already has) on the same top-row pairs and asks what it
would do at the owner's bar: no right join lost.

Every number is leave-one-out, including the threshold: for each pair, the weights and the threshold are fitted on the
other pairs, the threshold set just under the lowest right join among them, and the pair is then judged. So a right join
the rule would lose shows up as lost.

Two uses:
  - alone: the rule decides every top-row pair;
  - a veto: a pair joins only when sameStroke joins it and the rule agrees.

Prints counts only. Usage: python3 geometry.py <private dir>  (needs pairs.jsonl and new-rule.txt from run.sh)
"""
import json
import os
import sys
from collections import Counter

import numpy as np

PRIV = sys.argv[1]
TOP_ROW = 1 / 3
MAX_GAP = 400
ONE = {'right join', 'missed join'}
TWO = {'right non-join', 'wrong join'}
OTHERS = ('unclear', 'other')
BASE = ['gap', 'b_start_speed', 'carries_on', 'reverses', 'jump_keys']
WIDER = BASE + ['a_end_speed', 'a_end_vx', 'a_end_vy', 'b_start_vx', 'b_start_vy', 'jump_dx', 'jump_dy', 'a_end_y',
                'b_start_y']
NAMES = {'gap': 'gap, ms', 'b_start_speed': 'landing speed', 'carries_on': 'carries on', 'reverses': 'turns back',
         'jump_keys': 'distance, keys'}


def matrix(rows, feats):
    return np.array([[float(r[f] or 0.0) for f in feats] for r in rows])


def fit(X, y, steps=400):
    mu, sd = X.mean(0), X.std(0) + 1e-9
    Z = np.hstack([(X - mu) / sd, np.ones((len(X), 1))])
    w = np.zeros(Z.shape[1])
    for _ in range(steps):
        p = 1 / (1 + np.exp(-Z @ w))
        w -= .5 * (Z.T @ (p - y) / len(y) + .01 * np.r_[w[:-1], 0])
    return mu, sd, w


def value(model, X):
    mu, sd, w = model
    return np.hstack([(X - mu) / sd, np.ones((len(X), 1))]) @ w


def auc(pos, neg):
    pos, neg = np.asarray(pos), np.asarray(neg)
    return float(((pos[:, None] > neg[None, :]) + .5 * (pos[:, None] == neg[None, :])).mean())


def held_out(pool, feats, gate):
    """Each pair judged by weights and a threshold fitted without it. gate(r): the pairs the rule may join."""
    X = matrix(pool, feats)
    y = np.array([r['label'] in ONE for r in pool], dtype=float)
    rj = np.array([r['label'] == 'right join' and gate(r) for r in pool])
    joins, values = [], []
    for i in range(len(pool)):
        keep = np.arange(len(pool)) != i
        model = fit(X[keep], y[keep])
        v = value(model, X)
        edge = v[keep & rj].min()
        values.append(float(v[i]))
        joins.append(bool(gate(pool[i]) and v[i] >= edge - 1e-9))
    return joins, values


def summary(rows, joins):
    c, t = Counter(), Counter()
    for r, j in zip(rows, joins):
        t[r['label']] += 1
        c[r['label']] += j
    return (f'right joins kept {c["right join"]}/{t["right join"]}, wrong joins joined {c["wrong join"]}/{t["wrong join"]},'
            f' right non-joins joined {c["right non-join"]}/{t["right non-join"]}, missed joins found'
            f' {c["missed join"]}/{t["missed join"]}')


def main():
    pairs = [json.loads(l) for l in open(os.path.join(PRIV, 'pairs.jsonl'))]
    new = {l.split()[0]: l.split()[2] == 'true' for l in open(os.path.join(PRIV, 'new-rule.txt'))}
    top = [p for p in pairs if p['a_end_y'] < TOP_ROW and p['b_start_y'] is not None and p['b_start_y'] < TOP_ROW
           and 0 <= p['gap'] <= MAX_GAP]
    for p in top:
        p['same_now'] = new[p['pair']]
    pool = [p for p in top if p['label'] in ONE | TWO]
    rest = [p for p in top if p['label'] in OTHERS]
    print(f'top-row pairs within {MAX_GAP} ms: {len(top)}; one word or two known: {len(pool)}'
          f' ({sum(p["label"] in ONE for p in pool)} one, {sum(p["label"] in TWO for p in pool)} two)')
    missing = Counter(f for p in pool for f in WIDER if p[f] is None)
    print('  missing values (counted as 0):', dict(missing) or 'none')

    print('\n== Today: Swipe.sameStroke')
    print('  ', summary(pool, [p['same_now'] for p in pool]))
    print('   unclear and other joined:', dict(Counter(p['label'] for p in rest if p['same_now'])))

    y = np.array([p['label'] in ONE for p in pool], dtype=float)
    for name, feats in (('the pilot\'s five', BASE), ('fourteen (adds the end and landing directions and rows)', WIDER)):
        print(f'\n== The weighing, {name}')
        # Separation, leave-one-out, to compare with the pilot's 0.904.
        X = matrix(pool, feats)
        loo = []
        for i in range(len(pool)):
            keep = np.arange(len(pool)) != i
            loo.append(float(value(fit(X[keep], y[keep]), X[i:i + 1])[0]))
        print(f'  separation, leave-one-out: {auc([v for v, p in zip(loo, pool) if p["label"] in ONE], [v for v, p in zip(loo, pool) if p["label"] in TWO]):.3f}')
        for use, gate in (('alone', lambda r: True), ('as a veto on sameStroke', lambda r: r['same_now'])):
            joins, _ = held_out(pool, feats, gate)
            print(f'  {use}, at no right join lost (threshold fitted without the pair):')
            print('    ', summary(pool, joins))
            # Unclear and other pairs aren't in the fit; judge them with the weights and threshold from all of the pool.
            model = fit(X, y)
            v_pool = value(model, X)
            edge = v_pool[[p['label'] == 'right join' and gate(p) for p in pool]].min()
            v_rest = value(model, matrix(rest, feats)) if rest else []
            print('     unclear and other joined:', dict(Counter(p['label'] for p, v in zip(rest, v_rest) if gate(p) and v >= edge)))
            if use == 'as a veto on sameStroke':
                wrong = sorted(v for v, p in zip(v_pool, pool) if p['label'] == 'wrong join' and p['same_now'])
                rjs = sorted(v for v, p in zip(v_pool, pool) if p['label'] == 'right join' and p['same_now'])
                print(f'     room: the lowest right joins sit at {", ".join(f"{v:.2f}" for v in rjs[:4])};'
                      f' wrong joins below the lowest: {sum(w < rjs[0] for w in wrong)} of {len(wrong)}')
        if feats == BASE:
            mu, sd, w = fit(X, y)
            raw = w[:-1] / sd
            print('  the weights in the keyboard\'s own units (fitted on all pairs; the sum + constant, join above the threshold):')
            for f, r in zip(feats, raw):
                print(f'    {NAMES.get(f, f):<16} {r:+.4f}')
            print(f'    constant         {w[-1] - float((w[:-1] * mu / sd).sum()):+.4f}')

    print('\n== One measure at a time, as a veto on sameStroke: keep apart beyond the most extreme right join')
    joined = [p for p in pool if p['same_now']]
    for f in WIDER:
        rj = sorted(float(p[f] or 0) for p in joined if p['label'] == 'right join')
        wr = [float(p[f] or 0) for p in joined if p['label'] == 'wrong join']
        rn = [float(p[f] or 0) for p in joined if p['label'] == 'right non-join']
        low, high = rj[0], rj[-1]
        print(f'  {f:<14} below {low:9.3f}: wrong {sum(v < low for v in wr)}, right non-join {sum(v < low for v in rn)};'
              f' above {high:9.3f}: wrong {sum(v > high for v in wr)}, right non-join {sum(v > high for v in rn)}')


if __name__ == '__main__':
    main()
