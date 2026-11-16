#!/usr/bin/env python3
"""Issue #83: what the joining rule as built changes, pair by pair, against the code before it.

Takes JoinReplay's output for the old Swipe.java and for the new one. A pair the keyboard joined is cut when the old
code called it the same stroke and the new code doesn't. A pair it kept apart is joined when only the new code calls it
the same stroke (those join at once, without the 150 ms check). Every other pair keeps what the log recorded.
Writes the counts, numbers only, and a fixture of the pairs that matter for tests/swipe (the tails only: see fixture()).
Usage: confirm.py pairs.jsonl pairs-points.txt old-rule.txt new-rule.txt out.json [fixture.txt]
"""
import json
import sys
from collections import Counter

pairs_path, points_path, old_path, new_path, out_path = sys.argv[1:6]
fixture_path = sys.argv[6] if len(sys.argv) > 6 else None


def load(path):
    out = {}
    for line in open(path):
        pair, could, same = line.split()
        out[pair] = (could == 'true', same == 'true')
    return out


rows = [json.loads(l) for l in open(pairs_path)]
old, new = load(old_path), load(new_path)
res = Counter()
episodes = set()
changed = []
for r in rows:
    o, n = old[r['pair']][1], new[r['pair']][1]
    logged, lab = r['joined'], r['label']
    joins = logged
    if logged and o and not n:
        joins = False
    elif not logged and n and not o:
        joins = True
    if joins != logged:
        changed.append(r['pair'])
    era = '1.2' if r['version'] == '1.2.0' else '2.x'
    prompted = r['set'] == 'prompted'
    if logged:
        res[lab + 's'] += 1
        if not joins:
            res[lab + 's cut'] += 1
            res[f'{lab}s cut {era}'] += 1
            if lab == 'wrong join':
                episodes.add(r['episode'])
            res['headline'] += prompted if lab == 'wrong join' else -prompted if lab == 'right join' else 0
    else:
        res[lab + 's'] += 1
        if joins:
            res[lab + 's joined'] += 1
            res['headline'] += prompted if lab == 'missed join' else -prompted if lab == 'right non-join' else 0
res['wrong join episodes cut'] = len(episodes)
res['wrong join episodes'] = len({r['episode'] for r in rows if r['label'] == 'wrong join'})
summary = {'pairs': len(rows), 'pairs whose decision changes': len(changed), 'counts': dict(sorted(res.items()))}
json.dump(summary, open(out_path, 'w'), indent=1)
print(json.dumps(summary, indent=1))


def fixture(path):
    """The pairs either version could join, every logged join and every missed join, cut to the last 100 ms of the first
    swipe and the first 100 ms of the second: all the joining checks look at, and too little of a path to spell a word."""
    lines = {}
    for line in open(points_path):
        f = line.split()
        lines[f[0]] = f
    by_pair = {r['pair']: r for r in rows}
    out = ['# #83 join fixture: label logged oldSame newSame gap endContact na nb, then x y t for each point.',
           '# Tails of real swipe pairs from the saved logs (see docs/studies/83-joins.md). Built by confirm.py.']
    for pair, f in lines.items():
        r = by_pair[pair]
        if not (r['joined'] or r['label'] == 'missed join' or old[pair][0] or new[pair][0]):
            continue
        gap, end_contact, na, nb = f[1], f[2], int(f[3]), int(f[4])
        pts = [tuple(map(float, f[5 + i * 3:8 + i * 3])) for i in range(na + nb)]
        a, b = pts[:na], pts[na:]
        a_end = a[-1][2]
        a = [p for p in a if a_end - p[2] <= 100] or a[-1:]
        if len(a) < 2:
            a = pts[max(0, na - 2):na]
        b = [p for p in b if p[2] - b[0][2] <= 100] or b[:1]
        if len(b) < 2:
            b = pts[na:na + 2]
        t0 = a[0][2]
        a = [(x, y, t - t0) for x, y, t in a]
        tb = b[0][2]
        b = [(x, y, t - tb) for x, y, t in b]
        label = r['label'].replace(' ', '-')
        head = [label, 'joined' if r['joined'] else 'apart', str(old[pair][1]).lower(), str(new[pair][1]).lower(),
                gap, end_contact, str(len(a)), str(len(b))]
        out.append(' '.join(head + [f'{x:.5f} {y:.5f} {t:.1f}' for x, y, t in a + b]))
    open(path, 'w').write('\n'.join(out) + '\n')
    print('fixture lines', len(out) - 2)


if fixture_path:
    fixture(fixture_path)
