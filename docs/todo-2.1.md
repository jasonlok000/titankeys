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
  - **Assigned for build #12 (owner's go, 2026-11-13):** V: a panel opening moves nothing on a slide, and the decoration stays
    above the keyboard; F: Size grows only the bar's contents, follows the phone's font size until picked, moves to The bar; W:
    an immediate haptic on Save's press (and any late menu button); A: switches for Delete a word and Next keyboard. All from
    059caf3. Then: check the owner's phone lands on Biggest.
  - **Merged for build #13 (a5ceca7):**
    - A's idle rule (claude/idle-once 63954f2): one 25% roll exactly 3:00 after the last input, none after it in that period;
      the period after a game never plays; never the same game twice running; a new field, the keyboard reopening or the screen
      coming back on starts a new period; the debug broadcast doesn't count.
    - D's lowercase shorthand (claude/shorthand-lowercase 70a4d76): stored and matched lowercase, existing entries repaired once
      (an already-lowercase entry wins a clash). Cause: the build's compat.py stripped autoCapitalise={false} and public Ink
      always asked for sentence capitals; D patched Ink (tools/ink/titankeys.patch) and compat.py (outside the repo, old copy
      kept). TitanKeys itself follows each field's own capital flags; no wider bug.
    - Idle, autocorrect and guide checks pass; bun 169 of 171 (the two that need the ink package).
    - Still to come: W's 11 dp line and its renders, then build #13.
  - **Build #13 on the phone (f4d4e6c6, from ee80354, installed 17:54, 0 crashes; every verify check passed):**
    - The front page through 10 panel switches (371 frames): the text box stays at 573-574 px the whole time (before: 51 px
      lower with a panel open and a 139 px one-frame flash). Frames deleted.
    - The ∞ slide's title stays at 185 px with Sym open; the ∞ stays whole above the panel.
    - The menu slide passes (hold, I, I); TitanKeys stays whole beside "Already a favourite".
    - Settings is at normal size; Bar text size shows Smaller, Normal (ticked), Bigger.
    - Delete 1/3 shows the flat e with its strike.
    - The real bar: the small letters' middle sits on the new line, 61 px under the bar's top (11 dp under the old middle); a
      'g' tail ends about 10 px over the screen's edge.
  - **Merged: "TitanKeys Keyboard"** (F, claude/titankeys-keyboard-name 6cf799d): the launcher, app info, the keyboard list and
    Accessibility; the app's own screens keep "TitanKeys"; the debug build stays "TitanKeys Debug". Guide and bun checks pass.
  - **Smart Launcher's search box** (owner, 2026-11-13: "the shift backspace, some auto correct doesn't work on smart launcher's
    search box perfectly… emojis and sym doesn't work properly (doesn't show up at all)… it also doesn't work well with my
    swiftkey"). Home app ginlemon.flowerfree. From the code: TitanKeys treats a field as text only when it isn't TYPE_NULL
    (`editing`), shows its window only while editing (onEvaluateInputViewShown), and with "Hide the bar on an empty home search"
    on hides the bar in the home app's empty field. A passive watcher reads the field's settings while the owner opens it.
    - Found in Android's input history: the home screen itself starts as TYPE_NULL (normal for a window with no field), and the
      search box is a normal field, inputType 0x1 (plain text, no autocorrect flag) with imeOptions 0x3 (Search), field id
      0x7f0a029b. So TitanKeys does treat it as text: the TYPE_NULL theory is out. "Hide the bar on an empty home search"
      defaults off. Next suspect: Smart Launcher rewrites or restarts the box on each key to update its results, which would
      reset the word being typed (autocorrect, word delete). Why Sym and emoji don't show is still open. Needs a live repro
      in its search box; waiting on the owner's OK to type a test word there.
  - **Merged: C's gesture line** (claude/gesture-line 8ecfe99): every gesture shape on the words' line (carets, the held
    delete's word bars, flick dots, the selection and its handle, steering dots, the finger), carets as tall as the letters, the
    handle knob on top, drags clipped at the bar's sides. Reviewed by the Orchestrator from X:\Atlas\.dev
enders\gesture-line
    (sheet-normal). The edge glows (Space, steering, the held delete's corner glow) still rise from the screen's edge as designed:
    kept (Orchestrator). gesture-line, gesture-display, gesture-render, strip-fit and guide checks pass.
  - **Merged: D's Smart Launcher fix** (claude/launcher-edits 230d680): the launcher takes every key before any keyboard hears it
    (SwiftKey too), so TitanKeys never typed there: no fix, Shift + Backspace as plain Backspace, Sym unheard. Now, in that search
    box only and only with the Back service on, TitanKeys takes the keys (not Back or the phone's own keys) and handles them like
    any other field (TakenKeys). The disclosure line changes to "in the Smart Launcher search field, where the launcher would take
    your keys first, so TitanKeys types there as in any other app;" (needs the owner's OK). A debug-only TitanKeysSym log traces
    each Sym press. Also merged: C's render-temp (renders no longer land in the repo root). Modifiers, autocorrect, guide and
    gesture-line checks pass.
  - **Merged: A's idle line** (claude/idle-line c069d5b, merge a66d29b): all seven games and their ways home start and end on the
    line and stay inside the bar at Smaller, Normal and Bigger; 393 keyframes through corner_check with 0 outside the safe zone.
    Reviewed by the Orchestrator (X:\Atlas\.dev\renders\idle-line, normal-bowling). Idle, strip-fit and guide checks pass.
  - **Merged: W's round 2 on the line** (claude/chips-effects fe4b7dd + 8372692, merge ebb47ce): the modifier chips' text on the
    line with their boxes inside the bar (the render now models the phone's layout and reproduces build #13's cut chips); the six
    menu effects share one stage, the book and the hearts starting level; the two-line clip offer moves toward the line (shrinks
    to 0.84 at Normal and 0.72 at Bigger, its hint no smaller than 13 sp); the Sym band on the line at the screen's edge; the
    guide's ghost finger and Fix demo on the line. Global audit: 30 items, 0 of 165 bar states red in corner_check (W's renders in
    X:\Atlas\.claude\worktrees\word-menu-fix-25c785\.dev\renders\line-round2-w2). Conflict with C's finger
    in Strip.drawDemoFinger resolved to C's GestureDrawing.finger. Reviewed (modifier-normal, effects-normal-save/favourite);
    all bar checks pass. The Orchestrator's calls: keep the clip-offer shrink; keep the guide's first-slide side words rising from
    under the edge.
  - **Resumed (2026-11-14):** all six workers restarted with their queues (D: the square-key regression first). Merged:
    F's claude/setup-fixes (the Setup fixes, blocker #2 effa77a, #23, #26, #27, #28) and V's claude/menu-selected (blocker #1
    6556931, the selected-word underline 3c7de0f). Guide, word-menu, strip-fit, fixed-scale, idle-render and menu-render checks
    pass; bun 180 of 182 (the two that need the ink package). F's next: the always-dark keyboard and #36.
  - **Build #16 (abb07f92, from e04ca0c) installed 22:09, every verify check passed:** the square key fix (D's allowlist), both
    blockers, F's Setup fixes, V's underline.
  - **Merged after it (to 18a22ba and the merge fixes):** F's always-dark keyboard and #36; D's #4, #13, #14, #15, #24, #30, #37,
    #38 and the screen-lock guard; W's #3/#6, #11, #12, #25, the hover after a press, the Sym rows evenly spaced (68 px), the
    arrow (follow-up sent: 0.63 of the words' cap height, about 11 dp at Normal); C's #7, #16, #17, #18, #22, #35, the held
    delete's orange, the selection gap and the compressed swipe trail (about +/-13 px, much flatter: the owner to judge on the
    phone). Conflicts resolved: steering keeps D's steer flag and C's end-of-text rule; the field-start selected word kept once
    (C's); W's guideShowOnly now reads inGuide (V removed shownGuideWord). All targeted suites pass; bun 184 of 186.
  - **Merged next (to d79c5f9 and fixes):** A's 80% idle floor (claude/idle-floor 53b7cc5; reviewed: the figure, ball and letters
    land within the line's ends; the line only during floor games) and A's nine typing and idle fixes (claude/bug-hunt-typing
    a35aa1c: #5, #8, #9, #10, #29, #39, #19, #33, #34); W's arrow at 0.63 of the words' capitals (77d8e44). Conflicts: Accent and
    Look combined always-dark with the floor's cream; A's two check blocks kept; the floor check updated for always-dark. All
    targeted suites pass; bun 184 of 186. Still out: V (#20, #21, #31, #32, the end screen's Back, the border pass).
  - **Owner's review of build 0a6d754e (2026-11-14), noted, not started yet:**
    - Guide end screen, Back (owner: "When going back from the boring guide triggered from the end screen it should go back to the
      end screen. Applies to everything else for end screen"). Every page the end screen opens (the boring guide, Settings
      links, Share swipes and the rest) returns to the end screen on Back, as it was left. For V when work resumes, with a
      check per link.
    - **Regression, first for D on resume** (owner: "I think my square button is not working for me anymore on smart launcher.").
      D's TakenKeys takes every key in Smart Launcher's search except event.isSystem; Android's isSystem covers Home and Back but
      not KEYCODE_APP_SWITCH (the square), so TitanKeys takes Recents and drops it (AtlasIme.kt:1420). Fix: take only the keys
      TitanKeys types or handles (an allowlist: letters, digits, Space, Enter, Backspace, Shift, Alt, Sym, Ctrl/fn and the
      keyboard's own), and let every other key through, Recents and the rest of the navigation keys included. A check for the
      square key. Then a build for the owner.
    - Word menu, cosmetic: "When I press a button and let go it still registers as that I'm hovering." After a press and its
      release, the pressed button's hover preview stays on. Today the press keeps the preview it took over until the finger
      moves (W's design), so either the release must clear it or the lift isn't heard. For W when work resumes.
    - Held delete, design question (owner: "should the deleting long swipe should have more orange accents like other gestures
      like triple space or do you think its fine?"). The Orchestrator's view: yes, a little. The word being eaten right now turns
      orange, like triple Space's selected words; the queued words stay grey; it dims back to grey while paused. Waiting on the
      owner; a quick phone preview first if wanted.
      - Chosen on the phone preview (cal-tool/heldelete.html): option 2, the words it's eating in orange, with option 4 as its
        paused state: back to light grey, with a dimmer caret and glow, while the finger rests in the pause zone (owner: "Let's
        do 2"). Queued for C when work resumes.
    - Triple Space selection, spacing bug (owner's screenshot, 20:20: "Bugged with the spacing?"). The orange selection pill runs
      into the grey word pill on its left: no gap between them, while every other pair has one. The selection is drawn wider
      than its word's place (its padding, since C's gesture line sized it at 1.3x the words), so it eats the gap. Fix: keep the
      same gap on both sides of the selection, by moving the neighbours or limiting its padding to the gap. For C when work
      resumes.
    - Sym panel spacing (owner's screenshot, 20:22: "Is the bottom category bar supposed to have a taller gap above the categories
      to compensate for the border to be more evenly aligned in spacing?"). Measured: the three key rows' middles are 72 and 71 px
      apart, but the category row sits only 55 px under the last one, because it moved onto the line and the rows didn't. The
      Orchestrator's proposal: keep the top row and the category row where they are, and space all four evenly (about 66 px), so
      the gap above the categories matches the rest. For W when work resumes, with before/after renders.
      - Owner asked: spread the rows, or make the panel taller? The Orchestrator recommends spreading: the panel keeps its height,
        so it covers no more of the app and the layouts that depend on its height (the guide, the playground) stay as they are;
        66 px rows still leave plenty of room for one-character labels and emoji. Taller would mean about 17 px more of the app
        hidden on every page.
      - Decided (owner: "Ok agreed with u. Distribute it."): the same panel height, the four rows evenly spaced, the categories on
        the line. Queued for W when work resumes, with before/after renders.
    - Swipe trail on the bar (owner: "Should we compress slide animation on the bar so that its more center aligned properly with
      the border and like doesn't hit the bottom edge?"). Today the swipe path maps the three key rows onto the bar's full
      height, with its middle row on the line, so the bottom row's points run along the screen's edge. The Orchestrator's view:
      yes, compress it so the path is centred on the line, symmetric above and below, with its lowest point above the floor
      margin like the letters' tails. Confirmed (owner: "Yes please queue that. That's what I meant."). Queued for C, with
      before/after renders.
    - What else the border illusion touches (owner: "What else might I miss because of this border illusion?"). The
      Orchestrator's list, waiting on the owner:
      1. The light look: with the phone's dark mode off, the bar is white (Look.paper), the black border shows a clear edge, and
         the lower line would look too low. Use the bar's true middle in the light look.
      2. Screenshots and recordings (the Play listing, the README, Record a bug) don't include the border, so the bar's text
         looks low in them. Marketing images need the border or a device frame added.
      3. Black screens with buttons at the bottom (the share and Back disclosures, the reset confirms, Record a bug, Ink pages):
         the border makes their bottom gap look bigger than designed. One visual pass.
      4. The Sym panel's other pages (emoji, clipboard, quick settings) get the same even row spacing as the symbols page.
      5. Orange screens (the guide's pass, the finale, the end) don't blend with the black border, so the maths centre is right
         there; leave them.
      6. Brightness: if the screen's blacks glow at high brightness or in sunlight, the edge shows and the illusion weakens.
         Worth a look outdoors.
    - Auto-capital arrow (owner: "I think the auto cap icon should be smaller like how it was like in the old one before the
      resize. And scales with the bar element size."). The automatic capital's Shift arrow at the bar's left grew with the
      chips; bring it back to its size before font size mattered (owner: "Before the resize means before font size matters."): as
      Strip drew it at 824c865^, before FixedScale (2026-11-12), at Smaller, scaled by the Bar text size factor, on the line and
      clear of the corner. For W when work resumes, with before/after renders at all three sizes.
      - Owner's answers: the keyboard is always dark (W, when work resumes: Look always dark, the light look removed); the guide
        and the playground get the border pass (V); the Sym panel's other pages get the even spacing (W); orange screens stay;
        brightness ignored for now; screenshots noted for the Play launch.
  - **Paused for the usage limit (2026-11-14, 5-hour window at 95%, resets 02:10 UTC; weekly at 87%).** W, C, D and A stopped
    mid-turn; F and V were idle; their unfinished changes stay in their worktrees. On resume, send each a short "carry on":
    - V: blocker #1 first, then the selected-word underline, #20, #21, #31, #32.
    - F: blocker #2 first, then the Setup fixes, #23, #26, #27, #28.
    - W: #3, #6, #11, #12, #25, #36.
    - D: #4, #13, #14, #15, #24, #30, #37, #38, TakenKeys after screen-off.
    - C: #7, #16, #17, #18, #22, #35.
    - A: the floor line at 80%, solid, everything grounded; then #5, #8, #9, #10, #29, #39, #19, #33, #34.
    - Meanwhile a full verify of test-2.1h (build #15) runs on its own.
  - **Bug hunt done** (docs/bug-hunt-2.1.md, merged at c8fd49b): 41 findings, 2 blocking, 22 should fix, 17 later. The
    Orchestrator confirmed blocker #1 in the code (GuideActivity.onPause never removes showHelp, demoTick or huntTick). Triage:
    - V: #1 first (blocker: guide timers outlive the guide), then #20, #21, #31, #32 (#31 and #32 raised from later: the guide
      loses the user's clipboard).
    - F: #2 first (blocker: Reset literally everything leaves shorthand, app lists, recent apps, snippet start), then #23, #26,
      #27, #28.
    - W: #3, #6, #11, #12, #25, #36 (word menu, and accents-off cream).
    - D: #4, #13, #14, #15, #24, #30, #37, #38, and TakenKeys after screen-off.
    - C: #7, #16, #17, #18, #22, #35.
    - A, after the floor line: #5, #8, #9, #10, #29, #39, #19, #33, #34.
    - After the pre-release: #40 (battery) and #41 (leaks).
    Each fix comes with a check that reproduces it; workers confirm each finding first.
  - **Bug hunt:** its own session, "TitanKeys 2.1 bug hunt" (local_34f68a1f), findings only, to docs/bug-hunt-2.1.md; the
    Orchestrator triages and assigns fixes.
  - **Workers' model (owner, 2026-11-13):** subagents and every worker session run on Opus 5.5 at medium effort; set on A, C, D,
    F, V, W and the bug hunt.
  - **Setup (owner, 2026-11-13), assigned to F:** the Sliding Mode 1 false alarm for Chrome and Settings; the step names "Sliding
    Mode 1"; "Display over other apps" lands on the whole list (Android 11+ ignores the package; the highlight extra does nothing
    on this phone; android.settings.MANAGE_APP_OVERLAY_PERMISSION opens TitanKeys' own page from adb) and Accessibility likewise.
  - **The Orchestrator's slip:** an overlay test pressed Back three times without checking what was in front, while the owner was
    in SwiftKey's settings and Teams. Every Back or Home in the Orchestrator's scripts now checks the front window first.
  - **The real Sym key in Smart Launcher:** the owner confirms it doesn't get through. Android's dispatcher keeps no key codes and
    TitanKeys doesn't log Sym yet; D's debug trace will show where it goes.
  - **Withdrawn: build #14 is no longer the release candidate** (owner's round 2 on the line, 2026-11-13). In progress: W (the
    modifier bar's cut chips, the menu effects' anchor and scale), C (the gesture display on the line), A (the idle games on the
    line). Each sends keyframe strips in the border-and-corner render; the Orchestrator reviews every frame, then the phone.
  - **Build #14, the release candidate, passed every verify check** (from e3cefc5; debug APK c90e1827, not installed: the same
    app as build #13 apart from the release notes). The WSL build copy ~/build/tk-overnight now holds this tree with compat.py
    applied and Ink patched: the owner runs `cd ~/build/tk-overnight && bash tools/release.sh` there (as dev in atlas-build)
    after their hands-on pass, then publishes the GitHub pre-release. Any later verify of another branch replaces that copy.
  - **Merged for build #13: W's 11 dp line** (claude/bar-line-11dp 52e6297, merge 2b7d234). StripFit.BORDER_DP = 22; everything
    with text in the bar on the line 39 dp down (within 0.47, 0.17 and 0.13 px at Smaller, Normal, Bigger); the simulated-border
    render puts the letters' middle 0.13 to 0.72 px from the black band's middle; corner_check (r 100, margin 4) gives 0 of 96
    renders with red marks (7 before). Beyond the brief: side margins measured straight to the curve (words 63/73/91 px, chips
    67/77/100); the two-line clip offer shrinks a little to keep 4 px clear; Back rises 2/4/6 px; the pill is 32.6 dp; the guide's
    wiggle bobs upward only; Exact typing's chips hug their labels. Left on the old middle: the gesture display's shapes and the
    guide's ghost finger. Strip-fit, word-menu, fixed-scale, guide, idle, words and menu-render checks pass. Reviewed by the
    Orchestrator from the regenerated renders (border.png, before-after.png).
  - **Release re-audit (A), merged** (claude/release-reaudit 16a7587; docs/release-audit-2.1.md): 116.0 MB, not debuggable,
    allowBackup off, the same six permissions; Ink's InkLightSdkReceiver is gone (the patch removes it), 17 components, 3 exported
    (MainActivity, AtlasIme behind BIND_INPUT_METHOD, BackService behind BIND_ACCESSIBILITY_SERVICE); autoCapitalise carried
    through; the PANEL and IDLE_BAR broadcasts register only in .debug; no render or preview code ships.
    - Found: tools/release.sh didn't apply tools/ink/titankeys.patch, so a release on clean Ink would quietly lose the accent,
      autoCapitalise, the press haptic and the receiver's removal. Fixed by the Orchestrator: release.sh now refuses unless
      ../ink carries the whole patch (git apply --reverse --check), and says how to apply it. Tested on the patched shared Ink
      (passes) and a clean clone (refuses).
    - Release notes gaps filled by the Orchestrator: shortcut switches, the idle rule, lowercase shorthand, panels moving
      nothing, the word colours, everyone on Normal. The stale InkLightSdkReceiver comment in debug-only.test.ts fixed.
    - Next: after build #13's phone check, one last verify of the tip as the release candidate (build #14). It leaves the WSL
      build copy holding the final tree with Ink patched, where the owner runs tools/release.sh.

  - **The old "bar drops to zero height while cycling Sym", rechecked (build #12):** a screen recording of the playground while
    10 panel changes ran by the debug broadcast (370 frames). The keyboard never collapsed. What does jump is the playground's
    own layout: 51 px lower with a panel open, and one frame a further 139 px lower at each open and close (the drawn keys hide
    before the column takes the panel's room). V extends panel-still to the playground. The real Sym key stays for the owner's
    hands. The frames were deleted; some showed the clipboard.
  - **Languages wait until 2.1 is out** (owner, 2026-11-13: "Lets finis up 2.1 first.").
  - **Path to the 2.1 pre-release (asked by the owner, 2026-11-13):**
    1. W's, A's and D's work lands; the Orchestrator reviews W's corner and border renders; build #13; phone check.
    2. The owner's hands-only pass: the touchpad (swipe, held flick and its trail, PAUSE height, steer, select), the real Sym key
       and held letters, haptic feel (the menu's press tick too), pasting on the guide's slides, the two shortcut switches, and
       judging the moved menu word.
    3. The Orchestrator: recheck the old "bar drops to zero height while cycling Sym" (never closed in this file), and build the
       release variant without the owner's key to check it compiles and carries no debug code (against docs/release-audit-2.1.md).
       The release notes get a final pass for today's changes.
    4. The owner: merge test-2.1h or open the PR, run tools/release.sh with their own signing credentials, publish it as a GitHub
       pre-release, install it and daily-drive it for 3 days. The release app is com.titankeys, separate from the debug app on the
       phone, so it starts with fresh settings and words, and needs to be set as the keyboard and turned on in Accessibility.
  - **Assigned for build #13 (owner's go, 2026-11-13):** W: the 11 dp optical line with its mitigations and corner_check and
    simulated-border renders (reviewed before the build); A: idle games roll once at 3:00, no two triggers or same games running;
    D: shorthand saved lowercase and the cap-flag cause.
  - **Merged for build #13:** F's renamed sizes (claude/bar-sizes-renamed cb5984d): Smaller 1.0, Normal 1.2 (everyone's
    default, no following the phone), Bigger 1.4. Fixed-scale, strip-fit, word-menu, guide and menu-render checks pass; bun 168 of
    170 (the two that need the ink package). Waiting on the owner: the lower line with the effects check, and the idle odds.
  - **Build #12 on the phone (5acc28a2, from 574184f, installed 15:30, 0 crashes; every verify check passed).** Checked over
    adb:
    - Sym on the ∞ slide: the title stays at the same pixel (185 before, at 0.6 s, at 2 s and after; build #10 dropped 51 px),
      and the ∞ stays whole above the panel (V's panel-still).
    - The menu slide at Bigger: TitanKeys moves left as the menu opens and stays whole beside "Already a favourite" (W's
      menu-word-clear). The owner judges whether the move looks good; fallbacks are logged.
    - Settings is at Normal size; Settings > The bar > Bar text size shows Bigger, following the phone's text size (F).
    - Delete 1/3 shows the flat e with its strike (V).
    - Left for the owner's hands: the touchpad (swipe, held flick and its trail, PAUSE height, steer, select), the real Sym key
      and held letters, haptic feel (the menu's press tick included), pasting on the guide's slides, the two shortcut switches.
  - **Build #11 on the phone (1b094db8, installed 14:53, 0 crashes):** the menu slide works from start to pass over adb (hold
    TitanKeys, I, I): underlined in the box and on the bar, grey, then white, then orange, "FAVOURITED. GOOD TASTE.", Next. The ∞
    slide reads "ANSWER HONESTLY." over "So how long do I last in bed with your mother?". Found: at favourite, the "Already a
    favourite" pill covers the word ("TitanKe"); sent to W. The ∞ is cut by the Sym panel and the slide drops, as expected
    before V's fix.
  - **Bar text size starts at Bigger, never Biggest** (owner: "if it detects the size being big, it'll select big, not
    biggest. I think big is a good spot for me personally"). f8f743f. Biggest is only ever picked.
  - **Merged for build #12 (2abbaed):**
    - W's press tick (claude/menu-press-tick 5a9aebf): a 17 ms Texture tick on every word menu press whose own haptic lands later
      than 80 ms (Save 758 ms, Unsave 424, Favourite 166, Unfavourite 191, Block and Unblock 366); its own switch in Haptics > Bar.
    - F's Bar text size (claude/bar-text-size 499590b): only the bar's words, menu, chips, notice, hint and clip offer grow, the
      bar stays 56 dp, everything else is Normal; it follows the phone's text size until picked (new key bar_text_size, so the
      owner starts on "Biggest (phone)"); it lives in Settings > The bar.
    - Checks pass: fixed-scale, word-menu, haptics, modifiers, guide, strip-fit, menu-render; bun 168 of 170 (the two that need
      the ink package in the build copy).
    - Still to come: V's Sym fix. Then build #12.
  - **Merged for build #12:** A's shortcut switches (claude/shortcut-switches 55de058): "Hold Shift + Backspace to delete a word"
    and "Hold Alt + Sym for the next keyboard", on by default, back on with both resets. Off, Shift + Backspace deletes one letter
    and Alt + Sym opens the panel. Modifiers, guide, held-shortcuts and setting-names checks pass.
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
