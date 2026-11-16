#!/usr/bin/env python3
"""Why some Space gestures trigger and some don't (owner's request, Nov 10), from a TitanKeys Space gestures export.

Each try's touches and keys are walked along the path that CursorGesture takes (its numbers come from the export's
header), and a failed try is put down to the first rule it breaks, with the value that broke it. Then, for each rule,
the values from tries that worked, tries that failed on it, and tries that were not meant are listed, and a new limit is
suggested only where the data separates them. Counts and numbers only: the field held sample text.
Usage: space_gestures.py export.jsonl [out.json]"""
import json
import math
import sys
from collections import Counter, defaultdict

rows = [json.loads(l) for l in open(sys.argv[1], encoding='utf-8')]
header, tries = rows[0], rows[1:]
T = header['tuning']
BOTTOM = .75
SPACE_KEY = 62  # KeyEvent.KEYCODE_SPACE


def keys_apart(a, b):
    return math.hypot((b[0] - a[0]) * 10, (b[1] - a[1]) * 4)


def contacts(events):
    """The try's touches: landing point, all points, down and up times, and the gesture code's 'counts' for each."""
    out, current, counts = [], None, []
    for kind, t, data in events:
        if kind == 'gesture' and data['event'] == 'down':
            counts.append('counts=true' in data['detail'])
        if kind != 'm':
            continue
        pts = [p for p in data['p'] if p[0] is not None]
        if data['a'] == 0:
            current = {'down': t, 'pts': list(pts), 'up': None, 'cancel': False, 'fingers': data['fingers']}
        elif current is not None:
            current['pts'] += pts
            current['fingers'] = max(current['fingers'], data['fingers'])
            if data['a'] in (1, 3):
                current['up'] = t
                current['cancel'] = data['a'] == 3
                out.append(current)
                current = None
    for i, c in enumerate(out):
        c['counts'] = counts[i] if i < len(counts) else None
        land = c['pts'][0] if c['pts'] else None
        c['land'] = land
        c['bottom'] = land is not None and land[1] >= BOTTOM
        c['travel'] = max((keys_apart(land, p) for p in c['pts']), default=0) if land else 0
        c['across'] = max((abs(p[0] - land[0]) * 10 for p in c['pts']), default=0) if land else 0
        c['upward'] = max(((land[1] - p[1]) * 4 for p in c['pts']), default=0) if land else 0
        c['jump'] = max((keys_apart(c['pts'][j - 1], c['pts'][j]) for j in range(1, len(c['pts']))), default=0)
        c['duration'] = (c['up'] - c['down']) if c['up'] is not None else None
    return out


def judge(attempt):
    """The first rule that this try breaks on its way to the gesture it meant, as (rule, value), or None."""
    want_taps = 1 if attempt['kind'] == 'steer' else 2
    events = attempt['events']
    touches = contacts(events)
    key_downs = [(t, d['c']) for k, t, d in events if k == 'key' and d['d'] and not d['g'] and d['repeat'] == 0]
    if not header.get('cursorControl', True):
        return 'gestures off in Settings', None
    if not touches:
        if any(c == SPACE_KEY for _, c in key_downs):
            return 'Space pushed down as a key, with no touch on the pad', len(key_downs)
        return 'no touch reached the touchpad', None
    # The drag is the touch that went furthest. The taps are the touches just before it.
    drag_at = max(range(len(touches)), key=lambda i: touches[i]['travel'])
    taps = touches[max(0, drag_at - want_taps):drag_at]
    drag = touches[drag_at]
    if len(taps) < want_taps:
        if any(c == SPACE_KEY for _, c in key_downs):
            return 'Space pushed down as a key: a key press calls the gesture off', sum(c == SPACE_KEY for _, c in key_downs)
        return f'too few taps before the swipe ({len(taps)} of {want_taps})', len(taps)
    first = taps[0]
    sequence = taps + [drag]
    # A key pressed during the gesture, or within AFTER_KEY_MILLIS before the first tap, calls it off.
    for t, code in key_downs:
        if first['down'] - T['afterKeyMillis'] <= t <= (drag['up'] or drag['down']):
            name = 'Space' if code == SPACE_KEY else f'key {code}'
            if t >= first['down']:
                return f'{name} pushed down as a key during the gesture: it calls it off', t - first['down']
            return f'first tap too soon after a {name} key press (afterKeyMillis)', first['down'] - t
    for c in sequence:
        if c['fingers'] > 1 or c['cancel']:
            return 'a second finger, or the touch was cancelled', c['fingers']
    for c in sequence:
        if not c['bottom']:
            return 'a tap or the swipe landed above the bottom row', round(c['land'][1] * 4, 2) if c['land'] else None
    before = [c for c in touches if c['up'] is not None and c['up'] <= first['down']]
    if before and first['down'] - before[-1]['up'] < T['aloneMillis']:
        return 'first tap not alone: a touch lifted just before it (aloneMillis)', first['down'] - before[-1]['up']
    if first['counts'] is False:
        return 'the touch could not count (a resting finger, or the pause after a key)', None
    if first['duration'] > T['firstTapMillis']:
        return 'first tap held too long (firstTapMillis)', first['duration']
    if first['travel'] >= T['tapKeys']:
        return 'first tap moved too far to be a tap (tapKeys)', round(first['travel'], 2)
    for i in range(1, len(sequence)):
        prev, nxt = sequence[i - 1], sequence[i]
        gap = nxt['down'] - prev['up']
        if gap < T['minGapMillis']:
            return 'gap between touches too short (minGapMillis)', gap
        if gap > T['maxGapMillis']:
            return 'gap between touches too long (maxGapMillis)', gap
        apart = abs(nxt['land'][0] - prev['land'][0]) * 10
        if apart > T['maxApartKeys']:
            return 'touches landed too far apart (maxApartKeys)', round(apart, 2)
        if nxt is not drag:
            if nxt['duration'] > T['secondTapMillis']:
                return 'second tap held too long (secondTapMillis)', nxt['duration']
            if nxt['travel'] >= T['tapKeys']:
                return 'second tap moved too far to be a tap (tapKeys)', round(nxt['travel'], 2)
    if drag['jump'] > T['jumpKeys']:
        return 'the swipe jumped, as a second finger does (jumpKeys)', round(drag['jump'], 2)
    sideways = drag['across'] >= T['dragKeys'] and drag['across'] >= T['sideways'] * drag['upward']
    if not sideways and drag['upward'] < T['upRows']:
        if drag['across'] < T['dragKeys']:
            return 'the swipe was too short (dragKeys)', round(drag['across'], 2)
        return 'the swipe was not sideways enough (sideways)', round(drag['across'] / max(drag['upward'], 1e-6), 2)
    if len(touches) > drag_at + 1 or drag_at > want_taps:
        return 'extra touches around the gesture', len(touches)
    return 'no rule broken: look at this try by hand', None


