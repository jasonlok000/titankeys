#!/usr/bin/env python3
"""The owner's moved-up tap area, at two heights, against today's rule (Nov 10).
1. The Space gestures export: each intended gesture (each long touch in a steer or select try) through today's rule and
   both heights, with the gap for a dragging touch at 150 or 200 ms. The port is first checked against the keyboard's own
   decisions in the export.
2. The raw logs: the same rules over every touch of everyday typing. A word that a rule takes as a gesture is a cost,
   split by whether it started on V or B.
Counts only. Usage: space_compare.py export.jsonl raw-log.jsonl"""
import json
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gesture_sim as G
import rawlog as R

export_path, log_path = sys.argv[1:3]
rows = [json.loads(l) for l in open(export_path, encoding='utf-8')]
tries = rows[1:]

VARIANTS = {'today (Space row)': (G.today, 150, 2), 'today, gap 200 for a drag': (G.today, 200, 2)}
for area_name, area in (('lower half of V and B', G.vb_lower), ('lower half, as wide as Space (C to N)', G.space_width)):
    for ratio in (1.5,):
        VARIANTS[f'{area_name}, gap 200, sideways {ratio}:1'] = (area, 200, ratio)

# ---- 1. The export ------------------------------------------------------------------------------------------------------
stream, touches, actual = [], {}, {}
tid = 0
for n, attempt in enumerate(tries):
    counts = []
    open_touch = None
    for kind, t, data in attempt['events']:
        if kind == 'gesture':
            if data['event'] == 'down':
                counts.append('counts=true' in data['detail'])
            if data['event'] == 'drag' and open_touch is not None:
                actual[open_touch] = 'select' if 'words=true' in data['detail'] else 'steer'
            if data['event'] == 'double' and touches:
                actual.setdefault(max(touches), 'double')
        if kind == 'key' and data['d'] and data['repeat'] == 0 and not data['g']:
            stream.append(('key', None, t, True, None))
        if kind != 'm':
            continue
        pts = [tuple(p[:2]) + (p[3],) for p in data['p'] if p[0] is not None]
        if data['a'] == 0:
            tid += 1
            open_touch = tid
            touches[tid] = {'try': n, 'kind': attempt['kind'], 'pts': list(pts), 'counts': counts[-1] if counts else True}
            if pts:
                stream.append(('down', pts[0], t, touches[tid]['counts'], tid))
                for p in pts[1:]:
                    stream.append(('move', p, p[2], True, tid))
        elif open_touch is not None:
            touches[open_touch]['pts'] += pts
            last = None
            for p in pts:
                stream.append(('move', p, p[2], True, open_touch))
                last = p
            if data['a'] in (1, 3):
                stream.append(('up', None, t, True, open_touch))
                open_touch = None
stream.sort(key=lambda e: (e[2], {'key': 0, 'down': 1, 'move': 2, 'up': 3}[e[0]]))

today = G.replay(stream, G.Gesture(G.today, 150))
drags_actual = {k: v for k, v in actual.items() if v in ('steer', 'select')}
drags_port = {k: v for k, v in today.items() if v in ('steer', 'select')}
print('port check: the keyboard started', len(drags_actual), 'drags; the port with today\'s rule starts', len(drags_port),
      '; same touches and kinds:', sum(drags_port.get(k) == v for k, v in drags_actual.items()))


def travel(t):
    pts = t['pts']
    return max((G.dist(pts[0], p) for p in pts), default=0)


intended = [k for k, t in touches.items() if t['kind'] in ('steer', 'select') and t['pts'] and travel(t) >= 1
            and t['pts'][0][1] >= .5]
late = [k for k in intended if touches[k]['try'] >= len(tries) // 2]
print(f'intended gestures in the export: {len(intended)} (later half of the session: {len(late)})')
for name, (area, gap, ratio) in VARIANTS.items():
    fired = G.replay(stream, G.Gesture(area, gap, ratio))
    caught = [k for k in intended if fired.get(k) == touches[k]['kind']]
    wrong = [k for k in intended if fired.get(k) in ('steer', 'select') and fired.get(k) != touches[k]['kind']]
    caught_late = sum(k in late for k in caught)
    print(f'  {name:<32} caught {len(caught):>2}/{len(intended)}  wrong kind {len(wrong)}  later half {caught_late}/{len(late)}')

best = G.replay(stream, G.Gesture(G.space_width, 200, 1.5))
order = sorted(touches)
for k in intended:
    if best.get(k) == touches[k]['kind']:
        continue
    i = order.index(k)
    before = []
    for j in order[max(0, i - 3):i]:
        t = touches[j]
        if not t['pts']:
            continue
        gap = touches[k]['pts'][0][2] - t['pts'][-1][2]
        before.append(f"row {t['pts'][0][1] * 4:.2f} {t['pts'][-1][2] - t['pts'][0][2]}ms ends {gap}ms before")
    land = touches[k]['pts'][0]
    print(f"  missed: try #{touches[k]['try']} {touches[k]['kind']} -> {best.get(k)}; swipe lands row {land[1] * 4:.2f}; before: {before}")

# ---- 2. The raw logs ----------------------------------------------------------------------------------------------------
events = R.load(log_path)
rep = R.Replayer(events).run()
keys = [e['t'] for e in events if e.get('k') == 'key' and e.get('d') and not e.get('g')]
kept = {a.id: (a.rec is not None and a.slot is not None and bool(a.rec.get('words')) and rep.slots[a.slot].final is not None)
        for a in rep.attempts}
stream, info = [('key', None, t, True, None) for t in keys], {}
for i, c in enumerate(rep.contacts):
    pts = [tuple(p[:3]) for p in c['pts'] if p[0] is not None]
    if not pts or c['u'] is None:
        continue
    info[i] = {'out': c.get('out'), 'land': pts[0]}
    stream.append(('down', pts[0], c['d'], True, i))
    for p in pts[1:]:
        stream.append(('move', p, p[2], True, i))
    stream.append(('up', None, c['u'], True, i))
stream.sort(key=lambda e: (e[2], {'key': 0, 'down': 1, 'move': 2, 'up': 3}[e[0]]))
words_total = sum(1 for v in info.values() if v['out'] is not None and kept.get(v['out']))
vb_words = sum(1 for v in info.values() if v['out'] is not None and kept.get(v['out']) and .5 <= v['land'][1] < .75
               and .4 <= v['land'][0] < .6)
cn_words = sum(1 for v in info.values() if v['out'] is not None and kept.get(v['out']) and .5 <= v['land'][1] < .75
               and .3 <= v['land'][0] < .7)
print(f'raw log: {len(info)} touches, {words_total} kept swiped words, {vb_words} of them starting on V or B, {cn_words} on C, V, B or N')
base = G.replay(stream, G.Gesture(G.today, 150))
for name, (area, gap, ratio) in VARIANTS.items():
    fired = G.replay(stream, G.Gesture(area, gap, ratio))
    cost = Counter()
    for k, r in fired.items():
        if r not in ('steer', 'select'):
            continue
        v = info[k]
        if v['out'] is not None and kept.get(v['out']):
            on_vb = v['land'][1] < .75
            if on_vb:
                cost['  of them on ' + 'ZXCVBNM'[min(6, max(0, int(v['land'][0] * 10) - 1))]] += 1
            cost['kept words taken' + (', starting on V or B' if on_vb else '')] += 1
        elif k not in base:
            cost['new gestures where nothing was typed'] += 1
    print(f'  {name:<32} {dict(cost)}')
