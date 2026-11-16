# #83: Fewer swipes joined by accident at the top row

The pilot study the owner asked for: a deep dive into the saved logs to find how joining could be smarter, and more rules for when to join. It's a study only. Nothing in `Swipe.java` or `Composer.kt` changed. Engine 2.0 and the models are untouched.

Numbers only. No typed words appear here, in the scripts' output or in `report.json`.

## Summary

- The saved logs hold **502 joins**. Labelled from what the owner did next: **380 right**, **64 wrong** (46 separate episodes), **58 unclear**. There are also **18 missed joins**: slips left in two pieces.
- Wrong joins are far more common than the "3 wrong" in the 2.0 notes. That count only looked at joined words that were deleted outright. Most wrong joins are *fixed*: the owner kept the first swipe's own word instead of the joined one.
- Most wrong joins aren't two words merged. In 45% of them the second touch is tiny: under half a key and under 60 ms, a bounce or a resting finger. The rest are a clear first word with a late second swipe glued on.
- Today's window behaves well for real top-row word pairs. 35 kept pairs ended and started on the top row inside today's window, and the code kept every one of them apart.
- No rule cuts wrong joins without losing any right join. Three rules come close, and two new rules find missed joins at no cost to kept pairs. See the recommendation.

## Material and method

**Log.** The newest saved raw log, `raw-log-20261111-154845.jsonl` in WSL `~/tk-data/rawlog-20261111-1548/`. It contains all the earlier logs (same first header) and runs from 1.2.0 through 2.1.0. 6,291 swipes, 4,280 recognised. The attempt table is rebuilt with the baseline's `build.py`, into `~/build/joins83/private`, outside the repo.

**Pairs.** `pairs.py` takes every two swipes in a row in the same field where the second came 0 to 1,000 ms after the first, plus every logged join. That's 2,306 pairs. For each it measures:

- the time gap;
- where the first ends and the second starts (height in letter rows, distance in keys);
- direction and speed over the last and first 40 ms, as `Swipe.motion` does;
- the shape: length in keys, duration, point count;
- the recogniser's scores as logged: the lead of the best word over the next (margin) and the path score per letter;
- the contact size at the end of the first and the start of the second (logged from 2.0 on);
- whether the words kept end and start on the top row (checked inside the script; only the yes/no leaves it).

**The app's own code.** `JoinReplay.java` runs `Swipe.couldContinue` and `Swipe.sameStroke` from `android/kotlin/com/titankeys/engine/Swipe.java` on every pair, compiled on the desktop as-is. It reproduces **483 of the 502** logged joins as a same-stroke join, so the geometric part of the replay is the keyboard's real path. Of the 19 it doesn't reproduce, 1 went through the 150 ms check, which needs the recogniser. The other 18 fail `couldContinue` in the replay: 6 are mid-pad joins in 2.1.0 that look like the touch-level piece joining in `SwipePieces.kt`, and the rest probably come from small differences between the points rebuilt from the log and the ones the keyboard held.

**Labels**, from what happened next:

| Label | Rule | Count |
|---|---|---|
| Right join | Joined, and the joined word was kept. Or joined, then fixed to a word that needs both swipes (it fits the joined path clearly better than the first swipe alone) | 380 |
| Wrong join | Joined, then fixed to the first swipe's own reading, or to a word that fits the first swipe alone as well as both | 64 (46 episodes) |
| Unclear | Joined word deleted and nothing kept in its place | 58 |
| Missed join | Not joined, both words replaced by one word that needs both swipes (mean distance to its letters under 1 key, and 0.3 keys better than the first swipe alone) | 18 |
| Right non-join | Not joined, both words kept | 1,120 |
| Other | Not joined, one of the two corrected | 666 |

A run of joins (piece A, then B, then C) is judged by the chain's final word. An *episode* is one such run, so a single resting-finger run of 16 tiny joins on the bottom row counts once.

**Checking the wrong-join label.** If two words had been merged, the owner's next kept word should fit the second piece's path. It does in only 5 of 31 wrong joins, about the same rate as for right joins. Retyped words often come from the physical keys, which leave no swipe in the table, so this can't confirm or rule out "two words merged". What the label does show is that the first swipe was a word of its own.

**Rules.** `rules.py` replays each rule on every labelled pair, starting from what the keyboard did. Rules tuned on one stretch were checked on the other: 1.2.0 (332 joins) against 2.0 and 2.1 (170 joins).

## What tells them apart

Median, with the 10th and 90th percentile, for the groups that matter. "In window" means pairs that today's `couldContinue` would let join, kept apart, and both words kept.

