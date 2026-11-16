#!/usr/bin/env python3
"""Computes the swipe baseline from build.py's attempt table. Usage: analyze.py private-dir english.combined out-dir"""
import json
import os
import re
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

P, dict_path, OUT = sys.argv[1:4]
MODE = sys.argv[4] if len(sys.argv) > 4 else 'prompted'
os.makedirs(OUT, exist_ok=True)
rows = [json.loads(l) for l in open(os.path.join(P, 'attempts.jsonl'), encoding='utf-8')]
HAND_STEPS = {'step:01-normal-typing', 'step:08-taps-then-typing', 'step:12-shift-and-type', 'step:13-shift-and-swipe'}
if MODE == 'hand':
    # Words the owner left in place after correcting by hand are treated as what was meant.
    for r in rows:
        eligible = (r['block'] is None or r['block'] in HAND_STEPS) and r['hand'] and r['hand'].replace("'", '').isalpha()
        r['expected'] = r['hand'] if eligible else None
        r['final'] = r['hand'] if eligible else None
        if eligible:
            r['block'] = 'style:everyday'
        elif r['block'] and r['block'].startswith('style'):
            r['block'] = None
extras = json.load(open(os.path.join(P, 'extras.json')))
by_id = {r['id']: r for r in rows}

freq = {}
for line in open(dict_path, encoding='utf-8'):
    m = re.match(r' word=([^,]+),f=(\d+)', line)
    if m:
        w = R.norm(m.group(1)).replace("'", '')
        freq[w] = max(freq.get(w, 0), int(m.group(2)))


def L(w):
    return R.norm(w).replace("'", '') if w else w


def pct(a, b):
    return round(100 * a / b, 1) if b else None


def fmt(a, b):
    return f'{a}/{b} ({pct(a, b)}%)' if b else 'n/a'


STYLES = ['style:everyday'] if MODE == 'hand' else ['style:Swipe only', 'style:Left swipe', 'style:Index finger', 'style:Random']
M = {}

# ---- per-slot first try in the prompted blocks ----------------------------------------------------------------
slots = defaultdict(list)
for r in rows:
    if r['block'] in STYLES and r['expected'] is not None:
        slots[r['slot']].append(r)


def effective(rs):
    return [r for r in rs if r['superseded_by'] is None]


def enrich(r, target):
    """Geometry of the whole stroke that produced this attempt, against the word it should have been."""
    pts = R.key_units(r['chain_points'])
    if len(pts) < 1:
        return {}
    w = L(target)
    keys = R.ideal_points(w)
    g = {'n': len(pts), 'dur': pts[-1][2] - pts[0][2], 'len': R.path_length(pts), 'ideal': R.path_length(keys)}
    g['speed'] = g['len'] * 1000 / max(g['dur'], 1)
    g['ratio'] = g['len'] / g['ideal'] if g['ideal'] > 0 else None
    if keys:
        reach = R.monotone_reach(pts, keys)
        g['reach'] = reach
        g['max_reach'] = max(reach) if reach else None
        g['start_err'] = (pts[0][0] - keys[0][0], pts[0][1] - keys[0][1])
        g['end_err'] = (pts[-1][0] - keys[-1][0], pts[-1][1] - keys[-1][1])
        g['start_dist'] = R.dist(pts[0], keys[0])
        g['end_dist'] = R.dist(pts[-1], keys[-1])
    return g


words = []
for sid, rs in slots.items():
    rs.sort(key=lambda r: r['id'])
    eff = effective(rs)
    if not eff:
        continue
    first = eff[0]
    exp = L(first['expected'])
    tops = [L(w) for w in first['words']]
    pieces = len(first['chain'])
    final_ok = (first['final'] is not None and L(first['final']) == exp)
    g = enrich(first, exp)
    words.append({'slot': sid, 'style': first['block'], 'expected': exp, 'got': tops[0], 'tops': tops, 'ok1': tops[0] == exp, 'ok3': exp in tops,
                  'final_ok': final_ok, 'attempts': len(eff), 'pieces': pieces, 'contacts': sum(by_id[c]['contacts'] for c in first['chain']),
                  'picked': 'choose' in first['slot_events'], 'typed': 'typed' in first['slot_events'], 'first': first, 'g': g, 'len': len(exp),
                  'freq': freq.get(exp, 0), 'first_word': first['context'].strip() == '' or first['context'].rstrip()[-1:] in '.!?'})


def summary(items):
    n = len(items)
    return {'n': n, 'top1': sum(w['ok1'] for w in items), 'top3': sum(w['ok3'] for w in items), 'final': sum(w['final_ok'] for w in items)}


