#!/usr/bin/env python3
"""Typo audit (owner's request, Nov 10): words that went into the text uncorrected and should have been fixed.

From the owner's raw logs:
- every word as it stood when the next word began (after autocorrect), from the text before the cursor;
- the owner's own fixes: a word removed with Backspace and typed again differently in the same place;
- punctuation patterns in the text as it was left.
Missing apostrophes are found against english.combined: a word the list lacks that has one apostrophe-spelling the
list holds ("todays" for "today's"). Ambiguous ones that are words themselves (its, ill, were, well, cant, wont, hell,
shell, id, lets, wed, shed) are counted apart. Exports overlap, so each event is read once (boot and time).
Private: the per-word lists go to a private file; the printout is counts and common-word patterns.
Usage: typo_audit.py english.combined private-out.json raw-log.jsonl..."""
import json
import re
import sys
from collections import Counter, defaultdict

combined, private_out, logs = sys.argv[1], sys.argv[2], sys.argv[3:]

freq = {}
for line in open(combined, encoding='utf-8'):
    m = re.match(r' word=([^,]+),f=(\d+),flags=([^,]*)', line)
    if m:
        w = m.group(1)
        freq[w] = max(freq.get(w, 0), int(m.group(2)))
lower = {w.lower(): f for w, f in freq.items() if w == w.lower()}
any_case = {}
for w, f in freq.items():
    any_case[w.lower()] = max(any_case.get(w.lower(), 0), f)

AMBIGUOUS = {'its', 'ill', 'were', 'well', 'cant', 'wont', 'hell', 'shell', 'id', 'lets', 'wed', 'shed', 'whos', 'hes', 'shes',
             'ive', 'im', 'youll', 'theyll', 'weve', 'youd', 'theyd', 'hed', 'itll', 'thatll'}
WORD = re.compile(r"[A-Za-z][A-Za-z'’]*$")


def apostrophe_fix(w):
    """The apostrophe spelling of w that the word list holds, or None, if w itself is not a word."""
    lw = w.lower()
    best = None
    for i in range(1, len(lw)):
        cand = lw[:i] + "'" + lw[i:]
        f = lower.get(cand, 0)
        if f and (best is None or f > best[1]):
            best = (cand, f)
    return best


seen = set()
context = defaultdict(Counter)
words = Counter()          # (word as left, source) -> count
sources = Counter()
fixes = Counter()          # (removed, typed instead) -> count, the owner's own fixes
punct = Counter()
auto = Counter()
examples_punct = defaultdict(int)


def ends_word(before):
    m = WORD.search(before.rstrip('.,!?;:'))
    return m.group(0) if m else None


