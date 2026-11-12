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
   - Three quick hard pulses mean no: nothing happened at all.
   - A hit that bounces and settles, each pulse shorter, means a wall: the gesture reached the end of what it can do.
   - A rising run of three means celebrate.
   - Short, short, long means saved: two light taps settle into a firm one.
   - Long, short, short means removed: a firm hit that trails off.
3. **Strength is length only.** More important or more destructive means longer.
4. **Gaps.** At least 35 ms between pulses, so each one reads on its own. Crunch is the one exception: its two pulses are meant to be felt as one gritty tick.
5. **No prebaked effects at all.** Everything is our own waveform, `VibrationEffect.createWaveform` with timings. The Titan has no strength control, so every pulse is at the default strength. A single pulse may be a one-shot.
6. **Repeats.** A message can't repeat within 300 ms. Texture can.
7. **Boundaries.** A space, or a selection grabbing a word, gets one stronger pulse of about 30 ms instead of a texture tick.

The owner's lab notes led here: swipe and cursor ticks at 17 ms felt very good, every prebaked effect felt generic, messages were too subtle, and "can't save" didn't feel like an error.

**Haptics are only for what your thumb can't feel or see.** A physical key press already clicks under your thumb, so it never buzzes. A buzz is for the touchpad, which has no click of its own, and for what changes where you aren't looking.

## The families

Patterns are on and off in ms, starting with on.

| Family | Pattern | Plays for |
|---|---|---|
| **Texture** | 17 | Each of a flick's dots landing in the caret, swipe start, keys crossed, a light touch typing a key, each counted Space tap, steering picking up after a slip, cursor letters, the marked button in the word menu or a panel |
| **Crunch** | 9, 12, 9 | Keys crossed while a stroke is clearly a delete flick, and each word a long delete takes (fading, below) |
| **Boundary** | 30 | A stroke that crunched stops being a flick, the cursor passes a space, the selection grows or shrinks by a word, a new page or group on a panel, a long delete's finger going into the pause zone or reaching the top-speed line |
| **Confirm** | 15, 40, 35 | Ctrl's copy, cut, paste, undo and select all (lab experiment, below), tap a word on the bar, the word menu opens, a quick setting switched, steering starts, Record a bug starts or stops, paste from the clipboard page, paste the offered clip, a key or a chip tap on exact typing's bar |
| **Change** | 20, 35, 20 | Autocorrect, shorthand, a :name: emoji, a snippet, a held letter becoming its symbol, and with the lab experiment below Alt's symbol and a Sym panel symbol |
| **Lock** | 30, 50, 30 | Selecting starts, a double or triple tap picks a word, Shift or Alt locked, a clip pinned, a lock on exact typing's bar |
| **Release** | 30 | Shift or Alt unlocked, a clip unpinned, a locked chip on exact typing's bar unlocked, Back closing the word menu |
| **Gone flick** | 50, 35, 20 | A delete flick, timed to its dots |
| **Gone** | 60 | The clipboard cleared |
| **Back** | 35, 40, 15 | Undo after Forget, Backspace undoing a change |
| **No** | 25, 40, 25, 40, 25 | A word that can't be saved, a flick with nothing to delete, taps on Space that find nothing to pick or steer, Ctrl + C with nothing selected |
| **Wall** | 40, 35, 20, 35, 12 | Steering hits the start or end of the text, selecting reaches either end, a long delete takes the last word |
| **Celebrate** | 15, 40, 22, 40, 35 | The guide's celebrations |
| **Saved** | 20, 40, 20, 40, 50 | Save in the word menu, by I or the strip |
| **Removed** | 50, 40, 20, 40, 20 | Forget in the word menu, by O or the strip |

The rising and falling patterns use a short first or last pulse on purpose: the short pulse is what makes the rise or the fall. So Confirm's 15 ms and Change's 20 ms are under rule 1's "about 25". The table wins over that guide.

Saved and Removed are the word menu's own, so Save and Forget feel different from each other and from everything else. Saved rises at the end, so it isn't No's three equal pulses or Celebrate's steady rise, and it has three pulses, not Confirm's two. Removed is its mirror, so it isn't Gone's one pulse or the two of Gone flick and Back. Save used to play Confirm, the same as the menu opening, so it felt like nothing new, and the repeat rule dropped it when you saved within 300 ms of the menu opening. Each message family has its own pattern, and tests/haptics checks it.

