#!/usr/bin/env python3
"""Mistouches, accidental swipes and delete flicks, read from the raw contacts.
Usage: touches.py private-dir raw-log out-dir"""
import json
import os
import sys
from bisect import bisect_left, bisect_right
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

P, log_path, OUT = sys.argv[1:4]
events = R.load(log_path)
rows = {r['id']: r for r in (json.loads(l) for l in open(os.path.join(P, 'attempts.jsonl'), encoding='utf-8'))}
M = {}


def L(w):
    return R.norm(w).replace("'", '') if w else w


# ---- rebuild every contact with what became of it --------------------------------------------------------------------
contacts, current = [], None
outs = []
keys = []
texts = []
for e in events:
    if e['k'] == 'm':
        if e['a'] == 0:
            current = {'pts': list(e['s']), 'd': e['t'], 'u': None, 'cancel': False}
        elif current is not None and e['a'] == 2:
            current['pts'] += e['s']
        elif current is not None and e['a'] in (1, 3):
            current['pts'] += e['s']
            current['u'] = e['t']
            current['cancel'] = e['a'] == 3
            contacts.append(current)
            current = None
    elif e['k'] == 'out':
        outs.append(e)
    elif e['k'] == 'key':
        keys.append(e)
    elif e['k'] == 'text':
        texts.append(e)

downs = sorted(e['t'] for e in keys if e['d'] and not e['g'])
blocks = []
open_block = None
steps = []
open_step = None
for e in events:
    if e['k'] == 'mark':
        if e['kind'] == 'style-start':
            open_block = (e['label'], e['t'])
        elif e['kind'] == 'style-end' and open_block:
            blocks.append((open_block[0], open_block[1], e['t']))
            open_block = None
        elif e['kind'] == 'start':
            open_step = (e['label'], e['t'])
        elif e['kind'] in ('done', 'redo', 'skip') and open_step:
            if e['kind'] == 'done':
                steps.append((open_step[0], open_step[1], e['t']))
            open_step = None


def where(t):
    for name, a, b in blocks:
        if a <= t <= b:
            return 'style:' + name
    for name, a, b in steps:
        if a <= t <= b:
            return 'step:' + name
    return 'everyday'


def key_in(a, b):
    i = bisect_left(downs, a)
    return i < len(downs) and downs[i] <= b


# Mark what each contact became.
for c in contacts:
    c['pts'] = [p for p in c['pts'] if p[0] is not None and p[1] is not None]
    c['became'] = None
for o in outs:
    if o['type'] == 'tap':
        for c in reversed(contacts[-40:] if False else contacts):
            if c['pts'] and abs(c['pts'][-1][2] - o['end']) <= 1 and c['became'] is None:
                c['became'] = 'tap:' + str(o.get('key'))
                break
    else:
        for c in contacts:
            if c['pts'] and c['pts'][0][2] >= o['start'] - 2 and c['pts'][-1][2] <= o['end'] + 2 and c['became'] is None:
                c['became'] = 'delete' if o.get('deletes') else 'swipe'


def features(c):
    pts = c['pts']
    if not pts:
        return None
    first = pts[0]
    ku = [(p[0] * 10, p[1] * 4, p[2]) for p in pts]
    travel = max(R.dist(ku[0], p) for p in ku)
    dur = (pts[-1][2] - pts[0][2])
    return {'travel': travel, 'dur': dur, 'n': len(pts), 'path': R.path_length(ku), 'x': first[0] * 10, 'y': first[1] * 4, 'row': int(first[1] * 4)}


for c in contacts:
    c['f'] = features(c)
    c['where'] = where(c['d'])

valid = [c for c in contacts if c['f']]
print('=== Every touch, by where it happened and what it became ===')
M['touches'] = {}
for place in sorted({c['where'].split(':')[0] for c in valid}) + ['style:Random', 'step:01-normal-typing']:
    items = [c for c in valid if c['where'] == place or c['where'].startswith(place + ':') or (place == 'everyday' and c['where'] == 'everyday')]
    n = len(items)
    b = Counter()
    for c in items:
        if c['became']:
            b[c['became'].split(':')[0]] += 1
        elif c['f']['travel'] < .35:
            b['short: nothing'] += 1
        elif c['f']['travel'] < .6:
            b['small move: nothing'] += 1
        elif c['f']['travel'] < 1.5:
            b['moved 0.6-1.5 keys: nothing'] += 1
        else:
            b['moved 1.5+ keys: nothing'] += 1
    M['touches'][place] = {'n': n, **dict(b)}
    print(f'  {place:<26} n={n:<5}', dict(b))

