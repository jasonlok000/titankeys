#!/usr/bin/env python3
"""Where the committed word was not the best guess (owner's report, Nov 10: "today's" in the middle, "today" committed).

- Swipes: the recogniser's first word ('rec' words[0], the middle of the strip) against the word the swipe put in, when
  no word was picked from the strip.
- Typed words: each automatic change at Space ('autocorrect' text events), by kind: an apostrophe added, a trailing
  "s" dropped where the word list has the "'s" spelling (today's, what's), letters changed, a split, and so on.
Each event is read once across overlapping exports. Counts and word patterns only.
Usage: commit_mismatch.py english.combined raw-log.jsonl..."""
import json
import re
import sys
from collections import Counter

combined, logs = sys.argv[1], sys.argv[2:]
words = set()
for line in open(combined, encoding='utf-8'):
    m = re.match(r' word=([^,]+),f=(\d+)', line)
    if m and m.group(2) != '0':
        words.add(m.group(1).lower())

seen = set()
swipe = Counter()
swipe_pairs = Counter()
auto_kinds = Counter()
auto_pairs = Counter()
drop_s = Counter()
for path in logs:
    boot = None
    pending = None        # the last recognition, waiting for its commit
    picked = False
    before = ''
    for line in open(path, encoding='utf-8'):
        try:
            d = json.loads(line)
        except ValueError:
            continue
        k = d.get('k')
        if k == 'header':
            boot = round((d['wall'] - d['t']) / 60000)
            continue
        key = (boot, d.get('t'), k, d.get('cause'), d.get('via'), d.get('c'))
        if key in seen:
            continue
        seen.add(key)
        if k == 'sel':
            before = d.get('before') or ''
        elif k == 'rec':
            pending = d.get('words') or []
            picked = False
        elif k == 'pick':
            picked = True
        elif k == 'text' and d.get('cause') == 'commit' and d.get('via') == 'swipe' and pending:
            put = (d.get('ins') or '').strip()
            best = pending[0]
            if picked:
                swipe['picked from the strip'] += 1
            elif put == best:
                swipe['middle word'] += 1
            elif put.lower() == best.lower():
                swipe['middle word, other case'] += 1
            else:
                swipe['other than the middle word'] += 1
                swipe_pairs[(best.lower(), put.lower())] += 1
            pending = None
        elif k == 'text' and d.get('cause') == 'autocorrect':
            n = d.get('del') or 0
            typed = before[-n:] if n else ''
            fix = d.get('ins') or ''
            t, f = typed.lower().replace('’', "'"), fix.lower().replace('’', "'")
            if f.replace("'", '') == t and "'" in f:
                kind = 'apostrophe added'
            elif t.endswith('s') and f == t[:-1] and (t[:-1] + "'s") in words:
                kind = "trailing s dropped, though the list has 's"
                drop_s[(t, f)] += 1
            elif ' ' in f:
                kind = 'split into words'
            elif t == f:
                kind = 'capitals only'
            else:
                kind = 'letters changed'
            auto_kinds[kind] += 1
            auto_pairs[(kind, t, f)] += 1

print(json.dumps({
    'swipes': dict(swipe),
    'swipes_other_than_middle_top': [(a, b, n) for (a, b), n in swipe_pairs.most_common(30)],
    'autocorrect_kinds': dict(auto_kinds),
    'trailing_s_dropped': [(a, b, n) for (a, b), n in drop_s.most_common()],
    'changed_or_split': [(k, t, f, n) for (k, t, f), n in auto_pairs.most_common() if k in ('letters changed', 'split into words')],
    'apostrophe_added_top': [(t, f, n) for (k, t, f), n in auto_pairs.most_common() if k == 'apostrophe added'][:20],
}, indent=1))
