#!/usr/bin/env python3
"""Each time a word appears in the replayed field text, however it got there (keys, swipe, pick, paste), with the swipes
in the 20 s before it and what they showed. Prints only that word and those swipes' shown words.
Usage: word_arrivals.py raw-log.jsonl word"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

log_path, target = sys.argv[1], sys.argv[2]
pattern = re.compile(r"\b" + re.escape(target) + r"\b", re.I)
events = R.load(log_path)
rep = R.Replayer(events)
recs = []
count = 0
arrivals = 0
for e in events:
    handler = getattr(rep, 'on_' + e['k'], None)
    if handler:
        handler(e)
    if e['k'] == 'rec':
        recs.append(e)
    if e['k'] in ('text', 'start'):
        found = pattern.findall(rep.text())
        now = len(found)
        if e['k'] == 'text' and now > count:
            arrivals += 1
            near = [r for r in recs if 0 <= e['t'] - r['t'] <= 20000]
            spelt = found[-1]
            print(f"#{arrivals} as '{spelt}' via {e.get('via')} ({e.get('cause')}); swipes in the 20 s before: {len(near)}")
            for r in near[-5:]:
                print('   shown', (r.get('words') or [])[:5])
        count = now
print('arrivals:', arrivals)
