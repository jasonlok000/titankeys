#!/usr/bin/env python3
"""Private: the events around each moment a given word was left before a space. Usage: word_moments.py word raw-log..."""
import json, re, sys
target = sys.argv[1]
seen = set()
for path in sys.argv[2:]:
    events = []
    boot = None
    for line in open(path, encoding='utf-8'):
        d = json.loads(line)
        if d['k'] == 'header':
            boot = round((d['wall'] - d['t']) / 60000)
        events.append((boot, d))
    before = ''
    for i, (boot, d) in enumerate(events):
        if d['k'] == 'sel':
            before = d.get('before') or ''
        if d['k'] == 'text' and d.get('cause') == 'commit' and d.get('via') == 'space':
            if re.search(r'\b' + target + r'$', before, re.I) and (boot, d['t']) not in seen:
                seen.add((boot, d['t']))
                print('====', path.split('/')[-1], d['t'])
                for b, e in events[max(0, i - 14):i + 8]:
                    if e['k'] in ('text', 'pick', 'key', 'rec', 'out'):
                        x = {k: v for k, v in e.items() if k in ('k', 'cause', 'via', 'auto', 'del', 'ins', 'c', 'd', 'type', 'word', 'words', 'index', 'offered')}
                        print('  ', json.dumps(x)[:200])
