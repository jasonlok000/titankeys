#!/usr/bin/env python3
"""#30, the owner's steer: in the big logs, which touches were mistakes, of which kind, and what separates them?
Classes (every touchpad contact):
  real swipe      a swipe whose word was kept as shown
  swipe mistype   a swipe the owner meant, whose word they then changed (a pick, or another word kept in its place)
  swipe mistap    a swipe deleted within 5 s with nothing kept in its place (no intent)
  tap mistap      a touchpad tap whose letter was backspaced within 3 s before any other input
  real tap        any other touchpad tap
  typing contact  a contact that overlaps a physical key press (the finger on the pad while pushing a key)
  other           the rest (contacts that typed nothing and touched no key)
Features: duration, travel (keys), speed, largest step, landing step over usual, the largest jump in step between two
points, the step range, start row, time since the last physical key and since the last swipe. Sizes only exist from 2.0.0.
Counts and features only: no text is printed. Usage: mistakes.py raw-log.jsonl out.json"""
import bisect
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
keys = sorted((e['t'], e['d']) for e in events if e.get('k') == 'key' and not e.get('g'))
key_downs = [t for t, d in keys if d]
backspace_t = sorted(e['t'] for e in events if e.get('k') == 'text' and e.get('cause') == 'backspace')
inputs_t = sorted(e['t'] for e in events if e.get('k') in ('out', 'key') and not (e.get('k') == 'key' and not e.get('d')))
tap_outs = sorted((e['end'], e['t']) for e in events if e.get('k') == 'out' and e.get('type') == 'tap')
swipe_ends = sorted(a.out['end'] for a in rep.attempts)

# ---- swipe classes from the attempts ---------------------------------------------------------------------------------------
klass = {}
for a in rep.attempts:
    if a.rec is None or a.slot is None or not a.rec.get('words'):
        continue
    slot = rep.slots[a.slot]
    first = R.norm(a.rec['words'][0])
    final = R.norm(slot.final) if slot.final else None
    picked = any(ev[0] == 'choose' for ev in slot.events)
    if final is None:
        quick = a.deleted_at is not None and a.deleted_at - a.out['end'] <= 5000
        klass[a.id] = 'swipe mistap' if quick else 'swipe removed later'
    elif final == first and not picked:
        klass[a.id] = 'real swipe'
    else:
        # A mistype only if the word was changed within 5 s (a pick, or a backspace into it); later edits are rewording.
        times = [ev[1] for ev in slot.events if ev[0] == 'choose' or str(ev[0]).startswith('backspace')]
        quick = any(0 <= t - a.out['end'] <= 5000 for t in times if isinstance(t, (int, float)))
        klass[a.id] = 'swipe mistype' if quick else 'swipe changed later'


def tap_class(u):
    """A tap is a mistap if a backspace came within 3 s and before any other input."""
    i = bisect.bisect_left([e for e, _ in tap_outs], u - 30)
    for end, t in tap_outs[i:i + 2]:
        if abs(end - u) <= 30:
            b = bisect.bisect_left(backspace_t, t)
            nxt = bisect.bisect_right(inputs_t, t)
            back = backspace_t[b] if b < len(backspace_t) else None
            other = inputs_t[nxt] if nxt < len(inputs_t) else None
            if back is not None and back - t <= 3000 and (other is None or back <= other):
                return 'tap mistap'
            return 'real tap'
    return None


# ---- how each mistap and mistype was recovered (actions on its word in the 10 s after the swipe) -----------------------
recovery = defaultdict(Counter)
for a in rep.attempts:
    kind = klass.get(a.id)
    if kind not in ('swipe mistap', 'swipe mistype'):
        continue
    slot = rep.slots[a.slot]
    ev = [e for e in slot.events if isinstance(e[1], (int, float)) and 0 <= e[1] - a.out['end'] <= 10000]
    words = sum(e[0] == 'backspace-word' for e in ev)
    letters = sum(e[0] == 'backspace-letter' for e in ev)
    picks = sum(e[0] == 'choose' for e in ev)
    typed = sum(e[0] == 'typed' for e in ev)
    later = [x for x in slot.attempts if x != a.id and rep.attempts[x].out['start'] > a.out['end'] and rep.attempts[x].out['start'] - a.out['end'] <= 10000]
    if kind == 'swipe mistap':
        if words == 1 and letters == 0:
            how = '1 word backspace'
        elif words == 0 and letters > 0:
            how = f'{letters} letter backspace' + ('s' if letters > 1 else '')
        elif words + letters:
            how = f'{words} word + {letters} letter backspaces'
        else:
            how = 'other (removed some other way)'
    else:
        if picks and not (words or letters):
            how = 'a strip pick'
        elif (words or letters) and later:
            how = ('1 word backspace' if words == 1 and letters == 0 else f'{words + letters} backspaces') + ', then swiped again'
        elif (words or letters) and typed:
            how = ('1 word backspace' if words == 1 and letters == 0 else f'{words + letters} backspaces') + ', then typed'
        elif words or letters:
            how = f'{words} word + {letters} letter backspaces, then other'
        elif later:
            how = 'swiped again over it'
        else:
            how = 'other'
    recovery[kind][how] += 1
    actions = words + letters + picks + (1 if later else 0) + (1 if typed and kind == 'swipe mistype' else 0)
    recovery[kind + ' actions'][min(actions, 5)] += 1

