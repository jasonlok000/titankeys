#!/usr/bin/env python3
"""Rebuilds what happened in a TitanKeys raw input log: every swipe attempt, the text it became, and what the user
did to it afterwards. Pure standard library so it runs anywhere.

The model: a *slot* is one word position in the field. Every swipe aimed at that position (first try, retries after
a delete, joined pieces) is an attempt inside the slot, so the first attempt and the final word can be compared.
"""
import json
import re
from bisect import bisect_right
from collections import Counter, defaultdict

WORD = re.compile(r"[a-z']+")
LETTERS = 'abcdefghijklmnopqrstuvwxyz'
ROWS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm']
KEY = {}
for row, keys in enumerate(ROWS):
    # The bottom letter row starts one key in: Alt sits in the first column (see Layout.kt).
    offset = 1 if row == 2 else 0
    for col, ch in enumerate(keys):
        KEY[ch] = (col + offset + .5, row + .5)  # key units: 1 = one key width or one row


def load(path):
    with open(path, encoding='utf-8') as f:
        return [json.loads(line) for line in f]


class Clock:
    """uptimeMillis stops while the phone sleeps, so each time is read against the nearest earlier header."""

    def __init__(self, events):
        pairs = sorted((e['t'], e['wall']) for e in events if e['k'] == 'header')
        self.ts = [p[0] for p in pairs]
        self.walls = [p[1] for p in pairs]

    def wall(self, t):
        i = max(0, bisect_right(self.ts, t) - 1)
        return self.walls[i] + (t - self.ts[i])


class Slot:
    __slots__ = ('id', 'attempts', 'events', 'origin', 'final')

    def __init__(self, sid, origin):
        self.id, self.attempts, self.events, self.origin = sid, [], [], origin
        self.final = None  # the letters the slot holds now, or None once they are all deleted


class Attempt:
    """One logical swipe: an `out` line, its `rec` line and the text it put in the field."""

    def __init__(self, aid, out):
        self.id = aid
        self.out = out
        self.points = []
        self.contacts = 0
        self.gaps = []
        self.reconstructed = 'exact'
        self.rec = None
        self.committed = None
        self.context = ''
        self.slot = None
        self.joined_from = None  # attempt this one rejoined (resumed)
        self.superseded_by = None
        self.block = None
        self.field = None
        self.expected = None
        self.label = None
        self.label_source = None
        self.chain_points = None
        self.deleted_at = None
        self.sizes = []  # contact size of each raw point, when the log has it
        self.size_points = []  # [time, sensor step] of each raw point


