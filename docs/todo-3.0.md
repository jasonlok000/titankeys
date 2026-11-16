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

More calls get added here as the night goes.

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
