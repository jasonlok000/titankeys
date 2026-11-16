# #83: the owner's joins session, straight from the touchpad

On 2026-11-16 the owner swiped a dedicated session for #83 (the script in [`83-joins-recording.md`](83-joins-recording.md), with a second set of pairs, long words and look-alike pairs). It was captured from the touchpad device with `getevent`, so there are touches but no text.

The owner's focus: "focus on whether it detects a join or not falsefully. focus on finding the differences between an actual join vs a deliberate two words."

So this labels every gap as a real break inside one word or a deliberate gap between two words, replays TitanKeys' own joining rule on it, and compares the two kinds. No app code changed.

## Answer

**On this session, today's rule (`Swipe.sameStroke` on main):**

- **joins 24 of 154 deliberate two-word gaps.** 23 of them are among the 124 where both ends are on the top row.
- **leaves 10 of 49 real breaks apart,** 7 of them among the 45 at the top row.

**What tells a break from two words, best first:**

- **The gap:** a median of 85 ms for a break against 171 ms between words.
- **Where the first swipe ends:** a break ends right at the sensor's top edge.
- **The distance to the next touch:** 1.4 keys for a break, 3.6 between words.
- **Which way the first swipe moves as it lifts:** a break is still heading up, off the sensor; a word's end has stopped or turned back down.
- **The contact size at the end:** a finger sliding off thins out.

**One rule holds on both this session and the logs:**

- Keep apart when the first swipe ends heading down faster than 0.001 keys per ms.
- Here it cuts 7 of the 24 false joins and loses none of the 39 real breaks the rule joins.
- On the logs' top-row joins it cuts 2 of 37 wrong joins and loses none of 346 right joins.
- The highest real break here moves at 0.0003, and the highest right join in the logs at 0.0006, so it has room.
- It's a call for the owner (see Calls to confirm). Nothing was built.

**What no geometry can fix:** most false joins are pairs where the second word starts on or next to the key the first ended on, within 260 ms:

- try it: 4 of 4
- every one: 4 of 4
- out there: 3 of 4
- what if: 3 of 5

Only the readings can tell those. Composer's 150 ms check of both readings does that, but it needs the app's recogniser, so it isn't replayed here.

## The session

- **Capture:** `~/tk-data/joins-capture-20261116-1633/touchpad.txt`, 434 touches over 700 s. One touch had no lift, so 433 are complete.
- **How far it got:**
  - all 15 top-row pairs;
  - all 15 extra pairs;
  - all 10 long words;
  - 4 of the 10 look-alike pairs (in to, some one, may be, every one). The capture ends there.
- **Repeats:** the owner swiped each item 3 to 6 times.
- **Not used:** the first 7 touches, before a 169 s pause, and a few slips: 18 touches in all.

## Method

[`83-joins/capture.py`](83-joins/capture.py):

1. **Touches in the keyboard's letter space.**
   - Each touch runs from `BTN_TOUCH` down to up.
   - Raw X / 1079 and raw Y / 599 is what `Touchpad.samples` gets once the firmware's Sliding Mode offset and scale are off.
   - It then goes through the keyboard's built-in calibration (`android/assets/calibration/titan-2-saved.profile`, through the app's own `TouchAlignment.java` via [`83-joins/CaptureAlign.java`](83-joins/CaptureAlign.java)), then y × 4/3.
   - The sensor's top edge then lands at 0.154, as in the raw logs.
   - Contact size is `ABS_MT_TOUCH_MAJOR`. It runs 1 to 4, the same steps as the logs.
2. **Items.**
   - Touches split into runs at pauses over 550 ms. Only 7 of the 432 gaps fall between 300 and 600 ms, so the split is clear.
   - Runs are matched in order to the script by shape: the mean distance in keys between the path and each word's key path.
   - A run may be a slip, an item may take several runs, and one run may hold several repeats.
   - 201 of 210 runs matched, at a median of 0.4 to 1.0 keys per set. 10 runs matched worse than 1.6 keys and are left out.
3. **Gap labels, by shape alone, never by timing.**
   - Inside a run, each word takes one or more touches in a row, whichever fits best.
   - Two touches of one word make a break; the last touch of a word and the first of the next make a two-word gap.
   - Every false and missed join was checked by eye against the touches' key paths.
4. **TitanKeys' rule.**
   - `Swipe.sameStroke` from main, compiled with [`83-joins/JoinReplay.java`](83-joins/JoinReplay.java), with the first swipe's mean size over its last 60 ms.
   - It runs in order, so a touch after a join is judged against the joined swipe, as in the keyboard.

## Numbers

### TitanKeys' rule

| gaps | all | joined | in the window (`couldContinue`) | both ends on the top row |
|---|---|---|---|---|
| real breaks | 49 | 39 | 44 | 45 |
| two-word gaps | 154 | **24** | 49 | 124 |
| between repeats | 198 | 0 | 1 | 136 |

