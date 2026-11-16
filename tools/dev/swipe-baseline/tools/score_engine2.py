#!/usr/bin/env python3
"""Scores a phone run of engine 2.0 (Engine2Runner) against the labels: first choice and top 3, engine 2.0 against the
FUTO engine's own order from the same run. The shipped encoder and ranker learned from every labelled stroke, so these
numbers are in-sample: they show that the phone runs the engine as trained, not how it does on new strokes. For that,
use the held-back numbers of train_ranker.py. Usage: score_engine2.py corpus.jsonl engine2-phone.jsonl out.json"""
import json
import sys
from collections import defaultdict

sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
import ck_core as C

corpus_path, run_path, out_path = sys.argv[1:4]
corpus = {}
for line in open(corpus_path, encoding='utf-8'):
    e = json.loads(line)
    corpus[e['id']] = e
rows = [json.loads(l) for l in open(run_path, encoding='utf-8')]
tally = defaultdict(lambda: [0, 0, 0, 0, 0])
ms = defaultdict(list)
for r in rows:
    e = corpus[r['id'].split('#')[0]]
    want = C.key(e['expected'])
    new = [C.key(w) for w in r['top']]
    old = [C.key(w) for w in r['today']]
    for group in ('all', e['set']):
        t = tally[group]
        t[0] += 1
        t[1] += new[:1] == [want]
        t[2] += want in new[:3]
        t[3] += old[:1] == [want]
        t[4] += want in old[:3]
    for name, v in zip(('next-word model', 'FUTO engine', 'CleverKeys', 'ranker', 'whole swipe'), r['ms']):
        ms[name].append(v)
out = {}
for group, (n, f, t3, of, ot3) in tally.items():
    out[group] = {'n': n, 'engine 2.0': [round(100 * f / n, 1), round(100 * t3 / n, 1)], 'FUTO order': [round(100 * of / n, 1), round(100 * ot3 / n, 1)]}
    print(f"{group:<9} n={n:<5} engine 2.0 {out[group]['engine 2.0'][0]} / {out[group]['engine 2.0'][1]}   FUTO order {out[group]['FUTO order'][0]} / {out[group]['FUTO order'][1]}")
for name, v in ms.items():
    v = sorted(v)
    out.setdefault('ms', {})[name] = {'median': v[len(v) // 2], 'slowest tenth from': v[int(len(v) * .9)]}
    print(f"{name:<16} median {v[len(v) // 2]} ms, slowest tenth from {v[int(len(v) * .9)]} ms")
json.dump(out, open(out_path, 'w'), indent=1)
