#!/usr/bin/env python3
"""The app version and delete-flick numbers in each raw log's headers. Usage: log_tuning.py raw-log.jsonl..."""
import json
import sys
from collections import Counter

for path in sys.argv[1:]:
    seen = Counter()
    for line in open(path, encoding='utf-8'):
        if '"k":"header"' not in line:
            continue
        d = json.loads(line)
        t = d.get('tuning', {})
        seen[(d.get('atlas'), t.get('innerEdge'), t.get('rightEdge'), t.get('middleRowTop'), t.get('edgeEndTop'), t.get('innerTilt'))] += 1
    print(path, dict(seen))
