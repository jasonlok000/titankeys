#!/usr/bin/env python3
"""Scores a replay of the corpus through the current engine.
Usage: score_replay.py corpus.jsonl replay.jsonl out.json [--words private-table.json]"""
import json
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

corpus_path, replay_path, out_path = sys.argv[1:4]
corpus = {}
for line in open(corpus_path, encoding='utf-8'):
    e = json.loads(line)
    corpus[e['id']] = e
replay = {}
for line in open(replay_path, encoding='utf-8'):
    e = json.loads(line)
    replay[e['id']] = e


def key(w):
    return R.norm(w).replace("'", '')


def pct(a, b):
    return round(100 * a / b, 1) if b else None


rows = []
for rid, e in replay.items():
    c = corpus.get(rid)
    if not c:
        continue
    exp = key(c['expected'])
    top = [key(w) for w in e['top']]
    live = [key(w) for w in c['live']]
    rows.append({'id': rid, 'set': c['set'], 'style': c['style'], 'slot': c['slot'], 'first': c['first'], 'expected': exp, 'top': top, 'live': live,
                 'ok1': top[:1] == [exp], 'ok3': exp in top, 'live1': live[:1] == [exp], 'live3': exp in live, 'rank': e['rank'], 'deepCount': e['deepCount'],
                 'millis': e['millis'], 'words': e['words'], 'deep': e['deep'], 'foundCtc': e.get('foundCtc'), 'foundLm': e.get('foundLm'), 'scores': e['scores']})

out = {'strokes': len(rows)}


def table(name, items):
    n = len(items)
    t = {'n': n, 'top1': sum(i['ok1'] for i in items), 'top3': sum(i['ok3'] for i in items), 'live_top1': sum(i['live1'] for i in items), 'live_top3': sum(i['live3'] for i in items)}
    t['top1_pct'], t['top3_pct'], t['live_top1_pct'], t['live_top3_pct'] = pct(t['top1'], n), pct(t['top3'], n), pct(t['live_top1'], n), pct(t['live_top3'], n)
    out[name] = t
    print(f"  {name:<34} n={n:<4} current top1 {t['top1_pct']}%  top3 {t['top3_pct']}%   | recorded live top1 {t['live_top1_pct']}% top3 {t['live_top3_pct']}%")
    return t


print('=== Current engine replay: first try at each word position ===')
for setname in ('prompted', 'hand', 'recorder'):
    first = [r for r in rows if r['set'] == setname and r['first']]
    if not first:
        continue
    table(setname + ' first tries', first)
    if setname == 'prompted':
        for style in ('Swipe only', 'Left swipe', 'Index finger', 'Random'):
            table('  ' + style, [r for r in first if r['style'] == style])
        tune = [r for r in first if int(r['slot']) % 3 != 2]
        hold = [r for r in first if int(r['slot']) % 3 == 2]
        table('  tune split (slot % 3 != 2)', tune)
        table('  held-back split (slot % 3 == 2)', hold)
    if setname == 'recorder':
        table('  normal pace', [r for r in first if r['style'] == 'normal'])
        table('  rushed pace', [r for r in first if r['style'] == 'rushed'])

print('\n=== Every attempt, including retries ===')
for setname in ('prompted', 'hand'):
    items = [r for r in rows if r['set'] == setname]
    if items:
        table(setname + ' all attempts', items)

print('\n=== Where the right word sits, first tries (decoder list of up to 100) ===')
out['depth'] = {}
for setname in ('prompted', 'hand', 'recorder'):
    first = [r for r in rows if r['set'] == setname and r['first']]
    if not first:
        continue
    d = Counter()
    for r in first:
        rank = r['rank']
        d['rank 1' if rank == 1 else 'rank 2-3' if 1 < rank <= 3 else 'rank 4-16' if 3 < rank <= 16 else 'rank 17-100' if rank > 16 else 'not found'] += 1
    missed = [r for r in first if not r['ok1']]
    dm = Counter()
    for r in missed:
        rank = r['rank']
        dm['rank 1' if rank == 1 else 'rank 2-3' if 1 < rank <= 3 else 'rank 4-16' if 3 < rank <= 16 else 'rank 17-100' if rank > 16 else 'not found'] += 1
    out['depth'][setname] = {'all': dict(d), 'misses': dict(dm), 'n_first': len(first), 'n_missed': len(missed)}
    print(f' {setname}: all first tries {dict(d)}')
    print(f' {setname}: on misses        {dict(dm)}')

print('\n=== Live vs replay differences, prompted first tries (build 12:07 vs current) ===')
first = [r for r in rows if r['set'] == 'prompted' and r['first']]
wins = [r for r in first if r['ok1'] and not r['live1']]
losses = [r for r in first if r['live1'] and not r['ok1']]
out['live_vs_current'] = {'improved': len(wins), 'regressed': len(losses), 'improved_words': [r['expected'] for r in wins], 'regressed_words': [r['expected'] for r in losses]}
print(' improved', len(wins), [r['expected'] for r in wins])
print(' regressed', len(losses), [(r['expected'], r['top'][:1]) for r in losses])

misses = [r for r in first if not r['ok1']]
print('\n=== Prompted misses by the path score of the expected word (replay) ===')
categories = Counter()
for r in misses:
    if r['rank'] < 1:
        categories['not found by the decoder'] += 1
        continue
    if r['foundCtc'] is not None and r['deep'] and r['deep'][0][2] is not None:
        gap = r['deep'][0][2] - r['foundCtc']
        categories['in decoder list, right word has as good a path (within 1)' if gap <= 1 else 'in decoder list, path clearly worse than winner'] += 1
print(dict(categories))
out['miss_categories'] = dict(categories)

ms = sorted(r['millis'] for r in rows)
out['latency_ms'] = {'p50': ms[len(ms) // 2], 'p90': ms[int(len(ms) * .9)], 'max': ms[-1]}
print('\nLatency of the engine on the phone:', out['latency_ms'])

deep = [r for r in rows if r['set'] == 'prompted' and r['first'] and r['rank'] > 0]
print('\n=== Language-model role in misses ===')
out['context'] = {}
bywords = Counter()
for r in first:
    bywords[min(r['words'], 4)] += 1
for bucket in (0, 1, 2, 3, 4):
    items = [r for r in first if min(r['words'], 4) == bucket]
    if items:
        out['context'][str(bucket)] = {'n': len(items), 'top1': pct(sum(i['ok1'] for i in items), len(items))}
        print(f' words of context {bucket}{"+" if bucket == 4 else ""}: n={len(items)} first-try {pct(sum(i["ok1"] for i in items), len(items))}%')
json.dump(out, open(out_path, 'w'), indent=1)
