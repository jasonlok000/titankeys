#!/usr/bin/env python3
"""Where the right word sits below the top 3, and how each letter fares (the P question).
Usage: depth_and_letters.py private-dir out.json"""
import json
import os
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

P, out_path = sys.argv[1:3]
corpus = {}
for line in open(os.path.join(P, 'corpus.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    if e['first'] and e['set'] in ('prompted', 'hand', 'recorder'):
        corpus[e['id']] = e
replay = {}
for line in open(os.path.join(P, 'replay-4f75dcb-full.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    replay[e['id']] = e
dump = defaultdict(dict)
for line in open(os.path.join(P, 'dump.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    rid, reading = e['id'].split('#')
    dump[rid][reading] = e
ck = {}
for line in open(os.path.join(P, 'ck.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    ck[e['id']] = e['c']


def key(w):
    return R.norm(w).replace("'", '')


out = {}
print('=== Where the right word sits, first tries ===')
for s in ('prompted', 'hand', 'recorder'):
    ids = [i for i in corpus if corpus[i]['set'] == s and i in replay]
    n = len(ids)
    c = Counter()
    miss3 = Counter()
    for i in ids:
        exp = key(corpus[i]['expected'])
        top = [key(w) for w in replay[i]['top']]
        rank = replay[i]['rank']
        if top[:1] == [exp]:
            c['shown first'] += 1
        elif exp in top:
            c['shown 2nd or 3rd'] += 1
        else:
            c['not shown'] += 1
            band = 'decoder rank 1-3 (dropped by rerank)' if 1 <= rank <= 3 else 'decoder rank 4-16' if 4 <= rank <= 16 else 'decoder rank 17-100' if rank > 16 else 'not in decoder top 100'
            miss3[band] += 1
            reads = any(exp in {key(w[0]) for w in r['deep']} for r in dump[i].values())
            cks = any(key(w[0]) == exp for w in ck.get(i, []))
            miss3[('found by another reading' if reads and rank < 1 else 'not found by another reading') if rank < 1 else 'n/a'] += 0
            if rank < 1:
                miss3['  of those: another reading finds it' if reads else '  of those: no reading finds it'] += 1
                miss3['  of those: CleverKeys finds it' if cks else '  of those: CleverKeys misses it too'] += 1
    miss3.pop('n/a', None)
    miss3 = {k: v for k, v in miss3.items() if v}
    out[s] = {'n': n, **dict(c), 'not_shown_detail': miss3}
    print(f"{s}: n={n} {dict(c)}")
    print('   not shown:', miss3)

print('\n=== By letter: all three sets together, first tries ===')
letters = defaultdict(lambda: {'first': [], 'last': [], 'any': []})
rows = []
for i, e in corpus.items():
    if i not in replay:
        continue
    exp = key(e['expected'])
    top = [key(w) for w in replay[i]['top']]
    ok1, ok3 = top[:1] == [exp], exp in top
    x0, y0 = e['x'][0] * 10, e['y'][0] * 3
    xn, yn = e['x'][-1] * 10, e['y'][-1] * 3
    if not exp or exp[0] not in R.KEY:
        continue
    row = {'w': exp, 'ok1': ok1, 'ok3': ok3, 'x0': x0, 'y0': y0, 'xn': xn, 'yn': yn, 'maxx': max(e['x']) * 10, 'minx': min(e['x']) * 10, 'set': e['set'], 't0': e['t'][1] - e['t'][0] if len(e['t']) > 1 else 0}
    rows.append(row)
    letters[exp[0]]['first'].append(row)
    letters[exp[-1]]['last'].append(row)
    for ch in set(exp):
        letters[ch]['any'].append(row)


def nearest(x, y):
    return min(R.KEY, key=lambda c: (R.KEY[c][0] - x) ** 2 + (R.KEY[c][1] - y) ** 2)


table = {}
order = sorted(R.KEY, key=lambda c: (R.KEY[c][1], R.KEY[c][0]))
print('letter | starts: n first% top3% nearest-key-right% | ends: n first% top3% | contains: n first%')
for ch in order:
    st, en, an = letters[ch]['first'], letters[ch]['last'], letters[ch]['any']

    def pct(items, k):
        return round(100 * sum(r[k] for r in items) / len(items), 1) if items else None

    hit = round(100 * sum(nearest(r['x0'], r['y0']) == ch for r in st) / len(st), 1) if st else None
    hit_end = round(100 * sum(nearest(r['xn'], r['yn']) == ch for r in en) / len(en), 1) if en else None
    table[ch] = {'starts': len(st), 'start_first': pct(st, 'ok1'), 'start_top3': pct(st, 'ok3'), 'start_on_key': hit, 'ends': len(en), 'end_first': pct(en, 'ok1'), 'end_top3': pct(en, 'ok3'), 'end_on_key': hit_end, 'any': len(an), 'any_first': pct(an, 'ok1')}
    print(f"  {ch}  | {len(st):>3} {pct(st,'ok1')} {pct(st,'ok3')} on-key {hit} | {len(en):>3} {pct(en,'ok1')} {pct(en,'ok3')} on-key {hit_end} | {len(an):>3} {pct(an,'ok1')}")
out['letters'] = table

print('\n=== P in detail ===')
for label, group, xy in (('words starting with P', letters['p']['first'], ('x0', 'y0')), ('words ending with P', letters['p']['last'], ('xn', 'yn')),
                         ('words starting with Q', letters['q']['first'], ('x0', 'y0')), ('words starting with O', letters['o']['first'], ('x0', 'y0')), ('words starting with A', letters['a']['first'], ('x0', 'y0'))):
    land = Counter(nearest(r[xy[0]], r[xy[1]]) for r in group)
    xs = sorted(r[xy[0]] for r in group)
    print(f"  {label}: n={len(group)} read as {land.most_common(5)}; x p10/p50/p90 {R.percentile(xs,.1):.2f} {R.percentile(xs,.5):.2f} {R.percentile(xs,.9):.2f}")
with_p = letters['p']['any']
without_p = [r for r in rows if 'p' not in r['w']]
reach = sorted(r['maxx'] for r in with_p)
print('  words containing P: furthest right reached p10/p50/p90 =', round(R.percentile(reach, .1), 2), round(R.percentile(reach, .5), 2), round(R.percentile(reach, .9), 2), ' (P centre is 9.5, sensor stops at about 9.49)')
def rate(items):
    return round(100 * sum(r['ok1'] for r in items) / len(items), 1), round(100 * sum(r['ok3'] for r in items) / len(items), 1), len(items)
print('  with P (first, top3, n):', rate(with_p), ' without P:', rate(without_p))
for s in ('prompted', 'hand', 'recorder'):
    print(f'    {s}: with P', rate([r for r in with_p if r['set'] == s]), 'without P', rate([r for r in without_p if r['set'] == s]))
out['p'] = {'with': rate(with_p), 'without': rate(without_p)}
json.dump(out, open(out_path, 'w'), indent=1)