class Replayer:
    def __init__(self, events):
        self.events = events
        self.clock = Clock(events)
        self.slots = {}
        self.attempts = []
        self.deletes = []
        self.next_slot = 0
        self.contacts = []  # finished contacts, oldest first
        self.current = None
        self.pending_outs = []
        self.waiting_text = []
        self.buf = []
        self.cursor = 0
        self.emptied = []
        self.field = -1
        self.fields = []
        self.last_text_t = -1
        self.stats = Counter()
        self.block = None
        self.step = None
        self.block_snapshots = {}
        self.last_nonempty = []
        self.last_attempt = None

    # ---- the text buffer -------------------------------------------------------------------------------------
    def new_slot(self, origin):
        slot = Slot(self.next_slot, origin)
        self.next_slot += 1
        self.slots[slot.id] = slot
        return slot

    def text(self):
        return ''.join(c[0] for c in self.buf)

    def alive(self, sid):
        return any(c[1] == sid for c in self.buf)

    def remove_before(self, count):
        """Deletes characters before the cursor. A slot that loses its last character waits for what replaces it."""
        count = min(count, self.cursor)
        removed = self.buf[self.cursor - count:self.cursor]
        del self.buf[self.cursor - count:self.cursor]
        self.cursor -= count
        ids = []
        for ch, sid in removed:
            if sid is not None and sid not in ids:
                ids.append(sid)
        for sid in ids:  # left to right, so the leftmost ends on top of the stack
            if not self.alive(sid):
                self.emptied.append(sid)
        return ids

    def insert(self, text, sid_for_word):
        for ch in text:
            sid = sid_for_word if ch.isalpha() or ch == "'" else None
            self.buf.insert(self.cursor, [ch, sid])
            self.cursor += 1
        if self.buf:
            self.last_nonempty = [list(c) for c in self.buf]

    def word_slot_at_cursor(self):
        if self.cursor and (self.buf[self.cursor - 1][0].isalpha() or self.buf[self.cursor - 1][0] == "'"):
            return self.buf[self.cursor - 1][1]
        return None

    def take_slot(self, origin):
        return self.slots[self.emptied.pop()] if self.emptied else self.new_slot(origin)

    # ---- touches ---------------------------------------------------------------------------------------------
    def motion(self, e):
        a = e['a']
        # 'sz' is each raw point's contact size (touch major), logged from swipe engine 2.0 on; older logs lack it.
        if a == 0:
            self.current = {'pts': list(e['s']), 'raw': list(e['p']), 'sz': list(e.get('sz') or []), 'd': e['t'], 'u': None, 'cancel': False}
        elif self.current is not None and a == 2:
            self.current['pts'] += e['s']
            self.current['raw'] += e['p']
            self.current['sz'] += e.get('sz') or []
        elif self.current is not None and a in (1, 3):
            self.current['pts'] += e['s']
            self.current['raw'] += e['p']
            self.current['sz'] += e.get('sz') or []
            self.current['u'] = e['t']
            self.current['cancel'] = a == 3
            self.current['out'] = None
            self.contacts.append(self.current)
            self.current = None

    def attach(self, attempt):
        """Finds the contact or contacts that made this swipe, and rebuilds the points the keyboard used."""
        out = attempt.out
        found = [c for c in self.contacts[-24:] if c['pts'] and c['pts'][0][2] >= out['start'] - 2 and c['pts'][-1][2] <= out['end'] + 2]
        if not found:
            attempt.reconstructed = 'missing'
            return
        for c in found:
            c['out'] = attempt.id
        pts = [p for c in found for p in c['pts'] if p[0] is not None]
        attempt.sizes = [s for c in found for s in c.get('sz', []) if s is not None]
        # Each size with its raw point's time, as [time, sensor step]. The log's sz is 1.125 times the sensor step.
        attempt.size_points = [[p[2], round(s / 1.125)] for c in found for p, s in zip(c['raw'], c.get('sz', [])) if s is not None]
        attempt.contacts = len(found)
        attempt.gaps = [found[i + 1]['pts'][0][2] - found[i]['pts'][-1][2] for i in range(len(found) - 1)]
        n = out['n']
        if len(pts) == n:
            attempt.points = pts
        elif len(pts) > n:
            # The keyboard's pickup skips the part of a swipe made before the first letter. Its first point is synthetic.
            attempt.points = pts[-n:]
            attempt.reconstructed = 'trimmed'
        else:
            attempt.points = pts
            attempt.reconstructed = 'short'

    # ---- main loop -------------------------------------------------------------------------------------------
    def run(self):
        for e in self.events:
            handler = getattr(self, 'on_' + e['k'], None)
            if handler:
                handler(e)
        self.close_field()
        return self

    def on_m(self, e):
        self.motion(e)

    def on_start(self, e):
        self.close_field()
        self.field += 1
        before = e.get('before') or ''
        self.buf = [[c, None] for c in before]
        self.cursor = len(self.buf)
        self.emptied = []
        self.pending_outs = []
        self.waiting_text = []
        self.fields.append({'id': self.field, 't': e['t'], 'inputType': e['inputType'], 'reference': e['reference'],
                            'restarting': e['restarting'], 'attempts': 0})

    def close_field(self):
        pass

    def on_gap(self, e):
        self.pending_outs = []
        self.waiting_text = []
        self.emptied = []
        self.last_attempt = None

    def on_mark(self, e):
        kind, label = e['kind'], e['label']
        if kind == 'style-start':
            self.block = 'style:' + label
        elif kind == 'style-end':
            self.snapshot('style:' + label)
            self.block = None
        elif kind == 'style-type-anything':
            self.block = 'style-free:' + label
        elif kind == 'start':
            self.step = 'step:' + label
        elif kind in ('done', 'redo', 'skip'):
            self.step = None

    def snapshot(self, name):
        source = self.buf if any(c[0].strip() for c in self.buf) else self.last_nonempty
        self.block_snapshots[name] = [list(c) for c in source]

    def on_out(self, e):
        if e['type'] != 'swipe':
            return
        if e.get('deletes'):
            self.deletes.append({'t': e['t'], 'start': e['start'], 'end': e['end'], 'n': e['n'], 'block': self.block or self.step})
            return
        a = Attempt(len(self.attempts), e)
        a.block = self.block or self.step
        a.field = self.field
        self.attach(a)
        self.attempts.append(a)
        self.pending_outs.append(a)

    def on_rec(self, e):
        while self.pending_outs and e['t'] - self.pending_outs[0].out['t'] > 8000:
            self.pending_outs.pop(0)
            self.stats['out_without_rec'] += 1
        if not self.pending_outs:
            self.stats['rec_without_out'] += 1
            return
        a = self.pending_outs.pop(0)
        a.rec = e
        a.context = self.text()[:self.cursor]
        if e['resumed'] and self.last_attempt is not None:
            a.joined_from = self.last_attempt.id
            self.last_attempt.superseded_by = a.id
        self.waiting_text.append(a)
        self.last_attempt = a

    def resync(self, e):
        """Trusts the app's report of the text when no edit of ours is on its way."""
        before = e.get('before')
        if before is None:
            return
        sim = self.text()[:self.cursor]
        if sim.endswith(before) and (len(before) == 256 or len(sim) == len(before)):
            return
        if e['t'] - self.last_text_t < 250:
            self.stats['sel_during_edit'] += 1
            return
        # The cursor moved within known text.
        full = self.text()
        if full[:e['end']].endswith(before) and (len(before) == 256 or e['end'] == len(before)):
            self.cursor = e['end']
            self.emptied = []
            self.stats['cursor_moved'] += 1
            return
        self.stats['resync'] += 1
        keep = max(0, self.cursor - len(before))
        self.buf = self.buf[:keep] + [[c, None] for c in before] + self.buf[self.cursor:]
        self.cursor = keep + len(before)
        self.emptied = []

    def on_sel(self, e):
        self.resync(e)

    def on_pick(self, e):
        slot_id = self.word_before_cursor_slot()
        if slot_id is not None:
            self.slots[slot_id].events.append(('pick-seen', e['t']))

    def word_before_cursor_slot(self):
        i = self.cursor - 1
        while i >= 0 and not (self.buf[i][0].isalpha() or self.buf[i][0] == "'"):
            i -= 1
        return self.buf[i][1] if i >= 0 else None

    def refresh_finals(self, before_ids):
        ids = {c[1] for c in self.buf if c[1] is not None} | before_ids
        text = defaultdict(list)
        for ch, sid in self.buf:
            if sid is not None:
                text[sid].append(ch)
        for sid in ids:
            self.slots[sid].final = ''.join(text[sid]) if text[sid] else None

    def on_text(self, e):
        touched = {c[1] for c in self.buf if c[1] is not None}
        self.apply_text(e)
        self.refresh_finals(touched)

    def apply_text(self, e):
        self.last_text_t = e['t']
        cause, via, ins = e['cause'], e.get('via'), e['ins']
        deleted = max(0, e['del'])
        if not e['ok']:
            self.stats['refused_edit'] += 1
            return
        if cause == 'commit' and via == 'swipe':
            a = self.waiting_text.pop(0) if self.waiting_text else None
            if a is None:
                self.stats['swipe_text_without_rec'] += 1
            ids = self.remove_before(deleted)
            if a is not None and a.joined_from is not None and ids:
                sid = ids[0]
                if sid in self.emptied:
                    self.emptied.remove(sid)
                slot = self.slots[sid]
            else:
                slot = self.take_slot('swipe')
            word = ins.strip()
            self.insert(ins, slot.id)
            if a is not None:
                a.slot = slot.id
                a.committed = word
                slot.attempts.append(a.id)
            return
        if cause == 'backspace':
            ids = self.remove_before(deleted)
            for sid in ids:
                self.slots[sid].events.append(('backspace-' + str(via), e['t']))
            if ids:
                t = e['t']
                for sid in ids:
                    for aid in self.slots[sid].attempts[-1:]:
                        if self.attempts[aid].deleted_at is None:
                            self.attempts[aid].deleted_at = t
            return
        if cause == 'choose' or (e['auto'] and cause in ('autocorrect', 'autocorrectundo', 'lookalike')):
            ids = self.remove_before(deleted)
            if ids:
                sid = ids[0]
                if sid in self.emptied:
                    self.emptied.remove(sid)
                slot = self.slots[sid]
            else:
                slot = self.take_slot('choose')
            slot.events.append((cause, e['t'], ins.strip()))
            self.insert(ins, slot.id)
            return
        if cause == 'commit' and via in ('key', 'tap', 'symbol', 'space', 'enter'):
            self.remove_before(deleted)
            for ch in ins:
                if ch.isalpha() or ch == "'":
                    sid = self.word_slot_at_cursor()
                    if sid is None:
                        sid = self.take_slot('typed').id
                    else:
                        if not self.slots[sid].events or self.slots[sid].events[-1][0] != 'typed':
                            self.slots[sid].events.append(('typed', e['t']))
                    self.insert(ch, sid)
                else:
                    self.insert(ch, None)
                    if ch.isspace():
                        self.emptied = []
            return
        if e['auto']:  # full stop, dropped space
            self.remove_before(deleted)
            self.insert(ins, None)
            return
        if cause == 'command':
            self.stats['command'] += 1


