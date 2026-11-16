#!/usr/bin/env python3
"""Shows the shape of the resting-touch recording: field names, the reading values and one point's layout. No text."""
import collections
import json
import sys

rows = [json.loads(l) for l in open(sys.argv[1], encoding='utf-8')]
print('header keys', sorted(rows[0].keys()))
print('row keys', sorted(rows[1].keys()))
print('readings', collections.Counter(str(r.get('reading')) for r in rows[1:]).most_common(10))
p = rows[1]['points'][:2]
print('first points', p)
