# #73 study: were and we're

The owner asked to "use the context model to change were/we're when we're highly certain". This measures that with the model TitanKeys already uses for apostrophes. It's now built: a typed "were" becomes "we're" above 3.0 nats.

## Answer

**Yes, at 3.0 nats, and 4.0 with Only fix when sure.** The first sets pointed to 2.0. All of Tatoeba's English showed that was too low, so the margin went up by 1.0.

- **All of Tatoeba's English** (2,041,097 sentences, 50,793 with "were" or "we're"):
  - 40,488 meant "were". The highest scored 2.98. At 2.0 there were 4 wrong changes; at 3.0, none.
  - 11,343 meant "we're". 3.0 fixes 8,566 of them (76%), and 4.0 fixes 7,047 (62%).
- **The first, smaller sets:** no meant "were" scored above 0.68:
  - 42 in full sentences (Tatoeba's held-out set and the owner's judged cases);
  - 535 distinct three-word runs from Tatoeba's corpus, 18,000 uses by count.
- At 3.0 it fixes 12 of the 15 meant "we're" in those full sentences.

## How it works in the keyboard

"were" is one of the context words (`ApostropheContext.CANDIDATES`), with 2.0 more margin than the others (`EXTRA_MARGIN`):

- 3.0 nats, or 4.0 with Only fix when sure.
- It waits for the 2 words after it, or a mark, like its/it's.
- Swiped words too, but only the swipe's own pick: a word chosen from the bar, or fixed by hand, never changes ([73-swipes.md](73-swipes.md)).
- It only adds the apostrophe: "we're" never becomes "were".
- Backspace right after puts "were" back.
- It's off in the guide.
- It's under Settings > What gets fixed > Use context for its, lets, your….

## Method

- **Model and rule:** the same as the keyboard's apostrophe check (`ApostropheContext.kt`):
  - the on-device DistilGPT-2, 8-bit (`android/assets/swipe2/predictor/model-int8pc.onnx`);
  - the app's byte BPE, reading the last 32 tokens at each step;
  - the window back to the last . ! ? or line break, at most 10 words;
  - after the word, 2 words or up to the first mark;
  - fewer than 2 other words: too thin, no score.
- **Margin:** score("we're" + the words after) − score("were" + the words after), in nats. Every case is typed as "were", with its capital at the start of a sentence. Dropping the apostrophe from a real "we're" makes the mistake to fix, and a real "were" is a case that must stay.
- **Right and wrong:** a change on a meant "we're" is a right change. A change on a meant "were" is a wrong change.
- **Data:** public text already in the repo, all of Tatoeba's English (downloaded with the owner's yes, 2026-11-16, kept outside the repo), and the owner's logs as counts only.
  - **All of Tatoeba's English sentences** (the official export, `eng_sentences.tsv.bz2`, CC BY 2.0 FR, 2026-11-16): 2,041,097 sentences, 50,793 distinct ones with were or we're. The written form is what was meant, so a "were" written for "we're" would count as a wrong change. `73-were-study.py <repo> --tatoeba <eng_sentences.tsv>` scores them, and lists the meant "were" that score highest.
  - **Tatoeba's held-out English sentences** (`tests/autocorrect/tatoeba_heldout_en.txt`, CC BY): 38 "were", 13 "we're".
  - **Tatoeba's whole English corpus as word triples** (`android/assets/autocorrect/en_triples.txt`, CC BY 2.0 FR, the counts of each three-word run):
    - every run with were or we're;
    - `<s>` is the start of a sentence, and `</s>` the end, written as a full stop;
    - 803 "were" and 170 "we're" runs, 46,796 and 8,017 uses by count;
    - a run where the word comes first is left out: its sentence lost the words before it ("[they] were going to"), so it would read as opening a sentence, which it didn't;
    - a run where the word comes last has nothing after it, so it's too thin: 409 in all.
  - **The owner's logs** (`.dev/swipe-baseline`, private, counts only):
    - the 6 were/we're words judged in the apostrophe study (4 "were", 2 "we're");
    - as a weaker check, all 11 in the logs' final texts, where the written form stands in for what was meant.
- **Tool:** [`73-were-study.py`](73-were-study.py). It prints counts and margins only, never the logs' text.

## Numbers

### All of Tatoeba's English

51,831 cases in 50,793 sentences: 40,488 meant "were", 11,343 meant "we're". 855 are too thin to score.

| margin (nats) | meant "were" | meant "we're" |
|---|---|---|
| below −4 | 36,560 | 14 |
| −4 to −2 | 2,752 | 50 |
| −2 to 0 | 1,083 | 199 |
| 0 to 1 | 74 | 232 |
| 1 to 2 | 5 | 577 |
| 2 to 3 | 4 | 860 |
| 3 to 5 | 0 | 4,262 |
| 5 and up | 0 | 4,304 |

