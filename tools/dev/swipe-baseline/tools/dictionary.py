#!/usr/bin/env python3
"""How much of the problem is the dictionary. Prints counts only, with no typed words.
Usage: dictionary.py corpus replay english.combined vocab.txt out.json"""
import json
import os
import re
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

corpus_path, replay_path, dict_path, vocab_path, out_path = sys.argv[1:6]
entries = {}
flags_seen = Counter()
for line in open(dict_path, encoding='utf-8'):
    m = re.match(r' word=([^,]+),f=(\d+)(,?.*)', line)
    if not m:
        continue
    raw, f, flags = m.group(1), int(m.group(2)), m.group(3).strip(',')
    w = raw.lower().replace("'", '').replace('’', '')
    for flag in re.findall(r'(\w+)=', flags) or ([flags] if flags else []):
        flags_seen[flag] += 1
    e = entries.setdefault(w, {'f': 0, 'upper': False, 'flags': set()})
    e['f'] = max(e['f'], f)
    e['upper'] = e['upper'] or raw[:1].isupper()
    e['flags'].update(re.findall(r'[a-z_]+', flags))
vocab = {l.strip().lower().replace("'", '') for l in open(vocab_path, encoding='utf-8') if l.strip()}
print('dictionary words', len(entries), 'over f>=100 (outline set)', sum(e['f'] >= 100 for e in entries.values()),
      'names (capitalised)', sum(e['upper'] for e in entries.values()), 'flags', flags_seen.most_common(8))
print('context model vocabulary', len(vocab))
freq_bins = Counter(min(e['f'] // 25 * 25, 225) for e in entries.values())
print('dictionary frequency histogram', sorted(freq_bins.items()))

corpus = {}
for line in open(corpus_path, encoding='utf-8'):
    e = json.loads(line)
    corpus[e['id']] = e
out = {'dictionary_words': len(entries), 'outline_words': sum(e['f'] >= 100 for e in entries.values()), 'context_vocab': len(vocab)}


def key(w):
    return R.norm(w).replace("'", '')


rows = []
for line in open(replay_path, encoding='utf-8'):
    e = json.loads(line)
    c = corpus.get(e['id'])
    if not c or not c['first']:
        continue
    exp = key(c['expected'])
    d = entries.get(exp)
    rows.append({'set': c['set'], 'word': exp, 'ok1': [key(w) for w in e['top']][:1] == [exp], 'ok3': exp in [key(w) for w in e['top']], 'rank': e['rank'],
                 'in_dict': d is not None, 'f': d['f'] if d else -1, 'outline': bool(d and d['f'] >= 100), 'vocab': exp in vocab})

for setname in ('prompted', 'hand', 'recorder'):
    items = [r for r in rows if r['set'] == setname]
    n = len(items)
    misses = [r for r in items if not r['ok1']]
    notfound = [r for r in items if r['rank'] < 1]
    s = {'n': n, 'in_dictionary': sum(r['in_dict'] for r in items), 'not_in_dictionary': sum(not r['in_dict'] for r in items), 'in_outline_set': sum(r['outline'] for r in items), 'in_context_vocab': sum(r['vocab'] for r in items),
         'misses': len(misses), 'misses_not_in_dictionary': sum(not r['in_dict'] for r in misses), 'misses_not_outlined': sum(not r['outline'] for r in misses), 'misses_not_in_context_vocab': sum(not r['vocab'] for r in misses),
         'decoder_never_listed': len(notfound), 'never_listed_not_in_dictionary': sum(not r['in_dict'] for r in notfound)}
    freq = {}
    for label, a, b in (('f>=200', 200, 256), ('150-199', 150, 199), ('100-149', 100, 149), ('1-99', 0, 99)):
        band = [r for r in items if r['in_dict'] and a <= r['f'] <= b]
        freq[label] = {'n': len(band), 'first_pct': round(100 * sum(r['ok1'] for r in band) / len(band), 1) if band else None, 'never_listed_pct': round(100 * sum(r['rank'] < 1 for r in band) / len(band), 1) if band else None}
    oov = [r for r in items if not r['in_dict']]
    freq['not in dictionary'] = {'n': len(oov), 'first_pct': round(100 * sum(r['ok1'] for r in oov) / len(oov), 1) if oov else None, 'never_listed_pct': round(100 * sum(r['rank'] < 1 for r in oov) / len(oov), 1) if oov else None}
    s['by_frequency'] = freq
    repeats = Counter(r['word'] for r in misses)
    s['words_missed_twice_or_more'] = sum(1 for w, c in repeats.items() if c >= 2)
    s['misses_on_words_missed_twice_or_more'] = sum(c for w, c in repeats.items() if c >= 2)
    out[setname] = s
    print(setname, json.dumps(s))
json.dump(out, open(out_path, 'w'), indent=1)
