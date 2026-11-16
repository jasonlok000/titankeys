#!/usr/bin/env python3
"""Second pass: individual failures, per-style key offsets, lost touches and how strokes break.
Usage: micro.py private-dir raw-log out-dir"""
import json
import os
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

P, log_path, OUT = sys.argv[1:4]
rows = {r['id']: r for r in (json.loads(l) for l in open(os.path.join(P, 'attempts.jsonl'), encoding='utf-8'))}
words = json.load(open(os.path.join(P, 'words.json')))
extras = json.load(open(os.path.join(P, 'extras.json')))
M = {}


def key_at(x, y):
    """The nearest letter to a point in key units."""
    return min(R.KEY, key=lambda c: (R.KEY[c][0] - x) ** 2 + (R.KEY[c][1] - y) ** 2)


def trace(points):
    """Letters the path dwells nearest to, compressed."""
    out = []
    for x, y, t in points:
        c = key_at(x, y)
        if not out or out[-1] != c:
            out.append(c)
    return ''.join(out)


print('=== Failed first tries, one per line ===')
print('expected  got       top3                       class                                  pieces  durms  len  startkey>endkey  trace')
rows_out = []
for w in sorted(words, key=lambda x: (x['ok1'], x['gclass'])):
    r = rows[w['id']]
    pts = R.key_units(r['chain_points'])
    if not pts:
        continue
    item = dict(expected=w['expected'], got=w['got'], tops=w['tops'], cls=w['gclass'], engine=w['engine_class'], pieces=w['pieces'], dur=w['g'].get('dur'), length=round(w['g'].get('len', 0), 1),
                ideal=round(w['g'].get('ideal', 0), 1), start=[round(v, 2) for v in pts[0][:2]], end=[round(v, 2) for v in pts[-1][:2]], trace=trace(pts), reach=[round(v, 2) for v in w['g'].get('reach', [])],
                style=w['style'][6:], ok1=w['ok1'], n=w['g'].get('n'))
    rows_out.append(item)
    if not w['ok1']:
        print(f"{w['expected']:<9} {w['got']:<9} {','.join(w['tops']):<26} {w['gclass'][:36]:<37} {w['pieces']} {item['dur']:>6} {item['length']:>5}/{item['ideal']:<5} {key_at(*pts[0][:2])}>{key_at(*pts[-1][:2])}  {item['trace'][:44]}")
json.dump(rows_out, open(os.path.join(P, 'word_detail.json'), 'w'))

# ---- bias by row and style -----------------------------------------------------------------------------------------
print('\n=== Start and end offsets by style and row (dx dy, mean, keys) ===')
styles = ['Swipe only', 'Left swipe', 'Index finger', 'Random']
M['offsets'] = {}
for name in ('start', 'end'):
    key = name + '_err'
    print(name)
    for s in styles:
        lines = []
        M['offsets'].setdefault(name, {}).setdefault(s, {})
        for ri, keys in enumerate(R.ROWS):
            items = [w for w in words if w['style'][6:] == s and w['len'] >= 2 and key in w['g'] and ((w['expected'][0] if name == 'start' else w['expected'][-1]) in keys)]
            if items:
                dx = R.mean([w['g'][key][0] for w in items])
                dy = R.mean([w['g'][key][1] for w in items])
                lines.append(f"row{ri+1}: {dx:+.2f},{dy:+.2f} (n={len(items)})")
                M['offsets'][name][s]['row' + str(ri + 1)] = {'dx': round(dx, 2), 'dy': round(dy, 2), 'n': len(items)}
        print(f'  {s:<14}' + '   '.join(lines))

# x as a function of column for first points
print('\nStart x error by column (all prompted swipes): column dx n')
per = defaultdict(list)
for w in words:
    if w['len'] >= 2 and 'start_err' in w['g']:
        c = w['expected'][0]
        per[(R.KEY[c][1], R.KEY[c][0])].append(w['g']['start_err'][0])
M['start_dx_by_key'] = {}
for k in sorted(per):
    print(f'   row {k[0]:.1f} col {k[1]:.1f}: {R.mean(per[k]):+.2f} n={len(per[k])}')

