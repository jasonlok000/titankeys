# TitanKeys 2.1: to-do list

What's done, what's in progress, what's waiting and on whom. The orchestrating session keeps this up to date, checked against
test-2.1h's history and the workers' branches, not from memory. Decisions and their reasons are in
[owner-decisions-2.1.md](owner-decisions-2.1.md). Last checked: 2026-11-13, 00:10.

## On the phone now (build 2ec5f751, test-2.1h at 0f88ab9: v2 guide, menu v4, fixed sizes, bar fix)

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
  - the ambient heart and ribbon;
  - Save bursts like Favourite, with small orange ribbons in place of hearts (owner, 2026-11-12).
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

## In progress: the unattended round (started 23:00)

The owner's go (22:55): "start working first... no mockups this round... do what you think is going to be 10/10... mark it as
your decision that I did not approve... I review those decisions with the final product... finish everything end to end. Run
through literally everything to confirm no bugs." The Orchestrator's design decisions are in [design-v2.md](design-v2.md), and
all of them are unapproved.

- Done by the Orchestrator: the playground text (162 lines in the owner's tone, no repeats until all are used, no "i am stupid"),
  commit ab2ae31. The guide checks pass.
- C: the guide and playground v2 (design-v2 §2–3):
  - the top bar, the content block, Skip and Next, orange only for actions;
  - the 60% keyboard, edge to edge, with no gap between keys;
  - the playground header, the WPM starting only on a real word, and readable orange.
- D: word menu v4 (§4):
  - places, and hover that stays after a press;
  - dictionary words count as Saved, and there's no Save for symbols or emoji;
  - the sparkle burst for Save, a quieter Unsave, and a better heart break.
- F: sizes that ignore the font size and display size (FixedScale), the Scroll assistant note and default, and the orange accents bug.
- A: the bar jump is **done**, claude/bar-bottom 52227c3, merged.
  - The keyboard window stays full height, and onComputeInsets reports only the keyboard's top, as the content insets and the touch region.
  - After: across 44 frames of opening and closing, the bar is never more than 26 px off the bottom edge. Before, it floated 195 px up.
  - Checks: taps above the keyboard reach the app, apps resize, panels and the bar work, and a hidden keyboard catches no touches.
  - Not testable over adb: keys scrolling a no-field app (needs the physical scroll), and the phone locked itself at 23:15 mid-test.
  - A's slip: one tap and one Back landed in the Claude app. Nothing was typed and nothing visible changed.
- F: **done and merged** (873b859): FixedScale in every window; Scroll assistant follows the phone's setting until changed, with no
  note under it; Orange accents. F's call: Orange accents covers the keyboard's marks over other apps, while TitanKeys' own screens
  keep the app's orange, and a note under the switch says so. Every keyboard colour goes through Look, with a check. Not yet seen on
  the phone.
- A (next): the slide decorations (design-v2 §7, one per slide, pure decoration) as a self-contained GuideDecor module, wired in
  after C's redesign merges.
- F (next): a review of every user-facing text: "good, not cringey, not ai sounding, keep my tone, keep the ones I chose" (owner, 23:40).
  F edits the Ink app, GuideSteps (incl. the playground lines beyond the owner's 22) and the release notes, and lists strings in files
  C and D are editing, for the Orchestrator to apply after their merges.
  Widened (owner, 23:55: "audit all text and copy for the tone with the same criteria too"): every user-facing word, including
  setup, quick settings, notifications, announcements, Record a bug, toasts, accessibility labels, res strings, the README, the
  webpage and the Play listing. F returns a before/after audit table for the owner.
- **Phone for overnight tests (owner, 23:50):** "Test it when u find my phone idle. It'll be charging and unlocked." Wake it with
  KEYCODE_WAKEUP. If it's locked anyway, never unlock it with credentials: stop the phone part and log what's left.
- A (after GuideDecor): the flick glow's strength ramps from none at the right edge to full at the top-speed line (owner, 23:58).
- C: **guide and playground v2 done and merged** (f3abe56). Renders checked by the Orchestrator: clean, designed, readable on orange.
  C's own 18 calls are in guide-v2-built.md in the notes folder. Among them: titles in Inter Bold (the app ships no ExtraBold),
  lesson keys stay steady orange, the leave pop-up offers Keep going / Start again / I already know everything, and the boring guide
  button moved under the end's buttons. All unapproved.
- D: **word menu v4 done and merged** (4dd9fed). Frames checked: Save's sparkle ring reads well next to Favourite's hearts.
  - Unsave breaks a bookmark, quieter than the heart (owner's call, given in D's session).
  - D's own calls: a dictionary word's O is Forget, with Undo; a word the store can't hold opens no menu; a forgotten dictionary
    word opens Not saved; the particles go behind the word; an O that disappears fades out.
- Combined build #1 (C, D, F and A's bar fix): **2ec5f751, all 35 checks passed, installed 00:45, no crash.** The phone run-through
  waits for the phone to be idle: the owner was in the Claude app. Meanwhile the swipe suite runs on the phone off screen.
- A: **slide decorations done and merged** (6a2be09): GuideDecor, a pure renderer, and GuideDecorView, the host. Checks cover
  keep-outs, shape count, opacity, seamless loops and frame time. Contact sheets are in the notes folder under guide-decor/.
  - A's own calls: the decorations live mostly in the band between the field and the keys; the typewriter e sits in that band,
    not behind the title; the starfield keeps four-point sparkles; the e is cream, all others orange at 7–12%.
- C (now): wiring the decorations into the v2 guide, with live keep-outs; then check them in the renders.
- A: **the flick glow ramp is done and merged** (66a6d7f). No glow at reach .05; a smoothstep up to full at the top-speed line (.6, from
  REPEAT_LONG_KEYS); a late glow uses the ramp at the lift; a hold takes over with no jump. Checks are in gesture-display and delete-hold.
  A's call: the ramp follows the glow's shown reach, so strength and reach move together.
- F: **text audit done and merged** (86a0412). The table for the owner is text-audit-2.1.md in the notes folder.
  - The owner's lines are kept; three were touched lightly ("Both. Eh." exact, the plagiarist and clown roasts tightened).
  - Still to apply after C's wiring merges: two GuideActivity strings, GOING_BACK → "Missed something? Go on then." and
    BACK_FROM_END → "Miss us already? That's a bit sad."
  - Fix in the final release-notes pass: "bookmarks for a save" is out of date; Save bursts sparkles now (D's v4).
- Swipe suite on the phone (build 2ec5f751): **passed** (saved-word swipes, context swipes).
- **Plan for the rest of the night (Orchestrator):**
  1. Merge C's decoration wiring.
  2. Apply F's two GuideActivity strings, and fix the release-notes Save line (sparkles, not bookmarks).
  3. Build #2, then install once the phone is idle (scratchpad idle.sh; wait-idle.sh is watching).
  4. A does the full phone run-through and reports a concise issue list, to keep screenshots out of the Orchestrator's context:
     - run1.sh: playground, panels, word menu, Settings at font 2.0;
     - a walk through every guide slide;
     - phone-suite.sh, which skips feedback.sh because it force-stops;
     - judged against design-v2.
  5. The Orchestrator decides the fixes, the workers make them, then the final build and install.
  6. Last: update this file and the decisions log for the owner's morning review, with the release notes ready. The signed
     release build and publishing stay with the owner.
- Then: merge everything, one build, install, and a full run-through on the phone.
- Release prep (Orchestrator):
  - Update release-notes.txt for everything new since it was written. One line is already wrong: steering taps now count
    only on Space's width, not "anywhere on the C V B N row". New since then: the gestures rounds, Space at 150 ms, the word
    menu, Shift + Backspace and Alt + Sym, the quiet swell, Canadian spelling, fixed sizes, guide v2, the playground text and the
    Scroll assistant default.
  - The signed release build (tools/release.sh needs the owner's signing credentials) and publishing stay with the owner.

## Next, waiting on the owner's go (proposed order, 22:50)

1. **Guide and app redesign (the batch).** The owner rates the guide 5/10 visually ("feels like there's no design").
   - The Orchestrator mocks a v2 on the quick page with the real text and elements.
   - The owner approves it.
   - Then one batch: the redesign, the 60% keyboard (no gap between keys, 10.5 px between rows), the same sizes at any display or
     font size, the field always 2 lines, readable orange on the playground, the playground keyboard no longer clipped at its edges,
     and the new playground text.
2. **Word menu (D):**
   - Back on Backspace's centre, and hover that stays after a press, as on the Sym panel.
   - A floppy icon for Save, with a new effect that lands. A mock comes first.
   - A better heart break.
   - Save hidden for symbols and emoji.
   - Dictionary words count as Saved, and going down from Saved takes the word out of the dictionary.
3. **Playground text (Orchestrator):** the full ~3,000 words in the approved tone, without the touchpad line and without "i am stupid".
4. **Scroll assistant (F):** remove the note under the switch. The default follows the phone's own Scroll assistant.
5. **Orange accents setting (F):** it doesn't work everywhere. Find where it's ignored.
6. **Sym cycling (Orchestrator, on the phone):** record the bar dropping to zero height, frame by frame, and find the cause.

## Waiting on the owner

- Their go on the order above, or changes to it.
- Feel and judge on the phone: round 7 haptics, the quiet swell, and the exact-typing lab switch (texture against confirm).
- Key Mapper: delete the three switched-off Sym + S, P and E maps by hand. key_maps_titankeys_v3.zip in Downloads is the clean backup.
- Launch: whether to launch at 3.0 with languages instead of 2.1.

## Scroll assistant (traced 21:53)

- In every app without a text field, TitanKeys read its own setting as touchpadOnlyInFields=false, which means **Scroll
  assistant on**: the keys scroll apps outside text fields. So it never wanted the block, and hid as designed.
- Every exported typing log since 11-09 has the same value, false. So the setting has been "on" all along, and no code changed
  that. The phone's own props are the same as when first read: scroll_assistant=1, touchpad_forcescroll=yes.
- **Resolved (owner, 22:45):** the switch was on, and the owner had read it the other way round. No code bug. Follow-ups are in
  item 4 above.
- Also on the phone, with Accessibility on: TitanHold (com.jasonlok.titanhold HoldService) and DisplayExtend's touchpad service.
  Either could affect scrolling.

## Phone checks (the Orchestrator may take the phone any time; owner, 22:45)

- Measured 22:42: the Sym panel's columns are on the calibration, within 2 px from Q's column (54 px) to Backspace's
  (1,022 px). What looks off is the playground's and the guide's drawn keyboard: it's clipped at both sides by the page margin
  (batch item).
- Sym cycling: record the bar dropping to zero height for a moment, frame by frame, and find the cause.

## Blocked

- Google Play: FUTO's licence must be settled first. Then 12 testers for 14 days in closed testing.

## Small things

- C: the stale comment at GuideKeyboardView.kt:697 ("across C, V, B and N").

## Later (owner decided)

- Accented letters in Sym, voice input, a per-app modes page, bar size.
- Webpage (#58), trailer (#57), the Play build, the #54 wording session, the release check, the launch megathread.
- Languages are 3.0 (#56).
- Julian's Atlas 1.4.0, once released: backlight on touch, I'd context, hold-Alt shortcuts. Evaluate adapting them.
