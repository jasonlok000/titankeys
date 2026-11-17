#!/usr/bin/env python3
"""Issue #83: what each veto on the rule's top-row joins would trade, if some real breaks may be lost.

Rules, each keeping two touches apart when:
  down     the first touch's last 40 ms head down faster than t (keys per ms, as Swipe.motion measures it)
  gap      the gap is longer than t ms
  still    the second touch lands slower than t keys per ms over its first 20 ms (a still landing)
  combined all position and timing signals weighed together (micro.COMBINED), fitted on the session; held out one
           gap at a time on the session, fitted once on the whole session for the logs

Loss budgets are shares of the real breaks the rule joins: 0, 1, 2, 5 and 10%. The threshold is set two ways:
  - on the logs' 346 right joins (the larger set), then read on the session too;
  - on the session's 39 joined breaks, then read on the logs.

Then the everyday weight, from the logs: right joins and wrong joins per 1,000 swipes, and the net change for each
trade, counting a wrong join cut and a right join lost as one fix each (both take about three actions to repair).

Prints numbers only. Usage: python3 tradeoff.py <touchpad.txt> <repo root> <out dir> <#83 private dir>  (needs numpy)
"""
import importlib.util
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('micro', os.path.join(HERE, 'micro.py'))
M = importlib.util.module_from_spec(spec)
spec.loader.exec_module(M)
C = M.C

BUDGETS = [0, 1, 2, 5, 10]
# (name, signal, veto side): 'above' keeps apart when the signal is above t, 'below' when it's below.
RULES = [('down', 'a_vy_40', 'above'), ('gap', 'gap', 'above'), ('still', 'b_speed_20', 'below'),
         ('combined', 'score', 'below')]


def fit(X, y):
    mu, sd = X.mean(0), X.std(0) + 1e-9
    Z = np.hstack([(X - mu) / sd, np.ones((len(X), 1))])
    w = np.zeros(Z.shape[1])
    for _ in range(500):
        p = 1 / (1 + np.exp(-Z @ w))
        w -= .5 * (Z.T @ (p - y) / len(y) + .01 * np.r_[w[:-1], 0])
    return lambda A: np.hstack([(A - mu) / sd, np.ones((len(A), 1))]) @ w


def threshold(values, side, lose):
    """The t that keeps apart exactly [lose] of these real-break values (fewer when values tie)."""
    v = sorted(values, reverse=(side == 'above'))
    if lose >= len(v):
        return v[-1]
    edge = v[lose]
    # Just inside the (lose+1)-th break, so it stays joined.
    return edge + 1e-9 if side == 'above' else edge - 1e-9


def vetoed(value, side, t):
    return value is not None and (value > t if side == 'above' else value < t)


def count(rows, signal, side, t, label):
    return sum(1 for r in rows if r['label'] == label and vetoed(r.get(signal), side, t))


