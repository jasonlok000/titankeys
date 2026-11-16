#!/usr/bin/env python3
"""Issue #83: the owner's touchpad session at the level of single samples.

For every labelled gap from capture.py (a real break inside one word, or a deliberate gap between two words), it takes
the last 100 ms of the first touch and the first 100 ms of the second, sample by sample, and measures how the finger
left and how it came back. Real breaks are sorted by what happened: the finger slid off the top edge, the sensor lost
it mid-pad, or it bounced (a touch of a few samples, or a return within 50 ms to the same spot).

Each signal is judged at the owner's bar: a threshold that loses no real break. Here, that's beyond the most extreme
break. It's checked against the rule's joins in this session, and the same signals are measured on the #83 logs'
top-row pairs that Swipe.sameStroke joins (right joins against wrong joins), when the private folder is given.

Prints numbers only. Writes the sample windows, numbers only, to <out>/micro-windows.json for plot_micro.py.
Usage: python3 micro.py <touchpad.txt> <repo root> <out dir> [#83 private dir]
"""
import importlib.util
import json
import math
import os
import sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('capture', os.path.join(HERE, 'capture.py'))
C = importlib.util.module_from_spec(spec)
spec.loader.exec_module(C)

WINDOW = 100          # ms each side of the gap
EDGE = 0.154          # the sensor's top edge in letter space, after the built-in calibration
NEAR_EDGE = 0.02      # a first touch ending this close to the edge slid off it
STILL_KEYS = 0.25     # within this of a point, the finger is holding still


def keys(a, b):
    return math.hypot((b[0] - a[0]) * 10, (b[1] - a[1]) * 3)


def velocity(piece, ms, opening):
    """Keys per ms over the first or last [ms] of a piece (at least two samples)."""
    if len(piece) < 2:
        return 0.0, 0.0
    if opening:
        end = next((i for i, p in enumerate(piece) if p[2] - piece[0][2] >= ms), len(piece) - 1)
        a, b = piece[0], piece[max(end, 1)]
    else:
        start = next((i for i in range(len(piece) - 1, -1, -1) if piece[-1][2] - piece[i][2] >= ms), 0)
        a, b = piece[min(start, len(piece) - 2)], piece[-1]
    dt = max(b[2] - a[2], 1)
    return (b[0] - a[0]) * 10 / dt, (b[1] - a[1]) * 3 / dt


def mean(v):
    return sum(v) / len(v) if v else float('nan')


def sizes(piece, t0, t1):
    return [p[3] for p in piece if t0 <= p[2] <= t1 and p[3] is not None]


def dwell_end(piece):
    """How long the finger held still at the end: from the first sample within STILL_KEYS of the last, to the last."""
    t = piece[-1][2]
    for p in reversed(piece):
        if keys(p, piece[-1]) > STILL_KEYS:
            break
        t = p[2]
    return piece[-1][2] - t


def dwell_start(piece):
    """How long the finger held still after landing, before moving STILL_KEYS away."""
    for p in piece:
        if keys(piece[0], p) > STILL_KEYS:
            return p[2] - piece[0][2]
    return piece[-1][2] - piece[0][2]


def features(a, b, gap, lift_lag=None):
    ta, tb = a[-1][2], b[0][2]
    va20, va100 = velocity(a, 20, False), velocity(a, 100, False)
    vb20 = velocity(b, 20, True)
    sa, sb = math.hypot(*va20), math.hypot(*vb20)
    # Where the finger would be after the gap, carrying on as it left.
    guess = (a[-1][0] + va20[0] * gap / 10, a[-1][1] + va20[1] * gap / 3)
    f = {
        'gap': gap,
        'a_dt_last': ta - a[-2][2] if len(a) > 1 else float('nan'),
        'a_samples_100': sum(1 for p in a if p[2] >= ta - WINDOW),
        'a_dwell': dwell_end(a),
        'a_speed_20': sa,
        'a_speed_100': math.hypot(*va100),
        'a_slowing': sa / max(math.hypot(*va100), 1e-4),
        'a_vy_20': va20[1],
        # As Swipe.motion measures it: over 40 ms, reaching one point past.
        'a_vy_40': C.motion(a, False)[1],
        'a_edge_keys': (a[-1][1] - EDGE) * 3,
        'a_size_last': a[-1][3],
        'a_size_40': mean(sizes(a, ta - 40, ta)),
        'a_size_thinning': mean(sizes(a, ta - 200, ta - 60)) - mean(sizes(a, ta - 40, ta)),
        'b_dt_first': b[1][2] - tb if len(b) > 1 else float('nan'),
        'b_size_first': b[0][3],
        'b_size_40': mean(sizes(b, tb, tb + 40)),
        'b_size_growing': mean(sizes(b, tb + 60, tb + 200)) - mean(sizes(b, tb, tb + 40)),
        'b_speed_20': sb,
        'b_dwell': dwell_start(b),
        'b_edge_keys': (b[0][1] - EDGE) * 3,
        'jump_keys': keys(a[-1], b[0]),
        'miss_keys': keys(guess, b[0]),
        'turn': C.cosine(va20, vb20),
    }
    if lift_lag is not None:
        f['a_lift_lag'] = lift_lag
    return {k: (None if isinstance(v, float) and math.isnan(v) else v) for k, v in f.items()}


