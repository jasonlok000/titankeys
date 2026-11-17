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
- **Back from anything the end screen opens returns to the end screen, as it was left** (owner, 2026-11-14: "When going back from the boring guide triggered from the end screen it should go back to the end screen. Applies to everything else for end screen"). (Owner) Built by V (claude/end-back): the boring guide and Go type something open as /end/guide-boring and /end/play, with an /end page under them that reopens the end with its finale already played; the playground's Back from there goes back instead of leaving the app. How it's built is the Orchestrator's call (unapproved); merged 2026-11-14, not yet tried on the phone.

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

- **Owner's answers, 2026-11-13 (afternoon):** "yes to all" (fix the Sym jump now; Biggest for the owner; starting size from the
  phone's font size). (Owner)
  - **Size grows only the bar's contents** (owner: "size shouldn't apply to the bar height … maybe not literally everything
    bigger. i feel like the settings become a bit unusable after making it bigger. Maybe only applies to the elements of the
    bar? (but not the height of the bar itself)"). (Owner) Orchestrator's design, unapproved: the bar's words, its word menu, chips,
    clip offer and hints grow; the bar stays 56 dp; Ink's settings, the panels, the guide and the front page stay Normal. The
    setting moves to The bar as "Bar text size" or similar. F builds it.
  - **Starting size follows the phone's font size** until the user picks one: under 1.15 Normal, from 1.15 Bigger, never Biggest
    (owner: "how about by default if it detects the size being big, it'll select big, not biggest? I think big is a good spot for
    me personally"). Biggest is only ever picked. The resets go back to following it. The owner's phone (2.0) lands on Bigger by
    itself; the Orchestrator checks it once build #12 is installed. (Owner)
- **The bar's contents: optically centred, lower** (owner, 2026-11-13: "cause its a smiled and it blends with the border of
  the screen itself, I'm considering to make it actually sit lower… build the text and stuff to be visually aligned instead of
  mathematically aligned"). Today the small letters' middle sits on the bar's middle line (StripFit, 44 px down an 88 px bar).
  The bar's black runs into the black border below the screen, so its bottom edge can't be seen and its middle looks higher
  than it is. The Orchestrator agrees: one shared optical line for everything in the bar (words, menu word, pills, icons, chips,
  hints, clip offer), moved down together; touch areas and the bar's height unchanged. The owner picks how far on the phone
  (cal-tool/bar-low.html, 1 dp steps, at Normal, Bigger and Biggest, light or dark app above). Then it gets built. Waiting.
  - The owner's first try: 8 dp lower at Normal ("I think it's close now"), which leaves 30.5 dp over the small letters and
    14.5 dp under the baseline. On request ("can we do sth like drawing some guidelines"), the page now draws guides (the bar's
    middle, the letters' middle, the small letters' top and the baseline, with the gaps in dp) and saves every change to
    cal-values.log, so the Orchestrator reads the final pick from there.
  - **The owner's pick: 11 dp lower** (owner: "I measured with a ruler and I think 11dp lower is right… the letter's middle is
    right in the middle"). That centres the letters on the visible black band, the bar's 56 dp plus about 22 dp of black border
    under the screen: 39 dp from the bar's top. The Orchestrator's proposal, waiting on the owner's go: build it as that band's
    middle (one constant for the border), not a loose 11 dp; everything with text in the bar sits on that one line; containers
    that can't fit there (the 42 dp menu pill, the two-line clip offer) shrink or rise only as much as they must to stay inside
    the bar; the lowest letter keeps at least 4 dp from the screen's edge (Biggest rises about 1 dp); Exact typing's bar and the
    Sym panel's bottom row get the same line; the bar's height, touch areas, keys and the guide's slides don't change.
- **Bar text size: Smaller, Normal, Bigger; everyone starts at Normal** (owner, 2026-11-13: "let's make bigger normal, normal
  smaller. default all use the new normal."). (Owner) Smaller 1.0, Normal 1.2 (the default for everyone, no more following the
  phone's text size), Bigger 1.4 (renamed from Biggest so the middle reads Normal: the Orchestrator's call, unapproved). F builds it.
- **The lower line and the effects** (owner: "will this affect stuff like introducing crops etc to all our effects? we have to
  take into consideration the curved corner too"). The Orchestrator's answer: yes. Pieces that fall (Unsave's page, Unfavourite's
  broken heart, Block's flecks) already fall off the screen's bottom edge and would just reach it sooner; what must stay whole
  (the word, its underline, pills and icons, hearts and puffs, the idle games' letters, rope and ball) must stay inside the bar
  and inside the screen's curved bottom corners, using the corner shape TitanKeys already measures. The build renders every
  effect and idle game on the new line at all three sizes with the curve drawn, and checks it. Still waiting on the go.
- **The idle animation works but is rare** (owner: "I've never seen the idle animation like ever. is it actually working?").
  Triggered on the phone with the debug broadcast: the firework played in the playground's bar, then the words came back. It
  needs the keyboard open on a field that shows words, 3 to 4.5 minutes with no key, touch or swipe, the screen on, then a 25%
  chance, and never twice in a row: about 15 untouched minutes before the first game, so it's almost never seen. The
  Orchestrator's proposal, unapproved: wait 60 to 90 s and a 50% chance, about 2.5 minutes on average; any input still ends it.
- **The keyboard is always dark** (owner, 2026-11-14: "Wait what? I never know it follows light mode. Make it always black. The
  app is always in its current dark mode settings."). (Owner) The bar and panels stop following the phone's light mode (Look.of
  read uiMode): always black, so the border illusion always holds. The light look's colours (such as the accents-off favourite
  #35312E) go. Queued for W.
- **The border illusion's other places** (owner's answers to the Orchestrator's list): the app's own screens are fine; only the
  demo surfaces, the guide and the playground, get a pass for the border (queued for V). The Sym panel's other pages get the same
  even row spacing (W). Orange screens keep the maths centre. Brightness is ignored for now. Screenshots for the Play listing will
  need the border or a frame added (noted for the Play launch).
- **The idle games get a visible floor** (owner, 2026-11-13: "do you think we shd add a simple cream or white line to simulate the
  floor?", then on the phone preview cal-tool/floor.html, "let's do 3"). (Owner) A 1 dp line in the text's cream at 55% at
  Idle.FLOOR, stopping before the curved corners with soft ends, shown only while a game that uses the floor plays (the figure,
  Bowling, Race, the Firework, Juggle's Drop), fading in and out with it. Why (Orchestrator): the screen's edge blends into the
  border, so a figure standing on it looked like it stood on nothing. A builds it.
  - **Width and ends** (owner, after the width preview: "I like 80%. Optimize it so that they're still on the ground. Right now
    there's a fade on the edge with the page? We don't need that. Just a simple 80% width line and have the animations all be
    animated for that."). (Owner) 80% of the room between the corners, centred, solid with hard ends; every game reworked so
    whatever stands, lands, rolls or runs on the floor stays over the line.
  - **Only the idle games get a visible line** (owner: "Those should keep the border as a illusion. No visible line needed.").
    Everything else in the bar keeps the invisible optical line: nothing is drawn for the words, the clipboard suggestion, the
    chips or the menu. (Owner)
- **A selected word is underlined on the bar, in the guide and the real keyboard alike** (owner, 2026-11-13: "I think you prob
  misunderstood and the word TitanKeys in that guide should be selected as in highlighted litearlly selectfed, not underlined?",
  then "I think if the word is highlighted, it should be underlined before going into the word menu. in the word menu it
  follows existing rules. and obviously guide and the actual keyboard shd match in behavior. selected as in similar behavior to
  triple space's native select words."). (Owner) Whenever a field has a word selected, the bar shows it alone in the middle,
  underlined in the accent, until its menu opens; in the menu, colour only (the underline clashed with the strike). The guide's
  menu slide selects TitanKeys in the box like triple Space does and goes through the same real path; the guide-only underline
  and wiring go. The Orchestrator had read "keep it highlighted" as an accent underline: its miss. V builds it.
- **Bar: a global check that everything follows the visible-border line** (owner: "the hint for that should also follow the
  visible border thing. not the old mathematical center. please do a global check to ensure that everything is following that rule
  now."). W lists every view and drawing in the bar and the bottom band with what it's centred on; the two-line clip offer and its
  hint move to the visible band as nearly as fits. C (gestures) and A (idle games) are on the same rule.
- **Smart Launcher's search box, diagnosed live** (owner's OK: "type in my launcher. test it out."). The box (plain text, Search
  action) takes letters and Android's own Ctrl + Backspace, and TitanKeys' bar suggests correctly there, but TitanKeys' direct
  edits do nothing: "teh " isn't corrected to "the", and Shift + Backspace removes one character. The same keys in TitanKeys' own
  box work. The panels open there by the debug broadcast, so the panel can show; the real Sym key reportedly doesn't open it. D
  builds a fallback (check each direct edit took, else redo it with key presses, remembered per field) and traces the Sym key.
- **The app is called "TitanKeys Keyboard"** (owner, 2026-11-13: "could we call it sth like TitanKeys Keyboard in the app name so
  that it's easier to search in the launchers?"). (Owner) The Orchestrator's scope: the launcher label, app info, the keyboard
  picker and Accessibility say "TitanKeys Keyboard"; the app's own screens keep "TitanKeys"; the debug build stays "TitanKeys
  Debug". F builds it; the Orchestrator checks on the phone how the launcher shortens a name that long.
- **"Built for Titan 2 Elite" on Play** (owner: "can we do sth like 'built for titan2elite' when people open their play store?").
  For the Play launch, not 2.1. No custom device badge was found for Play listings. The plan instead: make it available only on
  the Titan 2 Elite in Play Console's device catalog (if its model is listed), and say "Built for the Titan 2 Elite" in the short
  description, feature graphic and screenshots. Recorded in the Play plan. (Orchestrator)
- **The automatic capital's arrow sits 3 dp over the words' line** (owner, 2026-11-14, on the quick page cal-tool/clip.html,
  "This is what I want for cap", after "I think the issue with the uppercase is visually it looks too low because its too close to
  the corners"). (Owner) Up 3.0 dp at Normal, not moved in from the corner, beside words and beside a clip alike. The
  Orchestrator's reading (unapproved): 3 dp at Normal scales with the Bar text size like everything else in the bar (2.5 at
  Smaller, 3.5 at Bigger); its middle is then level with the capitals' middle, within a pixel of a clip's digits and its icon.
- **The clip's icon follows the capitals' middle, and the clip is never cut** (owner, 2026-11-14: "I think those icons should follow
  the middle of uppercase like how the auto cap icon, so I think the paste icon is too low. Just let me align it by eye", then on
  cal-tool/clip.html "For clipboard I added my settings. Use my previous alignment for the uppercase"). (Owner) Icon 0.25 dp over
  where build #19 draws it once uncut, which puts its drawing's middle on the clip text's capitals' middle (1169.0 px at Normal);
  the arrow stays at 3 dp over the line. The clip offered alone shows whole: its icon's bottom edge and its tails (brackets, g, y)
  were cut at its box's bottom on #19. W builds it.
- **The swipe trail: option 6** (owner, 2026-11-14, on cal-tool/swipe.html: "for swipe do 6 because its the tallest while still
  beig center aligned"). (Owner) The three key rows' middles 12 dp apart (8.7 on #19, 14 on #16), the middle row 3 dp over the
  words' line, level with the automatic capital's arrow; the outer half of the top and bottom rows drawn at 0.3 of its height. The
  bottom row sits 8.0 dp over the screen's edge and the lowest point 6.2 dp (#19: 8.3 and 4.0). The Orchestrator's call
  (unapproved): the trail keeps these dp at every Bar text size, as it doesn't scale with the words. C builds it.
- **The 11 dp line, round 2** (owner, 2026-11-13, on build #13: "With the illusion shift, the modifier bar is not done well. The
  delete cursor looks visibly too far up not vertically center aligned. Space gestures seems also not be optimized… The position
  and scaling of the word menu effects seems inconsistent. Like the book seems to be much higher up than the heart animation. I
  didn't check the idle animations but make sure that its perfect with the new updated visual border rule. I though you're
  checking all visually especially with one more version with a simulated curve corner and border on your end?"). (Owner)
  - Reproduced by the Orchestrator on the phone: Shift once, and Ctrl, put their chips at the bar's very bottom, cut by the
    screen's edge, while Ctrl's shortcut text sits right; the renders had shown them whole, so the render's chip layout doesn't
    match the phone's.
  - The Orchestrator's miss: only static frames were reviewed. The gesture display was knowingly left on the old middle, and
    the menu effects, the modifier bar and the idle games weren't checked in motion or on the phone.
  - Assigned: W, the modifier bar and the menu effects' one anchor and scale; C, the gesture display (delete caret, flick dots,
    double and triple Space) on the line; A, the seven idle games and their ways home on the line. Each sends keyframe strips at
    Smaller, Normal and Bigger in W's border-and-corner render through corner_check, which the Orchestrator reviews frame by frame,
    then checks on the phone (Shift, the PANEL and IDLE_BAR broadcasts, a held bar word) before any build goes to the owner.
  - The release candidate (build #14) is withdrawn: don't sign it.
- **No AI-use declaration** (owner, 2026-11-13: "We don't care about ai use declaration remember?"). (Owner) Pre-release and PR
  handovers carry no AI-use line, whatever AGENTS.md says.
- **Languages in parallel?** (owner, 2026-11-13: "if I already want to start working on the other languages support, can I do it
  in parrelel or something?"). The Orchestrator's answer: yes, in its own session and branch, in two steps. Now: design and
  research only (where English is assumed today, the framework every language plugs into, how Cantonese is typed on a QWERTY
  keyboard and what the bar becomes, and data licences), new files only, no phone. After 2.1's release: the refactor of
  English into the framework, then Cantonese. Proposed as a session card, waiting on the owner.
- **The 11 dp line: build it** (owner, 2026-11-13: "For the 11dp lower, implement it. Do visual checks with the corner overlays
  that we used before. Check the visuals if they're aligned properly with a simulated border too. Do it"). (Owner) W builds it
  with the Orchestrator's mitigations below: the middle of the bar plus a 22 dp border; boxes at least 1 dp inside the edge (the
  pill slims to about 32 dp); side items take their margin from the curve; Exact typing's bar and the Sym panel's bottom row on
  the same line. Renders go through tools/corner_check.py (radius 100, margin 4) plus a simulated-border render, and the
  Orchestrator reviews them before the build.
- **The idle games: one roll at the 3-minute mark** (owner: "I think the probability is misunderstood. I meant like it triggers
  only on the 3 min mark of idle. Not resetting. The chance of triggering is 1/4. Meaning it won't trigger if I go afk for
  longer… And no two same animations in a row. Also no two triggers in a row."). (Owner) This replaces the Orchestrator's 60 to
  90 s, 50% proposal. Exactly 3:00 after the last input, one 25% roll per idle period, none after it; the period after a game
  can't play; never the same game twice running. A builds it.
- **Shorthand is saved in lowercase** (owner: "For short hands lets make sure that it doesn't get saved as with uppercase. So I
  won't mistakenly saved the uppercase version of it when setting up."). (Owner) The shorthand is stored lowercase, existing
  ones are repaired once, and D finds why capitals got in although its fields ask for none (and fixes TitanKeys if it
  capitalises fields that don't ask for it). D builds it.
- **Evaluations for the owner (2026-11-13):**
  - **Idle game odds, simulated** from the real rules (a wait of W to 1.5 W, then the chance, never twice running). Average
    untouched, keyboard-open time to the first game: 15 min today (W 3 min, 25%), 2.5 min proposed (W 60 s, 50%). Chance in one
    untouched pause: 60 s 0% and 0%; 90 s 0% and 50%; 2 min 0% and 50%; 3 min 0% and 75%; 4 min 17% and 85%; 5 min 25% and 91%.
  - **The lower line against the edge and the corners** (the screen reports a 100 px radius at each bottom corner, so the
    whole 88 px bar is inside the curve's height). At 11 dp lower the line is 26.6 px over the bottom edge (43.8 today).
    Px over the edge at Smaller, Normal, Bigger: tails 11.3, 8.2, 5.1; the guide's underline 10.2, 8.5, 6.8; the 42 dp pill
    -6.2 at every size (off the screen); Shift/Alt/Ctrl chips 8.7, 5.1, 1.5; the notice 6.2, 2.1, -2.0; the two-line clip offer
    can't move (it already fills the bar at Bigger). The side margin the curve needs grows from 40 px today to 60, 67, 75 px for
    the side words' tails, and 66, 75, 89 px for the chips. The back arrow at P clears the curve by 5.3, 3.0 and 0.6 px.
    Effects: falling pieces leave by the bottom edge as today; rising ones are unchanged; the idle games use the bar's own floor
    and keep 100 px from the corners, so only the letters' home places move.
  - **Mitigations the Orchestrator proposes:** text sits on the line (words, the menu word, the underline, the labels in pills);
    boxes stay at least 1 dp inside the edge, so the pill slims from 42 to about 32 dp, the notice rises 2 dp at Bigger, and
    the clip offer stays where it is; side items take their margin from the curve at their own lowest point (the bar's existing
    corner rule, fed the new height), so long side words shorten a little sooner and the chips step in 25 to 50 px; the back
    arrow nudges in 2 dp at Bigger. Still waiting on the owner's go.
- **The menu word moves left of the widest pill** (W's 536a3a7, merged for build #12). The owner judges it on the phone first:
  "ill see if it looks good first." If not, the owner's fallbacks: leave the word where it is and make the pill's label shorter
  ("Favourited", "alr a fav" or similar), or let the pill overlap the word, semi-transparent with a blur underneath. (Owner)
- **Never wait for the phone to be idle** (owner, 2026-11-13: "take my phone. never ever wait for my phone to be idle any
  more."). Installs and checks run at once; navigation and the TitanKeys-in-front check still stop them. (Owner)
  - **The Sym jump** goes to V, with checks that reproduce it.
- **Save gives a haptic at once** (owner: "the save button shd have a immediate haptic before the book closes"). (Owner) A light
  tick on the press, then the clap as the book shuts; every other menu button whose beat lands more than about 80 ms late gets the
  same tick (Orchestrator). W builds it.
- **Delete a word and Next keyboard become switches** (owner: "why is delte a word shift backsapce and next keyboard alt sym not
  toggelable?"). They were info rows with nothing to set. Both become switches in Shortcuts, on by default, back on with the
  resets (Orchestrator, by the vision: every behaviour adjustable). A builds them.
- **The bar isn't shorter; its words are smaller** (owner's question, 2026-11-13: "the bar by default is shorter than before and
  also the text are smaller?"). Measured on the phone's captures, the bar is 88 px (56 dp) on 11-09, on 11-12 and today. The
  words were 17 sp, which the phone's font size 2.0 made about 29 dp; since fixed sizes (11-12) they're 20 dp at Normal, about a
  third smaller. Settings > Look and feel > Size > Biggest (1.4) makes them 28 dp, close to before, and the bar keeps its height.
  The default stays Normal unless the owner says otherwise. (Orchestrator)
  - **Is Normal big enough?** (owner, 2026-11-13: "usually my text is pretty big… is that typical for other people?") Measured: the
    Titan 2 Elite's own density is 300; the owner set Display size to 250 and Font size to the largest, 2.0. On a stock phone
    (300, font 1.0), the bar's 20 dp words are 31 px, Android's 16 sp body text 30 px: Normal is standard-sized. On the owner's
    phone, other apps' body text is about 44 px (16 sp at 2.0), and Biggest makes the bar's words 44 px. About a quarter of Android
    users make text larger (Appt, Dutch users; iOS about a third, Q42). The largest setting is the far end. (Orchestrator)
  - Proposed, waiting on the owner: TitanKeys picks its starting Size from the phone's font size (under 1.15 Normal, up to 1.5
    Bigger, above Biggest) until the user picks one, so people with big text don't get small words the way the owner did.
- **The decoration with Sym open** (owner, 2026-11-13: "The background decor probably bugs visually when sym is open"). From the
  code: with a panel open, the keys drawing hides, so the decoration's lower band runs to the screen's bottom, behind the panel;
  the ∞ is centred in that band, so it jumps down, grows and is cut by the panel's top. Confirmed on the phone (build #10, the
  clown slide, Sym opened by the debug broadcast): the juggling balls vanish behind the panel, and the whole slide (title, field)
  drops 51 px (33 dp) as Sym opens and rises back as it closes, since the block is centred between two spacers and the keys'
  room changes. Proposed fix (Orchestrator, waiting on the owner's go): a panel opening moves nothing on the slide; the block
  keeps its place, and the decoration stops at the keyboard's top and keeps the keys drawing's place as its floor. For V.
- **The ∞ slide says ∞ outright: a symbol hunt, not a riddle** (owner, 2026-11-13: "we should be explicit with the symbol to
  find. Its a symbol hunt not a riddle. Ok use ur recommendation but also make it explicitly the ∞"). (Owner) This reverses the
  earlier "never names the symbol" rule. Built in 4c6fd0e:
  - title "Answer honestly." (Orchestrator's, approved), which still leaves the ∞ to the hunt;
  - the field line "So how long do I last in bed with your mother? ∞" (Orchestrator's, approved): a question, and it still starts
    with S for GO TOUCH GRASS NOW;
  - the hunt "Find ∞ and press its key. It's further in.", a wrong symbol "Not that one. Find ∞.", and closing Sym "Closed it.
    The ∞ is still in there." (Orchestrator's wording);
  - done stays "Sure you do.", and the fast and slow lines are unchanged;
  - the ∞ decoration stays: with ∞ named it no longer gives anything away, and it shows the shape to look for (Orchestrator,
    unapproved). The stopwatch idea is dropped.
- **A new playground line, from the owner's idea** (2026-11-13, 11:20: "if you are not dumb why did you buy a dumb thing thats
  because..."). (Owner) It's written as "if you are not dumb why did you buy a phone with a keyboard that is because you are dumb
  and proud of it". (Orchestrator's wording)
- **Delete 1/3's decoration: a flat e with a strike** (owner, 2026-11-13, 14:50: "the decoration e is good but like the style looks
  out of place with our design and also it shd be a strike through why is there a tape"). (Owner) V redraws it: a flat Inter
  Bold e with a pen strike drawn across it, in the Block strike's style, and brings any other non-flat decoration into line.
- **No status marks: the word's colour is the status** (owner, 2026-11-13, 14:45: "remove the statuses for the heart and book etc.
  keep it simple. favourited = orange. saved = white. neutral = grey. blocked = grey + striked through"). (Owner)
  - This replaces the marks inside the bar and the drifting heart. It also replaces the earlier rule that the word is always white.
  - Hover previews tint the word about 60% toward its next colour, and the effects' particles stay. (Orchestrator)
  - With Orange accents off, a favourite uses the warm accent-off colour, so it still differs from white. (Orchestrator)
- **The word menu slide teaches the ladder with TitanKeys** (owner, 2026-11-13, 14:30: "teach them the ladder system like only having
  titankeys as a option, maybe have it pre-highlighted, and then have them favorite TitanKeys"). (Owner) The Orchestrator's design,
  built by A:
  - the title "Make TitanKeys your favourite.";
  - TitanKeys alone on the bar, highlighted;
  - the steps: hold, I to save, I again to favourite;
  - chips for the wrong moves;
  - done: "Favourited. Good taste."
  - Off-track moves (owner, 14:40: "what about did not select the right word? or selected other stuff instead?"):
    - the bar is locked to TitanKeys on this slide;
    - text selected in the box snaps back: "Not the box. Hold TitanKeys on the bar.";
    - an empty side place: "The one in the middle. TitanKeys.";
    - typing or swiping is blocked: "Typing's off here. Hold TitanKeys.";
    - other keys in the menu: "I or O. Nothing else does anything here.";
    - closing halfway keeps the step: "Closed it. Hold TitanKeys again."

    The wording is the Orchestrator's.
  - The line starts with TitanKeys, keeping the T for the finale: "TitanKeys belongs in your favourites" (Orchestrator's wording).
    TitanKeys stays highlighted the whole time, in the box and on the bar (owner, 14:35: "naturally we can make it start with
    TitanKeys in the box. also keep it highlighted").
- **The guide says its word menu saves nothing** (owner, 2026-11-13, 14:25: "whatever the user does in the guide with the word menu is not saved right? we shd clearly says so"). (Owner) The menu slide's first instruction is now "Hold a word on the bar. Nothing here gets saved." (Orchestrator's wording, d200d58)
- **Status marks sit inside the bar, just above the word; Saved's book is filled** (owner, 2026-11-13, 14:20: "I find the heart and
  small book bad to be over the bar as a status. particles are fine. but statuses should be kept maybe just slightly on top of the
  word. also the book should be filled"). (Owner) Particles and effects still rise over the app. Marks are about 12 dp, about 3 dp
  above the letters: a filled book in white at about 60%, a filled heart in the accent at about 70%. (Orchestrator's numbers)
- **Not saved is a solid grey word** (owner, 2026-11-13, 14:15). The outline didn't read at any width (1.1 to 0.32 dp were shown
  on the phone: "all are pretty not visible"). Of three alternatives (dotted underline, grey word, lighter weight), the owner chose
  "grey b". (Owner)
  - Not saved is #99918A, Saved and Favourite are white, and Blocked is #5E5853 with a #99918A strike.
  - Hover previews move the word about 60% toward its next look, since grey can no longer mean "preview".
  - The effects turn the letters grey to white (Save) and back (Unsave).

  These details are the Orchestrator's. W builds it.
- **Analytics: in 3.0, the launch build** (owner, 2026-11-13, 14:05: "do it in 3.0? 3.0 is the build I would acc promote and launch"; before that, "theres no user now it's just me honestly"). (Owner) It must be ready at launch, since that's when other users arrive. Until then, the owner's raw log is the baseline.
  - With one user, the debug build's raw typing log and Record a bug already give richer data.
  - When a public release brings other users, revisit, under #55's regulated internet rules, with:
    - opt-in crash and ANR reports, never text;
    - daily on-device accuracy totals: kept, chosen and corrected rates, and autocorrect undos;
    - where people quit the guide, and which settings they change from the defaults.
  - Never third-party SDKs, per-event logs, text, app names or precise times. (Orchestrator)
- **"TL;DR Trust me bro."** replaces "Trust me bro." on the share screen, before the accept (owner, 2026-11-13, 13:35). (Owner)
- **Release audit** (A, claude/release-audit-2 81da968, docs/release-audit-2.1.md). The release APK is 115.9 MB (engine 2.0's next-word
  model and onnxruntime), with 6 permissions. Dev screens and adb hooks are gated, and GuideFrameActivity is now unexported in
  release. Seven new checks were added. The Orchestrator's calls on A's open points, unapproved:
  1. Drop Ink's InkLightSdkReceiver: an exported receiver behind an undefined "normal" permission. F does it.
  2. 116 MB is fine for GitHub and under Play's 200 MB limit. No change.
  3. Record a bug: the screen and the privacy policy say plainly that its key presses and word choices can show most of what
     was typed. F does it.
  4. The inert debug screens compiled into release stay as they are for 2.1. Moving them into android/debug is later.
  5. The four harmless debug-level log lines stay.
- **Release rule** (owner, 2026-11-13, 11:50): "my rule is i'll use a pre-release for 3 days and only after 3 days no bug will i push it
  for a real release." The build for it must have no dev stuff and be tested thoroughly. (Owner) A audits the release build for
  dev-only features (docs/release-audit-2.1.md, extending debug-only.test.ts). The Orchestrator tests the final build on the
  phone. Signing and publishing stay with the owner.
- **Settings shows a 1 px line under the title** (owner's report). Reproduced: while scrolling, the first row of the fade under the
  fixed title shows content at full strength (y = 268). It's queued for F.
- **Alt + Backspace deletes forward, behind a switch** (owner, 2026-11-13, 11:40): "make that a toggle. I think no one really uses alt
  backspace... as a pc user i'm used to control backspace. alt+backspace = forward delete too." (Owner)
  - The switch is "Alt + Backspace deletes forward", on by default. Off, Alt + Backspace goes to Android, which deletes the line.
  - Ctrl/fn + Backspace and held Shift + Backspace keep deleting a word.
  - Held Shift + Backspace deletes only one letter on build #5 (owner's report). F builds the switch and finds and fixes that bug.
  - Alt + Sym works only inside TitanKeys. Making it global would mean the accessibility service sees every key typed with
    other keyboards, so it stays inside TitanKeys. The owner had not known; Key Mapper can cover other keyboards. (Discussed)
- **Another dumb-phone line** (owner, 11:25: "many people are calling this phone a dumb phone. honestly it's split... some use it as
  a ultimate power house phone. some use it as a dumb phone so they scroll less"). (Owner) Written as "some of you bought this as a
  dumb phone to scroll less and some as a powerhouse and you are all still scrolling". (Orchestrator's wording)
- **Two resets merged** (F's f6f6d87; merge 67cdd95): Reset most things, and Reset literally everything.
- **Owner's screenshot of build #5** (2026-11-13, 10:38):
  - On the front page the real bar covers the drawn keyboard's bottom row. That was the "keyboard cut off by the bar" report. A
    fixes it so the drawing sits fully above the bar, on the guide's slides too.
  - The line to type along to stuttered ("your messages your messages") where two lines met. The Orchestrator fixed the shuffle
    (7049463), and a check covers 200 seeds.
  - The owner deleted the Sym $%= maps from Key Mapper. (Owner)
  - The owner tests the menu all at once on the next build. (Owner)
- **Two resets, named by the owner** (owner, 2026-11-13, 10:55): "Reset most things and reset literally everything. U could literally
  call it that in the app." (Owner)
  - "Reset most things" covers the settings only.
  - "Reset literally everything" is a fresh install: it also wipes words, snippets, shorthand, app lists, the clipboard,
    recordings, news state and Share swipes (back to off). Already-shared swipes are deleted separately on the Share swipes page.
    It confirms with "Wipe it all" and plays Gone. (Orchestrator's details)
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
  - The hunt's instruction is "Find the one that never ends.", and it never names ∞. Superseded: the slide now names ∞ (see
    "a symbol hunt, not a riddle" above).
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
- **TitanKeys signs with the NightKeys key** (owner, 2026-11-14: "We shouldn't be using the atlas key", "let's just use NightKeys signing key. It was a mistake for 2.0 to not use the same key as 1.2"). (Owner) Release and debug builds alike; 2.0.0 on GitHub re-signed with it; the key backed up on X:, D:, F: and G:.
- **Off Atlas** (owner, 2026-11-14: "We should be moving fully away from the atlas build now"). (Owner) Done: signing, remotes (origin = TitanKeys, atlas-upstream = Julian's), CONTRIBUTING and the debug docs, the WSL env file. After the pre-release (owner's "Okay"): the com.atlas.* packages, the X:\Atlas folder, the WSL distro's name.
- **One branch** (owner, 2026-11-14: "There should only be one branch left on the git now which is the master"). (Owner) main only on GitHub, plus gh-pages for the website; old branches kept as archive/ tags.
- **Everything in one place** (owner, 2026-11-14: "I want everything in one place"). (Owner) Dev tools live in the repo, not outside it, reversing Atlas's clean-repo habit.
- **3.0 backs up the whole app** (owner, 2026-11-14). (Owner) One export and import for everything, not just words and snippets.
- **The keyboard's "English (United Kingdom)" label changes later** (owner, 2026-11-14). (Owner) To something not tied to the UK; the Orchestrator proposes "English" now and one entry per language with 3.0.
- **The Orchestrator's words, approved** (owner, 2026-11-14: "Your word suggestions are good. Use yours"). (Owner) The paste slide's last line becomes "No, seriously. I mean it." in 3.0; the keyboard's language label becomes "English" after the pre-release, and one entry per language with 3.0.
- **2.1 ships as signed; three fixes wait for 3.0** (owner, 2026-11-14: "I was expecting that those fixes to be fixed by 3.0"). (Owner) The front page's stale Setup screen after leaving the guide, the guide's missing Sym and fn step, and its/it's settling at any punctuation with the other form in the bar.
- **Pre-release now, release is a flip** (owner, 2026-11-14: "We are now at pre release. Release is just a tag change."). (Owner) v2.1.0 is the GitHub pre-release on main with the NightKeys-signed APK; after three clean days it's marked the latest release, unchanged. The website publishes from main; main is the only branch.
- **Julian's agent rules are gone** (owner, 2026-11-14: "Yes that rule comes from jullian. Remove that rule."). (Owner) AGENTS.md no longer says never to open a pull request or issue, nor to report AI use; agents may file issues (the 3.0 notes are #65 to #86).
- **The new README is published** (owner, 2026-11-14: "Write the readme and post directly after ur review"). (Owner) The goal and every feature first, one short technical section; the full engine write-up in docs/engine.md, to become the research paper (docs/paper.md).
- **The README is rewritten in the playground's voice, and the research paper replaces docs/engine.md** (owner, 2026-11-14: "I hate the tone of it. Give it to whoever that writes the playground text"). (Owner) F wrote both; published at faadf88 after the Orchestrator's review. (Orchestrator, unapproved) The README makes no claim about Unihertz's own keyboard, only that keyboards made for glass ignore the touchpad and the corners.
- **All text writing goes to worker F** (owner, 2026-11-14: "Ok any future text writing give it to that worker. The playground text is very nicely written"). (Owner) README, release notes, in-app text, the guide, store listings, announcements and the paper's prose; others may supply facts and structure.
- **3.0: the guide must teach the Space gestures and the touchpad to someone who has never used the phone** (owner, 2026-11-14: "some users might be alien to some concept like even hovering and doing gestures on the touchpad. Make a issue for 3.0 to make it much more easy to be taught in the guide"). (Owner) Issue #90.
- **Version numbers** (owner, 2026-11-14): 2.1 is where things stabilise, the first polished, designed and complete state. A big version brings core new features, a small version minor adjustments and tweaks, and X.Y.1 quick fixes. 3.0 is the launch version, and everything in it is done before launch. (Owner)
- **Questions up front on every issue** (owner, 2026-11-14: "For every issue, write a comment for starting on questions and decisions that I have to answer before you can start and finish on your own unattended end to end. I'll ans straight on the issue itself."). (Owner) Each 3.0 issue gets a comment of questions, each with a default.


## 3.0: the owner's answers on the issues (2026-11-16)

The owner answered the questions on GitHub; anything skipped takes the issue's default. (Owner) unless marked.

- **2.1.0 is the full release** (owner: "2.1.0 becomes full release now because I've tested it already. dailydrivable with the bugs to be fixed at 3.0"). Marked latest on GitHub, #55 closed. Everything left is 3.0: "study now for those that require study. code for those that you can already start", proposed as one overnight unattended job.
- **#65 backup:** every default (word pairs in, clipboard out; a plain file; settings replaced, lists merged; Settings > Data gets Back up everything and Restore; no automatic backups).
- **#66 steering target:** the field shows only the slide's own line. "Steer" is being replaced (#90), so the target is the word in its place, about one word left of the cursor, picked by the Orchestrator once F's line exists ("the master chat will decide").
- **#67 the word menu's keys:** named "I/K" and "O/L", and Back "P/Backspace" (owner: "name it sth like I/K and O/L"), in the guide, the boring guide and the Haptics descriptions.
- **#68 hunts:** 10 s, 20 s, 30 s, the same for the ∞ and the clown.
- **#70 leaving the guide:** only the navigation bug ("It's just a navigation/UX bug rn. making me like i'm stuck unless I finish the guide"). Every way out lands on the playground once setup is done; no change to the end's lines and no share offer. Fixed together with #71.
- **#72 Sym and fn:** a step in the setup before the guide only, skippable with a warning; not on the front page. A slide that needs a skipped key says so in one line, with no button.
- **#73 its/it's:** every default, and were/we're is an offer only for now. Study whether the context model can change were/we're when it's very sure (owner: "investigate how we could possibly use the context model to change were/we're when we're highly certain").
- **#74 feedback:** email only, no GitHub row ("just send a email... no need git"). The crash offer, yes. The address titankeys@everyones-niche.com is set up. Public addresses switch to it (default).
- **#75 other phones:** the warning once, on first launch, in the guide's humour (F). The guide stays as it is.
- **#76 language label:** waits for the languages work (#56).
- **#77 hold default:** every default (Medium, 400 ms; untouched settings move too).
- **#78 Save and capitals:** to discuss in chat first.
- **#79 Accessibility disclosure:** every default; plain, no "Trust me bro".
- **#80 American first:** yes, but after languages, as a new language menu may replace the page.
- **#81 off Atlas:** every default (a clean rename; tell users in all four places; X:\TitanKeys; `titankeys-build`). The folder and WSL moves need a time with no session running.
- **#82 tools into the repo:** every default.
- **#83 joined swipes:** a study first: "do a deep dive in the logs to find out how we can join swipes more smartly and distinguish. discover more rules to join."
- **#84 hover after a press:** (a), the pill stays until the touchpad next reports, one rule for the pill and the word's colour.
- **#85 battery and leaks:** every default. The baseline day still needs the owner's phone off the charger.
- **#86 bar text size:** every default.
- **#87 contact names:** last in Quick setup; people already set up use Settings > Setup.
- **#89 long flick:** it was a normal flick, cursor at the end, nothing selected. No cap anywhere; "take as long as it takes", keeping today's speed-up, since any real action stops the dots.
- **#90 the guide for first-timers:** a refinement, not a rework. The instruction stays where it is. "Steer" goes from the guide, the chapter name and the drawings included. Clear from the first try, with no help that ramps up after misses. Timed help and Skip stay. A flat drawing of the Elite's keys and Space with a thumb replaces the ghost finger from the first try, the taps lighting 1, 2, held. No video. The rest of the issue's defaults stand, including "that's an X, not a Y" and one run with a first-time user before 3.0 ships.
- **#91 AUTO:** "if it is already in the dictionary, it shd have priority": a phone name never changes a word the dictionary has, and only adds words it doesn't. The base list already writes WhatsApp, YouTube, IKEA and ESPN with their capitals. A word you saved beats both.
- **#92, #94, #95:** "Not always happening, monitor." No work until they show again; then Record a bug, or the time.
- **#93:** a new swipe takes priority over the delete's dots.
- **#78: leave it** (owner, 2026-11-16). Capitals stick only on words the dictionary doesn't know; Favourite stays case-free. Closed as not planned.
- **#56: every default, generic for 3.0** (owner, 2026-11-16: "we ship generic for 3.0. as much languages as possible, focussing on making it work first. tranditional/simplified that's not linked to the input method cause it's interchangable"). (Owner) Languages ship without waiting for native speakers. Traditional or simplified is one switch for every Chinese input method. (Orchestrator, unapproved) Its default follows the phone's region, otherwise traditional.
- **The older issues are closed or moved** (owner, 2026-11-16: "close the solved issues"). Shipped in 2.1.0 and closed: #26, #52, #54, #59, #60. Done: #57. Moved to 3.0: #34, #36. The 2.1 milestone is closed.
- **The folder moves now** (owner, 2026-11-16, picked "Move it now"). The baseline day starts around 07:30 (owner: "let's assume just ard 730AM").
- **Plan first, then an explicit go** (owner, 2026-11-16: "make a plan and define the scope and wait for my explicit go ahead"). (Owner) The plan, the studies' scope, what needs clearing and what's blocked are in todo-3.0.md. Nothing starts before the go. (Orchestrator) 3.0 takes about three nights, not one; the code rename comes last on night 1, so the phone checks fit before 07:30.
- **Chinese variants, like English's** (owner, 2026-11-16: "allow me to choose variants, within the same engine, like how English has different varianats like british english etc now... within simplified like China simplified, and traditional like HK style, Taiwan style, etc, with its own tailoring and with its own dictionary"). (Owner) One engine, with variants you pick. (Orchestrator, to confirm) For 3.0: Simplified (mainland China), Traditional (Hong Kong), Traditional (Taiwan), with OpenCC per region. The default follows the phone's region, otherwise Hong Kong.
- **Internal milestones with gates** (owner, 2026-11-16: "we shd have some internal milestones for these tasks. and we confirm that one milestone passes before we start another"; "you shd make the micro decisions but write them down to be confirmed by me"). (Owner) M1, M2 and M3 are in todo-3.0.md, each with a gate. The Orchestrator's own calls are listed there for the owner to confirm.
- **Go for 3.0, unattended** (owner, 2026-11-16, 01:20: "Go. your goal is to finish as much issues as possible as long as they're not blocked by me, you make the micro decisions, you orchestrate and manage a team of opus 5.5 medium to tackle the issues, with you deciding the order etc."). (Owner) The Orchestrator passes the milestone gates itself and goes on. Its own calls are listed in todo-3.0.md for the owner to confirm. (Orchestrator) The go is taken as clearing what the plan listed: downloads of open-source code and data into WSL, and a test program on the phone. The folder move waits for the owner's next presence: it needs a message from the owner halfway, and workers inside X:\Atlas would break. The #83 study goes to a new worker in auto mode, not the old Swiping engine session, so it can't stall on a permission prompt overnight. Analytics is filed as #96, with questions.
- **Yale is the Chinese input method that matters most** (owner, 2026-11-16: "On my pc installed but if u can't find it just download it. Or maybe find alternatives or even develop our own. I just like the Yale input method personally"). The owner types with Weasel 0.16.1, schema `yale` (耶魯粵語拼音). Its schema and dictionary files were copied to WSL for the language study, without the user dictionary.
- **No default Chinese variant** (owner, 2026-11-16: "No default. User chooses"). The user picks Simplified (mainland China), Traditional (Hong Kong) or Traditional (Taiwan); nothing is chosen from the phone's region.
- **Bug reports and feedback through a form, to the server** (owner, 2026-11-16: "we should make it send the bug to the server easily with a form or something"; "so that the average Joe can submit and even label whats happening etc. Same with share feedback"). (Owner) Form first, email as the backup. The endpoint goes on the contributions Worker, and deploying it tonight is approved if the PC is already signed in to Cloudflare. Issue #97; #74's email flows become the backup.
- **The phone's fixed port** (owner, 2026-11-16, 01:30: "Set up fixed port"). adb tcpip 5555 is on again after a restart. It lasts until the next restart; making it permanent needs root. The phone stays awake on the charger (`stay_on_while_plugged_in` was already 3).
- **One line per slide, Enter for the next Sym page, swipes "on the keys"** (owner, 2026-11-16, 01:52, on phone screenshots of the triple tap and ∞ hunt slides: "this should also be one line only and here say enter. For these kind of overlays specify swipe on the keyboard"; a first-time user swiped on the screen instead of the keys). (Owner) Every slide's field shows only its own line. The ∞ hunt names Enter, where ↓ steps to the next page. Every overlay swipe instruction says it's on the keys. Sent to tk-guide and F for M1. The owner then made it precise: "Not just enter, more like swipe up on the keyboard or enter for the next page, and specific instructions within the different pages so the user will never be lost." (Owner) The line names both ways to the next page. On every slide that uses the Sym panel, emoji or the clipboard, the instruction changes with the open page: what to do there, the next page, going back, and leaving.
- **A pilot: can the context model decide top-row joins?** (owner, 2026-11-16 morning: "do a pilot investigation to see whether the context engine could predict whether the words should be joined or not, when they end on the top row and resume on the top row"). A study on the saved pairs, counts only, no app code. It goes with #83.
- **Close only what a test proves** (owner, 2026-11-16 morning: "for the simple fixes, just do a quick yes or no validation test, and then close it if you can prove that it's fixed without doubt"; then "So if it requires manual judgement or subjective judgements, do not close. but leave a comment on the current status."). An issue closes on a clear, objective yes. Anything that needs the owner's eyes, hands or taste stays open with a status comment.
- **The apostrophe fixes apply to swiped words too** (owner, 2026-11-16, on "Typed words only: a swiped were never changes": "why doesn't it apply to swipes? it should. same with other aprosophy rule."). The settle-later fix (its/it's and the others, and were/we're) will also check a swipe's own first pick, measured on the swipe logs first. A form the user picked stays. The bar's other-form offer stays typing only (owner, 2026-11-14).
- **正體中文, not 繁體中文** (owner, 2026-11-16: "For traditional chinese, call it 正體中文 instead of 繁體中文"). Anywhere TitanKeys names Traditional characters in Chinese; the variant switch's 香港繁體 becomes 香港正體.
- **Simulated touches, so simple hands checks stop needing the owner** (owner, 2026-11-16: "could you make it so that you could simulate touches so I dont have to do those type of simple manual tests anymore?"). adb's shell is in group `input`, so it can write raw events into the touchpad (`touchPad`) and keyboard (`TitanKey`) devices without root. tk-tools builds an injector and gesture generator in `tools/dev/touch/`. Judging feel and looks stays the owner's.
- **Languages stay in the app; download-on-demand is a future option only** (owner, 2026-11-16: "Let's hold requiring downloading them on demand a future option"). If the size ever passes Play's limit, that's when it comes back.
- **A context model for other languages, later** (owner, 2026-11-16: "I'm also thinking of doing sth similar to our LLM prediction engine in english to aid with autocorrect and suggestions in the future or maybe even in 3.0, but after the baseline stuff are done first then that's the refine. rn I still haven't seen a working product for the languages so I'll wait until that is done first until optimizing and refining."). Order: a working, phone-checked product for the languages first; optimizing and refining, the context model included, only after.
- **Phone tests paused, background ones only** (owner, 2026-11-16, about 11:00: "Pause the tests that interrupt me on the phone, keep only background ones"). Anything that opens a screen, sets the keyboard, creates an input device or sends a key waits until the owner frees the phone again. CPU-only shell runs with the screen untouched may go on.
- **The crash offer is bigger** (owner, 2026-11-16: "the crash notification is too small. make it bigger."). #74 reopened for it.
- **New engines can come from open source** (owner, 2026-11-16: "for the languages, if it needs a new engine, like Japanese, we could find open source engines like how we adapt rime for chinese, as a shortcut."). Languages the study left out because they need an engine (Japanese first) get a study of open-source engines that could be adapted the way Rime was, with licences, size and what it takes on Android.
- **Languages switch inside TitanKeys, never through Android's picker** (owner, 2026-11-16: "I do not want to use Android's native keyboard picker for holding sym, that's for switching with other keyboards like switching from titankeys to Gboard. I am expecting the switching to be done in app elegantly within the same keyboard. it does not neccesssarily has to be done right now. but after the basic testings are done this shd be done."). The held-Sym overlay is the way to switch languages; it comes right after the basic phone tests. Alt+Sym keeps opening Android's list for other keyboards (owner, below).
- **Alt+Sym is for other keyboards, and opening Android's list is intended** (owner, 2026-11-16: "alt sym (or shift sym? i forgot) is for switching to other keyboards.", then "opening the native list is fine and intended. we can keep that."). It's Alt+Sym (Settings: Hold Alt + Sym for the next keyboard). It stays as it is, Android's list included; the Orchestrator's call to skip TitanKeys' own languages is dropped. Switching TitanKeys' languages is the held Sym's job, inside the keyboard.
- **Downloads for the Japanese engine** (owner, 2026-11-16: "Yes please download whatever you need.", to the Orchestrator's question whether the build machine may download Bazel and Mozc's pinned dependencies). Into WSL `~/build/lang/ja/` only, from official sources, to build Mozc and measure its size and conversion quality before any app code.
- **#74's crash card confirmed** (owner, 2026-11-16: "74 confirmed. I like it.").
- **Languages: function first, then the look** (owner, 2026-11-16, on the held-Sym switcher, Settings > Input methods and Chinese characters: "it works in function so this might just be not done yet?", "the whole languages settings is functional but just pretty unintuitive rn. we could postpone it and wait until everything is functional before we nitpick those cosmetic stuff. just raising it rn for the future."). Logged for later: the switcher should hover like the overlays or the word menu's pill, or show the full name at the bottom like the paste menu (the owner asked for a proposal); Settings > Input methods reorders on selection; Chinese characters might belong inside Input methods.
- **Yale needs a way to pick any choice, Enter for the letters, and Chinese punctuation** (owner, 2026-11-16: "rn for yale, for usability, I can't really select the word besides the first one. what's the intended design mechanism? should we do like long press = select that word, and maybe long press p = expand the menu to a sym like menu for more choices, and like enter types the english out? ... also like chinese also types the punctuations differently. we shd do that. Or maybe instead of long pressing we could thinkg of swiping up as a gesture if that's accurate?"). The Orchestrator's proposal is in todo-3.0.md, waiting for the owner.
- **Chinese choices, the owner's answers so far** (2026-11-16, talking through the design; not final):
  - Picking: Space picks the first (typical); no marking first. "if we enable holding a key under a choice, we should disable syms for the whole chinese langauge, for better consistency."
  - P isn't a choice key. "to hold P to expand it, and basically like the emoji or sym page. many things can be exactly the same like backspace and enter for up and down pages. Hover etc. sym can remain to be X to close when that menu is open." Swiping up turns pages only in that bigger menu, never in the one-row bar.
  - The row: "we're assuming that one block = one line ... it's not very practical when there's more than one word. we need to optimize on the scale. Like for example with diu nei lou mou four words, We could have 屌你老母 in 2 column width or more instead." The code over Q and W wastes space: "q can be used for sth maybe cause the selection keys should be wertyuio", and "there should be an indication when a language method is being selected on the bar."
  - Enter types the code as letters; Backspace takes the last code letter: "yes thats the typical behavior."
  - Punctuation: "it shd default to those chinese punctuations".
  - Other languages: "I dont want to think of how other languages work yet. I want you to suggest me cause I dont know how those languages work."
- **Chinese choices, round 2** (owner, 2026-11-16; still discussing, not approved): choices take the width they need ("Yes I agree"). Skeptical of the code moving into the text field: "that might be confusing and a bit unintuitive. if we keep it on the bar what choices we have?" Q as the language mark: "I think that's a good one, like how shift and cap go right now, but I want you to brainstorm if there's better ideas." Alt keeps its symbols; Shift + the key under a choice instead, "cause we dont need uppercasing in Chinese". "I still think I would personally use flick up, if it's accurate." A swipe up to open the bigger menu would clash with the flick up to pick. Holding Sym is for choosing a language only; holding Q switches the input method within it ("we switch language then use different variants if needed"). With few languages in the switcher, spread them evenly, so 2 keys pick the same language. Other languages: "the similar selection methods? it should essentially be the same right? or shd we keep it simple so only pressing on screen? is there swipe input with those languages? shd we allow flick to select?"
- **#83's real worry** (owner, 2026-11-16): "it's more for a accidental join like when the first word ends at the top row and the second starts also at the top row ... say 'Elephant' and 'Ontario', if there's some mechanism or even the context LLM module deciding that it's better to treat as two words than one word."
- **Chinese choices, round 3** (owner, 2026-11-16): the code shows on a thin line floating just above the bar. No language name in the empty bar ("it should show the suggestions ... it shouldn't show useless info like that"), and no per-language tint ("keep it using the orange black theme"). English and Chinese typing stay separate: Shift + the key under a choice picks, but "shift itself shouldn't switch to english letter. if the user wants to type english he switches to the english keyboard", though typing an English word directly when it's clearly English and not a code is worth considering. The bigger menu opens by holding P only for now, "not even flicking from p"; a swipe up to open it is out, because actions told apart only by speed are hard to learn. The language switcher's places stay even: 2 languages 4/4, 3 languages 2/2/2 with the rest empty.
- **A check that Space types the bar's middle suggestion** (owner, 2026-11-16: "add a issue to confirm that when pressing space, the middle suggestion in the bar is indeed what's being typed out.").
- **Every language but Chinese is marked experimental** (owner, 2026-11-16: "we should remark all languages except for Chinese to be experimental in the app. do it elegantly and minimally tho."). English is the keyboard's own, so it isn't marked.
- **Android's keyboard list stays short** (owner, 2026-11-16: "there's a lot of different keyboards in the menu i dont think I shd see all of them. I thought I should be only seeing the other keyboard that I have which is gboard and then my titankeys which I select the individual langauges myself?"). The Orchestrator's call: each TitanKeys app shows one entry in Android's list, labelled with its current language; languages switch inside TitanKeys.
- **Chinese picking, the concept test** (owner, 2026-11-16: "let's do hold to pick and flick to pick as well. I want to test if it works intuitively in practice. we can remove it if it doesn't work well. no need to polish those two, it's a concept test."). So Chinese gets Space, a tap on the screen, Shift + the key under a choice, holding the key under a choice (hold for symbols off in Chinese) and a flick up from it. Swiping in languages other than English comes after ("Agreed with putting swiping other langauges than english next").
- **The rest of the Chinese proposal agreed** (owner, 2026-11-16: "agree with your other chinese suggestions and the rest of that message. please start implementing."): languages, then input methods within them, the Gboard way (Settings > Languages lists languages; each shows its input methods to tick and its own options, Chinese characters inside Chinese; holding Sym shows the languages evenly, and a second row shows the chosen language's input methods; no hold-Q); Serbian becomes one language with Cyrillic and Latin; the code on a thin line floating above the bar; choices as wide as they need over W to O; the language mark at Q; hold P for the bigger menu (Enter and Backspace page, hover, Sym as ✕); Enter types the code; full-width punctuation by default with a setting, digits plain; an English word offered only when the letters can't be a Chinese code, never first.
- **The phone cleaned of test kits** (owner, 2026-11-16: "yes turn it off. clean up the various test or temporary kits. leave only the main 2.1 keyboard and the stuff that you need for 3.0"). Done by the Orchestrator: TitanKeys Film turned off as a keyboard, then Film (0.12.8) and Showcase (0.3.3) uninstalled with their data kept (`pm uninstall -k`); the old ONNX test folder and the touch tools' logs removed from /data/local/tmp; the two test backups removed from Download. Kept: release TitanKeys 2.1, TitanKeys Debug, the Reference app (the recording tool for #83's session), the replay models in /data/local/tmp/tk-baseline and the touch injector. Left alone, not ours or unclear: Key Mapper's files, p.txt, pk.txt, x.mp4, the owner's raw logs and key maps in Download.
- **The overlay leak fix ships in 3.0, no 2.1.1** (owner, 2026-11-16: "leave the leak fix for 3.0.").
- **The Scroll assistant block under other keyboards** (owner, 2026-11-16: "if I switch to gboard or another keyboard, if possible, when scroll assistant is off, I should still be able to get scroll assistant blocked."). Read as: with TitanKeys' block on, the touchpad stays from scrolling apps outside fields even while another keyboard is in use, held by TitanKeys' Accessibility service rather than the keyboard.
- **Atlas's layers** (owner, 2026-11-16: "If Atlas will block TitanKeys, we should probably write up a issue for julian, or straight up block Atlas's layers if possible?"). First check Atlas's latest release for the same leak; draft an issue for Julian for the owner to see before anything is posted; and keep TitanKeys' own layer above other keyboards' where Android allows. Removing another app's windows isn't possible.
- **TitanKeys keeps the touchpad through its Accessibility service, without Julian** (owner, 2026-11-16: "let's do it so that it's staying on top and always on with the accessibility service, make it clear in the accessibility service what features it enables or what it would break if accessibility is not on. most users will just turn it on anyways. I just want this to be working without Julian's involvement, especially like some people might be using a old version of Julian's keyboard."). While TitanKeys is the keyboard, its Accessibility window stays on top, so leftover layers from other keyboards (old Atlas versions included) can't take the touchpad. The Accessibility screens say what it enables and what stops working without it.
- **Answers to the open list** (owner, 2026-11-16 evening): were/we're's bigger test set: "yes" (Tatoeba's download). The Orchestrator's smaller calls: "lets' review it later." #81's folder move: "let's postpone #81." The #83 recording: "ues ready now." Chinese next-word suggestions: likely after 3.0, depending on whether it needs an LLM. The email in git history: "use our new email. the titankeys@everyones-niche.com one." (the exact rewrite to be confirmed before any force push).
- **#96 analytics** (owner, on the issue, 2026-11-16): "opt in. merged with share swipes to be the same thing, in one sharing toggle. no need to seperate different sharings. agreed with everything else. Investigate if there's more that helps our diagnostics or engine or tuning etc. for example, wrongly joined strokes, auto correct accuracy, etc." And in chat: "need more discussion on what more can we collect to aid our development while still being reasonable and not blocking development."
- **Build the other scripts** (owner, 2026-11-16: "starting with japanese hindi thai etc, yes go ahead."). Order per the engines study: Thai, Hindi, Japanese (Mozc), then right to left (Hebrew first), each validated by measurement on public text and marked experimental.
- **Chinese next words: a study first** (owner: "study whether a quick add on or a small chinese LLM is better. and whether adding a small chinese LLM is easy. Check if our previous english LLM lesson would help with the implementaiton.").
- **The history rewrite with the new email** (owner: "yes permission granted provided I won't loose any development status."): every ref rewritten together after a full backup, at a moment when no worker is mid-commit; titankeys@everyones-niche.com replaces the personal address everywhere, author emails included, and is the address for future commits.
- **#96: keep all the proposed counts** (owner: "keep all of those."), and the consent question: "could we possibly keep it vague but still explicity say what we wont collect and touch, and then we collect the data whenever is needed so that we have data for what we need for the future so we're not bounded? does that work or do we need them to agree every single time we chagne the data collection schema?"
- **#96, consent to an envelope** (owner, 2026-11-16: "for #96 yes please draft one. keep it easy for us. And make sure that it's both legal and google play safe etc."). One opt-in sharing toggle (with Share swipes) consenting to stated kinds of data and a purpose, with a clear never-list; new counts inside that envelope need no new consent, and the app lists the current counts. tk-data drafts `docs/analytics.md`, the policy and Data safety changes, checked against GDPR and Google Play's rules, for the owner to review before any code.
- **Faster phone testing** (owner, 2026-11-16 evening: "yeah let's do your speed up stuff. no need to switch around. I can rtype with debug honestly. priotize bigger slots for overnight but if it needs to be done otherwise it'll be blocking then it's also ok."). Phone checks run as one unattended batch per slot, reported once; TitanKeys Debug may stay the keyboard for the whole slot (no switching back after each check); big slots go overnight, daytime slots only when something is blocked on them. The safety gates (Debug in front before keys, never over finance or maps apps, no secure-setting edits) stay.
- **Bug: swiping types under the language switcher** (owner, 2026-11-16: "when in the language or input selection menu when its eng, swipe animation and swipe typing still triggers"). While any picking overlay is open (the switcher, its input-method row, Chinese's bigger menu, the accent picker), swipes neither draw nor type. With tk-languages, for tonight's build.
- **The owner's hands checks, 2026-11-16 evening:** "66 works with the stering distance. 68 passes. 84 passes." and "93passes." (all four closed). "89 fails. I don't see dots at the end of a swiping long text. Lets cap it when it's a long text it deletes for 10s. We show a hardcoded animation with the flick, but with a pacman animation variation where the dots are being eaten by the pacman." The Orchestrator's call: an original chomper in TitanKeys' orange, not Pac-Man's look or name, to stay clear of Bandai Namco's trademark.
- **Guide notes from the same run:** "for double tap space it shd ban triple space. Swipe do not allow typing at all. Backspace the e in the text box doesn't need to dissapear. The space gestures, I think 2 and hold or 3 and hold shd be the same box, maybe even like tap-hold, and when I'm too slow it shd tell me. The animations shd reflect the speed for this to be triggered. And then tell me to slid left or right depending on where the word is when the cursor mode or select mode is triggered. I don't even know if the hint detected properly. Keep the touch zone after its finished. Remove the full screen popup nerd for the boring guide." And "6， 唔要" (to be confirmed).
- **The Chinese picking concept test** (owner, 2026-11-16, Cantonese: "6唔要hold key 揀字flick not working. sym not reliable." — no hold-a-key to pick, the flick doesn't work, Sym isn't reliable). Hold-to-pick comes out and hold for symbols comes back in Chinese; the flick gets fixed for a real finger; Sym's stuck-after-switcher bug is already fixed on main.
- **Chinese, made usable** (owner, 2026-11-16 night): "Shift select not working but honestly bad idea. Abandon it. With not going for long press p is now a bad idea too. Abandon p too." On the Orchestrator's proposal (Shift opens the bigger menu, a tap on ⋯ too): "I did not remap the bottom right shift key. Shd we remap it or not needed? And so we shd remove the hover animation too for the short bar. the sym is blocked when I have some key code typed. The enter key sends instead of switches page in the menu. The Yale Chinese indicator is not showing in the suggestion. Let's also start building the suggestion when there's nothing typed. Engine itself I think is good at this point, we just need to make it more usable. Languages shd be first in settings. is the loading for the engine on load avoidable?" Built into TitanKeys, no remap: Shift opens and closes the bigger menu in Chinese, and a tap on ⋯ too.
- **Two languages toggle on a held Sym; no "it is" line** (owner, 2026-11-16 night: "if there's only two languages, holding sym shd switch between them by itself without having to select from the above." and "I don't like the XX it is wording, shd we just remove it or what?"). With two languages on, a held Sym switches straight to the other; the line after a switch is removed, as the mark at Q shows the language.
- **Sym opens the Chinese menu while a code is typed; the code line joins the bar** (owner, 2026-11-16 night: "Let's use sym and not shift. but only when a code is being typed. The reasoning being that the emojis and symbols shold not be typed anyways when there's still code. the sym key should highlight the trigger menu even if it doesn't align, even tho I think not like the hover type of highlight but maybe more like just make it orange." and on the code line: "I think 1 would work for the visibility. alternatively, make the current small overlay background outter glow in orange. but still black as bg."). The "more" cue at the bar's end turns orange while a code is pending; the code becomes a tab joined to the bar, with renders of a plain and an orange-glow version for the owner to pick. The mark at Q shows for every non-English input method.
- **The code line floats on its own, with an orange glow** (owner, 2026-11-16 night: "for the glow, I think isolated would work better than a joined tab."). Black background, soft orange outer glow, full-brightness text at the bar's size.
- **Owner's answers, 2026-11-16 late:** Chinese gets 倉頡 and 速成 ("we should also add 倉頡 and 速成"); the Orchestrator adds 注音 for Taiwan and skips 九方 (proprietary). Tests: "build a script to test all at once and wait for results instead" (the batches). #90: "I wont have anyone new to the titan. I'll test it myself." #72: "just test automatically if it's set up properly" (closed). #58: "let's abandon it. we got playstore anyways." (closed; the privacy page stays for Play). #81's folder move: postponed until after 3.0 (it would break every session and tonight's run). The language mark: "I want to confirm like do we have space for them, like is there modifier shifts etc for those? what would be interaction? can we think this part out first?" (C drafts a layout note first). #98: "space shd indeed type what's in the middle ... what I see shd be what i type ... we're not changing how auto correct works. we're changing the bar so that space one writes what it will acc type."
- **The language mark, decided** (owner, 2026-11-17, on C's design note): "provided that auto capping is a thing in russian. like in chinese, if shift does nth, shift shd not show up in the first place." (no Shift indicator where Shift does nothing); on the sentence-start look, the Orchestrator's recommendation (the mark big, the automatic Shift arrow small and muted above it); "4. yes but switches input method not languages." (a tap on the mark switches the input method within the language).
- **#83: build the sweet spot** (owner, 2026-11-17: "if it's at no loss but have gain ofc we go with the gain. I'm however asking if we can do a balance like a little bit of loss but more gain? is there a sweet spot?"). The 1% setting of the downward-speed veto is the sweet spot (the most fewer mistakes per 1,000 swipes; 2% and 5% gain less). Glued two-word pairs in the recording go from 15.6% to 9.1%; broken words rejoined stay at 79.6% there, and 99.1% of the logs' right joins stay joined. And the modifier look: "ok let's use your suggestion for the modifier." (the mark big, Shift's arrow small above it).
- **Owner's answers, 2026-11-17 night:** the code line is the tab ("do the second for the tab"); Chinese suggestions keep every word pair ("3.6mb is nth") and a better Cantonese dictionary is to be found ("the default Rime dictionary is actually pretty shit"); Shift, not Sym, opens the Chinese menu after all ("I see how sym isn't a good fit now. use the original shift as the plan instead. No hover effect needed for the shift because it's at the blind spot."); the guide's swipe slide ignores every key ("pressing keys shd do nothing, rn it types. including not allowing deletes") and the first delete slide keeps its "e" ("I dont want it vanished"); #96 is built with a short screen and a separate full policy ("the user main screen shd be still be simple and straight to the point"), no migration for existing sharers ("there's no real users"), one all-or-nothing toggle, and no lawyer ("this is a hobbyiest project"). On the Orchestrator's list of smaller calls: "too hard to review ... i'll just check the result directly and judge there."
- **Gestures in every language; the bar never empty** (owner, 2026-11-17, on the Orchestrator's finding that the gestures assume spaces and "."): "I think your suggestions for the gestures are good. I think a flick should also delete the entire key code too. and each language should have a list of starting words, I dont like the bar being empty at all times unless it's like password or whatever. if not hard then it should be learning from the user too." A flick takes one word by the language's own word breaks (Chinese, Japanese, Thai); a flick with a code pending clears the whole code; double space types the language's own full stop (。 in Chinese and Japanese, । in Hindi, none in Thai); three Space taps pick one word. D builds it. Every language gets starter words and next or common words on the bar, empty only in fields without words such as passwords, and learns from the user per language if that's about half a day or less. C builds it.
- **The switcher centred; haptics only on a change; screen taps felt** (owner, 2026-11-17: "minor touch. in the language selection and the input method selection, could it be always be centered mathematically? and can we make the haptic for the different tiles like that vibrate only when switching from one to another, not every key, reference to how we did the clipboard. also btw we shd add haptics to the overlays if they're pressed on the screen directly. if they're pressed on the keyboard we can ignore the haptics."). The language row and the input-method row are centred exactly; the tile tick plays only when the highlight moves, as in the clipboard panel; a finger tap on an overlay plays a haptic, a key press plays none beyond that. C builds it after the two-languages merge.
- **Four languages at most; tap haptics everywhere** (owner, 2026-11-17). On centring the switcher, with tiles of whole keys within W–O (5 and 7 can't be centred): "How about we limit language count to only 4? Who needs more than 4 honestly?" Input methods too: "Yea apply to input methods too. Don't allow selecting more than 4." Settings won't turn on a fifth language or a fifth input method in a language; both switcher rows are centred within W–O (3 over E–I). Then: "For tapping gives haptics, shd be globally, including the normal paste menus or like sym menus etc. Globally." Every finger tap on anything the keyboard draws plays a haptic, through one shared touch path; the keyboard's own keys play nothing extra. C builds both.
- **Bug: a held letter without accents repeats** (owner, 2026-11-17: "For languages with hold for accent, if the key doesnt have an accent, it still shdnt spam the key. It shd just type one and do nth else."). In an input method with accents, a held letter that has none types once and its repeats do nothing. H fixes it.
- **Accents never over Q or P** (owner, 2026-11-17: "For the accents, when opening the accent menu, avoid opening on q or p's column"). The accent tiles stay within W–O, as the switcher's do, at most eight. H builds it with the no-repeat fix.
- **Corrections and next words in every language; the mark for a lone input method** (owner, 2026-11-17: "If only one input method is in one language, the q sign shd show the language icon instead. Yes I agree. Its ok to be bad but conservative. It it shd still be present. Future contributors would potentially improve that. Don't worry about size yet. That's kinda easily solvable. Basic Correction(at least in suggestions) and suggestions shd be built."). Every language with a word list gets conservative corrections of the word being typed, at least on the bar, from its own list and key neighbours; and next-word suggestions from word-pair counts (Leipzig's CC BY corpora first, or another source with a clear licence), size not a concern for now. A language with one input method shows the language's mark at Q, not the input method's.
- **Bug: the first key skips the Chinese engine with no keyboard shown** (owner, 2026-11-17: "if I selected the textbox but s+mehow did not have the keyboard on, like with cantonese, it'll type the first character out without putting it as a keycode"). In an engine input method the first key always goes to the engine, held until it's ready if need be, whether or not the input view shows. H fixes it.
- **The glow stays, not the tab** (owner, 2026-11-17, on the main-mem build: "I think the glow acc looks nice so we can keep that in place of linked tab."). The code line stays a floating line with the orange glow; the earlier "do the second for the tab" is withdrawn.
- **Yale: j/z and n/l interchangeable** (owner, 2026-11-17: "For yale specifically, make j and z, n and l interchangeable."). Fuzzy initials both ways in the Yale schema only; finals unchanged. B builds it.
- **Finger taps pick in every menu** (owner, 2026-11-17: "allow touch screen for sym menus, which also includes language selection menus like expanded Chinese choices"). The Sym panel, the switcher and its input-method row, the accent picker and Chinese's expanded menu all pick on a touchscreen tap, as the key under the tile would, with the tap haptic. C builds it in the shared touch path.
