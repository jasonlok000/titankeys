#!/usr/bin/env python3
"""#30 follow-up: can a rule drop touches made by resting fingers (long holds, flicker bursts, flat heavy contact) and
keep the owner's real taps and swipes? Resting touches: the Touch size screen recording (phases 1-7; phase 8, the notes,
is real swiping and is left out). Real touches: every touchpad contact in the latest raw log that TitanKeys read as a kept
swipe or as a tap. Counts only (the raw log holds typed text; nothing of it is printed).
Usage: rest_rules.py touch-size.jsonl raw-log.jsonl out.json"""
import json
import math
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

rest_path, log_path, out_path = sys.argv[1:4]
# The usual sensor step per touchpad cell (10 columns, 4 rows of the touchpad's height), from raw-log-10 (#52).
USUAL = [1.53, 1.90, 1.98, 1.84, 1.77, 1.76, 1.72, 1.63, 1.50, 1.41,
         2.26, 2.89, 2.90, 2.70, 2.53, 2.36, 2.38, 2.27, 1.96, 1.29,
         2.26, 3.10, 2.98, 3.00, 2.74, 2.52, 2.52, 2.21, 2.26, 2.26,
         2.26, 2.26, 2.26, 2.26, 2.26, 2.26, 2.26, 2.26, 2.26, 2.26]


def usual(x, y_pad):
    return USUAL[min(3, max(0, int(y_pad * 4))) * 10 + min(9, max(0, int(x * 10)))]


def measures(points, start_gap, near_count):
    """points: (x, y on the touchpad 0..1, sensor step, time ms)."""
    t0, t1 = points[0][3], points[-1][3]
    dur = t1 - t0
    keys = lambda a, b: math.hypot((b[0] - a[0]) * 10, (b[1] - a[1]) * 4)
    travel = sum(keys(points[i - 1], points[i]) for i in range(1, len(points)))
    still = dur
    for p in points:
        if keys(points[0], p) > .3:
            still = p[3] - t0
            break
    head = [p for p in points if p[3] - t0 <= 80] or points[:1]
    return {'duration': dur, 'travel': travel, 'still before moving': still, 'largest step': max(p[2] for p in points),
            'landing step over usual': max(p[2] - usual(p[0], p[1]) for p in head), 'gap to the touch before': start_gap,
            'touches within 1 s': near_count}


# ---- resting touches ----------------------------------------------------------------------------------------------------
rows = [json.loads(l) for l in open(rest_path, encoding='utf-8')][1:]
first = rows[0]['start']
rest = []
prev_end = None
starts = [r['start'] for r in rows]
for r in rows:
    secs = (r['start'] - first) / 1000
    if secs >= 400:  # phase 8: the owner's real swipes
        continue
    pts = [(p[0], p[1] * .75, p[2], p[4]) for p in r['points'] if p[0] is not None]
    if not pts:
        continue
    gap = r['start'] - prev_end if prev_end is not None else 10 ** 6
    prev_end = r['end']
    near = sum(abs(s - r['start']) <= 1000 for s in starts) - 1
    m = measures(pts, gap, near)
    m['read'] = 'swipe' if 'swipe' in (r['reading'] or '') else 'tap' if 'tap' in (r['reading'] or '') else ''
    m['phase'] = 1 if secs < 52 else 2 if secs < 100 else 3 if secs < 119 else 4 if secs < 252 else 5 if secs < 311 else 6 if secs < 343 else 7
    rest.append(m)

# ---- real touches from the raw log ---------------------------------------------------------------------------------------
rep = R.Replayer(R.load(log_path)).run()
taps = sorted(e['end'] for e in R.load(log_path) if e.get('k') == 'out' and e.get('type') == 'tap')
kept = {a.id for a in rep.attempts if a.slot is not None and rep.slots[a.slot].final}
contacts = [c for c in rep.contacts if c['pts'] and c.get('sz')]
real = []
cstarts = [c['d'] for c in contacts]
for i, c in enumerate(contacts):
    kind = None
    if c.get('out') is not None and c['out'] in kept:
        kind = 'swipe'
    elif c['u'] is not None and taps:
        j = min(range(len(taps)), key=lambda k: abs(taps[k] - c['u'])) if len(taps) < 5000 else None
        if j is not None and abs(taps[j] - c['u']) <= 30:
            kind = 'tap'
    if not kind:
        continue
    # Sizes belong to the raw points; take each sample's size from the nearest raw point in time.
    raw_t = [p[2] for p in c['raw']]
    sizes = [s / 1.125 for s in c['sz']]
    pts = []
    for x, y, t in [p for p in c['pts'] if p[0] is not None]:
        k = min(range(len(raw_t)), key=lambda k: abs(raw_t[k] - t))
        if k < len(sizes):
            pts.append((x, y, round(sizes[k]), t))
    if not pts:
        continue
    gap = c['d'] - contacts[i - 1]['u'] if i and contacts[i - 1]['u'] is not None else 10 ** 6
    near = sum(abs(s - c['d']) <= 1000 for s in cstarts) - 1
    m = measures(pts, gap, near)
    m['kind'] = kind
    real.append(m)

