#!/usr/bin/env python3
"""Scores the edge-reading variants against the unchanged engine, first tries only. Usage: edge_score.py private-dir"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

P = sys.argv[1]
corpus = {}
for line in open(os.path.join(P, 'corpus.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    if e['first'] and e['set'] in ('prompted', 'hand', 'recorder'):
        corpus[e['id']] = e


def key(w):
    return R.norm(w).replace("'", '')


def load(name):
    out = {}
    for line in open(os.path.join(P, f'variant-{name}.jsonl'), encoding='utf-8'):
        e = json.loads(line)
        if e['id'] in corpus:
            exp = key(corpus[e['id']]['expected'])
            top = [key(w) for w in e['top']]
            out[e['id']] = (top[:1] == [exp], exp in top, e['millis'])
    return out


base = load('control')
out = {}
for v in (sys.argv[2:] or ('control', 'shift', 'edges', 'both')):
    now = load(v)
    print(f'== {v}  latency median {sorted(x[2] for x in now.values())[len(now) // 2]} ms')
    out[v] = {}
    for label, select in (('prompted', lambda c: c['set'] == 'prompted'), ('prompted held-back', lambda c: c['set'] == 'prompted' and int(c['slot']) % 3 == 2), ('hand', lambda c: c['set'] == 'hand'), ('recorder', lambda c: c['set'] == 'recorder'),
                          ('words with P', lambda c: 'p' in key(c['expected'])), ('words with A', lambda c: 'a' in key(c['expected'])), ('words with neither', lambda c: not set('pa') & set(key(c['expected'])))):
        ids = [i for i, c in corpus.items() if select(c) and i in now and i in base]
        n = len(ids)
        first, top3 = sum(now[i][0] for i in ids), sum(now[i][1] for i in ids)
        better = sum(now[i][0] and not base[i][0] for i in ids)
        worse = sum(base[i][0] and not now[i][0] for i in ids)
        better3 = sum(now[i][1] and not base[i][1] for i in ids)
        worse3 = sum(base[i][1] and not now[i][1] for i in ids)
        out[v][label] = {'n': n, 'first': round(100 * first / n, 1), 'top3': round(100 * top3 / n, 1), 'first_better': better, 'first_worse': worse, 'top3_better': better3, 'top3_worse': worse3}
        print(f"  {label:<20} n={n:<4} first {100 * first / n:5.1f}% (+{better} -{worse})  top3 {100 * top3 / n:5.1f}% (+{better3} -{worse3})")
json.dump(out, open(os.path.join(os.path.dirname(P), 'report', 'edge-variants.json'), 'w'), indent=1)
