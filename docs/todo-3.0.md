# TitanKeys 3.0: to-do list

3.0 is the launch version, and everything in it is done before launch. 2.1.0 has been the full release since 2026-11-16, and
the 2.1 milestone is closed. The 2.1 list is [todo-2.1.md](todo-2.1.md), kept as history. Decisions and their reasons are in
[owner-decisions-2.1.md](owner-decisions-2.1.md), under "3.0". The orchestrating session keeps this list up to date, checked
against main and the workers' branches. Last checked: 2026-11-16, 01:20.

## Where the issues stand

- **Ready to build:** #65, #66, #67, #68, #69, #70 with #71, #72, #73, #74, #75, #77, #79, #81, #82, #84, #85, #86, #87, #89,
  #90, #91, #93. Each issue's questions comment and the owner's reply say what to build. The decisions log has the readings
  that needed interpreting.
- **#56, languages:** every default, with three changes from the owner.
  - 3.0 ships generic: as many languages as possible, working first, without waiting for native speakers.
  - Chinese has variants, as English has spellings. It's one engine, and you pick a variant (Simplified for mainland China,
    Traditional in Hong Kong or Taiwan style...), each with its own tailoring and dictionary.
  - The variant is one setting for every Chinese input method.
- **Studies first:** #83 (joined swipes), #73's were/we're, #56's sources and Rime.
- **Waiting for the languages work:** #76 (the label), #80 (American first).
- **Monitoring:** #92, #94, #95.
- **Closed 2026-11-16:**
  - Shipped in 2.1.0: #26, #52, #54, #59, #60.
  - Done: #55, #57.
  - Left as it is: #78.
- **Moved to 3.0:** #34, #36. Parked with no milestone: #33, #48, #51.

## Tonight's run (M1, started 01:25 on the owner's go)

- **Workers,** all Opus 5.5 at medium effort, each in its own worktree branched from main at 817b878:
  - tk-typing: #91, #77, #73, #85.
  - tk-gestures: #89, #93, #84.
  - tk-screens: #71 with #70, #86, #87's row, #75.
  - tk-data: #65, #74.
  - tk-guide: #68, #69, #67, #66 with "steer" out, #72, #87's step, #79.
  - tk-tools: #82.
  - tk-languages: the #56 study.
  - tk-joins: the #83 study.
  - F writes `docs/words-m1.md`.