harm = [m for m in rest if m['read']]
print('resting touches', len(rest), 'read as input', len(harm), Counter(m['read'] for m in harm), '| real swipes', sum(m['kind'] == 'swipe' for m in real),
      'real taps', sum(m['kind'] == 'tap' for m in real))
RULES = {
    'held still 400+ ms before moving': lambda m: m['still before moving'] >= 400,
    'held still 600+ ms before moving': lambda m: m['still before moving'] >= 600,
    'longer than 1.5 s': lambda m: m['duration'] >= 1500,
    'longer than 2.5 s': lambda m: m['duration'] >= 2500,
    'landing 2+ steps over usual': lambda m: m['landing step over usual'] >= 2,
    'landing 3+ steps over usual': lambda m: m['landing step over usual'] >= 3,
    'largest step 6+': lambda m: m['largest step'] >= 6,
    'largest step 7+': lambda m: m['largest step'] >= 7,
    'flicker: under 80 ms and another touch within 1 s': lambda m: m['duration'] < 80 and m['touches within 1 s'] >= 1,
    'burst: 3+ other touches within 1 s': lambda m: m['touches within 1 s'] >= 3,
    'still 400+ ms OR landing 3+ over usual OR burst 3+': lambda m: m['still before moving'] >= 400 or m['landing step over usual'] >= 3 or m['touches within 1 s'] >= 3,
    'still 600+ ms OR largest step 7+ OR burst 3+': lambda m: m['still before moving'] >= 600 or m['largest step'] >= 7 or m['touches within 1 s'] >= 3,
    'step 6+ OR landing 2+ over usual': lambda m: m['largest step'] >= 6 or m['landing step over usual'] >= 2,
    'step 6+ OR longer than 2.5 s': lambda m: m['largest step'] >= 6 or m['duration'] >= 2500,
    'step 6+ OR landing 2+ over usual OR longer than 2.5 s': lambda m: m['largest step'] >= 6 or m['landing step over usual'] >= 2 or m['duration'] >= 2500,
    'step 5+ OR landing 2+ over usual': lambda m: m['largest step'] >= 5 or m['landing step over usual'] >= 2,
    'landing 1.5+ over usual': lambda m: m['landing step over usual'] >= 1.5,
}
# Mistaps that no clean rule catches, by phase.
LEFT = lambda m: not (m['largest step'] >= 6 or m['landing step over usual'] >= 2 or m['duration'] >= 2500)
out = {'resting read as input': len(harm), 'real swipes': sum(m['kind'] == 'swipe' for m in real), 'real taps': sum(m['kind'] == 'tap' for m in real), 'rules': {}}
print(f"{'rule':<55} mistaps dropped (of {len(harm)})   real swipes dropped   real taps dropped")
for name, rule in RULES.items():
    a = sum(rule(m) for m in harm)
    s = sum(rule(m) for m in real if m['kind'] == 'swipe')
    t = sum(rule(m) for m in real if m['kind'] == 'tap')
    out['rules'][name] = {'mistaps dropped': a, 'real swipes dropped': s, 'real taps dropped': t}
    print(f'{name:<55} {a:>4}                       {s:>4}                  {t:>4}')
for label, group in (('mistaps by phase', harm), ('all resting by phase', rest), ('mistaps no clean rule catches, by phase', [m for m in harm if LEFT(m)])):
    out[label] = dict(Counter(m['phase'] for m in group))
# How the measures compare.
for key in ('duration', 'still before moving', 'largest step', 'landing step over usual', 'touches within 1 s', 'travel'):
    q = lambda v: sorted(v)[len(v) // 2] if v else None
    out.setdefault('medians', {})[key] = {'mistaps': q([m[key] for m in harm]), 'real swipes': q([m[key] for m in real if m['kind'] == 'swipe']),
                                          'real taps': q([m[key] for m in real if m['kind'] == 'tap'])}
print(json.dumps({k: out[k] for k in ('mistaps by phase', 'mistaps no clean rule catches, by phase')}))
print('largest step, real swipes', dict(sorted(Counter(m['largest step'] for m in real if m['kind'] == 'swipe').items())), 'real taps', dict(sorted(Counter(m['largest step'] for m in real if m['kind'] == 'tap').items())))
json.dump(out, open(out_path, 'w'), indent=1)
