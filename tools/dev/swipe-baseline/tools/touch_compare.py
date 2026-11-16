#!/usr/bin/env python3
"""What today's touch code does to the input that went wrong this morning.
Usage: touch_compare.py private-dir raw-log result.txt out.json"""
import json
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

P, log_path, result_path, out_path = sys.argv[1:5]
rows = [json.loads(l) for l in open(os.path.join(P, 'attempts.jsonl'), encoding='utf-8')]
events = R.load(log_path)
outs = [e for e in events if e['k'] == 'out' and e['type'] == 'swipe']
# prompt started ended counts cancelled raw(type a b c) final(type a b c)
results = {}
all_results = []
for line in open(result_path):
    f = line.split()
    item = {'start': int(f[1]), 'end': int(f[2]), 'counts': f[3] == '1', 'cancelled': f[4] == '1', 'raw': f[5], 'final': f[9], 'n': f[10] if f[9] == 'S' else None, 'del': f[12] == '1' if f[9] == 'S' else False}
    results.setdefault((item['start'], item['end']), []).append(item)
    all_results.append(item)


def now_for(out):
    key = (out['start'], out['end'])
    if key in results:
        return results[key][0]
    # A joined piece keeps its first time, so look for a result that ends with this stroke.
    for k, v in results.items():
        if k[1] == out['end']:
            return v[0]
    return None


def label(out_item):
    if out_item is None:
        return 'gone'
    if out_item['final'] == 'S':
        return 'delete' if out_item['del'] else 'swipe'
    return 'tap' if out_item['final'] == 'T' else 'nothing'


by_start = {}
for o in outs:
    by_start[(o['start'], o['end'])] = o

logged = Counter()
for o in outs:
    was = 'delete' if o.get('deletes') else 'swipe'
    logged[(was, label(now_for(o)))] += 1
print('Logged outcome -> what the current touch code does:')
for k, v in sorted(logged.items()):
    print(f'  {k[0]:<8} -> {k[1]:<8} {v}')

out = {'logged_to_now': {f'{a}->{b}': v for (a, b), v in logged.items()}}

# Accidental candidates and genuine strokes
eff = [r for r in rows if r['superseded_by'] is None]


def status(r):
    return label(now_for({'start': r['start'], 'end': r['end']}))


def norm(w):
    return R.norm(w).replace("'", '') if w else w


groups = {'removed with nothing in its place (accidental candidates)': [r for r in eff if r['hand'] is None and any(e.startswith('backspace') for e in r['slot_events'])],
          'kept words': [r for r in eff if r['hand'] and norm(r['words'][0]) == norm(r['hand']) and not any(e.startswith('backspace') for e in r['slot_events'])]}
out['groups'] = {}
for name, items in groups.items():
    c = Counter(status(r) for r in items)
    out['groups'][name] = {'n': len(items), **dict(c)}
    print(f'{name}: n={len(items)} now: {dict(c)}')

# Delete flicks that removed a right word
by_slot = {}
for r in eff:
    by_slot.setdefault(r['slot'], []).append(r)
false_delete = []
for rs in by_slot.values():
    rs.sort(key=lambda r: r['start'])
    target = rs[0]['expected'] or rs[0]['hand']
    if not target:
        continue
    for r in (rs[:-1] if len(rs) > 1 else rs):
        if 'backspace-word' in r['slot_events']:
            if norm(r['words'][0]) == norm(target):
                false_delete.append(r)
            break
# find the delete flick that came after each right word
deletes = sorted([o for o in outs if o.get('deletes')], key=lambda o: o['start'])
from bisect import bisect_left
starts = [d['start'] for d in deletes]
changed = Counter()
for r in false_delete:
    i = bisect_left(starts, r['end'])
    if i < len(deletes) and deletes[i]['start'] - r['end'] < 30000:
        changed[label(now_for(deletes[i]))] += 1
    else:
        changed['no delete flick found'] += 1
out['false_deletes'] = {'n': len(false_delete), 'flick_now': dict(changed)}
print('delete flicks that removed a right word:', out['false_deletes'])

# New outcomes the current code adds that were not logged.
logged_keys = set(by_start)
extra = Counter()
for key, items in results.items():
    item = items[0]
    if key not in logged_keys and item['final'] == 'S':
        extra['new delete' if item['del'] else 'new swipe'] += 1
out['new_outcomes'] = dict(extra)
print('Outcomes the current code makes that were not logged:', dict(extra))
json.dump(out, open(out_path, 'w'), indent=1)
