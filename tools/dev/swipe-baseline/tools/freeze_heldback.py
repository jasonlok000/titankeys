#!/usr/bin/env python3
"""Freezes engine 2.0's 540 held-back first tries by stroke start time, so a change to rawlog.py's slot order can never
reshuffle them. A raw-log stroke is keyed by its wall-clock start; a recorder stroke by its recording index.
Usage: freeze_heldback.py v9-dir out.json"""
import json
import os
import sys

V, OUT = sys.argv[1:3]
attempts = {}
for line in open(os.path.join(V, 'attempts.jsonl'), encoding='utf-8'):
    r = json.loads(line)
    attempts[r['id']] = r
evidence = {json.loads(l)['id'].split('#')[0] for l in open(os.path.join(V, 'evidence.jsonl'), encoding='utf-8')}
ck = {json.loads(l)['id'] for l in open(os.path.join(V, 'ck-crossfit2.jsonl'), encoding='utf-8')}
frozen = []
for line in open(os.path.join(V, 'corpus.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    if not (e['first'] and e['set'] in ('prompted', 'hand', 'recorder') and e['id'] in evidence and e['id'] in ck):
        continue
    if int(e['slot']) % 3 != 2:
        continue
    kind, n = e['id'].split('-')
    row = {'id': e['id'], 'set': e['set'], 'expected': e['expected']}
    if kind in ('log', 'hand'):
        a = attempts[int(n)]
        row.update({'wall': a['wall'], 'start': a['start']})
    else:
        row.update({'recorder_index': int(n)})
    frozen.append(row)
json.dump({'note': 'engine 2.0 held-back first tries (slot % 3 == 2), frozen 2026-11-10; match raw-log strokes by wall/start',
           'count': len(frozen), 'strokes': frozen}, open(OUT, 'w'), indent=0)
print('frozen', len(frozen), {s: sum(r['set'] == s for r in frozen) for s in ('prompted', 'hand', 'recorder')})
