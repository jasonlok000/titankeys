#!/usr/bin/env python3
"""Refreshes the live statistics from a labelled log: how the engine that was on the phone did, by period, using the words
it actually showed and whether engine 2.0 ranked them. Counts only. Usage: live_stats.py v10-dir v9-dir out.json"""
import json
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ck_core as C

V10, V9, OUT = sys.argv[1:4]
attempts = {json.loads(l)['id']: json.loads(l) for l in open(os.path.join(V10, 'attempts.jsonl'), encoding='utf-8')}
last9 = max(json.loads(l)['wall'] for l in open(os.path.join(V9, 'attempts.jsonl'), encoding='utf-8'))
corpus = [json.loads(l) for l in open(os.path.join(V10, 'corpus.jsonl'), encoding='utf-8')]
out = {}
groups = {}
for e in corpus:
    kind = e['id'].split('-')[0]
    if not e['first'] or kind not in ('log', 'hand'):
        continue
    a = attempts[int(e['id'].split('-')[1])]
    period = 'after engine 2.0 data' if a['wall'] > last9 else 'nov 9 (training data)'
    engine = 'engine 2.0 ranked' if a.get('ranked') else 'engine 1.x order'
    for g in ((period, e['set']), (period, e['set'], engine)):
        groups.setdefault(g, []).append(e)
for g, rows in sorted(groups.items()):
    n = len(rows)
    ok1 = sum([C.key(w) for w in e['live']][:1] == [C.key(e['expected'])] for e in rows)
    ok3 = sum(C.key(e['expected']) in [C.key(w) for w in e['live'][:3]] for e in rows)
    out[' / '.join(g)] = {'n': n, 'first': round(100 * ok1 / n, 1), 'top3': round(100 * ok3 / n, 1)}
    print(f"{' / '.join(g):<58} n={n:<5} first {100 * ok1 / n:5.1f}  top 3 {100 * ok3 / n:5.1f}")
json.dump(out, open(OUT, 'w'), indent=1)