Boundary is texture for the gate: it comes in the same fast runs as the letters, so it keeps their pace. Release is a single 30 ms pulse too, but it comes once, so it's a message.

The owner's table had the delete flick at 50, 30, 20. Its gap is 35 instead, so it keeps rule 4.

Wall is a dot hitting a wall with energy: a hit, then rebounds that die away, like a bouncing ball. The owner's start was 40 on, 30 off, 20 on, 20 off, 12 on. Its gaps are 35 instead, so it keeps rule 4, and the bounces die away by length. Hitting a wall feels the same in all three gestures, steering, selecting and a long delete, and by the same rule (`WallHits`): it plays the first time the gesture meets its limit, then nothing while the finger keeps pushing, and again only after it has moved away and come back. It used to be no, which read as an error. No is now only for nothing happening at all.

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

### The fade and the dots together

A hold's ticks and its dots' ticks are one sequence (`HoldTicks`): the flick that takes the first word, each word's fading tick, then at the lift a tick as each dot lands. A word's tick and a dot's tick never play within 35 ms of each other. The word wins, as it's what the hold just did, and the dot's tick is skipped. A thinned word plays nothing, so a dot next to it still ticks. Every other dot ticks, and the dots keep their own 30 ms spacing. At normal animation speed the first dot lands about 640 ms after the lift, so a skip is rare: it guards a fast animation speed and a no just before the lift.

The lab times a hold from its start, when the first word goes. The pause before that is the hold's own, 400 ms (`deleteHoldPauseMillis` in DeleteSwipe.kt), so a change to it moves the lab's hold too.

### The crunch

A stroke becomes a flick candidate once it is clearly a delete flick: 1.5 keys from where it started, inside the delete zone and no higher than the middle row, there within 60 ms, and passing every flick rule that can be checked so far (`FlickCrunch` in DeleteSwipe.kt). From then on the keys it crosses crunch instead of ticking. Before that they tick as a swipe. If it stops being a flick, by turning back, going too far or too slow, wandering or leaving the rows, one boundary tick plays, the break, and the rest of the stroke ticks as a swipe. The words a long delete takes crunch too.

The numbers come from the owner's raw logs: of 1,226 real flicks that went 1.5 keys, 80% got there within 60 ms. Of 165 word swipes from the delete zone, 20 did, all from the edge by L and Backspace; they crunch briefly, then break. A word from P, in the top row, never crunches.

A crunch's gap is 12 ms, under rule 4 on purpose: its two pulses are one grain. The lab's editor keeps its gap between 4 and 30 ms.

### Haptics for shortcuts

A lab experiment, off by default, for the owner to live with: should every way of doing something feel the same? With it on, Alt with a letter typing its symbol, and a symbol from the Sym panel, play change, as holding the letter does; Ctrl's copy, cut, paste, undo and select all play confirm. The gate's repeat rule keeps a fast run, such as a number typed with Alt, from piling up. Ctrl + C with nothing selected copies nothing, so it plays no, with the experiment on or off.

### A long delete's zones

Back within 0.8 keys of where the flick started, a long delete pauses, and 5.5 keys out is its top-speed line, past which it goes no faster. The finger going into the pause zone plays one boundary tick, and so does reaching the top-speed line. Each plays once per crossing and nothing more while the finger stays. The lab's experiment "Tick at delete zones" turns them off; it is on to start with.

## What plays what

**Swipe**
- The swipe is recognised: texture. This is the first tick, once the finger has gone as far as a swipe.
- Each key the swipe crosses: texture. The ticks come every 0.7 keys, at most one each 40 ms.
- A light touch types a key: texture. The touchpad has no click, so this stands in for the key's.

**Delete**
- A delete flick: gone flick.
- The flick's dots land in the caret: a light texture tick for every dot as it lands.
- A long delete's lines turn into dots as you lift, and those tick the same way as each lands.
- Keys crossed once the stroke is clearly a flick: crunch. If it stops being one: boundary, once, then swipe ticks (see The crunch).
- Each further word while you hold: crunch. In TitanKeys Debug, with the fade on, its pulses are shorter as more words go, then only every other one plays (see Held delete fade).
- A flick with nothing at all to delete: no.
- A long delete takes the last word: wall, once. The delete stays quiet after it while you hold.
- A long delete's finger goes into the pause zone, or reaches the top-speed line: boundary, once each time (see A long delete's zones).

