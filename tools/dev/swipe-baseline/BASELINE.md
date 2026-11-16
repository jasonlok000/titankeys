# Swipe engine baseline

Measured 2026-11-09 from `raw-log-4.jsonl` (the 17:05 export: 106k lines, 1,272 recognised swipes). Current engine is `4f75dcb`, the build on the phone. This file quotes only words from the prompted passages. Typed text stays in `$TK_PRIVATE`, outside the repo.

## The numbers to beat

Each row is the **first try** at a word position, run through the current engine. Rerun with `tools/replay.sh` and `tools/baseline.py` (see `README.md`).

| Set | Words | Right word first | Right word in top 3 | Decoder found it at all |
|---|---|---|---|---|
| **Prompted passages (headline)** | **404** | **76.5%** | **82.9%** | **87.9%** |
| Held-back third of those (`slot % 3 == 2`) | 136 | 76.5% | 81.6% | 86.0% |
| Hand-corrected text, confirmed tests removed | 323 | 64.7% | 76.8% | 82.4% |
| Hand-corrected text, also outside 13:20-16:30 | 189 | 59.8% | 73.5% | 82.0% |
| Recorder session (isolated words, older touch handling) | 300 | 67.3% | 75.0% | 80.0% |

By prompted block, first try and top 3:

| Block | Words | First | Top 3 |
|---|---|---|---|
| Swipe only | 134 | 79.9% | 85.1% |
| Index finger | 130 | 77.7% | 83.1% |
| Random (mixed with keys) | 43 | 76.7% | 81.4% |
| Left swipe | 97 | 70.1% | 80.4% |

Cost when the first try fails (prompted): 1.25 attempts per word, a retry is right 71% of the time, and final text ends up 99.3% right. Of 95 wrong first tries, 64 were deleted and swiped again, 25 were chosen from the strip, 3 were left wrong and 3 were typed.

Throughput with corrections: 25-30 words a minute in the swipe-only blocks. The engine takes 42 ms at the median and 61 ms at worst on the phone.

## How to read these sets

- **Prompted passages** have true intent from the on-screen text and align 456 of 459 words exactly. These are the headline.
- **Hand-corrected text** is the owner's own text, corrected by hand, so the word left in the field is taken as what was meant. It adds 344 word positions of real use.
- **Some of that text is deliberate testing.** The Issues session confirmed W9-W12 and W18 as tests, and those are removed. It could not confirm any window as everyday, and the owner's tests were spread across 13:20-16:30. That period is reported separately: 71.6% first try inside it against 59.8% outside it (n=134 and 189). The cause of that gap is unknown, so do not average the two.
- **Learned words:** the replay has no personal dictionary. On hand-corrected text the live engine got 7 first tries that the replay did not, and the reverse never happened.

## Replay is trustworthy

Top-3 lists from the replay match what the phone showed on 393 of 404 prompted first tries, and no word changed between right and wrong. The prompted blocks were recorded at 12:07-12:24 on the 07:04 build (`a3ae275`), so the replay on `4f75dcb` is also a check that this engine scores those strokes the same way.

The recorder row is not comparable to the earlier "75%" figure. That figure replayed touches through the newer rejoin logic. This one keeps the strokes as that session recorded them.

## Where first tries are lost

95 prompted misses and 113 hand-corrected misses (as recorded live), by what the stroke looked like. A stroke counts once. The classes are checked in this priority: label doubt, cut short, broken, start, end, middle.

| Stroke | Prompted: misses / strokes (first-try rate) | Hand-corrected misses |
|---|---|---|
| Clean: reached every letter | 20 / 199 (89.9%) | 21 |
| Cut short: under half the word's length | 18 / 18 (0%) | 15 |
| Start over 1 key from the first letter | 18 / 27 (33.3%) | 25 |
| End over 1 key from the last letter | 16 / 60 (73.3%) | 24 |
| Broken into pieces and rejoined | 11 / 48 (77.1%) | 13 |
| Passes over 1 key from a middle letter | 9 / 48 (81.2%) | 11 |
| Stroke over 2.2x the word's length (label in doubt) | 3 / 4 | 4 |

