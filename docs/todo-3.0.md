# TitanKeys 3.0: to-do list

3.0 is the launch version, and everything in it is done before launch. 2.1.0 has been the full release since 2026-11-16.
The 2.1 list is [todo-2.1.md](todo-2.1.md), kept as history. Decisions and their reasons are in
[owner-decisions-2.1.md](owner-decisions-2.1.md), under "3.0". The orchestrating session keeps this list up to date, checked
against main and the workers' branches. Last checked: 2026-11-16.

## Settled on the issues (owner's answers, 2026-11-16)

Ready to build: #65, #66, #67, #68, #69, #70 with #71, #72, #73, #74, #75, #77, #79, #81, #82, #84, #85, #86, #87, #89, #90,
#91, #93. Each issue's questions comment and the owner's reply say what to build. The decisions log has the readings that
needed interpreting: #66's target, #67's P/Backspace, #72's line on the slide, #90's drawing from the first try, and #91's
dictionary first.

- Studies first: #83 (joined swipes, a deep dive in the logs), #73's were/we're (can the context model decide it when it's
  very sure?), and #56 (Rime on Android and its size; no app code until #56 is answered).
- Waiting for the languages work: #76 (the label), #80 (American first, maybe in a new language menu).
- Monitoring, no work until they show again: #92, #94, #95.
- To discuss in chat: #78 (Save and capitals).
- Not answered yet: #56.

## Proposed overnight job (waiting for the owner's go)

1. **The rename first (#81, code only):** `com.atlas` to `com.titankeys`, `AtlasIme` to `TitanKeysIme`, and the rest of the
   issue's list, in one commit on main. Then the checks, a Debug build, and the update test on the phone. Debug's keyboard
   is turned back on over adb; its Accessibility needs the owner if adb can't. The folder and WSL moves wait for a time with
   no session running.
2. **During the rename, nothing that touches code:**
   - F writes every line the night needs: #66's new line without "steer", #67, #72's step and warning, #75, #79, #86's sample
     line, #87's step, #90's lines, and #74's mail texts.
   - Studies: #83 (the Swiping engine session), #73's were/we're, #56's Rime build and size.
   - #82: the dev tools into the repo.
   - The 2.1 issues still open: checked against the code, closed if done, otherwise moved to 3.0.
3. **After the rename, the code in parallel worktrees, each on its own files:**
   - Typing: #91, #77, #73.
   - Gestures and hover: #89, #93, #84 (rule a; the signal search waits for the owner's log).
   - App screens: #71 with #70, #86, #87's row in Settings > Setup.
   - Data: #65 backup, #74 feedback by email and the crash offer.
   - Battery and leaks: #85's fixes and the adb stress checks on Debug.
   - Guide: #68, #69, #67, #66, #72, #75, #79, #87's step, then #90 (likely runs past the morning).
4. **Merging and the phone:** the Orchestrator reviews and merges each branch, builds, and runs every adb check on the phone
   one at a time, in TitanKeys' own fields, silently. Release TitanKeys is the phone's keyboard again at the end.

## Needs the owner

- The go for the overnight job.
- #78: the chat discussion.
- #56: the questions.
- #85: a normal day with the phone off the charger, morning to evening, adb reachable. The Orchestrator resets the battery
  stats when the owner says it's off the charger.
- #84: with the raw log on, press Favourite and rest on it for 3 s, five times; press it and lift at once, five times; export.
- #83 (later): a raw log export after a few days of swiping, and the word pairs that end and start on the top row.
- Hands checks once built: #89's long flick, #93's swipe after a flick, #66's distance, #68's hunts, #72's Sym press, #90's
  gesture slides, and one run of the guide with someone who has never held a Titan.
- #81: the folder and WSL moves at a quiet time, then starting sessions again from X:\TitanKeys; Debug's Accessibility after
  the renamed install, if adb can't.
- #74: one real report sent from Debug.
