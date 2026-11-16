# Maintainer tools

These are the tools I use to build, test and measure TitanKeys. You don't need them to contribute: `bun run check` and the tests in [CONTRIBUTING](../../CONTRIBUTING.md) are enough.

They run in WSL (Ubuntu) on Windows, or any Linux. The phone tools need the Titan 2 Elite connected over adb, by USB or wireless debugging.

## Set up

1. Build TitanKeys once by hand in a checkout at `~/build/titankeys`, beside the public Ink at `~/build/ink`. The tools copy `node_modules`, `.ink` and `signing` from it into their throwaway build copies.
2. Put Kotlin 2.1.20 at `~/build/kotlin-2.1.20` and the Android SDK at `~/android-sdk`.
3. Write `~/titankeys-env.sh`, which sets `ANDROID_HOME`, `ANDROID_NDK_HOME`, `JAVA_HOME` and `PATH` for bun, cargo and the SDK.
4. Copy `local.env.example` to `local.env` in this folder and fill in what differs on your machine. `local.env` is ignored by git. It holds the phone's address, the WSL distro's name and local paths. Never put keys or passwords in it.

Every tool loads `env.sh`, which reads `local.env` and fills in the defaults. Python tools read the same variables, so run them from a shell that ran `. tools/dev/env.sh`.

## build

| Tool | Does | Needs |
|---|---|---|
| `verify.sh build` | `bun run check`, every test that needs no phone, and a Debug build, in a throwaway copy. The last line is `RESULT: PASS` or `RESULT: FAIL`. | WSL |
| `verify.sh phone` | All of `build`, then installs on the phone and runs the phone tests. | WSL, the phone awake and unlocked |
| `try.sh <checkout> <command>` | Runs one command in its own throwaway copy, without the verify lock, so several runs can go at once (at most 4). | WSL |
| `play-bundle.sh [checkout]` | Builds the Play bundle (`.aab`). | WSL, Julian's private Ink SDK |
| `keyboard.sh` | Selects TitanKeys Debug as the keyboard again after a reinstall. | the phone |
| `compat.py` | Removes the few options that only the private Ink has, in a build copy. `verify.sh`, `try.sh` and `play-bundle.sh` run it. Never run it in the repo. | |
| `rg` | Stands in for ripgrep where it isn't installed. | |

`verify.sh` and `try.sh` build the checkout they sit in. Set `TK_WORKTREE=<path>` for `verify.sh` to build another one, such as a git worktree. Only one `verify.sh` or `play-bundle.sh` runs at a time on a machine: they share a lock in `~/build/tk-tools`.

The Play bundle needs Julian's private Ink SDK, which isn't public and never goes in this repo. `play-bundle.sh` expects it at `TK_PLAY_INK` (default `~/build/play/ink`), patched with `tools/ink-bundle.patch`.

## phone

Every phone tool keeps these rules:

- It stops if a navigation app is in front, because the phone may be in a car.
- It checks the front window before every key or tap, so nothing lands in another app.
- It never force-stops TitanKeys. That turns its Accessibility service off and moves the phone to another keyboard.
- Screenshots lose the status bar, and the keyboard window when it's shown, so no notification or clipboard text is kept.

| Tool | Does |
|---|---|
| `shot.sh NAME` | Screenshot, cropped, to `$TK_OUT/shots/NAME.png` and a half-size copy. Needs Pillow. |
| `open.sh PACKAGE [ACTIVITY]` | Wakes the phone and opens an app. |
| `tap.sh X Y [PACKAGE]` | Taps only if that app is in front (default TitanKeys). |
| `back.sh [PACKAGE]` | Presses Back only if that app is in front. |
| `front.sh` | Prints the window in front. |
| `rec-start.sh`, `rec-stop.sh`, `rec-pull.sh` | Screen recording in 170 s pieces, with a log of the front window. |
| `rec-frames.py` | Contact sheets from a recording, keeping only frames with TitanKeys in front. The rest are deleted unseen. Needs ffmpeg and Pillow. |
| `reconnect.sh PORT` | Connects over wireless debugging after a restart and turns on the fixed port 5555. Needs `TK_ADB_HOST`. |
| `preview-test.sh` | Runs `tests/preview` from the last `verify.sh` build copy, then reinstalls and selects the keyboard. The test force-stops the app, so turn Accessibility back on after. |