report = {'tries': len(tries), 'by kind': {}, 'failed tries by rule': {}, 'values': {}, 'suggestions': []}
values = defaultdict(lambda: defaultdict(list))
for attempt in tries:
    kind = attempt['kind']
    stats = report['by kind'].setdefault(kind, Counter())
    stats['worked' if attempt['worked'] else 'failed'] += 1
    stats['result: ' + attempt['result']] += 1
    touches = contacts(attempt['events'])
    group = 'not meant' if kind == 'other' else 'worked' if attempt['worked'] else 'failed'
    # The measurements that the rules use, from every try, so limits can be compared across groups.
    if touches:
        drag_at = max(range(len(touches)), key=lambda i: touches[i]['travel'])
        taps = touches[max(0, drag_at - (1 if kind != 'select' else 2)):drag_at]
        if taps:
            values['firstTapMillis'][group].append(taps[0]['duration'])
            values['tapKeys'][group].append(round(max(t['travel'] for t in taps), 2))
        seq = taps + [touches[drag_at]]
        for i in range(1, len(seq)):
            values['maxGapMillis'][group].append(seq[i]['down'] - seq[i - 1]['up'])
            values['maxApartKeys'][group].append(round(abs(seq[i]['land'][0] - seq[i - 1]['land'][0]) * 10, 2))
        if len(taps) > 1:
            values['secondTapMillis'][group].append(taps[1]['duration'])
        values['dragKeys'][group].append(round(touches[drag_at]['across'], 2))
    if kind != 'other' and not attempt['worked']:
        rule, value = judge(attempt)
        entry = report['failed tries by rule'].setdefault(rule, {'tries': 0, 'values': []})
        entry['tries'] += 1
        if value is not None:
            entry['values'].append(value)
report['by kind'] = {k: dict(v) for k, v in report['by kind'].items()}
for name, groups in values.items():
    report['values'][name] = {g: sorted(v) for g, v in groups.items()}

# A new limit only where the data separates the tries that were meant from those that were not.
UPPER = {'firstTapMillis', 'secondTapMillis', 'tapKeys', 'maxGapMillis', 'maxApartKeys'}
for name, groups in values.items():
    meant = groups.get('worked', []) + groups.get('failed', [])
    other = groups.get('not meant', [])
    if not meant:
        continue
    if name in UPPER:
        need = sorted(meant)[int(.95 * (len(meant) - 1))]
        if need > T[name]:
            blocked = sum(v <= need for v in other)
            report['suggestions'].append(f'{name}: {T[name]} -> {need} covers 95% of meant tries; '
                                         f'{blocked} of {len(other)} not-meant tries would then fit this limit')
    elif name == 'dragKeys':
        need = sorted(meant)[int(.05 * (len(meant) - 1))]
        if need < T[name]:
            report['suggestions'].append(f'dragKeys: {T[name]} -> {need} covers 95% of meant swipes; '
                                         f'{sum(v >= need for v in other)} of {len(other)} not-meant tries reach it')
if len(sys.argv) > 2:
    json.dump(report, open(sys.argv[2], 'w'), indent=1)
print(json.dumps({k: report[k] for k in ('tries', 'by kind', 'failed tries by rule', 'suggestions')}, indent=1))