**Three in four prompted misses come from strokes that are off-target, broken or truncated.** On clean strokes the engine is right first 90% of the time, and the right word is in the top 3 98% of the time. The largest gain is likely in how strokes are captured and interpreted before ranking.

### Decoder or ranking

| Prompted first tries | Words |
|---|---|
| Decoder's own first word was right | 305 |
| Right word somewhere in its top 100, not first | 50 |
| Right word never listed | 49 |

- On the 95 misses, the decoder never listed the right word in 48 (hand-corrected replay: 59 of 120). Only 1 of the 49 unlisted words still came out right, so the later stages rarely rescue these.
- Decoder and final word differ on 66 of 404. The rerank layer fixed 18 words, broke 14 and changed 34 that stayed wrong: a net gain of 4 words on prompted text, and 28 on hand-corrected text (44 fixed, 16 broken).

Prompted confusions: `add>as` x3, `on>in` x3, `you>to` x2, `pot>it` x2, `has>had` x2, `had>has`, `who>walk`, `over>of`, `her>the`, `until>into`, `waste>water`, `walking>asking`. Full list in `report/metrics.json`.

Accuracy falls with rarity: frequency 200+ is 92.8%, 150-199 is 77.1%, 100-149 is 67.6%, under 100 is 55.6% (n=9). The hand-corrected set matches this: 87.2%, 66.7%, 64.2%, 53.7%.

## Raw stroke detail

From first tries on prompted text, in key widths. `dx` is positive to the right.

- **Speed:** median 15.6 keys a second, 486 ms a word. The stroke is 0.88x the ideal length at the median (0.69 at p10).
- **Reach:** 85.5% of letters are passed within one key. The last letter is missed by over a key 18.3% of the time, a middle letter 16.8% and the first letter 5.9%. Hardest letters: P 34%, I 33%, U 29%, O 28%, L 22%.
- **Top-right start points read to the left:** P -1.02, I -0.84, O -0.53 at the start. P -1.20 and O -0.79 at the end. Those are the keys at the sensor's edge.
- **The "Left swipe" block reads to the left.** Its start offsets by row are -0.62, -0.36 and -0.98 keys, against about zero for "Swipe only" and "Index finger". Its end offsets are -0.37, -0.41 and -1.19. It is also the weakest block. If that block was the left thumb, one calibration is being asked to fit two different hands.
- **Sampling:** 10 ms steady, delivered in events of 1-2 points. 653 of 63,065 intervals exceed 40 ms, and the longest is 3.5 s.
- **Stroke breaks:** 188 rejoins, 156 of them (83%) where the finger left on the top row, most often in the Y, U and I columns. Pieces sit a median 67 ms apart, p90 146 ms. New words start a median 848 ms apart, p10 248 ms. Those two ranges barely overlap, which is the right basis for a rejoin window.

## Touchpad size

From the phone's `getevent` and `dumpsys input`, and the calibration profile.

| Fact | Value |
|---|---|
| Sensor | `touchPad`, one finger, 1080 x 600 raw steps |
| Reports | One point at a time: X, Y and `TOUCH_MAJOR` 0-255 (contact size). No pressure and no minor axis |
| Horizontal coverage | Q centre to P centre: 8.9 of 10 key widths, about 120 raw steps a key |
| Vertical coverage | Top-row centre to Space-row centre: 3.1 of 4 rows, about 190 raw steps a row |
| Observed reach | x 0.056-0.949, y 0.116-0.885 of the full pad |

Consequences in the data:

- **The top half of the top row and the outer halves of the edge columns are not sensed.** A finger on those keys sits at the edge. 2.6% of samples are at the top edge, and most stroke breaks happen on the top row.
- **Contact size is not logged.** It is the main unused signal: `TOUCH_MAJOR` could separate a thumb from a fingertip, spot a resting or rolling finger, and show an edge-of-sensor contact. Add it to the raw log before the next data collection.
- **Gesture keys.** The touchpad also sends firmware key events: 436 lines, all presses being code 404. The log marks these with `g`, and recognition works only from the points.

## Mistouches and accidental input

3,845 touches in the log.