adb can't press Sym, hold a key or touch the touchpad. Those checks need hands.

## touch

The touch simulator plays touches on the touchpad and presses on the keys, as the hardware itself sends them, so the checks that
used to need hands run over adb: swipes, delete flicks, Space taps, a finger resting or lifting after a press, holds, Sym and fn.
It needs no root. On the Titan 2 Elite (Android 16) SELinux keeps adb's shell from writing to `/dev/input`, so the injector makes
a twin of each device through `/dev/uinput`, which the shell may use: the same name, location, bus, vendor and product, keys and
ranges, read from the real one. Android gives the twin the real device's configuration, and the kernel removes it when the
injector ends. It takes 0.8 s to be added, and its events then play within a few milliseconds of their times.

What works today and what doesn't (phone run, 2026-11-16):

- Keys work: typed letters reach TitanKeys Debug's field, and Sym reaches it as Sym (it opens the panel).
- A held Sym opens the Sym panel, not the switch overlay. Why isn't known yet.
- The touchpad doesn't work yet. The phone's input reader sets a new touchpad's height before it knows the display, and only
  sets it again at its next reconfiguration (screen off and on). Until then the twin's heights come out upside down and squeezed,
  so no touch lands on a key. A brief screen recording didn't trigger the reconfiguration.

The keys are real, so they type in whatever is in front. Before every gesture and every setup key, `touchlib.sh` checks that
the screen is awake and unlocked, no navigation app is running, TitanKeys Debug's window is in front and one of its own text
fields has the keyboard. Otherwise nothing is sent. The owner may use the phone meanwhile, so when another app is in front or
no Debug field has the keyboard, TitanKeys Debug's Space gestures screen comes back with `am start` (twice at most) and
everything is checked again first. A screen that's off or locked, or a navigation app, stops the run after a second try. The
release app is never a target: `TK_APP` must end in `.debug`. If the injector stops part way, it lets go of every key and lifts
the finger. TitanKeys Debug is the keyboard only while a check types in its field: release TitanKeys goes back right after,
read back each time, and at once whenever the front stops being Debug. Every tool holds the shared phone lock, `~/build/tk-tools/.lock`, for its whole run, so no other phone run overlaps.

| Tool | Does | Needs |
|---|---|---|
| `test/run.sh` | The offline checks: the calibration against the app's own `TouchAlignment.java`, every key read back on itself, each gesture against the rules in `DeleteSwipe.kt` and `CursorGesture.kt`, and the injector's script format. | a JDK, a C compiler |
| `build.sh` | Builds the injector, `tk-inject`, for the phone with the NDK. `touch.sh` builds and pushes it when it changes. | WSL, the NDK |
| `keymap.sh` | Reads which codes the keyboard sends for Sym, fn and the rest from its key layout file, and checks the touchpad's range. Run it once. | the phone |
| `touch.sh GESTURE...` | Plays gestures after the checks. At most 15 s for each call. | the phone |
| `gestures.py GESTURE...` | Writes the script `touch.sh` plays, without the phone. See its help for every gesture. | |
| `capture.sh SECONDS [--keys]` | Records real touches (and with `--keys`, keys) for `replay-getevent`. A capture with keys holds what was typed: keep it on your computer. | the phone |
| `checks.py [NAME...]` | The first checks, each with a YES or NO read back from the field, or a screenshot to look at (`--list`). | the phone, a Debug field focused |

Gestures, played in order, with `+MS` for a pause between two:

