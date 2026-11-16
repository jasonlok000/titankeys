#!/usr/bin/env python3
"""Scores CompletionRunner's output: right word first and in the top 3 after 1, 2 and 3 letters, and the letters typed
before the right word shows in the top 3. Variants: old context model, next-word model, both.
Usage: score_completions.py completions.jsonl out.json"""
import json
import sys

rows = [json.loads(line) for line in open(sys.argv[1], encoding='utf-8')]
names = ('old', 'model', 'fuse5', 'fuse20')
out = {}
for s in ('prompted', 'hand', 'recorder', 'all'):
    part = [r for r in rows if s == 'all' or r['set'] == s]
    res = {'n': len(part)}
    for v, name in enumerate(names):
        d = {}
        for k in (1, 2, 3):
            at = [r for r in part if r['len'] > k]
            first = sum(r['ranks'][k - 1][v] == 0 for r in at)
            top3 = sum(0 <= r['ranks'][k - 1][v] < 3 for r in at)
            d[f'{k} letters'] = [round(100 * first / len(at), 1), round(100 * top3 / len(at), 1), len(at)]
        # Letters typed before the word is in the top 3 to tap; a word never shown is typed in full.
        typed = [r['found'][v] if r['found'][v] > 0 else r['len'] for r in part]
        d['letters typed'] = round(sum(typed) / len(part), 2)
        d['letters saved %'] = round(100 * (1 - sum(typed) / sum(r['len'] for r in part)), 1)
        res[name] = d
    out[s] = res
json.dump(out, open(sys.argv[2], 'w'), indent=1)
for s, res in out.items():
    print(s, res['n'])
    for name in names:
        print(' ', name, res[name])