def line(name, items):
    s = summary(items)
    print(f"  {name:<26} n={s['n']:<4} first-try {pct(s['top1'], s['n'])}%  in-top3 {pct(s['top3'], s['n'])}%  final {pct(s['final'], s['n'])}%")
    return s


print('=== Prompted blocks, one entry per word position ===')
M['all'] = line('ALL', words)
M['by_style'] = {s[6:]: line(s[6:], [w for w in words if w['style'] == s]) for s in STYLES}
swipe_only = [w for w in words if w['style'] != 'style:Random']
M['thumb_and_index'] = line('no-Random', swipe_only)

print('\nBy word length')
buckets = [('1-2', 1, 2), ('3', 3, 3), ('4', 4, 4), ('5', 5, 5), ('6-7', 6, 7), ('8+', 8, 99)]
M['by_length'] = {b[0]: line(b[0], [w for w in words if b[1] <= w['len'] <= b[2]]) for b in buckets}
print('\nBy word frequency (dictionary 0-255)')
fb = [('255-200', 200, 255), ('199-150', 150, 199), ('149-100', 100, 149), ('<100', 0, 99)]
M['by_frequency'] = {b[0]: line(b[0], [w for w in words if b[1] <= w['freq'] <= b[2]]) for b in fb}
print('\nFirst word of a sentence')
M['first_word'] = {'first': line('first word', [w for w in words if w['first_word']]), 'later': line('later', [w for w in words if not w['first_word']])}

top = set('qwertyuiop')
print('\nBy letters on the top row')


def top_share(w):
    return sum(c in top for c in w) / len(w)


tb = [('none', -1, 0), ('some (<50%)', 0, .4999), ('half+', .5, .9999), ('all', 1, 1)]
M['by_top_row'] = {}
for n, a, b in tb:
    M['by_top_row'][n] = line(n, [w for w in words if (top_share(w['expected']) <= 0 if n == 'none' else a < top_share(w['expected']) <= b if n.startswith('some') else a <= top_share(w['expected']) <= b)])
print('\nBy first letter\'s row')
M['by_first_row'] = {name: line(name, [w for w in words if w['expected'][0] in set(keys)]) for name, keys in zip(['top', 'middle', 'bottom'], R.ROWS)}
print('\nBy last letter\'s row')
M['by_last_row'] = {name: line(name, [w for w in words if w['expected'][-1] in set(keys)]) for name, keys in zip(['top', 'middle', 'bottom'], R.ROWS)}

print('\nBy speed (keys/s along the actual path)')
sb = [('<5', 0, 5), ('5-8', 5, 8), ('8-11', 8, 11), ('11-14', 11, 14), ('14+', 14, 999)]
M['by_speed'] = {b[0]: line(b[0], [w for w in words if w['g'] and b[1] <= w['g']['speed'] < b[2]]) for b in sb}

print('\nBy stroke integrity')
M['by_integrity'] = {'one contact, not rejoined': line('one contact', [w for w in words if w['pieces'] == 1 and w['contacts'] == 1]),
                     'broken stroke, rejoined': line('broken+rejoined', [w for w in words if w['pieces'] > 1]),
                     'several contacts, one piece': line('many contacts', [w for w in words if w['pieces'] == 1 and w['contacts'] > 1])}

# ---- every effective attempt, not only the first ----------------------------------------------------------------
all_attempts = []
for sid, rs in slots.items():
    exp = L(rs[0]['expected'])
    for r in effective(sorted(rs, key=lambda x: x['id'])):
        all_attempts.append((r, exp))
retries = [(r, e) for r, e in all_attempts if r['position'] > 0 and r['id'] != effective(sorted(slots[r['slot']], key=lambda x: x['id']))[0]['id']]
M['attempts'] = {'all_attempts': len(all_attempts), 'correct_attempts': sum(L(r['words'][0]) == e for r, e in all_attempts),
                 'retries': len(retries), 'retries_right': sum(L(r['words'][0]) == e for r, e in retries),
                 'attempts_per_word': round(len(all_attempts) / len(words), 3)}
print('\nEvery attempt:', M['attempts'])

ghosts = [r for r in rows if r['block'] in STYLES and r['expected'] is None]
M['ghosts'] = {'attempts_with_no_surviving_word': len(ghosts), 'by_style': dict(Counter(r['block'][6:] for r in ghosts))}
print('Attempts that left no word in the final text:', M['ghosts'])

