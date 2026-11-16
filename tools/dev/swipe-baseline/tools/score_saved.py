#!/usr/bin/env python3
"""Scores the saved-word phone job: for nothing saved and for each boost with the saved stand-in, how often the right
word is first and in the top 3, on the owner's swipes of one name (ids 'name-', from name_corpus.py) and on the 735 paired-test strokes, and for the latter the
strokes that each boost wins or loses against nothing saved. Counts only.
Usage: score_saved.py corpus.jsonl saved-none.jsonl saved-boosts.jsonl saved.tsv out.json"""
import json
import sys

corpus_path, none_path, boosts_path, saved_path, out_path = sys.argv[1:6]
norm = lambda w: str(w).lower().replace('’', "'").strip()
expected = {}
for line in open(corpus_path, encoding='utf-8'):
    r = json.loads(line)
    expected[r['id']] = ('name' if r['id'].startswith('name-') else r['set'], norm(r['expected']))
saved = {norm(l.split('\t')[0]) for l in open(saved_path, encoding='utf-8') if l.strip()}


def read(path):
    out = {}
    for line in open(path, encoding='utf-8'):
        r = json.loads(line)
        out[r['id'].split('#')[0]] = r['tops']
    return out


none, boosted = read(none_path), read(boosts_path)
conditions = {'nothing saved': {i: t['0.0'] if '0.0' in t else list(t.values())[0] for i, t in none.items()}}
for boost in sorted({b for t in boosted.values() for b in t}, key=float):
    conditions[f'saved, boost {boost}'] = {i: t[boost] for i, t in boosted.items()}
report = {}
for name, tops in conditions.items():
    row = {}
    for part in ('name', 'other'):
        ids = [i for i in tops if i in expected and (expected[i][0] == 'name') == (part == 'name')]
        first = sum(norm(tops[i][0]) == expected[i][1] for i in ids if tops[i])
        three = sum(expected[i][1] in [norm(w) for w in tops[i][:3]] for i in ids)
        row[part] = {'strokes': len(ids), 'first': first, 'top 3': three}
        if part == 'other' and name != 'nothing saved':
            base = conditions['nothing saved']
            right = lambda t, i: bool(t[i]) and norm(t[i][0]) == expected[i][1]
            row[part]['wins against nothing saved'] = sum(right(tops, i) and not right(base, i) for i in ids if i in base)
            row[part]['losses against nothing saved'] = sum(right(base, i) and not right(tops, i) for i in ids if i in base)
            row[part]['saved word shown first where another was meant'] = sum(
                bool(tops[i]) and norm(tops[i][0]) in saved and norm(tops[i][0]) != expected[i][1] for i in ids)
    report[name] = row
json.dump(report, open(out_path, 'w'), indent=1)
print(json.dumps(report, indent=1))
