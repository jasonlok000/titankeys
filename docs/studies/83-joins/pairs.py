#!/usr/bin/env python3
"""Issue #83: every pair of swipes that was joined, or could have been, with what happened next and the measurements
that might tell them apart.

Reads the attempt table that the baseline's build.py writes from a raw log, and the raw log itself for the app version
of each stretch. Writes one line per pair with numbers and labels only (no typed text), plus a points file for
JoinReplay.java, which runs the app's own Swipe.couldContinue and Swipe.sameStroke on each pair.

Usage: pairs.py attempts.jsonl raw-log.jsonl baseline-tools-dir out-dir
"""
import json
import math
import os
import sys
from bisect import bisect_right
from collections import Counter

attempts_path, log_path, tools, out_dir = sys.argv[1:5]
sys.path.insert(0, tools)
import rawlog as R  # noqa: E402

TOP_ROW = set('qwertyuiop')
MAX_GAP = 1000  # wider than today's 400 ms window, to see what lies just outside it


def key(w):
    return R.norm(w or '').replace("'", '')


def pts(points):
    """Letter-space points as the keyboard sees them: x across ten columns, y across three rows (0 = top of Q)."""
    return [(p[0], p[1] * 4 / 3, p[2]) for p in points if p[0] is not None and p[1] is not None]


def motion(p, opening, window=40):
    """Speed in keys per ms over the first or last 40 ms, as Swipe.motion measures it."""
    if len(p) < 2:
        return 0.0, 0.0
    idx = range(len(p)) if opening else range(len(p) - 1, -1, -1)
    idx = list(idx)
    frm = idx[0]
    to = frm
    for i in idx[1:]:
        if abs(p[to][2] - p[frm][2]) >= window:
            break
        to = i
    s, e = min(frm, to), max(frm, to)
    el = max(p[e][2] - p[s][2], 1)
    return (p[e][0] - p[s][0]) * 10 / el, (p[e][1] - p[s][1]) * 3 / el


def cosine(ax, ay, bx, by):
    size = math.sqrt((ax * ax + ay * ay) * (bx * bx + by * by))
    return (ax * bx + ay * by) / size if size > 1e-6 else 0.0


def length_keys(p):
    return sum(math.hypot((p[i + 1][0] - p[i][0]) * 10, (p[i + 1][1] - p[i][1]) * 3) for i in range(len(p) - 1))


def reach(points, word):
    """Mean distance in keys from the path to each letter of [word], visited in order (lower fits better)."""
    keys = R.ideal_points(word)
    ku = [(x * 10, y * 3, t) for x, y, t in points]
    r = R.monotone_reach(ku, keys)
    return sum(r) / len(r) if r else None


def margin(scores):
    if not scores:
        return None
    return scores[0][1] - scores[1][1] if len(scores) > 1 else 99.0


def size_near(size_points, t0, t1):
    v = [s for t, s in size_points or [] if t0 <= t <= t1]
    return sum(v) / len(v) if v else None


# App version for each stretch of the log, from its header lines.
heads = []
with open(log_path, encoding='utf-8') as f:
    for line in f:
        if '"header"' in line[:20]:
            e = json.loads(line)
            heads.append((e['t'], e.get('atlas')))
heads.sort()
head_t = [h[0] for h in heads]


def version(t):
    i = max(0, bisect_right(head_t, t) - 1)
    return heads[i][1] if heads else None


rows = sorted((json.loads(l) for l in open(attempts_path, encoding='utf-8')), key=lambda r: r['start'])
by_id = {r['id']: r for r in rows}
order = {r['id']: i for i, r in enumerate(rows)}


def next_kept_fit(b, pb):
    """How well the next word the user kept, swiped within 10 s after [b], fits b's own path. A merged pair of words
    is fixed by typing the second word again, so it fits; a slip that was rightly joined has no such word."""
    for c in rows[order[b['id']] + 1:order[b['id']] + 6]:
        if c['field'] != b['field'] or c['start'] - b['end'] > 10000:
            return None
        if c['joined_from'] == b['id']:
            continue
        if c['hand'] and key(c['hand']) == key(c['committed']) and len(pb) > 2:
            return reach(pb, key(c['hand']))
    return None


