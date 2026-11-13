# How TitanKeys feels

## The rulebook

1. **Two kinds.**
   - **Texture:** fast, repeating things. One pulse of about 17 ms, never a pattern.
   - **Message:** something happened. Always a pattern of two pulses or more, or one long pulse, with each pulse about 25 ms or more.
2. **Meaning comes from rhythm, the same everywhere.**
   - Rising (short then long) means confirm, or done.
   - Falling (long then short) means undo, or back.
   - Two equal firm pulses mean lock: it stays.
   - A quiet swell, light, fuller, light, means it changed by itself. Just an acknowledgement, nothing to do.
   - One long pulse means gone.
   - Three quick hard pulses mean no: nothing happened at all.
   - A hit that bounces and settles, each pulse shorter, means a wall: the gesture reached the end of what it can do.
   - A rising run of three means celebrate.
   - Short, short, long means saved: two light taps settle into a firm one.
   - Long, short, short means removed: a firm hit that trails off.
3. **Strength is length only.** More important or more destructive means longer.
4. **Gaps.** At least 35 ms between pulses, so each one reads on its own. Crunch is the one exception: its two pulses are meant to be felt as one gritty tick.
5. **No prebaked effects at all.** Everything is our own waveform, `VibrationEffect.createWaveform` with timings. The Titan has no strength control, so every pulse is at the default strength. A single pulse may be a one-shot.
6. **Repeats.** A message can't repeat within 300 ms. Texture can. So can Change's quiet swell: it's only an acknowledgement, so the gate takes it as texture, and it never blocks or delays another haptic (its gaps are still 35, so rule 4 holds).
7. **Boundaries.** A space, or a selection grabbing a word, gets one stronger pulse of about 30 ms instead of a texture tick.

The owner's lab notes led here: swipe and cursor ticks at 17 ms felt very good, every prebaked effect felt generic, messages were too subtle, and "can't save" didn't feel like an error.

**Haptics are only for what your thumb can't feel or see.** A physical key press already clicks under your thumb, so it never buzzes. A buzz is for the touchpad, which has no click of its own, and for what changes where you aren't looking.

## The families

Patterns are on and off in ms, starting with on.

