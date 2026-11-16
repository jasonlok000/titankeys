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

`micro.py` prints numbers only, and writes the sample windows, numbers only, to the output folder.
