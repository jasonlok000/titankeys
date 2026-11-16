# #83: the joins session, sample by sample

The owner asked to "look at my strokes on a micro level". This takes every labelled gap from the touchpad session ([`83-joins-capture.md`](83-joins-capture.md)): 49 real breaks inside a word and 154 deliberate gaps between two words.

For each gap it looks at the last 100 ms of the first touch and the first 100 ms of the second, sample by sample: position, contact size, speed, direction, and the time between samples. No app code changed.

## Answer

**At the owner's bar, losing no real break, one sample-level signal holds on both this session and the logs: which way the finger was moving as it lifted.**

- A real break at the top row is the finger sliding up, off the sensor. A finger ending a word stops, or drifts down, before it lifts.
- Keep two touches apart when the first one's last 40 ms head down faster than 0.001 keys per ms:
  - this session: 7 of 24 false joins cut, 0 of 39 joined breaks lost;
  - the logs: 2 of 37 wrong joins cut, 0 of 346 right joins lost.
- That's the same rule [`83-joins-capture.md`](83-joins-capture.md) proposed; the micro level confirms why it works.

**Everything else separates on average but not at the bar.**

- The finger slowing to a stop before the lift, a still landing, the time until the landing finger first moves, and where the first touch ends all tell the two apart 0.68 to 0.81 of the time.
- But at least one real break sits among the deliberate gaps on each of them, so a no-loss threshold cuts 0 to 4 false joins.
- Weighing all of them together, held out one gap at a time, cuts 18 of 24 false joins but loses 3 of 39 joined breaks.
- Carried to the logs, that combination loses 24 right joins.

**What can't be seen at this level:**

- The sensor reports a sample about every 9.5 ms. The lift arrives one frame after the last sample, the same for breaks and words (9.2 ms median each).
- There is no stall, thinning burst or late sample just before a lift that marks either kind.

## The three kinds of real break

The breaks, sorted by what happened, with what the rule does now:

| kind | breaks | joined by the rule | how it looks |
|---|---|---|---|
| slid off the top edge | 15 | 15 | ends within 0.006 keys of the edge, moving up at full speed (0.028 keys/ms); the size thins by 0.6 steps over the last 200 ms; the finger comes back at the edge already moving (0.006 keys/ms), its second sample one frame later (9.3 ms) |
| the sensor lost it mid-row | 30 | 22 | ends 0.17 keys below the edge, still moving up (0.028 keys/ms); comes back 1.2 keys from where it was heading, its second sample two frames later (18 ms) |
| a bounce (a touch of 3 samples or fewer, or back within 50 ms to the same spot) | 4 | 2 | too few to describe |

**A deliberate gap between two words** (154) looks different:

- the first touch slows down as it ends (0.019 keys/ms over its last 20 ms, against 0.028);
- it holds still for about one frame before lifting (9.8 ms, against 0 for a break);
- it isn't heading anywhere (vertical speed 0.000, against −0.002 for a break);
- it ends 0.28 keys below the edge;
- the next touch lands still: its second sample comes 28 ms later, and it takes 58 ms to move 0.25 keys (38 ms for a break).

The sensor sends a sample only when something changes: of 3,570 pairs of samples in a row, none repeat. So a late second sample means the finger landed and held perfectly still.

## Plots

Numbers only. Each case grid has a row per kind of gap and three typical cases, those whose gap is nearest that kind's median. Time runs from the first touch's last sample; the grey band is the gap.

- [Height](83-joins/micro-cases-y.png): the dashed line is the sensor's top edge.
- [Speed](83-joins/micro-cases-speed.png): a word's end slows to near 0 before the lift; a break leaves moving.
- [Contact size](83-joins/micro-cases-size.png).
- [The signals for every gap, by kind](83-joins/micro-signals.png): black bars are medians, and the dashed line is the 0.001 veto.

## Numbers

### Every gap in the session

Separation is the chance a break and a two-word gap fall in the expected order; 0.5 is no help. "At the bar" is the most extreme real break; beyond it, the count of two-word gaps it would keep apart.

