# TitanKeys 3.0: to-do list

3.0 is the launch version, and everything in it is done before launch. 2.1.0 has been the full release since 2026-11-16, and
the 2.1 milestone is closed. The 2.1 list is [todo-2.1.md](todo-2.1.md), kept as history. Decisions and their reasons are in
[owner-decisions-2.1.md](owner-decisions-2.1.md), under "3.0". The orchestrating session keeps this list up to date, checked
against main and the workers' branches. Last checked: 2026-11-16, 01:15.

## Where the issues stand

- **Ready to build:** #65, #66, #67, #68, #69, #70 with #71, #72, #73, #74, #75, #77, #79, #81, #82, #84, #85, #86, #87, #89,
  #90, #91, #93. Each issue's questions comment and the owner's reply say what to build. The decisions log has the readings
  that needed interpreting.
- **#56, languages:** every default, with two changes. 3.0 ships generic: as many languages as possible, working first, without
  waiting for native speakers. Traditional or simplified is one switch for every Chinese input method. Its default follows
  the phone's region (Hong Kong or Taiwan: traditional; mainland China or Singapore: simplified; anything else: traditional).
  That default is the Orchestrator's choice.
- **Studies first:** #83 (joined swipes), #73's were/we're, #56's sources and Rime.
- **Waiting for the languages work:** #76 (the label), #80 (American first).
- **Monitoring:** #92, #94, #95.
- **Closed 2026-11-16:**
  - Shipped in 2.1.0: #26, #52, #54, #59, #60.
  - Done: #55, #57.
  - Left as it is: #78.
- **Moved to 3.0:** #34, #36. Parked with no milestone: #33, #48, #51.

## The plan (waiting for the owner's explicit go)

3.0 doesn't fit in one night. It takes about three nights of unattended work, then the owner's hands checks, then the 3.0
pre-release and three days of daily driving. Each night, the Orchestrator runs several worker sessions in parallel, each in its
own worktree on its own files. The Orchestrator reviews and merges their work, builds, and does every phone check one at a time.

### Night 1 (tonight)