def kind_of_break(a, b, gap):
    if len(a) <= 3 or len(b) <= 3 or (gap <= 50 and keys(a[-1], b[0]) <= 0.6):
        return 'bounce'
    if a[-1][1] - EDGE <= NEAR_EDGE:
        return 'edge'
    return 'dropout'


def auc(pos, neg):
    if not pos or not neg:
        return float('nan')
    return sum((x > y) + .5 * (x == y) for x in pos for y in neg) / (len(pos) * len(neg))


def at_bar(breaks, others):
    """Thresholds beyond the most extreme break, both ways. Returns (direction, edge, others beyond, room) for the better
    side: room is how far past the edge the next 'other' sits, as a share of the breaks' 10th-90th range."""
    best = None
    vb = sorted(breaks)
    spread = (vb[int(.9 * (len(vb) - 1))] - vb[int(.1 * (len(vb) - 1))]) or 1e-9
    for side, edge, beyond in (('above', vb[-1], [v for v in others if v > vb[-1]]),
                               ('below', vb[0], [v for v in others if v < vb[0]])):
        room = (min(beyond) - edge if side == 'above' else edge - max(beyond)) / spread if beyond else 0.0
        if best is None or len(beyond) > best[2]:
            best = (side, edge, len(beyond), room)
    return best


SIGNALS = ['gap', 'a_dt_last', 'a_lift_lag', 'a_samples_100', 'a_dwell', 'a_speed_20', 'a_speed_100', 'a_slowing',
           'a_vy_20', 'a_vy_40', 'a_edge_keys', 'a_size_last', 'a_size_40', 'a_size_thinning', 'b_dt_first', 'b_size_first',
           'b_size_40', 'b_size_growing', 'b_speed_20', 'b_dwell', 'b_edge_keys', 'jump_keys', 'miss_keys', 'turn']


def table(title, pos_name, neg_name, pos, neg, joined_neg=None):
    print(f'\n== {title}: {len(pos)} {pos_name}, {len(neg)} {neg_name}')
    print(f'  {"signal":<16} {pos_name + " median":>16} {neg_name + " median":>18} {"separation":>10}   at the bar (no {pos_name} lost)')
    out = {}
    for s in SIGNALS:
        vp = [r[s] for r in pos if r.get(s) is not None]
        vn = [r[s] for r in neg if r.get(s) is not None]
        if not vp or not vn:
            continue
        a = auc(vp, vn)
        side, edge, n, room = at_bar(vp, vn)
        extra = ''
        if joined_neg is not None:
            vj = [r[s] for r in joined_neg if r.get(s) is not None]
            cut = sum(v > edge for v in vj) if side == 'above' else sum(v < edge for v in vj)
            extra = f', joins cut {cut} of {len(vj)}'
        print(f'  {s:<16} {sorted(vp)[len(vp) // 2]:>16.4f} {sorted(vn)[len(vn) // 2]:>18.4f} {max(a, 1 - a):>10.2f}'
              f'   {side} {edge:.4f}: {neg_name} kept apart {n} of {len(vn)}{extra}, room {room:.2f}')
        out[s] = (side, edge)
    return out