# ---- how often the touchpad clips at the top edge --------------------------------------------------------------------
all_points = [p for r in rows.values() for p in r['points'] if p[0] is not None]
top_edge = min(p[1] for p in all_points)
clip = sum(1 for p in all_points if p[1] <= top_edge + .003)
M['top_edge'] = {'y_min': top_edge, 'share_of_points_at_edge': round(100 * clip / len(all_points), 1)}
row_points = Counter('top' if p[1] < .25 else 'middle' if p[1] < .5 else 'bottom' if p[1] < .75 else 'bottom row' for p in all_points)
M['points_by_row'] = dict(row_points)
print('\nTop edge', M['top_edge'], M['points_by_row'])
starts = [rows[w['id']]['chain_points'][0] for w in words if rows[w['id']]['chain_points']]
print('  first sample y for top-row-start words: distribution')
ys = [p[1] * 4 for p in starts]

# ---- losing the finger --------------------------------------------------------------------------------------------
print('\n=== Strokes that broke ===')
pieces = Counter()
for r in rows.values():
    if r['block'] and r['block'].startswith('style') and r['superseded_by'] is None:
        pieces[len(r['chain'])] += 1
M['chain_lengths'] = dict(pieces)
print('Chain lengths (pieces per word) in prompted blocks:', dict(pieces))
ends = []
for r in rows.values():
    if r['superseded_by'] is None or not r['points']:
        continue
    p = r['points'][-1]
    ends.append((round(p[0] * 10), round(p[1] * 4, 1), r['words'][0]))
col = Counter()
for r in rows.values():
    if r['superseded_by'] is not None and r['points']:
        p = r['points'][-1]
        col[int(p[0] * 10)] += 1
print('Where broken strokes left the pad, by column:', sorted(col.items()))
M['break_columns'] = dict(sorted(col.items()))

# ---- touches ------------------------------------------------------------------------------------------------------
print('\n=== Contacts that moved but made no word ===')
events = R.load(log_path)
contacts = extras['contacts']
keydowns = sorted(e['t'] for e in events if e['k'] == 'key' and e['d'] and not e['g'])
delete_spans = [(d['start'], d['end']) for d in extras['deletes']]
from bisect import bisect_left, bisect_right


def near_key(t, before, after):
    i = bisect_left(keydowns, t - before)
    return i < len(keydowns) and keydowns[i] <= t + after


blocks = []
open_block = None
for e in events:
    if e['k'] == 'mark':
        if e['kind'] == 'style-start':
            open_block = (e['label'], e['t'])
        elif e['kind'] == 'style-end' and open_block:
            blocks.append((open_block[0], open_block[1], e['t']))
            open_block = None


def block_of(t):
    for name, a, b in blocks:
        if a <= t <= b:
            return name
    return None


reasons = Counter()
reasons_block = Counter()
for c in contacts:
    if c['out'] is not None:
        continue
    if not any(a - 40 <= c['d'] <= b + 40 for a, b in delete_spans) and c['travel'] >= 1.5:
        pre = near_key(c['d'], 600, 0)
        during = near_key(c['u'], c['u'] - c['d'], 40) if c['u'] else False
        why = 'key pressed during contact' if during else 'started within 600 ms of a key press' if pre else 'no key near it'
        reasons[why] += 1
        block = block_of(c['d'])
        if block:
            reasons_block[(block, why)] += 1
M['lost_contacts'] = dict(reasons)
print('Moved 1.5+ keys, not a delete, no swipe:', dict(reasons))
print('In prompted blocks:', dict(reasons_block))

# ---- deletes ------------------------------------------------------------------------------------------------------
print('\n=== Delete flicks in prompted blocks ===')
delete_after = Counter()
for r in rows.values():
    if r['block'] and r['block'].startswith('style') and r['expected'] and 'backspace-word' in r['slot_events'] and r['superseded_by'] is None and r['position'] == 0:
        ok = R.norm(r['words'][0]).replace("'", '') == R.norm(r['expected']).replace("'", '')
        delete_after['deleted a word that was already right' if ok else 'deleted a word that was wrong'] += 1
print(dict(delete_after))
M['delete_targets'] = dict(delete_after)

# ---- how a first try's top-1 depends on the position of the error along the word -----------------------------------------------
print('\n=== Reach by outcome ===')
for ok in (True, False):
    r = [d for w in words if w['ok1'] == ok for d in w['g'].get('reach', [])]
    print(f" ok={ok}: median {R.percentile(r, .5):.2f}, >1 key: {100 * sum(d > 1 for d in r) / len(r):.1f}%")

json.dump(M, open(os.path.join(OUT, 'micro.json'), 'w'), indent=1)
