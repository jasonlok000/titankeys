#!/usr/bin/env python3
"""Finds one word in a raw log without printing other text: how it got into the field (swipe, pick, keys), which swipes came
out as something else just before it was typed, and those swipes' shown words. Usage: word_hunt.py raw-log.jsonl word"""
import json
import re
import sys

log_path, target = sys.argv[1], sys.argv[2].lower()
events = [json.loads(l) for l in open(log_path, encoding='utf-8')]
word = re.compile(r"\b" + re.escape(target) + r"\b", re.I)
recs = [(e['t'], e) for e in events if e.get('k') == 'rec']
picks = [(e['t'], e) for e in events if e.get('k') == 'pick']
found = 0
for i, e in enumerate(events):
    if e.get('k') != 'text' or not e.get('ins') or not word.search(e['ins']):
        continue
    found += 1
    t = e['t']
    # The swipes in the 20 s before: their shown words, so a miss and what beat it show up.
    before = [r for rt, r in recs if 0 <= t - rt <= 20000]
    print(f"#{found} via {e.get('via')} cause {e.get('cause')} auto {e.get('auto')}, swipes in the 20 s before: {len(before)}")
    for r in before[-4:]:
        shown = [w for w in (r.get('words') or [])][:5]
        print('   swipe shown', shown, 'contains target' if any(target == str(w).lower() for w in r.get('words') or []) else '')
print('times the word went into the field:', found)
