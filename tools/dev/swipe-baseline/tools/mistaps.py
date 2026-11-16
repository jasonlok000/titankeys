#!/usr/bin/env python3
"""#30 redone with the owner's words (Nov 10):
  MISTAP    the owner did not mean to type, nothing was typed, but the touchpad vibrated.
  MISTYPE   the owner did not mean to type, but a word was typed anyway (a swipe deleted within 5 s, nothing kept).
  wrong word  the owner meant to swipe and got the wrong word (engine accuracy, not this report).
Which contacts vibrated comes from HapticReplay.kt (the keyboard's own Contact with the swipe tick rule), joined here with
the log's words. Tap keys were off, so a touchpad tap types nothing and its own click never plays; the touchpad's only
vibration that comes without input is the swipe tick.
Counts and features only, no text. Usage: mistaps.py raw-log.jsonl contacts.tsv recording.jsonl rest-contacts.tsv out.json"""
import bisect
import collections
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R
from rest_rules_table import usual

log_path, replay_path, rest_path, rest_replay_path, out_path = sys.argv[1:6]
events = R.load(log_path)
rep = R.Replayer(events).run()
key_downs = sorted(e['t'] for e in events if e.get('k') == 'key' and e.get('d') and not e.get('g'))
swipe_ends = sorted(a.out['end'] for a in rep.attempts)


def read_replay(path):
    lines = open(path).read().split('\n')
    head = lines[0].split('\t')
    return [dict(zip(head, l.split('\t'))) for l in lines[1:] if l]


replay = {int(r['down']): r for r in read_replay(replay_path)}

# The owner's classes for each swipe, from what happened to its word.
klass = {}
for a in rep.attempts:
    if a.rec is None:
        # No recognition at all: a swipe in the Sym panel or with Alt on, where the touchpad does not tick, or a lost line.
        klass[a.id] = 'swipe with no recognition'
        continue
    if a.slot is None or not a.rec.get('words'):
        klass[a.id] = 'swipe that typed nothing'
        continue
    slot = rep.slots[a.slot]
    first = R.norm(a.rec['words'][0])
    final = R.norm(slot.final) if slot.final else None
    picked = any(ev[0] == 'choose' for ev in slot.events)
    if final is None:
        quick = a.deleted_at is not None and a.deleted_at - a.out['end'] <= 5000
        klass[a.id] = 'MISTYPE' if quick else 'swipe removed later'
    elif final == first and not picked:
        klass[a.id] = 'real swipe'
    else:
        times = [ev[1] for ev in slot.events if ev[0] == 'choose' or str(ev[0]).startswith('backspace')]
        quick = any(0 <= t - a.out['end'] <= 5000 for t in times if isinstance(t, (int, float)))
        klass[a.id] = 'wrong word' if quick else 'swipe changed later'


def size_features(pts, timed, upto=None):
    """Largest step and landing step over usual (first 80 ms), from sizes up to time [upto] when given."""
    if not timed:
        return None
    t0 = pts[0][2]
    use = [(t, s) for t, s in timed if upto is None or t <= upto] or timed[:1]
    head = [s for t, s in use if t - t0 <= 80] or [use[0][1]]
    return {'largest step': max(s for _, s in use), 'landing over usual': max(head) - usual(pts[0][0], pts[0][1])}


ROWS = ['QWERTYUIOP', 'ASDFGHJKL_', '_ZXCVBNM__']


def letter_at(x, y):
    """The letter key under a touchpad point, as Layout.keyAt has it for the three letter rows, else '_'."""
    row = int(y * 4)
    if row > 2 or row < 0:
        return '_'
    return ROWS[row][min(9, max(0, int(x * 10)))]


def size_rule(f):
    return f is not None and (f['largest step'] >= 6 or f['landing over usual'] >= 2)