| Measure | Right join (380) | Wrong join (64) | Right non-join in window (35) | Missed join (18) |
|---|---|---|---|---|
| Gap (ms) | 67 (29 to 143) | 86 (9 to 258) | 285 (171 to 381) | 123 (10 to 218) |
| First ends at (rows from top) | 0.21 | 0.25 | 0.26 | 0.25 |
| Second starts at (rows) | 0.16 | 0.18 | 0.22 | 0.25 (p90 0.52) |
| Jump (keys) | 1.3 | 1.0 | 2.0 | 1.9 |
| Second lands moving (0.004 keys/ms or more) | 74% | 33% | 14% | 56% |
| Carries on the way it left (cosine) | 0.98 | 0.85 | -0.95 | 0.46 |
| Second touch length (keys) | 3.8 | 0.8 | 6.9 | 4.1 |
| Second touch tiny (under 0.5 keys, 60 ms) | 7% | 45% | 0% | 0% |
| First word's margin | 5.5 | 5.4 | 23.0 | 6.0 |
| First word's path score per letter | -1.11 | -0.76 | -0.05 | -0.86 |
| Contact size at the first's end (steps) | 1.57 | 1.86 | 2.00 | 1.71 |
| Contact size at the second's start | 1.50 | 1.67 | 2.00 | 2.00 |

What this says:

- **Time is the cleanest clue for right joins.** 90% of right joins come within 143 ms. Kept pairs in the window are 171 ms or more.
- **A finger coming back is moving; a new word lands still.** Today's `moving` clue already uses this.
- **A clear first word is the best clue against joining.** Kept pairs have a first word that led by 23 and fit almost perfectly. Right joins lead by 5.5 and fit poorly, because the first piece is half a word.
- **Contact size helps.** A finger sliding off the edge ends with a smaller contact. A finished word ends at full size.
- **Wrong joins look like right joins on most measures.** Their second touch is short and their first word is often unclear. That's why single thresholds fail.
- **Top-row word pairs aren't today's problem.** 89 kept pairs ended and started on the top row within 400 ms, and 35 of them fell inside today's window, where `couldContinue` let them join. `sameStroke` and the 150 ms check turned all 35 down. Their second swipe lands still and goes the other way (median cosine -0.95), so `sameStroke` already turns them down.
- **Missed joins mostly break below the top edge.** 9 of the 18 start lower than today's 0.267 rows, 6 end below the top row, and 2 have gaps over 400 ms. Today's window can't reach them.

## Rules tried

Counts against today. "Headline" is a rough change in prompted first choice: each fixed pair in the prompted set counts +1, each broken one -1. For the adding rules it's a ceiling, since a joined swipe only helps if the engine reads it right, and that needs a phone replay. The log's own headline: 278 of 404 prompted first tries (68.8%) and 155 of 264 everyday first tries (58.7%) had the right first choice as read live.

### Cutting wrong joins (502 joins: 380 right, 64 wrong in 46 episodes, 58 unclear)

| Rule | Right joins lost | Wrong cut (episodes) | Unclear cut | Headline |
|---|---|---|---|---|
| C1. Second touch over 250 ms later | 11 | 9 (8) | 2 | -2 |
| C2. Over 150 ms later, and the first word was already clear (margin 10+, path score -0.3 or better per letter) | 4 | 8 (8) | 1 | +4 |
| C3. First word clear and second swipe 5+ keys long | 12 | 7 (7) | 0 | -4 |
| C4. Second lands still | 48 | 11 (11) | 13 | -34 |
| C5. A still, tiny touch outside the top-row window | 4 | 16 (1) | 0 | -3 |
| C6. Over 150 ms later, and the first ended with a full-size touch (2+ steps) | 1 | 7 (7) | 2 | +6 |
| C7. C2 or C6 | 4 | 12 (12) | 3 | +8 |

- C6 only acts on the 2.0 and 2.1 logs, which have contact size. There it cuts 7 of 23 wrong episodes and loses 1 of 162 right joins. That join hadn't changed the word.
- Of the 4 right joins C2 loses, 2 hadn't changed the word.
- Rules tuned on 1.2.0 alone, with zero right joins lost there, kept losing right joins on 2.0 and 2.1. A search over every pair of measures found none that holds at zero.

### Finding missed joins (18 missed, 1,120 right non-joins)

| Rule | Missed found | Right non-joins joined | Other pairs joined | Headline (ceiling) |
|---|---|---|---|---|
| A1. Lands moving within 150 ms and 2 keys, anywhere on the pad | 4 | 0 | 6 | +3 |
| A2. Lands moving within 150 ms, anywhere | 8 | 1 | 19 | +5 |
| A3. Today's window widened to the whole top row, when it lands moving within 150 ms | 3 | 0 | 7 | +3 |
| A4. Within 100 ms and 2 keys, anywhere | 3 | 0 | 8 | +2 |

