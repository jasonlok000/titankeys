# How TitanKeys feels

**Haptics are only for what your thumb can't feel or see.** A physical key press already clicks under your thumb, so it never buzzes. A buzz is for the touchpad, which has no click of its own, and for what changes where you aren't looking.

## Six feels, one meaning each

The Titan 2 Elite has five ready-made effects and no strength control, so each meaning is its own effect. They go from lightest to heaviest:

| Feel | Effect | Means |
|---|---|---|
| **Texture tick** | `TEXTURE_TICK` (21) | Moving |
| **Tick** | `TICK` (2) | A step |
| **Click** | `CLICK` (0) | Done |
| **Double click** | `DOUBLE_CLICK` (1) | Locked, or selecting armed |
| **Heavy click** | `HEAVY_CLICK` (5) | Gone |
| **No** | Three 12 ms pulses, 50 ms apart | Nothing happened |

A meaning is the same everywhere. "No" is the only timed pattern, so it can't be mistaken for anything else. A phone without one of the effects gets timed buzzes in its place.

## What plays what

**Swipe**
- The swipe is recognised: texture tick. This is the first tick, once the finger has gone as far as a swipe. A finger that sets off slowly, as from resting on a key, has to go two keys first, which stops most phantom buzzes.
- Each key the swipe crosses: texture tick. The ticks come every 0.7 keys, at most one each 40 ms.
- A light touch types a key: tick. The touchpad has no click, so this stands in for the key's.

**Delete**
- A delete flick: heavy click.
- Each further word while you hold: tick.
- Nothing left to delete: no. This goes for the flick and the hold.

**Space gestures**
- Steering armed: click.
- Selecting armed (tap, tap, then hold): double click.
- A double or triple tap picks a word: double click, as the word is now selected.
- The cursor passes a letter: texture tick. It passes a space or a line end: tick. So you can count words without looking. Punctuation has no feel of its own, as it almost always sits next to a space.
- The selection grows or shrinks by a word: tick.
- The cursor hits the start or end of the text: heavy click, once each time it gets there. It plays again once the cursor has moved away and comes back.

**Changes**
- Autocorrect on Space: tick. Shorthand spelled out goes through autocorrect, so it ticks too.
- A held letter becomes its symbol: click.
- A :name: becomes its emoji, or a snippet its text: tick.

**Bar**
- Tap a word: tick.
- The word menu opens: click.
- The marked menu button changes: texture tick.
- Save, or Undo after Forget: click.
- Forget: heavy click.
- Save goes wrong: no.
- Paste the offered clip: click.

**Shift and Alt**
- Locked: double click.
- Unlocked, from locked: tick.
- Once, or off: nothing. That's a key press you already felt.

**Sym, emoji, quick settings and clipboard**
- The mark moves to another key: texture tick.
- A new page: tick. A new group: tick.
- A quick setting switched: click.
- Paste a clip: click.
- Pin a clip: double click. Unpin it: tick.
- Clear the clipboard: heavy click.

**Other**
- Record a bug starts or stops: click.
- The guide's celebrations: three ticks rising into a click. The Titan can't make a tick stronger, so there the rise is the click at the end. A phone that can compose effects gets ticks that really grow.

## What plays nothing, and why

- **Physical key presses.** Your thumb feels them.
- **Shift or Alt once.** A key press, and the bar shows it.
- **The double-space full stop and the automatic capital.** You see them land as you type.
- **A swiped word landing.** Its ticks already played.
- **Slipping and carrying on.** The swipe or the cursor just goes on.
- **Lifting to end a steer.** You lifted, so you know.
- **Backspace undoing autocorrect.** A key press.
- **Typing in the Sym panel, and opening any panel.** Key presses, and you see the panel.
- **The touchpad's scroll, cursor and Back.** The screen moves.
- **The first press of Clear, and Pin mode.** Key presses, and the page shows them.
- **A key that picks a button in the word menu.** A key press. What the button does plays its own feel.
- **A new step in the guide, and setup changes there.** You're looking at the guide.
- **Speed effects and idle games.** They're for your eyes.

## Three rules that keep it calm

1. **No overlap.** Within 80 ms of the last feel that played, only the first plays. Moving is the exception: the owner kept a tick for each key a swipe or the cursor crosses, so moving ticks keep their own pace, up to one each 40 ms. A moving tick still never cuts or blocks a heavier feel, and it waits out a heavier feel's 80 ms.
2. **Priority.** Gone and no beat a step, which beats moving. Clicks and double clicks sit between a step and gone. A higher feel inside the 80 ms still plays, but only once the earlier one has finished.
3. **Nothing cuts a playing feel.** A feel is busy until it ends, and nothing starts before then.

The rules live in `HapticGate`, which has no Android parts, so tests/haptics can run it.

## Settings

**Settings > Haptics** has **All haptics** at the top, and a switch for each haptic below it, in the same groups. All of them are on to start with.

- **All haptics off** silences everything, the guide's celebrations too. Each switch keeps its own setting for when it's back on.
- **Vibration** on the quick settings page (Alt on the Sym panel, then V) is the same as All haptics.
- Android's **Touch feedback** switch turns everything off too.

There is no strength setting. The Titan 2 Elite can't make a vibration stronger, only different.