rows = []
unmatched = 0
for c in rep.contacts:
    pts = [p for p in c['pts'] if p[0] is not None]
    if not pts or c['u'] is None:
        continue
    r = replay.get(c['d'])
    if r is None:
        unmatched += 1
        continue
    ticks = int(r['ticks'])
    first_tick = int(r['firstTick'])
    outcome = r['outcome']
    if c.get('out') is not None:
        kind = klass.get(c['out'], 'swipe that typed nothing')
    elif outcome == 'delete':
        kind = 'delete flick'
    elif outcome == 'swipe':
        kind = 'swipe in replay, none in log'
    else:
        kind = 'typed nothing (' + ('tap' if outcome.startswith('tap') else outcome) + ')'
    timed = [(p[2], round(s / 1.125)) for p, s in zip(c['raw'], c.get('sz') or []) if s is not None]
    travel = sum(math.hypot((pts[i][0] - pts[i - 1][0]) * 10, (pts[i][1] - pts[i - 1][1]) * 4) for i in range(1, len(pts)))
    kb = bisect.bisect_left(key_downs, c['d']) - 1
    sb = bisect.bisect_left(swipe_ends, c['d']) - 1
    first_far = {}
    for limit in (.6, 1.0):
        far = next((p[2] for p in pts if math.hypot((p[0] - pts[0][0]) * 10, (p[1] - pts[0][1]) * 4) >= limit
                    and 'A' <= letter_at(p[0], p[1]) <= 'Z'), None)
        first_far[limit] = far
    ko = bisect.bisect_left(key_downs, c['d'])
    m = {'kind': kind, 'key during contact': ko < len(key_downs) and key_downs[ko] <= c['u'],
         'tick wait .6': None if first_far[.6] is None else first_far[.6] - c['d'],
         'tick wait 1.0': None if first_far[1.0] is None else first_far[1.0] - c['d'], 'vibrated': ticks > 0, 'ticks': ticks, 'duration': max(1, c['u'] - c['d']), 'travel': travel,
         'start row': pts[0][1] * 4, 'since key': c['d'] - key_downs[kb] if kb >= 0 else 10 ** 6,
         'since swipe': c['d'] - swipe_ends[sb] if sb >= 0 else 10 ** 6,
         'tick before it was a swipe': r['tickBeforeSwipe'] == 'true',
         'time to first tick': first_tick - c['d'] if ticks else None}
    full = size_features(pts, timed)
    early = size_features(pts, timed, first_tick) if ticks else None
    if full:
        m.update(full)
        m['size rule'] = size_rule(full)
        m['size rule by first tick'] = size_rule(early) if ticks else None
    rows.append(m)

