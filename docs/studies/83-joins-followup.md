# #83 follow-up: the phone's readings, a richer geometry rule, and newer data

The owner's worry: an accidental join "when the first word ends at the top row and the second starts also at the top row ... say 'Elephant' and 'Ontario'". The context pilot ([`83-joins-context.md`](83-joins-context.md)) left two open questions:

- Would the phone's own readings change its answer?
- Could a richer geometry rule do better than today's?

This answers both on the same saved logs. No app code changed.

## Answer

**Neither cuts the top-row wrong joins at the owner's bar, which is no right join lost. On these logs the wrong joins look like right joins, in their shape and in the words around them. More data is the next step.**

- **The phone's readings:** the context model's result holds. With the phone in place of the PC, the safest veto still cuts 2 of the 37 wrong joins, as in the pilot. With all three readings from the phone, it cuts 1.
- **A richer geometry rule:** weighing five or fourteen measures cuts 1 or 2 wrong joins, and loses 1 right join each time. Today's `sameStroke` already keeps apart all but 1 of the 62 pairs the owner kept apart. The wrong joins it still makes sit among the right joins.
- **Newer data:** there's no raw log on the PC newer than the one the study used, which ends on 2026-11-11. The weeks since are only in TitanKeys Debug, on the phone.
- **A recording session:** [`83-joins-recording.md`](83-joins-recording.md), 10 minutes, for the owner to run. Its export also brings the weeks of log already on the phone.

## 1. The context model with the phone's readings

`phone-replay.sh` read every pair that could join three ways on the phone, with the app's whole recogniser: joined, first alone and second alone. [`83-joins/phone_context.py`](83-joins/phone_context.py) scores those readings exactly as the pilot did.

**Coverage:** 517 of the 563 top-row pairs. The replay holds only pairs that could join, so it's missing 25 right non-joins and 21 others kept apart by a long gap. Every right join, wrong join, unclear and missed join is in it.

**The phone against the log**, how often the phone's top word is the one the log kept:

| reading | phone | the pilot's PC |
|---|---|---|
| joined word, joined pairs | 388 of 440 | 278 of 440 |
| first word | 433 of 517 | 294 of 563 |
| second word, pairs kept apart | 69 of 77 | 80 of 123 |

So the pilot's main doubt, readings from a recogniser that isn't the app's, is gone.

**Separation**, the chance a one-word pair scores above a two-word pair, text before only:

| | the pilot (log + PC) | log + phone | all from the phone |
|---|---|---|---|
| right join vs right non-join | 0.96 | 0.95 | 0.93 |
| right join vs wrong join | 0.74 | 0.75 | 0.71 |
| one word vs two | 0.87 | 0.84 | 0.81 |

**Margin by label**, log + phone, nats: right join median 10.4 (lowest −6.2), wrong join 4.1 (−18.8 to 24.1), right non-join −3.9, missed join 12.1.

**A veto on today's geometry** (`sameStroke` joins 430: 346 right, 37 wrong, 44 unclear, 2 other, 1 right non-join). It keeps a pair apart when the margin is at or below t:

| t | log + phone: right joins lost | wrong joins cut | all phone: right joins lost | wrong joins cut |
|---|---|---|---|---|
| −15 | 0 | 1 | 0 | 1 |
| −10 | 0 | 2 | 1 | 2 |
| −8 | 0 | 3 | 2 | 4 |
| at the lowest right join | 0 (−6.18) | 4 | 0 (−13.30) | 1 |
| −4 | 1 | 9 | 5 | 9 |

The safe veto still cuts 2 of 37, with 3.8 nats of room in the log + phone way. Reading all three from the phone makes it worse: one right join drops to −13.3.

## 2. A richer geometry rule

[`83-joins/geometry.py`](83-joins/geometry.py) fits a weighted sum of numbers the keyboard already has at join time (logistic regression, no model at run time) on the 462 top-row pairs whose answer is known: 356 one word, 106 two.