for path in logs:
    boot = None
    before = ''
    last_commit_via = None
    peak = None            # text before a run of deletions
    finals = []
    setting = version = None
    input_type = 0
    for line in open(path, encoding='utf-8'):
        try:
            d = json.loads(line)
        except ValueError:
            continue
        k = d.get('k')
        if k == 'header':
            boot = round((d['wall'] - d['t']) / 60000)
            setting = d.get('settings', {}).get('autocorrect')
            version = d.get('atlas')
            continue
        if k == 'start':
            input_type = d.get('inputType', 0)
        key = (boot, d.get('t'), k, d.get('c'), d.get('cause'), d.get('start'))
        if key in seen:
            continue
        seen.add(key)
        if k == 'sel':
            new = d.get('before') or ''
            # A run of deletions: remember the text at its start, and compare the word typed again in its place.
            if len(new) < len(before) and before.startswith(new):
                if peak is None:
                    peak = before
            elif peak is not None and new.startswith(peak[:len(new)]) is False and len(new) > 0 and new[-1:] in ' .,!?':
                pass
            if peak is not None and len(new) >= 1 and new[-1:] in ' .,!?' and len(new) > len(peak.rstrip()) - 40:
                # Typing has resumed past a boundary: the word at the deletion point, before and after.
                cut = 0
                while cut < min(len(new), len(peak)) and new[cut] == peak[cut]:
                    cut += 1
                start = max(peak.rfind(' ', 0, cut), new.rfind(' ', 0, cut)) + 1
                old_w = re.match(r"[A-Za-z'’]+", peak[start:])
                new_w = re.match(r"[A-Za-z'’]+", new[start:])
                if old_w and new_w and len(new) > start + len(new_w.group(0)) and old_w.group(0) != new_w.group(0):
                    fixes[(old_w.group(0), new_w.group(0))] += 1
                peak = None
            before = new
            continue
        if k == 'text' and d.get('auto') and d.get('ok', True):
            # An automatic change lands before the space that follows it, with no selection report in between.
            n = d.get('del') or 0
            before = (before[:-n] if n else before) + (d.get('ins') or '')
        if k == 'text':
            cause, via = d.get('cause'), d.get('via')
            if d.get('auto'):
                auto[cause] += 1
            if cause == 'commit' and via in ('swipe', 'key'):
                if via == 'swipe' or (d.get('ins', '') and d['ins'][-1:].isalpha()):
                    last_commit_via = via
            ins = d.get('ins') or ''
            if cause == 'commit' and (via == 'space' or (via in ('key', 'symbol') and ins in ('.', ',', '!', '?', ';', ':'))):
                w = ends_word(before)
                if w:
                    words[(w, last_commit_via)] += 1
                    # Why it was left: the field's class and variation, the setting, and whether the app asked for no suggestions.
                    variation = input_type & 0xff0
                    flags = input_type & 0xfff000
                    context[w.lower().replace('’', "'")][(version, setting, hex(input_type & 0xf), hex(variation),
                                                          bool(flags & 0x80000), bool(flags & 0x8000))] += 1
                    sources[last_commit_via] += 1
                # Punctuation patterns at the moment a mark goes in.
                if ins in '.,!?;:' and ins:
                    if before.endswith(' ') and before.strip():
                        punct['space before ' + ins] += 1
                    if before[-1:] in '.,!?;:' and not before.endswith('...') and not (ins == '.' and before.endswith('..')):
                        punct['doubled or mixed: ' + before[-1:] + ins] += 1
            if cause == 'commit' and via == 'key' and ins[:1].isalpha() and before and before[-1] in ',.!?;:' and not re.search(r'\d[.,]$', before) \
                    and not re.search(r'(https?://|www\.|@)\S*$', before) and not re.search(r'\b[A-Za-z]\.$', before):
                punct['no space after ' + before[-1:]] += 1

families = defaultdict(Counter)
ambiguous = Counter()
for (w, src), n in words.items():
    lw = w.lower().replace('’', "'")
    if "'" in lw:
        continue
    fix = apostrophe_fix(lw)
    if not fix:
        continue
    if lw in lower or lw in AMBIGUOUS:
        ambiguous[(lw, fix[0])] += n
        continue
    tail = fix[0].split("'", 1)[1]
    fam = {"t": "n't"}.get(tail, "'" + tail)
    families[fam][(lw, fix[0], src)] += n

out = {'words_left': sum(words.values()), 'by_source': dict(sources), 'autocorrect_events': dict(auto)}
out['apostrophe_families'] = {f: {'total': sum(c.values()), 'top': [(a, b, s, n) for (a, b, s), n in c.most_common(25)]}
                              for f, c in sorted(families.items(), key=lambda x: -sum(x[1].values()))}
out['ambiguous'] = [(a, b, n) for (a, b), n in ambiguous.most_common() if a in AMBIGUOUS]
out['apostrophe_context'] = {w: [(list(k), n) for k, n in context[w].most_common()]
                             for f in families.values() for (w, _, _) in f}
out['apostrophe_context'].update({a: [(list(k), n) for k, n in context[a].most_common()] for (a, _) in ambiguous if a in AMBIGUOUS})
out['punctuation'] = dict(punct.most_common())
# The owner's own fixes, where the typed-again word is a common word: what autocorrect left for the owner to do.
own = Counter()
for (a, b), n in fixes.items():
    al, bl = a.lower(), b.lower().replace('’', "'")
    if bl in lower and al != bl:
        kind = 'apostrophe' if al.replace("'", '').replace('’', '') == bl.replace("'", '') else (
            'case' if al == bl else 'letters')
        own[(kind, al, bl)] += n
out['owner_fixes_by_kind'] = dict(Counter(k for (k, _, _) in own.elements()))
out['owner_fixes_top'] = [(k, a, b, n) for (k, a, b), n in own.most_common(60)]
# Words left uncorrected that the list lacks entirely, most frequent first (private: may hold names).
unknown = Counter()
for (w, src), n in words.items():
    lw = w.lower().replace('’', "'")
    if lw not in any_case and "'" not in lw:
        unknown[(lw, src)] += n
priv = {'unknown_words': unknown.most_common(300), 'fixes_all': [(a, b, n) for (a, b), n in fixes.most_common(500)]}
json.dump(priv, open(private_out, 'w'), indent=1)
out['unknown_words_left'] = sum(unknown.values())
print(json.dumps(out, indent=1))