rows = []
for c in rep.contacts:
    if not c['pts'] or c['u'] is None:
        continue
    pts = [p for p in c['pts'] if p[0] is not None]
    if not pts:
        continue
    d, u = c['d'], c['u']
    if c.get('out') is not None and c['out'] in klass:
        kind = klass[c['out']]
    else:
        kind = tap_class(u) or None
    k = bisect.bisect_left(key_downs, d - 30)
    overlaps_key = k < len(key_downs) and key_downs[k] <= u + 30
    if overlaps_key and kind in (None, 'real tap', 'tap mistap'):
        kind = 'typing contact'
    kind = kind or 'other'
    travel = sum(math.hypot((pts[i][0] - pts[i - 1][0]) * 10, (pts[i][1] - pts[i - 1][1]) * 4) for i in range(1, len(pts)))
    dur = max(1, u - d)
    kb = bisect.bisect_left(key_downs, d) - 1
    sb = bisect.bisect_left(swipe_ends, d) - 1
    m = {'kind': kind, 'day': datetime.datetime.fromtimestamp(rep.clock.wall(d) / 1000).strftime('%m-%d'),
         'duration': dur, 'travel': travel, 'speed': travel / dur * 1000, 'start row': pts[0][1] * 4,
         'since key': d - key_downs[kb] if kb >= 0 else 10 ** 6, 'since swipe': d - swipe_ends[sb] if sb >= 0 else 10 ** 6,
         'overlaps key': overlaps_key}
    if c.get('sz'):
        steps = [s / 1.125 for s in c['sz']]
        m['largest step'] = max(steps)
        m['step range'] = max(steps) - min(steps)
        m['largest step jump'] = max([abs(steps[i] - steps[i - 1]) for i in range(1, len(steps))] or [0])
        m['landing over usual'] = max(steps[:3]) - usual(pts[0][0], pts[0][1])
    rows.append(m)


def auc(a, b):
    """Chance that a value from a is larger than one from b (0.5 = no separation)."""
    if not a or not b:
        return None
    b = sorted(b)
    wins = sum(bisect.bisect_left(b, x) + (bisect.bisect_right(b, x) - bisect.bisect_left(b, x)) / 2 for x in a)
    return round(wins / (len(a) * len(b)), 3)


out = {'per day': defaultdict(dict), 'features': {}, 'separation from real input (AUC)': {}}
for (day, kind), n in sorted(Counter((m['day'], m['kind']) for m in rows).items()):
    out['per day'][day][kind] = n
for day, kinds in out['per day'].items():
    swipes = sum(v for k, v in kinds.items() if 'swipe' in k)
    kinds['swipe mistaps per 100 swipes'] = round(100 * kinds.get('swipe mistap', 0) / max(1, swipes), 1)
    kinds['swipe mistypes per 100 swipes'] = round(100 * kinds.get('swipe mistype', 0) / max(1, swipes), 1)
    kinds['swipes'] = swipes
FEATURES = ['duration', 'travel', 'speed', 'start row', 'since key', 'since swipe', 'largest step', 'step range', 'largest step jump', 'landing over usual']
med = lambda v: sorted(v)[len(v) // 2] if v else None
for kind in sorted({m['kind'] for m in rows}):
    part = [m for m in rows if m['kind'] == kind]
    out['features'][kind] = {'n': len(part), 'with sizes': sum('largest step' in m for m in part),
                             **{f: (round(med([m[f] for m in part if f in m]), 2) if any(f in m for m in part) else None) for f in FEATURES}}
short_real = [m for m in rows if m['kind'] == 'real swipe' and m['travel'] < 3]
pairs = {'swipe mistap vs real swipe': ('swipe mistap', 'real swipe'), 'swipe mistype vs real swipe': ('swipe mistype', 'real swipe'),
         'tap mistap vs real tap': ('tap mistap', 'real tap'), 'typing contact vs real tap': ('typing contact', 'real tap')}
for label, (a, b) in pairs.items():
    A = [m for m in rows if m['kind'] == a]
    B = [m for m in rows if m['kind'] == b]
    out['separation from real input (AUC)'][label] = {f: auc([m[f] for m in A if f in m], [m[f] for m in B if f in m]) for f in FEATURES}
A = [m for m in rows if m['kind'] == 'typing contact']
out['separation from real input (AUC)']['typing contact vs kept short swipe (under 3 keys)'] = {
    f: auc([m[f] for m in A if f in m], [m[f] for m in short_real if f in m]) for f in FEATURES}
out['kept short swipes (under 3 keys)'] = len(short_real)
out['share of each kind that overlaps a physical key press'] = {k: round(sum(m['overlaps key'] for m in rows if m['kind'] == k) / max(1, sum(m['kind'] == k for m in rows)), 3)
                                                                for k in sorted({m['kind'] for m in rows})}
out['swipe mistaps under 1.5 keys of travel'] = sum(m['kind'] == 'swipe mistap' and m['travel'] < 1.5 for m in rows)
out['real swipes under 1.5 keys of travel'] = sum(m['kind'] == 'real swipe' and m['travel'] < 1.5 for m in rows)
print('overlap with key presses', out['share of each kind that overlaps a physical key press'])
print('under 1.5 keys: mistaps', out['swipe mistaps under 1.5 keys of travel'], 'real', out['real swipes under 1.5 keys of travel'])
out['recovery'] = {k: dict(v.most_common()) for k, v in recovery.items()}
json.dump(out, open(out_path, 'w'), indent=1)
for k, v in out['recovery'].items():
    print('RECOVERY', k, v)
print(json.dumps({'per day': out['per day']}, indent=1))
for kind, f in out['features'].items():
    print(f'{kind:<20}', f)
for label, v in out['separation from real input (AUC)'].items():
    print(label, v)