def chain_end(r):
    """The last piece of the joined chain [r] belongs to: its word is the one the user saw and kept or fixed."""
    while r['superseded_by'] is not None and r['superseded_by'] in by_id:
        r = by_id[r['superseded_by']]
    return r


def episode(r):
    """The first piece of r's chain, so that a run of joins counts once."""
    while r['joined_from'] is not None and r['joined_from'] in by_id:
        r = by_id[r['joined_from']]
    return r['id']
out = []
replay = []
for a, b in zip(rows, rows[1:]):
    if a['field'] != b['field']:
        continue
    joined = b['joined_from'] == a['id']
    gap = b['start'] - a['end']
    if not joined and not 0 <= gap <= MAX_GAP:
        continue
    pa, pb = pts(a['points']), pts(b['points'])
    ca = pts(a['chain_points'])  # the path the keyboard held for the first swipe, joined pieces included
    if len(pa) < 2 or len(pb) < 1 or len(ca) < 2:
        continue
    ex, ey, et = pa[-1]
    sx, sy, st = pb[0]
    jx, jy = (sx - ex) * 10, (sy - ey) * 3
    lv = motion(ca, False)
    en = motion(pb, True)
    a_committed, a_hand = key(a['committed']), key(a['hand']) if a['hand'] else None
    b_committed, b_hand = key(b['committed']), key(b['hand']) if b['hand'] else None
    end = chain_end(b) if joined else b
    if joined:
        b_committed = key(end['committed'])  # the same slot, so its final word is b's 'hand'
    a_words = [key(s[0]) for s in (a['scores'] or [])[:3]]

    # What happened next.
    fit_joined = fit_first = None
    if joined:
        if b_hand is None:
            label, why = 'unclear', 'joined word deleted, nothing kept in its place'
        elif b_hand == b_committed:
            label, why = 'right join', 'joined word kept'
        else:
            fit_joined, fit_first = reach(ca + pb, b_hand), reach(ca, b_hand)
            if end is not b:
                label, why = 'right join', 'a link in a longer chain whose word was then fixed'
                if fit_first is not None and fit_joined is not None and fit_first <= fit_joined + .05:
                    label, why = 'wrong join', 'a link in a chain; the word kept fits the first swipe alone'
            elif b_hand in a_words:
                label, why = 'wrong join', "word kept is the first swipe's own reading"
            elif fit_first is not None and fit_joined is not None and fit_first <= fit_joined + .05:
                label, why = 'wrong join', 'word kept fits the first swipe alone as well as both'
            else:
                label, why = 'right join', 'joined, then a misread fixed: the word kept needs both swipes'
    else:
        a_kept = a_hand is not None and a_hand == a_committed
        b_kept = b_hand is not None and b_hand == b_committed
        if a_kept and b_kept:
            label, why = 'right non-join', 'both words kept'
        elif a_hand is not None and a_hand != a_committed and b_hand is None:
            fit_joined, fit_first = reach(ca + pb, a_hand), reach(ca, a_hand)
            if fit_joined is not None and fit_first is not None and fit_joined + .3 < fit_first and fit_joined < 1.0:
                label, why = 'missed join', 'both replaced by one word that needs both swipes'
            else:
                label, why = 'other', 'both replaced, but the word kept does not need the second swipe'
        else:
            label, why = 'other', 'one of the two corrected'

    # Top-row word pairs, from the words kept (letters checked here; no text leaves this script).
    first_word = a_hand if not joined else (a_words[0] if label == 'wrong join' else None)
    second_word = b_hand if not joined else None
    tops = None
    if first_word and second_word:
        tops = (first_word[-1] in TOP_ROW, second_word[0] in TOP_ROW)

    sp_a, sp_b = a.get('size_points') or [], b.get('size_points') or []
    row = {
        'pair': f"{a['id']}-{b['id']}", 'version': version(a['start']), 'set': 'prompted' if a['block'] else 'everyday',
        'joined': joined, 'label': label, 'episode': episode(a) if joined else None, 'chain_len': len(end['chain']) if joined else None, 'why': why,
        'gap': gap, 'a_end_y': ey, 'b_start_y': sy, 'a_end_x': ex, 'jump_keys': math.hypot(jx, jy), 'jump_dx': jx, 'jump_dy': jy,
        'a_end_vx': lv[0], 'a_end_vy': lv[1], 'a_end_speed': math.hypot(*lv),
        'b_start_vx': en[0], 'b_start_vy': en[1], 'b_start_speed': math.hypot(*en),
        'carries_on': cosine(lv[0], lv[1], jx, jy), 'reverses': cosine(lv[0], lv[1], en[0], en[1]),
        'quick_sample': len(pb) > 1 and pb[1][2] - pb[0][2] <= 20,
        'a_min_y_last100': min(p[1] for p in pa if pa[-1][2] - p[2] <= 100),
        'a_len': length_keys(ca), 'a_dur': ca[-1][2] - ca[0][2], 'a_pieces': len(a['chain']),
        'b_len': length_keys(pb), 'b_dur': pb[-1][2] - pb[0][2], 'b_n': len(pb),
        'a_margin': margin(a['scores']), 'b_margin': margin(b['scores']),
        'a_score': a['scores'][0][1] if a['scores'] else None, 'b_score': b['scores'][0][1] if b['scores'] else None,
        'a_path_per_letter': a['scores'][0][2] / max(1, len(a_words[0])) if a['scores'] else None,
        'b_path_per_letter': b['scores'][0][2] / max(1, len(key(b['scores'][0][0]))) if b['scores'] else None,
        'a_word_len': len(a_words[0]) if a_words else None, 'b_word_len': len(key(b['scores'][0][0])) if b['scores'] else None,
        'a_end_size': size_near(sp_a, pa[-1][2] - 60, pa[-1][2]), 'a_mid_size': size_near(sp_a, pa[0][2], pa[-1][2] - 100),
        'b_start_size': size_near(sp_b, pb[0][2], pb[0][2] + 60), 'b_mid_size': size_near(sp_b, pb[0][2] + 60, pb[-1][2]),
        # Whether joining changed the word at all, and whether what was kept is the first swipe's own word.
        'join_changed_word': (key(b['committed']) != a_committed) if joined else None,
        'kept_first_alone': (b_hand == a_committed) if joined and b_hand else None,
        'next_fit_b': next_kept_fit(chain_end(b), pb) if joined else None,
        'b_alone_tiny': length_keys(pb) < .5 and pb[-1][2] - pb[0][2] < 60,
        'fit_joined': fit_joined, 'fit_first': fit_first, 'tops': tops,
        # Prompted first tries carry the headline number.
        'b_first_try': b['position'] == 0, 'a_first_try': a['position'] == 0,
        'a_right_alone': (a_words[:1] == [key(a['expected'])]) if a['expected'] else None,
    }
    out.append(row)
    t0 = ca[0][2]
    rp = [row['pair'], str(gap), str(len(ca)), str(len(pb))]
    rp += [f'{x:.5f} {y:.5f} {t - t0:.1f}' for x, y, t in ca]
    tb = pb[0][2]
    rp += [f'{x:.5f} {y:.5f} {t - tb:.1f}' for x, y, t in pb]
    replay.append(' '.join(rp))

os.makedirs(out_dir, exist_ok=True)
with open(os.path.join(out_dir, 'pairs.jsonl'), 'w') as f:
    for r in out:
        f.write(json.dumps(r) + '\n')
with open(os.path.join(out_dir, 'pairs-points.txt'), 'w') as f:
    f.write('\n'.join(replay) + '\n')
print('pairs', len(out))
print(Counter((r['joined'], r['label']) for r in out))
print(Counter(r['version'] for r in out if r['joined']))
