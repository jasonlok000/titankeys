#!/usr/bin/env python3
"""Which rule decides between joining two strokes and keeping them apart? Uses the replays of both readings.
Usage: join_gate.py private-dir"""
import json
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

P = sys.argv[1]


def key(w):
    return R.norm(w).replace("'", '')


base = {json.loads(l)['id']: json.loads(l) for l in open(os.path.join(P, 'replay-4f75dcb-full.jsonl'), encoding='utf-8')}
corp = {json.loads(l)['id']: json.loads(l) for l in open(os.path.join(P, 'stitch-corpus.jsonl'), encoding='utf-8')}
rows = []
for line in open(os.path.join(P, 'stitch-replay.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    kind, cid, gap = e['id'].split('|')
    if kind != 'F' or int(gap) > 150 or not e['scores'] or cid not in base or not base[cid]['scores']:
        continue
    c = corp[e['id']]
    b = base[cid]
    exp = key(c['expected'])
    alone, joined = b['scores'][0], e['scores'][0]
    alone_len, joined_len = max(1, len(key(alone[0]))), max(1, len(key(joined[0])))
    rows.append({'set': c['set'], 'exp': exp, 'gap': int(gap), 'alone_right': key(alone[0]) == exp, 'joined_right': key(joined[0]) == exp,
                 'alone_path': alone[2], 'joined_path': joined[2], 'alone_per': alone[2] / alone_len, 'joined_per': joined[2] / joined_len,
                 'alone_margin': alone[1] - b['scores'][1][1] if len(b['scores']) > 1 else 99, 'joined_margin': joined[1] - e['scores'][1][1] if len(e['scores']) > 1 else 99,
                 'alone_ctx': alone[3], 'joined_ctx': joined[3], 'alone_w': key(alone[0]), 'joined_w': key(joined[0])})
print('pairs with gap 150 ms or less:', len(rows), Counter((r['alone_right'], r['joined_right']) for r in rows))
RULES = {
    'never join': lambda r: False,
    'always join': lambda r: True,
    'alone margin under 5': lambda r: r['alone_margin'] < 5,
    'alone margin under 10': lambda r: r['alone_margin'] < 10,
    'alone margin under 20': lambda r: r['alone_margin'] < 20,
    'joined path per letter better': lambda r: r['joined_per'] > r['alone_per'],
    'joined margin beats alone margin': lambda r: r['joined_margin'] > r['alone_margin'],
    'joined word longer than alone word': lambda r: len(r['joined_w']) > len(r['alone_w']),
    'longer and alone margin under 20': lambda r: len(r['joined_w']) > len(r['alone_w']) and r['alone_margin'] < 20,
    'longer and joined margin >= alone margin': lambda r: len(r['joined_w']) > len(r['alone_w']) and r['joined_margin'] >= r['alone_margin'],
}
print(f"{'rule':<44} right-after  fixed  broken  pairs joined")
for name, rule in RULES.items():
    right = fixed = broken = joined = 0
    for r in rows:
        j = rule(r)
        joined += j
        now = r['joined_right'] if j else r['alone_right']
        right += now
        fixed += now and not r['alone_right']
        broken += r['alone_right'] and not now
    print(f'  {name:<42} {right:>4}/{len(rows):<4} {fixed:>4} {broken:>6} {joined:>8}')
print('\nCases:')
for r in rows:
    print(f"  {r['set'][:4]} gap {r['gap']:>3} exp {r['exp']:<12} alone {r['alone_w']:<10}{'R' if r['alone_right'] else ' '} m{r['alone_margin']:6.1f} p{r['alone_path']:7.1f} | joined {r['joined_w']:<12}{'R' if r['joined_right'] else ' '} m{r['joined_margin']:6.1f} p{r['joined_path']:7.1f}")
