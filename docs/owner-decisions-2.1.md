# TitanKeys 2.1: owner decisions log

What the owner asked for, what's decided and by whom, and what's still open. The orchestrating session keeps this up to date. The newest entries are at the top of each section. "Owner" means the owner decided; "Orchestrator" means the orchestrating session decided within the owner's direction, with the reason given.

## Philosophy

- TitanKeys is meant to be **the default keyboard for the Titan 2 Elite**: all the good things of every keyboard used on this phone in one. It should be very easy for people who just install it and use it, and extremely tailorable for people who want to customise it. The defaults must be great; every behaviour can still be adjusted somewhere. (Owner, 2026-11-12)
- Personality over plainness. Don't strip character in the name of minimalism. (Owner, 2026-11-12)
- Owner feedback is for discussion. Work starts only on a clear go, and bug investigation doesn't wait. (Owner, 2026-11-12)

## Look and feel

### Decided
- The app background is **pure black with orange accents** (option B): titles, section labels and values in orange, switches orange when on. No warm dark brown anywhere. The bar stays **pure black**. (Owner, 2026-11-12)
- **Ink's font (Inter) everywhere**: the guide, the bar, the front page and the native screens. No Roboto. (Owner, 2026-11-12)
- Consistent text levels in Settings, with one "note" style for explanations that never looks tappable. Fixes for truncated text, fine print below 13 sp, the About icon, a stepper for "Hold for symbols", a key-cap chip for the snippet prefix, and one capitalisation rule. (Owner approved; rule by Orchestrator: sentence case, and key names written as on the key: Space, Shift, Alt, Sym, Backspace, Enter, fn.)
- Section headers fade softly as they scroll under the fixed title, done once in Ink. (Owner, 2026-11-12)
- Settings navigation: ← goes up a level within Settings only, × leaves Settings for the front page, and the Settings root shows only ×. (Owner, 2026-11-12)
- Front page: a proper header with the title, Guide and Settings; whole sentences in the prompt line; a dark text box with personality (an orange accent); the full real-size drawn keyboard, bottom row included; the touchpad zone kept and shown as an outline in its real place. (Owner, 2026-11-12)
- Animation timing is not unified across the app. Keep the current timings and fix only what looks off; the owner felt one set of timings could make it generic. (Owner, 2026-11-12)

- **Sizes stay the same whatever the phone's display size (DPI) or font size setting.** TitanKeys' own screens, the guide, the bar, the panels and the playground look the same at any setting. (Owner, 2026-11-12) How: in the batch, TitanKeys' windows size by the screen's width, not the system's density or font scale. A font size of 2.0 then no longer doubles the text, and "the scaling" item is covered by this rule.

- The front page touchpad zone uses **corner brackets with a label** ("TOUCHPAD"), with no lines across the keys. (Owner, 2026-11-12)
- Add the real **Inter Bold** font file (OFL, from the official source), listed in the licences. (Owner, 2026-11-12)

## Gestures (double space, triple space, flick to delete)

"Gestures" means exactly these three. (Owner, 2026-11-12)

### Decided
- Hitting a wall feels the same in all three. **No** means nothing happened at all, for example an empty field: a flick, or a held delete that never starts, or steering or selecting with no text. **Wall** means the gesture moved, then ran out. (Owner, 2026-11-12)
- The double-space caret is **solid and never blinks**. Blinking is only for the held delete's pause zone. (Owner, 2026-11-12)
- Double space on a selection turns it back into a cursor and feels like steering, not a failure. (Owner, 2026-11-12)
- Triple space: keep the solid orange selection, for personality. The thin caret at the moving end is replaced by a directional marker. A handle (line plus knob) is the default; a chevron and the old caret can be picked in the Haptics lab. (Owner, 2026-11-12)
- Held delete:
  - The pause before it starts is 400 ms.
  - The glow comes from the bottom-right corner and follows the finger. It also shows on every single flick, late if the flick was very fast.
  - Pills are drawn at the real word lengths; with no text left, no pills.
  - A pause zone near Backspace, where the caret blinks.
  - The top speed stops at about F/G; no "top speed" label.
  - Zone ticks are on.
  - Each word is one firm hit, about as strong as a flick.
  (Owner, 2026-11-11 and 2026-11-12)
- A flick never leaves two spaces: "one two| three" becomes "one three". (Owner, 2026-11-12)
- The flick's dots queue up. When a new flick joins, the wait restarts for the whole line. (Owner, 2026-11-12)
- The flick-dots wait can be set in the lab; the owner likes the current 200 ms. (Owner, 2026-11-12)
- The space-gesture zone is not made taller: the misses are timing, not height. (Owner, after the logs study, 2026-11-12)

- **The flick's glow ramps with distance** (owner, 2026-11-12, 23:58): "there should be no glow when at the very right and the glow is stronger the more left it is to the limit... when I flick I shouldn't see the glow when I'm still at the right." None at the right edge, full at the top-speed line (about F/G), then capped. A short flick's after-lift glow follows the same ramp. The held delete's glow is unchanged.
- The held delete has no fade: each word is one firm hit, 50 ms at the slow pace down to 30 ms at top speed. That's the version the owner has on the phone, and the owner keeps it. (Owner, 2026-11-12)

