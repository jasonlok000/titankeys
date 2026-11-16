# #83 pilot: can the context model tell joins at the top row?

The owner asked for "a pilot investigation to see whether the context engine could predict whether the words should be joined or not, when they end on the top row and resume on the top row". This measures it on the saved logs with the model TitanKeys already has, the on-device DistilGPT-2 used for apostrophes. No app code changed.

## Answer

**Needs more data, and on these logs it adds little to what the keyboard already uses.**

- **It does tell joins from words the owner kept apart.** A right join scores above a kept-apart pair 96% of the time. But the shape of the swipe already settles those pairs: the keyboard's `sameStroke` joins 1 of the 62.
- **It's much weaker on the pairs that matter, the wrong joins.** A right join scores above a wrong join only 74% of the time. The safest veto that loses no right join cuts 2 of the 37 wrong joins the keyboard makes now, with 2.4 nats to spare. Pushed to the edge, it cuts 4, with no room at all.
- **Together with the geometry it adds nothing measurable.** A model of gap, landing speed, direction and distance separates one word from two 0.904 of the time. Adding the margin makes it 0.906.
- **It's fast enough.** Scoring both readings takes 74 ms (median) on one PC thread, starting cold.

What would settle it is below. The follow-up ([`83-joins-followup.md`](83-joins-followup.md)) re-scores with the phone's own readings and fits the richer geometry rule. Neither cuts the wrong joins at the owner's bar.

## Method

- **Pairs:** every saved pair from the #83 study (`83-joins.md`) whose first swipe ends on the top row (y < 1/3) and whose second starts there, with 400 ms or less between them. The labels are the #83 study's.
- **Readings:** two for each pair, the joined word and the two separate words.
  - From the log where it has them: the first swipe's word; the second swipe's word when it stood alone; the joined word when the pair was joined.
  - The rest come from the app's CTC recogniser (`android/assets/swipe2/ck-titan.onnx`, with `english.combined`), run on this PC with the baseline's `ck_core.py`. That's the joined reading of a pair kept apart, and the second swipe alone in a pair that was joined.
  - The PC recogniser isn't the whole engine (no ranker, no FUTO engine), so its top word matched the log's 52% to 65% of the time. As a check, a second run takes all three readings from the PC. It gives nearly the same separation (0.94 against 0.96), so mixing the two sources doesn't make the result.
- **Scoring:** as the were/we're study (`73-were-study.py`, imported unchanged):
  - the app's byte BPE, reading the last 32 tokens;
  - the window back to the last sentence mark, at most 10 words.
  - margin = score(text before + joined word) − score(text before + the two words), in nats. A positive margin favours one word.
- **Two contexts:** text before only, which is all the keyboard has when it decides; and with up to 2 words after. The words after are the next swiped words the owner kept, each within 5 s. The attempt table holds no typed words, so this is rough. 387 of the 563 pairs had words after.
- **Tool:** [`83-joins/context.py`](83-joins/context.py). It prints counts and margins only. Per-pair numbers, with no words, go to the private folder.

## Numbers

### The pairs

563 top-row pairs within 400 ms. 504 of them fell inside the window `couldContinue` allowed before #83.

| label | pairs | inside the window | |
|---|---|---|---|
| right join | 349 | 348 | |
| wrong join | 44 (41 episodes) | 44 | |
| unclear | 47 | 46 | deleted, nothing kept |
| missed join | 7 | 2 | **too small to say anything alone** |
| right non-join | 62 | 35 | |
| other | 54 | 25 | one of the two words corrected: the right answer is unknown |

Most of the wrong joins come from 1.2.0 (24); 15 are from 2.0.0 and 5 from 2.1.0. Too few of each version to compare.

### Margin by label

Text before only, in nats.

| label | median | 10th–90th percentile | lowest | highest |
|---|---|---|---|---|
| right join | 10.2 | 3.3 to 18.3 | −7.6 | 37.2 |
| missed join | 15.3 | 11.9 to 23.0 | 11.9 | 23.0 |
| unclear | 7.8 | −2.5 to 14.3 | −8.6 | 19.2 |
| other | 3.7 | −3.6 to 12.6 | −11.8 | 23.5 |
| wrong join | 4.3 | −8.6 to 13.8 | −18.8 | 26.1 |
| right non-join | −3.2 | −11.4 to 3.6 | −12.8 | 13.4 |

