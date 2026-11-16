#!/usr/bin/env python3
"""Hand-corrected text, with and without the windows known or suspected to be deliberate tests.
Usage: subsets.py private-dir corpus replay out.json"""
import datetime
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

P, corpus_path, replay_path, out_path = sys.argv[1:5]
rows = {r['id']: r for r in (json.loads(l) for l in open(os.path.join(P, 'attempts.jsonl'), encoding='utf-8'))}
corpus = {json.loads(l)['id']: json.loads(l) for l in open(corpus_path, encoding='utf-8')}
replay = [json.loads(l) for l in open(replay_path, encoding='utf-8')]
# Confirmed by the reference timeline or test runs, phone local time.
TESTS = [('11:07:16', '11:07:18'), ('11:12:09', '11:13:04'), ('11:16:20', '11:16:48'), ('11:24:12', '11:24:20'), ('14:31:16', '14:40:59')]
UNSURE = ('13:20:00', '16:30:00')


def clock(rid):
    return datetime.datetime.fromtimestamp(rows[rid]['wall'] / 1000).strftime('%H:%M:%S')


def key(w):
    return R.norm(w).replace("'", '')


items = []
for e in replay:
    c = corpus.get(e['id'])
    if not c or c['set'] != 'hand' or not c['first']:
        continue
    rid = int(e['id'].split('-')[1])
    t = clock(rid)
    exp = key(c['expected'])
    items.append({'t': t, 'test': any(a <= t <= b for a, b in TESTS), 'unsure': UNSURE[0] <= t <= UNSURE[1],
                  'now1': [key(w) for w in e['top']][:1] == [exp], 'now3': exp in [key(w) for w in e['top']],
                  'live1': [key(w) for w in c['live']][:1] == [exp], 'live3': exp in [key(w) for w in c['live']], 'found': e['rank'] > 0})


def show(name, chosen):
    n = len(chosen)
    r = {'n': n}
    for k in ('now1', 'now3', 'live1', 'live3', 'found'):
        r[k] = sum(x[k] for x in chosen)
        r[k + '_pct'] = round(100 * r[k] / n, 1) if n else None
    print(f"{name:<52} n={n:<4} current top1 {r['now1_pct']}% top3 {r['now3_pct']}% | live top1 {r['live1_pct']}% top3 {r['live3_pct']}% | in decoder list {r['found_pct']}%")
    return r


out = {'all hand-corrected': show('all hand-corrected first tries', items),
       'without confirmed tests': show('confirmed test windows removed', [x for x in items if not x['test']]),
       'strict': show('also outside 13:20-16:30 (uncertain period)', [x for x in items if not x['test'] and not x['unsure']]),
       'uncertain period only': show('only inside 13:20-16:30', [x for x in items if not x['test'] and x['unsure']])}
json.dump(out, open(out_path, 'w'), indent=1)
