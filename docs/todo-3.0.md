# TitanKeys 3.0: to-do list

3.0 is the launch version, and everything in it is done before launch. 2.1.0 has been the full release since 2026-11-16, and
the 2.1 milestone is closed. The 2.1 list is [todo-2.1.md](todo-2.1.md), kept as history. Decisions and their reasons are in
[owner-decisions-2.1.md](owner-decisions-2.1.md), under "3.0". The orchestrating session keeps this list up to date, checked
against main and the workers' branches. Last checked: 2026-11-16, 01:20.

## Launch state (2026-11-21, night)

- **Candidate:** 92d23328, on the owner's phone (switcher labels full size, Back after an app's own hide, the disclosure open by default). The owner daily-drives it before the release.
- **Pushed with `[skip ci]`, release held** (owner: "do the github bookkeeping but hold the release"). The release starts from Actions > App release > Run workflow, with Publish ticked.
- **Play:** waiting on identity verification. The store graphics are in .dev/store/play-store-graphics/final. The r/unihertz post and launch plan are in .dev/private.
- **Website on its own domain (2026-11-21):** everyones-niche.com is the Everyone's Niche landing page (repo jasonlok000.github.io); TitanKeys is at everyones-niche.com/titankeys/, privacy at /titankeys/privacy/ (GitHub Pages, HTTPS enforced, domain verified on GitHub; GoDaddy DNS: four A records, www CNAME, the challenge TXT; Zoho mail untouched). jasonlok000.github.io/titankeys/* redirects there, so 3.0's built-in feed and privacy links keep working. Play's privacy URL is the new one; 3.1 moves `Connected.SITE` and `PRIVACY_URL` in the app.
- **Into 3.0 (2026-11-22, "fix it with the 3.1 fixes but keep it 3.0"):** the guide's Space-gesture boxes stay lit and the thumb slides while held; the switcher in the owner's 2/3/4 layout (#101); the app's links on everyones-niche.com/titankeys. A new candidate, so the 3-day daily drive starts again.
- **Known for later:** #102 (Back in YouTube search and ChatGPT, shipped as is), #100 (Korean ready, drop its experimental mark).

## The 3.0 candidate (2026-11-20)

- Done and on the phone for the owner's 1–2 days: switcher over Q to O, the key audit's five fixes, the Hindi full stop,
  the support page's text, top band, fades, drawing mode and flat pay button, anonymous supporters off the board.
- Waiting: the frame-by-frame pass over every support screen on the shipping build (needs the phone); a focused scripted
  pass over what changed since the night run; the overnight Back log (on the phone in /data/local/tmp/tk-night) read in the
  morning; the unsigned release rebuilt from the final commit. Then the owner signs, pre-releases and daily-drives it.
- Tell F: the support text changes above (docs/support-text.md).

### The night log read (2026-11-20, 16:49–23:18; deleted after reading)

- **Back:** all 100 physical presses reached the system, each with Key Mapper's replay. Reddit takes the first press for its
  own back step and leaves on the second: Reddit's, not TitanKeys' or Key Mapper's. With the keyboard shown, one press hides
  it and the app's back follows about 0.3 s later (by design). One Maps case: the app hid the keyboard itself, Back went to
  Maps, which restarted its field, and the bar showed again. Left as it is for 3.0, noted here.
- **Home:** all 91 arrived. No input device has a Home key: Key Mapper makes it. Tonight's log (tk-night2) records the
  non-letter keys of event5 and event7, so a missed Home shows.
- **Double letter:** not caught (the C test on the home screen delivered each C once). Debug's key audit now says when a key
  goes to an app with no field, when a field starts soon after, and when the keyboard types that same key again.
- **Mistaps:** haptics weren't logged. Debug now logs each played haptic's id (TitanKeysHaptic), never text.
- **Crash:** the support page crashed opening once (11:21, DecorView null under the insets controller): fixed (1eef3538).
- **Support page blank:** reproduced 6 opens in 15. The page's first request reached the main thread before onPageStarted
  and was dropped, so no screen ever showed. Early messages now wait for the page, and no screen waits for an answer for
  good (40595ac2, ef0753c7). The "tile memory limits exceeded" lines at each open are a side effect, not the cause.
- **Candidate:** ef0753c7 on the phone 00:01. Overnight (01:05–01:20, owner asleep, Stay awake on): support page blank 0 of
  15 opens (was 6 of 15); swipe recognition, swipe preview, keyboard show and hide, app screens PASS; the touch simulator
  stopped at its start (it needs the release TitanKeys as the keyboard, a secure setting left alone), not run. Fix earlier
  words at Small, in the practice field: its going → It's going, get dome food → get some food, their going stays.
  Not done: the frame-by-frame pass over every support screen.

### The second 3.0.0 candidate (2026-11-21, evening)

- Built from 4be9d058, every verify suite passing, signed with the NightKeys key: `com.titankeys` 3.0.0 (59), sha256
  e94cf5ab…a5fa, md5 11fcd3ca70e62cbf384c989653ac95f5. Copy in .dev/prerelease. Installed 17:24 over the first candidate; the
  release is the owner's keyboard. The 3-day clock starts again with it.
- New since the first: the guide's cursor slides (the demo slides, every tap box lit while held, "slide" as the hold registers,
  Next not clipped); Alt, Shift and Ctrl over Chinese's row; the support page's pot line; Keep swiping after a short lift removed;
  Back at the top of Settings goes to the playground (Ink patch hook appBackAtRoot).
