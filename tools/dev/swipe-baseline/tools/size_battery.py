#!/usr/bin/env python3
"""Issue #30, broad test: does touch size (how hard and flat the finger presses) say anything about the swipe?
Works on kept swipes from a labelled log with size_points (sensor steps). Sizes are corrected two ways:
  pos   = step minus the usual step at that spot on the pad (the sensor reads smaller toward the top and right)
  rel   = pos minus the stroke's own mean, so only changes within the stroke remain
Parts:
  A. Within a stroke: rel against turning angle, speed, slowing down, nearness to an intended letter, entering and
     leaving a letter, and the stroke's start and end.
  B. Across strokes: the stroke's pressure level (mean pos) against whether engine 2.0 got it right live, how far the
     stroke lands from the keys, and stroke length and speed.
Counts only. Usage: size_battery.py attempts.jsonl corpus.jsonl out.json"""
import bisect
import json
import math
import sys
from collections import defaultdict

ROWS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm']
KEY = {ch: ((c + (1 if r == 2 else 0) + .5) / 10, (r + .5) / 3) for r, row in enumerate(ROWS) for c, ch in enumerate(row)}


def key(w):
    return w.lower().replace('’', "'").strip("'").replace("'", '')


def mean(v):
    return sum(v) / len(v) if v else float('nan')


def corr(a, b):
    n = len(a)
    if n < 3:
        return float('nan')
    ma, mb = mean(a), mean(b)
    sa = math.sqrt(sum((x - ma) ** 2 for x in a))
    sb = math.sqrt(sum((y - mb) ** 2 for y in b))
    return sum((x - ma) * (y - mb) for x, y in zip(a, b)) / (sa * sb) if sa and sb else float('nan')


def cell(x, y):
    return min(2, max(0, int(y * 3))), min(9, max(0, int(x * 10)))


def load(attempts_path, corpus_path):
    """Kept single-contact swipes with a size at every point: (row, points[(x, y_rows, t, step)], word, live words)."""
    live = {}
    for line in open(corpus_path, encoding='utf-8'):
        e = json.loads(line)
        if e['id'].split('-')[0] in ('log', 'hand') and e['first']:
            live[int(e['id'].split('-')[1])] = (key(e['expected']), [key(w) for w in e['live']])
    out = []
    for line in open(attempts_path, encoding='utf-8'):
        r = json.loads(line)
        sp = r.get('size_points') or []
        if not sp or r['id'] not in live or len(r.get('chain') or [r['id']]) > 1:
            continue
        times = [t for t, _ in sp]
        pts = []
        for x, y, t in [p for p in r['points'] if p[0] is not None]:
            i = bisect.bisect_left(times, t)
            j = min((k for k in (i - 1, i) if 0 <= k < len(sp)), key=lambda k: abs(times[k] - t))
            if abs(times[j] - t) <= 30:
                pts.append((x, y * 4 / 3, t, sp[j][1]))
        word, shown = live[r['id']]
        if len(pts) >= 8 and len(word) >= 2 and all(c in KEY for c in word):
            out.append((r, pts, word, shown))
    return out


def corrected(strokes):
    cells = defaultdict(list)
    for _, pts, _, _ in strokes:
        for x, y, t, s in pts:
            cells[cell(x, y)].append(s)
    usual = {k: mean(v) for k, v in cells.items()}
    res = []
    for r, pts, word, shown in strokes:
        pos = [s - usual[cell(x, y)] for x, y, t, s in pts]
        level = mean(pos)
        rel = [p - level for p in pos]
        res.append((r, pts, word, shown, pos, rel, level))
    return res


def letter_points(pts, word):
    idx, start = [], 0
    for ch in word:
        kx, ky = KEY[ch]
        j = min(range(start, len(pts)), key=lambda i: math.hypot((pts[i][0] - kx) * 10, (pts[i][1] - ky) * 3))
        idx.append(j)
        start = j
    return idx