| Family | Pattern | Plays for |
|---|---|---|
| **Texture** | 17 | Each of a flick's dots landing in the caret, swipe start, keys crossed, a light touch typing a key, each counted Space tap, steering picking up after a slip, cursor letters, the marked button in the word menu or a panel, a step of a stepper in the app, the playground's shake starting (a short run of ticks) |
| **Crunch** | 9, 12, 9 | Keys crossed while a stroke is clearly a delete flick |
| **Boundary** | 30 | A stroke that crunched stops being a flick, the cursor passes a space, the selection grows or shrinks by a word, a new page or group on a panel, a long delete's finger going into the pause zone or reaching the top-speed line |
| **Confirm** | 15, 40, 35 | Tap a word on the bar, the word menu opens, steering starts, paste from the clipboard page, paste the offered clip, a key or a chip tap on exact typing's bar, an option picked in the app |
| **Change** | 12, 35, 22, 35, 12 | The quiet swell: autocorrect, shorthand, a :name: emoji, a snippet, a held letter becoming its symbol, a hint in the guide, the playground's plant at the top. Texture for the gate |
| **Lock** | 30, 50, 30 | Selecting starts, a double or triple tap picks a word, Shift or Alt locked, a clip pinned, a lock on exact typing's bar, a quick setting switched on, Record a bug starts, a switch turned on in the app or the guide, agreeing to share swipes |
| **Release** | 30 | Shift or Alt unlocked, a clip unpinned, a locked chip on exact typing's bar unlocked, Back closing the word menu, a quick setting switched off, Record a bug stops, a switch turned off in the app or the guide |
| **Gone flick** | 50, 35, 20 | A delete flick, timed to its dots |
| **Hit** | 50 | Each word a long delete takes after its flick, shorter as it speeds up (see A held delete's hit, below). Each lightning flash in the playground |
| **Gone** | 60 | The clipboard cleared, a confirmed delete in the app |
| **Back** | 35, 40, 15 | Undo after Forget, Backspace undoing a change |
| **No** | 25, 40, 25, 40, 25 | A gesture that finds nothing at all: taps on Space or a steer with no text, a flick or a long delete with nothing to delete. Also a word that can't be saved, I on a word that's already a favourite, Ctrl + C with nothing selected, an action the guide blocks, something the app refuses. And the playground's confetti turning into rain, as a joke |
| **Wall** | 40, 35, 20, 35, 12 | A gesture that did something and then ran out: steering hits the start or end of the text, selecting reaches either end, a long delete takes the last word |
| **Celebrate** | 15, 40, 22, 40, 35 | The guide's celebrations, the playground's first confetti |
| **Saved** | 20, 40, 20, 40, 50 | Save in the word menu, by I or the strip |
| **Removed** | 50, 40, 20, 40, 20 | Forget in the word menu, by O or the strip |
| **Favourite** | 20, 40, 20, 40, 80 | Favourite in the word menu, by I on a saved word or the strip |
| **Unfavourite** | 30, 40, 20, 40, 12 | Unfavourite in the word menu, by O on a favourite or the strip |
| **Unsaved** | 40, 40, 20, 40, 15 | Unsave in the word menu, by O on a saved word or the strip |

The rising and falling patterns use a short first or last pulse on purpose: the short pulse is what makes the rise or the fall. So Confirm's 15 ms and Change's 20 ms are under rule 1's "about 25". The table wins over that guide.

Saved and Removed are the word menu's own, so Save and Forget feel different from each other and from everything else. Saved rises at the end, so it isn't No's three equal pulses or Celebrate's steady rise, and it has three pulses, not Confirm's two. Removed is its mirror, so it isn't Gone's one pulse or the two of Gone flick and Back. Save used to play Confirm, the same as the menu opening, so it felt like nothing new, and the repeat rule dropped it when you saved within 300 ms of the menu opening. Each message family has its own pattern, and tests/haptics checks it.

The word menu is a ladder, and its haptics are one system (owner, 2026-11-12). I moves a word one step up, and O one step down:

| Step | Button | Family | Pulses | Feel |
|---|---|---|---|---|
| Not saved → saved | Save (I) | **Saved** | 20, 20, 50 | Rises |
| Saved → favourite | Favourite (I) | **Favourite** | 20, 20, 80 | Rises to the strongest hit of the menu |
| Favourite → saved | Unfavourite (O) | **Unfavourite** | 30, 20, 12 | A light fall |
| Saved → not saved | Unsave (O) | **Unsaved** | 40, 20, 15 | A medium fall |
| Not saved → never suggested | Forget (O) | **Removed** | 50, 20, 20 | The heaviest fall |
| Favourite, the top | Already a favourite (I) | **No** | 25, 25, 25 | Nothing happened |

Going up rises: each pattern ends on its strongest pulse, and Favourite's last hit is the strongest of all. Going down falls: each starts on its strongest pulse, and the further down a step goes, the harder it starts and the more it weighs in all (62, 75 and 90 ms of pulses). Every gap is 40 ms, so each pulse reads on its own. A word that is a dictionary word starts at Favourite, and Unfavourite takes it back there. The spent heart plays No, not nothing, so a press that does nothing still answers. tests/haptics checks the directions and that each step feels like no other family.

Boundary is texture for the gate: it comes in the same fast runs as the letters, so it keeps their pace. Release is a single 30 ms pulse too, but it comes once, so it's a message. Hit is as firm as a message, but it's texture for the gate too: a long delete's words can come every 80 ms, and the repeat rule would drop them. Change's quiet swell is texture for the gate as well: it says something changed by itself, which needs nothing from you, so it never holds up or delays another haptic and may come again at once.

The owner's table had the delete flick at 50, 30, 20. Its gap is 35 instead, so it keeps rule 4.

Wall is a dot hitting a wall with energy: a hit, then rebounds that die away, like a bouncing ball. The owner's start was 40 on, 30 off, 20 on, 20 off, 12 on. Its gaps are 35 instead, so it keeps rule 4, and the bounces die away by length. Hitting a wall feels the same in all three gestures, steering, selecting and a long delete, and by the same rule (`WallHits`): it plays the first time the gesture meets its limit, then nothing while the finger keeps pushing, and again only after it has moved away and come back. It used to be no, which read as an error.

One rule tells the two apart in every gesture (owner, 2026-11-12): **no** when a gesture finds nothing at all, and **wall** only when it moved or did something and then ran out. So a flick with nothing to delete says no, and so does a long delete with nothing to delete, which never starts; a long delete that took the last word hits the wall. Taps on Space or a steer in a field with no text say no; steering or selecting into an end of the text hits the wall.

A long delete's wall is the same wall, played clean (owner, 2026-11-12). It waits until 100 ms after the last crunch, hit or tick, cuts the dots' ticks still to come, and nothing light plays for 100 ms after it (`HapticGate.reserveWall`). After the wall the delete stays quiet while you hold, zone ticks too. A stroke with nothing before the cursor never crunches, and a long delete with nothing to delete never starts: no words on the bar, no dots, only a no, once for the stroke.

### The delete flick and its dots

A flick shows a dot for each letter that went. The dots pop in one after another over 120 ms, at Android's animation speed. The flick's first pulse plays at the flick, and its 20 ms tail plays as a second vibration once the last dot has popped in: at 85 ms for a word of one or two letters, and up to just under 120 ms for a long one. The gate takes both parts as one message. With animations off, or no dots on the bar, it plays as one waveform, 50, 35, 20.

Then the dots go into the caret one after another, and every dot ticks as it lands, as texture. Deleting should feel satisfying. To keep each tick its own on the Titan's motor, the dots land at least 30 ms apart (`DOT_LAND_GAP`). So the way in takes max(450, 202.5 + 30 × dots) ms, times Android's animation speed, and never less than 30 ms a dot after that. Up to eight dots keep the owner's 450 ms. Thirty land over about 900 ms. Each dot's own trip takes 202.5 ms, and the first lands about 520 ms after the flick. `GestureDisplay.outMillis` and `dotStepMillis` hold this, and the drawing and the ticks both come from them.

A tick within 35 ms of the flick's tail is left out. Any key or touch cuts the dots at once, and the ticks still to come with them. A tick also only plays while that flick's dots are still on the bar. With animations off there are none. A long delete's lift turns its lines into dots too, which go in the same way and tick the same way, with the same switch. Its last word's tick or its no may have just played, so a dot tick within 35 ms of that is left out.

### A held delete's hit

Each word a held delete takes plays a hit: one firm pulse, the delete flick's first, 50 ms, so every word feels as strong as a quick flick (owner, 2026-11-12). Every word plays.

It gets shorter as the hold speeds up, so the hits don't blur together at the top pace. A hold's words go from 350 ms apart at pace 0 (`REPEAT_SLOW_MILLIS` in DeleteSwipe.kt) to 80 ms apart at pace 1. The hit is:

- **By pace.** 50 − (50 − 30) × pace, rounded: 50 ms at pace 0, 40 ms at pace 0.5, and 30 ms at pace 1.
- **With its quiet.** Never longer than the time between words less 35 ms, so the gap rule holds.
- **Firm.** Never under 30 ms, the shortest pulse that still reads as a message.

So at the top pace a hold plays 30 ms on and 50 ms off. Only the pace shortens it: how many words have gone doesn't.

The hit used to be a crunch that faded by length, 17 down to 9 ms over 8 words, then only every other word. The owner found it "quite bad and very light", so the held delete fade is gone, with its lab experiment and editor. An old stored fade setting does nothing.

The gate treats the hit as texture, so it never blocks a message, and a wall's quiet silences it as it does every other light tick. `HoldHit` holds the rule, and it has no Android parts, so tests/haptics can run it.

### A hold and its dots together

A hold's ticks and its dots' ticks are one sequence (`HoldTicks`): the flick that takes the first word, each word's hit, then at the lift a tick as each dot lands. A word's hit and a dot's tick never play within 35 ms of each other. The word wins, as it's what the hold just did, and the dot's tick is skipped. Every other dot ticks, and the dots keep their own 30 ms spacing. At normal animation speed the first dot lands about 640 ms after the lift, so a skip is rare: it guards a fast animation speed and a no just before the lift.

The lab times a hold from its start, when the first word goes. The pause before that is the hold's own, 400 ms (`deleteHoldPauseMillis` in DeleteSwipe.kt), so a change to it moves the lab's hold too.

### The crunch

A stroke becomes a flick candidate once it is clearly a delete flick: 1.5 keys from where it started, inside the delete zone and no higher than the middle row, there within 60 ms, and passing every flick rule that can be checked so far (`FlickCrunch` in DeleteSwipe.kt). From then on the keys it crosses crunch instead of ticking. Before that they tick as a swipe. If it stops being a flick, by turning back, going too far or too slow, wandering or leaving the rows, one boundary tick plays, the break, and the rest of the stroke ticks as a swipe. The words a long delete takes don't crunch: each plays a hit.

The numbers come from the owner's raw logs: of 1,226 real flicks that went 1.5 keys, 80% got there within 60 ms. Of 165 word swipes from the delete zone, 20 did, all from the edge by L and Backspace; they crunch briefly, then break. A word from P, in the top row, never crunches.

A crunch's gap is 12 ms, under rule 4 on purpose: its two pulses are one grain. The lab's editor keeps its gap between 4 and 30 ms.

### Shortcuts

Shortcuts stay quiet. The owner tried a confirm for Ctrl's copy, cut, paste, undo and select all, and change for Alt's and the Sym panel's symbols, and found it annoying (2026-11-12), so that experiment is gone. Ctrl + C with nothing selected copies nothing, so it plays no.

Shift + Backspace and Alt + Sym play nothing either. Both are presses of physical keys, which your thumb already feels, and you see what they do: a word goes from the text, or another keyboard comes up. A word delete by flick plays gone flick because the touchpad has no click, and these have one. Lock or release would say a modifier stayed or went, which isn't what happened, and confirm on every switch would be the annoying shortcut confirm again. With no other keyboard, Alt + Sym does nothing and plays nothing: it isn't a gesture that searched and found nothing, so no doesn't fit.

### A long delete's zones

Back within 0.8 keys of where the flick started, measured across only, so at any height, a long delete pauses, and 5.5 keys out is its top-speed line, past which it goes no faster. The finger going into the pause zone plays one boundary tick, and so does reaching the top-speed line. Each plays once per crossing and nothing more while the finger stays. The lab's experiment "Tick at delete zones" turns them off; it is on to start with.

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
- Each further word while you hold: hit, as firm as the flick's first pulse, shorter as the delete speeds up. Every word plays (see A held delete's hit).
- A flick with nothing at all to delete: no. A long delete with nothing to delete never starts; it plays the same no, once.
- A long delete takes the last word: wall, once, clean. The delete stays quiet after it while you hold.
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
- Ctrl + C with nothing selected: no. Other shortcuts, and Alt's and the Sym panel's symbols, play nothing.

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
- Exact typing's bar: a key is confirm. A tap on a Shift, Alt or Ctrl chip is confirm, a long press that locks one is lock, and a tap that unlocks a locked one is release. The Haptics lab's experiment "Exact-typing keys feel like a key (texture)", off by default, tries a texture tick for the keys instead, in TitanKeys Debug only.