| signal | break, median | two words, median | separation | at the bar |
|---|---|---|---|---|
| gap, ms | 85 | 170 | 0.93 | 64 of 154 above 180 ms |
| first touch ends below the top edge, keys | 0.14 | 0.28 | 0.81 | 5 |
| distance from where it was heading to where it came back, keys | 1.7 | 4.1 | 0.81 | 42 |
| distance to the next touch, keys | 1.5 | 3.3 | 0.78 | 79 |
| vertical speed over the last 40 ms, keys/ms (+ is down) | −0.0021 | 0.0003 | 0.76 | 5 |
| second touch: time to its second sample, ms | 18 | 28 | 0.75 | 5 |
| contact size over the last 40 ms, steps | 1.4 | 1.8 | 0.74 | 4 |
| second touch: time until it moves 0.25 keys, ms | 38 | 58 | 0.74 | 1 |
| speed over the last 20 ms against the last 100 ms | 1.47 | 0.80 | 0.73 | 3 |
| size thinning over the last 200 ms, steps | 0.49 | 0.00 | 0.69 | 1 |
| first touch: time held still before the lift, ms | 0 | 9.8 | 0.69 | 1 |
| second touch: speed over its first 20 ms, keys/ms | 0.0044 | 0.0006 | 0.69 | 1 |
| speed over the last 20 ms, keys/ms | 0.028 | 0.019 | 0.68 | 1 |
| time from the last sample to the lift, ms | 9.2 | 9.2 | 0.52 | 2 |
| time between the last two samples, ms | 9.5 | 9.5 | 0.51 | 0 |

### As a veto on the rule's joins

The 39 real breaks the rule joins against its 24 false joins. The bar loses none of the 39.