**Separation**, the chance a one-word pair scores above a two-word pair:

| | text before | with words after | all readings from the PC |
|---|---|---|---|
| right join vs right non-join | 0.96 | 0.97 | 0.94 |
| right join vs wrong join | 0.74 | 0.75 | 0.72 |
| one word (right and missed joins) vs two (right non-joins and wrong joins) | 0.87 | 0.88 | 0.85 |

The words after help a little with the pairs kept apart (median −3.2 → −8.1), and hardly with wrong joins.

### Thresholds

**Margin alone** (join when margin > t). There is no threshold that loses no right join and joins no kept-apart pair: the lowest right join scores −7.6, the highest kept-apart pair 13.4.

| t | right joins kept (349) | right non-joins joined (62) | wrong joins joined (44) | missed found (7) |
|---|---|---|---|---|
| −8 | 349 | 49 | 39 | 7 |
| −4 | 347 | 34 | 33 | 7 |
| 0 | 337 | 20 | 27 | 7 |
| 4 | 306 | 4 | 23 | 7 |
| 6 | 276 | 1 | 19 | 7 |

Alone, it is worse than the geometry, which joins 346 right joins and 1 kept-apart pair.

**On top of the geometry** (`Swipe.sameStroke` as built for #83: 430 pairs joined, 346 right joins, 37 wrong, 44 unclear, 2 other, 1 right non-join):

- **A veto: keep apart when margin ≤ t.**

  | t | right joins lost | wrong joins cut | unclear cut |
  |---|---|---|---|
  | −15 to −10 | 0 | 2 | 0 |
  | −7.62 (the edge) | 0 | 4 | 1 |
  | −6 | 1 | 5 | 3 |
  | −4 | 2 | 8 | 3 |

  The edge also cuts the 1 kept-apart pair the geometry joins. With 349 right joins, a veto at the edge is fitted to one pair; −10 leaves 2.4 nats of room.
- **An add: join a pair the geometry keeps apart when margin > 13.44,** above every two-word pair kept apart. It finds 6 of the 7 missed joins and 1 right join, but also joins 4 "other" pairs, whose right answer is unknown. With 7 missed joins and no room at the threshold, that's too few to trust.

**Combined, leave-one-out logistic regression**, one word (356 pairs) against two (106). Separation:

| | |
|---|---|
| margin alone | 0.865 |
| geometry alone (gap, landing speed, carries on, turns back, distance) | 0.904 |
| geometry and margin | 0.906 |
| the keyboard's `sameStroke` yes/no | 0.807 |

The margin adds nothing measurable to the geometry. The gap between 0.904 and 0.807 says the geometry itself has more to give than today's yes/no rule.

### Speed

Both readings, text before only, no cache, one thread on this PC: **74 ms median, 143 ms at the 90th percentile.** The model runs once per token of the candidate words, over the last 32 tokens, so the time grows with the words, not with the text before. The phone is slower, and it wasn't used, so its time is unmeasured.

## Limits

- **Readings:** the PC recogniser is the CTC part of the engine only, and half of the joined readings come from it.
- **Labels:** wrong joins are the pairs that matter, and there are 44 (41 episodes), spread over three versions. Missed joins: 7.
- **Words after:** only swiped words, so a typed word or a mark between is missed.
- **Text:** the context is the log's text before each swipe, as typed then, including words later corrected.

## What would settle it

1. **The real readings, both ways, on the phone.** `83-joins/phone-replay.sh` already reads every pair joined, first alone and second alone with the app's whole recogniser. Running `context.py` on those readings instead of the PC's would remove the main doubt, with no new swiping.
2. **More wrong joins from 2.x.** A few weeks more of the owner's normal logs would roughly double the 20 from 2.0 and 2.1. At least 100 wrong-join episodes would show whether a veto around −10 nats holds.
3. **Before it is built, a richer geometry model.** On these pairs it gets most of what the margin could add, and it needs no model at join time.