TYPED_NOTHING = lambda k: k.startswith('typed nothing') or k == 'swipe that typed nothing'
INPUT = ('real swipe', 'wrong word', 'swipe changed later', 'delete flick', 'MISTYPE', 'swipe removed later')
med = lambda v: round(sorted(v)[len(v) // 2], 2) if v else None


def auc(a, b):
    if not a or not b:
        return None
    b = sorted(b)
    wins = sum(bisect.bisect_left(b, x) + (bisect.bisect_right(b, x) - bisect.bisect_left(b, x)) / 2 for x in a)
    return round(wins / (len(a) * len(b)), 3)


out = {'contacts': len(rows), 'not found in the replay': unmatched,
       'by kind': {k: {'n': n, 'vibrated': sum(m['vibrated'] for m in rows if m['kind'] == k)}
                   for k, n in collections.Counter(m['kind'] for m in rows).most_common()}}
mistaps = [m for m in rows if m['vibrated'] and TYPED_NOTHING(m['kind'])]
vib_input = [m for m in rows if m['vibrated'] and m['kind'] in INPUT]
real_vib = [m for m in rows if m['vibrated'] and m['kind'] in ('real swipe', 'wrong word', 'swipe changed later', 'delete flick')]
swipes = sum(m['kind'] in INPUT and m['kind'] != 'delete flick' for m in rows)
out['MISTAPS (vibrated, typed nothing)'] = len(mistaps)
out['MISTAPS per 100 swipes'] = round(100 * len(mistaps) / max(1, swipes), 1)
out['MISTAPS by outcome'] = dict(collections.Counter(m['kind'] for m in mistaps).most_common())
out['MISTAPS with one tick'] = sum(m['ticks'] == 1 for m in mistaps)
out['MISTAPS whose first tick came before 0.6 keys (before it was a swipe)'] = sum(m['tick before it was a swipe'] for m in mistaps)
FEATURES = ['duration', 'travel', 'ticks', 'time to first tick', 'start row', 'since key', 'since swipe', 'largest step', 'landing over usual']
groups = {'MISTAP': mistaps, 'vibrated and typed (meant)': real_vib,
          'MISTYPE': [m for m in rows if m['kind'] == 'MISTYPE'], 'real swipe': [m for m in rows if m['kind'] == 'real swipe']}
out['medians'] = {g: {'n': len(v), 'with sizes': sum('largest step' in m for m in v),
                      **{f: med([m[f] for m in v if m.get(f) is not None]) for f in FEATURES}} for g, v in groups.items()}
out['separation (AUC), MISTAP against vibrated and typed'] = {
    f: auc([m[f] for m in mistaps if m.get(f) is not None], [m[f] for m in real_vib if m.get(f) is not None]) for f in FEATURES}
out['separation (AUC), MISTYPE against real swipe'] = {
    f: auc([m[f] for m in groups['MISTYPE'] if m.get(f) is not None], [m[f] for m in groups['real swipe'] if m.get(f) is not None]) for f in FEATURES}

out['ignored MISTAPS with a key press during the contact'] = sum(m['key during contact'] for m in mistaps if 'ignored' in m['kind'])
out['MISTAPS with a key press during the contact'] = sum(m['key during contact'] for m in mistaps)
# A tick rule that changes no input: the first tick waits until the finger is .6 (or 1) keys from where it landed.
for limit in ('.6', '1.0'):
    key = 'tick wait ' + limit
    waits = [m[key] - m['time to first tick'] for m in real_vib if m[key] is not None and m['time to first tick'] is not None]
    out[f'first tick only from {limit} keys'] = {
        'MISTAPS silenced': f"{sum(m[key] is None for m in mistaps)} of {len(mistaps)}",
        'MISTAPS silenced, by outcome': {k: f"{sum(m[key] is None for m in mistaps if m['kind'] == k)} of {sum(m['kind'] == k for m in mistaps)}"
                                         for k in sorted({m['kind'] for m in mistaps})},
        'uncertain (no recognition, maybe the panel) silenced': f"{sum(m[key] is None for m in rows if m['vibrated'] and m['kind'] == 'swipe with no recognition')} of {sum(m['vibrated'] and m['kind'] == 'swipe with no recognition' for m in rows)}",
        'meant input silenced': f"{sum(m[key] is None for m in real_vib)} of {len(real_vib)}",
        'meant input silenced, by kind': dict(collections.Counter(m['kind'] for m in real_vib if m[key] is None)),
        'meant input: first tick later by (median ms)': med(waits),
        'MISTYPES silenced': f"{sum(m[key] is None for m in groups['MISTYPE'] if m['vibrated'])} of {sum(m['vibrated'] for m in groups['MISTYPE'])}",
    }

# The size rule, two ways. Silencing: the rule, judged on the sizes up to the first tick, stops the contact's ticks.
# Dropping: the rule, judged on the whole contact, drops its input.
sized = lambda v: [m for m in v if 'size rule' in m]
out['size rule (step 6+ or landing 2+ over usual)'] = {
    'silence ticks: MISTAPS silenced': f"{sum(bool(m['size rule by first tick']) for m in sized(mistaps))} of {len(sized(mistaps))}",
    'silence ticks: meant input silenced': f"{sum(bool(m['size rule by first tick']) for m in sized(real_vib))} of {len(sized(real_vib))}",
    'drop input: MISTYPES caught': f"{sum(m['size rule'] for m in sized(groups['MISTYPE']))} of {len(sized(groups['MISTYPE']))}",
    'drop input: meant input lost': f"{sum(m['size rule'] for m in sized(real_vib))} of {len(sized(real_vib))}",
}
# The pause signal: mistypes come after a longer pause since the last swipe. Each threshold: share caught, share of real lost.
pause = {}
for limit in (1000, 1500, 2000, 3000):
    for short in (None, 3, 2):
        test = lambda m: m['since swipe'] >= limit and (short is None or m['travel'] < short)
        label = f"pause {limit} ms" + (f" and travel under {short} keys" if short else '')
        pause[label] = {'MISTYPES': f"{sum(test(m) for m in groups['MISTYPE'])} of {len(groups['MISTYPE'])}",
                        'real swipes': f"{sum(test(m) for m in groups['real swipe'])} of {len(groups['real swipe'])}"}
out['pause signal on MISTYPES'] = pause

# ---- the resting-touch recording ------------------------------------------------------------------------------------------
rest_rows = [json.loads(l) for l in open(rest_path, encoding='utf-8')][1:]
first = rest_rows[0]['start']
rest_rows = [r for r in rest_rows if (r['start'] - first) / 1000 < 400 and any(p[0] is not None for p in r['points'])]
rest_replay = read_replay(rest_replay_path)
rest = collections.Counter()
rest_rule = collections.Counter()
for r, played in zip(rest_rows, rest_replay):
    reading = r['reading'] or ''
    read = 'swipe' if 'swipe' in reading else 'tap' if 'tap' in reading else 'nothing'
    vib = int(played['ticks']) > 0
    rest[f"read as {read}, {'vibrated' if vib else 'quiet'}"] += 1
    if vib:
        pts = [(p[0], p[1] * .75, p[4]) for p in r['points'] if p[0] is not None]
        for limit in (.6, 1.0):
            reaches = any(math.hypot((p[0] - pts[0][0]) * 10, (p[1] - pts[0][1]) * 4) >= limit and 'A' <= letter_at(p[0], p[1]) <= 'Z' for p in pts)
            if not reaches:
                rest_rule[f'vibrated as {read}, silenced by first tick only from {limit} keys'] += 1
        early = size_features(pts, [(p[4], p[2]) for p in r['points'] if p[0] is not None], int(played['firstTick']))
        rest_rule['vibrated, silenced by the size rule' if size_rule(early) else 'vibrated, not silenced'] += 1
out['resting recording'] = {'touches': len(rest_rows), 'replayed': len(rest_replay), **dict(rest), **dict(rest_rule),
                            'replay reading agrees': sum((('swipe' in (r['reading'] or '')) == (p['outcome'] in ('swipe', 'delete')))
                                                         for r, p in zip(rest_rows, rest_replay))}
json.dump(out, open(out_path, 'w'), indent=1)
print(json.dumps(out, indent=1))