- The dev setup came off the phone (owner's go): TitanKeys Debug, the test launcher and the reference app uninstalled, the night
  log and its loggers stopped and deleted, the test files and recordings removed. The scripts and how to bring Debug back are in
  .dev/phone-tools; Debug APKs stay in WSL's ~/tk-out.
- WSL's cap is 6 GB now (.wslconfig): at 8 GB builds ran Windows out of commit space. A PC that has run long can hold ~30 GB of
  commit outside any program; a restart clears it.

### The 3.0.0 pre-release (2026-11-21)

- Built from cbd694cc (tests caught up in 0672f945, same keyboard code); every suite of verify.sh passes. Signed with the
  NightKeys key (owner's go), `com.titankeys` 3.0.0 (59), 277.6 MB, sha256 aeb6e19d…bda7, md5 1ca5db51ec5427ecf8ea2498060c4684.
  Copy in X:\Atlas\.dev\prerelease. Installed 15:23 as a new app (no release TitanKeys was on the phone); the owner turns it on
  (keyboard and Accessibility) and daily-drives it 3 days. Not published.
- Today's fixes in it: Back hides only with the keyboard up and always reaches the app otherwise; Key Mapper's replay of a
  handled Back is kept; a Chinese code clears with Back as with Sym; Chinese never shows the three-word bar; Smart Launcher's
  Back hides the keyboard; the support page's blank opens and its crash; the boring guide's Fix earlier words.
- Firmware baseline before the Unihertz update: X:\Atlas\.devirmwareefore-2026-11-21 (V02.00.04, Android 16). After the
  update: the same snapshot, a diff, and the phone suites again.
- Play: account, signing key, declaration video and the 14-day test are the owner's; an internal-testing track for the owner
  alone can start once the account exists (the only way the support page's purchases run).

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
      bundle patch. Fixed 2026-11-21: play-bundle.sh gets its own Ink with both patches (ink.sh), and a `--local`
      3.0.0 bundle (code 60) builds and passes the 16 KB checks (docs/play/build-tasks.md). The upload key is the owner's.
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
- **Phone re-tests before the owner's pause (tk-screens, Debug from 4658247):**
  - #74: YES, the crash offer shows on top of the playground within 3 s. Closed.
  - Reset literally everything: YES, it leads to Quick setup and Setup, as a fresh install does.
  - Language restore: NO. After a restore with Russian current, the bar still showed English words. Back with
    tk-languages, with a Debug log line to prove the choice next time.
  - `home-focus.py`: NO, an app finding: text typed in the playground is gone after Home and back (6 of 6). With tk-guide,
    to check against the owner's decisions first.
  - `check:phone`: about.sh and feedback.sh pass (feedback.sh now finds the #97 form by its label; merged at 35ca070).
  - Idle check and `tests/preview`: UNSURE. The idle runs were cut short, and the phone was charging, so batterystats
    didn't count; the preview printed one PASS before adb dropped.
  - Left on Debug: English and Russian, maybe Russian current. Two test backups sit in the phone's Download folder.
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

## Evening phone run, 2026-11-16 (A, main-tomorrow-debug 99fb0dcd, 21:38–22:34)

- **Languages:** 44 of 49 type right, read back. Hindi, Yale and Pinyin are right by the shots; Jyutping's shot isn't judged
  yet. lt, nn and ja: "field didn't start", a harness doubt, re-checked in the next slot.
- **Out of memory:** Debug crashed after about 18 input-method switches in one process. The fix is in main-mem (7a75c4bb).
- **Guide:** 66, 67, 69 pass, read back. The other slides were judged from the shots.
- **Gestures:** #89 (end, long word, 14 s hold stopped at 194 of 1984 characters), #93 and the swiped "its" pass. #83's
  pairs still join (goon, into, issue), as expected before the 1% veto.
- **Re-tests:** about, feedback and rawlog pass. home-focus and ime-window fail twice each, maybe from Debug being held:
  one plain run each next. #85's LeakCheck: two destroyed playgrounds still held after 15 guides (status comment, open).
- **Chinese (Yale):** the row, Space, Enter, the comma, tap, flick, the menu and the Sym panel all match.
- **For the owner's hands:** Georgian Shift+W types ჭ.
- **Next slot:** main-mem-debug went on at 22:36 and stays on Debug for the owner; its slot was stopped at once and runs overnight: all 49 languages, the out-of-memory count, LeakCheck,
  chinese-ui with the suggestion checks, #83's pairs.

## Found 2026-11-16: the gestures assume spaces and "." (owner's go 2026-11-17: D builds it; C does the bar never empty)

Read in the code, not seen on the phone yet; every gesture check so far runs in English only.
- A delete flick takes everything back to the last space (`wordDeleteLength`), so in Chinese, Japanese and Thai it can take a
  whole message. A flick while a Chinese code is pending deletes from the field and leaves the code.
- Double space types ". " in every language (Composer's full stop). Chinese and Japanese want "。" with no space, Hindi
  "।", and Thai no full stop.
- Three Space taps pick a run of letters (`WordSelection`), which in Chinese is a whole sentence.
- Proposed: ICU's word breaks for languages without spaces, each language's own full stop, a flick clears a pending code
  first, and A's gesture batch in every language.

## Paused for the usage limit (owner, 2026-11-17, about 00:00)

Every worker was told to commit its work in progress to its own branch and stop until "resume". On resume:
- D: gestures in every language (waiting on its test suites); then send CODE_FLICK to A.
- C: the two-languages merge, then the four-and-four cap with centring, tile and global tap haptics; then the bar-words job,
  which now includes a Korean word list (owner: Korean's bar is empty; find one with a clear licence, Leipzig's CC BY data
  first) and completions that match a syllable still being built.
- H: held letters without accents type once; accents within W–O.
- B: the Cantonese dictionary study (baseline 71.6% of characters, 10.6% of long phrases exact), then Cangjie, Quick and Zhuyin.
- E: the guide fixes (swipe slide ignores keys, the "e" stays); G: #96 analytics.
- A: read the 01:00 main-mem slot's reports (it runs in WSL during the pause).
- New on resume (owner, 2026-11-17):
  - D, after gestures: conservative corrections for every language with a word list, at least on the bar, reusing English's
    key-neighbour engine; Space applies one only at English's strict confidence. Tests with made-up typos per language.
  - J (free): word-pair tables for every language from Leipzig's CC BY corpora (licence checked per language), for next
    words; size no concern for now. C's bar-words reads them.
  - C: a language with one input method shows the language's mark at Q.

## App size and Google Play (found 2026-11-17)

The Debug APK is about 212 MB after the Chinese work and the Cantonese grammar (179 MB before), which looked over Google Play's 200 MB
limit; B read Google's current page on 2026-11-17: the base module may be 500 MB, each asset pack 1.5 GB, 4 GB in all. The owner: "Don't worry about size yet. That's kinda easily solvable." For the Play build: B's prototype (docs/play/asset-packs.md, tools/dev/build/play-packs.sh) moves the big data into four
install-time packs; the base download is then 41.4 MB, 201.8 MB in all on a Titan, with no app change. Not yet installed on
a phone. The GitHub APK can stay whole.

## For the owner's hands (collected 2026-11-17)

- Export TitanKeys Debug's typing log (Settings > Typing log > Export log), for J's #83 rerun with text since 11-11.
- The phone read 51 C on the charger at 04:19, not charging, with MediaTek's battery warning up since 02:49; it blocked the second
  half of the night's runs. Let it cool before the next slot.
- Shift opening and closing the Chinese menu (the twin's Shift can't be used); Georgian Shift+W types its letter; blocking a
  Spanish word from its menu; the saved-words list.

## Paused again for the usage limit (owner, 2026-11-17 morning)

On resume:
- E: the dominoes (every streak, never blocks a delete, ~2.5x longer) and a held delete with no 10 s cap (only the animation capped).
- D: #98 broken: "di" shows "do" in the middle but Space types "di".
- C: the bar stuck on the old language after a switch (priority), then Portugal/Brazil for Portuguese.
- B: Yale's loading line still shows with the engine warm.
- G: Debug's logs on by default with no time limit, a Debug-only LogPull receiver and a MARK gesture.
- J: Turkish and Azerbaijani lower-casing in next_words.py, and their tables rebuilt.
- A: the interactions batch (offline); slot5 waits for the owner to be at the office. Phone: 100.98.104.89:42033.
- Then: the full phone run on a fresh build, F's wording pass, the owner's hands list, the pre-release.

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

18. **Language labels (2026-11-16 night):** one label per input method, shown at Q and in the switcher, at most two characters in the language's own script: Latin languages by their two-letter code (ES, DE, …; English shows no mark at Q), Cyrillic in Cyrillic (РУ, УК, БЕ, БГ, СР, МК, ҚА), Greek ΕΛ, Georgian ქა, Thai ไท, Hebrew עב, Hindi हि, and one character for 한, あ and Chinese's 耶, 粵, 拼.

19. **#98, the bar shows what Space types (2026-11-16 night):** H's check found 4 cases where Space kept the typed word while a completion sat in the middle (every language but English, with Fix typos off, after a fix taken back, a hidden fix). The fix keeps Space as it is and makes the bar honest: when Space will keep the typed word, that word sits in the middle, completions to the sides. Space doesn't start autocompleting in other languages.

20. **Zhuyin's layout (2026-11-16 night):** 注音 uses the 許氏 Hsu layout, all 37 symbols on the 26 letters, since the standard 大千 layout needs 15 keys the Titan only has behind Alt. Named 注音 · 許氏 in Settings. Cangjie 5 and Quick 5 use the letters directly (official rime repos, LGPL-3.0).

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

## Found today: the touchpad overlay leak (2.1 and 3.0)

- Every time TitanKeys stops being the keyboard, Android's own shutdown calls `onFinishInput` after our `onDestroy`, which
  shows the touchpad overlay again with "touchpad only in fields" on. One overlay leaks per switch, and 2.1's Back service
  keeps a High window on top. On the owner's phone: 96 leaked 2.1 overlays and a High window that took the touchpad from
  TitanKeys Debug. Fixed for 3.0 on main (a `destroyed` flag; the overlay only for the current keyboard), with a check.
- 2.1 keeps leaking until its process ends (a reboot, or the 3.0 update). Possible 2.1.1 quick fix: the owner's call.

## Later, after the languages work on the phone (owner, 2026-11-16)

- **Proposal for Chinese choices (and any engine with choices), waiting for the owner:**
  - Today: choices sit over E to P; letters go on building the code; Space picks the one a resting finger marks, or the
    first; swiping up or down on the keys pages; , and . page too. Marking by resting a finger isn't discoverable, so in
    practice only the first choice gets picked.
  - Proposed, as function (now): hold a letter key to pick the choice above it (holding is free in Chinese, which has no
    accents; symbols stay on Alt and the Sym panel). Sym while choices show opens all of them in a Sym-panel grid (about 28
    a page, a press picks, swipes page), and Sym again closes it. Enter types the code as letters. Space keeps the marked or
    first choice. With no code typed, Chinese punctuation through Rime (，。？！：；、「」, Hong Kong style quotes).
  - Not proposed now: a flick up on a key to pick. Swipe up already pages, and its accuracy needs the touch feed first.
  - The look, later: the marked choice and the switcher both get the word menu's hover pill, with the full name.

- **Engines study, on main (`docs/studies/56-engines.md`), waiting for the owner:**
  - Japanese: Mozc (BSD-3, maintained, builds an Android library), about 2 weeks and 20–25 MB. Rime with jaroomaji
    (Apache-2.0) was tried on the PC: single words right, sentences break (no Japanese grammar), 26 MB.
  - Hindi: jquery.ime's rule tables (GPL-3.0) in a small rule runner, about a week; the runner then serves other Indic
    scripts. Thai: keyboard work only (Kedmanee keymap, word breaking). Right to left: keyboard and UI work only.
  - Proposed order: Thai, Hindi, Japanese, then right to left (Hebrew first).
  - Needs the owner's yes: running Bazel in WSL (it downloads bazelisk, Bazel and Mozc's pinned dependencies) to build
    Mozc and measure it.

- **Next after the basic phone tests: switching languages inside TitanKeys** with the held-Sym overlay, never Android's
  picker (owner, 2026-11-16). Today a held Sym opens the Sym panel instead; the Sym log now works on the phone (with
  `log.tag.TitanKeysSym`) to show why. Alt+Sym stays as it is: it opens Android's list for other keyboards (owner).

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

## Owner's manual review, 2026-11-17 (office)

- **Before launch:** rework the language menu (Settings > Languages and the switcher's lists). Owner: "it's very cluttered and messy.  hard to read anything." Planned for the pre-launch polish pass, with F on wording and a design pass first; not started (token budget).
- **Now (C's queue, batched):** the small language mark on the keyboard sits off centre. Owner: "match the vertical positionoing center with the auto cap in english." Centre it vertically on the same line as English's auto-cap mark.

## 2026-11-17 evening: where things stand

- **On the phone:** Debug from main 426a478b (md5 6a477563, mapping 052e80fa kept), installed 17:19. Not phone-checked yet.
- **Tonight:** night.sh 00:30–07:30, every batch plus b-lines, heap around languages, zh-back (Sym panel and Back), leak-85; crash guard on. A sends one summary in the morning.
- **Fixed today, offline only:**
  - the memory leak (unbounded corrector builds per switch; C), and the uncapped cursorPath, which 2.1.0 has too (3.0 only, no 2.1.1);
  - Chinese per character for every gesture and the held delete, inside a composition too; a flick with a partial selection; Space touches never pick; Enter and Sym with a code; the bar after a pick; more candidates; radicals on a resting finger (B);
  - held Backspace, the bar after a switch, the language mark, the dominoes past 100, the playground switcher, the Settings labels.
- **Open:** whether the Sym panel stays open after Back (zh-back tonight); 速成 and Debug's Settings, probably the leak, to confirm on the new build.
- **Next, cheap:** A's RunStatus notification, the home-screen autostart and the run speed-ups.
- **Next week (budget):** the language menu rework, F's wording pass, the LOOK shots, the website, then the pre-release.
- **How we work now:** workers on Opus 5.5 low. Batches: one merge, one offline run, one build, one night run. The Master reviews each diff.

### Native review follow-ups (November 18)

The review covered 51 input methods. It checked the physical layout, Shift and hold layers, punctuation, starters, next words, guide text and word boundaries. Korean, Thai and Yale had already been reviewed.

| Reviewed input methods | Changes from this review |
| --- | --- |
| af, bg, mk | Add missing held letters. |
| el, he, id, ja, ka, sr, yue-jyutping, zh-cangjie, zh-quick, zh-zhuyin | Correct the key map or guide text. |
| az, be, bs, ca, cs, da, de, eo, es, et, eu, fi, fr, ga, gl, hi, hr, hu, is, it, kk, lt, lv, nb, nl, nn, pl, pt, ro, ru, sk, sl, sr-latn, sv, tr, uk, vi, zh-pinyin | Keep the current layout and text. Follow-ups are listed below. |

These observations came from source and dictionary reviews. Phone results are still due.

- Chinese: try selecting and deleting punctuation alone, a supplementary character (𠮩), and a character before a space. Compare selection, eligibility and flick deletion in WordSelection.kt, TitanKeysIme.kt and DeleteSwipe.kt. Try Space with only predictions in the expanded menu.
- Chinese predictions: pick 简体 starters 一个, 这个 or 唔该. The prediction tables use traditional keys. Compare results with 一個, 這個 and 唔該. Also try a fallback choice alongside live engine predictions and inspect the next row after Escape.
- Cangjie and Quick: check character coverage for 𠮩 and 𨋢. Quick's upstream tables and Cangjie's character-set setting have different coverage. Check short codes hi and or for membership of 我 and 倉. Preserve candidate order. For Zhuyin, compare the displayed preedit for mj, lj and mys with the selected syllable. Review Pinyin's apostrophe separator in xi'an against the keyboard's punctuation rule before changing it.
- Japanese: try n', t'i and d'i through MozcEngine.key; punctuation interception may commit before Mozc can consume the apostrophe. Hindi: test // through RulesComposer.key for ऽ. Decide the handling of standalone digits separately from slash rules.
- Turkish and Azerbaijani: compare physical Shift+I, automatic sentence capitals, and held dotless ı. The unmapped printed keys take the hardware uppercase path, while the dictionary already uses locale casing. Dutch: check suggestion casing for ijs and ijzer at sentence start (IJs and IJzer).
- Compare word selection, correction context and next-word lookup for Catalan col·legi, Hebrew צה״ל, Swedish EU:s, Danish EU’s, Finnish vaa’an and Irish d'ár. Check Swedish automatic spacing before a suffix. Preserve ordinary punctuation boundaries.
- Ukrainian and Belarusian: compare predictions after ім'я and сям'я with the same words using ’ and ʼ. The prediction generator normalizes the apostrophe. Macedonian: verify leading apostrophe handling with ’рбет and ’рѓа. Review Ukrainian basic-word omissions and Catalan geminated-L entries against the source corpus.
- Check hyphenated words using Russian по-русски, Bulgarian по-добре, Estonian e-posti, Hungarian tudja-e, Polish e-mail and Indonesian anak-anak. Some whole words have shipped prediction keys although lookup takes only their suffix. Establish the intended selection unit per language.
- Check sentence capitals after local abbreviations and dates (Polish np. książka, Lithuanian 9 val. ryto, Czech 5. května). Compare Polish suggestions for explicitly accented dziękuję with dziękuje. Review Latvian greeting coverage including labdien. Judge conversational starter quality separately from spelling correctness.
- Portuguese: both regional tables are present. Compare corrections in complete sentences before changing frequencies. Nynorsk: check whether lower-ranked Bokmål entries affect actual suggestions. Other quality observations include formal starters in Hindi, Hebrew, Georgian and Indonesian.
