# TitanKeys 2.1: to-do list

What's done, what's in progress, what's waiting and on whom. The orchestrating session keeps this up to date, checked against
test-2.1h's history and the workers' branches, not from memory. Decisions and their reasons are in
[owner-decisions-2.1.md](owner-decisions-2.1.md). Last checked: 2026-11-13, 07:35.

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
- D: **bigger bar text done and merged** (d89e536). Bar words at 20 dp, ranked by size, with the word menu scaled to match. A source check keeps bar and panel text at 13 dp or more. Nothing in it breaks an owner decision (D checked).
- A: **guide robustness done and merged** (a5f41ef):
  - Blocked, not ignored: every locked gesture shows its start, then the chip and No, once per attempt. That follows the
    owner's v5 rule "each try says it's blocked"; my brief's "once per slide" wasn't the owner's.
  - The flick slide's glow and crunch had never shown (they sat behind the Swipe lock). Fixed.
  - The failure matrix is in docs/guide-failures.md. Dead ends fixed: a screen tap moving the cursor, fn+C on the paste slide.
  - The help hint comes after 3 s and repeats every 6 s, with the demo replayed.
  - Missing Next: the morph's pulse cancelled Next's fade-in at alpha 0. Fixed, with a check per slide.
  - The ∞ title is "Add how long you last in bed.", with the owner's two lines pinned.
  - The paste slide's nice clip pulses gently until pasted.
  - The Space and flick refusals live in AtlasIme, so they need the phone pass.
- F: **app haptics and Ink sizes done and merged** (72b9d1b).
  - App controls follow the rulebook: switch on is Lock, off is Release, a stepper ticks, a refused action is No, and plain
    buttons stay quiet.
  - The boring guide says "touch zone".
  - F reports Ink's text was already above the size floor, so nothing was enlarged. Check that on the phone, since the owner
    found some elements too small.
- C: **decoration wiring and the readability batch done and merged** (7ff66cc, d49ea3d; merged on 2026-11-13, 06:30).
  - Decorations sit behind the content, with live keep-outs, and fade in 320 ms after a slide settles. They turn ink on orange.
    None show on the playground or under the leave pop-up.
  - Type scale 36/24/19/24/14, with bigger key hints and corner symbols. The reference text always shows two lines.
  - Share swipes is now a native screen over the end. "Trust me bro." sits right before the one orange accept. Not now and Back
    return to the end, and "Trust me bro." is gone from the end screen.
  - Finale: the real logo splits into its T and K, which walk into "TitanKeys".
  - Owner-log items restored: the text box's orange bar, the TOUCH ZONE brackets, the guide's own pops and 340 ms timings,
    and the bigger end ✕.
  - Orchestrator's review of the renders: clean and readable, with two things to look at:
    - **The TOUCH ZONE brackets strike through the Q, P and Shift labels.** The clip-out's key test never matches. Sent back to
      C (06:35): clip the real label bounds plus 4 dp, and lengthen the arms.
    - The finale's "TitanKeys" looks like DejaVu in the desktop render, likely the render's font fallback. Check it on the phone.
  - C's open questions, settled by the Orchestrator (unapproved): guide titles stay cream (v2, which the owner saw), and the
    playground keeps the 60% drawing (the owner's own v2 call, later than "full real-size").
- Orchestrator (06:40, 70f4f38): F's two guide lines applied; release notes: sparkles for a save, "way less orange", a line for
  the app's haptics, and one for the share screen.
- **Build #2: 150cd978** (test-2.1h at 70f4f38 plus docs), all checks passed. Installed with no crash, then A ran the full phone run-through.
- **A's run-through of build #2: no blocker, 12 ugly, 9 nits, no crash, no new ANR.** Notes: run-through-build2.md in the
  notes folder. Fixes assigned, all from 29c411a, none on the phone:
  - **D** (claude/playground-menu): **done and merged** (505e777): the playground has its own field mark. the playground used the guide's show-only word menu (its field carries the guide's tag), so
    "then" showed Not saved and Save did nothing real. It now gets the real menu. That likely explains the owner's "why are words
    like 'and' not even saved?".
  - **A** (claude/guide-polish): **done and merged** (1d0f715, merge 1f98259). New chips: Swipe "Spaces are blocked here. Swipes
    space themselves.", Steering "Typed spaces are blocked. Taps on Space steer.", ∞ "Find it and press its key. It's further in."
    The stray space was the Backspace slide's cursor, not the menu. On the phone: the light-tap Space refusal and steering.
    The fixes:
    - Typing 3/3: a bar tap gets "hold, don't tap";
    - the help is on time after a Skip;
    - a lone Space is refused on the Swipe and Steering slides, with a Space-appropriate chip on the Alt slide;
    - no stray space after Next with the menu open;
    - the ∞ instruction stops naming ∞.
  - **C** (claude/guide-visual-polish, with the brackets): **done and merged** (707f28f, aa021de; merge 7db5c06).
    - The brackets pass behind the labels with a 4 dp gap.
    - Keep-outs fade softly, with nothing under the status bar.
    - The share screen's answers are pinned at the bottom.
    - The paste selection is orange at 50%, matching the owner's solid orange triple-space selection.
    - Not in the renders: the share screen and the paste selection. Check both on the phone.
    - Follow-up sent: on black, the playground's next line had the same grey as the current line, so the two ran together.
      It's dimmed to half, with a wider gap.
