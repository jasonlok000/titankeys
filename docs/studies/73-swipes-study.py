#!/usr/bin/env python3
"""
#73's swipe study: would the settle-later apostrophe fix (ApostropheContext) do right by swiped words?

Every swiped word in the owner's apostrophe study, with what the swipe put in and what the owner meant. A swipe that put in a word
with no apostrophe ("its", "your", "there", "were"...) is scored as the keyboard scores a typed one: the window back to the last
sentence mark, the words after up to 2 or the first mark, and the on-device DistilGPT-2. The fix only adds an apostrophe, so a swipe
that put in "it's" can't change.

Prints counts and margins only, never the text.

Usage: python3 73-swipes-study.py <repo root> <labels.tsv> <ctx.pkl>
The last two are the private apostrophe study's working files. They're read, never written, and no text from them is printed.
"""
import importlib.util
import os
import pickle
import sys
from collections import Counter

# The were study has the app's tokenizer, window and rule for the words after.
spec = importlib.util.spec_from_file_location("were_study", os.path.join(os.path.dirname(__file__), "73-were-study.py"))
were = importlib.util.module_from_spec(spec)
spec.loader.exec_module(were)

# As ApostropheContext.kt.
CANDIDATES = {
    "its": ["it's"], "lets": ["let's"], "ill": ["i'll"], "well": ["we'll"], "id": ["i'd"], "hell": ["he'll"], "shed": ["she'd"],
    "wed": ["we'd"], "shell": ["she'll"], "your": ["you're"], "their": ["they're"], "there": ["they're"], "were": ["we're"],
}
EXTRA_MARGIN = {"were": 1.0}
MARGIN, SURE_MARGIN = 1.0, 2.0


def cased(form, typed, opening):
    if form.startswith("i'"):
        return "I" + form[1:]
    return were.cased(form, typed, opening)


def judge(before, typed, rest):
    """(form, margin) for the best apostrophe form, or None when too thin."""
    after = were.following(rest)
    if after is None:
        return None
    ctx = were.window(before)
    if were.count_words(ctx) + were.count_words(after) < were.MIN_OTHER_WORDS:
        return None
    opening = ctx == ""
    lead = "" if opening else " "
    base = were.score(ctx, lead + cased(typed.lower(), typed, opening) + after)
    best = max((were.score(ctx, lead + cased(f, typed, opening) + after), f) for f in CANDIDATES[typed.lower()])
    return best[1], best[0] - base


def main():
    labels = dict(l.rstrip("\n").split("\t") for l in open(sys.argv[2], encoding="utf-8"))
    ctx = pickle.load(open(sys.argv[3], "rb"))
    swiped = [c for c in ctx if c["src"] == "swipe"]
    norm = lambda w: w.lower().replace("’", "'")
    print(f"swiped words in the study: {len(swiped)}")
    print(f"  put in with an apostrophe (the fix can't change them): {sum(1 for c in swiped if chr(39) in norm(c['committed']))}")
    plain = [c for c in swiped if norm(c["committed"]) in CANDIDATES]
    print(f"  put in as a word the fix looks at: {len(plain)}")
    print(f"  put in as another word: {len(swiped) - len(plain) - sum(1 for c in swiped if chr(39) in norm(c['committed']))}")

    # Meant: the judged form. "x" is a swipe that misread another word ("hell" for "heel"), "?" unclear: any change is wrong there.
    rows = []
    for c in plain:
        meant = norm(labels.get(str(c["id"]), "?"))
        verdict = judge(c["before"], c["committed"], c["after"])
        rows.append((norm(c["committed"]), meant, verdict))
    print("\nby word put in, and what was meant:")
    for (w, m), n in sorted(Counter((w, m if m in (w, *CANDIDATES[w]) else "misread or unclear") for w, m, _ in rows).items()):
        print(f"  {w:6} meant {m:20} {n}")
    kept = Counter("unreadable" if not c.get("kept") else "as put in" if norm(c["kept"]) == norm(c["committed"])
                   else "the apostrophe form" if norm(c["kept"]) in CANDIDATES[norm(c["committed"])] else "another word" for c in plain)
    print("what the owner kept: " + ", ".join(f"{k} {n}" for k, n in sorted(kept.items())))
    thin = sum(1 for *_, v in rows if v is None)
    print(f"too thin to score: {thin}")

    print("\nmargin of each scored word (rounded), by whether the apostrophe form was meant:")
    for w, m, v in sorted(rows, key=lambda r: (r[0], r[2][1] if r[2] else -99)):
        if v is not None:
            print(f"  {w:6} {'meant the apostrophe form' if m == v[0] else 'meant as put in' if m == w else 'misread or unclear':26} {v[1]:+.2f}")

    def changes(base):
        right = sum(1 for w, m, v in rows if v and v[1] > base + EXTRA_MARGIN.get(w, 0) and m == v[0])
        wrong = sum(1 for w, m, v in rows if v and v[1] > base + EXTRA_MARGIN.get(w, 0) and m != v[0])
        return right, wrong

    print("\nchanges at each margin (were takes 1.0 more):")
    for base in (0, 0.5, MARGIN, 1.5, SURE_MARGIN, 3):
        right, wrong = changes(base)
        name = " (today's)" if base == MARGIN else " (only when sure)" if base == SURE_MARGIN else ""
        print(f"  {base:>4}{name:18} right {right}, wrong {wrong}")
    top = max((v[1] - EXTRA_MARGIN.get(w, 0) for w, m, v in rows if v and m != v[0]), default=None)
    print(f"highest margin of a word not meant with the apostrophe (less were's extra): {top if top is None else round(top, 2)}")


if __name__ == "__main__":
    main()