"Other" pairs had one of the two words corrected, so whether joining them helps is unknown without the recogniser.

The 150 ms both-readings check in `Composer` would still guard every adding rule: within 150 ms, a join that isn't a same stroke only goes through when the joined reading is clearer. That check needs the recogniser, which runs only on the phone, so it isn't in these counts. It can only cut joins these rules add, which makes the right-non-join column a worst case.

## Recommendation

Build two changes, both small and both in the joining rule:

1. **Cut: C7.** Don't join when the second touch comes **over 150 ms** after the first and either **the first swipe already read as a clear word** (margin 10 or more, path score -0.3 or better per letter) or **it ended with a full-size touch** (2 steps or more). On the saved logs it cuts 12 of 46 wrong-join episodes and loses 4 of 380 right joins, 2 of which hadn't changed the word.
   - If the owner holds to "no right join lost", take **C6 alone**: 7 wrong episodes cut, 1 right join lost, and that join hadn't changed the word.
2. **Add: A1.** Join a second touch that **lands moving, within 150 ms and 2 keys, anywhere on the pad**, not only at the top edge. It's the flicker rule extended from 90 ms to 150 ms when the finger is plainly still travelling. It finds 4 of 18 missed joins and joins no pair the owner kept apart.

Not recommended:

- A still second touch alone (C4) loses 48 right joins.
- Dropping tiny second touches loses right joins, because a still touch at the sensor's top edge often adds a real last letter.
- Any rule built on top-row word pairs: today's code already keeps them apart.

The rest of the wrong joins (the ones inside 150 ms with an unclear first word) can't be told apart from right joins with what the logs hold. Getting those needs the joined and separate readings side by side, below.

## What new data would settle it

1. **A phone replay of both readings** (no new swiping needed). `stitch_export.py` and `replay.sh` from the baseline tools can read every pair here both joined and apart on the phone with the app's recogniser. That gives the real headline change for every rule, tells which "other" pairs A1 and A2 would fix, and lets a both-readings rule be tried past 150 ms. The Master or the owner runs it; it takes the phone.
2. **A few days of normal swiping on the 2.1 or later Debug build, then a raw log export from TitanKeys Debug.** Only 170 joins come from 2.0 or later, and only those carry contact size. C6 and C7 rest on 27 wrong joins with size. Another 200 or so would show whether the size clue holds.
3. **The recorded set of top-row word pairs** from the issue ("you were", "type quite", "write it"), at normal pace. It checks the finding that today's window already keeps them apart, and it gives labelled pairs with no guessing.
4. **Missed joins in the middle of the pad.** A1 rests on 4 cases. A short session swiping long words fast across the middle rows would show how often the sensor drops a finger there.

## Built

The Master took the issue's default, "lose none of the right joins". The build keeps one rule:

- **Join less (C6).** Over 150 ms after the last swipe, a swipe that ended with a full-size touch doesn't join: a mean of 2 sensor steps or more over its last 60 ms. The size comes from `Sample.step`, the same `round(getSize() × 255)` the study read from the log. `Composer` and `SwipePieces` pass it to `Swipe.sameStroke`. When the size isn't known, it's left out.
- **Join more (A1), taken back after review.** It was built first: a touch landing still moving, under 150 ms after the last swipe and 2 keys or less from where it ended, joined at once wherever it was on the pad. The review below found that 5 of the 9 pairs it joined were kept by the owner as two words, so those touches now go to Composer's 150 ms check of both readings, as they did before #83.
- Left out: the "over 150 ms after a clear word" half of C7. It loses 4 right joins.

On every saved pair, run with the final `Swipe.java` against the one before (`confirm.py`):