# ---- contacts around key presses --------------------------------------------------------------------------------------
print('\n=== While typing on the keys ===')
M['typing'] = {}
for place in ('style:Random', 'step:01-normal-typing', 'everyday'):
    items = [c for c in valid if c['where'] == place or (place == 'everyday' and c['where'] == 'everyday')]
    keyed = sum(1 for t in downs if where(t) == place)
    swipe_near = [c for c in items if c['became'] == 'swipe']
    touching_key = [c for c in items if key_in(c['d'], c['u'] + 40)]
    after_key = [c for c in items if key_in(c['d'] - 600, c['d'])]
    M['typing'][place] = {'key_presses': keyed, 'touches': len(items), 'touches_overlapping_a_key': len(touching_key), 'touches_within_600ms_after_a_key': len(after_key),
                          'swipes': len(swipe_near), 'touches_per_100_keys': round(100 * len(items) / keyed, 1) if keyed else None}
    print(f'  {place}: {M["typing"][place]}')

# ---- accidental swipes ---------------------------------------------------------------------------------------------
print('\n=== Accidental swipes: words that a swipe produced ===')
attempt_rows = sorted(rows.values(), key=lambda r: r['start'])


def near_key_before(r, window):
    return key_in(r['start'] - window, r['start'])


def stroke_len(r):
    pts = R.key_units(r['chain_points'])
    return R.path_length(pts) if len(pts) > 1 else 0


deleted_no_retry = []
for r in attempt_rows:
    if r['superseded_by'] is not None:
        continue
    r['slen'] = stroke_len(r)
    r['dur'] = r['chain_points'][-1][2] - r['chain_points'][0][2] if len(r['chain_points']) > 1 else 0
    r['after_key'] = near_key_before(r, 700)
    r['removed_no_trace'] = r['hand'] is None and 'backspace-word' in r['slot_events'] or (r['hand'] is None and 'backspace-letter' in r['slot_events'])
    if r['removed_no_trace']:
        deleted_no_retry.append(r)

print(f'Effective swipes in the log: {sum(1 for r in attempt_rows if r["superseded_by"] is None)}')
print(f'Swipes that produced a word later deleted with nothing left in that place: {len(deleted_no_retry)}')
M['accidental'] = {'removed_no_trace': len(deleted_no_retry),
                   'removed_after_key_700ms': sum(1 for r in deleted_no_retry if r['after_key']),
                   'removed_short_under_2_keys': sum(1 for r in deleted_no_retry if r['slen'] < 2)}