Everything is leave-one-out, the threshold too. Each pair is judged by weights and a threshold fitted on the other pairs, with the threshold just under their lowest right join. So a right join the rule would lose shows up as lost.

| | separation | right joins kept (349) | wrong joins joined (44) | right non-joins joined (62) | missed found (7) |
|---|---|---|---|---|---|
| today's `sameStroke` | 0.807 | 346 | 37 | 1 | 0 |
| five measures, alone | 0.903 | 348 | 43 | 37 | 7 |
| five measures, as a veto on `sameStroke` | | 345 | 36 | 1 | 0 |
| fourteen measures, as a veto on `sameStroke` | 0.896 | 345 | 35 | 1 | 0 |

- **The five:** the pilot's gap, landing speed, carries on, turns back and distance.
- **The fourteen** add the end and landing directions, the jump's direction, and the two rows.
- **Unclear and other:** the vetoes leave them as `sameStroke` does (44 and 2 joined), and fourteen measures cut 1 unclear.

**Why the higher separation doesn't help:** it ranks the pairs `sameStroke` already settles, mostly pairs kept apart by a long gap or a jump. Used alone at the owner's bar, the threshold must sit low enough for the last right join. That joins 37 of the 62 pairs kept apart, against `sameStroke`'s 1. Used as a veto, the wrong joins left are inside the right joins' range on every measure.

**One measure at a time**, as a veto on `sameStroke`, cutting beyond the most extreme right join:

- The first swipe still moving down at its end cuts 3 wrong joins and no right non-join. Right joins reach 0.0006, and the 3 wrong joins sit at 0.0009, 0.0035 and 0.0037.
- The two with room are both from 1.2.0. Too few to build on.
- A gap above 380 ms cuts 1.
- No other measure cuts any.

**Wrong joins `sameStroke` still makes, by version:** 24 from 1.2.0, 11 from 2.0.0, 2 from 2.1.0. The log has little 2.1.0 swiping, so this can't say whether 2.1 makes fewer.

## 3. Newer data

- The newest raw log is `~/tk-data/rawlog-20261111-1548/rawlog/raw-log-20261111-154845.jsonl`, the one #83 used. Its records run from 2026-11-09 12:02 to 2026-11-11 20:37 UTC. Each export holds all the earlier records, so it covers the logs from 1.2.0 on.
- Nothing newer is in `~/tk-data`, `~/build` or `X:\Atlas\.dev\swipe-baseline\private`, or in the Windows home folder (four levels down).
- The weeks since are only on the phone, in TitanKeys Debug's typing log. They come out with **Settings > Typing log > Export log**.

## 4. A recording session

[`83-joins-recording.md`](83-joins-recording.md), about 10 minutes in TitanKeys Debug with the typing log on:

- top-row pairs at a normal pace (elephant ontario, you were, type quite, write it and 11 more), three times each;
- long words on the top row;
- five sentences full of top-row pairs;
- long words fast across the middle rows.

The owner fixes mistakes as usual, which is how the log labels joins. The export at the end also brings the weeks already logged.

**What it needs to settle the question:** about 100 wrong-join episodes on 2.1 or later, against 37 now from all versions. One session will add some; the weeks of everyday logs matter more.

## Reproduce it

In WSL `atlas-build`, as `dev`, after `83-joins/run.sh` and `83-joins/phone-replay.sh`, with the apostrophe study's venv:

```bash
~/build/apostrophe-venv/bin/python3 -I docs/studies/83-joins/phone_context.py "$PWD" ~/build/joins83/private "$PWD/tools/dev/swipe-baseline/tools"
~/build/apostrophe-venv/bin/python3 -I docs/studies/83-joins/geometry.py ~/build/joins83/private
```

Both print counts and margins only. `phone_context.py` writes per-pair numbers, with no words, to the private folder.
