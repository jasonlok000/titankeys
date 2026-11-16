#!/usr/bin/env python3
"""Upper limit of letting the following word correct the previous one. Uses the word that really came next.
Usage: nextword.py make|score private-dir [in] [out]"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

mode, P = sys.argv[1:3]
corpus = []
for line in open(os.path.join(P, 'corpus.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    if e['first'] and e['set'] in ('prompted', 'hand'):
        corpus.append(e)
replay = {}
for line in open(os.path.join(P, 'replay-4f75dcb-full.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    replay[e['id']] = e


def key(w):
    return R.norm(w).replace("'", '')


corpus.sort(key=lambda e: (e['set'], e['style'], int(e['slot'])))
pairs = {}
for a, b in zip(corpus, corpus[1:]):
    if a['set'] == b['set'] and a['style'] == b['style'] and 0 < int(b['slot']) - int(a['slot']) <= 3 and b['context'].endswith(tuple(' ')) and a['id'] in replay:
        pairs[a['id']] = (a, b['expected'])

if mode == 'make':
    with open(sys.argv[3], 'w', encoding='utf-8') as out:
        for cid, (a, nxt) in pairs.items():
            cands = [w for w in replay[cid]['top']][:3]
            if len(cands) >= 2:
                out.write(json.dumps({'id': cid, 'context': a['context'], 'next': key(nxt), 'cands': cands}) + '\n')
    print('pairs', len(pairs))
    sys.exit()

scores = {}
for line in open(sys.argv[3], encoding='utf-8'):
    e = json.loads(line)
    scores[e['id']] = e['logp']
results = {}
for setname in ('prompted', 'hand'):
    ids = [i for i in scores if pairs[i][0]['set'] == setname]
    tune = [i for i in ids if int(pairs[i][0]['slot']) % 3 != 2]
    hold = [i for i in ids if int(pairs[i][0]['slot']) % 3 == 2]

    def right(ids_, w):
        ok = base = 0
        for i in ids_:
            a, _ = pairs[i]
            r = replay[i]
            cand = [c for c in r['top']][:3]
            sc = [s[1] for s in r['scores']][:len(cand)]
            exp = key(a['expected'])
            combined = [sc[k] + w * scores[i][k] for k in range(len(cand))]
            best = max(range(len(cand)), key=lambda k: combined[k])
            ok += key(cand[best]) == exp
            base += key(cand[0]) == exp
        return ok, base, len(ids_)

    sweep = {w: right(tune, w) for w in (0, .25, .5, 1, 1.5, 2, 3, 4, 6)}
    best_w = max(sweep, key=lambda w: sweep[w][0])
    held = right(hold, best_w)
    every = right(ids, best_w)
    results[setname] = {'n': len(ids), 'best_weight_from_tuning_part': best_w, 'held_back': {'n': held[2], 'first_before': held[1], 'first_after': held[0]}, 'all': {'n': every[2], 'first_before': every[1], 'first_after': every[0], 'pct_before': round(100 * every[1] / every[2], 1), 'pct_after': round(100 * every[0] / every[2], 1)}}
    print(setname, json.dumps(results[setname]))
    print('  tuning sweep (correct first of', sweep[0][2], '):', {k: v[0] for k, v in sweep.items()})
json.dump(results, open(sys.argv[4], 'w'), indent=1)
