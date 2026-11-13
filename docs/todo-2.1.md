# TitanKeys 2.1: to-do list

What's done, what's in progress, what's waiting and on whom. The orchestrating session keeps this up to date, checked against
test-2.1h's history and the workers' branches, not from memory. Decisions and their reasons are in
[owner-decisions-2.1.md](owner-decisions-2.1.md). Last checked: 2026-11-12, 21:30.

## On the phone now (build 3968470a, test-2.1h at f68044d)

- Gestures:
  - The held delete: corner glow, pause zone, zone ticks, a firm hit per word, and a thin caret that blinks only when paused.
  - The double-space caret is solid.
  - Triple-space select has a handle.
  - No against Wall.
  - A flick never leaves two spaces.
  - The flick dots queue up.
- Space gestures: the first tap waits 150 ms, and double and triple taps count only on Space's own width (A).
- Haptics:
  - Round 7: quick settings and Record a bug as Lock/Release, and the exact-typing lab switch.
  - The quiet swell for changes that happen by themselves (A).
- Word menu v3.1 (D):
  - one ladder for every word, with Unfavourite back to Saved and no snaps;
  - orange particles on both sides, a heart burst, a crack and a ribbon;
  - the ambient heart and ribbon.
- Guide v5.3 (C).
- Settings (F):
  - One Show the bar setting.
  - One name for the Space taps setting.
  - Touchpad, Haptics and Shortcuts sections.
  - "Scroll assistant" rename.
  - Setup checks the phone's own settings (Sym/fn, scroll mode).
- Typing (D):
  - 37 more shorthands, each switchable, plus your own.
  - Snippets start with "/".
  - Canadian spelling.
  - The autocorrect controls page.
  - Apostrophe fixes per the study.
- Shift + Backspace deletes a word, and Alt + Sym switches keyboard, built into TitanKeys (F).
- Calibration:
  - One-pixel centred lines.
  - The owner's calibration (107.5 px, centred) is the default for the Sym/emoji panels, clipboard, word menu buttons and hover marks.
- Earlier fixes: the keyboard crash on open (bar init order), and a lone "i" capitalised.

## In progress

- Nothing building. The workers are idle until the owner's review.

## Waiting on the owner

- **Review of the current build**, then one batch for the whole app (owner's call):
  - Guide and playground keyboard: same width, 60% height, no gap between keys, 10.5 px between rows.
  - The guide crash with the font size at 2.0: the fix is ready on claude/guide-font-crash.
  - The app's scaling at large font sizes.
  - Playground words unreadable when the screen is fully orange.
  - Question: should the Sym panel's rows also take the keys' shape? (asked, no answer yet)
- Feel and judge on the phone:
  - Round 7 haptics and the quiet swell.
  - Exact-typing bar texture against confirm (lab switch).
  - Word menu v3.1.
- Key Mapper: import key_maps_titankeys_v2.zip (in Downloads) or switch the combos off by hand.
- Launch decisions: whether 2.1 goes out as a public beta, and the language levels (see the decisions log, Open).

## Waiting on the phone being free (the Orchestrator does these)

- Measure the Sym panel's columns against the calibration lines from a screenshot, from Q to Sym.
- Sym cycling: record the bar dropping to zero height for a moment, frame by frame, and find the cause.
- Scroll assistant: install the hold-trace build (claude/hold-trace, ~/tk-install/hold-trace), capture the hold decisions while
  switching apps, then bisect the old builds. The owner's hands are needed only to check key scrolling on each one.

## Blocked

- Scroll assistant fix: blocked on the trace and the bisect above. A fixes it once the last good and first bad builds are known.
- Google Play: FUTO's licence must be settled first. Then 12 testers for 14 days in closed testing.

## Small things

- C: the stale comment at GuideKeyboardView.kt:697 ("across C, V, B and N").

## Later (owner decided)

- Accented letters in Sym, voice input, a per-app modes page, bar size.
- Webpage (#58), trailer (#57), the Play build, the #54 wording session, the release check, the launch megathread.
- Languages are 3.0 (#56).
- Julian's Atlas 1.4.0, once released: backlight on touch, I'd context, hold-Alt shortcuts. Evaluate adapting them.
