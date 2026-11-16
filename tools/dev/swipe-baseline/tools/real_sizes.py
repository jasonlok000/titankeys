#!/usr/bin/env python3
"""#30 follow-up: how often real use reaches the resting-finger rule's sizes. For every touchpad contact with sizes in a
raw log: its largest sensor step and its landing step over the usual (#52 table), by date and hour, for kept swipes, taps
and all other contacts. The touchpad reports one pointer only, so two fingers show up as one contact; a jump of 2+ keys
between consecutive points inside a contact marks such a moment. Counts only. Usage: real_sizes.py raw-log.jsonl out.json"""
import datetime
import json
import math
import os
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R
from rest_rules_table import usual

log_path, out_path = sys.argv[1:3]
events = R.load(log_path)
rep = R.Replayer(events).run()
taps = sorted(e['end'] for e in events if e.get('k') == 'out' and e.get('type') == 'tap')
kept = {a.id for a in rep.attempts if a.slot is not None and rep.slots[a.slot].final}
import bisect
out = defaultdict(lambda: defaultdict(Counter))
examples = []
for c in rep.contacts:
    if not c['pts'] or not c.get('sz') or c['u'] is None:
        continue
    if c.get('out') is not None:
        kind = 'kept swipe' if c['out'] in kept else 'other swipe'
    else:
        i = bisect.bisect_left(taps, c['u'])
        near = [taps[j] for j in (i - 1, i) if 0 <= j < len(taps)]
        kind = 'tap' if near and min(abs(t - c['u']) for t in near) <= 30 else 'no input'
    steps = [s / 1.125 for s in c['sz']]
    raw = c['raw']
    pts = [p for p in c['pts'] if p[0] is not None]
    top = round(max(steps))
    head = [(p, s) for p, s in zip(raw, steps) if p[2] - raw[0][2] <= 80]
    # Raw points are sensor pixels; the samples hold the letter-space position, so use the first sample for the spot.
    over = max(s for _, s in head) - usual(pts[0][0], pts[0][1]) if head and pts else 0
    jump = any(math.hypot((pts[i][0] - pts[i - 1][0]) * 10, (pts[i][1] - pts[i - 1][1]) * 4) >= 2 and pts[i][2] - pts[i - 1][2] <= 40
               for i in range(1, len(pts)))
    when = datetime.datetime.fromtimestamp(rep.clock.wall(c['d']) / 1000)
    day = when.strftime('%m-%d')
    g = out[day][kind]
    g['contacts'] += 1
    g['step 6+'] += top >= 6
    g['landing 2+ over usual'] += over >= 2
    g['rule (either)'] += top >= 6 or over >= 2
    g['jump of 2+ keys inside the contact'] += jump
    g['rule and jump'] += (top >= 6 or over >= 2) and jump
    out['by hour'][when.strftime('%m-%d %H')][kind + (' caught' if top >= 6 or over >= 2 else '')] += 1
    if kind in ('kept swipe', 'tap') and (top >= 6 or over >= 2):
        examples.append({'kind': kind, 'when': when.strftime('%m-%d %H:%M:%S'), 'largest step': top, 'over usual': round(over, 2),
                         'ms': c['u'] - c['d'], 'jump': jump})
report = {day: {k: dict(v) for k, v in kinds.items()} for day, kinds in out.items() if day != 'by hour'}
report['real touches the rule would catch'] = examples
report['step histogram, kept swipes and taps'] = dict(sorted(Counter(round(max(s / 1.125 for s in c['sz'])) for c in rep.contacts
                                                                      if c.get('sz') and (c.get('out') in kept)).items()))
json.dump(report, open(out_path, 'w'), indent=1)
for day in sorted(k for k in report if k[:2].isdigit()):
    for kind, v in sorted(report[day].items()):
        print(day, f'{kind:<12}', v)
print('real touches caught:', len(examples))
for e in examples[:20]:
    print('  ', e)
