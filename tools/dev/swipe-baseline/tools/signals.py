#!/usr/bin/env python3
"""Everything else the log holds that recognition does not use. Sensor mapping, rhythm, confidence.
Usage: signals.py private-dir raw-log out-dir replay-file corpus"""
import json
import os
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

P, log_path, OUT, replay_path, corpus_path = sys.argv[1:6]
events = R.load(log_path)
rows = {r['id']: r for r in (json.loads(l) for l in open(os.path.join(P, 'attempts.jsonl'), encoding='utf-8'))}
M = {}

# ---- raw points vs the keyboard's points -----------------------------------------------------------------------------
print('=== Raw touch reports (p) against the keyboard coordinates (s) ===')
xs, ys, rxs, rys = [], [], [], []
batch = Counter()
raw_axes = {'x': [], 'y': []}
ms = [e for e in events if e['k'] == 'm']
down = [e for e in ms if e['a'] == 0]
for e in ms:
    if len(e['p']) == len(e['s']):
        for p, s in zip(e['p'], e['s']):
            if p[0] is not None and s[0] is not None:
                rxs.append(p[0]); rys.append(p[1]); xs.append(s[0]); ys.append(s[1])
                raw_axes['x'].append(p[0]); raw_axes['y'].append(p[1])
    batch[len(e['p'])] += 1


def fit(a, b):
    n = len(a)
    ma, mb = sum(a) / n, sum(b) / n
    cov = sum((x - ma) * (y - mb) for x, y in zip(a, b))
    var = sum((x - ma) ** 2 for x in a)
    slope = cov / var
    icept = mb - slope * ma
    err = max(abs(icept + slope * x - y) for x, y in zip(a[:5000], b[:5000]))
    return slope, icept, err


sx = fit(rxs, xs)
sy = fit(rys, ys)
M['sensor_map'] = {'x_slope': sx[0], 'x_intercept': sx[1], 'x_max_err': sx[2], 'y_slope': sy[0], 'y_intercept': sy[1], 'y_max_err': sy[2]}
print(' x_key_space = %.6f * raw_x + %.4f (max err %.5f)' % sx)
print(' y_key_space = %.6f * raw_y + %.4f (max err %.5f)' % sy)
for axis, values in raw_axes.items():
    print(f'  raw {axis}: min {min(values)} p1 {R.percentile(values, .01):.0f} p99 {R.percentile(values, .99):.0f} max {max(values)}  distinct {len(set(values))}')
    M['raw_' + axis] = {'min': min(values), 'max': max(values), 'p1': R.percentile(values, .01), 'p99': R.percentile(values, .99), 'distinct_values': len(set(values))}
print(' 1 key width = %.1f raw x units, 1 row = %.1f raw y units' % (.1 / sx[0], .25 / sy[0]))
M['units_per_key'] = {'x': round(.1 / sx[0], 2), 'y': round(.25 / sy[0], 2)}
print(' Motion lines: points per event', batch.most_common(6))
M['batching'] = dict(batch.most_common(8))

# Edge sticking: how many samples sit at the very edge in raw terms.
bins = Counter(int(y) for y in raw_axes['y'])
edge = {k: bins[k] for k in sorted(bins)[:6]}
print(' Lowest raw y values and how many samples at each:', edge)
M['raw_y_low_counts'] = {str(k): v for k, v in edge.items()}
top = [p for r in rows.values() for p in r['points'] if p[1] is not None]

# Where contacts first land by raw location.
down_heat = Counter((int(e['s'][0][0] * 10), int(e['s'][0][1] * 4)) for e in down if e['s'] and e['s'][0][0] is not None)
M['landing_heat'] = {f'{c},{r}': v for (c, r), v in sorted(down_heat.items())}

# ---- what the log has that the engine ignores ------------------------------------------------------------------------
print('\n=== Rhythm between swipes ===')
ordered = sorted(rows.values(), key=lambda r: r['start'])
gaps = []
for a, b in zip(ordered, ordered[1:]):
    if a['superseded_by'] is None and b['joined_from'] is None and a['field'] == b['field']:
        gaps.append(b['start'] - a['end'])