- **False joins by pair:**
  - try it 4 of 4, every one 4 of 4;
  - out there 3 of 4, what if 3 of 5;
  - you were 2 of 6, are you 2 of 5, are they 2 of 6;
  - type quite, your turn, what time and power tower, 1 each.
- **False joins' gaps:** 57 to 256 ms. Their distances: 5 under 1 key, 19 from 1.7 to 3.4 keys, just inside the rule's 3.5.
- **Missed joins (10):**
  - 3 broke mid-pad, not at the top row;
  - 2 landed below the top edge;
  - 5 were in the window but not judged one stroke.
  - 3 are tiny return touches, under 1 key.
  - All 10 come within 150 ms, where Composer's check of both readings could still join them.
- **Breaks by set:** top-row pairs 11 of 14 joined, extra pairs 12 of 18, long words 14 of 15, look-alike pairs 2 of 2.
- **Two-word gaps by set:** top-row pairs 14 of 64 joined, extra pairs 6 of 72, look-alike pairs 4 of 18.

### What separates a break from two words

The 45 breaks and 124 two-word gaps with both ends on the top row. Separation is the chance a break and a two-word gap fall in the expected order; 0.5 is no help.

| measure | break: median (10th to 90th) | two words: median (10th to 90th) | separation |
|---|---|---|---|
| gap, ms | 85 (37 to 142) | 171 (114 to 256) | 0.92 |
| where the first swipe ends, y (edge 0.154) | 0.192 (0.154 to 0.228) | 0.240 (0.198 to 0.302) | 0.86 |
| first swipe's vertical speed at its end, keys/ms (− is up) | −0.002 (−0.005 to 0.000) | 0.000 (−0.003 to 0.003) | 0.84 |
| distance to the next touch, keys | 1.4 (0.4 to 3.0) | 3.6 (1.0 to 6.2) | 0.81 |
| contact size, last 60 ms, steps | 1.43 (1.00 to 1.86) | 1.86 (1.14 to 2.00) | 0.77 |
| first swipe's duration, ms | 277 (104 to 683) | 381 (191 to 495) | 0.69 |
| landing speed, keys/ms | 0.012 (0.001 to 0.018) | 0.003 (0.000 to 0.016) | 0.68 |
| carries on (cosine) | 0.995 | −0.949 | 0.68 |
| turns back (cosine) | −0.949 | −0.049 | 0.66 |
| where the second touch lands, y | 0.176 | 0.198 | 0.63 |
| contact size at landing, first 60 ms | 1.29 | 1.60 | 0.60 |
| path lengths, first and second | 5.8, 6.0 keys | 6.2, 5.6 keys | 0.53, 0.50 |

Quick first sample (within 20 ms): 36 of 45 breaks, 63 of 124 two-word gaps.

### A veto on the rule's joins

Keep apart when the first swipe ends heading down faster than t. The logs are the #83 study's top-row pairs that `sameStroke` joins.

| t, keys/ms | here: real breaks lost (39) | false joins cut (24) | logs: right joins lost (346) | wrong joins cut (37) |
|---|---|---|---|---|
| 0 | 1 | 12 | 5 | 4 |
| 0.0005 | 0 | 9 | 3 | 3 |
| 0.0007 | 0 | 8 | 0 | 3 |
| **0.001** | **0** | **7** | **0** | **2** |
| 0.002 | 0 | 4 | 0 | 2 |

The gap alone, the strongest measure here, doesn't carry over: cutting joins over 180 ms would cut 7 false joins here, but lose 20 right joins in the logs, which reach 380 ms.

## Limits

- **One session, drills at a steady pace.** The pauses between words here (114 to 256 ms) may be shorter or more even than in everyday typing.
- **Calibration:** the built-in one. If the owner calibrated their own keys, positions shift a little; the session holds no settings.
- **Composer's 150 ms check** needs the app's recogniser on the phone, so it isn't replayed. Within 150 ms, it also sees all 10 breaks the rule leaves apart and 41 two-word gaps the rule doesn't join.
- **Labels come from shape.** A short return touch can fit either side; every false and missed join was checked by eye, but not every gap.

## Calls to confirm

- **The veto at 0.001 keys per ms:** a one-line addition to `Swipe.sameStroke` that would cut nearly a third of this session's false joins (7 of 24) and 2 of the logs' 37, losing nothing in either. Worth building, or wait for the logs since 11-11?

## Reproduce it

In WSL `atlas-build`, as `dev` (needs `javac` and `java`; the last argument is optional and adds the logs' cross-check):

```bash
python3 -I docs/studies/83-joins/capture.py ~/tk-data/joins-capture-20261116-1633/touchpad.txt "$PWD" ~/build/joins83/capture ~/build/joins83/private
```

It prints counts and measures only. It writes the per-gap numbers and an alignment listing, which names the script's words, to the output folder.