**Shift and Alt**
- Locked: lock.
- Unlocked, from locked: release.
- Once, or off: nothing. That's a key press you already felt.

**Sym, emoji, quick settings and clipboard**
- The mark moves to another key: texture.
- A new page: boundary. A new group: boundary.
- A quick setting switched on: lock. Switched off: release.
- Paste a clip: confirm.
- Pin a clip: lock. Unpin it: release.
- Clear the clipboard: gone.

**Other**
- Record a bug starts: lock. It stops: release.
- The guide's celebrations: celebrate.

**TitanKeys' own screens** (Settings, every page in it, the guide and the playground)

A press on the touchscreen has no click of its own, so the controls that change something say what changed, in the keyboard's
own words. Everything else stays quiet: Android's stock click is a prebaked effect (rule 5), so TitanKeys turns it off in
Ink's screens and plays nothing in its place.

| Control | Plays | Why |
|---|---|---|
| A switch turned on | **Lock** | It stays on, as a quick setting switched on does. |
| A switch turned off | **Release** | It lets go, as a quick setting switched off does. |
| Agreeing to share swipes | **Lock** | It turns Share swipes on. Declining changes nothing, so it's quiet. |
| The guide's "rather not paste" box, ticked or unticked | **Lock**, **Release** | It's a switch. |
| The guide's Share my swipes, turned off | **Release** | It's a switch. Turning it on is agreeing on the policy page, which locks. |
| Shorter or Longer on a stepper | **Texture** | Each step is a small move, as a cursor letter is. Texture can repeat, so fast presses each tick. |
| An option picked in a list of choices: a preset, a spelling, the Enter key, a snippet start | **Confirm** | Done. It isn't Lock: Lock is kept for on and off. |
| Reset most things, once it's done | **Confirm** | Done. It puts choices back and wipes no data, so it isn't Gone. |
| Reset literally everything | **Gone** | Gone means gone: it wipes your words, snippets and the rest. |
| A confirmed delete: Remove all learned words, Delete the log, Delete my swipes, Delete waiting swipes, deleting a bug recording | **Gone** | Gone means gone, as the cleared clipboard. Only for wiping data. |
| Something the app refuses: a word, a snippet or a shorthand it can't take | **No** | Nothing happened, and the page says why. |
| The playground at 40 words a minute: the first confetti pops | **Celebrate** | The guide's celebration: you got going. |
| The playground at 100: the confetti turns into rain | **No** | The joke: the party's over, nope. The one No that isn't about nothing happening. |
| The playground at 150: the screen starts to shake | **Texture**, a short run | One short rumble as the shake starts: 7 ticks 55 ms apart, about 350 ms, never a continuous rumble. Each gap keeps rule 4. |
| The playground at 180 and up: each lightning flash | **Hit** | A firm hit with each flash, the held delete's per-word hit. Hit is texture, so it never blocks or delays a message. |
| The playground at the top: everything stops and the plant grows | **Change** | The quiet swell: it changed by itself, and it's calm now. |