0. **The folder move (#81), about 20 minutes, two messages from the owner.** This session, F and the engine session step out
   to another folder. X:\Atlas becomes X:\TitanKeys, the worktrees are repaired with `git worktree repair`, the settings and
   notes get the new paths, and the sessions step back in. If anything holds the folder open, it waits for tomorrow and tonight
   runs from X:\Atlas.
1. **Workers, all starting at once:**

   | Worker | Work | Phone |
   | --- | --- | --- |
   | F | Every line tonight needs: #66's new line without "steer", #67, #72's step, warning and slide line, #75, #79, #86's sample line, #87's step, #74's mail texts | none |
   | Typing | #91, #77, #73, #85's battery and leak fixes; the were/we're study | adb typing in TitanKeys' own field, the stress checks |
   | Gestures and hover | #89, #93, #84's rule (a) | builds for the owner's hands |
   | Screens | #71 with #70, #86, #87's row in Settings > Setup | adb walks and screenshots |
   | Data | #65 backup, #74 feedback by email and the crash offer | adb, nothing sent |
   | Guide | #68, #69, #67, #66, #72, #75, #79, #87's step, with F's words | adb where keys reach; renders |
   | Tools | #82, the dev tools into the repo | a preview page over adb |
   | Engine study | #83 | none |
   | Language study | #56: sources, licences, size; Rime built for Android | one test program run over adb |

2. **Merging and the phone:** each branch is merged into main once its checks pass and the Orchestrator has reviewed it. Then a
   Debug build and its adb checks, one at a time, until 07:30.
3. **07:30, the baseline day (#85):** Android's battery stats are reset by a timer set when the night starts. Release TitanKeys
   is the keyboard. The phone is left alone until the evening. Work that doesn't need the phone carries on.
4. **The rename (#81, code) comes last:** once tonight's branches are merged, one mechanical commit renames `com.atlas` to
   `com.titankeys` and `AtlasIme` to `TitanKeysIme`, with the issue's full list, and its checks run. The update test on the
   phone waits for the evening. The rename comes last, not first, so the workers can start at once and the phone checks fit
   before 07:30.
5. **The WSL distro (#81):** exported and imported as `titankeys-build` at a time when nothing builds. `atlas-build` stays until
   the owner has seen the new one work.

### Night 2

- #90, the guide for first-timers.
- The language framework: the switch overlay, the picker on the bar, Settings > Languages, and one keyboard-list entry per
  input method (#76 lands here).
- Generic languages from the study's data.
- Rime in the app.
- Anything the studies turned into code: #83's join rule, were/we're.

### Night 3

- More languages and Chinese polish.
- Fixes from the owner's hands checks.
- #80 with the new language menu.
- The website (#58) brought up to 3.0.
- Then the 3.0 pre-release.

### The studies' scope

- **#83, joined swipes:**
  - Every join in the saved logs (.dev/swipe-baseline) is labelled right, wrong or missed, from what was kept or deleted after
    it.
  - What separates them: the gap, where one ends and the next starts, direction, speed, the shape, the word scores.
  - Then new join rules, including joins missed today, each replayed on every saved log.
  - The report on #83 gives right joins kept, wrong joins cut, missed joins found, and the headline numbers, which must not
    drop. No app code. Engine 2.0 stays off the phone.
- **#73, were/we're:**
  - Every were/we're in the saved logs' text and in an English sample is run through the context model TitanKeys already
    uses.
  - The model's choice and its certainty are measured, and a threshold is sought with no wrong change.
  - The report on #73 gives how many it would fix, and a yes or no. Code only on a yes, next night.
- **#56, generic languages:**
  - Per language: where its letters, accents and word lists come from (CLDR, Hunspell, the open keyboard dictionaries, word
    frequency lists), each source's licence, and its size.
  - How its letters sit on the Titan's QWERTY keys.
  - Which languages work from data alone, and which need code: Korean, Vietnamese, right-to-left scripts, Indic scripts,
    Japanese.
  - What N languages add to the app, against Google Play's 200 MB.
- **#56, Chinese:**
  - librime and OpenCC built for Android arm64 the way Trime does it.
  - The schemas compiled: Yale if rime-cantonese has it, Jyutping, Pinyin, Cangjie.
  - The sizes measured.
  - A small test program on the phone over adb that turns a typed code into characters, in both traditional and simplified,
    removed afterwards.
  - Every licence noted.
- **Output:** docs/languages.md updated with the findings and the framework's design. No app code tonight.

## Needs clearing before the go

- **Downloads** into the WSL build machine:
  - librime and its libraries (Boost, LevelDB, marisa, yaml-cpp, glog), and OpenCC.
  - The Rime schemas: rime-cantonese, rime-luna-pinyin, Cangjie.
  - Trime's build files, as a reference.
  - CLDR data, Hunspell dictionaries, open word-frequency lists.
  - The Android NDK if it isn't installed.
  - All from their official repositories. Nothing goes into the app tonight.
- **A test program on the phone:** run over adb from /data/local/tmp, then deleted.
- **The 07:30 battery reset:** already agreed (owner, 2026-11-16: "let's assume just ard 730AM").

## Blocked, or not mine to finish

- **Waiting on others:**
  - #36: Google Play waits for the D-U-N-S number.
  - #42: the Play migration notice waits for #36.
- **Needs the owner:**
  - #34: the deletion and retention check, with a throwaway contributor and the server's export.
  - #58: the owner's final review.
  - #84's signal search: a Record a bug of rest-and-lift presses.
  - #83's fix: new swiping data. The full typing log is only in TitanKeys Debug, so that's a few days on Debug after the
    baseline day.
  - Hands checks once built: #89, #93, #84, #66, #68, #72, #90, and one run with someone who has never held a Titan.
- **Not yet possible:** #85's after numbers come only with the 3.0 pre-release.
- **Monitoring:** #92, #94, #95.
- **Decisions coming, not needed tonight:**
  - Analytics: decided for 3.0 on 2026-11-13, no issue yet. The Orchestrator files it with questions.
  - The app's size once many languages are in: included or downloaded per language. After the language study.
  - Whether Japanese, right-to-left and Indic scripts are in 3.0. After the language study.
