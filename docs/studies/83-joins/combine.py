#!/usr/bin/env python3
"""Issue #83: does the recogniser's fit and the context model add to the micro signals, at each loss budget?

For each top-row pair the rule joins, three groups of signals:
  micro    the position and timing signals of micro.py (micro.COMBINED)
  fit      the app's recogniser on the joined path against the two halves, from the phone replay: the joined reading's
           CTC fit against the two halves' fits summed (a joined path like "elephant" + "ontario" fits no word well,
           while each half fits one), the same for the total score, and how sure each reading is (its lead over
           the next word)
  context  the on-device DistilGPT-2 margin, joined word against the two words, text before only (all the keyboard
           has when it decides), as in context.py with all three readings from the phone

Models, each a weighted sum (logistic regression) with a veto when the sum falls below a threshold:
  down               the down veto alone (micro.py): the first touch's last 40 ms heading down
  micro              all micro signals
  micro + fit
  micro + fit + context
  fit + context      the readings alone, no geometry

Everything is nested leave-one-out: for each pair, the weights and each budget's threshold are fitted on the other
pairs, the threshold set to lose that share of their right joins, and the pair is then judged.

  combine.py export <touchpad.txt> <repo root> <out dir>
      writes <out>/capture-corpus.jsonl for the baseline's replay.sh: each labelled gap of the session read joined,
      first alone and second alone. capture-replay.sh runs it on the phone.
  combine.py score <touchpad.txt> <repo root> <out dir> <#83 private dir> [capture replay-out.jsonl]
      the logs (with the phone replay from phone-replay.sh), and the session once its replay exists.

Prints numbers only. Needs numpy; the session's context margin also needs onnxruntime (the apostrophe study's venv).
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
FIT = ['fit_ctc', 'fit_score', 'j_sure', 'a_sure', 'b_sure']
CONTEXT = ['ctx_margin']
MODELS = [('down', ['a_vy_40']), ('micro', M.COMBINED), ('micro + fit', M.COMBINED + FIT),
          ('micro + fit + context', M.COMBINED + FIT + CONTEXT), ('fit + context', FIT + CONTEXT)]
MAX_MILLIS = 9000


def word(w):
    w = (w or '').strip().strip('.,!?;:"')
    return w.lower() if w else None


def labelled(path, repo, out):
    """The session's labelled gaps, with each touch's points and the micro signals."""
    T, lifts, gaps = C.label(path, repo, out)
    rows = []
    for g in gaps:
        if g['kind'] == 'next item':
            continue
        i = g['i']
        f = M.features(T[i], T[i + 1], g['gap'])
        f.update({'id': str(i), 'label': 'right join' if g['kind'] == 'break' else 'wrong join', 'joins': g['joins']})
        rows.append((f, T[i], T[i + 1]))
    return rows


def export(path, repo, out):
    n = 0
    with open(os.path.join(out, 'capture-corpus.jsonl'), 'w') as fh:
        for f, a, b in labelled(path, repo, out):
            for kind, pts in (('J', a + b), ('A', a), ('B', b)):
                base = pts[0][2]
                t = [round(p[2] - base, 1) for p in pts]
                if t[-1] > MAX_MILLIS:
                    continue
                fh.write(json.dumps({'id': kind + '|' + f['id'], 'set': 'capture83', 'style': f['label'], 'slot': 0,
                                     'first': True, 'expected': '', 'context': '', 'live': [],
                                     'x': [round(p[0], 5) for p in pts], 'y': [round(p[1], 5) for p in pts], 't': t}) + '\n')
                n += 1
    print('capture replay lines', n)


def readings(path):
    """id -> (top word, its total score, its CTC score, its lead over the next word)."""
    out = {}
    for line in open(path, encoding='utf-8'):
        e = json.loads(line)
        s = e.get('scores') or []
        if not s:
            continue
        lead = s[0][1] - s[1][1] if len(s) > 1 else 10.0
        out[e['id']] = (word(s[0][0]), s[0][1], s[0][2], lead)
    return out


def add_fit(f, key, reads):
    j, a, b = (reads.get(k + '|' + key) for k in 'JAB')
    if None in (j, a, b):
        return False
    f.update({'fit_ctc': j[2] - (a[2] + b[2]), 'fit_score': j[1] - (a[1] + b[1]), 'j_sure': j[3], 'a_sure': a[3],
              'b_sure': b[3], 'words': (j[0], a[0], b[0])})
    return True


def fit(X, y):
    mu, sd = X.mean(0), X.std(0) + 1e-9
    Z = np.hstack([(X - mu) / sd, np.ones((len(X), 1))])
    w = np.zeros(Z.shape[1])
    for _ in range(500):
        p = 1 / (1 + np.exp(-Z @ w))
        w -= .5 * (Z.T @ (p - y) / len(y) + .01 * np.r_[w[:-1], 0])
    return lambda A: np.hstack([(A - mu) / sd, np.ones((len(A), 1))]) @ w


def scorer(name, feats, rows):
    """A function from training rows to a scoring function; a higher score looks more like a right join."""
    if name == 'down':
        return lambda train: (lambda A: -A[:, 0])
    return lambda train: fit(np.array([[r[k] for k in feats] for r in train], dtype=float),
                             np.array([r['label'] == 'right join' for r in train], dtype=float))


def thresholds(scores, right, budgets):
    s = sorted(v for v, r in zip(scores, right) if r)
    return {b: s[min(math.floor(len(s) * b / 100), len(s) - 1)] - 1e-9 for b in budgets}


def nested(rows, name, feats):
    """Per budget: right joins lost and wrong joins cut, each pair judged by weights and a threshold fitted without it."""
    X = np.array([[r[k] for k in feats] for r in rows], dtype=float)
    right = np.array([r['label'] == 'right join' for r in rows])
    make = scorer(name, feats, rows)
    lost, cut = {b: 0 for b in BUDGETS}, {b: 0 for b in BUDGETS}
    for i in range(len(rows)):
        keep = np.arange(len(rows)) != i
        score = make([r for r, k in zip(rows, keep) if k])
        v = score(X)
        thr = thresholds(v[keep], right[keep], BUDGETS)
        for b in BUDGETS:
            if v[i] < thr[b]:
                lost[b] += right[i]
                cut[b] += not right[i]
    return lost, cut


def table(title, rows, net):
    """net(cut, lost, right joins, wrong joins): the change in fixes per 1,000 swipes."""
    n_r = sum(r['label'] == 'right join' for r in rows)
    n_w = len(rows) - n_r
    print(f'\n== {title}: {n_r} right joins, {n_w} wrong joins')
    print('  model                    budget  right lost  wrong cut   net per 1,000 swipes')
    results = {}
    for name, feats in MODELS:
        lost, cut = nested(rows, name, feats)
        results[name] = (lost, cut)
        for b in BUDGETS:
            print(f'  {name:<24} {b:>4}%    {lost[b]:>4} of {n_r:<4} {cut[b]:>3} of {n_w:<3}  {net(cut[b], lost[b], n_r, n_w):>+7.2f}')
    return results


def apply(train, test, per, title):
    """Weights and thresholds from all of [train], applied to [test]."""
    n_r = sum(r['label'] == 'right join' for r in test)
    n_w = len(test) - n_r
    print(f'\n== {title}: {n_r} breaks the rule joins, {n_w} false joins')
    print('  model                    budget  breaks lost  false cut   weighted net per 1,000 (logs\' rates)')
    for name, feats in MODELS:
        score = scorer(name, feats, train)(train)
        Xtr = np.array([[r[k] for k in feats] for r in train], dtype=float)
        thr = thresholds(score(Xtr), [r['label'] == 'right join' for r in train], BUDGETS)
        v = score(np.array([[r[k] for k in feats] for r in test], dtype=float))
        for b in BUDGETS:
            lost = sum(1 for x, r in zip(v, test) if x < thr[b] and r['label'] == 'right join')
            cut = sum(1 for x, r in zip(v, test) if x < thr[b] and r['label'] != 'right join')
            print(f'  {name:<24} {b:>4}%    {lost:>3} of {n_r:<4} {cut:>3} of {n_w:<3}  {per(cut / n_w, lost / n_r):>+7.2f}')


def score_all(path, repo, out, private, capture_out=None):
    logs = [r for r in M.logs_features(private) if r['label'] in ('right join', 'wrong join')]
    reads = readings(os.path.join(private, 'replay-out.jsonl'))
    ctx = {r['pair']: r.get('margin_phone') for r in map(json.loads, open(os.path.join(private, 'context-phone-pairs.jsonl')))}
    swipes = sum(1 for _ in open(os.path.join(private, 'attempts.jsonl'), encoding='utf-8'))
    per = 1000 / swipes
    usable = []
    for r in logs:
        if add_fit(r, r['pair'], reads) and ctx.get(r['pair']) is not None:
            r['ctx_margin'] = ctx[r['pair']]
            if all(r.get(k) is not None for k in M.COMBINED + FIT + CONTEXT):
                usable.append(r)
    print(f'logs: top-row joins with every signal: {len(usable)} of {len(logs)}')
    table("The logs, the rule's top-row joins", usable, lambda c, l, n_r, n_w: (c - l) * per)

    if capture_out:
        n_r = sum(r['label'] == 'right join' for r in usable)
        n_w = len(usable) - n_r
        rate_r, rate_w = n_r * per, n_w * per
        reads = readings(capture_out)
        sys.argv = [sys.argv[0], repo]
        wspec = importlib.util.spec_from_file_location('were', os.path.join(HERE, '..', '73-were-study.py'))
        W = importlib.util.module_from_spec(wspec)
        wspec.loader.exec_module(W)
        session = []
        for f, a, b in labelled(path, repo, out):
            if not f['joins'] or not add_fit(f, f['id'], reads):
                continue
            j, first, second = f['words']
            if None in (j, first, second):
                continue
            # No text before in the session: the margin opens a sentence.
            f['ctx_margin'] = W.score('', j.capitalize()) - W.score('', (first + ' ' + second).capitalize())
            if all(f.get(k) is not None for k in M.COMBINED + FIT + CONTEXT):
                session.append(f)
        apply(usable, session, lambda c, l: (c * rate_w - l * rate_r), 'The session, weights and thresholds from the logs')
        # The session's shares at the logs' everyday rates.
        table('The session on its own, nested leave-one-out', session,
              lambda c, l, n_r_s, n_w_s: c / n_w_s * rate_w - l / n_r_s * rate_r)


if __name__ == '__main__':
    if sys.argv[1] == 'export':
        export(*sys.argv[2:5])
    else:
        score_all(*sys.argv[2:7])
