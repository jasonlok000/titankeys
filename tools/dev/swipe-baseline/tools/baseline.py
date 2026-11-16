#!/usr/bin/env python3
"""The one number set to compare an engine change against. Prints it and writes baseline.json.
Usage: baseline.py replay.jsonl corpus.jsonl out.json [--hand-live words-hand.json]
Compare two replays by running this on each. Only the corpus lines in both are counted."""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

replay_path, corpus_path, out_path = sys.argv[1:4]
corpus = {}
for line in open(corpus_path, encoding='utf-8'):
    e = json.loads(line)
    corpus[e['id']] = e
rows = []
for line in open(replay_path, encoding='utf-8'):
    e = json.loads(line)
    c = corpus.get(e['id'])
    if c:
        rows.append((c, e))


def key(w):
    return R.norm(w).replace("'", '')


def pct(a, b):
    return round(100 * a / b, 1) if b else None


def score(items):
    n = len(items)
    top1 = sum(key(e['top'][0]) == key(c['expected']) if e['top'] else False for c, e in items)
    top3 = sum(key(c['expected']) in [key(w) for w in e['top']] for c, e in items)
    found = sum(e['rank'] > 0 for c, e in items)
    return {'n': n, 'top1': top1, 'top1_pct': pct(top1, n), 'top3': top3, 'top3_pct': pct(top3, n), 'in_decoder_list': found, 'in_decoder_list_pct': pct(found, n)}


first = [(c, e) for c, e in rows if c['first']]
out = {'prompted': score([x for x in first if x[0]['set'] == 'prompted']),
       'prompted_held_back': score([x for x in first if x[0]['set'] == 'prompted' and int(x[0]['slot']) % 3 == 2]),
       'hand': score([x for x in first if x[0]['set'] == 'hand']),
       'recorder': score([x for x in first if x[0]['set'] == 'recorder'])}
for style in ('Swipe only', 'Left swipe', 'Index finger', 'Random'):
    out['prompted: ' + style] = score([x for x in first if x[0]['set'] == 'prompted' and x[0]['style'] == style])
json.dump(out, open(out_path, 'w'), indent=1)
for k, v in out.items():
    print(f"{k:<28} {v}")
