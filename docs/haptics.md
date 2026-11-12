# How TitanKeys feels

## The rulebook

1. **Two kinds.**
   - **Texture:** fast, repeating things. One pulse of about 17 ms, never a pattern.
   - **Message:** something happened. Always a pattern of two pulses or more, or one long pulse, with each pulse about 25 ms or more.
2. **Meaning comes from rhythm, the same everywhere.**
   - Rising (short then long) means confirm, or done.
   - Falling (long then short) means undo, or back.
   - Two equal firm pulses mean lock: it stays.
   - Two quick light pulses mean changed.
   - One long pulse means gone.
   - Three quick hard pulses mean no.
   - A rising run of three means celebrate.
3. **Strength is length only.** More important or more destructive means longer.
4. **Gaps.** At least 35 ms between pulses, so each one reads on its own.
5. **No prebaked effects at all.** Everything is our own waveform, `VibrationEffect.createWaveform` with timings. The Titan has no strength control, so every pulse is at the default strength. A single pulse may be a one-shot.
6. **Repeats.** A message can't repeat within 300 ms. Texture can.
7. **Boundaries.** A space, or a selection grabbing a word, gets one stronger pulse of about 30 ms instead of a texture tick.

The owner's lab notes led here: swipe and cursor ticks at 17 ms felt very good, every prebaked effect felt generic, messages were too subtle, and "can't save" didn't feel like an error.

**Haptics are only for what your thumb can't feel or see.** A physical key press already clicks under your thumb, so it never buzzes. A buzz is for the touchpad, which has no click of its own, and for what changes where you aren't looking.

## The families

Patterns are on and off in ms, starting with on.

| Family | Pattern | Plays for |
|---|---|---|
| **Texture** | 17 | Each of a flick's dots landing in the caret, swipe start, keys crossed, a light touch typing a key, each counted Space tap, steering picking up after a slip, cursor letters, words in a held delete (fading, below), the marked button in the word menu or a panel |
| **Boundary** | 30 | The cursor passes a space, the selection grows or shrinks by a word, a new page or group on a panel |
| **Confirm** | 15, 40, 35 | Tap a word on the bar, the word menu opens, Save, a quick setting switched, steering starts, Record a bug starts or stops, paste from the clipboard page, paste the offered clip, a key or a chip tap on exact typing's bar |
| **Change** | 20, 35, 20 | Autocorrect, shorthand, a :name: emoji, a snippet, a held letter becoming its symbol |
| **Lock** | 30, 50, 30 | Selecting starts, a double or triple tap picks a word, Shift or Alt locked, a clip pinned, a lock on exact typing's bar |
| **Release** | 30 | Shift or Alt unlocked, a clip unpinned, a locked chip on exact typing's bar unlocked |
| **Gone flick** | 50, 35, 20 | A delete flick, timed to its dots |
| **Gone** | 60 | Forget, the clipboard cleared |
| **Back** | 35, 40, 15 | Undo after Forget, Backspace undoing a change |
| **No** | 25, 40, 25, 40, 25 | A word that can't be saved, nothing left to delete (flick and hold), the cursor hitting the start or end of the text, taps on Space that find nothing to pick or steer |
| **Celebrate** | 15, 40, 22, 40, 35 | The guide's celebrations |

The rising and falling patterns use a short first or last pulse on purpose: the short pulse is what makes the rise or the fall. So Confirm's 15 ms and Change's 20 ms are under rule 1's "about 25". The table wins over that guide.

Boundary is texture for the gate: it comes in the same fast runs as the letters, so it keeps their pace. Release is a single 30 ms pulse too, but it comes once, so it's a message.

The owner's table had the delete flick at 50, 30, 20. Its gap is 35 instead, so it keeps rule 4.

### The delete flick and its dots

A flick shows a dot for each letter that went. The dots pop in one after another over 120 ms, at Android's animation speed. The flick's first pulse plays at the flick, and its 20 ms tail plays as a second vibration once the last dot has popped in: at 85 ms for a word of one or two letters, and up to just under 120 ms for a long one. The gate takes both parts as one message. With animations off, or no dots on the bar, it plays as one waveform, 50, 35, 20.

Then the dots go into the caret one after another, and every dot ticks as it lands, as texture. Deleting should feel satisfying. To keep each tick its own on the Titan's motor, the dots land at least 30 ms apart (`DOT_LAND_GAP`). So the way in takes max(450, 202.5 + 30 × dots) ms, times Android's animation speed, and never less than 30 ms a dot after that. Up to eight dots keep the owner's 450 ms. Thirty land over about 900 ms. Each dot's own trip takes 202.5 ms, and the first lands about 520 ms after the flick. `GestureDisplay.outMillis` and `dotStepMillis` hold this, and the drawing and the ticks both come from them.

