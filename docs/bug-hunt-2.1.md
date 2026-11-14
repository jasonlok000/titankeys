# Bug hunt, 2.1

A read of the whole app before the 2.1 pre-release, on `test-2.1h` at 81ece32 (2026-11-13, evening). Nothing was fixed here:
Master triages this list and assigns the fixes. No phone was used.

**How:** nine readers, one per area, traced the code across files and tried to disprove each candidate (guards elsewhere, call
order, threading, existing checks). Then each kept finding was checked again at the cited lines. The typing findings were also run
on the real Composer with the real autocorrect data (a copy of `tests/autocorrect/AutocorrectChecks.kt` with probe cases). Known
items are left out: the modifier bar's cut chips, the menu effects' anchor, the selected word's underline, Setup's Sliding Mode 1
alarm and its overlay and Accessibility pages, Smart Launcher taking keys first, the idle games' line, and the gesture display on
the old middle.

**Tests run** (WSL, a copy of the tree, offline suites only): see [Tests](#tests) at the end.

Severity: **blocks** the pre-release, **should fix** before the real release, or **later**. Confidence: **confirmed** (the
code path is traced end to end, or the probe reproduced it) or **plausible** (needs the phone to size it). Line numbers are at
81ece32.

## Summary

| # | Severity | Area | Finding |
|---|---|---|---|
| 1 | blocks | Guide | The guide keeps vibrating and running its demo after you leave it |
| 2 | blocks | Settings | "Reset literally everything" leaves your shorthand, app lists and snippet start |
| 3 | should fix (high) | Word menu | With the menu open, Sym/emoji/clipboard/quick settings or Alt keys press hidden menu buttons |
| 4 | should fix (high) | Modifiers | A tapped Shift or Caps turns steering into a selection; the next letter replaces it |
| 5 | should fix (high) | Typing | Chrome's address bar and URI fields: Space after a swiped or picked word gives two spaces |
| 6 | should fix | Word menu | The menu survives the keyboard hiding and eats the next I/O/K/L/P/Backspace |
| 7 | should fix | Gestures | A flick or held delete with text selected deletes it, but plays No and shows nothing |
| 8 | should fix | Typing | Typos and Missing apostrophes off: the second look still makes those fixes |
| 9 | should fix | Typing | Missing spaces off: "andMary" is still split |
| 10 | should fix | Typing | Fields that ask for no suggestions are still autocorrected and still teach words |
| 11 | should fix | Word menu | Save keeps the sentence-start capital for a swiped or spaced word |
| 12 | should fix | Word menu | Accented words open a menu with a wrong status and dead buttons |
| 13 | should fix | Typing | "Spell out shorthand" does nothing while Fix typos is off |
| 14 | should fix | Snippets | A "?" or "!" snippet start eats the sentence's question or exclamation mark |
| 15 | should fix | Snippets | Adding or renaming onto an existing shortcut silently overwrites it |
| 16 | should fix | Gestures | Steering past either end of the text keeps sending arrows |
| 17 | should fix | Lifecycle | The last field's selection leaks into the next field |
| 18 | should fix | Haptics | Holding a bar word, chip or clip also plays Android's stock long-press buzz |
| 19 | should fix | Idle games | A game keeps drawing over a selected word or clip offer that arrives mid-game |
| 20 | should fix | Guide | The guide reopens by itself long after its Accessibility step was abandoned |
| 21 | should fix | Playground | WPM counts a paste, shortcuts and menu keys as typing |
| 22 | should fix | Swipe | One failed engine load kills swipe until a reboot; the promised Retry doesn't exist |
| 23 | should fix | Record a bug | Main-thread work: the auto-stop waits on the writer, the page re-exports the file |
| 24 | should fix | Modifiers | Hold Alt + Sym has no fallback for an Alt press that Key Mapper swallowed |
| 25-41 | later | various | See [Later](#later) |

## Blocks the pre-release

### 1. The guide keeps vibrating and running its demo after you leave it

- **Area:** guide.
- **Confidence:** confirmed.
- **Repro:** open the guide and reach a slide with help, such as Steering. Wait 3 s for the help chip, then press Home (or turn
  the screen off). Use another app: the GuideHint haptic plays about every 6 s until Android destroys the guide. Pressing Home
  within 5 s of the last key on a slide with a demo leaves the demo loop drawing at 30 fps in the background. The ∞ hunt's tick
  also keeps going and vibrates at 20, 35 and 50 s.
- **Evidence:**
  - `GuideActivity.kt:1080-1096` `onPause` only calls `endDemo()` and removes `wpmTick`. It never removes `showHelp` (:955),
    `startDemo`, `demoTick`, `huntTick` or the idle lines. There is no `onStop`.
  - `GuideActivity.kt:977-1002` `helpDue` checks the finger, held keys and the menu, but not `visible`: :991
    `haptics.play(Haptic.GuideHint)`, :995-998 posts `startDemo`, and :999 `main.postDelayed(showHelp, HELP_REPEAT_MILLIS)` re-arms
    itself.
  - `GuideActivity.kt:2466-2473` `acted()` posts `startDemo` at +5 s; `:2558-2609` the demo loops by `postDelayed(demoTick, 33)`.
  - `GuideActivity.kt:780-803` `huntTick` re-posts every second and calls `hinted()`, which vibrates (:2494).
- **Why it's wrong:** phantom vibrations and battery drain in other apps, during the owner's 3-day daily drive.
  `docs/guide-failures.md:34-35` itself says that away from the guide the keyboard is as it always is, and that with the screen off
  the demo stops.
- **Fix:** in `onPause`, remove `showHelp`, `startDemo`, `demoTick`, `huntTick` and the idle lines; re-arm them in `onResume`
  with `acted()` and `waitForHelp()`. Or gate `helpDue`, `beginDemo` and `hinted` on `visible`.

### 2. "Reset literally everything" leaves your shorthand, app lists and snippet start

- **Area:** settings, resets.
- **Confidence:** confirmed by trace (the race goes the wrong way nearly every time). Confirm on the phone with
  `run-as com.titankeys.debug cat shared_prefs/typing_settings.xml` after the wipe: it should be missing.
- **Repro:** add your own shorthand (hw → however), add an app to "Hide the bar in" and to Exact typing, and pick "#" as the
  snippet start. Settings > Reset literally everything > Wipe it all. After the restart, hw, both app lists, the recent apps and "#"
  are all still there.
- **Evidence:**
  - `vandam/ink/AppNativeAdapters.kt:343` `"wipeEverything"` runs `SettingsReset.reset(activity)`, then `FreshStart.wipe(activity)`.
  - `SettingsReset.kt:45` and `:48`: `typing.edit()…remove(…).apply()` and the same for haptics. `apply()` is queued on the cached
    `typing_settings` instance, which still holds the data keys (own_shorthand, enter_apps, exact_apps, hidden_bar_apps,
    recent_apps, snippet_start).
  - `FreshStart.kt:58` `activity.deleteSharedPreferences(name)` deletes the file, but the queued `apply()` writes the cached map
    back about 100 ms later (Android writes when the file is missing). The old process is killed only once the restart activity
    is up, well after that.
- **Why it's wrong:** owner decision (owner-decisions-2.1.md, "Two resets, named by the owner"): Reset literally everything is a
  fresh install and "also wipes words, snippets, shorthand, app lists…". The confirm page says the same.
- **Fix:** in `wipe`, call `getSharedPreferences(name).edit().clear().commit()` on each WIPED file before deleting it (the
  commit queues behind the pending apply, so it lands last). Or skip `SettingsReset.reset` in the wipe and reload Accent and
  learning in memory. `tools/checks/settings-reset.test.ts` pins the current order, so it changes too.

## Should fix

### 3. With the menu open, panel or Alt keys press hidden menu buttons

- **Area:** word menu, panels. **Confidence:** confirmed. Close to a blocker: it silently changes a word's status and loses the
  key.
- **Repro:** hold a bar word so its menu opens. Press Sym: the Sym panel replaces the bar. Press I to type I's symbol. Nothing is
  typed, and the word is silently Saved (or Favourited). O blocks or unsaves; Backspace closes the menu instead of turning the
  page. The emoji, clipboard and quick-settings pages do the same, and so does Alt + I/O/K/L/P.
- **Evidence:**
  - `Modifiers.kt:357` Sym is a modifier, so `AtlasIme.kt:3214-3217` ("typing on closes the menu") never runs for it.
  - `AtlasIme.kt:1868` `menuOpen` stays true while the panel hides the strip (`:3653`).
  - `AtlasIme.kt:3190-3207`: the menu's key branch runs before `panelKey` (:3425) and has no panel or Alt guard. The touch paths do
    have one: `:2268` `if (menuOpen && !panelOpen)`, `:2998` `if (menuOpen && !altOrPanel) return`.
- **Why it's wrong:** a status change the user can't see (the scene doesn't draw while the strip is gone), against "no icon changes
  instantly; every change animates" and the one ladder.
- **Fix:** close the menu when a panel opens or Alt is pressed, and add `&& !altOrPanel` to the branch at 3190.

### 4. A tapped Shift or Caps turns steering into a selection; the next letter replaces it

- **Area:** modifiers, steering. **Confidence:** confirmed (a TextView extends the selection on Shift + arrow).
- **Repro:** in "hello world|", tap Shift once, then steer left on Space. The text is selected instead of the caret moving. Type
  "a" and the selection is replaced by "A". With Double press locks on, Caps does this on every steer, and also turns Tab into
  Shift + Tab and Ctrl + Z into Ctrl + Shift + Z (redo).
- **Evidence:** `Modifiers.kt:107-108` adds META_SHIFT_ON (and CAPS_LOCK) for Once and Locked. `AtlasIme.kt:3550` drops Shift
  from commands only when it was the automatic Shift. `AtlasIme.kt:2637-2654` `moveCursor` → `command(arrow)`. `:2308` Shift doesn't
  stop steering, and steering never consumes Shift Once. Tab (`:3400`) and Ctrl shortcuts (`:3373`) go through the same `command`.
- **Why it's wrong:** text loss; the comment at `:3549` says an arrow must not become a selection.
- **Fix:** never add sticky Shift to the steering arrows; in `command`, drop Shift for non-text keys when Shift is Locked.
  Separately check on the phone whether letters under Caps (SHIFT_ON | CAPS_LOCK_ON passed to `getUnicodeChar`, `:3402`) come out
  upper case on the Titan's key map.

### 5. Chrome's address bar and URI fields: Space after a swiped or picked word gives two spaces

- **Area:** typing. **Confidence:** confirmed by the probe ("weather" swiped then Space gave "weather  "; a picked word and a
  light-touch "a" did the same). A normal field gives one space.
- **Repro:** in Chrome's address bar, swipe a word, or type "wea" and tap "weather" on the bar, then press Space.
- **Evidence:** `Field.kt:57` `fullStops = !password && !address` is false for URI fields while `words` and `suggestions` stay on.
  The word puts its own space and sets `automaticSpace` (`Composer.kt:573`, `:695`, `:1114`). `Composer.kt:806` `!field.fullStops
  || anchor.selected -> edit(…, " ")` runs before `:807`, the branch that swallows the extra space.
- **Fix:** test `automaticSpace == anchor && before.endsWith(" ")` before the `!field.fullStops` branch; only the full stop should
  depend on `fullStops`.

### 6. The word menu survives the keyboard hiding and eats the next key

- **Area:** word menu. **Confidence:** confirmed by trace; needs a field that keeps focus after Back to see it.
- **Repro:** hold a bar word to open the menu. Press the hardware Back (or the nav bar's hide) in a field that stays focused. Type
  "i": nothing appears and the word is Saved; Backspace only closes the menu.
- **Evidence:** `AtlasIme.kt:1504-1525` `onWindowHidden` closes the panel ("a panel that nobody can see must not take the next
  key") but not the menu. Back with the menu open hides the window (`:3283-3289`). On the next key, `:3181` only posts the strip
  back, and the menu branch (`:3190`) takes the key at once. Only `onFinishInput` (`:2134`) closes the menu.
- **Fix:** `closeMenu()` in `onWindowHidden`; optionally let Back close an open menu first and stay in the app, as with a panel.

### 7. A flick or held delete with text selected deletes it, but plays No and shows nothing

- **Area:** gestures. **Confidence:** confirmed.
- **Repro:** type "hello world", select all (fn + A), flick left from Backspace. Everything goes, but you feel No and see no glow
  or dots. With a selection mid-text, the dots count the word before the selection, not what went.
- **Evidence:** `AtlasIme.kt:2731-2734` `somethingToDelete` reads only `getTextBeforeCursor(1, 0)`. `:3014-3021` plays
  `deleteFoundNothing` when that is empty, then still calls `composer.backspace(true)`, which deletes the selection
  (`ConnectionEditor.kt:135-136`). The held delete (`:450-458`) has the same blind spot.
- **Why it's wrong:** owner decision (Gestures): "No means nothing happened at all".
- **Fix:** count a non-empty selection as something to delete in `somethingToDelete`, `outcome()` and the held delete, and draw
  the dots from the selected text.

### 8. Typos and Missing apostrophes off: the second look still makes those fixes

- **Area:** autocorrect. **Confidence:** confirmed by the probe. With Typos off: "teh cat " → "the cat ", "see you tomorow then "
  → "tomorrow", "I saw teh." → "the.". With Missing apostrophes off: "i dont know " → "I don't know ".
- **Repro:** Settings > What gets fixed > Typos off (Second look on, its default). Type "teh cat ".
- **Evidence:** the first check honours the switches (`TypedCorrector.kt:363`, `:392`), but the second look checks only
  SECOND_LOOK (`:425`) and then `context.review` (`:440`), which fixes non-words anyway: `ContextCorrector.kt:178`
  `reviewShortSlip`, `:146` `fixForUnknown`, with contractions among the candidates (`TypedCorrector.kt:233`). At a sentence end,
  `:444-447` fixes the last word itself. `AutocorrectChecks.kt:2435-2461` types only "teh " with nothing after, so it misses this.
- **Why it's wrong:** owner decision: each kind of fix has its own switch.
- **Fix:** pass TYPOS and APOSTROPHES into `review`; gate the slip and unknown-word paths on TYPOS and apostrophe-only fixes on
  APOSTROPHES. Real-word confusions stay under SECOND_LOOK.

### 9. Missing spaces off: "andMary" is still split

- **Area:** autocorrect. **Confidence:** confirmed by the probe, also with every switch off.
- **Evidence:** `TypedCorrector.kt:349-353`, the camel-case branch, has no `on(FixKind.MISSING_SPACES)`, unlike `:366` and `:406`.
  FixKind's own doc names "andMary" as this switch's example.
- **Fix:** return null there unless MISSING_SPACES is on.

### 10. Fields that ask for no suggestions are still autocorrected and still teach words

- **Area:** typing, learning. **Confidence:** confirmed by the probe: `Field.of(TEXT | NO_SUGGESTIONS)` gives autocorrect,
  suggestions and learning; "jsmith " became "smith ", and "zorblat" was learned.
- **Repro:** a username or code field that sets TYPE_TEXT_FLAG_NO_SUGGESTIONS (web inputs with autocorrect off use it): type
  "jsmith" and Space.
- **Evidence:** `Field.kt:51` reads the flag only for URL search bars; the return at `:56-61` ignores it for every other field.
- **Fix:** for text fields with the flag (outside URI bars), turn off autocorrect, suggestions and learning. `AutocorrectChecks.kt:2151`
  expects capital I in such a field; keep that if the owner wants it.

### 11. Save keeps the sentence-start capital for a swiped or spaced word

- **Area:** word menu. **Confidence:** confirmed.
- **Repro:** in an empty field, swipe "yeet" (or type it and Space): it shows "Yeet". Hold it on the bar, press I. Later,
  mid-sentence, it is typed "Yeet".
- **Evidence:** `AtlasIme.kt:2087-2091` `keptCase` drops `held.typed.length` from the text before the cursor, but `typed` is empty
  for a swiped or recalled word (`Composer.kt:1043-1047`) and doesn't count the trailing space for a typed one. `before` then
  ends with the word itself, not at a sentence start, so `WordCase.kept` (`WordCase.java:26-27`) keeps the capital.
  `WordCaseChecks` tests WordCase alone.
- **Why it's wrong:** owner's "and → And" decision (10:20): store the dictionary's or the natural case. The fix only works for a
  word still being typed.
- **Fix:** pass the text before the held word's real start (the composer knows it) to `WordCase.kept`.

### 12. Accented words open a menu with a wrong status and dead buttons

- **Area:** word menu. **Confidence:** confirmed.
- **Repro:** type "cafe" so the bar offers "café", or type "José". Hold it: the menu shows Not saved (grey), though both are in the
  dictionary. I or O plays the failure and snaps back.
- **Evidence:** `MenuKeys.kt:104-107` `opens()` accepts any `isLetter()`; the stores accept only `[a-z]+('[a-z]+)*`
  (`SwipeWords.java:6-9`, `BlockedWords.valid`). So the built-in lookup is 0 and Save/Block throw (`LanguageHabits.java:41-43`).
  The same goes for "dogs'" and "'til".
- **Why it's wrong:** owner: a dictionary word counts as Saved, and words that can't be stored open no menu.
- **Fix:** make `opens()` use the stores' rule (`SwipeWords.normalise(word).isNotEmpty()`).

### 13. "Spell out shorthand" does nothing while Fix typos is off

- **Area:** shorthand. **Confidence:** confirmed.
- **Repro:** turn Fix typos as you type off, keep Spell out shorthand on, type "shd" and Space. It stays "shd"; your own shorthand
  too.
- **Evidence:** `AtlasIme.kt:1369-1372` drops the corrector when autocorrect is off; shorthand lives only inside it
  (`TypedCorrector.kt:303-307`; `Composer.kt:1446`, `:1501-1502`). Settings shows Spell out shorthand as its own live row
  (`app/settings/index.tsx:63`).
- **Fix:** run the shorthand step without the full corrector, or grey the row out with a note while autocorrect is off.

### 14. A "?" or "!" snippet start eats the sentence's mark

- **Area:** snippets. **Confidence:** confirmed. The default "/" is not affected.
- **Repro:** snippet start "?", a snippet "omw". Type "coming", then "?", then "omw" and Space: "coming on my way ". The "?" is
  gone, and Backspace gives "coming ?Omw ". "!" does the same ("Thanks! ty").
- **Evidence:** the sentence mark pulls the space and is followed by an automatic space (`Composer.kt:679-706`); `freeMark`
  (`:1956-1959`) lets `codeAt` (`Expansions.kt:88-95`) take the mark as the snippet start; `withSpaces` (`Composer.kt:2069`)
  deletes it. `TypingSettings.java:310` offers "/#!?*".
- **Fix:** let a free mark start a snippet only for ";" (the old start), never for sentence marks; add "?" and "!" cases to
  `ExpansionChecks`.

### 15. Adding or renaming onto an existing shortcut silently overwrites it

- **Area:** snippets, shorthand. **Confidence:** confirmed. Up to 4,000 characters of snippet text lost with no warning.
- **Repro:** have "/addr" with your address; add a snippet "addr" with text "x". The address is gone. Renaming "sig" to "addr" and
  adding an existing shorthand do the same.
- **Evidence:** `Snippets.kt:27-34`, `AppNativeAdapters.kt:241-252`, `TypingSettings.java:428-432`, and the edit pages
  (`snippets/edit.tsx:28-31`, `shorthand/edit.tsx:26-29`) never check for an existing key.
- **Fix:** refuse with "You already have /addr" when the key exists and isn't the one being edited; the pages already show a
  thrown message.

### 16. Steering past either end of the text keeps sending arrows

- **Area:** steering. **Confidence:** the code is confirmed; the effect needs the phone.
- **Repro:** steer right at the end of a chat box with a Send button beside it, or of a field next to another field.
- **Evidence:** `AtlasIme.kt:2647-2653` reads that nothing is next and plays CursorEnd, but always runs `command(arrow)`. An
  unhandled DPAD makes Android move focus in that direction.
- **Why it's wrong:** at the end it should be a Wall and nothing else.
- **Fix:** skip `command(arrow)` when `next?.isEmpty() == true`.

### 17. The last field's selection leaks into the next field

- **Area:** IME lifecycle. **Confidence:** confirmed that nothing resets it; how often it bites depends on when apps send the first
  selection update.
- **Repro:** in Gmail, triple-space a word in Subject, press Enter to go to the body, double-space: the cursor jumps (setSelection
  with Subject's offsets). Chrome's URL bar opens with the URL selected, but the bar doesn't show it and double space doesn't
  collapse it.
- **Evidence:** `AtlasIme.kt:106-107` `lastSelectionStart/End` are written only in `onUpdateSelection` (`:2194`). `onStartInput`
  passes `initialSelStart/End` to the composer (`:1333`) but never to these, nor clears `cursorBeforeSelect` or `selectedWord`.
  Readers: `collapseSelection` (`:2746-2756`), `confirmDoubleTap`, `nothingHere`, `shortcutFeel` (a wrong Ctrl + C No), and
  `fieldOffset`'s fallback.
- **Why it's wrong:** owner: whenever a field has a word selected, the bar shows it.
- **Fix:** in `onStartInput`, set them from `initialSelStart/End`, clear the two others, and show a starting selection.

### 18. Holding a bar word, chip or clip also plays Android's stock long-press buzz

- **Area:** haptics. **Confidence:** confirmed in code; feel on the phone.
- **Repro:** Settings > Haptics > All haptics off, phone touch feedback on. Hold a word on the bar: the menu opens with a buzz.
  With haptics on, it lands on top of MenuOpen.
- **Evidence:** `Strip.kt:441`, `ExactBar.kt:39`, `SymPanel.kt:182` and `:319` set long-click listeners that return true; only
  `AppPress.kt:20` sets `isHapticFeedbackEnabled = false`.
- **Why it's wrong:** docs/haptics.md rule 5 (no prebaked effects), and the haptics switches aren't honoured.
- **Fix:** turn the stock feedback off on those views, with a check that every long-click listener does.

### 19. An idle game keeps drawing over a selected word or clip offer that arrives mid-game

- **Area:** idle games. **Confidence:** confirmed.
- **Repro:** while a game plays, select a word with the touchscreen, or copy text in the app. The bar switches to the word or the
  clip, but the letters keep bouncing over it, and the first tap only sends them home.
- **Evidence:** `Strip.kt:1209` checks `selected` and `clip` only when a game starts. `showSelectedWord` (`AtlasIme.kt:2128`) and
  `offerClip` (`:1649`) never stop it; `Strip.kt:744` draws the game over them; `:1181-1183` takes the bar's touches while idle.
- **Fix:** stop the game in `Strip.render()` when a selection, clip or notice shows, or wake the bar from those two calls.

### 20. The guide reopens by itself long after its Accessibility step was abandoned

- **Area:** guide, setup. **Confidence:** confirmed.
- **Repro:** in the guide's setup, tap the Accessibility step, agree, then Back out of Android's page without turning it on. Close
  the guide. Later, turn TitanKeys' Accessibility on from Settings: a new guide jumps to the front.
- **Evidence:** `GuideActivity.kt:2991` sets the static `awaitingAccessibility` (:4818); only `accessibilityOn` (:4824-4828,
  called from `BackService.kt:52`) clears it, and then starts the guide.
- **Fix:** clear it in `onDestroy` and when the guide resumes from that step.

### 21. Playground WPM counts a paste, shortcuts and menu keys as typing

- **Area:** playground. **Confidence:** confirmed.
- **Repro:** in the playground, fn + V to paste a sentence: hundreds of WPM, orange, the plant and its haptic. Saving a word with
  the menu's I, or fn + C, shows "0 WPM" for 5 s.
- **Evidence:** `AtlasIme.kt:3164` publishes `Pushed` for every key before Ctrl and the menu are handled; `GuideActivity.kt:3330`
  starts a burst on it, and `:3337` → `WpmMeter.kt:41-52` counts every added character. `:3367` plays the moment.
- **Why it's wrong:** owner: the WPM starts only once a real word is being typed; the five moments come from speed.
- **Fix:** don't count modified keys or keys the menu or panel took, and don't count field growth without a letter or swipe.

### 22. One failed engine load kills swipe until a reboot; the promised Retry doesn't exist

- **Area:** swipe. **Confidence:** confirmed by trace; the trigger (low storage while models copy, a damaged asset) is rare.
- **Evidence:** `SwipeTyping.java:150-176` catches a load failure into `unavailable()` (`:215-224`), which sets "Swipe typing
  unavailable. Retry loading in settings.". Every swipe then fails (`:279`, `:308-313`) and `EngineWords.kt:24-26` drops the error,
  so swipes vanish silently, and completions go too (`:362`). `retryLoading()` (`:230`) has no caller anywhere.
- **Fix:** retry the load on the next field start after a failure (bounded), and show the failure on the bar.

### 23. Record a bug: main-thread work can freeze the keyboard

- **Area:** Record a bug. **Confidence:** confirmed that it's on the main thread; the cost needs a 3-minute recording on the phone.
- **Evidence:** `BugRecording.kt:41`, `:60`: the auto-stop runs on the main looper and `:71` joins the writer for up to 2 s
  (`BugStopReceiver` already does this off the main thread). `summary()` (`:112-138`) deletes and re-exports up to 24 MB and parses
  every line; `lib/bug.ts` refreshes it on every `atlas-state` and `atlas-window` message (each resume and settings change), and it
  rewrites the file the share sheet lent out.
- **Fix:** stop on a background thread, cache the summary at stop, export only in `share()`.

### 24. Hold Alt + Sym has no fallback for an Alt press that never arrived

- **Area:** modifiers. **Confidence:** plausible; the debug Sym log shows it ("Sym handled: panel now Symbols" while Alt is held).
- **Evidence:** `Modifiers.kt:234` checks only the held map. Shift + Backspace got a fallback to the event's META_SHIFT_ON
  (`Modifiers.kt:87`), because Key Mapper took the owner's Shift press (`ModifiersChecks.kt:229-234`). `down()` gets no meta, so the
  Sym event's META_ALT_ON is ignored.
- **Why it's wrong:** owner decision: Alt + Sym switches to the next keyboard. If Alt is swallowed like Shift was, the panel opens.
- **Fix:** pass the Sym event's meta state into `down()` and treat META_ALT_ON as a held Alt for the switch.

## Later

| # | Area | Finding | Evidence | Fix |
|---|---|---|---|---|
| 25 | Word menu | Unblock of a dictionary word blocked in Settings shows Not saved, then Saved on reopening | `AtlasIme.kt:2108-2114`, `:1788-1797` | Put it in unsaved_words on Unblock |
| 26 | News | Turning News off cancels every notification, including Record a bug's ongoing one with Stop; recording goes on | `Announcements.kt:39` `cancelAll()` | Cancel only news notifications |
| 27 | Settings | Reset most things leaves News off (borderline: is News "yours"?) | `Announcements.kt` key `announcements` in the connected store; `SettingsReset` touches only typing and haptics | Ask the owner; reset it if it's a setting |
| 28 | Contributions | Delete can race an upload in flight; sent lines are removed by count, so a cleared or trimmed queue loses newer examples | `Contributions.kt` `deleteAll`, `upload` → `removeOldest(lines.size)` | Lock upload against delete; remove by content |
| 29 | Typing | Sym "&" and "=" get spaces around them in address bars | `Composer.kt:753-759` decides with `field.words` | Use `field.markSpaces` |
| 30 | Snippets | A snippet over 2,048 characters can't be undone with Backspace | `Snippets.kt:18` MAX_TEXT 4000 vs `Composer.kt:35` CONTEXT_LENGTH 2048, `:1691` | Compare the window's tail, or cap at 2,000 |
| 31 | Guide | A slide change that lands after you leave runs `enter()`, putting the paste slide's clip in the real clipboard history | `GuideActivity.kt:3989-3992`, `:4059-4063`, `:2768`; `AtlasIme.kt:1588-1597` | Defer `enter` to `onResume` |
| 32 | Guide | The paste slide replaces the system clipboard for good | `GuideActivity.kt:2768`, `:3610` | Save and restore the user's clip |
| 33 | Idle games | A period that started with the Sym panel open never gets its roll | `AtlasIme.kt:3145`, `:988`, `:1008-1011` | Post the roll always; check when it fires |
| 34 | Idle games | `onStartInputView(restarting = true)` restarts the period and cuts a game | `AtlasIme.kt:1474-1477` | Don't wake on restarts |
| 35 | Haptics | Boundary and Release are the same pattern, 30 ms, against one meaning per pattern | `Haptics.kt:22`, `:34` | Give Release its own; check patterns are distinct |
| 36 | Look | Orange accents off: the bar's marks go white (Look's `off = ink`), the menu's effects cream | `Look.kt:17`, `Accent.kt:31`, `MenuScene.kt:74` | Cream in the dark look, as the owner's "cream everywhere" |
| 37 | Modifiers | fn as Ctrl (the default) never picks a Sym group | `AtlasIme.kt:3228` before `panelKey`'s fn at `:3471` | Try the group before Ctrl takes fn |
| 38 | Modifiers | A Backspace whose first event was dropped repeats with the last press's choice (word or forward) | `AtlasIme.kt:3388` | Clear `backspaceDoes` on key-up |
| 39 | Autocorrect | One failed autocorrect load (an OOM while engine 2.0 loads) turns autocorrect off until the process dies | `AutocorrectLoader.kt:26`, `:45`, `:52`, `:78` | Retry after a back-off |
| 40 | Battery | Contacts and app observers re-read everything on each change and bump the names revision even when nothing changed; ORT sessions spin their threads | `PhoneNameSource.kt:34-38`, `SwipeTyping.java:488-492`; `CtcReader.java:23-26`, `NextWordModel.java:57-60` | Debounce; skip unchanged; `allow_spinning=0` |
| 41 | Leaks | A phone-names thread per keyboard restart; `GuideHooks.menuOpen`/`menuCloses` keep the old keyboard; static `inkPress` keeps the old Settings activity | `PhoneNameSource.kt:28`, `:62-68`; `AtlasIme.kt:3686-3696`; `AppNativeAdapters.kt:78`, `:138`, `:174-187` | Shut down, null, clear on destroy |

Also stale: `docs/guide-failures.md:33-34` say Back on a slide asks "Leaving already?" and that a guide Android closed resumes at
its slide. Both contradict the owner's later decisions (Back exits; the guide always starts from the beginning). The code follows
the owner.

## Checked and rejected

Not bugs, after tracing: TSX and Kotlin setting keys and defaults (all 36 match); Bar text size applying without a restart; the
apostrophe policy against the study; the lone "i" when blocked; learning in password, email and private fields; undo of a fix;
swipe results landing in a new field (epoch and snapshot checks); swipes in password or exact fields; Share swipes collecting while
off, or turning on without the accept; the overlay added twice; recognition on the main thread; a batch edit left open; the debug
broadcasts, raw log and recorder in release; clipboard history keeping sensitive or password clips; ZWJ and skin-tone emoji split by
a delete; the feed's signature and replay checks; the Back service acting while off; Setup changing system settings; shorthand that
is also a real word; Smart Launcher's taken keys when the Back service is off; the space-gesture timings against the owner's 150 and
200 ms.

## What needs the phone

- **#2:** after Wipe it all, `typing_settings.xml` should be gone (`run-as … cat shared_prefs/typing_settings.xml`).
- **#1:** leave the guide on Steering after its help shows; the phone should stay silent.
- **#4:** Caps on, type physical letters: are they upper case on the Titan's key map?
- **#6, #16:** which apps keep the field after Back, and whether an arrow at a field's end moves focus (a chat's Send button).
- **#17:** whether Gmail's Enter-to-body and Chrome's URL bar send a selection update before the user acts.
- **#18:** the stock long-press buzz from the keyboard's window on this ROM.
- **#22, #23, #40:** an engine load failure under low storage; a 3-minute bug recording's stop and Settings page; CPU of the ORT
  threads and contacts-sync bursts.
- **#24:** Alt + Sym with Key Mapper on, in the debug Sym log.
- **Smart Launcher (TakenKeys):** with its search box open, lock the screen or pull down the shade and press keys. Nothing resets
  the key watch on screen off (`AtlasIme.kt:984-986` only sleeps the idle bar), so if Android doesn't end the input, hardware keys
  on the lock screen or shade could be taken into the launcher's field. Also: Enter still opens the top result, and a key never lands
  twice if the main thread is slow.
- **Typing in long WebView fields** (Gmail compose, Chrome textareas past about 1,000 characters): if the app returns less text than
  asked, `Composer.edit` (`:1297-1314`) sees every edit as a mismatch, and autocorrect, undo and the bar quietly stop.
- **WhatsApp and Teams "Enter is send":** Enter in a multi-line field is committed as "\n"; check that those apps still send.
- **The touchpad by hand:** swipe, held flick and its trail, the pause zone's height and blink, the solid double-space caret, the
  triple-space handle, steering and selecting, hover after a press on the menu and quick settings.
- **Feel:** the press tick against each menu step's beat, the playground's five moments, the guide's chips.
- **Looks:** word colours in light and dark and with Orange accents off; effects and Bigger text inside the curved corners; the
  share screen at large font.

## Tests

Run in WSL (`atlas-build`, user `dev`) on a copy of this tree. The phone suites (swipe, preview, ime-window, check:phone) were not
run.

- **Passed (34):** autocorrect, back, backup, clipboard, connected, contact, cursor, delete-hold, emoji, engine2, enter,
  expansion, first-word, fixed-scale, gesture-display, gesture-line, gesture-render, guide, guide-decor, haptics, high-touchpad,
  hold, idle, key-alignment, menu-render, modifiers, once-per-boot, panel-keys, rawlog, reference, strip-fit, touchpad-hold,
  word-menu, words.
- **`bun test tools/checks`:** 172 pass, 2 fail. Both failures are the known pair that import the `ink` package, which this copy
  has no `node_modules` for (`lib/brand.ts`, `lib/atlas.ts`: "Cannot find package 'ink'"). Not app bugs.
- The suites pass while findings 7, 8, 9, 14 and 21 are real: the checks don't cover those paths (noted in each finding).
