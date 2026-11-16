#!/usr/bin/env python3
"""Issue #83: the owner's dedicated joins session, read straight from the touchpad (getevent -lt, no key events).

1. Touches: each BTN_TOUCH DOWN to UP, in letter space as the keyboard sees it: raw X / 1079 and raw Y / 599 (what
   Touchpad.samples gets once the firmware's Sliding Mode offset and scale are taken off), through the built-in
   calibration (CaptureAlign.java with TouchAlignment.java), then y * 4 / 3. The letter rows are 0 to 1; the sensor's
   top edge lands near 0.15, halfway up the top row. The contact size is ABS_MT_TOUCH_MAJOR, the keyboard's size step.
2. Words: touches are grouped into runs at pauses over PAUSE ms, and the runs matched in order to the session's
   script (SETS below) by shape against each word's key path. A run may be a slip that matches nothing, an item may
   take several runs (the owner sometimes did a fourth), and a run may hold several repeats. Within a run, each word
   takes one or more touches in a row. Gap labels inside a run come from the shapes alone, never from the timing.
3. Gaps: between two touches of one word, a break (should join); between touches of two words, a deliberate gap
   (should stay apart).
4. TitanKeys' own rule: Swipe.sameStroke from android/kotlin/com/titankeys/engine/Swipe.java, through JoinReplay.java,
   in order, so a touch after a join is judged against the joined swipe, as in the keyboard.

Prints counts and measures only. Writes the per-gap numbers to <out>/capture-gaps.jsonl.
Usage: python3 capture.py <touchpad.txt> <repo root> <out dir> [#83 private dir, for the logs' cross-check]
(needs javac and java)
"""
import json
import math
import os
import subprocess
import sys
from collections import Counter

ROWS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm']
# As the baseline's ck_core layout: the bottom row starts one key in.
KEY = {ch: ((c + (1 if r == 2 else 0) + .5) / 10, (r + .5) / 3) for r, row in enumerate(ROWS) for c, ch in enumerate(row)}

PAIRS_1 = ['elephant ontario', 'you were', 'type quite', 'write it', 'were out', 'out there', 'your turn', 'the price',
           'go to', 'try it', 'what if', 'more power', 'three people', 'quite tired', 'write your']
PAIRS_2 = ['what time', 'are you', 'we were', 'pretty quiet', 'type it', 'are they', 'write out', 'top row', 'try to',
           'power tower', 'other people', 'quite pretty', 'you try', 'write poetry', 'out of']
LONG = ['typewriter', 'property', 'territory', 'requirement', 'pottery', 'opportunity', 'repertoire', 'rewrite',
        'typeset', 'outpour']
ALIKE = ['in to', 'some one', 'may be', 'every one', 'any way', 'on to', 'out put', 'up to', 'with out', 'to day']
SETS = [('top-row pairs', PAIRS_1), ('more top-row pairs', PAIRS_2), ('long words', LONG), ('look-alike pairs', ALIKE)]

N = 32                 # points per resampled path
PAUSE = 550            # ms: no gap falls between 300 and 600 ms but 7; within an item they're under 460
SKIP_RUN = 2.0         # cost of a run that matches no item, in keys of mean shape distance
SKIP_ITEM = 3.0        # cost of an item with no run
MAX_REPEATS = 6
UNSURE = 1.6           # a run matched worse than this, in keys, is left out of the gap labels
FLICKER = 90           # the keyboard's lift-off grace, ms (JoinReplay's default)


def touches(path):
    out, cur, frame = [], None, {}
    for line in open(path):
        if not line.startswith('['):
            continue
        t = float(line[1:line.index(']')]) * 1000
        f = line[line.index(']') + 1:].split()
        if len(f) < 3:
            continue
        code, val = f[1], f[2]
        if code == 'BTN_TOUCH':
            if val == 'DOWN':
                cur = []
            elif cur is not None:
                if cur:
                    out.append(cur)
                cur = None
        elif code.startswith('ABS_MT_'):
            frame[code] = int(val, 16)
        elif code == 'SYN_MT_REPORT':
            if cur is not None and 'ABS_MT_POSITION_X' in frame:
                cur.append((frame['ABS_MT_POSITION_X'] / 1079, frame['ABS_MT_POSITION_Y'] / 599, t,
                            frame.get('ABS_MT_TOUCH_MAJOR', 0)))
            frame = {}
    return out


