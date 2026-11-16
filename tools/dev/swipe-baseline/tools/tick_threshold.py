#!/usr/bin/env python3
"""Phantom buzzes against the first swipe tick's distance (owner's request, Nov 10).

The swipe tick (Haptics.SwipeTicks) plays its first tick once the contact is a distance from where it landed, on a letter
(today 0.6 keys, Contact.SWIPE_KEYS). For each contact in the owner's raw logs this finds how far from its landing it
got on letters, and when, and sorts it by what it gave:
- word: a swipe that typed a word that was kept (not removed before the next word);
- removed: a swipe that typed a word that was then removed (a mistype or a wrong word);
- flick: a delete flick;
- nothing: no word and no flick (a phantom buzz, if it ticked);
- tap: read as a tap.
A phantom is a contact that ticked and gave nothing or a tap. For each first-tick distance it counts the phantoms
silenced, the meant swipes that lose every tick, and how much later a meant swipe's first tick plays.
Each contact is read once across overlapping exports. Counts only.
Usage: tick_threshold.py raw-log.jsonl... [--since-build 2.0.0]"""
import json
import math
import sys
from collections import Counter

logs = [a for a in sys.argv[1:] if not a.startswith('--')]
since = sys.argv[sys.argv.index('--since-wall') + 1] if '--since-wall' in sys.argv else None
if since:
    logs.remove(since)
    since = int(since)
BACKSPACE = 67
LETTERS = set('QWERTYUIOPASDFGHJKLZXCVBNM')
ROWS = [list("QWERTYUIOP"), list("ASDFGHJKL") + ["Bksp"], ["Alt"] + list("ZXCVBNM") + ["Sym", "Enter"]]


def key_at(x, y):
    if not (0 <= x <= 1 and 0 <= y <= 1):
        return ''
    row = min(3, int(y * 4))
    return 'bottom' if row == 3 else ROWS[row][min(9, int(x * 10))]


def keys(a, b):
    return math.hypot((b[0] - a[0]) * 10, (b[1] - a[1]) * 4)


# DeleteSwipe today, as in delete_zone.py.
def is_flick(pts):
    first, last = pts[0], pts[-1]
    if first[0] - last[0] < .1 or any(p[0] < last[0] - .05 for p in pts):
        return False
    path = sum(keys(a, b) for a, b in zip(pts, pts[1:]))
    wander = path / max(keys(first, last), 1e-6)
    if first[0] >= .875:
        return .1 <= first[1] < .62 and .175 <= last[1] < .625 and wander <= 1.15
    return first[0] >= .76 and .25 <= first[1] < .56 and .25 <= last[1] < .6 and abs(last[1] - first[1]) <= .1 and wander <= 1.1


seen = set()
contacts = []
for path in logs:
    boot, wall0 = None, None
    events = []
    for line in open(path, encoding='utf-8'):
        try:
            d = json.loads(line)
        except ValueError:
            continue
        if d.get('k') == 'header':
            boot = round((d['wall'] - d['t']) / 60000)
            wall0 = d['wall'] - d['t']
        events.append((boot, wall0, d))
    current = None
    for i, (boot, wall0, d) in enumerate(events):
        k = d.get('k')
        if k == 'm':
            pts = [s for s in d.get('s', []) if s and s[0] is not None]
            if d['a'] == 0:
                current = {'boot': boot, 'wall0': wall0, 'down': d['t'], 'pts': list(pts), 'i': i, 'keys': 0}
            elif current is not None:
                current['pts'] += pts
                if d['a'] in (1, 3):
                    current['up'] = d['t']
                    current['j'] = i
                    contacts.append((current, events))
                    current = None
        elif k == 'key' and d.get('d') and current is not None:
            current['keys'] += 1

