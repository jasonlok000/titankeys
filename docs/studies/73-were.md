# #73 study: were and we're

The owner asked to "use the context model to change were/we're when we're highly certain". This measures that with the model TitanKeys already uses for apostrophes. It's now built: a typed "were" becomes "we're" above 2.0 nats.

## Answer

**Yes, at 2.0 nats, and 3.0 with Only fix when sure.**

- No meant "were" scored above 0.68 on any set:
  - 42 in full sentences (Tatoeba and the owner's judged cases);
  - 535 distinct three-word runs from Tatoeba's whole English corpus, 18,000 uses by count.
- So 2.0 leaves a gap of 1.3 nats, and makes no wrong change on any set.
- At 2.0 it fixes 13 of 15 meant "we're" in full sentences. With one word on each side it fixes few, 5 of 29.

## How it works in the keyboard

"were" is one of the context words (`ApostropheContext.CANDIDATES`), with 1.0 more margin than the others (`EXTRA_MARGIN`):

- 2.0 nats, or 3.0 with Only fix when sure.
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
- **Data:** public text already in the repo, and the owner's logs as counts only. Fetching more text needs the owner's yes, so none was downloaded.
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

- Tatoeba is short, plain sentences. Chat is messier. The owner's logs have only 6 judged were/we're.
- The three-word runs give one word of context on each side. In the keyboard there's often more, which helps.
- Fetching more text needs the owner's yes. With it, re-run the tool on a larger corpus, for example all of Tatoeba's English sentences. If any meant "were" scores above about 1.5, raise `EXTRA_MARGIN`.