# ---- passage alignment ---------------------------------------------------------------------------------------
SPELLING = {'neighbour': 'neighbor', 'tyres': 'tires', 'favourite': 'favorite', 'colour': 'color', 'grey': 'gray'}


def norm(word):
    word = word.lower().replace('’', "'")
    word = word.strip("'")
    return SPELLING.get(word, word)


def tokens_of(text):
    return [norm(m.group(0)) for m in WORD.finditer(text.lower()) if norm(m.group(0))]


def tokens_with_slots(chars):
    """Words of a buffer snapshot, each with the slot that owns most of its letters."""
    out, word, owners = [], [], []
    for ch, sid in chars + [[' ', None]]:
        lower = ch.lower()
        if lower.isalpha() or lower in "'’":
            word.append(lower)
            owners.append(sid)
        elif word:
            counts = Counter(o for o in owners if o is not None)
            out.append((norm(''.join(word)), counts.most_common(1)[0][0] if counts else None))
            word, owners = [], []
    return [t for t in out if t[0]]


def lev(a, b):
    if a == b:
        return 0
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def align(typed, passage):
    """Needleman-Wunsch of typed words against the passage. The end of the passage may be left untyped."""
    n, m = len(typed), len(passage)
    gap = -1.0
    score = [[0.0] * (m + 1) for _ in range(n + 1)]
    back = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(1, n + 1):
        score[i][0] = i * gap
        back[i][0] = 1
    for j in range(1, m + 1):
        score[0][j] = j * gap
        back[0][j] = 2
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            a, b = typed[i - 1], passage[j - 1]
            sim = 1 - lev(a, b) / max(len(a), len(b))
            s = 3.0 if a == b else (1.0 if sim >= .5 else -.5 if a[:1] == b[:1] else -1.5)
            best, way = score[i - 1][j - 1] + s, 0
            if score[i - 1][j] + gap > best:
                best, way = score[i - 1][j] + gap, 1
            if score[i][j - 1] + gap > best:
                best, way = score[i][j - 1] + gap, 2
            score[i][j], back[i][j] = best, way
    # free end: the passage need not be finished
    end_j = max(range(m + 1), key=lambda j: score[n][j])
    pairs = []
    i, j = n, end_j
    while i or j:
        way = back[i][j]
        if way == 0:
            pairs.append((i - 1, j - 1))
            i, j = i - 1, j - 1
        elif way == 1:
            pairs.append((i - 1, None))
            i -= 1
        else:
            pairs.append((None, j - 1))
            j -= 1
    pairs.reverse()
    return pairs, end_j