gaps = [g for g in gaps if 0 < g < 10000]
M['gap_between_swipes'] = {'n': len(gaps), 'p10': R.percentile(gaps, .1), 'p25': R.percentile(gaps, .25), 'p50': R.percentile(gaps, .5), 'p75': R.percentile(gaps, .75), 'p90': R.percentile(gaps, .9)}
print('  ms between one swipe ending and the next starting:', M['gap_between_swipes'])
rejoins = [r['start'] - rows[r['joined_from']]['end'] for r in rows.values() if r['joined_from'] in rows]
print('  rejoined pieces, same measure: p50 %.0f, p90 %.0f (n=%d)' % (R.percentile(rejoins, .5), R.percentile(rejoins, .9), len(rejoins)))

# Throughput in the prompted blocks
print('\n=== Throughput in prompted blocks ===')
style_rows = defaultdict(list)
for r in ordered:
    if r['block'] and r['block'].startswith('style'):
        style_rows[r['block']].append(r)
M['throughput'] = {}
blocks = {}
for e in events:
    if e['k'] == 'mark' and e['kind'] in ('style-start', 'style-end'):
        blocks.setdefault(e['label'], {})[e['kind']] = e['t']
extras = json.load(open(os.path.join(P, 'extras.json')))
for name, marks in blocks.items():
    if 'style-end' not in marks:
        continue
    minutes = (marks['style-end'] - marks['style-start']) / 60000
    info = extras['report']['blocks']['style:' + name]
    M['throughput'][name] = {'minutes': round(minutes, 2), 'final_words': info['typed_words'], 'wpm': round(info['typed_words'] / minutes, 1)}
    print(f'  {name}: {M["throughput"][name]}')

# Correction behaviour
print('\n=== How quickly a wrong word is noticed ===')
M['pick_rank'] = dict(Counter(e['index'] for e in events if e['k'] == 'pick'))
print('Strip position chosen (0=first):', M['pick_rank'])

# ---- confidence: is the engine's own margin a useful signal? -----------------------------------------------------------
print('\n=== Engine confidence, first tries on prompted text ===')
corpus = {}
for line in open(corpus_path, encoding='utf-8'):
    e = json.loads(line)
    corpus[e['id']] = e
replay = {}
for line in open(replay_path, encoding='utf-8'):
    e = json.loads(line)
    replay[e['id']] = e
words = [json.loads(l) for l in []]


def L2(w):
    return R.norm(w).replace("'", '')


def margin_table(selector):
    buckets = [('<2', 0, 2), ('2-5', 2, 5), ('5-10', 5, 10), ('10-20', 10, 20), ('20+', 20, 1e9)]
    out = {}
    for name, a, b in buckets:
        n = ok = 0
        for rid, e in replay.items():
            c = corpus.get(rid)
            if not c or c['set'] != selector or not c['first'] or len(e['scores']) < 2:
                continue
            margin = e['scores'][0][1] - e['scores'][1][1]
            if a <= margin < b:
                n += 1
                ok += L2(e['top'][0]) == L2(c['expected'])
        out[name] = {'n': n, 'top1': round(100 * ok / n, 1) if n else None}
    return out


for setname in ('prompted', 'hand', 'recorder'):
    t = margin_table(setname)
    M['confidence_' + setname] = t
    print(' ', setname, t)

# ---- fields and settings --------------------------------------------------------------------------------------------
print('\n=== Fields used ===')
input_types = Counter(e['inputType'] for e in events if e['k'] == 'start')
M['field_types'] = {str(k): v for k, v in input_types.most_common(10)}
print(M['field_types'])
meta = Counter(e['meta'] for e in events if e['k'] == 'key' and e['d'] and not e['g'])
print('Key meta states on press:', meta.most_common(6))
json.dump(M, open(os.path.join(OUT, 'signals.json'), 'w'), indent=1)