def part_a(data):
    turn, speedv, slow, near, side, ends = defaultdict(list), defaultdict(list), [], defaultdict(list), defaultdict(list), defaultdict(list)
    for r, pts, word, shown, pos, rel, level in data:
        n = len(pts)
        sp = [0.0] + [math.hypot((pts[i][0] - pts[i - 1][0]) * 10, (pts[i][1] - pts[i - 1][1]) * 3) / max(1, pts[i][2] - pts[i - 1][2]) * 1000 for i in range(1, n)]
        idx = letter_points(pts, word)
        for i in range(n):
            frac = (pts[i][2] - pts[0][2]) / max(1, pts[-1][2] - pts[0][2])
            ends[min(int(frac * 10), 9)].append(pos[i])
            if 3 <= i < n - 3:
                ax, ay = (pts[i][0] - pts[i - 3][0]) * 10, (pts[i][1] - pts[i - 3][1]) * 3
                bx, by = (pts[i + 3][0] - pts[i][0]) * 10, (pts[i + 3][1] - pts[i][1]) * 3
                na, nb = math.hypot(ax, ay), math.hypot(bx, by)
                if na > .3 and nb > .3:
                    ang = math.degrees(math.acos(max(-1, min(1, (ax * bx + ay * by) / (na * nb)))))
                    turn[min(int(ang // 20), 6)].append(rel[i])
                speedv[min(int(sp[i] // 5), 8)].append(rel[i])
                if sp[i - 2] > 0 and sp[i + 2] > 0:
                    slow.append((sp[i] / max(sp[i - 2], 1e-3), rel[i]))
            d = min(math.hypot((pts[i][0] - KEY[c][0]) * 10, (pts[i][1] - KEY[c][1]) * 3) for c in word)
            near[min(int(d / .25), 8)].append(rel[i])
        inner = idx[1:-1]
        for j in inner:
            for off in range(-4, 5):
                if 0 <= j + off < n:
                    side[off].append(rel[j + off])
    out = {}
    out['by turning angle (degrees): mean rel'] = {f'{20 * k}-{20 * k + 20}': [round(mean(v), 3), len(v)] for k, v in sorted(turn.items())}
    out['by speed (keys/s): mean rel'] = {f'{5 * k}-{5 * k + 5}': [round(mean(v), 3), len(v)] for k, v in sorted(speedv.items())}
    out['corr(speed ratio now/before, rel)'] = round(corr([a for a, _ in slow], [b for _, b in slow]), 3)
    out['by distance to the nearest intended letter (keys): mean rel'] = {f'{.25 * k:.2f}': [round(mean(v), 3), len(v)] for k, v in sorted(near.items())}
    out['around inner intended letters (points before/after): mean rel'] = {str(k): [round(mean(v), 3), len(v)] for k, v in sorted(side.items())}
    out['by time through the stroke (tenths): mean pos'] = {str(k): [round(mean(v), 3), len(v)] for k, v in sorted(ends.items())}
    return out


def part_b(data):
    rows = []
    for r, pts, word, shown, pos, rel, level in data:
        idx = letter_points(pts, word)
        miss = mean([math.hypot((pts[j][0] - KEY[c][0]) * 10, (pts[j][1] - KEY[c][1]) * 3) for j, c in zip(idx, word)])
        dx = mean([(pts[j][0] - KEY[c][0]) * 10 for j, c in zip(idx, word)])
        dy = mean([(pts[j][1] - KEY[c][1]) * 3 for j, c in zip(idx, word)])
        length = sum(math.hypot((pts[i][0] - pts[i - 1][0]) * 10, (pts[i][1] - pts[i - 1][1]) * 3) for i in range(1, len(pts)))
        dur = pts[-1][2] - pts[0][2]
        rows.append({'level': level, 'raw': mean([p[3] for p in pts]), 'right1': shown[:1] == [word], 'right3': word in shown[:3],
                     'miss': miss, 'dx': dx, 'dy': dy, 'len': length, 'speed': length / max(dur, 1) * 1000, 'n': len(word),
                     'spread': math.sqrt(mean([v * v for v in rel]))})
    rows.sort(key=lambda d: d['level'])
    q = len(rows) // 4
    out = {'strokes': len(rows), 'by pressure quarter (lightest first)': {}}
    for k in range(4):
        part = rows[k * q:(k + 1) * q] if k < 3 else rows[3 * q:]
        out['by pressure quarter (lightest first)'][f'Q{k + 1}'] = {
            'n': len(part), 'level': round(mean([d['level'] for d in part]), 2), 'raw steps': round(mean([d['raw'] for d in part]), 2),
            'engine 2.0 live first %': round(100 * mean([d['right1'] for d in part]), 1),
            'top 3 %': round(100 * mean([d['right3'] for d in part]), 1),
            'keys from intended letters': round(mean([d['miss'] for d in part]), 3),
            'x offset (keys, + right)': round(mean([d['dx'] for d in part]), 3), 'y offset (rows, + down)': round(mean([d['dy'] for d in part]), 3),
            'speed keys/s': round(mean([d['speed'] for d in part]), 1), 'word length': round(mean([d['n'] for d in part]), 2)}
    out['corr(level, keys from letters)'] = round(corr([d['level'] for d in rows], [d['miss'] for d in rows]), 3)
    out['corr(level, speed)'] = round(corr([d['level'] for d in rows], [d['speed'] for d in rows]), 3)
    out['corr(level, right first)'] = round(corr([d['level'] for d in rows], [float(d['right1']) for d in rows]), 3)
    out['corr(spread within stroke, right first)'] = round(corr([d['spread'] for d in rows], [float(d['right1']) for d in rows]), 3)
    return out


def spearman(a, b):
    """Rank correlation, and a z score for it (|z| > 2 is unlikely by chance)."""
    def ranks(v):
        order = sorted(range(len(v)), key=lambda i: v[i])
        r = [0.0] * len(v)
        i = 0
        while i < len(order):
            j = i
            while j + 1 < len(order) and v[order[j + 1]] == v[order[i]]:
                j += 1
            for k in range(i, j + 1):
                r[order[k]] = (i + j) / 2
            i = j + 1
        return r
    pairs = [(x, y) for x, y in zip(a, b) if x == x and y == y]
    if len(pairs) < 10:
        return float('nan'), float('nan')
    rho = corr(ranks([p[0] for p in pairs]), ranks([p[1] for p in pairs]))
    return rho, rho * math.sqrt(len(pairs) - 1)


def stroke_measures(r, pts, pos, rel, level, word=None, shown=None):
    n = len(pts)
    length = sum(math.hypot((pts[i][0] - pts[i - 1][0]) * 10, (pts[i][1] - pts[i - 1][1]) * 3) for i in range(1, n))
    dur = max(1, pts[-1][2] - pts[0][2])
    t_end = pts[-1][2]
    last = [pos[i] for i in range(n) if t_end - pts[i][2] <= 100]
    before = [pos[i] for i in range(n) if 100 < t_end - pts[i][2] <= 300]
    first = [pos[i] for i in range(n) if pts[i][2] - pts[0][2] <= 100]
    m = {'pressure level': level, 'raw steps': mean([p[3] for p in pts]), 'pressure spread': math.sqrt(mean([v * v for v in rel])),
         'pressure at start (first 100 ms)': mean(first), 'pressure drop at the end (last 100 ms minus the 200 ms before)': mean(last) - mean(before) if last and before else float('nan'),
         'length (keys)': length, 'duration (ms)': dur, 'speed (keys/s)': length / dur * 1000,
         'start row (0 top)': pts[0][1] * 3, 'end row (0 top)': pts[-1][1] * 3, 'start column': pts[0][0] * 10, 'end column': pts[-1][0] * 10,
         'hour of day': (r['wall'] / 3600000 + 8) % 24}
    if word:
        idx = letter_points(pts, word)
        m['word length'] = len(word)
        m['keys from intended letters'] = mean([math.hypot((pts[j][0] - KEY[c][0]) * 10, (pts[j][1] - KEY[c][1]) * 3) for j, c in zip(idx, word)])
        m['engine 2.0 live right first'] = float(shown[:1] == [word])
        m['engine 2.0 live in top 3'] = float(word in shown[:3])
    return m


def part_e(data, broken_ids):
    """Every stroke measure against pressure, as rank correlations."""
    rows = []
    for r, pts, word, shown, pos, rel, level in data:
        m = stroke_measures(r, pts, pos, rel, level, word, shown)
        m['broken (a later piece rejoined it)'] = float(r['id'] in broken_ids)
        rows.append(m)
    names = list(rows[0].keys())
    out = {}
    for p in ('pressure level', 'raw steps', 'pressure spread', 'pressure drop at the end (last 100 ms minus the 200 ms before)'):
        out[p] = {}
        for nme in names:
            if nme == p:
                continue
            rho, z = spearman([d[p] for d in rows], [d[nme] for d in rows])
            out[p][nme] = {'rho': round(rho, 3), 'z': round(z, 1)}
    return out


def part_f(attempts_path):
    """Broken strokes: pieces that a later swipe rejoined, against swipes that ended on their own."""
    rows = [json.loads(l) for l in open(attempts_path, encoding='utf-8')]
    joined_into = {r['joined_from'] for r in rows if r.get('joined_from') is not None}
    strokes = []
    for r in rows:
        sp = r.get('size_points') or []
        if not sp:
            continue
        times = [t for t, _ in sp]
        pts = []
        for x, y, t in [p for p in r['points'] if p[0] is not None]:
            i = bisect.bisect_left(times, t)
            j = min((k for k in (i - 1, i) if 0 <= k < len(sp)), key=lambda k: abs(times[k] - t))
            if abs(times[j] - t) <= 30:
                pts.append((x, y * 4 / 3, t, sp[j][1]))
        if len(pts) >= 6:
            strokes.append((r, pts, None, [], ))
    # The usual size at each spot, from every stroke with sizes.
    cells = defaultdict(list)
    for r, pts, _, _ in strokes:
        for x, y, t, s in pts:
            cells[cell(x, y)].append(s)
    usual = {k: mean(v) for k, v in cells.items()}
    groups = defaultdict(list)
    for r, pts, _, _ in strokes:
        pos = [s - usual[cell(x, y)] for x, y, t, s in pts]
        level = mean(pos)
        rel = [p - level for p in pos]
        m = stroke_measures(r, pts, pos, rel, level)
        broken = r['id'] in joined_into
        top_end = m['end row (0 top)'] < 1.0
        groups[('broken' if broken else 'whole', 'ends on the top row' if top_end else 'ends lower')].append(m)
        # The last raw sizes before the end, in steps, as a timeline.
        t_end = pts[-1][2]
        for k in range(6):
            v = [p[3] - usual[cell(p[0], p[1])] for p in pts if k * 50 <= t_end - p[2] < (k + 1) * 50]
            if v:
                groups[('timeline', 'broken' if broken else 'whole', k)].append(mean(v))
    out = {}
    for g in sorted(k for k in groups if k[0] != 'timeline'):
        v = groups[g]
        out[' / '.join(g)] = {'n': len(v), **{k: round(mean([d[k] for d in v if d[k] == d[k]]), 3) for k in
                                              ('pressure level', 'raw steps', 'pressure drop at the end (last 100 ms minus the 200 ms before)',
                                               'speed (keys/s)', 'end row (0 top)', 'end column', 'duration (ms)')}}
    out['pressure (position-corrected) in the last 300 ms, by 50 ms before the end'] = {
        kind: {f'{50 * k}-{50 * k + 50} ms': round(mean(groups[('timeline', kind, k)]), 3) for k in range(6)} for kind in ('broken', 'whole')}
    # Does the end pressure drop predict a break, among strokes ending on the top row?
    top = [(d, 1.0) for d in groups[('broken', 'ends on the top row')]] + [(d, 0.0) for d in groups[('whole', 'ends on the top row')]]
    for name in ('pressure drop at the end (last 100 ms minus the 200 ms before)', 'pressure level', 'speed (keys/s)'):
        rho, z = spearman([d[name] for d, _ in top], [b for _, b in top])
        out[f'top-row ends: {name} against broken'] = {'rho': round(rho, 3), 'z': round(z, 1), 'n': len(top)}
    return out, joined_into


if __name__ == '__main__':
    attempts_path, corpus_path, out_path = sys.argv[1:4]
    data = corrected(load(attempts_path, corpus_path))
    f, joined_into = part_f(attempts_path)
    report = {'A within a stroke': part_a(data), 'B across strokes': part_b(data),
              'E correlations': part_e(data, joined_into), 'F broken strokes': f}
    json.dump(report, open(out_path, 'w'), indent=1)
    print(json.dumps(report, indent=1))