# ---- what happened after a wrong first try ------------------------------------------------------------------------
wrong = [w for w in words if not w['ok1']]
recovery = Counter()
for w in wrong:
    if not w['final_ok']:
        recovery['left wrong in final text'] += 1
    elif w['picked']:
        recovery['chosen from strip'] += 1
    elif w['attempts'] > 1:
        recovery['deleted and swiped again'] += 1
    elif w['typed']:
        recovery['fixed by typing'] += 1
    else:
        recovery['other'] += 1
M['recovery'] = dict(recovery)
print('\nAfter a wrong first try:', M['recovery'])

# ---- error causes -------------------------------------------------------------------------------------------------
print('\n=== Why first tries failed ===')
for w in words:
    g = w['g'] or {}
    r = w['first']
    gesture = []
    if w['pieces'] > 1 or w['contacts'] > 1:
        gesture.append('broken')
    if g.get('start_dist', 0) > 1.0:
        gesture.append('start-off')
    if g.get('end_dist', 0) > 1.0:
        gesture.append('end-off')
    if g.get('max_reach', 0) > 1.0:
        gesture.append('letter-missed')
    w['gesture'] = gesture
    scores = {L(s[0]): s for s in (r['scores'] or [])}
    w['exp_score'] = scores.get(w['expected'])
    w['got_score'] = scores.get(w['got'])


def cls_engine(w):
    if w['ok1']:
        return None
    a, b = w['expected'], w['got']
    if a.startswith(b) or b.startswith(a):
        if abs(len(a) - len(b)) <= 3:
            return 'inflection or prefix'
    if w['ok3']:
        e, g = w['exp_score'], w['got_score']
        if e and g:
            return 'ranked 2-3: path favoured the right word' if e[2] > g[2] else 'ranked 2-3: path favoured the wrong word'
        return 'ranked 2-3'
    return 'not offered (not in the top 3)'


fails = [w for w in words if not w['ok1']]
for w in fails:
    w['engine_class'] = cls_engine(w)
M['failure_engine'] = dict(Counter(w['engine_class'] for w in fails))
print('By engine outcome:', M['failure_engine'])


def gclass(w):
    gs = w['gesture']
    g = w['g'] or {}
    if g.get('ratio') is not None and g['ratio'] > 2.2 and w['len'] >= 2:
        return 'stroke far longer than the word (label doubt)'
    if g.get('ratio') is not None and g['ratio'] < .5 and w['len'] >= 3:
        return 'stroke cut short (< half the word)'
    if 'broken' in gs:
        return 'stroke broken into pieces'
    if 'start-off' in gs:
        return 'start > 1 key from first letter'
    if 'end-off' in gs:
        return 'end > 1 key from last letter'
    if 'letter-missed' in gs:
        return 'passes > 1 key from a middle letter'
    return 'clean: reached every letter'


M['gesture_base_rates'] = {}
print('\nGesture quality vs outcome (all first tries)')
for cls in ['clean: reached every letter', 'stroke broken into pieces', 'start > 1 key from first letter', 'end > 1 key from last letter', 'passes > 1 key from a middle letter', 'stroke cut short (< half the word)', 'stroke far longer than the word (label doubt)']:
    items = [w for w in words if gclass(w) == cls]
    M['gesture_base_rates'][cls] = line(cls[:26], items) | {'fails': sum(not w['ok1'] for w in items)}
M['failure_gesture'] = dict(Counter(gclass(w) for w in fails))
print('Failures by gesture class:', M['failure_gesture'])
print('\nEngine outcome x gesture class')
table = Counter((w['engine_class'], gclass(w)) for w in fails)
for k, v in table.most_common():
    print(f'  {v:>3}  {k[0]:<44} {k[1]}')
M['failure_table'] = [{'engine': k[0], 'gesture': k[1], 'n': v} for k, v in table.most_common()]

print('\nConfusions (expected -> got)')
conf = Counter((w['expected'], w['got']) for w in fails)
M['confusions'] = [{'expected': a, 'got': b, 'n': n} for (a, b), n in conf.most_common(60)]
print('  ', ', '.join(f'{a}>{b}' + (f' x{n}' if n > 1 else '') for (a, b), n in conf.most_common(80)))

print('\nFailure rate by expected word (2+ tries)')
by_word = defaultdict(list)
for w in words:
    by_word[w['expected']].append(w)
hard = sorted(((sum(not x['ok1'] for x in v), len(v), k) for k, v in by_word.items() if len(v) >= 2), reverse=True)
print('  ', ', '.join(f'{k} {f}/{n}' for f, n, k in hard[:30]))
M['hard_words'] = [{'word': k, 'fails': f, 'n': n} for f, n, k in hard[:40]]

