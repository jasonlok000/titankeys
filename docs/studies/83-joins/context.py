#!/usr/bin/env python3
"""Issue #83 pilot: can the context model tell a right join from two separate words, at the top row?

For every saved pair whose first swipe ends on the top row and whose second starts there, within 400 ms, it scores two
readings with the on-device DistilGPT-2 the way the were/we're study does (73-were-study.py: the app's byte BPE, the last
32 tokens, the window back to the last sentence mark, at most 10 words):

    margin = score(text before + joined word) - score(text before + first word + second word), in nats

once with the text before only (all the keyboard has when it decides) and once with up to 2 words after.

Readings come from the log where it has them: the first swipe's word, the second swipe's word when it stood alone, and the
joined word when the pair was joined. The rest (the joined reading of a pair kept apart, the second swipe alone in a pair
that was joined) come from the app's CTC recogniser (android/assets/swipe2/ck-titan.onnx) run on this PC with the
baseline's ck_core.py. A second run reads all three on the PC, for comparison.

Prints counts and margins only, never text. Writes per-pair numbers (no words) to <private>/context-pairs.jsonl.
Usage: python3 context.py <repo root> <private dir> <baseline tools dir>
Needs numpy and onnxruntime (the apostrophe study's venv has both).
"""
import importlib.util
import json
import math
import os
import sys
import time
from collections import Counter

import numpy as np

ROOT, PRIV, TOOLS = sys.argv[1:4]
HERE = os.path.dirname(os.path.abspath(__file__))
# The were/we're study's tokenizer, model and window, unchanged. It reads the repo root from sys.argv[1].
spec = importlib.util.spec_from_file_location('were', os.path.join(HERE, '..', '73-were-study.py'))
W = importlib.util.module_from_spec(spec)
spec.loader.exec_module(W)
sys.path.insert(0, TOOLS)
import ck_core as C  # noqa: E402

TOP_ROW = 1 / 3
MAX_GAP = 400
AFTER_WORDS = 2
AFTER_MILLIS = 5000
GROUPS = ['right join', 'wrong join', 'unclear', 'missed join', 'right non-join', 'other']
# One word wanted against two: the owner kept one word, or kept two.
ONE = {'right join', 'missed join'}
TWO = {'right non-join', 'wrong join'}

# --- the recogniser on this PC ---
ROWS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm']
GRID = {ch: ((c + (1 if r == 2 else 0) + .5) / 10, (r + .5) / 3) for r, row in enumerate(ROWS) for c, ch in enumerate(row)}
KEYS, MASK = C.layout_arrays(GRID)
LEX_ROOT, VOCAB = C.load_lexicon(os.path.join(ROOT, 'android/assets/swipe/english.combined'))
CTC = C.session(os.path.join(ROOT, 'android/assets/swipe2/ck-titan.onnx'))


def letter_space(points):
    points = [p for p in points if p[0] is not None and p[1] is not None]
    return [p[0] for p in points], [p[1] * 4 / 3 for p in points], [p[2] for p in points]


def read(points):
    """The CTC recogniser's top word for a path, or None."""
    x, y, t = letter_space(points)
    if len(x) < 2 or t[-1] - t[0] > 9000:
        return None
    ranked = C.recognise(CTC, LEX_ROOT, VOCAB, x, y, t, KEYS, MASK)
    return ranked[0] if ranked else None


# --- the text ---
def word(w):
    w = (w or '').strip().strip('.,!?;:"')
    return w.lower() if w else None


def cased(words, opening):
    words = ['I' if w == 'i' else w for w in words]
    if opening and words:
        words[0] = words[0][:1].upper() + words[0][1:]
    return words


def margin(before, joined, first, second, after):
    ctx = W.window(before)
    opening = ctx == ''
    lead = '' if opening else ' '
    tail = (' ' + ' '.join(after)) if after else ''
    one = score(ctx, lead + ' '.join(cased([joined], opening)) + tail)
    two = score(ctx, lead + ' '.join(cased([first, second], opening)) + tail)
    return one - two


def score(ctx, text):
    return W.score(ctx, text)


