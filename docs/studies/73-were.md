# #73 study: were and we're

The owner asked how the context model could change "were" to "we're" when it's very sure. This measures that with the model TitanKeys already uses for apostrophes. No code changes the word yet: "were" and "we're" are only offered on the bar (#73).

## Answer

**Yes, with a margin of 2.0 nats.** On every set with full sentences, no meant "were" scored above 0.68, and at 2.0 the model makes no wrong change and fixes 13 of 15 meant "we're". Thin context (one word on each side) fixes little at 2.0, but it still makes no wrong change.

The data is small: 15 judged "we're" and 42 judged "were" with full sentences. "were" is a very common right word, so one wrong change costs a lot. Read it as "safe at 2.0 on what we have", not as proof.

## Method

- **Model and rule:** the same as the keyboard's apostrophe check (`ApostropheContext.kt`):
  - the on-device DistilGPT-2, 8-bit (`android/assets/swipe2/predictor/model-int8pc.onnx`);
  - the app's byte BPE, reading the last 32 tokens at each step;
  - the window back to the last . ! ? or line break, at most 10 words;
  - after the word, 2 words or up to the first mark, as #73 now settles it;
  - fewer than 2 other words: too thin, no score.
- **Margin:** score("we're" + the words after) − score("were" + the words after), in nats. Every case is typed as "were", with its capital at the start of a sentence. A change happens when the margin is above the threshold.
- **Right and wrong:** a change on a meant "we're" is a right change. A change on a meant "were" is a wrong change.
- **Data:**
  - Tatoeba's held-out English sentences (`tests/autocorrect/tatoeba_heldout_en.txt`): 38 "were", 13 "we're". The written form is what was meant.
  - The owner's logs (`.dev/swipe-baseline`, private, counts only): the 6 were/we're words judged in the apostrophe study (4 "were", 2 "we're"). As a weaker check, all 11 in the logs' final texts, where the written form stands in for what was meant.
  - Three-word runs from `android/assets/autocorrect/en_triples.txt` with were or we're in the middle: 544 "were", 132 "we're", each counted once. They have one word on each side, so they test thin context.
- **Tool:** [`73-were-study.py`](73-were-study.py). It prints counts and margins only, never text.

## Numbers

### How right it is against how sure it is

Number of cases in each margin band, by the form that was meant. Tatoeba and the owner's judged cases together (57):

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

The one meant "were" above 0 scored 0.68. Every meant "we're" scored above 1.

### Changes at each threshold

| threshold | Tatoeba (38 / 13) | owner judged (4 / 2) | together (42 / 15) | three-word runs (544 / 132, 112 too thin) |
|---|---|---|---|---|
| 0 | 13 right, 1 wrong | 2, 0 | 15, 1 | 21, 6 |
| 0.5 | 13, 1 | 2, 0 | 15, 1 | 20, 0 |
| 1.0 | 13, 0 | 2, 0 | 15, 0 | 14, 0 |
| 1.5 | 13, 0 | 2, 0 | 15, 0 | 7, 0 |
| **2.0** | **11, 0** | **2, 0** | **13, 0** | **5, 0** |
| 3.0 | 10, 0 | 2, 0 | 12, 0 | 1, 0 |
| 5.0 | 7, 0 | 1, 0 | 8, 0 | 0, 0 |

(Each cell gives right changes, then wrong changes. The column heads give meant "were" / meant "we're".)

- **Zero wrong changes:** every threshold from 1.0 up gives none on every set. At 0, Tatoeba has 1 wrong change and the three-word runs 6. At 0.5, Tatoeba still has its 1.
- **The owner's logs, final texts (weak labels, 11):** 10 written "were", 1 "we're". One written "were" scores 8.6. It is most likely the judged "we're" that the owner left as "were": the study found 2 meant "we're", only 1 kept as written. So it's a missed fix, not a wrong change.
- **The apostrophe study's made-up sentences** (`tests/autocorrect`, cases 7 to 9): the meant "were" scored −4.5, −0.2 and −9.2, all safe. The study's two made-up "we're" with "were" typed scored −0.6 and −1.1, so 2.0 would miss them.

## Threshold

**2.0 nats**, the same as Only fix when sure (`ApostropheContext.SURE_MARGIN`).

- It sits 1.3 nats above the highest meant "were" seen (0.68). 1.0 is the lowest with no wrong change, but 0.68 is too close to it for a word as common as "were".
- On the test set it would fix 13 of 15 meant "we're" (87%): 11 of 13 on Tatoeba and both of the owner's. In the owner's logs that's the 1 "we're" that was left wrong.
- It should work like the other context words:
  - typed words only;
  - add the apostrophe only, never take it out;
  - Backspace right after undoes it.

## Before building it

- Collect more data: more of the owner's logs with "we're", and a larger English sample. Re-run the tool, and build only if 2.0 still gives no wrong change.
- With Only fix when sure on, use a higher margin, for example 3.0. On these sets it fixes 12 of 15.