def calibrated(T, classes, profile):
    """Through the keyboard's built-in calibration (Touchpad.samples), then into letter space (y * 4 / 3, as SwipePieces)."""
    flat = [p for t in T for p in t]
    r = subprocess.run(['java', '-cp', classes, 'com.titankeys.engine.CaptureAlign', profile],
                       input='\n'.join(f'{p[0]:.6f} {p[1]:.6f}' for p in flat) + '\n', capture_output=True, text=True, check=True)
    xy = [tuple(map(float, l.split())) for l in r.stdout.split('\n') if l.strip()]
    out, k = [], 0
    for t in T:
        out.append([(xy[k + q][0], xy[k + q][1] * 4 / 3, p[2], p[3]) for q, p in enumerate(t)])
        k += len(t)
    return out


def keys_xy(p):
    return p[0] * 10, p[1] * 3


def resample(xy):
    if len(xy) == 1:
        return [xy[0]] * N
    d = [0.0]
    for a, b in zip(xy, xy[1:]):
        d.append(d[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
    if d[-1] < 1e-6:
        return [xy[0]] * N
    out, j = [], 0
    for i in range(N):
        s = d[-1] * i / (N - 1)
        while j < len(d) - 2 and d[j + 1] < s:
            j += 1
        f = (s - d[j]) / max(d[j + 1] - d[j], 1e-9)
        out.append((xy[j][0] + (xy[j + 1][0] - xy[j][0]) * f, xy[j][1] + (xy[j + 1][1] - xy[j][1]) * f))
    return out


TEMPLATE = {}


def template(word):
    if word not in TEMPLATE:
        letters = [c for i, c in enumerate(word) if i == 0 or c != word[i - 1]]
        TEMPLATE[word] = resample([keys_xy(KEY[c]) for c in letters])
    return TEMPLATE[word]


def shape_cost(pieces, word):
    xy = [keys_xy(p) for piece in pieces for p in piece if p[1] <= 1.05]
    if not xy:
        return 99.0
    a, b = resample(xy), template(word)
    return sum(math.hypot(p[0] - q[0], p[1] - q[1]) for p, q in zip(a, b)) / N


def runs_of(T):
    """Touches in a row with no pause over PAUSE between them: one item of the script, or a slip."""
    runs, cur = [], [0]
    for i in range(1, len(T)):
        if T[i][0][2] - T[i - 1][-1][2] > PAUSE:
            runs.append(cur)
            cur = []
        cur.append(i)
    runs.append(cur)
    return runs


def split(T, run, parts):
    """The cheapest reading of a run as the item's words, in order, once or more (the owner sometimes left under PAUSE
    between repeats). Each word takes one or more touches in a row. For each count of words the cheapest total is kept;
    the count with the lowest mean distance per word wins, so repeats are read where each word fits. Returns (mean distance per word, group of each touch); group // len(parts) is the repeat."""
    n, p = len(run), len(parts)
    INF = float('inf')
    best = [[INF] * (n + 1) for _ in range(n + 1)]   # [touches used][words placed]
    back = {}
    best[0][0] = 0.0
    for i in range(n):
        for w in range(n):
            if best[i][w] == INF:
                continue
            for j in range(i + 1, n + 1):
                c = best[i][w] + shape_cost([T[k] for k in run[i:j]], parts[w % p])
                if c < best[j][w + 1]:
                    best[j][w + 1], back[(j, w + 1)] = c, i
    ends = [w for w in range(p, n + 1, p) if best[n][w] < INF]
    if not ends:
        return INF, None
    w = min(ends, key=lambda w: best[n][w] / w)
    total, labels, j = best[n][w], [0] * n, n
    while w > 0:
        i = back[(j, w)]
        for k in range(i, j):
            labels[k] = w - 1
        j, w = i, w - 1
    return total / (max(labels) + 1), labels


def align(T, runs):
    """Runs to the script's items, in order. Each item takes one or more runs (the owner sometimes did a fourth), a run
    may be a slip that matches nothing, and an item may be left out; items after the last one reached cost nothing.
    Returns, for each run, (item, word of each touch, shape distance) or None."""
    items = [(set_name, i, item.split()) for set_name, its in SETS for i, item in enumerate(its)]
    n, m = len(runs), len(items)
    memo = {}
    def cost(r, it):
        if (r, it) not in memo:
            memo[(r, it)] = split(T, runs[r], items[it][2])
        return memo[(r, it)]
    INF = float('inf')
    best = [[INF] * (m + 1) for _ in range(n + 1)]
    back = [[None] * (m + 1) for _ in range(n + 1)]
    best[0][0] = 0.0
    for r in range(n + 1):
        for it in range(m + 1):
            c = best[r][it]
            if c == INF:
                continue
            if r < n and c + SKIP_RUN < best[r + 1][it]:
                best[r + 1][it], back[r + 1][it] = c + SKIP_RUN, ('run', r, it)
            if it < m and c + SKIP_ITEM < best[r][it + 1]:
                best[r][it + 1], back[r][it + 1] = c + SKIP_ITEM, ('item', r, it)
            if it < m:
                total = c
                for k in range(1, MAX_REPEATS + 1):
                    if r + k > n:
                        break
                    total += cost(r + k - 1, it)[0]
                    if total < best[r + k][it + 1]:
                        best[r + k][it + 1], back[r + k][it + 1] = total, ('match', r, it, k)
    it = min(range(m + 1), key=lambda j: best[n][j])
    r, out = n, [None] * n
    while r > 0 or it > 0:
        step = back[r][it]
        if step[0] != 'match':
            r, it = step[1], step[2]
            continue
        _, r0, it0, k = step
        for q in range(k):
            c, labels = cost(r0 + q, it0)
            out[r0 + q] = (items[it0], labels, c)
        r, it = r0, it0
    return out


def end_size(piece, ms=60):
    t1 = piece[-1][2]
    v = [p[3] for p in piece if p[2] >= t1 - ms]
    return sum(v) / len(v)


def start_size(piece, ms=60):
    t0 = piece[0][2]
    v = [p[3] for p in piece if p[2] <= t0 + ms]
    return sum(v) / len(v)


def motion(piece, opening, ms=40):
    """As Swipe.motion: keys per ms over the first or last 40 ms, reaching one point past."""
    idx = range(len(piece)) if opening else range(len(piece) - 1, -1, -1)
    idx = list(idx)
    f, to = idx[0], idx[0]
    for q in idx[1:]:
        to = q
        if abs(piece[q][2] - piece[f][2]) >= ms:
            break
    s, e = min(f, to), max(f, to)
    el = max(piece[e][2] - piece[s][2], 1)
    return (piece[e][0] - piece[s][0]) * 10 / el, (piece[e][1] - piece[s][1]) * 3 / el


def cosine(a, b):
    s = math.sqrt((a[0] ** 2 + a[1] ** 2) * (b[0] ** 2 + b[1] ** 2))
    return (a[0] * b[0] + a[1] * b[1]) / s if s > 1e-9 else 0.0


def path_keys(piece):
    return sum(math.hypot((b[0] - a[0]) * 10, (b[1] - a[1]) * 3) for a, b in zip(piece, piece[1:]))


def replay(classes, lines):
    r = subprocess.run(['java', '-cp', classes, 'JoinReplay', str(FLICKER)], input='\n'.join(lines) + '\n',
                       capture_output=True, text=True, check=True)
    return {l.split()[0]: (l.split()[1] == 'true', l.split()[2] == 'true') for l in r.stdout.split('\n') if l.strip()}


def line_for(name, gap, size, a, b):
    t0, tb = a[0][2], b[0][2]
    f = [name, str(int(round(gap))), f'{size:.4f}', str(len(a)), str(len(b))]
    f += [f'{p[0]:.5f} {p[1]:.5f} {p[2] - t0:.1f}' for p in a]
    f += [f'{p[0]:.5f} {p[1]:.5f} {p[2] - tb:.1f}' for p in b]
    return ' '.join(f)


def main():
    path, repo, out = sys.argv[1:4]
    os.makedirs(out, exist_ok=True)
    engine = os.path.join(repo, 'android/kotlin/com/titankeys/engine')
    classes = os.path.join(out, 'classes')
    os.makedirs(classes, exist_ok=True)
    subprocess.run(['javac', '--release', '17', '-d', classes, os.path.join(engine, 'Swipe.java'), os.path.join(engine, 'TouchAlignment.java'),
                    os.path.join(repo, 'docs/studies/83-joins/JoinReplay.java'), os.path.join(repo, 'docs/studies/83-joins/CaptureAlign.java')],
                   check=True)
    T = calibrated(touches(path), classes, os.path.join(repo, 'android/assets/calibration/titan-2-saved.profile'))
    runs = runs_of(T)
    match = align(T, runs)
    # For checking the alignment by eye. It names the script's words, so it stays in the output folder.
    with open(os.path.join(out, 'capture-align.txt'), 'w') as f:
        for r, run in enumerate(runs):
            m = match[r]
            for q, i in enumerate(run):
                gap = T[i][0][2] - T[i - 1][-1][2] if i else 0
                f.write(f'{i:3} gap {gap:6.0f} run {r:3} ' + (f'{m[0][0]} {m[0][1] + 1} {m[0][2][m[1][q] % len(m[0][2])]} rep-in-run {m[1][q] // len(m[0][2])} cost {m[2]:.2f}' if m else '-') + '\n')
    print(f'touches: {len(T)}; runs (split at pauses over {PAUSE} ms): {len(runs)}')
    got = [m for m in match if m]
    print(f'runs matched to an item: {len(got)}; left as slips: {len(runs) - len(got)}'
          f' ({sum(len(run) for run, m in zip(runs, match) if not m)} touches)')
    last = max((m[0] for m in got), key=lambda it: ([s for s, _ in SETS].index(it[0]), it[1]))
    print(f'how far it got: the last item matched is "{last[0]}" item {last[1] + 1} of {len(dict(SETS)[last[0]])}')
    for set_name, items in SETS:
        ms = [m for m in got if m[0][0] == set_name]
        if not ms:
            print(f'  {set_name}: none')
            continue
        reps = Counter(m[0][1] for m in ms)
        c = sorted(m[2] for m in ms)
        print(f'  {set_name}: items {len(reps)} of {len(items)}, runs {len(ms)} (per item: {dict(sorted(Counter(reps.values()).items()))});'
              f' shape distance median {c[len(c) // 2]:.2f} keys, 90th {c[int(len(c) * .9)]:.2f}, over {UNSURE}: {sum(x > UNSURE for x in c)}')

    # Gaps inside a matched run: a break between touches of one word, else the gap between the two words.
    gaps = []
    for run, m in zip(runs, match):
        if not m or m[2] > UNSURE:
            continue
        (set_name, item, parts), labels, _ = m
        for q in range(len(run) - 1):
            i = run[q]
            if labels[q] == labels[q + 1]:
                kind = 'break'
            elif labels[q] // len(parts) == labels[q + 1] // len(parts):
                kind = 'two words'
            else:
                kind = 'next item'
            gaps.append({'i': i, 'kind': kind, 'set': set_name, 'item': item, 'gap': T[i + 1][0][2] - T[i][-1][2],
                         'pieces': labels.count(labels[q])})
    # Gaps between items, for completeness: the pause rules them out.
    for r in range(len(runs) - 1):
        if match[r] and match[r + 1]:
            i = runs[r][-1]
            gaps.append({'i': i, 'kind': 'next item', 'set': match[r + 1][0][0], 'item': match[r + 1][0][1],
                         'gap': T[i + 1][0][2] - T[i][-1][2], 'pieces': 0})

    # TitanKeys' rule, in order: after a join the keyboard holds the joined swipe.
    joined_into = {}
    for g in sorted(gaps, key=lambda g: g['i']):
        i = g['i']
        start = joined_into.get(i, i)
        a = [p for t in T[start:i + 1] for p in t]
        b = T[i + 1]
        r = replay(classes, [line_for('g', g['gap'], end_size(T[i]), a, b)])['g']
        g['could'], g['joins'] = r
        if r[1]:
            joined_into[i + 1] = start

    for g in gaps:
        a, b = T[g['i']], T[g['i'] + 1]
        leaving, entering = motion(a, False), motion(b, True)
        jump = ((b[0][0] - a[-1][0]) * 10, (b[0][1] - a[-1][1]) * 3)
        g.update({
            'a_end_y': a[-1][1], 'b_start_y': b[0][1], 'a_end_x': a[-1][0], 'b_start_x': b[0][0],
            'top_row': a[-1][1] < 1 / 3 and b[0][1] < 1 / 3,
            'jump_keys': math.hypot(*jump), 'jump_dx': jump[0], 'jump_dy': jump[1],
            'a_end_speed': math.hypot(*leaving), 'b_start_speed': math.hypot(*entering),
            'a_end_vy': leaving[1], 'b_start_vy': entering[1],
            'carries_on': cosine(leaving, jump), 'reverses': cosine(leaving, entering),
            'a_end_size': end_size(a), 'b_start_size': start_size(b),
            'a_mid_size': sum(p[3] for p in a) / len(a), 'a_last_size': a[-1][3], 'b_first_size': b[0][3],
            'a_min_y': min(p[1] for p in a if p[2] >= a[-1][2] - 100),
            'a_keys': path_keys(a), 'b_keys': path_keys(b), 'a_ms': a[-1][2] - a[0][2], 'b_ms': b[-1][2] - b[0][2],
            'quick_sample': len(b) > 1 and b[1][2] - b[0][2] <= 20,
        })
    with open(os.path.join(out, 'capture-gaps.jsonl'), 'w') as f:
        for g in gaps:
            f.write(json.dumps(g) + '\n')
    report(gaps)
    veto(gaps, sys.argv[4] if len(sys.argv) > 4 else None)


def veto(gaps, private):
    """A candidate: keep apart when the first swipe ends heading down (back into the pad), faster than t keys per ms.
    A real top-row break is the finger sliding up off the sensor. Checked on this session and, given the #83 private
    folder, on the logs' top-row pairs that Swipe.sameStroke joins."""
    joined = [g for g in gaps if g['joins']]
    br, fj = [g for g in joined if g['kind'] == 'break'], [g for g in joined if g['kind'] != 'break']
    logs = []
    if private:
        new = {l.split()[0]: l.split()[2] == 'true' for l in open(os.path.join(private, 'new-rule.txt'))}
        logs = [p for p in map(json.loads, open(os.path.join(private, 'pairs.jsonl'))) if new[p['pair']]
                and p['a_end_y'] < 1 / 3 and p['b_start_y'] is not None and p['b_start_y'] < 1 / 3]
    print('\n== A veto on the joins: keep apart when the first swipe ends heading down faster than t (keys per ms)')
    print(f'  highest real break here: {max(g["a_end_vy"] for g in br):.5f}'
          + (f'; highest right join in the logs: {max(p["a_end_vy"] for p in logs if p["label"] == "right join"):.5f}' if logs else ''))
    for t in (0.0, 0.0005, 0.0007, 0.001, 0.0015, 0.002):
        line = (f'  t {t:.4f}: here, breaks lost {sum(g["a_end_vy"] > t for g in br)} of {len(br)}, false joins cut'
                f' {sum(g["a_end_vy"] > t for g in fj)} of {len(fj)}')
        if logs:
            c = Counter(p['label'] for p in logs if p['a_end_vy'] > t)
            n = Counter(p['label'] for p in logs)
            line += (f'; logs, right joins lost {c["right join"]} of {n["right join"]}, wrong joins cut {c["wrong join"]} of'
                     f' {n["wrong join"]}, unclear cut {c["unclear"]} of {n["unclear"]}')
        print(line)


def q(v, f):
    v = sorted(v)
    return v[min(len(v) - 1, int(f * len(v)))] if v else float('nan')


def report(gaps):
    print(f'\ngaps between two touches of the script: {len(gaps)}: {dict(Counter(g["kind"] for g in gaps))}')
    print('  breaks by set:', dict(Counter(g['set'] for g in gaps if g['kind'] == 'break')))
    print('  two-word gaps by set:', dict(Counter(g['set'] for g in gaps if g['kind'] == 'two words')))
    print('\n== TitanKeys\' rule (Swipe.sameStroke as on main), in order')
    for kind in ('break', 'two words', 'next item'):
        sub = [g for g in gaps if g['kind'] == kind]
        print(f'  {kind:<10} {len(sub):>4}: joined {sum(g["joins"] for g in sub):>3}, in the window (couldContinue)'
              f' {sum(g["could"] for g in sub):>3}, within 400 ms {sum(g["gap"] <= 400 for g in sub):>3},'
              f' top row both ends {sum(g["top_row"] for g in sub):>3}')
    br = [g for g in gaps if g['kind'] == 'break']
    tw = [g for g in gaps if g['kind'] == 'two words']
    print(f'  false joins (two words joined): {sum(g["joins"] for g in tw)} of {len(tw)};'
          f' at the top row: {sum(g["joins"] for g in tw if g["top_row"])} of {sum(g["top_row"] for g in tw)}')
    print(f'  missed joins (breaks left apart): {sum(not g["joins"] for g in br)} of {len(br)};'
          f' at the top row: {sum(not g["joins"] for g in br if g["top_row"])} of {sum(g["top_row"] for g in br)}')
    print('  breaks by set, joined / all:', {s: f'{sum(g["joins"] for g in br if g["set"] == s)}/{sum(g["set"] == s for g in br)}'
                                              for s in dict.fromkeys(g['set'] for g in br)})
    print('  two-word gaps by set, joined / all:', {s: f'{sum(g["joins"] for g in tw if g["set"] == s)}/{sum(g["set"] == s for g in tw)}'
                                                   for s in dict.fromkeys(g['set'] for g in tw)})
    print(f'  why breaks were left apart: not top-row end {sum(1 for g in br if not g["joins"] and g["a_end_y"] >= 1 / 3)},'
          f' landing below the top edge (y >= 0.267) {sum(1 for g in br if not g["joins"] and g["a_end_y"] < 1 / 3 and g["b_start_y"] >= .267)},'
          f' gap over 400 ms {sum(1 for g in br if not g["joins"] and g["gap"] > 400)},'
          f' in the window but not judged one stroke {sum(1 for g in br if g["could"] and not g["joins"])}')
    print(f'  Composer\'s 150 ms check of both readings would also see: breaks left apart within 150 ms'
          f' {sum(1 for g in br if not g["joins"] and g["gap"] <= 150)}, two-word gaps not joined within 150 ms'
          f' {sum(1 for g in tw if not g["joins"] and g["gap"] <= 150)} (its readings need the phone, so not replayed)')

    print('\n== What separates a break from two words: breaks against two-word gaps, median (10th to 90th)')
    for name, sub_b, sub_t in (('all', br, tw), ('top row both ends', [g for g in br if g['top_row']], [g for g in tw if g['top_row']])):
        print(f'  -- {name}: {len(sub_b)} breaks, {len(sub_t)} two-word gaps')
        for f in ('gap', 'a_end_y', 'b_start_y', 'a_min_y', 'jump_keys', 'jump_dx', 'jump_dy', 'a_end_speed', 'b_start_speed',
                  'a_end_vy', 'b_start_vy', 'carries_on', 'reverses', 'a_end_size', 'a_last_size', 'b_start_size', 'b_first_size',
                  'a_mid_size', 'a_keys', 'b_keys', 'a_ms', 'b_ms'):
            vb, vt = [float(g[f]) for g in sub_b], [float(g[f]) for g in sub_t]
            if not vb or not vt:
                continue
            sep = sum((x > y) + .5 * (x == y) for x in vb for y in vt) / (len(vb) * len(vt))
            print(f'    {f:<14} break {q(vb, .5):8.3f} ({q(vb, .1):8.3f} to {q(vb, .9):8.3f})'
                  f'   two words {q(vt, .5):8.3f} ({q(vt, .1):8.3f} to {q(vt, .9):8.3f})   separation {max(sep, 1 - sep):.2f}')
        qs_b, qs_t = sum(g['quick_sample'] for g in sub_b), sum(g['quick_sample'] for g in sub_t)
        print(f'    quick first sample: breaks {qs_b} of {len(sub_b)}, two words {qs_t} of {len(sub_t)}')


if __name__ == '__main__':
    main()
