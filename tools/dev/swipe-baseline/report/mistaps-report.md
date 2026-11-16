# #30 redone with the owner's words, 2026-11-10

- **Mistap:** the owner did not mean to type, nothing was typed, but the touchpad vibrated.
- **Mistype:** the owner did not mean to type, but a word was typed anyway (a swipe deleted within 5 s, nothing kept in its place).
- **Wrong word:** the owner meant to swipe and got the wrong word. This is engine accuracy (#31), not this report.

Data: raw-log-12 (Nov 9 07:02 to Nov 10 12:00, 8,664 contacts) and the resting-touch recording (344 touches). To find which contacts vibrated, both were replayed through the keyboard's own `Contact` class with the swipe-tick rule from `TitanKeysIme.motion` and `Haptics.move`. The replay reproduces the log's outcomes closely: 4,329 swipes and flicks against 4,303 logged, and 1,004 taps against 1,100. On the recording, its reading agrees with what TitanKeys showed on 333 of 344 touches. Tools: `tools/HapticReplay.kt`, `tools/haptic_events.py`, `tools/mistaps.py`. Numbers: `report/mistaps-v12.json`. Counts and features only.

## 1. Where the touchpad vibrates without input

| Path | Fires when | Input? |
|---|---|---|
| Swipe tick (`haptics.move`) | the contact counts, and the finger is **0.2 keys** from where it landed, on a letter | Not necessarily: a contact becomes a swipe only at **0.6 keys** |
| Tap click (`haptics.tap`) | a tap on a tap key | Only with tap keys on. They were off, so it never played |
| Cursor click (`haptics.click`) | double tap, or a Space drag starts the cursor | It moves the cursor or selects, which is input |
| Cursor and selection steps, held delete | each step | Input |
| Delete flick (`haptics.tap`) | a flick deletes | Input |

So with tap keys off, the swipe tick is the only touchpad vibration that can come without input. It fires at 0.2 keys, a third of the way to being a swipe.

## 2. How many mistaps

**241 mistaps, 7.7 per 100 swipes** (each contact ticked and typed nothing). Of these:

- **132 contacts the keyboard then ignored.** In 125 of them a physical key went down during the contact: the finger slid on the pad while pushing a key, ticked, and the key press then voided the contact.
- **70 that settled to nothing.**
- **39 read as taps.** Tap keys are off, so these type nothing but still ticked on the way.

On top of these, 191 vibrating swipes had no recognition at all. Most of them are probably Sym-panel or Alt swipes, where the real keyboard does not tick. If none of them were, mistaps would be at most 432, or 13.8 per 100.

- 235 of the 241 ticked exactly once.
- **214 of 241 ticked before the contact had gone 0.6 keys**, so before it was a swipe at all.

## 3. What separates mistaps from vibrations that typed

AUC, mistaps against meant input that vibrated (3,719: kept swipes, wrong words, flicks):

| Feature | Mistaps (median) | Meant input (median) | AUC |
|---|---|---|---|
| **Travel** | 1.4 keys | 4.5 keys | **0.15** |
| Time to the first tick | 94 ms | 66 ms | 0.83 |
| Pause since the last swipe | 3.7 s | 1.0 s | 0.80 |
| Duration | | | 0.68 |
| Largest size step | | | 0.44 |
| Landing size over usual | | | 0.38 |

Travel separates them best, and size does not separate them: mistaps are no larger than real touches. Only 23 mistaps have sizes, because sizes start with 2.0.0.

## 4. Does the size rule help?

The size rule is: step 6+, or landing 2+ over the usual size for that spot.

- **Silencing ticks only** (judged on the sizes up to the first tick):
  - real use: silences **0 of 23** mistaps and 0 of 1,090 meant inputs
  - resting recording: silences **12 of the 53 touches that vibrated**
- **Dropping input:** catches 0 of 40 mistypes, and loses 0 of 1,090 meant inputs.

It is harmless but does nearly nothing in real use. Its only effect is on exaggerated resting thumbs.

## 5. A rule that silences mistaps without touching input

**Let the first tick wait until the finger is 0.6 keys from where it landed, the distance at which a contact becomes a swipe.** Input does not change at all, only when the first tick plays.

| | First tick from 0.6 keys | First tick from 1.0 keys |
|---|---|---|
| Mistaps silenced | **149 of 241 (62%)** | 179 of 241 (74%) |
| Read as taps | all 39 | all 39 |
| Settled to nothing | 54 of 70 | 59 of 70 |
| Ignored (key pushed) | 56 of 132 | 81 of 132 |
| Meant input that loses its ticks | **33 of 3,719 (0.9%)** | 115 (3.1%) |
| Meant input: first tick later by | **19 ms** (median) | 38 ms |
| Resting recording, vibrations silenced | 26 of 53 (all 5 taps, 20 of 31 read as nothing, 1 swipe) | 36 of 53 |

The 0.6-key version takes out most mistaps, delays a real swipe's first tick by about 19 ms, and costs about 1% of real swipes their ticks. The 33 that lose them are short swipes, probably a second contact carrying a swipe on, which my replay measures from the second landing. The rest of the mistaps are mostly the finger sliding over a key it then pushes. Those need a longer distance, or a short delay that a key press cancels.

## 6. Mistypes (unintended words)

The numbers stand: 156 in the log, about 5 per 100 swipes.

- Size rule: **0 of 40** with sizes caught.
- The pause signal does exist (median 1.3 s since the last swipe, against 0.7 s for kept swipes, AUC 0.57), but it costs too much as a rule:

| Rule | Mistypes caught | Kept swipes lost |
|---|---|---|
| pause 1 s or more | 89 of 156 | 674 of 1,717 |
| pause 1 s, travel under 3 keys | 29 | 97 |
| pause 1 s, travel under 2 keys | 21 | 40 |
| pause 2 s, travel under 2 keys | 12 | 35 |

Every version drops more real words than unintended ones. So the earlier advice stands: make undo cheap rather than detect mistypes.

## Recommendation

1. **Ship "first tick from 0.6 keys"** (Contact.SWIPE_KEYS). It is about one line in `Haptics.move`, changes no input, and removes about 6 in 10 mistaps, including every tap mistap.
2. **Keep the size rule parked.** It silences nothing in real use, and only an exaggerated resting thumb triggers it.
3. **Mistypes:** no rule catches them cheaply. Keep the one-flick undo idea.

## Decisions (Master, Nov 10)

1. First tick at 0.6 keys, the same constant as `Contact.SWIPE_KEYS`, ships in 2.1: commit d6794b1 on engine-2.1.
2. The size rule is parked again. The resting recording stays as evidence.
3. Mistypes: nothing for now, since one word backspace is already the cheap undo.

Release notes draft: "No more stray vibrations when a finger brushes the keys"
