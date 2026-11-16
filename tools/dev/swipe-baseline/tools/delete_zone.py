#!/usr/bin/env python3
"""What a bigger delete-flick start zone over I, O, M and Sym would cost and gain (Master's request, Nov 10).

Every swipe in the owner's raw logs is walked through DeleteSwipe.isDeleteWordSwipe as it is today, and again with the
start zone moved to a rectangle of today's zone's size over I, O, M and Sym. Each swipe is judged by what followed it:
- word: the keyboard recognised a word ('rec' follows the swipe), and nothing removed it before the next word;
- removed: a word was recognised, then Backspace or a delete flick removed it before the next word;
- flick: no word was recognised (today's delete flicks, and swipes that did nothing).
Duplicated exports are read once, by each event's own session (the header before it) and time. Until Nov 11 the key used
the file's last header for every event, so overlapping exports were counted several times. Counts and numbers only, no text.
Usage: delete_zone.py raw-log.jsonl... [--out out.json]"""
import json
import math
import sys
from collections import Counter

args = [a for a in sys.argv[1:] if not a.startswith('--')]
out_path = sys.argv[sys.argv.index('--out') + 1] if '--out' in sys.argv else None
if out_path in args:
    args.remove(out_path)

# DeleteSwipe.kt today.
RIGHT_EDGE, EDGE_START_TOP, EDGE_START_BOTTOM, EDGE_END_TOP, EDGE_END_BOTTOM, EDGE_WANDER = .875, .1, .62, .175, .625, 1.15
INNER_EDGE, MIDDLE_ROW_TOP, INNER_START_BOTTOM, INNER_END_BOTTOM, INNER_TILT, INNER_WANDER = .76, .25, .56, .6, .1, 1.1
MIN_TRAVEL, TURN_BACK = .1, .05
BACKSPACE = 67
WORD_GAP_MS = 6000

# Today's shaded zone: x .76 to 1 (Backspace and the inner box), y .175 to .62. Size .24 by .445.
W, H = 1 - INNER_EDGE, min(EDGE_START_BOTTOM, EDGE_END_BOTTOM) - max(EDGE_START_TOP, EDGE_END_TOP)


def path_keys(pts):
    return sum(math.hypot((b[0] - a[0]) * 10, (b[1] - a[1]) * 4) for a, b in zip(pts, pts[1:]))


def shape(pts):
    """The parts of the flick test that do not depend on where it starts."""
    first, last = pts[0], pts[-1]
    if first[0] - last[0] < MIN_TRAVEL or any(p[0] < last[0] - TURN_BACK for p in pts):
        return None
    wander = path_keys(pts) / max(math.hypot((last[0] - first[0]) * 10, (last[1] - first[1]) * 4), 1e-6)
    return wander


def today(pts):
    wander = shape(pts)
    if wander is None:
        return False
    first, last = pts[0], pts[-1]
    if first[0] >= RIGHT_EDGE:
        return EDGE_START_TOP <= first[1] < EDGE_START_BOTTOM and EDGE_END_TOP <= last[1] < EDGE_END_BOTTOM and wander <= EDGE_WANDER
    return (first[0] >= INNER_EDGE and MIDDLE_ROW_TOP <= first[1] < INNER_START_BOTTOM and MIDDLE_ROW_TOP <= last[1] < INNER_END_BOTTOM
            and abs(last[1] - first[1]) <= INNER_TILT and wander <= INNER_WANDER)


def in_box(p, box):
    return box[0] <= p[0] < box[2] and box[1] <= p[1] < box[3]


def moved(pts, box, strict=False, max_keys=None, max_ms=None):
    """A flick that starts in the box, with today's edge rules (or the inner box's level rules when strict)."""
    wander = shape(pts)
    if wander is None or not in_box(pts[0], box):
        return False
    first, last = pts[0], pts[-1]
    if not EDGE_END_TOP <= last[1] < EDGE_END_BOTTOM:
        return False
    if strict and (abs(last[1] - first[1]) > INNER_TILT or wander > INNER_WANDER):
        return False
    if not strict and wander > EDGE_WANDER:
        return False
    if max_keys is not None and (first[0] - last[0]) * 10 > max_keys:
        return False
    if max_ms is not None and last[2] - first[2] > max_ms:
        return False
    return True


def key_at(x, y):
    rows = [list("QWERTYUIOP"), list("ASDFGHJKL") + ["Bksp"], ["Alt"] + list("ZXCVBNM") + ["Sym", "Enter"]]
    row = min(3, int(y * 4))
    if row == 3:
        return "bottom"
    return rows[row][min(9, max(0, int(x * 10)))]