The playground's speed show plays only those five big moments, never a step and never a comment (owner, 2026-11-13: "Add the
haptics"). Each plays once, the first time the show reaches it, and again only after the playground has calmed down (five quiet
seconds, with the number back to nothing) and the show reaches it anew. Lightning is the exception: each flash hits while the
storm lasts. A jump in speed that passes several at once plays only the highest, and counts the rest as reached, so two never run
into each other. All five share one switch, **Playground effects**.

They play through the keyboard's switches: **All haptics** off silences them, and each has its own switch in Settings >
Haptics, under TitanKeys' own screens, except the playground's five moments, which share **Playground effects**. Turning All haptics off plays its release first, while haptics are still on. Turning it
on plays nothing, as haptics were off when it was pressed.

## What plays nothing, and why

- **Physical key presses.** Your thumb feels them.
- **Shift or Alt once.** A key press, and the bar shows it.
- **Shift + Backspace and Alt + Sym.** Key presses, and you see the word go or the keyboard change (see Shortcuts, above).
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
- **The playground's speed show, apart from its five big moments, and idle games.** Each step, comment, spark and flame is for
  your eyes. Only the five moments above play, so nothing else buzzes while you type at speed.
- **Navigation in TitanKeys' own screens:** opening a page, ←, ×, Back, links and tabs. The screen changes, so you see it.
- **Plain buttons there:** Export, Import, Copy, Share, Open, Add app, the guide's Skip, Start again, Leave and the setup steps. What
  they do shows on the screen, or opens another one.