- **Front page title and one-paragraph text: done and merged** (e7acb0d, df8a6ce; merge fc2c23c). Renders checked: the title is on one line, ink on orange, and the text scrolls as one paragraph. Build #4 started.
- **Build #4: 91b08c1c** (test-2.1h at bcaf215), all checks passed. Installed at 08:00 with no crash. This is the candidate for the owner's review.
- **Owner's notes on build #4 (08:05), in progress:**
  - **A** (claude/playground-haptics): **done and merged** (4dc5e00, merge c5b67c3). The five moments:
    - Celebrate at 40;
    - No at 100;
    - a run of 7 Texture ticks over about 350 ms at 150;
    - Hit on each lightning flash;
    - Change (the swell) at the plant.

    Each plays once until calm, and a jump plays only the highest. The "Playground effects" switch sits in Settings > Haptics. The feel and the hit landing on the flash still need the phone.
  - **F** (claude/size-setting): Settings > Look and feel > Size, with Normal, Bigger and Biggest, making everything bigger,
    the bar included.
  - **Orchestrator, done** (05fce6c): the boring guide's Scroll assistant rewritten (the phone hijacks the touchpad, the creator
    hates it, TitanKeys can block it), plus a new topic, "The local LLM". The boring guide checks pass.
  - **Orchestrator, done:** TitanKeys' Alt + Backspace word delete is gone. Alt + Backspace now goes to the app as Android's own
    combination. The boring guide's Delete a word is fixed. The modifiers and text checks pass.
  - **D** (claude/bar-even): **done and merged** (e56f3cc, merge 3baeb3e). All three words are 20 dp, with the best in Inter Bold
    and Medium dropped. Font padding is off and the words are lifted by Inter's x-height, so the letters sit −0.5 px from the bar's
    centre, against +3.3 px before. The render checked out.
  - **Fine calibration: done** (db4889b). The owner set 108 px, centred, and KeyAlignment and its checks follow. To re-measure the Sym
    panel over the lines on build #5: on build #4 the symbols leaned 0–3 px left (about 1 px on average) on both sides, which is
    glyph shape and rounding, not spacing.
  - Before: the grid is right, but "maybe the calibration guide a bit off". The owner is refining it on
    cal-tool/fine.html (full-height lines, quarter-pixel steps). When the owner is done, the saved values go into KeyAlignment's
    defaults, with a shift if there is one.
  - F's brief corrected: Size keeps the bar's height and grows only what's inside.
  - **Orchestrator, done:** the boring guide's new topic, "Space gestures: tap a bit higher".
  - **W, a new dedicated worker** (2.1 W: word menu ladder and hover fix, local_7148acab): Forget only on really learned words, not
    candidates; hover kept after a press, as in the quick settings; a check for the whole ladder.
  - **Release notes** (3122c02): Alt + Backspace (2.0 deleted a word, and now Android deletes the line), one-size bar words, and
    playground haptics. Size gets its line once F's branch merges.
  - **Owner's bug (08:50):** opening the menu on a selected word plays the heart burst, then the glitter. Sent to W: nothing bursts
    on open.
  - **Under discussion (08:50):** the owner finds Save's glitter generic, and Save could become another word if that's easier to
    animate. The Orchestrator proposed "Add" and "Remove" with a book: the word drops into a book that claps shut, and on Remove a
    page tears out. Waiting on the owner.
  - **Ladder review page (09:00):** cal-tool/ladder.html is open on the phone. It shows four rungs (Blocked, Not saved, Saved,
    Favourite), with learned words counting as Saved, Unsave for every word and a Block step. It has bar mockups for each status
    and its hover, plus Play mockups for each step's effect: Save as a book, Unsave as a torn page, Block as a ring and slash.
    W ships the hover and open-burst fixes first, and holds the state changes until the owner confirms.
  - Owner's report: on the home screen the keyboard is cut off by the bar. It isn't on screen now, so the owner's screenshot is
    needed.
  - F's Size setting is pushed (4af0690, verify passed). F kept the guide and front page at Normal. Review before merging.
  - **W's fixes, merged** (46977ef, fd01c83; merge 179b7ad):
    - **Hover:** the owner's raw log showed the touchpad's lift arriving about 124 ms after the key-up, and then nothing more. So
      the hover now pins to the pressed key and moves or ends only when the touchpad reports the finger again. The catch: after
      a real lift the pill stays until the next touch, since the touchpad reports nothing after a press.
    - **Open:** the echo uses the word's own mark, and the ambient waits a full cycle, so there are no hearts and then glitter.
    - **Next for W:** the quick settings page has the same late lift (its mark clears about 0.3 s after a toggle), so it gets the
      same fix. The ladder work stays held until v4 is approved.
  - **Merged for build #6 (12:05):**
    - **A's front bar fix** (2169552; merge e1bf15c). It wasn't an overlap: fitKeys dropped the bottom row when the page was about
      42 px short (the TOUCH ZONE label's 24 dp room, an empty actions row, and renders that assumed small bars). The label now sits
      in the existing gap, the actions row is gone, and 20 px are left to spare.
    - **F's branch** (merge 6ae0880): Alt + Backspace forward delete behind a switch; held Shift + Backspace reading Shift from the
      key itself; the Settings title line (the fade starts on the whole pixel, with a scroll-fade check); and A's two raw oranges
      routed through Accent.

    - **W's ladder v4** (b8046a1, a fast-forward):
      - four rungs, with Forget and Undo gone, and one-letter words, symbols and emoji opening no menu;
      - Unsave on a dictionary word goes to a new unsaved_words list: hidden from suggestions but learnable again;
      - Block takes over the autocorrect refusal;
      - And/AUTO: Save stored the word as shown, so WordCase.kept now drops accidental capitals, with a one-time repair (word_case).

      Follow-up sent: lift the heart preview off the bar's edge, and confirm the pill style is unchanged.

    - **C's finale** (4a61820; merge 3e236a1), then **V's decorations** (7571f2c; merge b7d2b57, taking V's GuideDecor.kt and
      GuideDecorRender.kt whole). guide-decor and guide pass on the merge.
    - **D's press haptics** (27bdc2e; merge adcd3c5): a Press tick for every plain press, routed through one place (AppPress, plus
      Ink), with docs/haptics-audit.md and a check.
  - **Build #6 (7570ac85) passed, and is on the phone (12:50).**
    - run1: no crash, no new ANR, and every page renders.
    - The run's word menu test unfavourited the owner's "what's" (it held the bar's middle word). The Orchestrator restored it to
      Favourite on the phone, and run1 now only opens and closes the menu.
  - **Build #7, the release candidate,** started from 098c159, after merging W's 2671bb0, A's audit (81da968), D's baseline
    (780dbe5) and F's release fixes (fed4129). debug-only passes on the merge.
  - **Build #7 (354c9b85) passed and is on the phone (12:20).** Verified by adb in the playground: held Shift + Backspace deletes a
    word, Ctrl + Backspace deletes a word, and Alt + Backspace deletes forward.
  - **A slip in the Orchestrator's guide walk:** the walk driver read the finale's orange as Next and tapped through the end screen,
    turning Share swipes ON. It was turned off at once on the phone and checked on the Share swipes page. Nothing was sent, since
    swipes are only collected while it's on and none were made. The driver now stops at any orange screen without tapping.
  - **Found on the phone:** guide titles showed in sentence case. setSingleLine wiped isAllCaps; this was never visible in the
    renders. Fixed in 9f15383, with the check guide-title-caps.test.ts.
  - **A's pre-release pass on build #8 is done:** 0 crashes, no new ANR, every setting restored, and Share swipes still off.
    - Titles are upper case, the four drawn rows show, Size and Orange off work, both reset confirms were cancelled, and the new
      Settings items are all there.
    - Fixes out:
      - A: the Backspace demo should take only the extra e, and the heart demo should select its target;
      - V: the clown valance's hard keep-out;
      - C: about 16 dp more under the end wordmark.
    - By design: the Saved word's faint book over the front page's drawn keys.
    - For the owner's hands: the touchpad (swipe, held flick and PAUSE height, steer, select), the real Sym key and held
      letters, haptic feel, pasting on the guide's slides, and Next on a really passed slide.
  - **Build #11 started** from e4ea287. It adds A's TitanKeys underline on the bar for the whole menu slide (fad05f1; the wiggle
    is gone) and W's light-look accents-off favourite #35312E (b619011; all four statuses checked in both looks). Not in it: the
    Sym jump and decoration fix, which waits on the owner.
  - **Merged for build #11, round 2 (176b921):** W's colour-only status (6694e8c: Favourite in the accent, Saved white, Not saved
    #99918A, Blocked #5E5853 with a strike, no marks; hover leans 60%; Strip.lit underlines a bar word), A's render fix for the
    passed menu slide (2dc6e18), and V's flat e with a pen strike (claude/flat-e-strike; paper balls and sticky notes flattened).
    Word-menu, guide, menu-render and guide-decor checks pass. Still to come before the build: A wires Strip.lit for TitanKeys
    through the open menu; W's light-look favourite colour with a four-status render. The Sym fix waits on the owner's go.
  - **Merged for build #11 (A):** the menu slide on TitanKeys (8db62f7: "TitanKeys belongs in your favourites", marked in the
    box, only the hold unlocked, a chip for every off-track move, lock chips all say "blocked") and the held flick's trail (8f31e44:
    HoldTrail keeps the flick's points and the hold's, so the guide's drawing shows the whole path and fades it at the lift). Guide
    and delete-hold checks pass on the merge. Open: the passed render drops TitanKeys' underline in the box (asked A); the bar's
    mark through the open menu waits on W's Strip.lit hook.
  - **W, accents off:** Favourite takes Accent's raised cream #E3D5CB in the dark look (warm(orange) matched Not saved's grey). The
    light look's value must sit as far from Saved's ink as cream does from white; W sends a four-status render. (Orchestrator)
  - **The ∞ slide names ∞ (4c6fd0e), for build #11:** "Answer honestly." over "So how long do I last in bed with your mother? ∞";
    the hunt "Find ∞ and press its key. It's further in."; done "Sure you do."; the ∞ decoration kept. Guide checks pass.
  - **Owner's report (14:55):** the held flick shows no finger trail on the guide's "Hold the flick" slide. Sent to A, after the
    TitanKeys menu slide.
  - **Build #10: bb6ee4b1**, all checks passed, installed at 14:25 with no crash. It has C's end-screen no-bar fix and "TL;DR Trust me
    bro.". Next is build #11, the final candidate: W's grey Not saved word and status marks inside the bar, plus the guide's
    "Nothing here gets saved".
  - **Build #9: f95a0380**, all checks passed, installed at 13:30 with no crash. The Orchestrator's walk confirmed the three fixes:
    capital titles, the demos, the clown valance and the end spacing. One regression: the real bar shows on the end screen and
    covers the grass's bottom, when arriving by Skip from the clipboard slide. Sent to C. Build #10 follows with that fix.
  - **Build #9, the pre-release candidate,** started at 13:15. It includes:
    - A's demos (c9b6cb4): Backspace takes only the e, and the heart demo selects the target;
    - C's end gap (cb22447): 48 dp under the wordmark;
    - V's clown valance (462260b), with a check that reproduces the bug.
  - **Build #8: aaef6f81**, all checks passed, installed at 12:40 with no crash. A's pre-release pass is running on it.
  - **Build #8** started with the caps fix. A runs the pre-release phone pass on it, with strict guardrails: no share step, no reset
    confirms, settings restored, the owner's words untouched.
  - **To merge after build #6:**
    - W's 2671bb0: the heart previews sit at the book's place, 8 dp over the bar; the book fades out under them; the pill is
      unchanged, a 24% accent tint since v3;
    - A's audit, claude/release-audit-2 81da968;
    - then F's receiver drop and Record a bug wording, and D's baseline.
  - **Build #6 started (12:25)** from adcd3c5. Still to come: W's heart-preview and pill follow-up, D's bar baseline, and A's release
    audit.

  - **Faster finish (11:55):** the verify queue was six runs deep, about 90 minutes of duplicates. Every worker now pushes once its
    targeted checks pass and cancels its own queued verify. The Orchestrator merges C, V, W, D, A and F, then runs one full verify
    on the combined test-2.1h as build #6.
  - **V's decorations are built** (312f54f, branch at 7571f2c): the approved levels (accent 36–46%, the e at 40%, grass ink at
    24/40/60%), the lighter big top with real juggling balls, and grass drawn for 150 dp. The Orchestrator checked the renders:
    the clown and the end screen look right.
    - Merge C first, then V, taking V's GuideDecor.kt and GuideDecorRender.kt whole. V's verify is still queued.
  - **Usage limit hit, about 10:55 to 11:10.** On resuming, each worker continued from its branch:
    - W: claude/word-ladder-v4 f112d60, the ladder;
    - C: claude/finale-grass cddc991, the finale;
    - D: claude/press-haptics 59e7e01, then the bar baseline;
    - A: the front page bar overlap;
    - V: the GuideDecor port, from guide-decor-v3-handover.md.

    F kept running: its resets are on fork/claude/resets (f6f6d87), waiting for its report. Merge order: C, then V; the rest
    as they report. Then build #6.
  - **Decor v3 review (Orchestrator, 11:05).** Approved: strengths of 36–46%, grass ink at 24/40/60%, and every design.
    Changes:
    - the clown valance about half as deep at about 30%, with juggling balls that read as balls;
    - grass about 150 dp tall. C makes the room 150 dp (GRASS_DP was 96).

    Merge C's finale branch first, then V's GuideDecor build.
  - **A's guide zones: merged** (325e623, ebe561e; merge 7405e10).
    - Decorations are about twice as strong.
    - The hold slide has no field demo.
    - The zones read START (the only filled patch), a FASTER arrow, and a dashed full-height PAUSE column.
    - The real pause now counts at any height (DeleteHold goes by distance across only).
    - The Orchestrator resolved GuideDecor.LOOKS (F's accent form with A's levels) and the decoration check; guide and
      guide-decor pass.
    - On the phone: how the zones look, and the full-height pause by hand.
  - **Size and global Orange accents: merged** (F's 4af0690, c019ec2 and 03af407; merge 743a128). The Orchestrator resolved
    tests/menu-render/run.sh by keeping both file lists; menu-render and word-menu pass. The release notes cover both. Next for F:
    the two resets.
  - **V, a new focused session** (2.1 V: guide decorations and grass, local_23ffee83, 10:35): decorations and finale grass, through
    phone preview pages and the owner's review ("current install still too subtle"). It then builds them into GuideDecor. A
    keeps the held-delete items, with its strength pass handed on if already done. C keeps the finale layout and the grass's
    placement.
  - **Quick settings hover: merged** (71acf0b). The pressed key pins the mark, its late lift is ignored, and there's no timer. On the phone: rest on F (Sym, then Alt) and press it three times; the mark should stay.
  - **Build #5** started (test-2.1h at 179b7ad). It has everything merged except F's Size and Orange work and W's ladder.
  - **Ladder mockup v4** (C): `X:/Atlas/.dev/mockups/ladder-v4.html`, opened on the phone at 09:40.
    - Not saved: a 42% fill under denser dots.
    - Grey hover previews for every button.
    - A 200 px stage above the bar.
    - Unsave tears the right page down the spine.
    - Block is the strike itself, with no ring: strikethrough icons, and Unblock pulls it back from the right.
    - Springs, interruption, haptic ticks and reduced motion.
    - C's own calls: the next grey preview waits for a fresh rest; Blocked's strike rests grey; the marks sit above the word.

    Waiting on the owner's review.
  - **Effects above the bar:** today they're clipped to the bar's 56 dp (Strip draws inside its own bounds). Since the keyboard
    window became full height (A's bar fix), a see-through effects layer can sit above the bar and draw over the app. Touches still
    pass through, because onComputeInsets' touch region covers only the keyboard. W adds this layer when porting v4's animations.
  - W: the hover and open-burst fixes are in verify. W pushes both, then holds the ladder work.
  - **The plan from 08:45:**
    1. F's Size setting (its verify is running) and W's word menu fix land, and get reviewed and merged.
    2. Build #5 and install.
    3. The Orchestrator's phone checks, done without hands: run1, the Sym panel over the lines at 108, the share screen, the paste
       selection, the front page, and Size at Biggest.
    4. The owner's hands-on review: the word menu's hover and ladder, how the haptics feel, swipe, flick, steer, and the clown hunt.
    5. Release: the owner removes the Sym $%= maps from Key Mapper, does the signed release build and publishes. The release notes get a line for Size and one for the playground haptics.
- **Asked the owner (07:58): haptics for the playground's effects?** Today they're silent. The Orchestrator's proposal is five big moments
  and nothing per step:
  - 40 wpm, the first confetti: Celebrate;
  - 100, the rain: No;
  - 150, the shake: one rumble;
  - 180, each lightning flash: a firm hit;
  - the plant at the top: the swell.

  Each would have its own switch. Owner said yes (08:05).
- **Front page text as one paragraph (owner, 07:50):** C drops the sentence anchoring and the next-line dimming (9b56000). The reference becomes a two-line window that scrolls through one continuous paragraph. Same branch as the title.
- **Front page title (owner, 07:45):** a guide-style title and description. C adds it on claude/playground-next-line, after the
  next-line dimming (9b56000, render checked). Both go into build #4.
- **Build #3: 3511f1fa**, from 7db5c06, with everything from the run-through: all checks passed. Installed at 07:50 with no crash, while the owner was on the phone. The Orchestrator's phone checks wait until it's idle.
    - the share screen gets FixedScale, with its buttons pinned (at font 2.0 the accept was off screen);
    - decoration keep-outs feathered, with the 8 dp gap and nothing under the status bar;
    - the playground's effects go behind its text;
    - the paste selection in orange;
    - the tagline at Label 14;
    - no keyboard on the end screen, so nothing jumps;
    - the playground reference always fills two lines;
    - key sublabels at least 13 dp;
    - the 🤪 burst kept to the content area;
    - a scrim on the leave pop-up;
    - hanging bullets on the share screen.
  - **F** (claude/ink-polish): **done and merged** (b817409).
    - Settings titles and setup rows wrap at font 2.0;
    - no Ink flash on "Go type something";
    - the boring guide doesn't end on a lone word;
    - the home-focus check fixed (it looked for the old hint text).
  - Not changed: Back from the end restarts the fun guide at slide 1 (fine).
  - Not testable over adb: swipe, flick, held flick, steer, three-tap select, Alt+A, hold V, the clown hunt, hover after a press.
- **The overnight phone run-through never ran (the Orchestrator's miss).** idle.sh read the frozen "state at time of last ANR"
  snapshot in dumpsys input, which always says 5218 ms. So it called the phone busy for 6 hours while nobody had touched it.
  Fixed: it now reads only the live dispatcher.
- That snapshot shows an old ANR: GuideActivity didn't answer a focus change for 5 s, on 2026-11-12 at 14:23, a build before
  2ec5f751. The run-through checks for new ANRs.
- **Plan for the rest of the night (Orchestrator):**
  1. ~~Merge C's decoration wiring.~~ Done.
  2. ~~Apply F's two GuideActivity strings, and fix the release-notes Save line.~~ Done.
  3. Build #2, then install once the phone is idle (scratchpad idle.sh). Then C's bracket fix, and build #3.
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
