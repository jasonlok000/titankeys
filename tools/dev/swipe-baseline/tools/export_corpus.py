#!/usr/bin/env python3
"""Writes the replayable corpus: each labelled stroke as the keyboard gave it to the engine, in letter space.
Usage: export_corpus.py private-dir recorder-session.jsonl out-file"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

P, recorder_path, OUT = sys.argv[1:4]
rows = [json.loads(l) for l in open(os.path.join(P, 'attempts.jsonl'), encoding='utf-8')]
by_id = {r['id']: r for r in rows}
STYLES = {'style:Swipe only', 'style:Left swipe', 'style:Index finger', 'style:Random'}


def gesture(points):
    points = [p for p in points if p[0] is not None and p[1] is not None]
    start = points[0][2]
    return [round(p[0], 5) for p in points], [round(p[1] * 4 / 3, 5) for p in points], [p[2] - start for p in points]


def chain_context(r):
    while r['joined_from'] is not None and r['joined_from'] in by_id:
        r = by_id[r['joined_from']]
    return r['context']


HAND_STEPS = {'step:01-normal-typing', 'step:08-taps-then-typing', 'step:12-shift-and-type', 'step:13-shift-and-swipe'}


def is_everyday(r):
    """Text the owner meant and corrected. Steps that test odd gestures or a resting finger are left out."""
    return r['block'] is None or r['block'] in HAND_STEPS


def retried_first(slot_rows):
    return min(r['id'] for r in slot_rows if r['superseded_by'] is None)


slots = {}
for r in rows:
    if r['block'] in STYLES and r['expected'] is not None and r['superseded_by'] is None:
        slots.setdefault(r['slot'], []).append(r)

count = 0
with open(OUT, 'w', encoding='utf-8') as out:
    for sid, attempts in slots.items():
        first = retried_first(attempts)
        for r in attempts:
            if not r['chain_points']:
                continue
            x, y, t = gesture(r['chain_points'])
            if len(x) < 2:
                continue
            out.write(json.dumps({'id': 'log-' + str(r['id']), 'set': 'prompted', 'style': r['block'][6:], 'slot': sid, 'first': r['id'] == first,
                                  'expected': r['expected'], 'context': chain_context(r), 'live': r['words'], 'x': x, 'y': y, 't': t}) + '\n')
            count += 1
    # Hand-corrected everyday text: the word that stayed in the field is the label.
    hand_slots = {}
    for r in rows:
        if r['superseded_by'] is None and is_everyday(r) and r['hand'] and r['hand'].replace("'", '').isalpha():
            hand_slots.setdefault(r['slot'], []).append(r)
    for sid, attempts in hand_slots.items():
        first = retried_first(attempts)
        for r in attempts:
            if not r['chain_points']:
                continue
            x, y, t = gesture(r['chain_points'])
            if len(x) < 2:
                continue
            out.write(json.dumps({'id': 'hand-' + str(r['id']), 'set': 'hand', 'style': r['block'] or 'everyday', 'slot': sid, 'first': r['id'] == first,
                                  'expected': r['hand'], 'context': chain_context(r), 'live': r['words'], 'x': x, 'y': y, 't': t}) + '\n')
            count += 1
    # The recorder session: isolated words, no context, the same set used for the earlier results.
    attempts = {}
    for line in open(recorder_path, encoding='utf-8'):
        e = json.loads(line)
        if 'format' in e:
            continue
        if 'redo' in e or 'skip' in e:
            attempts.pop(e.get('redo', e.get('skip')), None)
        else:
            attempts[e['index']] = e
    for index in sorted(attempts):
        a = attempts[index]
        if a.get('pace') == 'typed':
            continue
        # Only the swipe(s) of the last recognition make up the word.
        pieces, current, last = [], None, None
        contacts = [ev for ev in a['events'] if ev['k'] == 'm']
        strokes, active = [], []
        for ev in contacts:
            if ev['a'] == 0:
                active = list(ev['s'])
            elif ev['a'] in (1, 2):
                active += ev['s']
            if ev['a'] == 1:
                strokes.append(active)
                active = []
        outs = [ev for ev in a['events'] if ev['k'] == 'out' and ev['type'] == 'swipe']
        recs = [ev for ev in a['events'] if ev['k'] == 'rec']
        if not outs or not recs:
            continue
        picked = []
        for out_event, rec in zip(outs, recs):
            pts = [p for s in strokes if s and s[0][2] >= out_event['start'] - 2 and s[-1][2] <= out_event['end'] + 2 for p in s]
            if len(pts) > out_event['n']:
                pts = pts[-out_event['n']:]
            if rec['resumed'] and picked:
                picked += pts
            else:
                picked = pts
        if len(picked) < 2:
            continue
        x, y, t = gesture(picked)
        out.write(json.dumps({'id': 'rec-' + str(index), 'set': 'recorder', 'style': a['pace'], 'slot': index, 'first': True, 'expected': a['prompt'],
                              'context': '', 'live': a['top'], 'x': x, 'y': y, 't': t}) + '\n')
        count += 1
print('corpus lines', count)
