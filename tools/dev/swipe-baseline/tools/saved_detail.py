#!/usr/bin/env python3
"""Details behind score_saved.py, counts and shown words only: each name stroke's top three per condition, and for the
other strokes where a saved word came first but was not meant, whether the first word was already wrong with nothing
saved, and whether the meant word left the top three. Usage: saved_detail.py corpus none.jsonl boosts.jsonl saved.tsv"""
import json
import sys

corpus_path, none_path, boosts_path, saved_path = sys.argv[1:5]
norm = lambda w: str(w).lower().replace('’', "'").strip()
expected = {json.loads(l)['id']: norm(json.loads(l)['expected']) for l in open(corpus_path, encoding='utf-8')}
saved = {norm(l.split('\t')[0]) for l in open(saved_path, encoding='utf-8') if l.strip()}
read = lambda p: {json.loads(l)['id'].split('#')[0]: json.loads(l) for l in open(p, encoding='utf-8')}
none, boosted = read(none_path), read(boosts_path)
boosts = sorted(next(iter(boosted.values()))['tops'], key=float)
for i in sorted(k for k in boosted if k.startswith('name-')):
    print(i, 'nothing saved', list(none[i]['tops'].values())[0], '| saved candidates', boosted[i]['savedCandidates'])
    for b in boosts:
        print('   boost', b, boosted[i]['tops'][b])
for b in boosts:
    hits = []
    for i, r in boosted.items():
        if i.startswith('name-') or i not in expected:
            continue
        top = r['tops'][b]
        if top and norm(top[0]) in saved and norm(top[0]) != expected[i]:
            base = list(none[i]['tops'].values())[0]
            hits.append((norm(base[0]) == expected[i] if base else False, expected[i] in [norm(w) for w in top[:3]]))
    print(f'boost {b}: saved word wrongly first {len(hits)}; already wrong with nothing saved {sum(not h[0] for h in hits)};'
          f' meant word still in top 3 {sum(h[1] for h in hits)}')
