# TitanKeys 2.1: to-do list

What's done, what's in progress, what's waiting and on whom. The orchestrating session keeps this up to date, checked against
test-2.1h's history and the workers' branches, not from memory. Decisions and their reasons are in
[owner-decisions-2.1.md](owner-decisions-2.1.md). Last checked: 2026-11-12, 22:10.

## On the phone now (build 26dabd27, test-2.1h at a7cb9b9)

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
- The guide opens at font size 2.0: a title or instruction that can't shrink keeps its largest size (brought in ahead of the batch).
- Earlier fixes: the keyboard crash on open (bar init order), and a lone "i" capitalised.

## In progress

- D: Save gets a particle burst like Favourite's heart burst, with orange bookmark shapes on both sides (owner: Favourite "done perfectly", Save "not done nicely"). Only Save changes.
- Layout sample page: on the phone at http://localhost:8765/sample.html, served from the PC through adb reverse. It's scratchpad
  cal-tool/sample.html, with the Guide and Playground screens, a Sizes button and Full screen. **Waiting for the owner's verdict** before the batch.
- Playground tone sample: 22 lines in docs/playground-text-draft.md. **Waiting for the owner's verdict**, then the full ~3,000 words.

## Waiting on the owner

- **Review of the current build**, then one batch for the whole app (owner's call):
  - Guide and playground keyboard: same width, 60% height, no gap between keys, 10.5 px between rows.
  - Scaling: keep every size the same whatever the display size (DPI) or font size setting (owner, 21:45). The owner says the
    scaling in the current build "is better" (21:40), but the rule covers every setting.
  - Playground words unreadable when the screen is fully orange.
  - Guide: the text field must always show **two lines**. It's buggy now: the code asks for 3 and drops to 2 when tight.
  - Playground text: rewrite it in the owner's tone ("offensive fun casual"), lowercase with no punctuation, nothing off limits,
    and **long enough to type for a long time without looping**. Today it's 10 short, tame sentences that repeat.
  - The batch waits for the owner to approve the layout sample page.
- Feel and judge on the phone:
  - Round 7 haptics and the quiet swell.
  - Exact-typing bar texture against confirm (lab switch).
  - Word menu v3.1: Favourite approved ("done perfectly"). Save is being redone by D.
- Key Mapper: v2 is imported. Importing can't delete key maps, so the owner deletes the three switched-off Sym + S, P and E maps by hand
  (owner: "the $%= shd be removed"). key_maps_titankeys_v3.zip in Downloads is the clean backup without them.
- Launch: whether to launch at 3.0 with languages instead of 2.1 (the owner is leaning that way; see the decisions log).

## Scroll assistant (traced 21:53)

- In every app without a text field, TitanKeys read its own setting as touchpadOnlyInFields=false, which means **Scroll
  assistant on**: the keys scroll apps outside text fields. So it never wanted the block, and hid as designed.
- Every exported typing log since 11-09 has the same value, false. So the setting has been "on" all along, and no code changed
  that. The phone's own props are the same as when first read: scroll_assistant=1, touchpad_forcescroll=yes.
- **Owner to check:** Settings > Touchpad > Scroll assistant.
  - If it shows on, turn it off and the block should come back.
  - If it shows off while TitanKeys reads on, that's a display or save bug for A.
- Also on the phone, with Accessibility on: TitanHold (com.jasonlok.titanhold HoldService) and DisplayExtend's touchpad service.
  Either could affect scrolling.

## Waiting on the phone being free (the Orchestrator does these)

- Measure the Sym panel's columns against the calibration lines from a screenshot, from Q to Sym.
- Sym cycling: record the bar dropping to zero height for a moment, frame by frame, and find the cause.

## Blocked

- Scroll assistant: the trace ran at 21:53. The finding is in Waiting on the owner. No bisect until the owner has checked the switch.
- Google Play: FUTO's licence must be settled first. Then 12 testers for 14 days in closed testing.

## Small things

- C: the stale comment at GuideKeyboardView.kt:697 ("across C, V, B and N").

## Later (owner decided)

- Accented letters in Sym, voice input, a per-app modes page, bar size.
- Webpage (#58), trailer (#57), the Play build, the #54 wording session, the release check, the launch megathread.
- Languages are 3.0 (#56).
- Julian's Atlas 1.4.0, once released: backlight on touch, I'd context, hold-Alt shortcuts. Evaluate adapting them.