| What it became | Touches |
|---|---|
| Swipe | 1,438 |
| Tap (types nothing, Tap keys is empty) | 685 |
| Delete flick | 401 |
| Short touch, no effect | 934 |
| Moved up to 1.5 keys, no effect | 174 |
| Moved over 1.5 keys, no effect | 213 |

- **Touches during typing:** 78 per 100 key presses outside the prompted blocks. 505 of 2,530 overlapped a key press.
- **Accidental swipes:** 86 of 1,084 swipes (7.9%) produced a word that was later deleted with nothing put in its place. They are the upper bound on accidental swipes, because a deletion can also be a change of mind. A key press in the 700 ms before raises this only slightly: 10.4% against 7.7%. So key-press proximity alone won't catch them.
- **Very short strokes:** 80 strokes moved under 2 keys, some as short as 38 ms, and 18 were later removed. Prompted examples turned `messages` and `that` into `ok`.
- **Delete flicks:** median 77 ms and 2.0 keys. **20 of 181 labelled deletes (11%) removed a word that was right.** The rest removed wrong ones. Those 20 can be either misread strokes or changes of mind.
- **Moved over 1.5 keys, no effect (213):** 90 started on the Space row, 54 have no outcome recorded, 38 were long slow drags, 27 had a key pressed during them and 4 started just after a key press.
- **Two-finger handovers:** 141 contacts ended with another starting within 12 ms and 1.2+ keys away.
- **Physical key typos:** 6.9% of 2,324 typed letters were backspaced within 1.5 s.

## Signals not used yet

- **The engine's own confidence is strong.** Gap between its first and second word, prompted first tries:

| Score gap | Words | First-try right |
|---|---|---|
| Under 2 | 48 | 43.8% |
| 2-5 | 42 | 40.5% |
| 5-10 | 58 | 62.1% |
| 10-20 | 89 | 83.1% |
| 20+ | 167 | 96.4% |

  Hand-corrected text gives the same shape (36.2% to 88.9%). A small gap could trigger a different decision.
- **The strip:** the second word was chosen 57 times, the first 23 and the third 22.
- **Context:** first word of a sentence is no worse than later words here (80.0% against 76.3%, prompted, n=15).

## Limits

- One person, one phone, one day. With 404 word positions the 76.5% carries roughly a 4-point margin either way, so judge a change by which words flip on the same strokes, and confirm on the held-back third.
- The hand-corrected label is good only where the owner noticed the word. Unnoticed errors count as right there, so those numbers are an upper estimate. Three prompted final words stayed wrong, so the leak is small.
- The stroke classes describe what happened alongside each failure. They suggest causes but do not prove them.
- Everyday windows have not all been classified. Only the ones the reference timeline confirms were removed.
- Key positions in this analysis use `Layout.kt`: the bottom letter row starts one key in, because Alt fills the first column.

## Where the work should go first

In the order the data suggests:

1. **Keep truncated and broken strokes intact.** Cut-short strokes almost never succeed (0 of 18 prompted, 2 of 17 hand-corrected). The rejoin decision has a clean time signal to use, and most breaks are on the top row.
2. **Handle the sensor's edges and each hand.** Start and end misses are concentrated at top-right keys and differ between thumbs.
3. **Find why the decoder misses the right word entirely** on half of failures. Run it with wider tolerance at the ends of the stroke, using the current replay.
4. **Reconsider the rerank layer.** It changes 66 first words on prompted text for a net gain of only 4.
5. **Use the confidence gap** to decide when to show alternatives or defer a commit.
6. **Log contact size** so the next dataset can tell fingers and accidental touches apart.

## Files

| Path | Contents |
|---|---|
| `report/baseline.json` | The headline numbers |
| `report/replay-4f75dcb.json` | Replay scoring, including decoder depth |
| `report/hand-subsets.json` | Hand-corrected subsets |
| `report/metrics.json`, `metrics-hand.json`, `micro.json`, `touches.json`, `signals.json` | Full tables |
| `$TK_PRIVATE` | Attempts with text and points. Personal, never in the repo |
| `tools/` | Everything to rebuild this and test an engine change |