- **Phone:** reached at 100.98.104.89:5555 again (the owner sent Wireless debugging's port at 01:20, after a restart). At 01:25 it was at 2% and not charging.
- **07:30:** a background timer resets the battery stats, with release TitanKeys as the keyboard, then logs to the scratchpad.
- **Analytics** is #96, with questions for the owner.
- **Merged into main at 2d54b0d, 02:40:**
  - screens: #70, #71, #75, #86, #87's row;
  - data: #65, #74, #97;
  - tools: #82;
  - the #83 study.

  `bun run check` passes (222 tests) and the Debug build passes. #97's report route is deployed to the contributions Worker,
  and the routes answer correctly. Phone checks run through tk-screens, now the phone tester, on
  `~/build/m1-apks/main-2d54b0d-debug.apk`.
- **M1 gate report (03:35):**
  - **Merged into main:** every M1 code branch.
    - typing: #91, #77, #73, #85;
    - gestures: #89, #93, #84;
    - screens: #70, #71, #75, #86, #87's row;
    - data: #65, #74, #97;
    - guide: #66, #67, #68, #69, #72, #79, #87's step, and the owner's 01:52 notes;
    - tools: #82.

    Each branch's verify build passed. The full suite on merged main is running.
  - **Studies merged:**
    - #83, joins;
    - #73, were/we're: yes at a 2.0 nats margin, 0 wrong changes and 13 of 15 fixed, on a small sample;
    - #56, languages: 43 languages from data alone, about 160 MB with Chinese, all inside the app; Yale is rime-jyutping's,
      LGPL, so it ships; Rime and OpenCC built for arm64; the variants convert correctly.
  - **Deployed:** #97's report route on the contributions Worker.
  - **Not done:**
    - every phone check: the phone is locked;
    - #81's code rename, now done first thing in M2;
    - the Play bundle, because the private Ink in WSL lacks the TitanKeys patch, and fixing it would wipe its own
      bundle patch. That's for the owner, since only the owner makes Play builds.
  - **Waiting for the owner's hands:** #89, #93 and #84 by touch; #66's distance; #68's hunts; #72's Sym check; the panel
    page lines while paging.
- **main at 58ce707 (03:50) has all of M1 and #83's rule.** It passes `bun run check` (222), all 41 test suites, the join
  checks and the Debug build. The phone checklist for the evening, or whenever the phone is free, is in the Orchestrator's
  scratchpad, `m1-phone-checks.md`.
- **M2 merged into main at b6d042d (04:35):**
  - F's words pass: every M1 draft replaced, and the overflowing guide lines shortened.
  - #90:
    - a flat thumb with steps 1, 2, HOLD replaces the ghost finger;
    - misses say "That's X, not Y";
    - one action per key step;
    - a far swipe is shown against its word's path at 1.2 keys;
    - slides and misses go in the raw log.
  - were/we're: "were" becomes "we're" above 2.0 nats (3.0 with Only fix when sure). It makes no wrong change on any test
    set, including Tatoeba's counts, and Backspace puts it back.
  - The conflict between F's blocked-line wording and #90's "That's X, not Y" lines went to #90's, since the pattern is
    the owner's. F polishes #90's new lines after the rename.
  - Tests pass: check, guide, guide-decor, gesture-line, gesture-render, word-menu, back, key-alignment, autocorrect,
    words, and the build.
- **#81's code rename is on main at 161e0bc (05:15).**
  - It covers `com.titankeys.*`, `TitanKeysIme`, `lib/titankeys.ts` and the native channel, plus a one-time quiet
    notification after the update if the keyboard or Back is off.
  - `bun run check` (228) and all 41 suites pass, and the Debug build is
    `~/build/m1-apks/main-rename-debug.apk`.
  - Not yet run: the phone update test.
- **main at 4f3278f (06:05) has the language framework,** with 36 input methods:
  - Latin script: es fr de pt it nl pl tr sv da nb nn fi cs sk ro hu id hr sl lv lt ca gl et is af eo az ga eu;
  - national layouts: ru uk be bg el.

  It brings Settings > Languages with American spelling first (#80), one keyboard-list entry per input method (#76), the
  held-Sym overlay, accents on hold shown on the bar, and the engine interface and choice picker for Rime. Also the
  review's follow-up fixes and the setup-loop fix.
  - `bun run check` (242) and all 41 suites pass. The Debug build is 135 MB.
  - The build needed a clean Ink folder: Gradle's incremental build kept stale classes across the rename. The shared
    verify copy (`~/build/tk-overnight/.ink`) may need the same once it's free.
  - Skipped languages: Serbian Cyrillic (layout mapping), Serbian Latin, Macedonian and Georgian (licence unclear), Bosnian
    (list didn't parse), Kazakh (no Android layout).
- **#85's baseline day started at 07:30:13:** battery stats reset, release TitanKeys the keyboard, the phone at 100% and
  unplugged. The phone is left alone until the owner says the day is done, or 21:00.
- **Merged into main at 07:28: Rime, and the join-risk fix.** `bun run check` (248), all 42 suites, the join checks and a
  clean Debug build pass. The Debug APK is 156 MB (135 MB before Rime): `~/build/m1-apks/main-m2-rime-debug.apk`.
  - **Rime** (`claude/m2-rime`, tk-gestures): Cantonese Yale and Jyutping, Mandarin Pinyin, the three variants, on the
    renamed packages (`com.titankeys.rime`), with a reproducible 3.1 MB library. Rime's learned words are in the backup
    (#65), and the bar says "Getting 粵語耶魯 ready…" if a Chinese input method takes over half a second to start. 39
    input methods in all.
  - **#83's review, risk 2** (`claude/m1-joins-risks`, tk-joins): a quick touch landing moving mid-pad no longer joins by
    its shape. 5 of the 9 pairs it would join were kept by the owner as two words, mostly two short words. Those touches go
    back to the 150 ms check of both readings, as before #83. Missed joins found by shape: 0 of 18 (was 4); kept-apart
    pairs joined: 0 of 1,120.
  - **Risk 1, the 150 ms check:** unchanged. The 9 November phone replay shows it glued none of the 11 separate-word pairs,
    and every stricter version tried loses 2 of its 8 fixes.
- **More of the Orchestrator's calls, to confirm (07:28):**
  - A restored Rime snapshot waits until its input method is next used, then merges.
  - "Getting … ready" shows after 500 ms, every time it's slow.
  - Rime's learned words merge Rime's way on restore, adding counts from both sides; the other lists take the file's.
  - A backup syncs Rime first, which drops any half-typed Chinese code at that moment.
  - The 4 missed joins are left to the 150 ms check, not joined outright, and the check gets no extra lead, so no right join
    is lost.
- **M3: the six skipped languages on main** (`claude/m3-languages`, tk-languages): Serbian Cyrillic (СР) and Latin (SR),
  Macedonian, Georgian, Bosnian and Kazakh, each from a source with a clear licence. 45 input methods. `bun run check`
  (248), all 42 suites, the join checks and the build pass; the Debug APK is 159 MB. tk-languages' calls, to confirm:
  - Serbian Cyrillic's label is СР and Serbian Latin's is SR.
  - Serbian puts ж on Z instead of ѕ (not a Serbian letter); Macedonian holds ж on P.
  - Kazakh uses Russian's keys, with its nine extra letters held on the nearest Russian letter (і on ы).
  - Georgian's Shift gives a key's own second letter, and Georgian has no sentence capitals.
  - Bosnian's and Macedonian's word lists come from film subtitles, so they lean towards everyday speech.
- **Fixes from the phone checks on main (4658247), all suites and a clean build pass (258), APK
  `~/build/m1-apks/main-m3-fixes-debug.apk`; phone re-tests next:**
  - #74: the crash offer shows on top, wherever the app opens (tk-data).
  - Reset literally everything starts as a fresh install, the guide, Back's disclosure and release notes included, with a
    check listing everything a fresh install doesn't have (tk-data).
  - #56: a restored or Settings-chosen input method is the one that types; Android's keyboard-list choice wins only when it
    changed since the keyboard last set it (tk-languages). Calls: a switch in Android's list made while TitanKeys wasn't
    running still wins; the new `keyboard_list` store stays out of backups.
  - #85: a static in the guide held the last app screen after it closed; it's cleared now. A Debug-only leak check
    (`am broadcast -n com.titankeys.debug/com.titankeys.keyboard.LeakCheck`) lists any guide or playground still in memory
    after a forced collection (tk-typing). Call: its weak list of guides is in every build, costing almost nothing.
  - Still coming: #97's overlap is an Ink bug (a focused field's text isn't clipped under a pinned header, on every Ink
    screen); tk-data is patching Ink. #66's title and #87's App info (tk-guide). Chinese prisms, ranking and 正體
    (tk-gestures).
- **Phone checks, section 1 (tk-screens, Debug from 95f2778), judged by the Orchestrator:**
  - **Closed, proven by an objective test:** #70, #71, #75, #77, #86, #91, #76, #80, #65. #67 was closed, then reopened:
    its triple Space slide wasn't checked.
  - **Open with a status comment:** #73 (swiped words), #97 (Record a bug's Stop; a text overlap), #87 (App info opens
    after a second denial), #74 (the crash offer hides behind the playground), #79, #69, #72, #90 and #66 (the owner's
    judgement or hands; #66's title also loses its last word), #81 (the notice: Debug's notifications are off; the folder).
  - **Fixes assigned:** tk-guide (`claude/m3-guide-fixes`): #66's cut title at the owner's largest font, #87's App info.
    tk-data (`claude/m3-data-fixes`): #74's hidden offer, #97's overlap, and Reset literally everything leaving setup state
    behind (it lands on the playground, not Setup).
  - **Noted:** the guide's paste slide writes the real clipboard, which the owner's clipboard-sync app picked up. Debug's
    contacts permission is now denied, from the #87 test.
- **#85's baseline ended at 08:42 on the owner's word** ("my phone is ready now"): 1 h 12 min on battery from the 07:30
  reset, 100% to 89%, 431 mAh drained with the screen on 62 min. Release TitanKeys (2.1, uid 10335): 2.83 mAh, the vibrator
  891 times (19 s), its foreground service the whole screen-on time. The screen-off phone runs (07:55–08:09) fall inside it.
  Saved: the Orchestrator's scratchpad, `baseline/batterystats-baseline-end.*`. Short, but it's what the day gave; the "after"
  comes with the 3.0 pre-release.
- **The Rime test program on the phone (08:45, Wi-Fi):** the three variants convert right (軟件/軟體, 打印機/印表機,
  Simplified throughout), 24 lookups in 3 s. Findings, with tk-gestures (`claude/m3-chinese-2`): Rime compiled its own prisms
  instead of using the shipped ones; Yale ranks 月餘 before 粵語 and 我地 before 我哋; 対 shows as a sixth choice.
- **#73: the apostrophe fixes apply to swiped words too** (`claude/m3-apostrophe-swipes`, tk-typing, on the owner's word).
  On the saved log: 18 swiped words the fix looks at, 13 scorable, 1 right change and 0 wrong at every margin from 0 to 3;
  the highest score for a word that should stay was −8.4. A small sample, and no swiped "were" in it. All suites and a clean
  build pass. tk-typing's calls, to confirm:
  - Swipes use the same margins as typed words (1.0; 2.0 with Only fix when sure; "were" +1.0).
  - The bar's other-form offer stays in typing only.
  - A swipe that goes on into a longer word is read again, and the old word stops waiting.
  - For F: `docs/paper.md` §6.4 still says the fix never turns "were" into "we're".
- **M3: Korean and Vietnamese on main** (`claude/m3-korean-vietnamese`, tk-languages): Korean as a Hangul engine on the
  2-set layout, Vietnamese as a Telex engine with its 11k word list. 47 input methods. All suites and a clean build pass:
  `~/build/m1-apks/main-m3-kv-debug.apk`. Also fixed: a capital typed mid-code dropped Rime's code. Thai waits: about 27%
  of its typing would be holds without a third layer of keys. tk-languages' calls, to confirm:
  - Korean's label is 한, and Korean has no word list (none with a clear licence), so no suggestions.
  - In Korean, Enter writes the syllable and then does Enter.
  - Telex puts the tone in the older style (hòa, thúy).
  - Telex's choices are picked with the finger; Space writes the word as typed.
- **M3: the Chinese page opens on the first switch** (`claude/m3-chinese`, tk-gestures): the first switch to a Chinese input
  method with no variant picked opens Settings > Chinese characters, once. All suites pass; the build needed a clean Ink
  folder again (stale classes), then passed: `~/build/m1-apks/main-m3-chinese-debug.apk`. tk-gestures' calls, to confirm:
  - Once ever, not once per Chinese input method. After that, the bar's choice is the only reminder.
  - It opens once the input method has loaded, so on the very first switch "Warming up …" comes first.
  - Reset most things clears the variant and the once, so the page opens again on the next switch.
  - Open question: should turning Chinese on in Settings > Languages also open the page? Today it doesn't.
- **Screen-off phone runs at the owner's word (about 07:55 to 08:09, on mobile data, battery 95% to 94%):** these fall in
  #85's baseline day, so allow for them when the stats are read.
  - **#83's phone replay** (main with the join-risk fix; `~/build/joins83/replay-report.json`): of 645 pairs, the first word
    comes out right in 314 now, against 308 before #83 (+6), and 311 with a stricter lead of 2. 515 join now, 525 before.
  - **The 150 ms check,** on pairs only it decides: it joins 6 missed joins, 5 right joins, 1 wrong join, 5 unclear and 20
    others, and glues 2 of the pairs the owner kept apart. That's the same before #83, now and with a lead of 2: a stricter
    lead loses 2 missed joins and stops neither glue.
  - **The swipe suite's phone part** (saved words, and context): passes.
  - **Fixed:** `phone-replay.sh` pointed at the old `.dev` replay tools, which don't follow the rename; it uses the repo's
    copy now.
- **New pilot study, #83 (owner, 2026-11-16 morning):** "do a pilot investigation to see whether the context engine could
  predict whether the words should be joined or not, when they end on the top row and resume on the top row". With tk-joins,
  branch `claude/m3-joins-context`: the context model's margin for the joined word against the two words, on the saved
  top-row pairs, by label, alone and with the geometry. Counts only, no app code, no phone.
- **F's words pass 3 on main at c508c31:** every "F: draft" in the language framework, Chinese, the licences and the report
  form is replaced (`docs/words-m1.md` has each line before and after). F's calls, to confirm:
  - The Yale input method is named 耶魯拼音, with "Cantonese, Yale" under it.
  - The licences and the README credit 幸延 for the Yale rules, as `native/rime/SOURCES.md` does.
  - Not yet seen on the phone: whether "Simplified or Traditional? Settings." fits the bar.
- **Review of M1 and M2 (05:50), merged at 3084dd0 with the Worker redeployed.** Seven bugs fixed, each with a test:
  - a flick took two words in web fields, whose spaces are non-breaking;
  - reports over 20 parts couldn't upload;
  - one abandoned report blocked deleting the rest;
  - a resent part kept its old bytes in the server's count;
  - a damaged snippets list broke a restore;
  - the Contacts switch could be lost on restore;
  - a contact in capitals beat the dictionary's acronym.

  Findings now being fixed:
  - a front-page loop for 2.1.0 users with contacts unanswered (tk-guide);
  - two quick-swipe join risks, checked against the logs first (tk-joins);
  - restore stopping after the words;
  - saving a backup over an older file;
  - the flick's read and delete not in one batch edit;
  - the recording switch being opt-out (tk-data).
- **More of the Orchestrator's calls, to confirm:**
  - The contacts question and the Sym/fn part show only in the guide's own setup, never in the playground or from the
    front page. Settings > Setup has them.
  - In the report form, the recording is on only when the form comes straight from Record a bug's Stop; otherwise it's
    off. The crash file is always attached: class names and lines only.
- **In flight at 05:15:**
  - tk-languages merges the renamed main into the language framework and finishes Spanish, French, German and more.
  - tk-gestures then merges Rime on top (`claude/m2-rime`: librime and OpenCC, 3.1 MB, reproducible; Yale, Jyutping,
    Pinyin; the variants), rebuilds its library for the new names, and adds Rime's learned words to the backup and a
    "getting ready" state.
  - F polishes #90's lines and writes #81's notice and notes.
  - tk-tools reviews M1 and M2 on main for correctness bugs: deleting too much, data loss, privacy, typing, joins, the
    front page.
- **Next (done):** the #81 rename, brought up to date with main by tk-data. Then the language framework (tk-languages) and Rime
  (tk-gestures) merge the renamed main into their branches.
- **M2, started 03:50, on code that doesn't need the phone:**
  - tk-data: #81's code rename first, then each worker merges main in.
  - tk-languages: the language framework, generic Latin languages, Settings > Languages with English spelling inside
    (#80), and one keyboard-list entry per input method (#76).
  - tk-gestures: Rime in the app (librime and OpenCC, Yale, Jyutping, Pinyin, the variants).
  - tk-guide: #90.
  - tk-typing: were/we're on more data, then the auto-change if it still makes no wrong change.
  - F: the words pass on main (every `F:` draft, the three overflowing guide lines, "steer" outside the guide, and
    CONTRIBUTING's Languages section).
  - tk-joins: #83's rule, in progress.
- **The phone locked at about 02:03** (power button). It's behind a PIN, so the phone checks wait for the owner to unlock
  it. They're queued with tk-screens, the phone tester. It charged to 77% by 02:57.
- **gestures merged at 03:00:** #89, #93, #84. The owner's #84 recording showed nothing that tells resting from lifting
  (12 of 17 presses seen, lift 123–136 ms after key-up at the same size in both halves), so rule (a) stands.
- **Started on M2 work that doesn't need the phone:**
  - tk-joins builds #83's two safe rules: join more (+4 missed), and join less by touch size (−7 wrong episodes, no
    word changed).
  - F writes the lines for #65, #97, the privacy policy and #75 and #86, then does the words pass once M1 is merged.
- **#83's study says:**
  - 502 joins: 380 right, 64 wrong (46 episodes), 58 unclear; and 18 missed joins.
  - No rule cuts wrong joins without losing a right one.
  - Recommended: join less when the second touch comes over 150 ms later after a clear word or a full-size touch (−12
    wrong episodes, −4 right joins); join more for a touch that lands still moving, within 150 ms and 2 keys (+4 missed
    joins, nothing kept apart broken).
  - Next: a phone replay of every pair, both ways.
- **Owner's answers at 01:40:**
  - Yale input comes first for Chinese, from the owner's Weasel schema; the files are in WSL `~/build/lang/owner-rime/`.
  - No default Chinese variant: the user chooses.
  - #84's rest-and-lift recording was pulled for tk-gestures.
  - #97: a bug and feedback form sent to the server, with email as the backup. tk-data builds it, deploy approved.
  - Port 5555 is fixed until the next restart.

## Milestones (internal; the owner gave the go at 01:20)

Each milestone has a gate: a list of what must be true before the next one starts. At each gate the Orchestrator writes a
report here: what passed, what's waiting for the owner's hands, and every call the Orchestrator made. Workers run as separate
sessions, each in its own worktree on its own files. The Orchestrator reviews and merges, builds, and does every phone check
itself, one at a time.

### M1: fixes and groundwork (tonight)

Started by the go, starting with the folder move. Workers all start at once:

| Worker | Work |
| --- | --- |
| F | Every line M1 needs: #66's new line without "steer", #67, #72's step, warning and slide line, #75, #79, #86's sample line, #87's step, #74's mail texts |
| Typing | #91, #77, #73's its/it's, #85's battery and leak fixes, and the were/we're study |
| Gestures and hover | #89, #93, #84's rule (a) |
| Screens | #71 with #70, #86, #87's row in Settings > Setup |
| Data | #65 backup, #74 feedback by email and the crash offer |
| Guide | #68, #69, #67, #66, #72, #75, #79, #87's step, with F's words |
| Tools | #82, the dev tools into the repo |
| Engine study | #83 |
| Language study | #56: sources, licences and size per language; Rime and OpenCC built for Android, with the Chinese variants |

The #81 code rename comes last, once the branches above are merged.

**M1's gate:**
- Every item above is merged on main.
- `bun run check`, the JVM tests and each area's test script pass, and `verify.sh build` passes.
- A Debug build from main is on the phone, and each issue's own adb check is done, with screenshots where it asks for them.
- The three study reports are on #83, #73 and #56.
- The folder is X:\TitanKeys, and a build passes from there. The WSL distro is `titankeys-build`, and a build passes in it.
- The code rename is on main and its checks pass. Its update test on the phone waits for the evening.
- What only the owner's hands can check is listed, not waited for: #89, #93, #84, #66, #68, #72.

### M2: the guide and languages

- #90, the guide for first-timers.
- The language framework:
  - the switch overlay on a held Sym;
  - the picker on the bar, with the word menu's hover;
  - Settings > Languages, with the Chinese variant page;
  - one keyboard-list entry per input method, which brings #76.
- Generic languages: the first set from the study's data, each passing one shared check of its files.
- Rime in the app:
  - Cantonese: Yale if rime-cantonese has it, and Jyutping.
  - Mandarin: Pinyin.
  - The Chinese variants.
- What the studies turn into code: #83's join rule, and were/we're if the study says yes.

**M2's gate:** merged and checked as for M1. Typed codes give the right characters in each variant on the phone over adb, and
each generic language types its letters and accents over adb.

### M3: breadth, polish and the pre-release

- More languages: Cyrillic and Greek, and whichever need code (Korean, Vietnamese...), as the study ranks them.
- Chinese polish.
- Fixes from the owner's hands checks.
- #80 with the new language menu.
- The website (#58), brought up to 3.0.
- Analytics, after the owner's answers.
- The 3.0 pre-release build. Signing and publishing stay with the owner.

**M3's gate:** everything in 3.0 merged and checked, the owner's hands checks passed, and a pre-release candidate built.

### Tonight's limits

- The phone is free until 07:30. Then a timer resets the battery stats for #85's baseline day. Release TitanKeys is the
  keyboard, and the phone is left alone until the owner says the day is done, or until 21:00 if no word comes. Work that
  doesn't need the phone carries on.
- What waits for the evening: the rename's update test, and any phone check that didn't fit before 07:30.

## The studies' scope

- **#83, joined swipes:**
  - Every join in the saved logs (.dev/swipe-baseline) is labelled right, wrong or missed, from what was kept or deleted after
    it.
  - What separates them: the gap, where one ends and the next starts, direction, speed, the shape, the word scores.
  - New join rules are tried, including joins missed today, and each is replayed on every saved log.
  - The report gives right joins kept, wrong joins cut, missed joins found, and the headline numbers, which must not drop.
  - No app code. Engine 2.0 stays off the phone.
- **#73, were/we're:**
  - Every were/we're in the saved logs' text and in an English sample is run through the context model TitanKeys already
    uses.
  - The model's choice and its certainty are measured, and a threshold is sought with no wrong change.
  - The report gives how many it would fix, and a yes or no.
- **#56, generic languages:**
  - Per language: where its letters, accents and word lists come from (CLDR, Hunspell, open keyboard dictionaries, word
    frequency lists), each source's licence, and its size.
  - How its letters sit on the Titan's QWERTY keys.
  - Which languages work from data alone, and which need code.
  - What N languages add to the app, against Google Play's 200 MB.
- **#56, Chinese:**
  - librime and OpenCC built for Android arm64 the way Trime does it.
  - The schemas compiled: Yale if rime-cantonese has it, Jyutping, Pinyin.
  - The variants: each region's OpenCC conversion and word lists.
  - The sizes measured.
  - A small test program on the phone over adb that turns a typed code into characters in each variant, removed afterwards.
  - Every licence noted.
- **Output:** docs/languages.md updated with the findings and the framework's design. No app code in M1.

## The Orchestrator's calls, waiting for the owner's confirmation

1. The milestones live here, not on GitHub.
2. In M1 the code rename comes last, so the workers start at once and the phone checks fit before 07:30.
3. `atlas-build` stays until the owner has seen `titankeys-build` work.
4. **The Chinese variants for 3.0:**
   - Simplified (mainland China), Traditional (Hong Kong) and Traditional (Taiwan).
   - Each uses OpenCC's conversion for its region, and its own word list where Rime has one.
   - Macau uses Hong Kong. Singapore and Malaysia use Simplified.
   - No default: the user chooses (owner, 2026-11-16). Confirmed.
   - It's one page in Settings > Languages, like English spelling.
5. The baseline day runs from 07:30 until the owner says it's done, or 21:00, and the stats are read then.
6. The study reports go on their issues as comments.
7. #66's target is the word that takes "steer"'s place in F's new line, about one word left of the cursor.
8. Analytics gets an issue with questions. No analytics code until it's answered.
9. Issues are closed once merged and checked on the phone. Those waiting for the owner's hands stay open, with a comment saying
   so.

10. The folder move (#81) waits for the owner's next presence. It needs a message from the owner halfway, and workers
    inside X:\Atlas would break.
11. The #83 study goes to a new worker in auto mode, not the old Swiping engine session, so it can't stall on a permission
    prompt overnight.
12. The owner's go is taken as clearing the downloads and the phone test program listed under "Needs clearing".
13. The workers draft user-facing text with `F:` markers. F's lines in `docs/words-m1.md` replace the drafts, either in the
    workers' branches or after merging.

14. The #81 code rename runs at the start of M2, in parallel with the M2 workers. Each worker merges the renamed main into
    their branch before reporting, since they know their own changes best.
15. "Steer" also goes from Settings > Haptics, the Haptics lab and the README, for one word throughout. The published 2.1.0
    release notes stay as they are.
16. were/we're changes automatically only if a larger test set still gives no wrong change at the study's margin.
17. Every language from the study ships inside the app; none is downloaded (43 Latin, Cyrillic and Greek languages plus
    Chinese, about 160 MB in all).

More calls get added here as the night goes.

## For the owner in the morning

- **were/we're's bigger test set needs your yes in its own session:**
  - The typing worker (tk-typing, branch `claude/m2-were`) can't download Tatoeba's English sentences without the owner's
    own yes in that session; a go from the Orchestrator doesn't count.
  - It's using what's on the machine instead: the triples at every position, Tatoeba's held-out file, and the owner's
    judged cases.
  - To widen it, say yes to the download in that session.

- **Your personal email address was public in the repo:** wrangler's cache file
  `server/contributions/.wrangler/cache/wrangler-account.json` held it in the Cloudflare account's name, from commit 54473ca
  (2026-11-10). It's removed from main (c842d50), and `.wrangler/` is ignored now, but it's still in the git history.
  Taking it out of history means rewriting main and force-pushing, which only you can decide.

## Needs clearing before the go

- **Downloads** into the WSL build machine:
  - librime and its libraries (Boost, LevelDB, marisa, yaml-cpp, glog), and OpenCC.
  - The Rime schemas: rime-cantonese, rime-luna-pinyin.
  - Trime's build files, as a reference.
  - CLDR data, Hunspell dictionaries, open word-frequency lists.
  - The Android NDK if it isn't installed.
  - All from their official repositories.
- **A test program on the phone:** run over adb from /data/local/tmp, then deleted.
- **After the rename:** Debug's keyboard is turned back on over adb (`ime enable`). Debug's Accessibility stays off until the
  owner turns it on; no secure settings are edited over adb.
- **Already allowed, for the record:**
  - Debug installs, and switching the keyboard to Debug and back.
  - adb typing only in TitanKeys' own fields, and cropped screenshots.
  - Pushing to main, issue comments, and closing done issues.
  - The WSL distro export and import.
  - The 07:30 battery reset.

## Later, after the languages work on the phone (owner, 2026-11-16)

- **Refine the languages** once the owner has seen them working: a context model like English's next-word model to help
  autocorrect and suggestions in other languages (maybe in 3.0), and other optimizing.
- **Download on demand** stays a future option, only if the app's size ever passes Play's limit.

## Blocked, or not the Orchestrator's to finish

- **Waiting on others:**
  - #36: Google Play waits for the D-U-N-S number.
  - #42: the Play migration notice waits for #36.
- **Needs the owner:**
  - #34: the deletion and retention check, with a throwaway contributor and the server's export.
  - #58: the owner's final review.
  - #84's signal search: a Record a bug of rest-and-lift presses.
  - #83's fix: new swiping data. The full typing log is only in TitanKeys Debug.
  - Hands checks, and one run with someone who has never held a Titan.
- **Not yet possible:** #85's after numbers come only with the 3.0 pre-release.
- **Monitoring:** #92, #94, #95.
- **Decisions coming:**
  - Analytics.
  - The app's size once many languages are in.
  - Whether Japanese, right-to-left and Indic scripts are in 3.0.