def main():
    rows = {}
    for line in open(os.path.join(PRIV, 'attempts.jsonl'), encoding='utf-8'):
        a = json.loads(line)
        rows[a['id']] = a
    pairs = [json.loads(l) for l in open(os.path.join(PRIV, 'pairs.jsonl'))]
    live = {l.split()[0]: l.split()[1:3] for l in open(os.path.join(PRIV, 'live-rule.txt'))}
    new = {l.split()[0]: l.split()[1:3] for l in open(os.path.join(PRIV, 'new-rule.txt'))}

    by_field = {}
    for a in sorted(rows.values(), key=lambda a: a['start']):
        by_field.setdefault(a['field'], []).append(a)

    def after_words(a, b):
        """Up to 2 swiped words the owner kept after the pair, each within 5 s of the last. Typed words aren't in the table."""
        out, last = [], b['end']
        for c in by_field[b['field']]:
            if c['start'] <= b['start'] or c['superseded_by'] is not None or c['slot'] in (a['slot'], b['slot']):
                continue
            if c['start'] - last > AFTER_MILLIS:
                break
            if c['hand']:
                out.append(word(c['hand']))
                last = c['end']
            if len(out) == AFTER_WORDS:
                break
        return [w for w in out if w]

    def chain_start(r):
        while r['joined_from'] is not None and r['joined_from'] in rows:
            r = rows[r['joined_from']]
        return r

    top = [p for p in pairs if p['a_end_y'] < TOP_ROW and p['b_start_y'] is not None and p['b_start_y'] < TOP_ROW
           and 0 <= p['gap'] <= MAX_GAP]
    print(f'top-row pairs within {MAX_GAP} ms: {len(top)}')
    print('  by label:', dict(Counter(p['label'] for p in top)))
    print('  inside the window couldContinue allowed before #83:', dict(Counter(p['label'] for p in top if live[p['pair']][0] == 'true')))

    out, agree, timings = [], Counter(), []
    for p in top:
        a_id, b_id = map(int, p['pair'].split('-'))
        a, b = rows[a_id], rows[b_id]
        first_logged = word(a['committed'])
        pc_first = read(a['chain_points'])
        pc_second = read(b['points'])
        pc_joined = read(a['chain_points'] + b['points'])
        if p['joined']:
            joined, second = word(b['committed']), pc_second
            agree['logged joined word = PC joined reading'] += joined == pc_joined
            agree['joined pairs'] += 1
        else:
            joined, second = pc_joined, word(b['committed'])
            agree['logged second word = PC reading'] += second == pc_second
            agree['pairs kept apart'] += 1
        agree['logged first word = PC reading'] += first_logged == pc_first
        agree['pairs'] += 1
        before = chain_start(a)['context']
        after = after_words(a, b)
        rec = {'pair': p['pair'], 'label': p['label'], 'version': p['version'], 'joined': p['joined'], 'gap': p['gap'],
               'window': live[p['pair']][0] == 'true', 'same_before': live[p['pair']][1] == 'true',
               'same_now': new[p['pair']][1] == 'true', 'after_words': len(after),
               'b_start_speed': p['b_start_speed'], 'carries_on': p['carries_on'], 'reverses': p['reverses'],
               'jump_keys': p['jump_keys'], 'a_end_size': p['a_end_size']}
        if None in (joined, first_logged, second):
            rec['skipped'] = True
            out.append(rec)
            continue
        W.CACHE.clear()
        started = time.perf_counter()
        rec['margin'] = margin(before, joined, first_logged, second, [])
        timings.append(time.perf_counter() - started)
        rec['margin_after'] = margin(before, joined, first_logged, second, after) if after else rec['margin']
        if None not in (pc_joined, pc_first, pc_second):
            rec['margin_pc'] = margin(before, pc_joined, pc_first, pc_second, [])
        out.append(rec)

    with open(os.path.join(PRIV, 'context-pairs.jsonl'), 'w') as f:
        for rec in out:
            f.write(json.dumps(rec) + '\n')
    print('\nThe PC recogniser against the log (how far the PC readings can stand in):')
    for k, v in agree.items():
        print(f'  {k}: {v}')
    print(f'  skipped (a reading missing): {sum(1 for r in out if r.get("skipped"))}')
    print(f'  with words after: {sum(1 for r in out if r["after_words"])}')
    t = sorted(timings)
    print(f'\nTime per pair, both readings, text before only, no cache, one CPU thread on this PC: median {1000 * t[len(t) // 2]:.0f} ms, '
          f'90th percentile {1000 * t[int(len(t) * .9)]:.0f} ms')
    report(out)


def stats(v):
    v = sorted(v)
    if not v:
        return 'none'
    q = lambda f: v[min(len(v) - 1, int(f * len(v)))]
    return f'n {len(v):3}  median {q(.5):6.2f}  10th {q(.1):6.2f}  90th {q(.9):6.2f}  min {v[0]:6.2f}  max {v[-1]:6.2f}'


def auc(pos, neg):
    if not pos or not neg:
        return float('nan')
    wins = sum((p > n) + .5 * (p == n) for p in pos for n in neg)
    return wins / (len(pos) * len(neg))


def logistic_loo(rows, features):
    """Leave-one-out logistic regression; returns each row's held-out chance of one word."""
    X = np.array([[r[f] for f in features] for r in rows], dtype=float)
    X = (X - X.mean(0)) / (X.std(0) + 1e-9)
    X = np.hstack([X, np.ones((len(X), 1))])
    y = np.array([r['label'] in ONE for r in rows], dtype=float)
    out = []
    for i in range(len(rows)):
        keep = np.arange(len(rows)) != i
        w = np.zeros(X.shape[1])
        for _ in range(300):
            p = 1 / (1 + np.exp(-X[keep] @ w))
            w -= .5 * (X[keep].T @ (p - y[keep]) / keep.sum() + .01 * w)
        out.append(float(1 / (1 + np.exp(-X[i] @ w))))
    return out


