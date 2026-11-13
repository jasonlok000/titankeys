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
- Bug: with that switch off, the keys still scroll Reddit (its full-screen viewer). A is investigating. (Owner report, 2026-11-12)
- No "Set it up for me" through Accessibility: Google Play's policy forbids it. (Owner's rule "only if it passes Google Play"; the Orchestrator checked the policy.)

- No: password and 2FA codes on the bar (autofill chips). No: back up everything in one file. No: changing the defaults to match stock and BlackBerry (Caps Lock on double Shift, hold for a capital, spelling from the phone's language). (Owner, 2026-11-12)

## Calibration

### Decided
- Calibrate by the **gaps between keys**, with every key the same width. Only the gaps are drawn, as bands that line up with the real gaps below the screen. Sliders for key width, gap width and shift, each with fine −/+ steps. This replaces the two-edge version, which was too hard to judge by eye. (Owner, 2026-11-12)
- Key calibration is for the overlays tied to keys: the Sym and emoji panels, the clipboard, the word menu buttons, and the guide's hints and trail. It has two sliders, Q's left edge and P's right edge, over the three letter rows. The defaults come from the phone's real geometry, and the owner fine-tunes it on the phone. (Owner, 2026-11-12)

## Process

- No AI co-author lines in commits. No pull requests; the owner opens them. No force push. (Owner)
- Phone tests are silent. Never force-stop TitanKeys. Don't take the phone while navigation is running. (Owner)
- Pause the workers near the usage limit and resume after the reset. (Owner, 2026-11-12)
- The flicker when a selection collapses comes from the Sefirah clipboard app, not TitanKeys. (Found 2026-11-12)