A tick within 35 ms of the flick's tail is left out. Any key or touch cuts the dots at once, and the ticks still to come with them. A tick also only plays while that flick's dots are still on the bar. With animations off there are none. A long delete's lift turns its lines into dots too, which go in the same way and tick the same way, with the same switch. Its last word's tick or its no may have just played, so a dot tick within 35 ms of that is left out.

### Held delete fade

A held delete ticks once for each further word. On a long hold that turned into one constant buzz, so the ticks fade as more words go.

The fade is a debug-only experiment for now. Only TitanKeys Debug fades, while its switch in the lab's Experiments is on, and it is to start with. With the switch off, and in every other build, each word plays the plain 17 ms texture tick, as before. Once the owner approves the fade, release gets it too.

The Titan has no strength control, so the fade is by length:

- **Start.** The first word's tick plays the start length, 17 ms.
- **Fade.** Each word after it is shorter, in even steps, until the floor, 9 ms. It gets there after 8 words, so word 9 plays 9 ms.
- **Thin.** From there only every other tick plays.

So a hold plays 17, 16, 15, 14, 13, 12, 11, 10, 9, then 9 on every other word. A new hold starts again at 17.

The floor is 9 ms, not lower: the phone's own vibration log shows that 8 ms buzzes barely register on this motor. A lower floor would fade to nothing.

The ticks are still texture for the gate, so they never block a message. A thinned tick plays nothing, and the gate never hears of it. `HeldDeleteFade` holds the rule, and it has no Android parts, so tests/haptics can run it.

## What plays what

**Swipe**
- The swipe is recognised: texture. This is the first tick, once the finger has gone as far as a swipe.
- Each key the swipe crosses: texture. The ticks come every 0.7 keys, at most one each 40 ms.
- A light touch types a key: texture. The touchpad has no click, so this stands in for the key's.

**Delete**
- A delete flick: gone flick.
- The flick's dots land in the caret: a light texture tick for every dot as it lands.
- A long delete's lines turn into dots as you lift, and those tick the same way as each lands.
- Each further word while you hold: texture. In TitanKeys Debug, with the fade on, it's shorter as more words go, then only every other one (see Held delete fade).
- Nothing left to delete: no. This goes for the flick and the hold.

**Space gestures**
- Each Space tap that counts toward a gesture: texture, so you feel tap 1 and tap 2 land. It's texture, so it never holds up the confirm or lock that follows.
- Steering starts: confirm.
- Selecting starts (tap, tap, then hold): lock.
- A double or triple tap picks a word: lock, as the word is now selected.
- Nothing to pick: a double or triple tap with no word to take (an empty field, between spaces, only marks), or a steer with no text at all. Nothing happens, no selection and no lock, only a no, once. Two taps above Space play it too, but only in the guide's practice field, which teaches it. Everywhere else they stay quiet.
- Your finger slips off while steering and lands again in time: texture, once, so you know you're still steering and not typing.
- The cursor passes a letter: texture. It passes a space or a line end: boundary. So you can count words without looking. Punctuation has no tick of its own, as it almost always sits next to a space.
- The selection grows or shrinks by a word: boundary.
- The cursor hits the start or end of the text: no, once each time it gets there. It plays again once the cursor has moved away and comes back.

**Changes**
- Autocorrect on Space: change. Shorthand spelled out goes through autocorrect, so it plays change too.
- Backspace right after a change puts back what you typed: back.
- A held letter becomes its symbol: change.
- A :name: becomes its emoji, or a snippet its text: change.

**Bar**
- Tap a word: confirm.
- The word menu opens: confirm.
- The marked menu button changes: texture.
- Save: confirm.
- Forget: gone.
- Undo after Forget: back.
- Save goes wrong: no.
- Paste the offered clip: confirm.
- Exact typing's bar: a key is confirm. A tap on a Shift, Alt or Ctrl chip is confirm, a long press that locks one is lock, and a tap that unlocks a locked one is release.

**Shift and Alt**
- Locked: lock.
- Unlocked, from locked: release.
- Once, or off: nothing. That's a key press you already felt.