- A double or triple tap only picks or steers when it lands on the Space bar itself, not on the Shift or ▦ spots beside it. No lab slider for the wait: 150 ms is fixed. Going lower recovered nothing in the logs. (Owner, 2026-11-12)
- The space gestures' "first tap alone" wait goes from 200 ms to **150 ms**. It recovers 7 failed steers in the logs and adds about 0.09 false triggers per 1,000 words. The hold wait stays at 200 ms. (Owner, 2026-11-12)

## Haptics

### Decided
- Every haptic follows the rulebook in `docs/haptics.md`: one meaning per pattern. (Owner, 2026-11-12)
- Quick settings: on plays Lock, off plays Release. Record a bug: start plays Lock, stop plays Release. (Owner approved the Orchestrator's audit, 2026-11-12)
- Exact-typing bar keys: a lab switch to try texture against confirm before deciding. (Owner, 2026-11-12)
- Status: No against Wall, the quick-settings and Record-a-bug haptics, and the exact-typing lab switch are built in round 7 (merged at 418daaa). Not yet felt on the phone.
- Haptics for shortcuts (Alt symbols, Sym panel, Ctrl) were tried and removed as annoying. Ctrl+C with nothing selected still plays No. (Owner, 2026-11-12)
- Lab defaults: dots speed up when long, held delete fade (since retired), zone ticks on. (Owner, 2026-11-12)
- Changes that happen by themselves (autocorrect, shorthand, snippets, :name: emoji, a held symbol) play a **quiet swell**, 12, 35, 22, 35, 12. It's a subtle acknowledgement with nothing to do, and the gate treats it as texture. The undo of a fix stays Back. Designed by the Orchestrator to the owner's brief ("subtle, just an acknowledgement"). (Owner approved, 2026-11-12)
- No haptic strength setting: only on or off, because the patterns are tuned carefully. (Owner, 2026-11-12)

## Word menu

### Decided
- **One ladder for every word**, dictionary words included: Not saved → Save → Saved ("remembered") → Favourite. I goes one step up and O one step down, never skipping. Unfavourite always lands on Saved. Forget appears only on a not-saved word that TitanKeys learned from typing. One-letter words have no Forget. (Owner, 2026-11-12)
- Hover labels: the hovered button grows into a pill with its name, and the word slides to stay centred, then never moves. No labels when nothing is hovered. No first-time hint. Back has no label and no hover. Hover also works from K, L and Backspace, and it stays after a press. (Owner, 2026-11-12)
- Icons: the heart, a broken heart for Unfavourite, a bookmark, a broken bookmark for Unsave, and a bin for Forget. No quotes around the word. (Owner, 2026-11-12)
- Colour: **the word is always white**. All particles and animated marks are orange. Buttons are white at rest, the filled heart is orange, and the hovered button is orange. (Owner, 2026-11-12)
- No icon changes instantly; every change animates. (Owner, 2026-11-12)
- Animations play on both sides of the word, roughly symmetric:
  - Favourite: the word pops and hearts burst out.
  - Unfavourite: a heart cracks, the halves fall, and the word gives a tiny shake.
  - Save: a ribbon drops and bounces.
  - Unsave: the ribbon tears.
  - Opening the menu on a saved word plays a soft echo.
  (Owner, 2026-11-12)
- Favourite's heart burst is "done perfectly". **Save becomes a particle effect like it**, with orange bookmark shapes on both sides, replacing the ribbon drop. (Owner, 2026-11-12)
- Review of v3.1 (owner, 2026-11-12, 22:25): the ladder logic is fine, but the state isn't shown properly.
  - **Back moves to Backspace's centre**, so I, O and Back are evenly spaced, a key apart. It no longer dodges the rounded corner.
  - **Hover stays when the state changes**, as on the Sym panel: after a press, the pill stays on that key and shows its next step.
  - Save and Unsave: the owner still doesn't like anything about the animation, and maybe not the ribbon icon either. Three ideas are on the mockup page: A, a check in a ring; B, a pin; C, an underline. The owner picks one.
  - Symbols and emoji can't be saved: the word store only takes 2 to 24 letters, so Save fails silently. Open: hide Save for them, or keep them somewhere else.
  - "and" shows as not saved: by the one-ladder rule, Saved means you pressed Save, and a dictionary word starts at Not saved. Open: should dictionary words start at Saved?
  - A visual mockup comes first (owner): scratchpad cal-tool/menu.html, on the phone at http://localhost:8765/menu.html.
- Mockup review (owner, 22:45):
  - The positioning is good: I, O and Back are evenly spaced, with Back on Backspace's centre.
  - Save gets a **floppy disk** icon. The icon is fine; it's the effect that's bad. Save needs an effect that works, either a particle burst that really lands or another kind of effect.
  - The heart is good. The heart breaking is slightly bad and needs work.
  - **Hide Save for symbols and emoji.**
- **A dictionary word counts as saved.** Saving a word means adding it to the dictionary, and forgetting a word means taking it out. So "and" starts at Saved, and taking a dictionary word down the ladder takes it out of the dictionary. (Owner, 2026-11-12)
- A quiet ambient status: a favourite has a faint heart drifting up now and then, and a saved word has a faint resting ribbon. It pauses while hovering. (Owner, 2026-11-12)

## Guide

### Decided
- The guide only goes forward. Back exits; opening the guide again resumes where you left off. (Owner, 2026-11-12)
- No slide that teaches picking a word from the bar. Every slide is correct on the first try, swiping included. (Owner, 2026-11-12)
- The hidden phrase spelled by the lines' first letters is **GO TOUCH GRASS NOW**: 15 slides, each line a real takeaway. (Owner, 2026-11-12)
- Hold V for "?": "Are you even reading this?" (Owner, 2026-11-12)
- The ∞ slide's title doesn't name the symbol. Found fast: "Quick. Just like you in bed." Took long, in the description: "That took forever. Still shorter than you claim to last." The clown hunt works the same way: impressed if fast, roasted if slow. (Owner, 2026-11-12)
- A full-width chapter bar with a tick per slide. A normal-size Next. Room goes to the text, not the keyboard. The drawn keyboard is the real size and never covers the real panels. (Owner, 2026-11-12)
- The word-menu slide teaches the real ladder (Save, then Unsave) and matches the real menu exactly. (Orchestrator, within the owner's "the guide matches the real keyboard")
- Every recording bug gets fixed: gap slides with a dead end, the selection toolbar, hints during gestures, wrong chapter messages, and the drawing glitches. (Owner, 2026-11-12)
- End screen: × on the right and bigger, "Trust me bro." on its own line, a real "Start the guide again" button, and a custom "Leaving already?" pop-up. (Owner, 2026-11-12)

- **Visual review, 2026-11-12 22:45: the guide is 5/10, and "feels like there's no design".** (Owner)
  - The top bar is claustrophobic and all orange. The owner dislikes the slide ticks along the top and where Skip sits.
  - The owner dislikes the scaling and the positioning of things.
  - Idea from the owner: the title line ("Swipe it. One hand, no questions.") could be all upper case.
  - The keyboard at the same width and 60% height is fine.
  - Next: the Orchestrator judges the guide from screenshots and proposes how to reach 10/10. A mock on the quick page comes before anything is built.

## Typing, autocorrect and words

### Decided
- Autocorrect controls this release: today's behaviour is the default, with everything on, and each kind of fix has its own switch. (Owner, 2026-11-12)
- Shorthands: grow the list, leave out anything that's also a real word, and let users switch each one on or off and add their own. (Owner, 2026-11-12)
- The snippet prefix is a symbol you can type with Alt. The default is "/" (Alt+G, or hold G), and it can be changed. (Owner approved; "/" chosen by the Orchestrator)
- Spelling: add **Canadian**, whose description carries the joke ("Both. Eh."). (Owner, 2026-11-12)
- A lone "i" becomes "I" even when "i" is in Blocked words. (Owner; fixed 2026-11-12)

- Apostrophe autocorrect is planned in three groups, which the owner agreed:
  - Always fix: dont, isnt, youre, im…
  - Fix only with context: its/it's, were/we're, lets, id, ill.
  - Never change automatically: well, hell, shell, shed, wed, cant, wont.
  The reverse direction (it's → its) is left out. The owner wants the logs analysed before any of it is built, including how much context the language model should use. (Owner, 2026-11-12)

- Study result (A, apostrophe-study.md, judged by meaning): today 73% are right, with no wrong changes but many misses. With the study's policy it's 93%, still with no wrong changes. The policy uses 5 words before and 2 after, a margin of 1.0 nats, and fires 2 words back. D builds it exactly to the study's spec, which replaces the earlier lists. were → we're is never changed automatically. (Owner's go, 2026-11-12)
- Boundary study (the owner's idea): the window goes back to the last sentence mark (. ! ? a line break, or the start of the field), at most 10 words, plus 2 after. Commas don't count as boundaries; they measured no better. Still no wrong changes, and the fewest misses. (Study result, 2026-11-12)
- Build it as soon as the study is in. The study judges which form was right by reading the context, not by what the owner kept, because the owner usually leaves wrong apostrophes in place. A does the study; D builds it in TypedCorrector. (Owner, 2026-11-12)

- Built into TitanKeys, so they work with Key Mapper off (owner's yes, 2026-11-12):
  - **Shift + Backspace** deletes a word.
  - **Alt + Sym** switches to the next keyboard. The owner suggested Alt + Sym rather than Shift + Sym.
  - Both count only while the modifier is held down. A tapped or automatic Shift doesn't count, so Backspace at the start of a sentence stays a plain Backspace. Switching goes to the next keyboard, as Key Mapper's switch did, not to the picker. (Orchestrator)
- Key Mapper: the owner imported v2. **Sym + S, P and E ($ % =) are removed from Key Mapper, and TitanKeys doesn't get its own version** (owner: "no need to implement sth like that in titankeys"). key_maps_titankeys_v3.zip is the backup without them; importing can't delete, so the owner removes them by hand. (Owner, 2026-11-12)
- Key Mapper: key_maps_titankeys_v2.zip removes Shift + Sym and Shift + Backspace, which TitanKeys now does itself. Sym + S, P and E and Space + Alt stay switched off, because they double Sym or hold back Space. (Owner asked, 2026-11-12)
- Found: a Key Mapper combo that starts with Shift or Sym makes Key Mapper send that key a second time whenever the combo doesn't complete. TitanKeys then reads one push of Shift as two, and one push of Sym as two. A combo that starts with Space holds back every Space press until it's let go. The owner's working copy of the export turns those combos off. (Orchestrator, from Key Mapper's source, 2026-11-12)

## Setup and settings scope

### Decided
- This release: Setup checks the phone's own settings: Sym and fn as Programmable key, and each app's scroll mode. It only detects them and opens the right page; it never changes system settings. (Owner, 2026-11-12)
- This release: fix the problems in our own settings:
  - one "Show the bar" setting;
  - one name for the Space cursor and select setting;
  - stale boring guide references;
  - Record a bug listed once;
  - new Touchpad, Haptics and Shortcuts sections.
  (Owner, 2026-11-12)
- Later: accented letters in Sym, voice input, a per-app modes page, bar size. (Owner, 2026-11-12)
- No: a letter-shortcuts row. (Owner, 2026-11-12)
- Rename "Scroll assistant outside text fields" to "Scroll assistant". Inside text fields TitanKeys' own touchpad takes over, so the switch really turns app scrolling on or off. (Owner, 2026-11-12)
- Bug: with that switch off, the keys still scroll apps outside text fields: first Reddit, then many apps ("it's scrolling many diff things now"). The block fails when the keyboard isn't in focus. The owner rejected the quick theories (Back service off, Atlas' service) and asked to find the last build that worked and compare the code. Status: A's hold-trace build is ready, and the bisect list of old builds is ready, but neither has been run yet. (Owner, 2026-11-12)
- Trace result (21:53): TitanKeys reads its Scroll assistant setting as **on** (touchpadOnlyInFields=false) in every app, and every exported typing log since 11-09 says the same. So it never wanted the block, by design, and no code change explains it. The owner checks what the switch shows. If it shows off, it's a display or save bug. (Orchestrator)
- Scroll assistant (owner, 22:45): the switch was on all along. The owner had read it the other way round.
  - Remove the explanatory text under the switch.
  - Default: TitanKeys' Scroll assistant starts the way the phone's own Scroll assistant is set. On if the phone's is on.
- Bug (owner, 22:45): the **Orange accents** setting doesn't work, at least not everywhere.
- No "Set it up for me" through Accessibility: Google Play's policy forbids it. (Owner's rule "only if it passes Google Play"; the Orchestrator checked the policy.)

- No: password and 2FA codes on the bar (autofill chips). No: back up everything in one file. No: changing the defaults to match stock and BlackBerry (Caps Lock on double Shift, hold for a capital, spelling from the phone's language). (Owner, 2026-11-12)

## Calibration

### Decided
- Calibrate by the **gaps between keys**, with every key the same width. Only the gaps are drawn, as bands that line up with the real gaps below the screen. Sliders for key width, gap width and shift, each with fine −/+ steps. This replaces the two-edge version, which was too hard to judge by eye. (Owner, 2026-11-12)
- The lines are **one pixel**, at the **centre** of each gap, with one at each outer edge. There are two sliders, Spacing (key plus gap) and Shift. This replaces the bands and the separate key and gap sliders. (Owner, 2026-11-12)
- Calibrated by the owner on a quick page served to the phone (2026-11-12): a key and a gap every 107.5 px on the 1076 px screen, centred. This is now the default for everyone, and values stored against the old default are dropped.
- The real keys, 1:1 (owner, tuned on the quick page, 2026-11-12): **square**, with **no gap between keys** in a row and **10.5 px between rows**. At 1:1 the four rows are about 461 px tall, nearly half the screen.
- The overlays must line up exactly with the calibration, at least from Q (top left) to Sym (bottom right). (Owner, 2026-11-12)
- The Sym panel keeps its own rows. Only its columns follow the calibration. (Owner, 2026-11-12)
- Key calibration is for the overlays tied to keys: the Sym and emoji panels, the clipboard, the word menu buttons, and the guide's hints and trail. It has two sliders, Q's left edge and P's right edge, over the three letter rows. The defaults come from the phone's real geometry, and the owner fine-tunes it on the phone. (Owner, 2026-11-12)
### Open
- The guide's keyboard: **same width, 60% of the real height**, with the bars below counted in the overall height. Same shape but smaller was rejected. (Owner, 2026-11-12)
- Waiting for one batch after the owner reviews the current build (owner: "let's do the whole app thing in one batch after my review"):
  - the guide keyboard at 60%, with no gap between keys and 10.5 px between rows;
  - the guide crash with the phone's font size at its largest (2.0): brought in ahead of the batch, merged at a7cb9b9 (owner: "bring the crash fix in now");
  - the app's scaling at large font sizes ("pretty awful");
  - on the playground, when the screen is fully orange, the words can't be read.
  - the guide's text field always shows two lines (owner, 2026-11-12: "it shd always show two lines, just bugged rn");
  - the playground's text is rewritten in the owner's offensive, fun, casual tone, **lowercase with no punctuation, nothing off limits**. The tone sample is approved except the touchpad line, and "i am stupid" goes (owner, 22:45). The full text is long enough to type for a long time without looping (owner, 2026-11-12).
  - Before the batch is built, the owner sees the layout on a sample page with the real text and real elements (owner: "show me the layout first").
- Built so far: the calibration as the default, and in-app calibration centred. This is look-only; the touchpad code is unchanged.

## Launch

### Decided
- The stability gate is **at least 3 days**, plus time to make the videos. (Owner, 2026-11-12)
- **Languages need a repeatable framework**, so every language is added the same way. **Cantonese gets full support first**, because the owner can tune it, and the other languages use the same framework. (Owner, 2026-11-12)

### Open
- The owner is thinking of making the public launch **3.0, with languages**, rather than 2.1. Not settled.
- Launch 2.1 as a public beta? The Orchestrator recommends yes, after a stability gate: about 3 days of daily driving with no crash or broken basics, a clean fresh install, a known-issues list and the News feed working. Testers on GitHub are unlimited. Play closed testing can use a Google Group, but new personal accounts need 12 testers for 14 days. The owner hasn't decided.
- Languages: the Orchestrator suggested two levels. Basic would be open to anyone, with autocorrect off by default and the label "help us make it great". Full would come only after a native speaker has worked on it, which is the owner's rule. The owner hasn't decided. 3.0 is languages (#56).

### Decided
- For the promo launch, build a **megathread** listing all the current tools developers have built for the Titan 2 Elite: keyboards, key mappers, launchers, light and backlight tools, layout projects and so on, with TitanKeys among them. (Owner's note to self, 2026-11-12)

## Process

- **Reset all settings** (owner, 2026-11-13, 10:50): "there's so many now its easy to mess up." (Owner) The Orchestrator's design
  (unapproved), queued for F after the global accent:
  - a row at the bottom of the Settings root;
  - every switch, stepper and choice goes back to its default; Scroll assistant follows the phone again;
  - words, snippets, shorthand, app lists, the clipboard, Share swipes consent and recordings stay;
  - a confirm "Reset all settings?" with Reset and "Keep mine", and a note afterwards in the owner's tone.
- **Decor review goes through the Orchestrator** (owner, 2026-11-13, 10:45): "for the decor do the mockups and then you review it for
  me." (Owner) V sends its preview page and screencaps to the Orchestrator, who approves them before anything is built.
- **Bar words, bottom aligned?** (owner, 10:45). The Orchestrator's answer: at equal sizes they already share one baseline. Only a
  word that StripFit shrinks sat higher, because it was centred on its own small letters. D will put every word on the full-size
  words' baseline. (Orchestrator)
- **The paste slide turns wholesome** (owner, 2026-11-13, 10:40): "I am doing great, lets make this wholesome instead so its not what
  the user thought... from I am doing great to I hope you are doing great, genuinely." (Owner)
  - The nice clip "I'm doing great." pastes "I hope you're doing great, genuinely".
  - The line is "Wanna paste? Sym, then A. I hope you're doing great, genuinely": 62 characters, starting with W for the finale.
  - The slide says "Didn't see that coming, did you?", and the rain is 🫶. (Orchestrator)
- **The paste prank** (owner, 2026-11-13, 10:30): "So is my whole personality didn't land. I don't get it honestly." (Owner)
  - The Orchestrator's replacement: the nice clip "You're doing great." pastes "You're doing great. Kidding", and the slide says
    "Gotcha. You believed it."
  - The line is now "What you copy is in Sym, then A. You're doing great. Kidding". It's 60 characters, with no full stop, as
    the summary needs.
- **Haptics on every touchscreen press in the app** (owner, 2026-11-13, 10:20): "Add Haptics to every touch screen press on the
  app. Do a audit. For example, the cross button and the back button." (Owner) This reverses "plain buttons stay quiet". D audits
  every control and adds a light Press haptic with its own switch, with a check that no clickable is left silent. Controls with
  a meaning keep theirs.
- **"and" corrected to "And", and "auto" to "AUTO"** (owner's bug, 10:20). Orchestrator's diagnosis from the code: the word menu
  saves or favourites the word as shown, so a capital from the sentence start, or caps, gets stored and treated like a name's
  capitals. W fixes it in the ladder branch: store the dictionary's case, or the natural case, and repair existing entries once.
- **The ∞ slide's text** (owner, 2026-11-13, 10:05–10:15). (Owner)
  - The field line: "How long I last in bed with your mother: ∞". It's built as "Seriously, how long I last in bed with your
    mother: ∞", because the finale's GO TOUCH GRASS NOW needs this line to start with S. (Orchestrator) This reverses the
    earlier "me @ your mom" skip, at the owner's own request.
  - The hunt's instruction is "Find the one that never ends.", and it never names ∞.
  - Done is "Sure you do." The "Love you too" theme is gone. (Orchestrator)
  - A slow find says its seconds: "That took N seconds. Still longer than you claim to last."
  - The clipboard digs now fit on one line. "As discussed, Next is not a personality." didn't land, so it's now "Looping in
    your last brain cell." (Orchestrator)
- **Finale and end screen** (owner, 10:10): the gathered phrase is too small; the end screen sits too low and its scaling is off;
  grass should grow along the bottom of the screen, like the owner's reference (layered flat blades, matched to our minimal
  style); the phrase should hold longer. (Owner) C does it, with grass as ink at three strengths on orange.
- **Build #5** (b3d80476) was installed at 10:15 with no crash.
- **Guide notes** (owner, 2026-11-13, 10:00): the decorations barely show; the held-delete slide's text box doesn't need a deleting
  animation; the held-delete zones are badly labelled (ASDF looks like the place to swipe); the pause zone should be full height.
  (Owner)
  - A does all four: decorations about twice as strong; no field animation on that slide; zones relabelled as start, a
    "← faster" direction and pause; and the pause zone full height, in the real gesture too if it was one row only.
- **The ladder is approved: build it in the app** (owner, 2026-11-13, 09:55): "let's focus this patch on the ladder logic and the
  animation. no need to build from scratch. the mockup only for concept and design and execution reference. build the app." (Owner)
  Corrections to v4:
  - Not saved is a plain outline, with no dots and no fill.
  - Save's book is opaque when closed, so the word can't be seen through it.
  - The book sits fully above the bar, not across its top edge.
  - Hovering Unsave covers Favourite, as the pill already does.

  W builds it on the existing code. The Orchestrator's calls (unapproved):
  - one-letter words, symbols and emoji open no menu;
  - Unsave on a dictionary word removes it from suggestions without blocking it (it can be learned back), with a fallback to
    Saved → Block if the store can't do that safely;
  - Block takes over the autocorrect refusal that Forget used to add;
  - "Undo after Forget" goes away.
- **The guide always starts from the beginning** (owner, 2026-11-13, 09:45): "everytime I click on the guide, i shd start from the
  beginning. no saved slide." (Owner) Done in ca6559a: the guide no longer saves the slide, and an older saved place is cleared on open.
- **Ladder review, round 3** (owner, 2026-11-13, 09:20); the owner rated v3 7/10 for animation quality and execution. (Owner)
  - The dotted outline isn't readable enough.
  - Hovering a button previews the word as it will be, in grey. Pressing makes it real: for Block, the strike draws in orange,
    left to right.
  - Effects drift further up above the bar.
  - Unsave's page is one full page with its torn edge on the left.
  - Block's ring and the strike don't go together: alternatives wanted. Unblock reverses the stroke's direction.

  Orchestrator's proposal, unapproved: Block is the strike itself, drawn like a pen, with the letters greying as it passes, and
  Material's strikethrough as its icon. C builds v4 (`X:/Atlas/.dev/mockups/ladder-v4.html`) to the 10/10 list: springs, one
  motion language, anticipation and follow-through, interruption, haptic beats, 60 fps, reduced motion and quiet ambient marks.
- **Orange accents is global, with a roast** (owner, 09:20): "add a roast when they turn it off, to say that they're a boring
  person, and it should work for the app as well. literally everything globally." (Owner)
  - This replaces F's keyboard-only call.
  - Off, orange becomes cream everywhere, and cream fills sit behind ink text.
  - The note when off, the Orchestrator's line: "Orange is off everywhere. Boring, just like you."
  - F does it after merging Size with D's bar change.
- **Ladder review, round 2** (owner, 2026-11-13, 09:05). (Owner)
  - Not saved shows as dotted outline letters.
  - The book effect is OK, but the book icon looked bad, and mixing a ribbon icon with a book effect is inconsistent: one metaphor.
  - One-letter words don't open the menu at all.
  - Refine the animations.
  - The mockup must use what would ship: the final icons and animation.

  The Orchestrator's v3 page (cal-tool/ladder.html) uses:
  - the app's own heart, filled heart, broken heart and Back drawables;
  - a new open book for Save and the same book with a zigzag tear for Unsave, on the same 24 grid and 2-unit line as the heart;
  - Material's block and its ring for Block and Unblock;
  - particles drawn from the same paths, and the text in the app's Inter;
  - Save filling the dotted letters solid as the book claps shut, and Unsave tearing a page and draining the letters back to dots.
- **Forget keeps the menu open** (owner, 2026-11-13, 08:55): "instead of showing the undo when forgetting a word, keep it on the bar
  and just show a new status." (Owner) There's no Undo notice. The word stays in the menu at Not saved, and I (Save) brings it back.
  Sent to W.
- **Ladder review (08:55):** the Orchestrator wrote the ladder out against build #4's code. There are five differences:
  - Forget on words that are only candidates;
  - Forget closing with Undo;
  - a dictionary word's O as Forget with Undo;
  - the hover resetting;
  - hearts then glitter on open.

  W is fixing all of them. Open: whether a dictionary word's O says Unsave, as the Orchestrator recommends so every word reads the
  same, or Forget.
- **Key calibration refined: 108 px a key and gap, centred** (owner, 2026-11-13, 08:38, on full-height lines in quarter-pixel
  steps). It replaces 107.5. (Owner) The outer keys now reach 2 px past each screen edge: the real keyboard is a touch wider than
  the screen. The drawings follow the calibration exactly, including the 2 px past each edge. The row gap keeps the hardware's ratio.
  (Orchestrator)
- **Word menu, still wrong on build #4** (owner, 2026-11-13, 08:20):
  - Forget showed on a gibberish word typed once. Cause: D's menuFor counts any word "still gathering evidence" as learned.
    The owner's meaning is a word TitanKeys knows, i.e. would suggest.
  - The hover still resets after a press ("said multiple times"). D reported it done, but nobody checked it with a hand on the
    touchpad. The fix should follow the quick settings' hover fix.
  - The owner's call: "If it's worker then use a new worker dedicated to this." (Owner) W was started for it, with checks for the
    whole ladder.
- **The boring guide notes that Space gestures land best a bit higher** (owner, 2026-11-13, 08:15): "its better to type above space
  bar because of the touch zone." (Owner) It's a new topic, "Space gestures: tap a bit higher", at the top of Cursor and picking
  text. It says to tap the top half of Space or the keys right above it, from C to N, because the touch zone stops around the middle
  of Space. That's true to the code: CursorGesture.inZone takes Space's width on its row and the row above.
- **Bar words: one size, the middle bold** (owner, 2026-11-13, 08:15): "I want it consistent but bolded is a nice touch." All three
  are 20 dp on one shared baseline, truly centred on the bar's height, and the middle is in a bolder weight. (Owner) This replaces
  D's 20/18 split. D picks Medium or Bold for the middle from renders, as the Orchestrator's call.
- **Size setting: the bar keeps its height** (owner, 08:15): "the bigger everything means the bigger elements, bar size stays the
  same." Only what's inside the bar grows. (Owner)
- **No TitanKeys Alt + Backspace** (owner, 2026-11-13, 08:15): "I didn't want Alt backspace to work unless it's a native key
  combination, and I'm intending it to be hold shift then backspace." (Owner)
  - Android's own Alt + Backspace deletes the line in a standard field. So TitanKeys now sends Alt + Backspace to the app as it is,
    with no word delete of its own. (Orchestrator)
  - A held Shift + Backspace deletes a word (TitanKeys'). Ctrl or fn + Backspace deletes a word, as Android does natively.
- **Playground haptics: yes** (owner, 2026-11-13, 08:05: "Add the haptics"). (Owner) The design, the Orchestrator's and approved:
  - only five big moments, each once until the playground calms down: 40 wpm Celebrate, 100 the rain No, 150 the shake one
    short rumble, 180+ each lightning flash a firm hit, the plant at the top the swell;
  - nothing per step or per comment;
  - its own "Playground effects" switch in Settings > Haptics.
- **A size setting that makes everything bigger, the bar included** (owner, 08:05): "Right now it's smaller which should be fine for
  most ppl but usually I like bigger and it was bigger for me." (Owner) The Orchestrator's design, unapproved:
  - Settings > Look and feel > Size: Normal 1.0 (the default), Bigger 1.2, Biggest 1.4;
  - it grows FixedScale's unit, so text and the bar's height grow;
  - anything calibrated to the physical keys keeps its place.
- **The boring guide's Scroll assistant text** (owner, 08:05): it should say the phone hijacks the touchpad with its Scroll
  assistant, the creator doesn't like it, and TitanKeys can block it. The old text was confusing. (Owner) Rewritten by the
  Orchestrator.
- **Say we use a local LLM** where it helps, with "that data doesn't go online and is never used for training" (owner, 08:05). (Owner)
  - The Orchestrator's topic "The local LLM" sits in the boring guide's Typing section and names DistilGPT-2, which runs on the phone.
  - To stay true to the privacy policy, it says what you type never goes online and never trains any model. The one exception is
    Share swipes, which is off until you agree and sends swipe examples to train the swipe models.
- **The front page's text is one giant paragraph (owner, 2026-11-13, 07:50):** "I would want to join them without adding a new line
  even though they're two different lines. So it's one giant paragraph not multiple." (Owner) It replaces the v5.3 rule "starts at
  the sentence you're in". The reference is a two-line window on one continuous paragraph, scrolling a line at a time, with no break
  or dimming between sentences.
- **The front page gets a title and a description, like the guide (owner, 2026-11-13, 07:45):** "I'm expecting the format to be
  like the guide there a title and a description." (Owner) The title is "PRACTICE. YOU NEED IT." in the guide's title style
  (Orchestrator's pick, unapproved). The line under it stays the description, and the speed comments still take its place.
- **Morning calls (Orchestrator, 2026-11-13, 06:40; none approved by the owner):**
  - Guide titles stay cream, as in v2, which the owner saw on build 2ec5f751 ("Looks much nicer now"). Settings keeps its orange
    titles (option B).
  - The playground keeps the 60% keyboard drawing, as the guide does. It follows the owner's later v2 call ("I think 60% rn is
    good height"), not the earlier "full real-size drawn keyboard".
  - The TOUCH ZONE brackets stay at the zone's real corners, which fall on the Q, P and Shift labels. Each bracket passes behind
    its label with a 4 dp gap, instead of striking through it.
  - The playground is a real field: its word menu really saves and favourites, unlike the guide's show-only one.
  - On the playground, the rain and reactions fall behind the header and the field, so the text is always on top.
  - The playground's reference always fills two lines. It runs on into the next line, and never ends on a lone word.
  - The guide's end screen and finale hide the keyboard, so the buttons never jump.
  - Drawn-key sublabels are at least 13 dp, the same floor as all real text.
  - On the Swipe and Steering slides, a lone Space that would type is refused with the slide's chip, and a steer's two taps still work.
  - Back from the end restarts the fun guide at slide 1.
  - Release notes: "New look: Pure black, Inter everywhere, and way less orange." The old "orange only where it means do this now"
    stopped being true once the text box's orange bar and the brackets came back. New lines cover the app's haptics and the share
    screen.
- **"me @ your mom" is out:** "is bad let's skip it." Any @ line gets something better, or no joke at all. The slide may use another symbol if the line lands better (owner, 23:50). (Owner, 23:45)
- **Before bed (owner, 01:35):** "Ok continue working and make decisions good night." (Owner)
  - **"Trust me bro." moves** from the end screen to the share step, right before you accept. You still return to the end screen after. (Owner) C does it.
  - **Some slides lose their Next** after they're passed. Every passed slide shows Next. (Owner bug) A does it.
  - **The ∞ slide is the owner's sex joke** about how long you last in bed. The Orchestrator's title (unapproved): "Add how long you last in bed." The symbol is never named, and the fast line ("Quick. Just like you in bed.") and slow line ("That took forever. Still shorter than you claim to last.") are kept exactly. A does it.
  - **The paste slide:** the right clip, the last one, blinks: a gentle orange pulse until it's picked. (Owner) A does it.
- **"Touchpad call it touch zone"** (owner, 01:28). The outlined area the touchpad reads is the **touch zone**: the label on the playground's drawing says TOUCH ZONE, and so does the text about that area. The Orchestrator's reading (unapproved): the hardware and Settings' "Touchpad" section keep their name. C does the label, F the text.
- **Typing 3/3 (the save slide):** "the tap hint still needs to be sooner and more frequent." (Owner, 01:25) The Orchestrator's call (unapproved): first hint after 3 s, repeated every 6 s while waiting, each repeat visible, never over an open menu. A does it, and checks the other slides' timings too.
- **Reviews must not violate the owner's decisions** (owner, 01:20): "check aesthetically and readability but also you should not violate my decisions." Where design-v2 or any Orchestrator or worker call conflicts with this log, the owner's decision wins, unless a later owner message replaced it. Conflicts found so far and sent back to C:
  - the front page text box's orange accent;
  - the TOUCHPAD corner brackets on the drawing;
  - the end screen's bigger × on the right, "Start the guide again" and "Trust me bro.";
  - the guide's own animation timings, which stay as they are: the 220 ms move is for content swaps only. (Owner)
- **Review of build 2ec5f751 (owner, 01:15):** "Looks much nicer now to be honest. Just review the readability." (Owner)
  - Titles too small; the reference text should be two lines; the bar's text is a bit too small. "Text size should convey hierarchy and they should also be readable. You need to consider the size of my screen like physically." "Some elements are too small... Don't overdo it but review the sizes."
    - The Orchestrator's sizes (unapproved): Display 36, Title 24, Body 19, Field 24, Label 14; bar words 20; Ink body at least 18, secondary at least 15, labels at least 14; no real text below 13 dp.
  - Haptics for the whole app itself.
  - Share swipes goes back to the end screen, with a clear indication of where to tap accept.
  - The ending animation is the logo itself, animating into TitanKeys.
  - Account for every way of not following instructions or failing. "Gestures should be blocked not ignored": a delete still shows, and then says it's blocked.
  - Later: review every visible element visually.
  - Assigned:
    - C: sizes, the 2-line field, the share flow and the logo finale;
    - A: blocked gestures and the failure matrix (docs/guide-failures.md);
    - D: bar text;
    - F: app-wide haptics and Ink sizes.
- **Text review (owner, 23:40):** "review also the text to make sure it's good and not cringey and not ai sounding but maintain my tone. leave the ones that I chose at least consistent to my idea." (Owner)
- **Slide effects are decoration only** (owner, 23:25): "it should be sth that decorate the slides. not sth that changes the meaning or gives any meaning." Each slide gets its own (design-v2 §7). GO TOUCH GRASS NOW is good. (Owner)
- **The Orchestrator decides everything for 2.1 (owner, 23:15):** "You can change the decisions that you made yourself. If anything fails, or looks ugly, or whatever, you can change your mind. otherwise make all decisions. I want this to be ready for a 2.1 release, with as much things fixed and polished and refined as possible. And then tmr morning I just review and if no comments good to go. so it shd be 10/10. review the work yourself. make the micro decisions if needed." The owner saw the list of 28 decisions in design-v2.md and the chat, and raised no objection. (Owner)
- **Unattended round (owner, 22:55):** build straight to "10/10" without mockups. The Orchestrator decides, marks every decision as unapproved, and the owner reviews them on the final product. The decisions are in [design-v2.md](design-v2.md). Mockups come back next round. (Owner)
- The Orchestrator may take the phone any time without asking: "take my phone. do not ask for it again." (Owner, 2026-11-12)
- **The WPM starts only once a real word is being typed.** (Owner, 2026-11-12)
- The word menu's Save doesn't need a floppy disk: "I care mostly about the particle effect." (Owner, 2026-11-12)


- No AI co-author lines in commits. No pull requests; the owner opens them. No force push. (Owner)
- Phone tests are silent. Never force-stop TitanKeys. Don't take the phone while navigation is running. (Owner)
- Pause the workers near the usage limit and resume after the reset. (Owner, 2026-11-12)
- Look-only tests (calibration, guide drawings, overlays) go through a quick page served to the phone's browser first, which takes 15 s instead of a 15-minute build. The result is then built in once. Never change touchpad code for them. (Owner, 2026-11-12)
- The flicker when a selection collapses comes from the Sefirah clipboard app, not TitanKeys. (Found 2026-11-12)