# ---- raw touch geometry -------------------------------------------------------------------------------------------
print('\n=== Geometry of the raw strokes, first tries in the prompted blocks ===')
gs = [w for w in words if w['g'] and 'reach' in w['g'] and w['len'] >= 2]
for name, key in [('start', 'start_err'), ('end', 'end_err')]:
    dx = [w['g'][key][0] for w in gs]
    dy = [w['g'][key][1] for w in gs]
    print(f'  {name}: mean offset ({R.mean(dx):+.2f},{R.mean(dy):+.2f}) keys; distance median {R.percentile([(a*a+b*b)**.5 for a,b in zip(dx,dy)], .5):.2f}, p90 {R.percentile([(a*a+b*b)**.5 for a,b in zip(dx,dy)], .9):.2f}')
M['endpoint_error'] = {}
for name, key in [('start', 'start_err'), ('end', 'end_err')]:
    print(f'\n  {name} point vs letter centre (key units), by letter: letter n dx dy spread')
    per = defaultdict(list)
    for w in gs:
        ch = w['expected'][0] if name == 'start' else w['expected'][-1]
        per[ch].append(w['g'][key])
    M['endpoint_error'][name] = {}
    for ch in sorted(per, key=lambda c: (R.KEY[c][1], R.KEY[c][0])):
        xs = [p[0] for p in per[ch]]
        ys = [p[1] for p in per[ch]]
        sd = (sum((x - R.mean(xs)) ** 2 for x in xs) / len(xs) + sum((y - R.mean(ys)) ** 2 for y in ys) / len(ys)) ** .5
        M['endpoint_error'][name][ch] = {'n': len(xs), 'dx': round(R.mean(xs), 2), 'dy': round(R.mean(ys), 2), 'sd': round(sd, 2)}
        if len(xs) >= 3:
            print(f'    {ch} {len(xs):>3}  {R.mean(xs):+.2f} {R.mean(ys):+.2f}  {sd:.2f}')

print('\n  Reach: how close the stroke gets to each letter of the word, in order')
all_reach = [d for w in gs for d in w['g']['reach']]
for t in (.25, .5, .75, 1.0, 1.5):
    print(f'    within {t} key: {pct(sum(d <= t for d in all_reach), len(all_reach))}%')
M['reach_cdf'] = {str(t): pct(sum(d <= t for d in all_reach), len(all_reach)) for t in (.25, .5, .75, 1.0, 1.5)}
letter_reach = defaultdict(list)
position_reach = defaultdict(list)
for w in gs:
    keys_word = ''.join(c for i, c in enumerate(w['expected']) if i == 0 or c != w['expected'][i - 1])
    for i, (ch, d) in enumerate(zip(keys_word, w['g']['reach'])):
        letter_reach[ch].append(d)
        tag = 'first' if i == 0 else 'last' if i == len(keys_word) - 1 else 'middle'
        position_reach[tag].append(d)
print('  letter: median reach, % over 1 key')
M['reach_by_letter'] = {}
for ch in sorted(letter_reach, key=lambda c: (R.KEY[c][1], R.KEY[c][0])):
    v = letter_reach[ch]
    M['reach_by_letter'][ch] = {'n': len(v), 'median': round(R.percentile(v, .5), 2), 'over1': pct(sum(d > 1 for d in v), len(v))}
    print(f"    {ch} n={len(v):<4} {R.percentile(v, .5):.2f}  {pct(sum(d > 1 for d in v), len(v))}%")
M['reach_by_position'] = {k: {'n': len(v), 'median': round(R.percentile(v, .5), 2), 'over1': pct(sum(d > 1 for d in v), len(v))} for k, v in position_reach.items()}
print('  by position:', M['reach_by_position'])
ratios = [w['g']['ratio'] for w in gs if w['g'].get('ratio')]
M['length_ratio'] = {'p10': round(R.percentile(ratios, .1), 2), 'p50': round(R.percentile(ratios, .5), 2), 'p90': round(R.percentile(ratios, .9), 2)}
print('  stroke length / ideal length:', M['length_ratio'])
speeds = [w['g']['speed'] for w in gs]
durs = [w['g']['dur'] for w in gs]
M['speed'] = {'p10': round(R.percentile(speeds, .1), 1), 'p50': round(R.percentile(speeds, .5), 1), 'p90': round(R.percentile(speeds, .9), 1), 'dur_p50': R.percentile(durs, .5), 'dur_p90': R.percentile(durs, .9)}
print('  speed keys/s and duration ms:', M['speed'])

