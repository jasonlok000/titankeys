#!/usr/bin/env python3
"""A stand-in for the owner's saved words, built as the keyboard learns them: each word outside the dictionary that the
final field texts hold at least twice, with LanguageHabits' frequency for its uses. Private output (word<TAB>frequency).
Prints only counts. Usage: saved_list.py raw-log.jsonl english.combined out.tsv [extra words...]"""
import collections
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

log_path, dictionary_path, out_path = sys.argv[1:4]
extra = [w.lower() for w in sys.argv[4:]]
known = set()
for line in open(dictionary_path, encoding='utf-8'):
    m = re.match(r'^ word=([^,]+),', line)
    if m:
        known.add(m.group(1).lower())
events = R.load(log_path)
rep = R.Replayer(events)
texts = []
for e in events:
    if e['k'] == 'start':
        texts.append(rep.text())
    handler = getattr(rep, 'on_' + e['k'], None)
    if handler:
        handler(e)
texts.append(rep.text())
uses = collections.Counter()
for text in texts:
    for word in re.findall(r"[A-Za-z]+(?:'[A-Za-z]+)*", text.replace('’', "'")):
        w = word.lower()
        if 2 <= len(w.replace("'", '')) <= 24 and w not in known:
            uses[w] += 1
saved = {w: n for w, n in uses.items() if n >= 2}
for w in extra:
    saved[w] = max(saved.get(w, 0), 2)
with open(out_path, 'w', encoding='utf-8') as out:
    for w, n in sorted(saved.items()):
        support = min(16, n)
        out.write(f"{w}\t{min(255, 96 + min(96, 8 + max(0, support - 2) * 8))}\n")
print('fields', len(texts), 'words outside the dictionary', len(uses), 'saved stand-in', len(saved))
