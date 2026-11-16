#!/usr/bin/env python3
"""Lists each event kind in a raw log with its count and field names, and the value sets of short fields. No text."""
import collections
import json
import sys

counts = collections.Counter()
fields = {}
values = collections.defaultdict(collections.Counter)
for line in open(sys.argv[1], encoding='utf-8'):
    e = json.loads(line)
    k = e.get('k')
    counts[k] += 1
    fields.setdefault(k, set()).update(e.keys())
    for f in ('a', 'type', 'cause', 'd', 'g'):
        if f in e and isinstance(e[f], (int, str, bool)) and len(str(e[f])) < 20:
            values[(k, f)][e[f]] += 1
for k, n in counts.most_common():
    print(k, n, sorted(fields[k]))
for (k, f), v in sorted(values.items(), key=str):
    print(' ', k, f, dict(v.most_common(12)))
