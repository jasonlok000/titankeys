#!/usr/bin/env python3
"""Private: each word typed with keys and ended with Space, with the text before it, from a raw log, for the bar replay.
Writes "context<TAB>word" lines to a private file. Usage: space_moments.py raw-log.jsonl out.tsv"""
import json
import re
import sys

before = ''
typed_by_keys = False
out = open(sys.argv[2], 'w', encoding='utf-8')
n = 0
for line in open(sys.argv[1], encoding='utf-8'):
    d = json.loads(line)
    k = d.get('k')
    if k == 'sel':
        before = d.get('before') or ''
    elif k == 'text':
        if d.get('cause') == 'commit' and d.get('via') == 'key' and (d.get('ins') or '')[-1:].isalpha():
            typed_by_keys = True
        elif d.get('cause') == 'commit' and d.get('via') == 'swipe':
            typed_by_keys = False
    elif k == 'key' and d.get('d') and d.get('c') == 62 and d.get('repeat', 0) == 0:
        m = re.search(r"([A-Za-z][A-Za-z'’]*)$", before)
        if m and typed_by_keys:
            context = re.sub(r'[\t\n]', ' ', before[:m.start()])[-80:]
            out.write(f"{context}\t{m.group(1)}\n")
            n += 1
        typed_by_keys = False
print(n, 'moments')
