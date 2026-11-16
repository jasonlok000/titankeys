#!/usr/bin/env python3
"""Space gestures, the owner's proposal (Nov 10): where quick taps land near the bottom of the touchpad, and what a
larger tap area would cost. CursorGesture's steer pattern (a lone quick tap, then within 30-150 ms a second touch about
the same place that drags) is looked for in everyday logs for each candidate area. A match whose second touch typed a word
is a word the gesture would have taken. Counts only. Usage: tap_area.py raw-log.jsonl"""
import bisect
import json
import math
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

FIRST_TAP, TAP_KEYS, MIN_GAP, MAX_GAP, APART, ALONE, AFTER_KEY = 130, .3, 30, 150, 1.0, 200, 500
DRAG_KEYS, SIDEWAYS, UP_ROWS, JUMP = .5, 2.0, .6, 1.0

events = R.load(sys.argv[1])
rep = R.Replayer(events).run()
key_downs = sorted(e['t'] for e in events if e.get('k') == 'key' and e.get('d') and not e.get('g'))
kept = {}
for a in rep.attempts:
    if a.rec is not None and a.slot is not None and a.rec.get('words'):
        kept[a.id] = rep.slots[a.slot].final is not None


def dist(a, b):
    return math.hypot((b[0] - a[0]) * 10, (b[1] - a[1]) * 4)


touches = []
for c in rep.contacts:
    pts = [p for p in c['pts'] if p[0] is not None]
    if not pts or c['u'] is None:
        continue
    land = pts[0]
    touches.append({'down': c['d'], 'up': c['u'], 'land': land, 'pts': pts, 'out': c.get('out'),
                    'travel': max(dist(land, p) for p in pts), 'dur': c['u'] - c['d'],
                    'across': max(abs(p[0] - land[0]) * 10 for p in pts), 'upward': max((land[1] - p[1]) * 4 for p in pts),
                    'jump': max((dist(pts[i - 1], pts[i]) for i in range(1, len(pts))), default=0)})
touches.sort(key=lambda t: t['down'])

# ---- 1. Where quick taps land, by row band, and how far down the sensor reaches at all ----------------------------------
taps = [t for t in touches if t['dur'] <= FIRST_TAP and t['travel'] < TAP_KEYS]
bands = [(0, .5, 'letter rows above'), (.5, .625, 'bottom letter row, upper half'), (.625, .75, 'bottom letter row, lower half'),
         (.75, .8, 'Space row, top'), (.8, .85, 'Space row, middle'), (.85, 2, 'Space row, lowest the sensor reads')]
lowest = sorted(p[1] for t in touches for p in t['pts'])
print('touches', len(touches), 'quick taps', len(taps))
print('the sensor reads down to y =', round(lowest[-1], 3), '(99.9th percentile', round(lowest[int(.999 * (len(lowest) - 1))], 3), ')')
for lo, hi, name in bands:
    inside = [t for t in taps if lo <= t['land'][1] < hi]
    middle = sum(.35 <= t['land'][0] < .65 for t in inside)
    print(f'  taps landing in {name:<34} {len(inside):>5}   of them in the middle (V/B columns) {middle}')


# ---- 2. The steer pattern for each candidate area -----------------------------------------------------------------------
def area_current(p):
    return p[1] >= .75


def area_vb_lower(p):
    return p[1] >= .75 or (p[1] >= .625 and .4 <= p[0] < .6)


def area_vb_keys(p):
    return p[1] >= .75 or (p[1] >= .5 and .4 <= p[0] < .6)


def area_row_lower(p):
    return p[1] >= .625


def band(top, left=0.0, right=1.0):
    return lambda p: p[1] >= .75 or (p[1] >= top and left <= p[0] < right)


AREAS = {'today: Space row only (y >= 0.75)': area_current,
         'Space row + a band 0.03 above it (y >= 0.72), middle third': band(.72, .35, .65),
         'Space row + a band 0.05 above it (y >= 0.70), middle third': band(.70, .35, .65),
         'Space row + a band 0.05 above it, full width': band(.70),
         'Space row + lower half of V and B': area_vb_lower,
         'Space row + all of V and B': area_vb_keys,
         'Space row + lower half of the whole bottom letter row': area_row_lower}