| | Count |
|---|---|
| Right joins lost | 1 of 380 (it hadn't changed the word) |
| Wrong joins cut | 7 of 64 (7 of 46 episodes), all in the 2.0 and 2.1 logs |
| Unclear joins cut | 2 of 58 |
| Missed joins found by shape | 0 of 18 (the 150 ms check may still find some: only the phone can tell) |
| Pairs the owner kept apart, now joined | 0 of 1,120 |
| Other pairs now joined (one word was corrected) | 0 of 666 |
| Headline, rough | +6 prompted first tries (a ceiling: it assumes each change gets the word right) |

`tests/swipe/joins.sh` checks the rule on hand-made swipes, including the review's examples, and pins these counts on `tests/swipe/join-pairs.txt`: the 645 pairs either version could join, every logged join, every missed join and every pair within 150 ms. Each pair keeps only the last 100 ms of the first swipe and the first 100 ms of the second, plus one point either side, which is all the joining checks read, and gives the same decision as the whole pair. No words are in it.

Only the phone can show whether the joins it cuts, and the ones the 150 ms check makes, give the word the owner kept. `83-joins/phone-replay.sh` reads every one of those pairs three ways with the app's recogniser and scores them.

## Review: quick separate swipes

A reviewer asked whether two quick separate words can be glued into one. Both risks were checked on the saved logs.

**1. The 150 ms check of both readings (`Composer`, older than #83).** Within 150 ms, a pair is read both ways and joined when the joined reading leads its runner-up by more than the first word leads its own. The worry: a longer path may lead by more just for being longer, so "in" then "to" becomes "into".

- 525 of the 2,306 saved pairs came within 150 ms. 89 were kept apart, 38 of them two words of 3 letters or less. The check came in with 2.0.0; 1.2.0 had none.
- The fair test is a replay of both readings. The one from 9 November (`.dev/swipe-baseline/tools/join_gate.py`) read 24 pairs within 150 ms both ways. The check joined 11 and fixed 8; of the 11 pairs where the first word alone was right, it glued **none**. 18 of the 24 had a first word of 3 letters or less; there too it glued none and fixed 8.
- Every stricter version tried loses 2 of those 8 fixes and stops no glue: a lead of 1 to 8 more, 1.25 to 2 times the first word's lead, or the leads per letter.
- In the 2.0 and 2.1 logs, 15 joins came about some other way than their shape (most through the check): 5 right, 2 wrong, 8 unclear. The 2 wrong ones led by 0.15 and 3.6 more than the first word. A lead of 2 more would cut one of them and lose none of the 5 logged right joins, but it loses 2 of 8 in the replay.

So the check stays as it is. The phone replay below now runs the check on every pair within 150 ms (as before #83, now, and with a lead of 2) to settle it with more pairs.

**2. Joining a quick touch that lands moving (A1).** The worry: almost every swipe moves at its first point, so "go" then "on" from beside O becomes one word.

- Most new words land still. 16% of the second swipes the owner kept apart moved 0.004 keys/ms or more over their first 40 ms (median 0.0004), against 29% of all second swipes.
- Within 150 ms and 2 keys, the 16 pairs the owner kept apart all landed still (0.0012 keys/ms at most), so A1 joined none of them.
- But A1 also joined 5 "other" pairs: the owner kept two words and corrected one. 4 of those 5 are two words of 2 or 3 letters. Nothing in the logs tells them from the 4 missed joins A1 found: start speed, direction and distance overlap, and only 2 of the 9 have sizes.

So A1 no longer joins at once. Those touches go to the 150 ms check, which glued no separate words in its replay. 3 of the 4 missed joins are from 1.2.0, which had no check, so the check may still find them; the phone replay counts it.

**An older case the review's example shows.** If "on" lands already moving beside O, the top-edge rule from before #83 joins it: it left the top row, came back at the top edge nearby, and heads back the other way while moving. The logs don't show it: of 35 kept top-row pairs inside that window, none joined, because their second swipe landed still. A touch within 90 ms and 1.5 keys joins as a flicker whatever it does, by design.

## Reproduce it

In WSL `atlas-build` as `dev`:

```bash
OLD_SWIPE=old-Swipe.java bash docs/studies/83-joins/run.sh ~/tk-data/rawlog-20261111-1548/rawlog/raw-log-20261111-154845.jsonl
```

`old-Swipe.java` is `Swipe.java` from before the rule, saved on Windows with `git show 93eb16d:android/kotlin/com/atlas/engine/Swipe.java`, with its first line changed to `package com.titankeys.engine;`. Add `FIXTURE=tests/swipe/join-pairs.txt` to write the fixture again.

| File | Does |
|---|---|
| `83-joins/run.sh` | Builds the attempt table if needed, then runs the three steps below. Private files go to `~/build/joins83`, outside the repo |
| `83-joins/pairs.py` | Finds and labels every pair, and measures it |
| `83-joins/JoinReplay.java` | Runs the app's `Swipe.couldContinue` and `Swipe.sameStroke` on each pair |
| `83-joins/rules.py` | Replays the rules and writes the counts |
| `83-joins/confirm.py` | Compares the final `Swipe.java` with the one before on every pair, and writes the test fixture |
| `83-joins/replay_pairs.py`, `83-joins/phone-replay.sh` | The phone replay of every pair that could join, with the 150 ms check |
| `83-joins/report.json` | The counts from the run above |

The scripts live here, not with the baseline tools in `.dev/swipe-baseline/tools`, because that folder isn't in the repo and the Master reviews this branch. They read the baseline's `rawlog.py` and `build.py` from there (set `BASELINE_TOOLS` to move it).