def logs_features(private):
    """The same signals on the #83 logs' top-row pairs that Swipe.sameStroke joins (no lift times in the logs)."""
    rows = {}
    for line in open(os.path.join(private, 'attempts.jsonl'), encoding='utf-8'):
        r = json.loads(line)
        rows[r['id']] = r
    new = {l.split()[0]: l.split()[2] == 'true' for l in open(os.path.join(private, 'new-rule.txt'))}
    out = []
    for p in map(json.loads, open(os.path.join(private, 'pairs.jsonl'))):
        if not new[p['pair']] or p['a_end_y'] >= 1 / 3 or p['b_start_y'] is None or p['b_start_y'] >= 1 / 3:
            continue
        a_id, b_id = map(int, p['pair'].split('-'))
        a, b = rows[a_id], rows[b_id]

        def with_sizes(points, size_points):
            pts = [(q[0], q[1] * 4 / 3, q[2]) for q in points if q[0] is not None and q[1] is not None]
            sp = sorted(size_points or [])
            out, j = [], 0
            for x, y, t in pts:
                while j + 1 < len(sp) and abs(sp[j + 1][0] - t) <= abs(sp[j][0] - t):
                    j += 1
                out.append((x, y, t, sp[j][1] if sp and abs(sp[j][0] - t) <= 20 else None))
            return out
        pa = with_sizes(a['chain_points'], a.get('size_points'))
        pb = with_sizes(b['points'], b.get('size_points'))
        if len(pa) < 2 or len(pb) < 2:
            continue
        has_size = all(q[3] is not None for q in pa[-3:] + pb[:3])
        if not has_size:
            pa = [(x, y, t, 0) for x, y, t, _ in pa]
            pb = [(x, y, t, 0) for x, y, t, _ in pb]
        f = features(pa, pb, pb[0][2] - pa[-1][2])
        if not has_size:
            for k in list(f):
                if 'size' in k:
                    f[k] = None
        f['label'], f['version'] = p['label'], p['version']
        out.append(f)
    return out


COMBINED = ['gap', 'a_vy_40', 'a_speed_20', 'a_slowing', 'a_dwell', 'a_edge_keys', 'b_dt_first', 'b_speed_20', 'b_dwell',
            'jump_keys', 'miss_keys', 'turn']


def combined(recs, logs):
    """All the position and timing signals weighed together (logistic regression), leave-one-out on this session, the
    threshold set without the held-out gap just under its lowest joined break. Sizes are left out: the logs mostly lack
    them. Then the weights and threshold from the whole session, carried to the logs' top-row joins."""
    import numpy as np
    pool = [r for r in recs if all(r.get(k) is not None for k in COMBINED)]
    X = np.array([[r[k] for k in COMBINED] for r in pool], dtype=float)
    y = np.array([r['kind'] == 'break' for r in pool], dtype=float)
    joined_break = np.array([r['kind'] == 'break' and r['joins'] for r in pool])

    def fit(X, y):
        mu, sd = X.mean(0), X.std(0) + 1e-9
        Z = np.hstack([(X - mu) / sd, np.ones((len(X), 1))])
        w = np.zeros(Z.shape[1])
        for _ in range(500):
            p = 1 / (1 + np.exp(-Z @ w))
            w -= .5 * (Z.T @ (p - y) / len(y) + .01 * np.r_[w[:-1], 0])
        return lambda A: np.hstack([(A - mu) / sd, np.ones((len(A), 1))]) @ w

    kept_apart = []
    for i in range(len(pool)):
        keep = np.arange(len(pool)) != i
        score = fit(X[keep], y[keep])
        v = score(X)
        kept_apart.append(bool(v[i] < v[keep & joined_break].min() - 1e-9))
    joins = [r['joins'] for r in pool]
    print(f'\n== All position and timing signals weighed together, leave-one-out ({len(pool)} gaps)')
    lost = sum(k for k, r, j in zip(kept_apart, pool, joins) if j and r['kind'] == 'break')
    cut = sum(k for k, r, j in zip(kept_apart, pool, joins) if j and r['kind'] != 'break')
    n_break = sum(1 for r in pool if r['joins'] and r['kind'] == 'break')
    n_false = sum(1 for r in pool if r['joins'] and r['kind'] != 'break')
    print(f"  as a veto on the rule's joins: joined breaks lost {lost} of {n_break}, false joins cut {cut} of {n_false}")
    if logs:
        score = fit(X, y)
        edge = score(X)[joined_break].min()
        L = [r for r in logs if all(r.get(k) is not None for k in COMBINED)]
        v = score(np.array([[r[k] for k in COMBINED] for r in L], dtype=float))
        cut = Counter(r['label'] for r, x in zip(L, v) if x < edge)
        n = Counter(r['label'] for r in L)
        print(f'  carried to the logs: right joins lost {cut["right join"]} of {n["right join"]}, wrong joins cut {cut["wrong join"]} of {n["wrong join"]}')