# ---- sampling and tracking loss ---------------------------------------------------------------------------------
print('\n=== Touchpad sampling, every swipe attempt in the log ===')
dts, big = [], []
for r in rows:
    pts = r['points']
    for a, b in zip(pts, pts[1:]):
        dt = b[2] - a[2]
        dts.append(dt)
        if dt > 40:
            big.append((dt, b[1]))
dt_hist = Counter(min(d, 40) for d in dts)
M['sampling'] = {'intervals': len(dts), 'p50': R.percentile(dts, .5), 'p90': R.percentile(dts, .9), 'p99': R.percentile(dts, .99), 'max': max(dts), 'over40ms': len(big), 'zero': dt_hist.get(0, 0)}
print(' ', M['sampling'])
gaps = [g for r in rows for g in r['gaps']]
# gaps between rejoined pieces
chain_gaps = []
chain_where = Counter()
for r in rows:
    if r['joined_from'] is not None and r['joined_from'] in by_id:
        prev = by_id[r['joined_from']]
        if prev['points'] and r['points']:
            gap = r['points'][0][2] - prev['points'][-1][2]
            chain_gaps.append(gap)
            last = prev['points'][-1]
            chain_where['top row' if last[1] < .25 else 'middle' if last[1] < .5 else 'bottom' if last[1] < .75 else 'space row'] += 1
M['rejoin'] = {'n': len(chain_gaps), 'gap_p50': R.percentile(chain_gaps, .5), 'gap_p90': R.percentile(chain_gaps, .9), 'where_finger_left': dict(chain_where)}
print('  rejoined pieces:', M['rejoin'])
xs = [p[0] for r in rows for p in r['points']]
ys = [p[1] for r in rows for p in r['points']]
M['coverage'] = {'x_min': round(min(xs), 3), 'x_p1': round(R.percentile(xs, .01), 3), 'x_p99': round(R.percentile(xs, .99), 3), 'x_max': round(max(xs), 3),
                 'y_min': round(min(ys), 3), 'y_p1': round(R.percentile(ys, .01), 3), 'y_p99': round(R.percentile(ys, .99), 3), 'y_max': round(max(ys), 3)}
print('  touchpad coverage:', M['coverage'])

# ---- touches that never became a word ---------------------------------------------------------------------------
print('\n=== Touches ===')
contacts = extras['contacts']
c = Counter()
for x in contacts:
    if x['out'] is not None:
        c['became a swipe'] += 1
    elif x['travel'] >= 1.5:
        c['moved 1.5+ keys, no swipe'] += 1
    elif x['travel'] >= .6:
        c['moved 0.6-1.5 keys, no swipe'] += 1
    else:
        c['short touch'] += 1
M['contacts'] = dict(c)
print(' ', M['contacts'])
M['deletes'] = {'delete_flicks': len(extras['deletes']), 'by_block': dict(Counter((d['block'] or 'none').split(':')[0] for d in extras['deletes']))}
print('  delete flicks', M['deletes'])

# ---- ordinary use -------------------------------------------------------------------------------------------------
print('\n=== Everyday typing (no prompt) ===')
natural = [r for r in rows if not r['block']]
natural_slots = defaultdict(list)
for r in natural:
    natural_slots[r['slot']].append(r)
result = Counter()
for sid, rs in natural_slots.items():
    rs.sort(key=lambda r: r['id'])
    eff = effective(rs)
    if not eff:
        continue
    ev = eff[0]['slot_events']
    if 'choose' in ev:
        result['chose another word from the strip'] += 1
    elif len(eff) > 1:
        result['deleted and swiped again'] += 1
    elif any(e.startswith('backspace') for e in ev):
        result['deleted or edited, not swiped again'] += 1
    elif 'typed' in ev:
        result['edited by typing'] += 1
    else:
        result['kept as first offered'] += 1
n = sum(result.values())
M['natural'] = {'words': n, 'outcomes': dict(result), 'kept_pct': pct(result['kept as first offered'], n)}
print(' ', M['natural'])

json.dump(M, open(os.path.join(OUT, 'metrics-hand.json' if MODE == 'hand' else 'metrics.json'), 'w'), indent=1)
keep = []
for w in words:
    entry = {k: w[k] for k in ('slot', 'style', 'expected', 'got', 'tops', 'ok1', 'ok3', 'final_ok', 'attempts', 'pieces', 'contacts', 'picked', 'typed', 'len', 'freq', 'first_word', 'gesture')}
    entry['engine_class'] = w.get('engine_class')
    entry['gclass'] = gclass(w)
    entry['g'] = w['g']
    entry['id'] = w['first']['id']
    keep.append(entry)
json.dump(keep, open(os.path.join(P, 'words-hand.json' if MODE == 'hand' else 'words.json'), 'w'))