**Sym, emoji, quick settings and clipboard**
- The mark moves to another key: texture.
- A new page: boundary. A new group: boundary.
- A quick setting switched: confirm.
- Paste a clip: confirm.
- Pin a clip: lock. Unpin it: release.
- Clear the clipboard: gone.

**Other**
- Record a bug starts or stops: confirm.
- The guide's celebrations: celebrate.

## What plays nothing, and why

- **Physical key presses.** Your thumb feels them.
- **Shift or Alt once.** A key press, and the bar shows it.
- **The double-space full stop, the space after a mark and the automatic capital.** You see them land as you type. Backspace taking that space out again is a key press too.
- **A swiped word landing.** Its ticks already played.
- **Slipping and carrying on in a swipe.** The swipe just goes on. Steering is different: it ticks once when your finger lands again, above.
- **Lifting to end a steer.** You lifted, so you know.
- **Typing in the Sym panel, and opening any panel.** Key presses, and you see the panel.
- **The touchpad's scroll, cursor and Back.** The screen moves.
- **The first press of Clear, and Pin mode.** Key presses, and the page shows them.
- **A key that picks a button in the word menu.** A key press. What the button does plays its own haptic.
- **A new step in the guide, and setup changes there.** You're looking at the guide.
- **Speed effects and idle games.** They're for your eyes.

## The gate

1. **Nothing cuts a playing message.** Until it ends, nothing else plays, and the next message waits 35 ms more, so the two read apart.
2. **A message can't repeat.** A message of the same family doesn't play within 300 ms of the last one that played. The rule is by family, not by haptic: two of the same rhythm that close run together into one longer pattern, whichever haptics they came from.
3. **Texture keeps its own pace.** Texture and boundaries aren't held to the repeat rule, and they never block a message.
4. **Messages beat texture.** A message plays even right on top of a tick, and texture waits while a message plays.

The rules live in `HapticGate`, which has no Android parts, so tests/haptics can run it.

## Settings

**Settings > Haptics** has **All haptics** at the top, and a switch for each haptic below it, in the same groups. All of them are on to start with.

- **All haptics off** silences everything, the guide's celebrations too. Each switch keeps its own setting for when it's back on.
- **Vibration** on the quick settings page (Alt on the Sym panel, then V) is the same as All haptics.
- Android's **Touch feedback** switch turns everything off too.

There is no strength setting. The Titan 2 Elite can't make a vibration stronger, only longer.

## The Haptics lab

Haptics go with what you do too closely to judge one alone, so TitanKeys Debug's Haptics lab shows the ones the keyboard really plays. Open it from Settings, then type, swipe and steer in its practice field.

- A timeline shows the last 5 s, newest on the right. Each haptic is its pulses to scale, with its name, its family and what set it off. One the gate or a switch dropped is hollow, with the reason: busy, too soon, switched off, or all off.
- A list under it has the last 15. Tap one to open its family's editor: a slider for each pulse (4 to 120 ms) and each gap (35 to 200 ms), a Play for that pattern alone, and Reset. The next real action plays the edited pattern.
- **Held delete fade** is in Experiments on the lab's page in Settings, next to "Dots speed up when long". Its switch turns the fade off and on, so you can feel a hold with and without it back to back. "Tune the held delete fade" under it opens the lab's screen on the fade's editor, and so does a tap on a held delete's tick in the list. The editor has a slider for the start (4 to 40 ms), the floor (4 to 40 ms, never above the start), the words to fade (1 to 20) and how often a tick plays after that (1 in 1 to 1 in 4). A strip shows a hold's first 16 ticks to scale, with thinned ones hollow. Play holds for 10 words, 350 ms apart, as a hold starts. The next real held delete plays the edits.
- On the timeline, each held delete tick shows at the length it played, with its word: "Held delete word 5 · Texture 13 ms". A thinned one is hollow, with the reason "thinned". With the fade off, the label ends in "no fade".
- Notes: one overall, one per family, one on the fade and one per haptic. They go in `comments.json` in the app's files, with each family's pattern in use, the fade's four numbers, and each haptic's family and pattern.

A tried pattern or fade is kept in the `swipe_haptics` preferences, and only a package that ends in ".debug" reads it. Every other build plays the patterns above, with no fade.

The keyboard and Settings run in one process, so the feed is a plain object, `HapticFeed`: the keyboard's `Haptics` records each play there with its result, and the lab listens while it shows, and stops when it goes. The feed keeps the last 64, so the lab shows recent ones as it opens. Only a ".debug" package turns it on. In every other build it's off, and nothing is recorded.
