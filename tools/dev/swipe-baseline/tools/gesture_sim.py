#!/usr/bin/env python3
"""A port of CursorGesture (android/kotlin/com/atlas/keyboard/CursorGesture.kt) with a choice of tap area, to compare the
owner's proposal against today's rule. It replays a stream of touches and key presses and reports each drag it starts
(steer or select) and each double tap. Used by space_compare.py on the Space gestures export and the raw logs."""
import math

FIRST_TAP, SECOND_TAP, TAP_KEYS = 130, 200, .3
MIN_GAP, MAX_GAP, APART, ALONE, AFTER_KEY = 30, 150, 1.0, 200, 500
DRAG_KEYS, SIDEWAYS, UP_ROWS, JUMP = .5, 2.0, .6, 1.0
SPACE_ROW = .75


def today(p):
    return p[1] >= SPACE_ROW


def vb_lower(p):
    """The Space row, and the lower half of the V and B keys."""
    return p[1] >= SPACE_ROW or (p[1] >= .625 and .4 <= p[0] < .6)


def space_width(p):
    """The Space row, and above it the lower half of the bottom letter row as wide as the Space bar (C to N)."""
    return p[1] >= SPACE_ROW or (p[1] >= .625 and .36 <= p[0] < .64)


def vb_full(p):
    """The Space row, and all of the V and B keys."""
    return p[1] >= SPACE_ROW or (p[1] >= .5 and .4 <= p[0] < .6)


def dist(a, b):
    return math.hypot((b[0] - a[0]) * 10, (b[1] - a[1]) * 4)


class Gesture:
    """area(point) says where a touch may land. Above the Space row, a drag must go sideways: up there it is a word's start."""

    def __init__(self, area=today, max_gap_drag=MAX_GAP, band_ratio=SIDEWAYS):
        self.area, self.max_gap_drag, self.band_ratio = area, max_gap_drag, band_ratio
        self.state, self.first_from = None, 0

    def down(self, p, counts=True):
        prev = self.state
        if not counts or not self.area(p):
            self.state = None
        elif prev and prev['kind'] == 'tapped' and self.follows(prev, p):
            self.state = {'kind': 'next', 'taps': prev['taps'], 'land': p, 'last': p, 'across': 0, 'up': 0, 'travel': 0,
                          'gap': p[2] - prev['lifted']}
        elif p[2] >= self.first_from:
            self.state = {'kind': 'first', 'land': p, 'travel': 0}
        else:
            self.state = None

    def follows(self, tap, p):
        return MIN_GAP <= p[2] - tap['lifted'] <= self.max_gap_drag and abs(p[0] - tap['land'][0]) * 10 <= APART

    def move(self, p):
        s = self.state
        if s is None:
            return None
        if s['kind'] == 'first':
            s['travel'] = max(s['travel'], dist(s['land'], p))
            return None
        if s['kind'] != 'next':
            return None
        if dist(s['last'], p) > JUMP:
            self.state = None
            return None
        s['last'] = p
        s['across'] = max(s['across'], abs(p[0] - s['land'][0]) * 10)
        s['up'] = max(s['up'], (s['land'][1] - p[1]) * 4)
        s['travel'] = max(s['travel'], dist(s['land'], p))
        on_space = s['land'][1] >= SPACE_ROW
        ratio = SIDEWAYS if on_space else self.band_ratio
        sideways = s['across'] >= DRAG_KEYS and s['across'] >= ratio * s['up']
        if sideways or (on_space and s['up'] >= UP_ROWS):
            self.state = None
            return 'select' if s['taps'] == 2 else 'steer'
        return None

    def up(self, p, t):
        if p is not None:
            r = self.move(p)
            if r:
                return r
        self.first_from = max(self.first_from, t + ALONE)
        s, self.state = self.state, None
        if s and s['kind'] == 'first' and t - s['land'][2] <= FIRST_TAP and s['travel'] < TAP_KEYS:
            self.state = {'kind': 'tapped', 'taps': 1, 'land': s['land'], 'lifted': t}
        # A longer gap is only for a touch that then drags; a quick second tap still needs today's gap.
        if s and s['kind'] == 'next' and s['taps'] == 1 and t - s['land'][2] <= SECOND_TAP and s['travel'] < TAP_KEYS:
            self.state = {'kind': 'tapped', 'taps': 2, 'land': s['land'], 'lifted': t}
            # A second tap after a longer gap can only lead on to a select drag, never a double tap by itself.
            return 'double' if s['gap'] <= MAX_GAP else None
        return None

    def cancel(self, t):
        self.state = None
        self.first_from = max(self.first_from, t + AFTER_KEY)


def replay(stream, gesture):
    """stream: time-ordered ('down'|'move'|'up', point (x, y, t) or None, time, counts, touch id) and ('key', None, time, ...).
    Returns {touch id: 'steer' | 'select' | 'double'} for the touches where the gesture fired."""
    fired = {}
    for kind, p, t, counts, tid in stream:
        if kind == 'key':
            gesture.cancel(t)
        elif kind == 'down':
            gesture.down(p, counts)
        elif kind == 'move':
            r = gesture.move(p)
            if r:
                fired.setdefault(tid, r)
        elif kind == 'up':
            r = gesture.up(p, t)
            if r:
                fired.setdefault(tid, r)
    return fired