| threshold | right changes | wrong changes |
|---|---|---|
| 1.0 | 10,003 | 9 |
| 1.5 | 9,704 | 6 |
| 2.0 (the old rule) | 9,426 | 4 |
| 2.5 | 9,057 | 3 |
| **3.0** | **8,566** | **0** |
| **4.0** (Only fix when sure) | **7,047** | **0** |

- **The meant "were" above 1.5,** read by hand. All but one are real "were" in forms the small sets lacked:
  - 2.98, 2.73: the old subjunctive ("If all the year were playing holidays…", "Were a serious crisis to arise…");
  - 2.88, 2.45: a question opening a quote ("Were the stars visible last night?");
  - 1.78: a typo, "the were talking" for "they were talking";
  - 1.57: "We were sorry."
- **The gap at 3.0 is thin:** 0.02 nats over the highest "were". Those four are rare in chat. A wider gap would cost more fixes, and no case in this set needs it.
- **The stricter rule** (a word before "were") still made 1 wrong change at 2.0, and fixes only 2,182 at 3.0, so it stays out.

### How sure against how right

**Full sentences:** Tatoeba and the owner's judged cases, 57.

| margin (nats) | meant "were" | meant "we're" |
|---|---|---|
| below −4 | 40 | 0 |
| −4 to −2 | 1 | 0 |
| −2 to 0 | 0 | 0 |
| 0 to 1 | 1 | 0 |
| 1 to 2 | 0 | 2 |
| 2 to 3 | 0 | 1 |
| 3 to 5 | 0 | 4 |
| 5 and up | 0 | 8 |

**Tatoeba's three-word runs:** 564 scored. In brackets, uses by count.

| margin (nats) | meant "were" | meant "we're" |
|---|---|---|
| below −4 | 171 (4,540) | 0 |
| −4 to −2 | 280 (9,795) | 0 |
| −2 to 0 | 78 (3,403) | 8 (108) |
| 0 to 1 | 6 (211) | 7 (169) |
| 1 to 2 | 0 | 9 (224) |
| 2 to 3 | 0 | 4 (102) |
| 3 to 5 | 0 | 1 (27) |

### Changes at each threshold

Each cell gives right changes, then wrong changes.

| threshold | full sentences (42 / 15) | three-word runs (535 / 29 scored) | runs by count (18,000 / 630) |
|---|---|---|---|
| 0 | 15, 1 | 21, 6 | 522, 211 |
| 0.5 | 15, 1 | 20, 0 | 480, 0 |
| 1.0 | 15, 0 | 14, 0 | 353, 0 |
| 1.5 | 15, 0 | 7, 0 | 155, 0 |
| **2.0** | **13, 0** | **5, 0** | **129, 0** |
| 3.0 | 12, 0 | 1, 0 | 27, 0 |

- **Full sentences, by part:** at 2.0, Tatoeba gets 11 of 13 right and the owner's judged cases 2 of 2, with 0 wrong.
- **The owner's final texts (weak labels, 11):** one written "were" scores 8.6. It's most likely the judged "we're" that the owner left as "were", a missed fix, not a wrong change: the study found 2 meant "we're", but only 1 kept as written.
- **Questions that open with "Were":** they're barely in the sample, so the tests check them with the real model.
  - Made-up questions scored −2.3 to −13 ("Were you at home last night?", "Were the kids asleep?", "Were there any problems?"). Tatoeba's one such sentence scored −12.7.
  - Statements that drop "we" score high, as they should: "Were almost there." scored +6.4.
- **The closest call:** a dropped subject after "and" ("they came and were going to stay") scored +0.67. It's below 2.0, and it's a test case.

### A stricter rule, not taken

Needing a word before "were" makes no fewer wrong changes, as there are none already. It loses most fixes at a sentence's start: on Tatoeba, 11 right changes become 4.

## Limits

- Tatoeba is short, plain sentences, and many are near copies ("Were both Tom and Mary…"). Chat is messier. The owner's logs have only 6 judged were/we're.
- On all of Tatoeba the written form is the label, so a "we're" written as "were" counts against the rule. In the top 30 only the "the were" typo was like that.
- The three-word runs give one word of context on each side. In the keyboard there's often more, which helps.
- Done: all of Tatoeba's English was re-run with the owner's yes, and `EXTRA_MARGIN` went from 1.0 to 2.0. With newer chat logs, re-run it; if a meant "were" scores above about 2.5, raise it again.
