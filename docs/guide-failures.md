# The guide: every way to fail

Owner, 2026-11-13: "Gestures should be blocked not ignored", and every way of not following the instructions, or failing, gets a
clear, in-tone answer and a way on, with no dead end. This is the list, slide by slide. Each case says what the user did and what
the guide does.

Where it says "checked", a JVM check covers it (tests/guide, tests/gesture-display, tests/delete-hold). The rest is keyboard or
screen behaviour that only the phone shows, and the phone pass covers it.

## Every slide

**Blocked means blocked, never ignored.**
- A locked key or gesture never changes the field.
- The phone says no: the GuideBlocked haptic, family No (docs/haptics.md).
- The slide's chip says what's blocked, and why or what to do instead.
- It says so once for each press or each attempt.

| What the user does | What happens |
|---|---|
| A key the slide doesn't teach (letters, Backspace, Enter, Alt, Sym, fn and Ctrl shortcuts) | Nothing types. The slide's lock chip and a no, once for the press. Its repeats and its release are kept too. Checked: every lesson on every slide has its own chip that says it's blocked, on one line. |
| A delete flick the slide doesn't teach | It glows and crunches like any flick until the lift, so the user sees it registered. At the lift nothing goes, and the chip and a no follow. The crunch's feel is held back, so the no is the only feel. New. |
| A held delete the slide doesn't teach | Its glow shows. It's refused when the hold would start: the chip ("Held delete is blocked here", or the flick's if flicks are locked too) and a no. Nothing deletes, and the lift adds nothing more. New. |
| Double or triple Space where Space gestures aren't taught yet | The taps glow and tick as they count. Then it's refused as it would act: a double tap once it's settled, a third tap as it lands, a held tap as it registers, and a slide as it starts. The chip "Space taps are blocked here. That's later." and a no. Nothing steers, selects or types. New. |
| Two taps above Space where Space gestures aren't taught | The same chip and a no. New. |
| Holding a bar word where the menu isn't taught | No menu. The chip "Holding bar words is blocked here. That's later." and a no. |
| Holding a letter where holding isn't taught | The letter stays as typed and its repeats type nothing. The chip "Holding for symbols is blocked here. That's later." and a no, once for the hold. New. |
| Swiping where swiping isn't taught | The trail shows on the drawing, but nothing types and no word grows on the bar. At the lift, the slide's own swipe chip and a no. |
| Quick settings opened on a typing slide | "Settings? Nobody asked." |
| Sym opened too early | "Easy, tiger. That's later." Checked. |
| Alt on too early | "Alt is on, so the touchpad won't type." Checked. |
| Idle | After 20 s, "Still there?". After 20 s more, "The keys won't press themselves. We checked." The demo plays again after 5 s of nothing. Slides with help show it after 5 s, or 20 s if the help has a title. |
| Back | The leave pop-up asks "Leaving already?": Stay, or "I already know everything". Back again closes the pop-up, and you stay. |
| Leaving and coming back (Home, another app, a call) | Away from the guide, the keyboard is unlocked and as it always is. Back on the slide, its lock and bar come back, and the slide is as it was. A guide that Android closed resumes at its slide. Checked: resuming. |
| The screen turns off | The same as leaving. The decorations and the demo stop and pick up again. |
| Another keyboard chosen | The guide notices when it comes back into focus, and asks for TitanKeys again. |
| Touching the screen on a steering slide | "The screen? Really? Why'd you buy this phone?" |
| Touching the screen where the cursor has to be in one place (the delete slides, the symbol gaps, the clown) | The cursor goes back where the slide needs it: "Put it back. The keys do this, not the screen." and a no. New. |

## Next after a pass

Every passed slide shows Next, however it passed: typed, swiped, a hold, a pick from the panel, a paste or a clip (owner,
2026-11-13). Only the end has none, because its own buttons take its place. Checked for each slide.

A pass before Skip had shown lost its Next. Next fades in after a 250 ms beat, and the pass's pulse, at 220 ms, cancelled that
fade while Next was still unseen. The pulse now runs on its own. As a guard, Next is put in full view just after its fade should
be over, if anything stopped it.

## Hints and their timing

Each timer counts from the last input, and any input restarts it. None shows while a finger is on the touchpad, a key is held,
or the word menu is open. Each waits and looks again 0.7 s later.

| What | When | Slides |
|---|---|---|
| The slide's help, how to start its gesture | First after **3 s**, then again every **6 s** while the slide still waits for that first step. Each time the chip comes back in and the slide's demonstration plays again, so it's never a silent text swap. Never on a later step. (Owner, 2026-11-13: "sooner and more frequent".) | menu ("Hold, don't tap. Keep your finger on a bar word."), steer, selectmore |
| The demonstration (the ghost finger, the bar's hold) | After 5 s with no input, and 1 s later if a finger is down or the menu is open | Every slide with one |
| A chip | Shows for 3 s, and another waits until it's gone | All |
| The idle chips | After 20 s with no input: "Still there?". After 20 s more: "The keys won't press themselves. We checked." Once a slide. | All |
| The hunt | The bar drains over 20 s, then a mock. At 35 s a hint, and at 50 s leave to skip. It never fails. | symgroups, clown |

## Owner decisions this follows

docs/owner-decisions-2.1.md wins over this list wherever they differ.
- **The guide only goes forward.** Back exits through the "Leaving already?" pop-up, and opening the guide again resumes. No failure sends you back a slide: a hint goes back to a step's start, within the slide.
- **Every slide is correct on the first try.** Swipes and typos become the line's word, and nothing has to be redone because of a misread.
- **No means nothing happened.** A refused gesture changed nothing, even though its glow showed, so it plays No, never Wall. A blocked action plays No with its chip.
- **Each try says it's blocked** (owner, v5: nothing is blocked silently). The same chip isn't shown twice while it's still up.

## Typing

### type: Make a typo. We'll fix it.
| What the user does | What happens |
|---|---|
| Space before any letter | "Letters first. Then Space fixes them." Checked. |
| A word typed right, no typo | "Make a typo, bro. Learn to read." once, and the line goes on. Checked. |
| No typo in the whole line | It still passes, with "No typos? Show-off." Checked. |
| Any word typed wrong, however wrong | It becomes the line's word (forceFix), so there's no way to fail. |
| Deleting too much | Backspace is allowed. The grey line stays to type over, and nothing is lost. |
| A swipe | "Swiping's blocked here. Type it. Swiping's next." and a no. |
| A tap on the bar | "The bar's blocked here. Type it, typo and all." and a no. |
| A flick, a held delete, Space taps, Enter, Sym, Alt, a letter held | Blocked, as for every slide. |

### swipe: Swipe it. One hand, no questions.
| What the user does | What happens |
|---|---|
| A tap | "That's a tap. Slide, don't lift." Checked. |
| The word typed key by key | "That's typing. Swipe this one." Checked. |
| A swipe that misreads, or a typo in a swipe | It becomes the line's word (forceFix), and it counts. Checked. |
| A swipe that starts on Backspace, read as a flick | It glows, then "Flicks are blocked here. That's later." and a no. Nothing deletes. New. |
| Stopping before three swipes | The instruction counts the swipes left. |

### menu: Save a word. Then unsave it.
| What the user does | What happens |
|---|---|
| A tap on a bar word | "That typed it. Hold it, don't tap." |
| P closes the menu | "Closed it. Hold the word again." It goes back to the hold. |
| I twice makes it a favourite | "A favourite. Fancy. O steps it back down." |
| The bar runs empty | "Nothing to judge. Rare for you." and "Type a letter so the bar fills up." |
| A word already saved | O on it is the next step, and the slide moves on. |

## Delete

The delete slides start with their line in the field and the cursor where the deleting is.

| What the user does | What happens |
|---|---|
| The field emptied | It comes back with more to delete, under a new heading each time. |

### backspace: Delete the extra e. The old way.
| What the user does | What happens |
|---|---|
| A flick | It glows, then "Flicks are blocked here. Backspace this one." and a no. New. |
| Letters | "Typing's blocked here. Typing's done. Keep up." and a no. |
| The cursor moved with the screen | It goes back to the end. New. |

### flick: Flick away the stupid word.
| What the user does | What happens |
|---|---|
| Backspace | "Backspace is blocked. Flick it." Checked. |
| A held flick | The hold never starts: "Held delete is blocked here. Flick and lift." and a no. The flick still deletes its one word at the lift. Checked. |
| A swipe | "Swiping's blocked here. Flick from Backspace." |
| The flick now glows and crunches here | Before this change it didn't, because swiping was locked. New. |

### hold: Hold the flick. Delete in bulk.
| What the user does | What happens |
|---|---|
| A flick that lifts | It counts, and says "That was one word. Hold it down." Checked. |
| A hold let go early | "Therapy's not over. Hold longer." and the count stays. Checked. |
| Deleting into the line's first words | They come straight back. |
| The regrets all gone | Deleting is locked: "Blocked. The regrets are gone, the rest stays." New. |

## Steering

The field is the slide's line again and again. Deleting most of it brings it back: "The delete lesson is over, man."

### steer: Steer to the underlined word.
| What the user does | What happens |
|---|---|
| Two taps that lift | "You lifted. Keep the second tap down." Checked. |
| Three taps | "One tap too many. Two taps, the second held." Checked. |
| Held, then let go without sliding | It goes back to the taps: "Held it, then chickened out." Checked. |
| Steering past or away from the word | It waits until the cursor lands in it. Checked. |
| The screen touched | "The screen? Really?" |
| A flick | It glows, then "Flicks are blocked here. That's later." |

### selectmore: Grab my whole heart. Three taps.
| What the user does | What happens |
|---|---|
| Two taps, then steering | "That's two. Count to three." Checked. |
| Three taps that lift with one word | "One word? I'm worth more than that." Checked. |
| Too much grabbed | "Easy. Just my heart. Slide back a bit." Checked. |
| Let go before all of it | "So close. Then you let go." and back to the taps. Checked. |

## Symbols

Each slide's line has a gap, with the cursor in it. A wrong symbol in the gap is taken back out at once, and the slide says so.

### alt: Add the *, the traditional way.
| What the user does | What happens |
|---|---|
| A letter without Alt | "Blocked. Hold Alt while you press it." |
| Alt let go before the key | "Fine, that works too. Alt waits for one key." Checked. |
| Alt locked | "Locked it. Commitment, finally." |
| Alt on, then off | "On, off. Very decisive." and back to the start. |
| The wrong symbol | "That's not *..." It's taken back out. |
| Sym | "Sym is blocked on this slide. Not this way." |

### holdsym: Add the ?. Hold V for it.
| What the user does | What happens |
|---|---|
| Let go too soon | "Too quick. Hold it like a grudge." and the letter is taken back. Checked. |
| Another letter held | "Not ?. Took it back. V, held." Checked. |
| Alt and V | "Alt is blocked on this slide. Not this way." |

### symmain: Add the &. It's behind Sym.
| What the user does | What happens |
|---|---|
| Emoji first | "Emoji already? Patience. Sym again closes them." Checked. |
| An empty key | "That key's empty. Pick one with a symbol on it." Checked. |
| The wrong symbol | "Not &. Took it back." |
| Closed without typing | "Just looking? Window shopper." and back to Sym. Checked. |

### symgroups: Add how long you last in bed.
| What the user does | What happens |
|---|---|
| ∞ found fast, under 20 s, or straight from the first step before the hunt started | The chip "Quick. Just like you in bed." The owner's line, exactly, and nothing else takes its place. Checked. |
| ∞ found slowly | The description under the success line: "That took forever. Still shorter than you claim to last." The owner's line, exactly, in place of the slide's praise. Checked. |
| The wrong squiggle | It's taken back: "Wrong squiggle. Took it back. ∞ never ends." |
| A group from the bottom row, or a sideways swipe | "Yeah, that works too." |
| Closed | "Closed it. The ∞ is still in there." |

### clown: Sign it with 🤡. Find yourself.
| What the user does | What happens |
|---|---|
| A symbol, not an emoji | "That's a symbol, not a clown." |
| The wrong emoji | "Not you. The other clown." |
| Closed | "Closed? Your face is still in there." |
| A slow search | A hunt that never fails: mockery, then a hint, then leave to skip. |

## Copy and paste

### paste: Paste over the orange words.
| What the user does | What happens |
|---|---|
| V without fn | "Blocked. Hold fn with it. That's the trick." |
| fn and C, a copy | "That's copy. V for paste." The slide's own words go back on the clipboard, so fn and V still pastes them. The clipboard is put back as of this change; before it, this was a dead end. New. |
| Something else pasted | "That's not ours. Ours is better." A paste of ours still passes. |
| The selection lost | "Lost the selection. Classic. Paste anyway." |
| fn and V that changed nothing | After a moment: "Nothing happened. Shocking." fn isn't Ctrl yet, and the button below turns it on. |

### clipboard: Paste the one nice clip.
| What the user does | What happens |
|---|---|
| Emoji instead of symbols | "Emoji? Sure, A works from there too." |
| Which clip? | The right one to paste, the last, pulses in orange: its outline glows in and out and its fill warms a little, every 1.1 s, never a harsh flash. With animations off it keeps a steady orange outline. It pulses until it's pasted; a rude one pasted and taken back doesn't stop it. (Owner, 2026-11-13.) Checked: it's the nice one. |
| A rude clip pasted | It's taken back out: "That one's rude. Took it back. Find the nice one." and a no. |
| The panel closed | "Leaving? The nice one's still in there." and back to Sym. |

## The end and setup

| What the user does | What happens |
|---|---|
| The finale | It plays by itself, with nothing to fail, and its buttons come in under it. |
| Setup | Each part says what to do in Settings. Coming back with the part still not done shows its help again. |