**Space gestures**
- Each Space tap that counts toward a gesture: texture, so you feel tap 1 and tap 2 land. It's texture, so it never holds up the confirm or lock that follows.
- Steering starts: confirm. A double space with text selected puts the cursor back where it was before the select, or at the selection's end, plays confirm, and steers from there.
- Selecting starts (tap, tap, then hold): lock.
- A double or triple tap picks a word: lock, as the word is now selected.
- Nothing to pick: a double or triple tap with no word to take (an empty field, between spaces, only marks), or a steer with no text at all. Nothing happens, no selection and no lock, only a no, once. Two taps above Space play it too, but only in the guide's practice field, which teaches it. Everywhere else they stay quiet.
- Your finger slips off while steering and lands again in time: texture, once, so you know you're still steering and not typing.
- The cursor passes a letter: texture. It passes a space or a line end: boundary. So you can count words without looking. Punctuation has no tick of its own, as it almost always sits next to a space.
- The selection grows or shrinks by a word: boundary.
- The selection reaches the start or end of the text: wall, once each time it gets there.
- The cursor hits the start or end of the text: wall, once each time it gets there. It plays again once the cursor has moved away and comes back.

**Changes**
- Autocorrect on Space: change. Shorthand spelled out goes through autocorrect, so it plays change too.
- Backspace right after a change puts back what you typed: back.
- A held letter becomes its symbol: change.
- A :name: becomes its emoji, or a snippet its text: change.
- Alt with a letter types its symbol, or the Sym panel types one: change, only with the lab's Haptics for shortcuts on.
- Ctrl's copy, cut, paste, undo or select all: confirm, only with Haptics for shortcuts on. Ctrl + C with nothing selected: no, always.

**Bar**
- Tap a word: confirm.
- The word menu opens: confirm.
- The marked menu button changes: texture.
- Back closes the word menu: release. Typing or leaving the field closes it with nothing.
- Save, by I or the strip: saved. It plays as you press, and a save that fails plays no after it.
- Forget, by O or the strip: removed.
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
- **Save and Forget in the guide.** They're only for show there: nothing is saved or forgotten, and the guide says so.
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
- **Held delete fade** is in Experiments on the lab's page in Settings, next to "Dots speed up when long". Its switch turns the fade off and on, so you can feel a hold with and without it back to back. "Tune the held delete fade" under it opens the lab's screen on the fade's editor, and so does a tap on a held delete's tick in the list. The editor has a slider for the start (4 to 40 ms), the floor (4 to 40 ms, never above the start), the words to fade (1 to 20) and how often a tick plays after that (1 in 1 to 1 in 4). A strip shows a whole hold to scale on one time line: the pause, the flick, a bar for each of 10 words, the lift, and a round mark in the dots' colour for each dot as it lands. A thinned word is hollow, and so is a dot skipped near a word, with its reason. Play plays that hold, 350 ms a word as a hold starts, then its dots at Android's animation speed, and each tick shows on the timeline as it plays. With animations off there are no dots, and the strip says so. The next real held delete plays the edits.
- On the timeline, each held delete tick shows at the length it played, with its word: "Held delete word 5 · Texture 13 ms". A thinned one is hollow, with the reason "thinned". With the fade off, the label ends in "no fade".
- A delete's dots show as round marks in their own colour. A hold's dots have their number over them, and one skipped near a word's tick is hollow, with "skipped: near a word". In the list each is "Hold's dot 3 of 12".
- Notes: one overall, one per family, one on the fade and one per haptic. They go in `comments.json` in the app's files, with each family's pattern in use, the fade's four numbers, and each haptic's family and pattern.

A tried pattern or fade is kept in the `swipe_haptics` preferences, and only a package that ends in ".debug" reads it. Every other build plays the patterns above, with no fade.

The keyboard and Settings run in one process, so the feed is a plain object, `HapticFeed`: the keyboard's `Haptics` records each play there with its result, and the lab listens while it shows, and stops when it goes. The feed keeps the last 64, so the lab shows recent ones as it opens. Only a ".debug" package turns it on. In every other build it's off, and nothing is recorded.