| signal | separation | false joins cut at the bar |
|---|---|---|
| vertical speed over the last 40 ms | 0.81 | 10 (edge 0.0003; no room: 0.03 of the breaks' spread) |
| gap | 0.88 | 7 (above 180 ms) |
| speed over the last 20 ms | 0.73 | 4 (below 0.005: no room) |
| distance to the next touch | 0.73 | 4 (above 3.26 keys: no room) |
| size thinning | 0.52 | 4 |
| slowing | 0.73 | 3 |
| all others | 0.52 to 0.78 | 0 to 3 |

### Carried to the logs

The logs are the #83 study's top-row pairs that `Swipe.sameStroke` joins: 346 right joins and 37 wrong joins. Only 142 and 16 of them have contact sizes, and they have no lift times.

| signal | this session's bar | logs: right joins lost | wrong joins cut |
|---|---|---|---|
| vertical speed over the last 40 ms, above 0.001 | 0 lost, 7 cut | **0** | **2** |
| vertical speed, at this session's edge (0.0003) | 0 lost, 10 cut | 4 | 3 |
| gap above 180 ms | 0 lost, 7 cut | 20 | 10 |
| speed over the last 20 ms below 0.005 | 0 lost, 4 cut | 65 | 3 |
| slowing below 0.25 | 0 lost, 3 cut | 36 | 1 |
| all signals weighed together | 3 lost, 18 cut | 24 | 13 |

On the logs alone the signals separate right joins from wrong joins far less well: 0.69 at best (where it came back against where it was heading), and 0.65 for the gap. The wrong joins the logs hold look like breaks at the sample level too.

## Trading some real breaks for fewer false joins

The owner asked whether giving up some real breaks would buy a better balance. [`83-joins/tradeoff.py`](83-joins/tradeoff.py) relaxes the bar to losing 0, 1, 2, 5 and 10% of the real breaks the rule joins, for four vetoes:

- **down:** the first touch's last 40 ms head down faster than t;
- **gap:** a gap longer than t;
- **still:** the second touch lands slower than t;
- **combined:** all position and timing signals weighed together.

**No: relaxing the bar doesn't pay.**

- **How often each happens.** In the logs (4,280 swipes), the rule makes 80.8 right top-row joins per 1,000 swipes and 8.6 wrong ones. A real break is 9.4 times as common as a wrong join.
- **What each costs.**
  - A wrong join glues two words into one: delete it, swipe both again, about 3 actions.
  - A lost break leaves two wrong half-words: delete both, swipe the word again, also about 3 actions.
  - So a trade gains only if it cuts wrong joins at over 9.4 times the rate it loses breaks: losing 1% of breaks needs over 9% of wrong joins cut.
- **On the logs, points that lose real breaks come out about even or behind.**
  - At 1% they break even.
  - Combined at 2% is +0.23 per 1,000 swipes, 7 wrong joins cut for 6 right joins lost: a gain of one fix in all 4,280 swipes.
  - At 5% and 10% every veto loses, down to −5 per 1,000.
- **The session alone looks better,** because its drills held many more false joins: 24 against 39 breaks, against 37 to 346 in the logs.
  - Losing 1 of its 39 breaks (5%) still comes out ahead at everyday rates: +2.25 for down, +2.61 for combined.
  - But the same thresholds lose on the logs (−0.23 and −1.87), and at 10% everything is behind.

### Threshold set on the logs' 346 right joins

Net is per 1,000 swipes, on the logs.

| rule | loss budget | logs: right joins lost | wrong joins cut (37) | net | session: breaks lost (39) | false joins cut (24) |
|---|---|---|---|---|---|---|
| down | 0% | 0 | 3 | +0.70 | 0 | 8 |
| down | 1% | 3 | 3 | 0.00 | 0 | 10 |
| down | 2 to 10% | 5 | 4 | −0.23 | 1 | 12 |
| gap | 0% | 0 | 1 | +0.23 | 0 | 0 |
| gap | 1% | 3 | 3 | 0.00 | 0 | 0 |
| gap | 5% | 17 | 7 | −2.34 | 0 | 3 |
| gap | 10% | 31 | 11 | −4.67 | 3 | 12 |
| still | 0 to 2% | 0 | 0 | 0.00 | 0 | 0 |
| still | 10% | 34 | 12 | −5.14 | 1 | 3 |
| combined | 0% | 0 | 1 | +0.23 | 0 | 0 |
| combined | 2% | 6 | 7 | +0.23 | 0 | 1 |
| combined | 5% | 17 | 9 | −1.87 | 0 | 10 |
| combined | 10% | 34 | 13 | −4.91 | 5 | 20 |

The down rule can't be pushed further. Many right joins have no vertical motion at all at the end (exactly 0), so budgets of 2 to 10% all land at t = 0 and lose the same 5.

### Threshold set on the session's 39 joined breaks

- **Net:** on the logs, a wrong join counted as one fix.
- **×2:** a wrong join counted as two fixes, if the owner feels it as twice as bad.
- **Weighted:** the session's shares at the logs' everyday rates.

All three columns are per 1,000 swipes.

| rule | loss budget | session: breaks lost | false joins cut | logs: right lost | wrong cut | net | ×2 | weighted |
|---|---|---|---|---|---|---|---|---|
| down | 0 to 2% | 0 | 10 | 4 | 3 | −0.23 | +0.47 | +3.60 |
| down | 5% | 1 | 12 | 5 | 4 | −0.23 | +0.70 | +2.25 |
| down | 10% | 3 | 16 | 56 | 10 | −10.75 | −8.41 | −0.46 |
| gap | 0 to 2% | 0 | 7 | 20 | 10 | −2.34 | 0.00 | +2.52 |
| gap | 10% | 3 | 16 | 39 | 13 | −6.07 | −3.04 | −0.46 |
| still | 0 to 2% | 0 | 0 | 13 | 6 | −1.64 | −0.23 | 0.00 |
| still | 10% | 3 | 4 | 44 | 13 | −7.24 | −4.21 | −4.78 |
| combined | 0 to 2% | 0 | 10 | 19 | 10 | −2.10 | +0.23 | +3.60 |
| combined | 5% | 1 | 13 | 21 | 13 | −1.87 | +1.17 | +2.61 |
| combined | 10% | 3 | 17 | 24 | 13 | −2.57 | +0.47 | −0.10 |

The session's 0% thresholds already cost right joins in the logs, because the session's most extreme break isn't as extreme as the logs'.

### The down rule at fixed thresholds

| t, keys/ms | session: breaks lost | false joins cut | logs: right lost | wrong cut (1.2.0 / 2.x) | net | ×2 | weighted |
|---|---|---|---|---|---|---|---|
| 0 | 1 | 12 | 5 | 4 (3 / 1) | −0.23 | +0.70 | +2.25 |
| 0.0003 | 0 | 10 | 4 | 3 (2 / 1) | −0.23 | +0.47 | +3.60 |
| 0.0005 | 0 | 9 | 3 | 3 (2 / 1) | 0.00 | +0.70 | +3.24 |
| 0.0007 | 0 | 8 | 0 | 3 (2 / 1) | +0.70 | +1.40 | +2.88 |
| **0.001** | **0** | **7** | **0** | **2 (2 / 0)** | **+0.47** | **+0.93** | **+2.52** |
| 0.0015 | 0 | 4 | 0 | 2 (2 / 0) | +0.47 | +0.93 | +1.44 |

### Recommendation

**Keep the bar at no real break lost, and take the down veto at 0.001 keys per ms.**

- It's the only point that loses nothing on both sets with room to spare. The logs' highest right join moves at 0.0006 and the session's highest break at 0.0003.
- 0.0007 cuts one more wrong join in the logs, but sits just 0.0001 above a right join.
- **Expected gain:** 0.5 to 2.5 fewer fixes per 1,000 swipes, about one every 400 to 2,000 swipes. The logs give the low end, the session at everyday rates the high end.

**How sure:**

- **The "loses nothing" part is fairly firm.** 0 of 385 real breaks were lost across both sets. With 95% confidence, the true loss is under 0.8% of breaks, at most about 0.6 per 1,000 swipes.
- **The gain is not firm.** It rests on 2 wrong joins in the logs, both from 1.2.0, and 7 of 24 false joins in one session of drills.
- So the most likely result is a small gain, and the worst plausible one about even.
- The owner's logs since 11-11 would settle it without more recording: they'd show 2.1's own wrong joins.

### Adding the recogniser's fit and the context model

The owner asked whether the context model could tell one word from two. On its own it added little to the geometry (the [pilot](83-joins-context.md)). Here it's combined with the micro signals and with how well the paths fit words.

[`83-joins/combine.py`](83-joins/combine.py) adds two groups of signals to the micro ones:

- **Fit:** from the phone replay, the app's recogniser reads the joined path, the first touch alone and the second alone. A wrongly joined path like "elephant" + "ontario" should fit no word well, while each half fits one. The signals:
  - the joined reading's CTC fit against the two halves' fits summed;
  - the same for the total score;
  - how far each reading leads the next word.
- **Context:** the DistilGPT-2 margin, joined word against the two words, text before only, all three readings from the phone.

Each model is a weighted sum with a veto below a threshold. Everything is nested leave-one-out: for each pair, the weights and each budget's threshold are fitted on the other pairs, then the pair is judged. So these numbers are fairer, and a little worse, than the in-sample thresholds above.

**The logs:** 346 right joins and 37 wrong joins, every one with all signals. Each cell gives right joins lost / wrong joins cut, then the net per 1,000 swipes.

| model | 0% | 1% | 2% | 5% | 10% |
|---|---|---|---|---|---|
| down alone | 1 / 3, +0.47 | 4 / 3, −0.23 | 5 / 4, −0.23 | 5 / 4, −0.23 | 5 / 4, −0.23 |
| micro | 2 / 0, −0.47 | 4 / 9, **+1.17** | 12 / 11, −0.23 | 20 / 11, −2.10 | 37 / 16, −4.91 |
| micro + fit | 2 / 2, 0.00 | 6 / 9, +0.70 | 9 / 11, +0.47 | 24 / 15, −2.10 | 44 / 16, −6.54 |
| micro + fit + context | 3 / 6, +0.70 | 4 / 7, +0.70 | 10 / 12, +0.47 | 24 / 15, −2.10 | 44 / 17, −6.31 |
| fit + context | 3 / 4, +0.23 | 4 / 6, +0.47 | 8 / 7, −0.23 | 20 / 12, −1.87 | 38 / 14, −5.61 |

For comparison, the down veto at its fixed 0.001 loses 0 and cuts 2: +0.47.

- **The combination doesn't clearly beat the micro veto.**
  - The best point is micro at 1%: +1.17 per 1,000 swipes, against +0.47 for the down veto. That's 3 more pairs set right in 4,280 swipes.
  - Adding fit and context makes the 0% point better (+0.70 against −0.47), but not the 1% point.
  - Every difference is 1 to 5 pairs out of 383, within what a different handful of wrong joins would change.
- **The context and fit alone (no geometry)** reach +0.47 at best.
- **At 5% and 10%, every model is behind,** as before.

**The session:** the readings need the app's recogniser on the phone. [`83-joins/capture-replay.sh`](83-joins/capture-replay.sh) reads each of the session's labelled gaps three ways: 609 readings, CPU only, about 5 to 10 minutes. It then scores the same models two ways:

- with weights and thresholds from the logs;
- nested leave-one-out on the session alone.

The session has no text before, so its context margin opens a sentence. It ran on 2026-11-16, CPU only, under the phone lock.

**The session, with weights and thresholds from the logs:** the fair test, since nothing was fitted on the session. 39 breaks the rule joins, 24 false joins. Each cell gives breaks lost / false joins cut, then the net per 1,000 swipes at the logs' everyday rates.

| model | 0% | 1% | 2% | 5% | 10% |
|---|---|---|---|---|---|
| down alone | 0 / 8, +2.88 | 0 / 10, **+3.60** | 1 / 12, +2.25 | 1 / 12, +2.25 | 1 / 12, +2.25 |
| micro | 0 / 2, +0.72 | 1 / 8, +0.81 | 2 / 15, +1.26 | 6 / 18, −5.95 | 11 / 21, −15.24 |
| micro + fit | 0 / 2, +0.72 | 1 / 10, +1.53 | 1 / 13, +2.61 | 5 / 18, −3.88 | 8 / 22, −8.66 |
| micro + fit + context | 0 / 6, +2.16 | 0 / 8, +2.88 | 2 / 16, +1.62 | 5 / 20, −3.16 | 9 / 21, −11.09 |
| fit + context | 0 / 3, +1.08 | 0 / 3, +1.08 | 0 / 3, +1.08 | 1 / 8, +0.81 | 3 / 12, −1.90 |

**The session on its own** (nested leave-one-out on its 63 joins):

- The richer models overfit so few gaps: micro + fit + context loses 6 of 39 breaks at every budget (−3.79).
- Micro alone loses 2 and cuts 20 (+3.06).
- Down alone loses 1 and cuts 10 (+1.53).
- Fit + context alone loses 1 and cuts 8 (+0.81).

**So the context model and the fit don't beat the down veto.**

- **On the logs,** the best combination is +0.7 per 1,000 swipes better than the down veto at the same budget, which is 3 pairs.
- **Carried to the session,** the down veto from the logs is as good as or better than every combination at every budget.
- **On their own, without geometry,** the readings and the context cut few false joins: 3 to 8 of 24 here, 4 to 7 of 37 in the logs.
- **The down veto is the only rule that comes out ahead in all three views.** At its fixed 0.001 it loses 0 of 39 breaks and cuts 7 of 24 false joins here, and loses 0 of 346 right joins and cuts 2 of 37 wrong joins in the logs.

The recommendation stands: the down veto at 0.001, with no real break lost.

## Limits

- **One session of drills.** Its two-word gaps may be more regular than everyday typing, which may be why the gap separates far better here (0.93) than in the logs (0.65).
- **Bounces:** 4, too few to describe.
- **The logs' samples** come through Android's motion events, not straight from the sensor, and most lack sizes. Timing signals may not carry over exactly.
- **Thresholds at the bar** sit on the single most extreme break. Where the table says no room, one more break would move them.

## Reproduce it

In WSL `atlas-build`, as `dev`, with numpy (the apostrophe study's venv has it), then the plots with matplotlib:

```bash
~/build/apostrophe-venv/bin/python3 -I docs/studies/83-joins/micro.py ~/tk-data/joins-capture-20261116-1633/touchpad.txt "$PWD" ~/build/joins83/capture ~/build/joins83/private
python docs/studies/83-joins/plot_micro.py <path to micro-windows.json> docs/studies/83-joins
```

`micro.py` prints numbers only, and writes the sample windows, numbers only, to the output folder. The trade-off tables come from `tradeoff.py`, run like `micro.py`.