downs = [t['down'] for t in touches]
for name, inside in AREAS.items():
    for sideways_only in (False, True):
        found = Counter()
        for i, a in enumerate(touches[:-1]):
            if not inside(a['land']) or a['dur'] > FIRST_TAP or a['travel'] >= TAP_KEYS:
                continue
            if i > 0 and a['down'] - touches[i - 1]['up'] < ALONE:
                continue
            k = bisect.bisect_right(key_downs, a['down']) - 1
            if k >= 0 and a['down'] - key_downs[k] < AFTER_KEY:
                continue
            b = touches[i + 1]
            gap = b['down'] - a['up']
            if not (MIN_GAP <= gap <= MAX_GAP) or not inside(b['land']) or abs(b['land'][0] - a['land'][0]) * 10 > APART:
                continue
            if b['jump'] > JUMP:
                continue
            sideways = b['across'] >= DRAG_KEYS and b['across'] >= SIDEWAYS * b['upward']
            # Today a drag up from the Space row counts too. A touch that lands on V or B and goes up is a word's start.
            upward = not sideways_only and b['upward'] >= UP_ROWS
            if not (sideways or upward):
                continue
            above_space = b['land'][1] < .75 or a['land'][1] < .75
            if name.startswith('today') and not sideways_only:
                found[f"tap landed y {min(.885, round(a['land'][1] * 40) / 40):.3f}"] += 1
            if b['out'] is not None and kept.get(b['out']):
                found['would take a kept word' + (' (landed above Space)' if above_space else '')] += 1
            elif b['out'] is not None:
                found['would take a swiped word that was not kept'] += 1
            else:
                found['typed nothing (a gesture, or nothing lost)'] += 1
        rule = 'sideways drag only' if sideways_only else 'sideways or up, as today'
        print(f'{name} | {rule}: {dict(found)}')


# ---- 3. The shape of the second touch: today's sideways gestures against the words a V/B band would take -------------
def shape(t):
    lowest_y = min(p[1] for p in t['pts'])
    return {'across': round(t['across'], 2), 'up rows': round(t['upward'], 2), 'highest row reached': round(lowest_y * 4, 2),
            'landed row': round(t['land'][1] * 4, 2), 'ms': t['dur']}


inside = band(.70)
gestures, words = [], []
for i, a in enumerate(touches[:-1]):
    if not inside(a['land']) or a['dur'] > FIRST_TAP or a['travel'] >= TAP_KEYS:
        continue
    if i > 0 and a['down'] - touches[i - 1]['up'] < ALONE:
        continue
    k = bisect.bisect_right(key_downs, a['down']) - 1
    if k >= 0 and a['down'] - key_downs[k] < AFTER_KEY:
        continue
    b = touches[i + 1]
    gap = b['down'] - a['up']
    if not (MIN_GAP <= gap <= MAX_GAP) or not inside(b['land']) or abs(b['land'][0] - a['land'][0]) * 10 > APART or b['jump'] > JUMP:
        continue
    if not (b['across'] >= DRAG_KEYS and b['across'] >= SIDEWAYS * b['upward']):
        continue
    if b['out'] is not None and kept.get(b['out']):
        att = rep.attempts[b['out']]
        word = R.norm(att.rec['words'][0])
        info = shape(b)
        info['word length'] = len(word)
        info['all bottom-row letters'] = all(ch in 'zxcvbnm' for ch in word.replace("'", ''))
        info['changed within 5 s'] = any(isinstance(ev[1], (int, float)) and 0 <= ev[1] - att.out['end'] <= 5000
                                         and (ev[0] == 'choose' or str(ev[0]).startswith('backspace')) for ev in rep.slots[att.slot].events)
        words.append(info)
    else:
        gestures.append(shape(b))
print('kept words a band would take:', words)
for key in ('up rows', 'highest row reached', 'across', 'landed row'):
    v = sorted(g[key] for g in gestures)
    print(f'gestures, {key}: min {v[0]}, 10% {v[len(v) // 10]}, median {v[len(v) // 2]}, 90% {v[9 * len(v) // 10]}, max {v[-1]}')