def main():
    path, repo, out, private = sys.argv[1:5]
    T, lifts, gaps = C.label(path, repo, out)
    session = []
    for g in gaps:
        if g['kind'] == 'next item' or not g['joins']:
            continue
        i = g['i']
        f = M.features(T[i], T[i + 1], g['gap'], lifts[i] - T[i][-1][2])
        f['label'] = 'break' if g['kind'] == 'break' else 'false join'
        session.append(f)
    logs = M.logs_features(private)
    logs = [r for r in logs if r['label'] in ('right join', 'wrong join')]

    # The combined score: higher looks more like a break. Fitted on every labelled session gap (joined or not).
    pool = []
    for g in gaps:
        if g['kind'] == 'next item':
            continue
        i = g['i']
        f = M.features(T[i], T[i + 1], g['gap'])
        f['is_break'] = g['kind'] == 'break'
        f['joined'] = g['joins']
        pool.append(f)
    pool = [r for r in pool if all(r.get(k) is not None for k in M.COMBINED)]
    X = np.array([[r[k] for k in M.COMBINED] for r in pool], dtype=float)
    y = np.array([r['is_break'] for r in pool], dtype=float)
    held_out = []
    for i in range(len(pool)):
        keep = np.arange(len(pool)) != i
        held_out.append(float(fit(X[keep], y[keep])(X[i:i + 1])[0]))
    joined_scores = [(s, r) for s, r in zip(held_out, pool) if r['joined']]
    session_c = [{'label': 'break' if r['is_break'] else 'false join', 'score': s} for s, r in joined_scores]
    whole = fit(X, y)
    for r in logs:
        r['score'] = (float(whole(np.array([[r[k] for k in M.COMBINED]], dtype=float))[0])
                      if all(r.get(k) is not None for k in M.COMBINED) else None)

    def rows_for(name, which):
        if name == 'combined':
            return session_c if which == 'session' else logs
        return session if which == 'session' else logs

    n_s_break = sum(r['label'] == 'break' for r in session)
    n_s_false = sum(r['label'] == 'false join' for r in session)
    n_l_right = sum(r['label'] == 'right join' for r in logs)
    n_l_wrong = sum(r['label'] == 'wrong join' for r in logs)
    swipes = sum(1 for _ in open(os.path.join(private, 'attempts.jsonl'), encoding='utf-8'))
    print(f'session: {n_s_break} joined breaks, {n_s_false} false joins; logs: {n_l_right} right joins, {n_l_wrong} wrong joins,'
          f' {swipes} swipes')
    per = 1000 / swipes
    print(f'everyday, per 1,000 swipes (logs): top-row right joins {n_l_right * per:.1f}, wrong joins {n_l_wrong * per:.1f};'
          f' a trade gains when the share of wrong joins cut is over {n_l_right / n_l_wrong:.1f} times the share of right joins lost')

    print('\n== Threshold set on the logs\' right joins')
    print('  rule      budget  t           logs: right lost  wrong cut   net fixes per 1,000 swipes   session: breaks lost  false cut')
    for name, signal, side in RULES:
        lrows = rows_for(name, 'logs')
        values = [r[signal] for r in lrows if r['label'] == 'right join' and r.get(signal) is not None]
        for b in BUDGETS:
            lose = math.floor(len(values) * b / 100)
            t = threshold(values, side, lose)
            rl, wc = count(lrows, signal, side, t, 'right join'), count(lrows, signal, side, t, 'wrong join')
            srows = rows_for(name, 'session')
            bl, fc = count(srows, signal, side, t, 'break'), count(srows, signal, side, t, 'false join')
            print(f'  {name:<9} {b:>4}%   {t:>10.4f}   {rl:>4} of {len(values):<4}     {wc:>3} of {n_l_wrong}'
                  f'      {(wc - rl) * per:>+6.2f}                      {bl:>3} of {n_s_break}          {fc:>3} of {n_s_false}')

    def weighted(fc, bl):
        """The session's shares at everyday rates: wrong joins cut minus right joins lost, per 1,000 swipes."""
        return (fc / n_s_false * n_l_wrong - bl / n_s_break * n_l_right) * per

    print('\n== Threshold set on the session\'s joined breaks')
    print('  (net: on the logs; x2: a wrong join counted as two fixes; weighted: the session\'s shares at the logs\' rates)')
    print('  rule      budget  breaks lost  t           session: false cut   logs: right lost  wrong cut   net per 1,000   x2      weighted')
    for name, signal, side in RULES:
        srows = rows_for(name, 'session')
        values = [r[signal] for r in srows if r['label'] == 'break' and r.get(signal) is not None]
        for b in BUDGETS:
            lose = math.floor(len(values) * b / 100)
            t = threshold(values, side, lose)
            bl, fc = count(srows, signal, side, t, 'break'), count(srows, signal, side, t, 'false join')
            lrows = rows_for(name, 'logs')
            rl, wc = count(lrows, signal, side, t, 'right join'), count(lrows, signal, side, t, 'wrong join')
            print(f'  {name:<9} {b:>4}%   {bl:>3} of {n_s_break}    {t:>10.4f}   {fc:>3} of {n_s_false}            {rl:>4} of {n_l_right}     {wc:>3} of {n_l_wrong}'
                  f'   {(wc - rl) * per:>+6.2f}          {(2 * wc - rl) * per:>+6.2f}  {weighted(fc, bl):>+6.2f}')

    print('\n== The down rule, fixed thresholds')
    print('  t        session: breaks lost  false cut   logs: right lost  wrong cut  (1.2.0 / 2.x)   net per 1,000   x2      weighted')
    for t in (0.0, 0.0003, 0.0005, 0.0007, 0.001, 0.0015, 0.002):
        bl, fc = count(session, 'a_vy_40', 'above', t, 'break'), count(session, 'a_vy_40', 'above', t, 'false join')
        rl, wc = count(logs, 'a_vy_40', 'above', t, 'right join'), count(logs, 'a_vy_40', 'above', t, 'wrong join')
        old = sum(1 for r in logs if r['label'] == 'wrong join' and r['version'] == '1.2.0' and vetoed(r['a_vy_40'], 'above', t))
        print(f'  {t:.4f}   {bl:>3} of {n_s_break}             {fc:>3} of {n_s_false}      {rl:>4} of {n_l_right}       {wc:>3} of {n_l_wrong}'
              f'   ({old} / {wc - old})       {(wc - rl) * per:>+6.2f}          {(2 * wc - rl) * per:>+6.2f}  {weighted(fc, bl):>+6.2f}')


if __name__ == '__main__':
    main()
