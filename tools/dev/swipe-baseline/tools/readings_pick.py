#!/usr/bin/env python3
"""Which readings of a stroke are worth their cost. Greedy choice on one part of the strokes, checked on the rest.
Usage: readings_pick.py private-dir out.json"""
import json
import os
import sys
import zlib
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

P, out_path = sys.argv[1:3]
corpus = {}
for line in open(os.path.join(P, 'corpus.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    if e['first'] and e['set'] in ('prompted', 'hand', 'recorder'):
        corpus[e['id']] = e


def key(w):
    return R.norm(w).replace("'", '')


found = defaultdict(dict)
for line in open(os.path.join(P, 'dump.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    rid, reading = e['id'].split('#')
    words = {key(w[0]) for w in e['deep']}
    found[rid][reading] = key(corpus[rid]['expected']) in words
ids = list(found)
tune = [i for i in ids if zlib.crc32(i.encode()) % 3 != 2]
hold = [i for i in ids if zlib.crc32(i.encode()) % 3 == 2]
all_readings = list(next(iter(found.values())))


def score(chosen, group, s=None):
    items = [i for i in group if s is None or corpus[i]['set'] == s]
    return round(100 * sum(any(found[i].get(r) for r in chosen) for i in items) / len(items), 1)


chosen = ['plain']
order = [('plain', score(chosen, tune), score(chosen, hold))]
while len(chosen) < len(all_readings):
    best = max((r for r in all_readings if r not in chosen), key=lambda r: score(chosen + [r], tune))
    chosen.append(best)
    order.append((best, score(chosen, tune), score(chosen, hold)))
print('reading added -> tune retrieval, held-back retrieval')
for row in order:
    print(' ', row)
out = {'greedy': order, 'by_set': {s: {str(k): score(chosen[:k], ids, s) for k in (1, 2, 3, 4, 6, 12)} for s in ('prompted', 'hand', 'recorder')}}
print(out['by_set'])
json.dump(out, open(out_path, 'w'), indent=1)