- **Removing one item from a list** (✕ on a word, a snippet, an app or a key). The row goes as you watch. Gone is for wiping
  data, after a confirm.
- **Typing in the app's own text fields.** Key presses.

## The gate

1. **Nothing cuts a playing message.** Until it ends, nothing else plays, and the next message waits 35 ms more, so the two read apart.
2. **A message can't repeat.** A message of the same family doesn't play within 300 ms of the last one that played. The rule is by family, not by haptic: two of the same rhythm that close run together into one longer pattern, whichever haptics they came from.
3. **Texture keeps its own pace.** Texture, boundaries and Change's quiet swell aren't held to the repeat rule, and they never block a message.
4. **Messages beat texture.** A message plays even right on top of a tick, and texture waits while a message plays.
5. **A wall has room.** No texture plays for 100 ms after any wall. A delete's wall also waits until 100 ms after the last texture, and no texture plays while it waits.

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
- A tap on a held delete's word in the list opens Hit's editor, like any other family's. Its slider sets the hit at the slowest pace; the pace shortens it from there. **Play a hold** plays a whole hold: the flick, 10 words 350 ms apart as a hold starts, then its dots at Android's animation speed, and each tick shows on the timeline as it plays. With animations off there are no dots, and the editor says so.
- On the timeline, each held delete hit shows at the length it played, with its word: "Held delete word 5 · Hit 40 ms".
- A delete's dots show as round marks in their own colour. A hold's dots have their number over them, and one skipped near a word's tick is hollow, with "skipped: near a word". In the list each is "Hold's dot 3 of 12".
- Notes: one overall, one per family and one per haptic. They go in `comments.json` in the app's files, with each family's pattern in use, and each haptic's family and pattern.

A tried pattern is kept in the `swipe_haptics` preferences, and only a package that ends in ".debug" reads it. Every other build plays the patterns above.

The keyboard and Settings run in one process, so the feed is a plain object, `HapticFeed`: the keyboard's `Haptics` records each play there with its result, and the lab listens while it shows, and stops when it goes. The feed keeps the last 64, so the lab shows recent ones as it opens. Only a ".debug" package turns it on. In every other build it's off, and nothing is recorded.
