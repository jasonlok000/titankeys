#!/usr/bin/env python3
"""The owner's swipes of one name, as replayable corpus rows (private: they hold context). A swipe counts when it came
in the 20 s before the name went into the field and showed one of the words a swipe of the name turns into.
Prints only counts and the shown words.
Usage: name_corpus.py raw-log.jsonl out.jsonl NAME LOOKALIKE[,LOOKALIKE...]
NAME is the word as it should be written. The look-alikes are the lowercase words such a swipe came out as."""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

log_path, out_path, TARGET, looks = sys.argv[1:5]
LOOKS_LIKE = {w.strip().lower() for w in looks.split(',') if w.strip()}
pattern = re.compile(r'\b' + re.escape(TARGET.lower()) + r'\b', re.I)
events = R.load(log_path)
rep = R.Replayer(events)
count = 0
arrivals = []
for e in events:
    handler = getattr(rep, 'on_' + e['k'], None)
    if handler:
        handler(e)
    if e['k'] in ('text', 'start'):
        now = len(pattern.findall(rep.text()))
        if e['k'] == 'text' and now > count:
            arrivals.append(e['t'])
        count = now
rep.close_field()
chosen = []
for a in rep.attempts:
    if a.rec is None or not a.points:
        continue
    shown = [str(w).lower() for w in (a.rec.get('words') or [])]
    if not set(shown[:3]) & LOOKS_LIKE:
        continue
    if any(0 <= t - a.rec['t'] <= 20000 for t in arrivals):
        chosen.append(a)


def gesture(points):
    points = [p for p in points if p[0] is not None and p[1] is not None]
    start = points[0][2]
    return [round(p[0], 5) for p in points], [round(p[1] * 4 / 3, 5) for p in points], [p[2] - start for p in points]


with open(out_path, 'w', encoding='utf-8') as out:
    for a in chosen:
        x, y, t = gesture(a.points)
        # Marked as the owner's hand-corrected first attempts, which make_readings.py replays. The 'name-' id marks them.
        out.write(json.dumps({'id': f'name-{a.id}', 'set': 'hand', 'first': True, 'expected': TARGET, 'context': a.context,
                              'live': a.rec.get('words')[:3], 'x': x, 'y': y, 't': t}) + '\n')
        print('name stroke', a.id, 'shown', a.rec.get('words')[:3], 'points', len(x))
print('arrivals', len(arrivals), 'strokes', len(chosen))
