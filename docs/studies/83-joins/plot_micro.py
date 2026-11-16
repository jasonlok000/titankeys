#!/usr/bin/env python3
"""Issue #83: plots for the micro-level look at the owner's touchpad session (micro.py writes the input).

  micro-cases-y.png      the finger's height, sample by sample, 100 ms each side of the gap
  micro-cases-speed.png  its speed between samples
  micro-cases-size.png   its contact size
  micro-signals.png      the signals that separate best, every gap, by kind

Each case grid has a row per kind of gap and three typical cases: those whose gap is nearest the kind's median.
Numbers only: no words from the session. Needs matplotlib.
Usage: python plot_micro.py <micro-windows.json> <output dir>
"""
import json
import math
import os
import sys

import matplotlib

matplotlib.use('Agg')
import matplotlib.pyplot as plt  # noqa: E402

# The reference palette (dataviz skill, light mode), slots in fixed order.
FIRST, SECOND = '#2a78d6', '#eb6834'
KINDS = [('edge', 'break: slid off the top edge', '#2a78d6'),
         ('dropout', 'break: sensor lost it', '#eb6834'),
         ('bounce', 'break: bounce', '#1baf7a'),
         ('apart', 'two words, kept apart', '#eda100'),
         ('glued', 'two words, joined by the rule', '#e87ba4')]
INK, MUTED, GRID, SURFACE = '#0b0b0b', '#52514e', '#e4e3df', '#fcfcfb'
EDGE = 0.154

plt.rcParams.update({'font.size': 9, 'axes.edgecolor': MUTED, 'axes.labelcolor': MUTED, 'xtick.color': MUTED,
                     'ytick.color': MUTED, 'axes.facecolor': SURFACE, 'figure.facecolor': SURFACE,
                     'axes.grid': True, 'grid.color': GRID, 'grid.linewidth': .6, 'axes.spines.top': False,
                     'axes.spines.right': False})


def kind(w):
    if w['sort'] != 'two words':
        return w['sort']
    return 'glued' if w['joins'] else 'apart'


def typical(windows, k, n=3):
    sub = [w for w in windows if kind(w) == k and len(w['a']) >= 2 and len(w['b']) >= 2]
    if not sub:
        return []
    gaps = sorted(w['gap'] for w in sub)
    median = gaps[len(gaps) // 2]
    return sorted(sub, key=lambda w: abs(w['gap'] - median))[:n]


def speeds(points):
    out = []
    for p, q in zip(points, points[1:]):
        dt = max(q[0] - p[0], 1e-3)
        out.append(((p[0] + q[0]) / 2, math.hypot((q[1] - p[1]) * 10, (q[2] - p[2]) * 3) / dt))
    return out


def grid(windows, name, ylabel, values, out, invert=False, edge=None):
    fig, axes = plt.subplots(len(KINDS), 3, figsize=(10, 11), sharex=True, sharey=True, constrained_layout=True)
    for row, (k, title, _) in enumerate(KINDS):
        cases = typical(windows, k)
        for col in range(3):
            ax = axes[row][col]
            if col >= len(cases):
                ax.set_visible(False)
                continue
            w = cases[col]
            gap_end = w['b'][0][0]
            ax.axvspan(0, gap_end, color=GRID, alpha=.7, lw=0)
            for pts, colour in ((w['a'], FIRST), (w['b'], SECOND)):
                xs, ys = zip(*values(pts)) if values(pts) else ([], [])
                ax.plot(xs, ys, color=colour, lw=2, marker='o', ms=4, mec=SURFACE, mew=1)
            if edge is not None:
                ax.axhline(edge, color=MUTED, lw=1, ls='--')
            ax.set_title(f'gap {w["gap"]:.0f} ms', fontsize=8, color=MUTED, loc='right')
            if col == 0:
                ax.set_ylabel(f'{title}\n{ylabel}', color=INK, fontsize=8)
            if row == len(KINDS) - 1:
                ax.set_xlabel('ms from the last sample before the lift')
    if invert:
        axes[0][0].invert_yaxis()
    fig.suptitle(f'{name}: first touch (blue), gap (grey), second touch (orange)', color=INK, fontsize=10)
    fig.savefig(out, dpi=150)
    plt.close(fig)


def signals(windows, out):
    shown = [('gap', 'gap, ms'), ('a_vy_40', 'first touch: vertical speed over its last 40 ms,\nkeys/ms (+ is down)'),
             ('a_speed_20', 'first touch: speed over its last 20 ms, keys/ms'),
             ('a_edge_keys', 'first touch: last sample below the top edge, keys'),
             ('b_dt_first', 'second touch: time to its second sample, ms'),
             ('b_speed_20', 'second touch: speed over its first 20 ms, keys/ms')]
    fig, axes = plt.subplots(3, 2, figsize=(10, 9), constrained_layout=True)
    for ax, (key, label) in zip(axes.flat, shown):
        for i, (k, title, colour) in enumerate(KINDS):
            vals = [w['f'][key] for w in windows if kind(w) == k and w['f'].get(key) is not None]
            ys = [i + ((j * 0.618) % 1 - .5) * .5 for j in range(len(vals))]
            ax.scatter(vals, ys, s=14, color=colour, edgecolor=SURFACE, linewidth=.6, zorder=3)
            if vals:
                m = sorted(vals)[len(vals) // 2]
                ax.plot([m, m], [i - .32, i + .32], color=INK, lw=2, zorder=4)
        if key == 'a_vy_40':
            ax.axvline(0.001, color=MUTED, lw=1, ls='--')
        ax.set_yticks(range(len(KINDS)))
        ax.set_yticklabels([t for _, t, _ in KINDS], color=INK)
        ax.invert_yaxis()
        ax.set_xlabel(label)
        ax.grid(axis='y', visible=False)
    fig.suptitle('Every gap in the session, by kind (black bar: median)', color=INK, fontsize=10)
    fig.savefig(out, dpi=150)
    plt.close(fig)


def main():
    windows = json.load(open(sys.argv[1]))
    out = sys.argv[2]
    os.makedirs(out, exist_ok=True)
    grid(windows, 'Height', 'y, letter rows', lambda pts: [(p[0], p[2]) for p in pts],
         os.path.join(out, 'micro-cases-y.png'), invert=True, edge=EDGE)
    grid(windows, 'Speed', 'keys/ms', speeds, os.path.join(out, 'micro-cases-speed.png'))
    grid(windows, 'Contact size', 'size step', lambda pts: [(p[0], p[3]) for p in pts],
         os.path.join(out, 'micro-cases-size.png'))
    signals(windows, os.path.join(out, 'micro-signals.png'))
    print('wrote 4 plots to', out)


if __name__ == '__main__':
    main()
