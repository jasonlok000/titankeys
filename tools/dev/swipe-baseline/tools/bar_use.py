#!/usr/bin/env python3
"""How the owner uses the bar (Master's request, Nov 11). Counts only.
- Taps ('pick'): by slot (index 0 middle, 1 left, 2 right, as Strip.WORD_ORDER shows them) and by moment: after a swipe (the
  last word came from a swipe and nothing was typed since), mid-word (the text before the cursor ends in letters), or next
  word (it ends in a space or mark).
- Words typed with keys and ended by Space or a mark with no tap.
- Autocorrect fixes at a word's end, and how many were undone (autocorrectundo, Backspace right after).
Overlapping exports are read once. Usage: bar_use.py raw-log.jsonl..."""
import json
import sys
from collections import Counter

seen = set()
picks = Counter()
ended = Counter()
fixes = Counter()
for path in sys.argv[1:]:
    boot = None
    before = ''
    last = None          # 'swipe' or 'key': where the last word came from
    for line in open(path, encoding='utf-8'):
        try:
            d = json.loads(line)
        except ValueError:
            continue
        k = d.get('k')
        if k == 'header':
            boot = round((d['wall'] - d['t']) / 60000)
            continue
        key = (boot, d.get('t'), k, d.get('cause'), d.get('via'), d.get('c'), d.get('index'))
        if key in seen:
            continue
        seen.add(key)
        if k == 'sel':
            before = d.get('before') or ''
        elif k == 'pick':
            if last == 'swipe':
                moment = 'after a swipe'
            elif before[-1:].isalpha() or before[-1:] in "'’":
                moment = 'mid-word typed'
            else:
                moment = 'next word'
            slot = {0: 'middle', 1: 'left', 2: 'right'}.get(d.get('index'), 'other')
            picks[(moment, slot)] += 1
            last = 'pick'
        elif k == 'text':
            cause, via, ins = d.get('cause'), d.get('via'), d.get('ins') or ''
            if cause == 'commit' and via == 'swipe':
                last = 'swipe'
            elif cause == 'commit' and via == 'key' and ins[-1:].isalpha():
                last = 'key'
            elif cause == 'commit' and (via == 'space' or ins in ('.', ',', '!', '?', ';', ':')):
                if last == 'key':
                    ended['typed word ended by ' + ('Space' if via == 'space' else 'a mark') + ', no tap'] += 1
                if last != 'swipe':
                    last = None
            elif cause in ('autocorrect', 'autocorrectundo'):
                fixes[cause] += 1

print('taps by moment and slot:')
for moment in ('mid-word typed', 'after a swipe', 'next word'):
    row = {s: picks[(moment, s)] for s in ('left', 'middle', 'right', 'other')}
    print(f'  {moment}: {row}, total {sum(row.values())}')
print('words ended without a tap:', dict(ended))
print('autocorrect fixes:', fixes['autocorrect'], 'undone right after:', fixes['autocorrectundo'])
