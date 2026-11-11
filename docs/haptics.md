# How TitanKeys feels

Every gesture has its own feel, so you can tell what happened without looking. One meaning per feel:

| Feel | Means | When |
|---|---|---|
| **Lightest tick** | Moving over keys | Each key a swipe crosses. The cursor passing a letter. |
| **Tick** | A step | The cursor passing a space. A selection growing by a word. A new Sym page or group. The clipboard opening. Each word after the first in a held delete. The word menu opening. A tap that types a key. |
| **Click** | Done | A word picked on the strip. A clip pasted. A word saved. A quick setting switched. **Steering starts** (tap, then slide). |
| **Double click** | Now it stays | **Selecting starts**: a double tap on a word, or tap tap and slide. Shift or Alt locked. A clip pinned. |
| **Short, then longer** | Changed into something | A held letter became its symbol. Autocorrect changed a word. |
| **Longer, then short** | Put back | Autocorrect undone. A forgotten word brought back with Undo. |
| **Heavy click** | Removed | A delete flick, or the first word of a held delete. A word forgotten. The clipboard cleared. |
| **Long buzz** | Can't | Deleting with nothing left before the cursor. A word that can't be saved. |
| Nothing | — | Single taps on Space (most are a resting finger). Shift or Alt once or off. Opening Sym. A swiped word landing (its ticks already played). |

**Space gestures, by count:**
- Steering (tap, then hold and slide) is one click. It plays as soon as the held touch counts (70 ms), before the slide, and the strip shows the gesture then too.
- Selecting (a double tap, a triple tap, or tap tap then hold and slide) is a double click, also as soon as the held touch counts.
- A double or triple tap with no slide picks the word once no further touch follows (150 ms), so a resting finger's run of short touches never picks one.
- While steering, a space or a line end is a tick and every other character the lightest tick. Punctuation gets no third feel: the other effects already mean done, stays and removed, and punctuation almost always sits next to a space, whose tick already marks the word's end.
- A resting finger's touches last about 40 ms, so they never get to the 70 ms that a held touch needs.
- **Debug build only:** Settings > Vibration > Word pick feel tries three ways to feel a picked word's length: one buzz that lasts longer for a longer word, a short buzz per syllable, or a heavier effect for a longer word.

**Calm while typing fast:**
- Plain key presses never vibrate.
- A feel never cuts into one that is still playing.
- Two confirmations closer than 80 ms play only the first.
- An autocorrect change that lands as a swipe ends stays silent.

**Settings:**
- **Vibration Off** silences all of it, except the guide's celebrations.
- **Normal and Strong** only change the swipe ticks: the lightest tick for Normal, a tick for Strong. The Titan 2 Elite can't make a vibration stronger, only longer or different.
- Android's **Touch feedback** switch turns everything off too.

**Other phones** get the same feels. A phone without one of the ready-made effects gets timed buzzes, and a phone that can compose effects gets composed pairs.
