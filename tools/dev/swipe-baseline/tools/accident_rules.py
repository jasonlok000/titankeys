#!/usr/bin/env python3
"""Tries simple rules for catching accidental strokes, with the known test windows removed. Counts only.
Usage: accident_rules.py private-dir replay corpus out.json"""
import datetime
import json
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

P, replay_path, corpus_path, out_path = sys.argv[1:5]
rows = [json.loads(l) for l in open(os.path.join(P, 'attempts.jsonl'), encoding='utf-8')]
TESTS = [('10:12:00', '11:05:00'), ('11:07:16', '11:07:18'), ('11:12:09', '11:13:04'), ('11:16:20', '11:16:48'), ('11:24:12', '11:24:20'), ('14:31:16', '14:40:59')]


def clock(r):
    return datetime.datetime.fromtimestamp(r['wall'] / 1000).strftime('%H:%M:%S')


def is_test(r):
    t = clock(r)
    return any(a <= t <= b for a, b in TESTS)


def info(r):
    pts = R.key_units(r['chain_points'])
    length = R.path_length(pts) if len(pts) > 1 else 0
    dur = pts[-1][2] - pts[0][2] if len(pts) > 1 else 0
    s = r['scores'] or []
    margin = s[0][1] - s[1][1] if len(s) > 1 else 99
    return length, dur, margin


stroke = [r for r in rows if r['superseded_by'] is None and not is_test(r)]
gone = [r for r in stroke if r['hand'] is None and any(e.startswith('backspace') for e in r['slot_events'])]
kept = [r for r in stroke if r['hand'] is not None]
prompted_ghost = [r for r in gone if r['block'] and r['block'].startswith('style')]
print('strokes outside test windows', len(stroke), 'removed-with-nothing', len(gone), 'of which prompted', len(prompted_ghost), 'kept or corrected', len(kept))
RULES = {
    'under 60 ms and under 1.5 keys': lambda L, D, M: D < 60 and L < 1.5,
    'under 100 ms and under 2 keys': lambda L, D, M: D < 100 and L < 2,
    'under 150 ms and under 2.5 keys': lambda L, D, M: D < 150 and L < 2.5,
    'under 2 keys': lambda L, D, M: L < 2,
    'score gap under 2': lambda L, D, M: M < 2,
    'score gap under 5': lambda L, D, M: M < 5,
    'under 150 ms and gap under 10': lambda L, D, M: D < 150 and M < 10,
    'under 2.5 keys and gap under 10': lambda L, D, M: L < 2.5 and M < 10,
}
out = {'n_gone': len(gone), 'n_kept': len(kept), 'rules': {}}
print(f"{'rule':<36} catches removed   also catches kept/corrected")
for name, rule in RULES.items():
    a = sum(rule(*info(r)) for r in gone)
    b = sum(rule(*info(r)) for r in kept)
    out['rules'][name] = {'caught': a, 'caught_pct': round(100 * a / len(gone), 1), 'genuine_hit': b, 'genuine_pct': round(100 * b / len(kept), 1)}
    print(f"  {name:<34} {a:>3} ({100 * a / len(gone):4.1f}%)     {b:>4} ({100 * b / len(kept):4.1f}%)")
print('\nTop 2 strokes under 60 ms of kept words, as a count by word length:')
c = Counter(len(r['hand']) for r in kept if info(r)[1] < 60 and info(r)[0] < 1.5)
print(' ', dict(c))
json.dump(out, open(out_path, 'w'), indent=1)