- `swipe:WORD[:SPEED]`: through the word's key centres, at .012 keys a millisecond unless you set it.
- `flick[:KEYS[:MS]]`: a level delete flick from Backspace.
- `space:N`: 1, 2 or 3 taps on Space.
- `key:NAME[:HOLD[:AFTER]]`: a press with the finger on the key. AFTER is `lift`, `rest`, `slide` or `none`.
- `hold:LETTER[:MS]` and `touch:X,Y[:MS]`.
- `replay-getevent:FILE` and `replay-rawlog:FILE[:FROM[:TO]]`.

For example, `bash tools/dev/touch/touch.sh swipe:go +100 swipe:on` swipes "go" and "on" 100 ms apart.

Positions are chosen where TitanKeys reads them, after the built-in calibration, and turned back into the touchpad's raw steps.
The touchpad reports about every 9.5 ms, with a contact size of 2 or 3 while moving and 1 or 2 while still, as real captures do.
A raw log replay reads only its touch and key lines; the rest, which can hold typed text, is skipped unread.

## preview

Quick pages for look-only changes: calibration, guide drawings, overlays. A page reloads in seconds, where a build takes about 15 minutes. Tune it on the page, then build the result into TitanKeys once.

1. `python3 tools/dev/preview/server.py` serves `pages/` on 127.0.0.1:8765. It serves the Inter fonts from the app's own copies in `android/src/main/res/font` (OFL, see `licences/Inter-OFL.txt`).
2. `bash tools/dev/preview/open.sh index.html` runs `adb reverse` and opens the page in Chrome on the phone.
3. Each change on the page is saved to `$TK_OUT/preview/values.json` and added to `values.log`, so read the values there.

Chrome screenshots can show other tabs. Crop or delete them before sharing.

The phone's display is set to 1076×1200 at density 250. The calibrated key pitch is 107.5 px, centred, with square keys and even rows.

## swipe-baseline

Measures the swipe engine on recorded swipes, and rebuilds engine 2.0's models. The numbers in `docs/paper.md` come from here. See [swipe-baseline/README.md](swipe-baseline/README.md) for every tool and the steps.

- The recordings and typing logs hold typed text. They live at `TK_PRIVATE` and `TK_DATA`, on your machine only, and never go in git. `report/` holds counts and the public passages' words only.
- ONNX Runtime for Android isn't committed. Download onnxruntime-android 1.20.0 from Maven Central and unpack it into `TK_ORT`, as the README there says.
- The ranker tools need numpy, LightGBM, scikit-learn, onnxruntime and transformers. A venv works: `python3 -m venv ~/venv-tk && ~/venv-tk/bin/pip install numpy lightgbm scikit-learn onnxruntime transformers`.

## diagrams

The scripts behind the paper's 18 animated diagrams in `assets/readme`. Every input is in the repo, so they need no settings. Run `sh tools/dev/diagrams/make_all.sh`. It needs matplotlib, numpy and Pillow. Set `GIF_OUT` to draw them somewhere else.

## misc

Smaller tools that were used more than once.

| Tool | Does | Needs |
|---|---|---|
| `score.py` | Scores a swipe recorder session: right word first, top 3, broken strokes. | a session file, such as `android/debug/recorder/samples/` |
| `make-words.py` | Writes the recorder's word list (`RecorderWords.kt`) from the dictionary, with a fixed seed. | |
| `pull-recordings.sh [PREFIX]` | Copies the recorder's sessions from the phone into `TK_DATA/recordings`. | the phone |
| `recorder-status.sh` | What's in front, the keyboard in use, the sessions on the phone and recent crashes. | the phone |
| `live-state.sh` | A read-only look at the keyboard's state, windows and recent log lines. | the phone |
| `route-check.sh` | A read-only look at how the touchpad is routed. | the phone |
| `display-check.sh` | Display size, density, font scale and the installed builds. | the phone |
| `mode-watch.sh [MINUTES]` | Logs the touchpad mode for each app in front to `TK_OUT/modes.txt`. | the phone |
| `preflight.sh` | Checks before a long unattended run: the link, power, screen timeout, keyboard and free space. | the phone |
