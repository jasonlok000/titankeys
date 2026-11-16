#!/usr/bin/env python3
"""Engine 2.1 (#31) splits, fixed once: every labelled stroke in the v10 corpus gets one part.
  held   the 540 held-back first tries of engine 2.0, frozen by stroke time (never trained on by the twin)
  test   the later half (by time) of the strokes recorded after engine 2.0's training data: the clean test
  train  everything else, including the earlier half of the new strokes
Writes splits.json and titan-strokes-v21.jsonl for the 3090 (strokes and words only, no text around them).
Usage: split_v21.py v10-dir frozen-540-v10.json new-v10.json"""
import json
import os
import sys

V, frozen_path, new_path = sys.argv[1:4]
frozen = {s['id'] for s in json.load(open(frozen_path))['strokes']}
new = {s['id'] for s in json.load(open(new_path))['strokes']}
attempts = {json.loads(l)['id']: json.loads(l) for l in open(os.path.join(V, 'attempts.jsonl'), encoding='utf-8')}
corpus = [json.loads(l) for l in open(os.path.join(V, 'corpus.jsonl'), encoding='utf-8')]


def wall(e):
    kind, n = e['id'].split('-')
    return attempts[int(n)]['wall'] if kind in ('log', 'hand') else 0


new_sorted = sorted((e for e in corpus if e['id'] in new), key=wall)
later = {e['id'] for e in new_sorted[len(new_sorted) // 2:]}
splits = {}
with open(os.path.join(V, 'titan-strokes-v21.jsonl'), 'w', encoding='utf-8', newline='\n') as out:
    for e in corpus:
        part = 'held' if e['id'] in frozen else 'test' if e['id'] in later else 'train'
        splits[e['id']] = part
        out.write(json.dumps({'id': e['id'], 'set': e['set'], 'slot': e['slot'], 'first': e['first'], 'expected': e['expected'],
                              'x': e['x'], 'y': e['y'], 't': e['t'], 'part': part}) + '\n')
json.dump(splits, open(os.path.join(V, 'splits.json'), 'w'))
from collections import Counter
print(Counter((p, e['first']) for e, p in ((e, splits[e['id']]) for e in corpus)))
