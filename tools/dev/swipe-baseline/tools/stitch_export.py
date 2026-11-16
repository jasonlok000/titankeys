#!/usr/bin/env python3
"""Builds strokes joined to the stroke just after or before them, to measure what missed rejoins cost.
Usage: stitch_export.py private-dir out.jsonl"""
import json
import os
import sys

P, out_path = sys.argv[1:3]
rows = sorted((json.loads(l) for l in open(os.path.join(P, 'attempts.jsonl'), encoding='utf-8')), key=lambda r: r['start'])
corpus = {}
for line in open(os.path.join(P, 'corpus.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    if e['first'] and e['set'] in ('prompted', 'hand'):
        corpus[e['id']] = e
index = {r['id']: i for i, r in enumerate(rows)}


def gesture(points):
    points = [p for p in points if p[0] is not None and p[1] is not None]
    start = points[0][2]
    return [round(p[0], 5) for p in points], [round(p[1] * 4 / 3, 5) for p in points], [p[2] - start for p in points]


NEXT_MS, PREV_MS = 400, 250
n = 0
with open(out_path, 'w', encoding='utf-8') as out:
    for cid, e in corpus.items():
        rid = int(cid.split('-')[1])
        r = rows[index[rid]]
        i = index[rid]
        if i + 1 < len(rows):
            nx = rows[i + 1]
            gap = nx['start'] - r['end']
            if nx['field'] == r['field'] and 0 <= gap <= NEXT_MS and nx['joined_from'] != r['id'] and nx['points']:
                x, y, t = gesture(r['chain_points'] + nx['points'])
                if t[-1] <= 9000:
                    out.write(json.dumps({'id': 'F|' + cid + '|' + str(gap), 'set': e['set'], 'style': e['style'], 'slot': e['slot'], 'first': True, 'expected': e['expected'], 'context': e['context'], 'live': e['live'], 'x': x, 'y': y, 't': t}) + '\n')
                    n += 1
        if i:
            pv = rows[i - 1]
            gap = r['start'] - pv['end']
            if pv['field'] == r['field'] and 0 <= gap <= PREV_MS and r['joined_from'] != pv['id'] and pv['chain_points']:
                x, y, t = gesture(pv['chain_points'] + r['chain_points'])
                if t[-1] <= 9000:
                    out.write(json.dumps({'id': 'B|' + cid + '|' + str(gap), 'set': e['set'], 'style': e['style'], 'slot': e['slot'], 'first': True, 'expected': e['expected'], 'context': pv['context'], 'live': e['live'], 'x': x, 'y': y, 't': t}) + '\n')
                    n += 1
print('stitched strokes', n)