# ---- geometry ------------------------------------------------------------------------------------------------
def key_units(points):
    """Touchpad samples as (key x, key y, time)."""
    return [(p[0] * 10, p[1] * 4, p[2]) for p in points if p[0] is not None and p[1] is not None]


def dist(a, b):
    return ((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2) ** .5


def path_length(points):
    return sum(dist(points[i], points[i + 1]) for i in range(len(points) - 1))


def ideal_points(word):
    out = []
    for ch in word:
        if ch in KEY and (not out or out[-1] != KEY[ch]):
            out.append(KEY[ch])
    return out


def monotone_reach(points, keys):
    """How close the swipe comes to each letter, visiting them in order. Returns one distance per letter, in keys."""
    n = len(points)
    if not n or not keys:
        return []
    inf = float('inf')
    cost = [dist(points[i], keys[0]) for i in range(n)]
    best_prefix = cost[:]
    for i in range(1, n):
        best_prefix[i] = min(best_prefix[i - 1], cost[i])
    table = [best_prefix[:]]
    step_cost = [cost]
    for k in keys[1:]:
        c = [dist(points[i], k) for i in range(n)]
        step_cost.append(c)
        prev = table[-1]
        cur = [inf] * n
        for i in range(n):
            cur[i] = min(cur[i - 1] if i else inf, c[i] + prev[i])
        table.append(cur)
    # walk back
    result = [0.0] * len(keys)
    i = n - 1
    for k in range(len(keys) - 1, -1, -1):
        while i > 0 and table[k][i - 1] <= table[k][i] + 1e-9:
            i -= 1
        result[k] = step_cost[k][i]
    return result


def percentile(values, q):
    if not values:
        return None
    values = sorted(values)
    position = (len(values) - 1) * q
    low = int(position)
    high = min(low + 1, len(values) - 1)
    return values[low] + (values[high] - values[low]) * (position - low)


def mean(values):
    return sum(values) / len(values) if values else None
