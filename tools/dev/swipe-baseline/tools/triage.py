#!/usr/bin/env python3
"""Case-by-case look at what went wrong in the prompted blocks, and how much of the removed-word list is deliberate testing.
Usage: triage.py private-dir"""
import json, sys, os, datetime
from collections import Counter
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R
P = sys.argv[1]
rows = [json.loads(l) for l in open(os.path.join(P, 'attempts.jsonl'), encoding='utf-8')]
ex = json.load(open(os.path.join(P, 'extras.json')))
ordered = sorted(rows, key=lambda r: r['start'])
idx = {r['id']: i for i, r in enumerate(ordered)}
STY = {'style:Swipe only', 'style:Left swipe', 'style:Index finger', 'style:Random'}
def L(w): return R.norm(w).replace("'", '') if w else w
def stroke(r):
    pts = R.key_units(r['chain_points'])
    return round(R.path_length(pts), 1) if len(pts) > 1 else 0, (pts[-1][2] - pts[0][2]) if len(pts) > 1 else 0
def gaps(r):
    i = idx[r['id']]
    p = r['start'] - ordered[i-1]['end'] if i else None
    n = ordered[i+1]['start'] - r['end'] if i+1 < len(ordered) else None
    return p, n
print('=== Prompted words with no surviving place (ghosts) ===')
for r in rows:
    if r['block'] in STY and r['expected'] is None and r['superseded_by'] is None:
        print(' ', r['block'][6:], 'got', r['committed'], 'len/dur', stroke(r), 'prevgap/nextgap', gaps(r), 'chain', len(r['chain']), r['slot_events'][:3], 'context-tail', r['context'][-24:])
print('\n=== Cut-short and very short prompted first tries ===')
words = json.load(open(os.path.join(P, 'words.json')))
by = {r['id']: r for r in rows}
for w in words:
    if w['gclass'].startswith('stroke cut short'):
        r = by[w['id']]
        print(' ', w['expected'], '->', w['got'], 'len/ideal', round(w['g']['len'], 1), round(w['g']['ideal'], 1), 'dur', w['g']['dur'], 'gaps', gaps(r), 'attempts', w['attempts'], 'next-word', ordered[idx[r['id']]+1]['committed'] if idx[r['id']]+1 < len(ordered) else None)
print('\n=== Right word removed by a delete flick (prompted) ===')
slots = {}
for r in rows:
    if r['block'] in STY and r['expected'] and r['superseded_by'] is None:
        slots.setdefault(r['slot'], []).append(r)
dels = sorted(ex['deletes'], key=lambda d: d['start'])
from bisect import bisect_left
ds = [d['start'] for d in dels]
for rs in slots.values():
    rs.sort(key=lambda r: r['start'])
    for r in (rs[:-1] if len(rs) > 1 else rs):
        if 'backspace-word' in r['slot_events']:
            if L(r['words'][0]) == L(r['expected']):
                i = bisect_left(ds, r['end'])
                d = dels[i] if i < len(dels) else None
                print(' ', r['block'][6:], r['words'][0], '| flick after', d['start'] - r['end'] if d else None, 'ms, flick pts', d['n'] if d else None, 'dur', d['end'] - d['start'] if d else None, '| then', [x['committed'] for x in rs[1:3]])
            break
print('\n=== Removed words by time window, outside prompted blocks ===')
c = Counter(); total = Counter()
for r in rows:
    if r['superseded_by'] is not None or r['block'] in STY: continue
    t = datetime.datetime.fromtimestamp(r['wall']/1000).strftime('%H')
    total[t] += 1
    if r['hand'] is None and any(e.startswith('backspace') for e in r['slot_events']): c[t] += 1
print('  by hour removed/total', {k: f'{c[k]}/{total[k]}' for k in sorted(total)})
print('  in windows W4-W8 (10:12-11:05):', sum(1 for r in rows if r['superseded_by'] is None and r['block'] not in STY and r['hand'] is None and any(e.startswith('backspace') for e in r['slot_events']) and '10:12' <= datetime.datetime.fromtimestamp(r['wall']/1000).strftime('%H:%M') <= '11:05'))