def main():
    path, repo, out = sys.argv[1:4]
    private = sys.argv[4] if len(sys.argv) > 4 else None
    T, lifts, gaps = C.label(path, repo, out)
    recs, windows = [], []
    for g in gaps:
        if g['kind'] == 'next item':
            continue
        i = g['i']
        a, b = T[i], T[i + 1]
        f = features(a, b, g['gap'], lifts[i] - a[-1][2])
        f.update({'kind': g['kind'], 'joins': g['joins'], 'top_row': g['top_row']})
        f['sort'] = kind_of_break(a, b, g['gap']) if g['kind'] == 'break' else 'two words'
        recs.append(f)
        # Numbers only: times from the lift, letter-space position, contact size.
        ta, tb = a[-1][2], b[0][2]
        windows.append({'sort': f['sort'], 'joins': g['joins'], 'gap': g['gap'], 'lift_lag': f['a_lift_lag'],
                        'f': {k: f.get(k) for k in SIGNALS},
                        'a': [[round(p[2] - ta, 1), round(p[0], 4), round(p[1], 4), p[3]] for p in a if p[2] >= ta - WINDOW],
                        'b': [[round(p[2] - ta, 1), round(p[0], 4), round(p[1], 4), p[3]] for p in b if p[2] <= tb + WINDOW]})
    with open(os.path.join(out, 'micro-windows.json'), 'w') as fh:
        json.dump(windows, fh)

    br = [r for r in recs if r['kind'] == 'break']
    tw = [r for r in recs if r['kind'] == 'two words']
    print(f'gaps: {len(br)} real breaks, {len(tw)} two-word gaps')
    print('  breaks by what happened:', dict(Counter(r['sort'] for r in br)))
    print('  joined by the rule, by what happened:', {k: f'{sum(r["joins"] for r in br if r["sort"] == k)}/{v}'
                                                       for k, v in Counter(r['sort'] for r in br).items()})
    print(f'  samples: median time between samples {sorted(r["a_dt_last"] for r in recs if r["a_dt_last"])[len(recs) // 2]:.1f} ms')

    for k in ('edge', 'dropout', 'bounce'):
        sub = [r for r in br if r['sort'] == k]
        if sub:
            print(f'\n-- {k} breaks ({len(sub)}): median ' + ', '.join(
                f'{s} {sorted(r[s] for r in sub if r.get(s) is not None)[len(sub) // 2]:.4f}'
                for s in ('gap', 'a_dt_last', 'a_lift_lag', 'a_dwell', 'a_speed_20', 'a_vy_20', 'a_edge_keys',
                          'a_size_thinning', 'b_dt_first', 'b_speed_20', 'b_dwell', 'miss_keys')))
    print(f'-- two-word gaps ({len(tw)}): median ' + ', '.join(
        f'{s} {sorted(r[s] for r in tw if r.get(s) is not None)[len(tw) // 2]:.4f}'
        for s in ('gap', 'a_dt_last', 'a_lift_lag', 'a_dwell', 'a_speed_20', 'a_vy_20', 'a_edge_keys',
                  'a_size_thinning', 'b_dt_first', 'b_speed_20', 'b_dwell', 'miss_keys')))

    fj = [r for r in tw if r['joins']]
    table('This session, all gaps', 'breaks', 'two words', br, tw, joined_neg=fj)
    # The owner's bar for a veto: lose none of the breaks the rule joins.
    here = table("This session, as a veto on the rule's joins", 'joined breaks', 'false joins',
                 [r for r in br if r['joins']], fj)
    if private:
        L = logs_features(private)
        rj = [r for r in L if r['label'] == 'right join']
        wj = [r for r in L if r['label'] == 'wrong join']
        print(f'\nlogs: top-row joins {len(L)}: right {len(rj)}, wrong {len(wj)}; with sizes: '
              f'{sum(r["a_size_last"] is not None for r in rj)} right, {sum(r["a_size_last"] is not None for r in wj)} wrong')
        table("The logs, the rule's top-row joins", 'right joins', 'wrong joins', rj, wj)
        combined(recs, L)
        print('\n== This session\'s bar carried to the logs (threshold from this session\'s breaks)')
        for s, (side, edge) in here.items():
            vr = [r[s] for r in rj if r.get(s) is not None]
            vw = [r[s] for r in wj if r.get(s) is not None]
            if not vr:
                continue
            beyond = (lambda v: v > edge) if side == 'above' else (lambda v: v < edge)
            print(f'  {s:<16} {side} {edge:.4f}: right joins lost {sum(map(beyond, vr))} of {len(vr)}, wrong joins cut {sum(map(beyond, vw))} of {len(vw)}')


if __name__ == '__main__':
    main()
