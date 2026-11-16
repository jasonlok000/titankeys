#!/usr/bin/env python3
"""History for the #38 and #39 pilots: for each first try in a labelled log, what the owner had kept BEFORE it, from the
messages of other fields that started earlier: how often each word was kept (only words that start with the right word's
first letter, as no other word can be a completion) and which words followed the word before the cursor (learned pairs).
The field the word is typed in is left out, so the word can't leak into its own history. Private: stays on this PC.
Usage: history.py attempts.jsonl corpus.jsonl messages.jsonl out.jsonl"""
import json
import re
import sys
from collections import Counter, defaultdict

attempts_path, corpus_path, messages_path, out_path = sys.argv[1:5]
WORD = re.compile(r"[A-Za-z]+(?:['’][A-Za-z]+)?")


def words(text):
    return [w.lower().replace('’', "'") for w in WORD.findall(text)]


attempts = {}
for line in open(attempts_path, encoding='utf-8'):
    r = json.loads(line)
    attempts[r['id']] = (r['wall'], r['field'])
messages = sorted((json.loads(l) for l in open(messages_path, encoding='utf-8')), key=lambda m: m['wall'])
n = 0
with open(out_path, 'w', encoding='utf-8', newline='\n') as out:
    for line in open(corpus_path, encoding='utf-8'):
        e = json.loads(line)
        kind, num = e['id'].split('-')
        if not e['first'] or kind not in ('log', 'hand'):
            continue
        wall, field = attempts[int(num)]
        expected = e['expected'].lower().replace('’', "'")
        before = words(e['context'])
        prev = before[-1] if before else ''
        counts, pairs = Counter(), Counter()
        for m in messages:
            if m['wall'] >= wall:
                break
            if m['field'] == field:
                continue
            ws = words(m['text'])
            for i, w in enumerate(ws):
                if expected and w[:1] == expected[:1]:
                    counts[w] += 1
                if prev and i and ws[i - 1] == prev:
                    pairs[w] += 1
        out.write(json.dumps({'id': e['id'], 'prev': prev, 'counts': dict(counts), 'pairs': dict(pairs)}) + '\n')
        n += 1
print('history rows', n)