rows = []
for c, events in contacts:
    key = (c['boot'], c['down'])
    if key in seen or len(c['pts']) < 1 or c['wall0'] is None:
        continue
    if since and c['wall0'] + c['down'] < since:
        continue
    seen.add(key)
    land = c['pts'][0]
    # Distance from landing on letters, and when each distance was first reached.
    reach = []
    best = 0.0
    for p in c['pts']:
        if key_at(p[0], p[1]) not in LETTERS:
            continue
        dist = keys(land, p)
        if dist > best:
            best = dist
            reach.append((dist, p[2]))
    # What the contact gave: the outcome logged for it, then what followed.
    kind = 'nothing'
    for b, w, d in events[c['j']:c['j'] + 40]:
        if d.get('k') == 'out' and d.get('start') == c['down']:
            if d.get('type') == 'tap':
                kind = 'tap'
            elif d.get('type') == 'swipe':
                kind = 'flick' if len(c['pts']) > 1 and is_flick(c['pts']) else 'swipe?'
            break
        if d.get('k') == 'm' and d.get('a') == 0:
            break
    if kind == 'swipe?':
        kind = 'nothing'
        out_at = None
        for n, (b, w, d) in enumerate(events[c['j']:c['j'] + 60]):
            if d.get('k') == 'rec':
                kind = 'word'
                out_at = n
                break
            if d.get('k') == 'm' and d.get('a') == 0:
                break
        if kind == 'word':
            # Removed: Backspace or a delete flick before the next word starts.
            for b, w, d in events[c['j'] + out_at + 1:c['j'] + 400]:
                if d.get('k') == 'key' and d.get('d') and not d.get('g') and d.get('repeat', 0) == 0:
                    if d['c'] == BACKSPACE:
                        kind = 'removed'
                    break
                if d.get('k') == 'text' and d.get('cause') == 'backspace':
                    kind = 'removed'
                    break
                if d.get('k') == 'rec' or (d.get('k') == 'text' and d.get('via') in ('swipe', 'key') and d.get('cause') == 'commit'):
                    break
    rows.append({'kind': kind, 'max': best, 'reach': reach, 'keys': c['keys'],
                 'land_key': key_at(land[0], land[1]), 'dur': c['up'] - c['down']})


def first_at(row, t):
    for dist, time in row['reach']:
        if dist >= t:
            return time
    return None


kinds = Counter(r['kind'] for r in rows)
out = {'contacts': len(rows), 'by_kind': dict(kinds)}
THRESHOLDS = [.6, .8, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5]
table = []
ticked_now = [r for r in rows if r['max'] >= .6]
phantoms_now = [r for r in ticked_now if r['kind'] in ('nothing', 'tap')]
meant = [r for r in rows if r['kind'] in ('word', 'flick')]
for t in THRESHOLDS:
    phantom = [r for r in rows if r['kind'] in ('nothing', 'tap') and r['max'] >= t]
    lost = [r for r in meant if r['max'] >= .6 and r['max'] < t]
    delays = sorted(first_at(r, t) - first_at(r, .6) for r in meant if r['max'] >= t)
    removed = [r for r in rows if r['kind'] == 'removed' and r['max'] >= t]
    table.append({
        'first_tick_keys': t,
        'phantoms_left': len(phantom),
        'phantoms_silenced': len(phantoms_now) - len(phantom),
        'meant_ticked': sum(1 for r in meant if r['max'] >= t),
        'meant_losing_all_ticks': len(lost),
        'meant_losing_by_kind': dict(Counter(r['kind'] for r in lost)),
        'first_tick_later_ms_median': delays[len(delays) // 2] if delays else None,
        'first_tick_later_ms_p90': delays[int(len(delays) * .9)] if delays else None,
        'removed_still_ticking': len(removed),
    })
out['phantoms_now'] = len(phantoms_now)
out['phantoms_now_by_kind'] = dict(Counter(r['kind'] for r in phantoms_now))
out['phantoms_now_with_key_press'] = sum(1 for r in phantoms_now if r['keys'])
mx = sorted(r['max'] for r in phantoms_now)
mw = sorted(r['max'] for r in meant if r['max'] >= .6)
q = lambda xs, f: round(xs[min(len(xs) - 1, int(len(xs) * f))], 2) if xs else None
out['phantom_max_keys_quartiles'] = [q(mx, f) for f in (.1, .25, .5, .75, .9)]
out['meant_max_keys_quartiles'] = [q(mw, f) for f in (.01, .05, .1, .25, .5)]
out['table'] = table
print(json.dumps(out, indent=1))