def report(out):
    scored = [r for r in out if 'margin' in r]
    for key, name in (('margin', 'text before only (what the keyboard has)'), ('margin_after', 'with up to 2 words after'),
                      ('margin_pc', 'text before only, all three readings from the PC recogniser')):
        print(f'\n== Margin, {name}: joined word over the two words, nats')
        for g in GROUPS:
            print(f'  {g:<15} {stats([r[key] for r in scored if r["label"] == g and key in r])}')
        one = [r[key] for r in scored if r['label'] in ONE and key in r]
        print(f'  separation (chance a one-word pair scores above a two-word pair):'
              f' right join vs right non-join {auc([r[key] for r in scored if r["label"] == "right join" and key in r], [r[key] for r in scored if r["label"] == "right non-join" and key in r]):.2f},'
              f' right join vs wrong join {auc([r[key] for r in scored if r["label"] == "right join" and key in r], [r[key] for r in scored if r["label"] == "wrong join" and key in r]):.2f},'
              f' one word vs two {auc(one, [r[key] for r in scored if r["label"] in TWO and key in r]):.2f}')

    print('\n== Joining by margin alone (join when margin > t), text before only')
    print('  t       right joins kept  right non-joins joined  wrong joins joined  missed found  unclear joined  other joined')
    rj = [r for r in scored if r['label'] == 'right join']
    for t in (-12, -10, -8, -6, -4, -2, 0, 2, 4, 6):
        c = Counter(r['label'] for r in scored if r['margin'] > t)
        print(f'  {t:>4}    {c["right join"]:>4} / {len(rj):<4}        {c["right non-join"]:>4}              {c["wrong join"]:>4}'
              f'            {c["missed join"]:>3}          {c["unclear"]:>4}           {c["other"]:>4}')
    low_rj = min(r['margin'] for r in rj)
    high_rn = max(r['margin'] for r in scored if r['label'] == 'right non-join')
    print(f'  lowest right join {low_rj:.2f}, highest right non-join {high_rn:.2f}:',
          'a threshold that loses no right join and joins no kept-apart pair exists' if high_rn < low_rj else 'no threshold loses no right join and joins no kept-apart pair')

    print('\n== With the geometry the keyboard uses now (Swipe.sameStroke on the pair)')
    geo = [r for r in scored if r['same_now']]
    print(f'  geometry joins {len(geo)}: by label {dict(Counter(r["label"] for r in geo))}')
    print('  a veto: of those, keep apart when margin <= t')
    rj_geo = sum(1 for r in geo if r['label'] == 'right join')
    for t in sorted({-20, -15, -12, -10, -8, -6, -4} | {math.floor(low_rj * 100) / 100 - .01}):
        cut = Counter(r['label'] for r in geo if r['margin'] <= t)
        print(f'    t {t:>7.2f}: right joins lost {cut["right join"]:>3} of {rj_geo}, wrong joins cut {cut["wrong join"]:>3},'
              f' unclear cut {cut["unclear"]:>3}, other cut {cut["other"]:>3}')
    lowest_geo_rj = min(r['margin'] for r in geo if r['label'] == 'right join')
    cut = Counter(r['label'] for r in geo if r['margin'] < lowest_geo_rj)
    print(f'  the strictest veto that loses no right join (margin below {lowest_geo_rj:.2f}): cuts wrong joins {cut["wrong join"]},'
          f' unclear {cut["unclear"]}, other {cut["other"]}, right non-joins {cut["right non-join"]}')
    apart = [r for r in scored if not r['same_now']]
    print(f'  geometry keeps apart {len(apart)}: by label {dict(Counter(r["label"] for r in apart))}')
    highest_apart_two = max((r['margin'] for r in apart if r['label'] in TWO), default=float('nan'))
    found = Counter(r['label'] for r in apart if r['margin'] > highest_apart_two)
    print(f'  an add: join when margin > {highest_apart_two:.2f} (above every two-word pair kept apart): finds {dict(found)}')

    print('\n== Does the margin add anything to the geometry? Leave-one-out logistic regression, one word against two')
    pool = [r for r in scored if r['label'] in ONE | TWO]
    print(f'  pairs: {len(pool)} ({sum(r["label"] in ONE for r in pool)} one word, {sum(r["label"] in TWO for r in pool)} two words)')
    for r in pool:
        r['speed'], r['gap_f'] = r['b_start_speed'] or 0.0, float(r['gap'])
    geometry = ['gap_f', 'speed', 'carries_on', 'reverses', 'jump_keys']
    for name, feats in (('margin alone', ['margin']), ('geometry alone', geometry), ('geometry and margin', geometry + ['margin'])):
        chances = logistic_loo(pool, feats)
        print(f'  {name:<22} separation {auc([c for c, r in zip(chances, pool) if r["label"] in ONE], [c for c, r in zip(chances, pool) if r["label"] in TWO]):.3f}')
    for r in pool:
        r['geo_now'] = 1.0 if r['same_now'] else 0.0
    print(f'  the keyboard\'s sameStroke alone   separation {auc([r["geo_now"] for r in pool if r["label"] in ONE], [r["geo_now"] for r in pool if r["label"] in TWO]):.3f}')


if __name__ == '__main__':
    main()