# Read every swipe once, with what followed it.
seen = set()
swipes = []
for path in args:
    boot = None
    contacts = {}
    current = None
    events = []
    for line in open(path, encoding='utf-8'):
        try:
            d = json.loads(line)
        except ValueError:
            continue
        k = d.get('k')
        if k == 'header':
            boot = round((d['wall'] - d['t']) / 60000)
            continue
        t = d.get('t')
        if k == 'm':
            pts = [s for s in d.get('s', []) if s and s[0] is not None]
            if d['a'] == 0:
                current = {'down': t, 'pts': list(pts)}
            elif current is not None:
                current['pts'] += pts
                if d['a'] in (1, 3):
                    contacts[current['down']] = current
                    current = None
        events.append((k, t, d, boot))
    for i, (k, t, d, boot) in enumerate(events):
        if k != 'out' or d.get('type') != 'swipe':
            continue
        key = (boot, d['start'])
        c = contacts.get(d['start'])
        if key in seen or c is None or len(c['pts']) < 2:
            continue
        pts = [p for p in c['pts'] if d['start'] <= p[2] <= d['end']] or c['pts']
        seen.add(key)
        recognised, removed = False, False
        for k2, t2, d2, _ in events[i + 1:]:
            if t2 is not None and t2 - d['end'] > WORD_GAP_MS:
                break
            if k2 == 'rec' and not recognised:
                recognised = True
                continue
            if k2 == 'key' and d2.get('d') and d2.get('repeat', 0) == 0 and not d2.get('g'):
                if d2['c'] == BACKSPACE:
                    removed = True
                break
            if k2 == 'out':
                if d2.get('type') == 'swipe':
                    nxt = contacts.get(d2.get('start'))
                    if nxt and len(nxt['pts']) > 1 and today(nxt['pts']):
                        removed = True
                break
        swipes.append({'pts': pts, 'kind': ('removed' if removed else 'word') if recognised else 'flick'})

boxes = {
    'today': None,
    'new over I O M Sym (x .68-.92)': (.68, max(EDGE_START_TOP, EDGE_END_TOP), .68 + W, max(EDGE_START_TOP, EDGE_END_TOP) + H),
    'new, centred on I-O (x .68-.92) same as above': None,
}
NEW = boxes['new over I O M Sym (x .68-.92)']
ZONE_TODAY = (INNER_EDGE, max(EDGE_START_TOP, EDGE_END_TOP), 1.0, min(EDGE_START_BOTTOM, EDGE_END_BOTTOM))
NO_IO = lambda p: not (p[1] < .25 and .7 <= p[0] < .9)

rules = {
    'today': today,
    'A: replace today with the new rectangle (edge rules)': lambda p: moved(p, NEW),
    'B: today OR new rectangle (edge rules)': lambda p: today(p) or moved(p, NEW),
    'C: today OR new rectangle, level (tilt <= .1, wander <= 1.1)': lambda p: today(p) or moved(p, NEW, strict=True),
    'D: C, and not starting on I or O': lambda p: today(p) or (NO_IO(p[0]) and moved(p, NEW, strict=True)),
    'E: D, and at most 6 keys and 400 ms': lambda p: today(p) or (NO_IO(p[0]) and moved(p, NEW, strict=True, max_keys=6, max_ms=400)),
}

kinds = Counter(s['kind'] for s in swipes)
result = {'swipes': len(swipes), 'by_kind': dict(kinds), 'new_rectangle': NEW, 'today_zone': ZONE_TODAY, 'rules': {}}
base = [today(s['pts']) for s in swipes]
for name, rule in rules.items():
    fired = [rule(s['pts']) for s in swipes]
    added = Counter(s['kind'] for s, f, b in zip(swipes, fired, base) if f and not b)
    lost = Counter(s['kind'] for s, f, b in zip(swipes, fired, base) if b and not f)
    total = Counter(s['kind'] for s, f in zip(swipes, fired) if f)
    added_keys = Counter(key_at(*s['pts'][0][:2]) for s, f, b in zip(swipes, fired, base) if f and not b and s['kind'] == 'word')
    result['rules'][name] = {'fires': dict(total), 'added_vs_today': dict(added), 'lost_vs_today': dict(lost),
                             'added_kept_words_by_start_key': dict(added_keys.most_common())}

# Swipes that start in the new rectangle but outside today's zone, and how many of each kind would pass the shape tests.
inside = [s for s in swipes if in_box(s['pts'][0], NEW) and not in_box(s['pts'][0], ZONE_TODAY)]
result['start_in_new_not_today'] = dict(Counter(s['kind'] for s in inside))
result['start_in_new_not_today_by_key'] = {k: dict(Counter(s['kind'] for s in inside if key_at(*s['pts'][0][:2]) == k))
                                           for k in sorted({key_at(*s['pts'][0][:2]) for s in inside})}
print(json.dumps(result, indent=1))
if out_path:
    json.dump(result, open(out_path, 'w'), indent=1)
