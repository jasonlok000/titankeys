#!/usr/bin/env python3
"""#52: the owner's everyday touch sizes against the usual size at each spot, to scale the guide's trail. For every point
of every contact with sizes, the sensor step less the usual step there (USUAL_STEP, as TrailThickness has it), and the
same after the trail's smoothing (follow 0.12 per point). Prints percentiles only. Usage: trail_range.py raw-log.jsonl"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R
from rest_rules_table import usual

rep = R.Replayer(R.load(sys.argv[1])).run()
raw, smooth, peaks, ratios = [], [], [], []
contacts = 0
for c in rep.contacts:
    pts = [p for p in c['pts'] if p[0] is not None]
    sizes = c.get('sz') or []
    if not pts or len(sizes) != len(c['raw']) or len(pts) < 3:
        continue
    contacts += 1
    level = None
    peak = -9
    for p, s in zip(pts, sizes):
        if s is None:
            continue
        over = round(s / 1.125) - usual(p[0], p[1])
        raw.append(over)
        level = over if level is None else level + (over - level) * .12
        smooth.append(level)
        peak = max(peak, level)
    peaks.append(peak)


def pct(values, q):
    v = sorted(values)
    return round(v[min(len(v) - 1, int(q * (len(v) - 1)))], 2)


print('contacts with sizes', contacts, 'points', len(raw))
for name, values in (('points, step over usual', raw), ('smoothed, as the trail sees it', smooth),
                     ('highest smoothed point of each contact', peaks)):
    print(name, {q: pct(values, q) for q in (.02, .05, .1, .25, .5, .75, .9, .95, .98, .995)})
