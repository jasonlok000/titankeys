#!/usr/bin/env python3
"""Writes each first-try stroke several ways, to measure what multiple readings of one stroke can recover.
Usage: make_readings.py corpus.jsonl out.jsonl"""
import json
import math
import sys

corpus_path, out_path = sys.argv[1:3]
ONLY = set(sys.argv[3].split(',')) if len(sys.argv) > 3 else None
WIDTH, ROW = .1, 1 / 3  # one key in letter space


def clamp(v):
    return min(1.0, max(0.0, v))


def direction(xs, ys, ts, end):
    """Unit direction of travel, in key units, over the last or first 40 ms."""
    n = len(xs)
    if end:
        a = n - 1
        while a > 0 and ts[-1] - ts[a] < 40:
            a -= 1
        dx, dy = (xs[-1] - xs[a]) / WIDTH, (ys[-1] - ys[a]) / ROW
    else:
        b = 0
        while b < n - 1 and ts[b] - ts[0] < 40:
            b += 1
        dx, dy = (xs[b] - xs[0]) / WIDTH, (ys[b] - ys[0]) / ROW
    size = math.hypot(dx, dy)
    return (dx / size, dy / size) if size > 1e-6 else (0.0, 0.0)


def extend(x, y, t, start, end, keys=.6):
    x, y, t = list(x), list(y), list(t)
    if end:
        ux, uy = direction(x, y, t, True)
        x.append(clamp(x[-1] + ux * keys * WIDTH))
        y.append(clamp(y[-1] + uy * keys * ROW))
        t.append(t[-1] + 40)
    if start:
        ux, uy = direction(x, y, t, False)
        x.insert(0, clamp(x[0] - ux * keys * WIDTH))
        y.insert(0, clamp(y[0] - uy * keys * ROW))
        t = [0] + [v + 40 for v in t]
    return x, y, t


def stretch(values, factor):
    centre = (min(values) + max(values)) / 2
    return [clamp(centre + (v - centre) * factor) for v in values]


READINGS = {
    'plain': lambda x, y, t: (x, y, t),
    'x+3': lambda x, y, t: ([clamp(v + .03) for v in x], y, t),
    'x-3': lambda x, y, t: ([clamp(v - .03) for v in x], y, t),
    'x+6': lambda x, y, t: ([clamp(v + .06) for v in x], y, t),
    'x-6': lambda x, y, t: ([clamp(v - .06) for v in x], y, t),
    'y-5': lambda x, y, t: (x, [clamp(v - .05) for v in y], t),
    'y+5': lambda x, y, t: (x, [clamp(v + .05) for v in y], t),
    'sx12': lambda x, y, t: (stretch(x, 1.2), y, t),
    'sy125': lambda x, y, t: (x, stretch(y, 1.25), t),
    'endx': lambda x, y, t: extend(x, y, t, False, True),
    'startx': lambda x, y, t: extend(x, y, t, True, False),
    'bothx': lambda x, y, t: extend(x, y, t, True, True),
}

count = 0
with open(out_path, 'w', encoding='utf-8') as out:
    for line in open(corpus_path, encoding='utf-8'):
        e = json.loads(line)
        if not e['first'] or e['set'] not in ('prompted', 'hand', 'recorder'):
            continue
        for name, fn in READINGS.items():
            if ONLY and name not in ONLY:
                continue
            x, y, t = fn(e['x'], e['y'], e['t'])
            out.write(json.dumps({'id': e['id'] + '#' + name, 'context': e['context'], 'expected': e['expected'], 'x': [round(v, 5) for v in x],
                                  'y': [round(v, 5) for v in y], 't': t, 'outlines': name == 'plain'}) + '\n')
            count += 1
print('readings', count)
