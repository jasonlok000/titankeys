# Haptics: one feel for each kind of thing (proposal, Nov 10)

For the owner. Nothing is built yet.

## What the phone can do

- The Titan 2 Elite's vibrator has **no strength control**: every buzz is full strength, and only its length and the gaps between buzzes can change. Today's "Normal" and "Strong" therefore differ only in length.
- It has five ready-made effects, tuned by the maker: **texture tick** (the lightest), **tick**, **click**, **double click** and **heavy click**.
- So the palette is those five, plus patterns of buzzes and gaps. Phones with strength control get the same patterns, with strength added where it helps.

## The language: one meaning per feel

| Feel | Meaning | Where |
|---|---|---|
| **Texture tick** (lightest) | Movement over keys | Swipe ticks; the cursor passing a letter |
| **Tick** | A step or boundary | The cursor passing a space; a selection growing by a word; a new Sym page or group; the clipboard opening; each word after the first in a held delete; the word menu opening |
| **Click** | Done, accepted | A word picked from the strip; a clip pasted; a word saved; a quick setting switched; **steering starts** |
| **Double click** | Something now stays: selected or locked | **Selecting starts** (double tap on a word, or tap tap and slide); Shift or Alt locked; a clip pinned |
| **Rising pair** (short, gap, longer) | Changed into something else | A held letter becomes its symbol; autocorrect changed a word |
| **Falling pair** (longer, gap, short) | Put back | Autocorrect undone; a forgotten word brought back with Undo |
| **Heavy click** | Something removed | A delete flick; the first word of a held delete; a word forgotten or blocked; the clipboard cleared |
| **Long buzz** (about 80 ms, used nowhere else) | Can't do that | Deleting at the start of the text; a saved word refused (the list is full, or the word isn't allowed) |
| *(nothing)* | — | Single Space taps; Shift or Alt once or off; Sym opening; a swiped word landing (its ticks already played); the Teams bounce; the idle bar |

### The Space gestures, by count

The count you feel matches the touches you made: steering (tap, then slide) is **one click**, and selecting (a double tap, or tap tap and slide) is a **double click**. The haptic comes when the gesture is recognised, not on each tap.

Ticking on each Space tap would mean ticking on resting fingers too. Most quick taps on the Space row are not gestures, and those stray vibrations are exactly what the 2.1 tick fix removed.

## What changes from today

| Trigger | Today | Proposed |
|---|---|---|
| Swipe ticks | short buzz | texture tick (same timing) |
| Steering starts | 20 ms buzz | click |
| Double tap selects a word | 20 ms buzz (same as steering) | double click |
| Tap tap and slide selects words | 20 ms buzz (same as steering) | double click |
| Cursor passes a letter / a space | short / longer buzz | texture tick / tick |
| Selection grows by a word | short buzz | tick |
| Delete flick | strong buzz | heavy click |
| Held delete, each further word | short buzz | tick |
| Hold a letter for its symbol | two equal buzzes | rising pair |
| Word picked on the strip | word buzz (long, short) | click |
| Autocorrect changed a word | word buzz | rising pair |
| Autocorrect undone | nothing | falling pair |
| Word menu opens (hold on the strip) | strong buzz | tick |
| Save word | nothing | click |
| Forget or block word | nothing | heavy click |
| Undo a forget | nothing | falling pair |
| Clip pasted | word buzz | click |
| Clip pinned / unpinned | word buzz | double click / tick |
| Clipboard cleared | word buzz | heavy click |
| Clipboard opens, Sym page or group | word buzz | tick |
| Quick setting switched | word buzz | click |
| Shift or Alt locked | nothing | double click |
| Delete at the start of the text, saved word refused | nothing | long buzz |
| Guide chapter and finale | their own patterns | unchanged (guide only) |

## Calm while typing fast

- **Never on a plain key press or a single tap.** The keys already click physically.
- **One feel at a time.** A feel never cuts into another one that is still playing. Texture ticks wait (as today), and two confirmations closer than 80 ms play only the first.
- **No double feedback at the end of a swipe.** An autocorrect change that lands as a swipe ends stays silent, as today.
- **Length budget:**
  - ticks: 20 ms or less
  - clicks: the effect's own length
  - pairs: 15 + 45 + 30 ms, under 100 ms
  - the long buzz: 80 ms, only for "can't".

## Settings

- **Vibration Off** silences every feel above, except the guide's.
- **Normal and Strong** set the swipe ticks only: texture tick for Normal, tick for Strong, as this phone can't change strength.
- **Android's Touch feedback switch** turns everything off, as today.
- **Option, not proposed by default:** split the setting into "Swipe ticks" and "Confirmations", for anyone who wants ticks off but keeps confirmations. Today's single switch covers both.

## Other phones

- Every ready-made effect exists on Android 10 and later, so the same code runs everywhere, and phones without the effect get Android's nearest built-in one.
- Where a phone can compose effects, the pairs use its rise and fall primitives instead of timed buzzes.
- Where it has strength control, Normal and Strong also scale strength.