# A stroke made close after pressing a key, in every context
print('Swipes by whether a key was pressed in the 700 ms before them:')
M['swipe_after_key'] = {}
for label, selector in (('key pressed within 700 ms before', True), ('no key within 700 ms before', False)):
    items = [r for r in attempt_rows if r['superseded_by'] is None and r.get('after_key') == selector]
    removed = sum(1 for r in items if r.get('removed_no_trace'))
    short = sum(1 for r in items if r['slen'] < 2)
    durs = sorted(r['dur'] for r in items)
    M['swipe_after_key'][label] = {'n': len(items), 'removed_with_no_replacement': removed, 'removed_pct': round(100 * removed / len(items), 1) if items else None, 'under_2_keys': short, 'median_dur': durs[len(durs) // 2] if durs else None}
    print(f'  {label}: {M["swipe_after_key"][label]}')

short_strokes = [r for r in attempt_rows if r['superseded_by'] is None and r['slen'] < 2]
print(f'\nStrokes that travelled under 2 keys: {len(short_strokes)} of {sum(1 for r in attempt_rows if r["superseded_by"] is None)}')
one_letter_words = Counter(L(r['words'][0]) for r in short_strokes)
print('  top words produced by them (for prompted, safe text):', [w for w in one_letter_words.most_common(20)])
M['short_strokes'] = {'n': len(short_strokes), 'removed_no_trace': sum(1 for r in short_strokes if r.get('removed_no_trace'))}

# Which swipes follow a tap or brush within a moment (a second contact after the keyboard already typed a word).
# ---- delete flicks --------------------------------------------------------------------------------------------------
print('\n=== Delete flicks ===')
delete_contacts = [c for c in contacts if c['became'] == 'delete']
M['deletes'] = {'n': len(delete_contacts)}
print('count', len(delete_contacts))
dt = sorted(c['f']['dur'] for c in delete_contacts if c['f'])
tr = sorted(c['f']['travel'] for c in delete_contacts if c['f'])
print('  duration ms p10/p50/p90', R.percentile(dt, .1), R.percentile(dt, .5), R.percentile(dt, .9), ' travel keys', round(R.percentile(tr, .1), 2), round(R.percentile(tr, .5), 2), round(R.percentile(tr, .9), 2))
M['deletes']['dur'] = [R.percentile(dt, .1), R.percentile(dt, .5), R.percentile(dt, .9)]
M['deletes']['travel'] = [round(R.percentile(tr, .1), 2), round(R.percentile(tr, .5), 2), round(R.percentile(tr, .9), 2)]

# False deletes: the same word comes back straight after a delete flick in the same place.
false_deletes = 0
reinstated = []
by_slot = defaultdict(list)
for r in attempt_rows:
    if r['superseded_by'] is None:
        by_slot[r['slot']].append(r)
for sid, rs in by_slot.items():
    rs.sort(key=lambda x: x['start'])
    for a, b in zip(rs, rs[1:]):
        if L(a['words'][0]) == L(b['words'][0]) and 'backspace-word' in a['slot_events']:
            false_deletes += 1
            reinstated.append(L(a['words'][0]))
M['deletes']['same_word_reswiped_after_delete'] = false_deletes
print('  deleted, then the same word swiped again in that place:', false_deletes)

# A delete that removes a word the owner meant is a false delete. The hand-corrected final word says what was meant.
right_deleted, wrong_deleted, unknown = 0, 0, 0
right_deleted_strokes = []
for sid, rs in by_slot.items():
    rs.sort(key=lambda x: x['start'])
    label = rs[0]['expected'] or rs[0]['hand']
    for a in rs:
        if 'backspace-word' not in a['slot_events'] or a['deleted_at'] is None if 'deleted_at' in a else False:
            pass
for sid, rs in by_slot.items():
    rs.sort(key=lambda x: x['start'])
    label = rs[0]['expected'] or rs[0]['hand']
    if not label:
        continue
    label = L(label)
    if len(rs) > 1 or 'backspace-word' in rs[0]['slot_events']:
        for a in rs[:-1] if len(rs) > 1 else rs:
            if 'backspace-word' in a['slot_events']:
                if L(a['words'][0]) == label:
                    right_deleted += 1
                    right_deleted_strokes.append(a)
                else:
                    wrong_deleted += 1
                break
M['deletes']['removed_a_right_word'] = right_deleted
M['deletes']['removed_a_wrong_word'] = wrong_deleted
print(f'  of labelled slots: removed a word that was wrong {wrong_deleted}, removed a word that was right {right_deleted}')

# ---- touches that moved a long way and did nothing -------------------------------------------------------------------
print('\n=== Touches that moved 1.5+ keys and did nothing (everyday use) ===')
dead = [c for c in valid if not c['became'] and c['f']['travel'] >= 1.5]
reason = Counter()
for c in dead:
    f = c['f']
    if key_in(c['d'], c['u'] + 40):
        reason['a key was pressed during it'] += 1
    elif key_in(c['d'] - 600, c['d']):
        reason['started just after a key press'] += 1
    elif f['y'] >= 3:
        reason['started on the space row (cursor, or not a letter)'] += 1
    elif c['where'] != 'everyday':
        reason['test step'] += 1
    elif f['dur'] >= 1500:
        reason['long slow drag, 1.5 s or more'] += 1
    else:
        reason['other: no outcome recorded'] += 1
M['dead_moves'] = dict(reason)
print(' ', dict(reason))
durations = sorted(c['f']['dur'] for c in dead)
print('  duration ms p10/p50/p90:', R.percentile(durations, .1), R.percentile(durations, .5), R.percentile(durations, .9))

# ---- second finger and swap reports ----------------------------------------------------------------------------------
print('\n=== Two fingers or a touch handing over ===')
swaps = 0
for a, b in zip(contacts, contacts[1:]):
    if a['u'] is not None and b['d'] - a['u'] <= 12 and a['pts'] and b['pts']:
        far = R.dist((a['pts'][-1][0] * 10, a['pts'][-1][1] * 4), (b['pts'][0][0] * 10, b['pts'][0][1] * 4))
        if far >= 1.2:
            swaps += 1
M['finger_swaps'] = swaps
pointer = Counter(e['a'] for e in events if e['k'] == 'm')
print('  contact ends then another starts within 12 ms, 1.2+ keys away:', swaps, ' motion actions:', dict(pointer))
M['motion_actions'] = {str(k): v for k, v in pointer.items()}

# ---- delete flicks that happened right after a word was committed: possible misreads ---------------------------------
delete_outs = [o for o in outs if o['type'] == 'swipe' and o.get('deletes')]
rejected = []
print('\n=== Typing errors on the physical keys ===')
commits = [t for t in texts if t['cause'] == 'commit' and t.get('via') == 'key']
corrections = [t for t in texts if t['cause'] == 'backspace' and t.get('via') == 'letter']
commit_times = sorted(t['t'] for t in commits)
fix = 0
for t in corrections:
    i = bisect_left(commit_times, t['t'] - 1500)
    if i < len(commit_times) and commit_times[i] < t['t']:
        fix += 1
M['keys'] = {'key_letters': len(commits), 'backspaced_within_1500ms': fix, 'pct': round(100 * fix / len(commits), 1) if commits else None}
print(' ', M['keys'])
letter_keys = Counter((t['ins'] or '').lower() for t in commits)

# ---- taps that did nothing ------------------------------------------------------------------------------------------
print('\n=== Taps (the Tap keys setting is empty, so these type nothing) ===')
tap_outs = [o for o in outs if o['type'] == 'tap']
print(' total tap outcomes', len(tap_outs), ' by key', Counter(o.get('key') for o in tap_outs).most_common(12))
M['taps'] = {'n': len(tap_outs), 'by_key': dict(Counter(o.get('key') for o in tap_outs).most_common(15))}

json.dump(M, open(os.path.join(OUT, 'touches.json'), 'w'), indent=1)
