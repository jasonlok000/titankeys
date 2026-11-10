# Swipe recorder and replay tools

Tools for measuring swipe accuracy on a real Titan 2 Elite. They build only into TitanKeys Debug. Normal TitanKeys never compiles them.

## Record a session

1. Run `bash tools/debug.sh --install`.
2. Make TitanKeys Debug the active keyboard, then open **TitanKeys Recorder**.
3. Swipe each word as it is shown. Use **Next**, **Skip** or **Redo** to move on. The session has 200 normal words, 100 rushed words and 25 typed sentences.
4. Use **Export** to save the session as a JSONL file.

## See touch size

**TitanKeys Touch size** shows the size that the touchpad reports for each point of a touch, to learn what it means (issue #30).

1. Make TitanKeys Debug the active keyboard, then open **TitanKeys Touch size**.
2. Pick a kind of touch: fingertip swipe, flat thumb swipe, thumb rest or fingertip rest. Do it about 10 times on the keys.
3. The map draws each point as a circle as large as its size. Below, each kind shows its average size, how often each size came up, and how long and how far its touches went.
4. Use **Export** to save the touches as JSON lines: one line for each touch, with its kind and each point's position, size, Android's touch major and time. It keeps no text.

The size is the sensor's touch major, from 0 to 255. The screen reads Android's size axis, which is that value divided by 255, and multiplies it back. Android's touch major axis is scaled to the screen, and its range is the screen's diagonal, so it does not give the sensor's steps. The raw log's `sz` is that scaled touch major.

## Replay a session

Replay runs a recording through the real touch handling, text logic and swipe engine, with no keyboard window.

```bash
dir=/sdcard/Android/data/com.titankeys.debug/files/replay
adb shell mkdir -p "$dir"
adb push session.jsonl "$dir/in.jsonl"
adb shell am instrument -w -e in in.jsonl -e out out.jsonl com.titankeys.debug/com.atlas.keyboard.ReplayRunner
adb pull "$dir/out.jsonl"
```

Android restarts the app under test, so replaying restarts TitanKeys Debug if it is your active keyboard. To avoid that, build a second copy under another package name.

Each output line has `index`, `prompt`, `pace`, `touches`, `joined`, `top` (the words offered), `text` (the final text), `recs`, `outs` and `selects`.

Options, each passed as `-e name value`:

| Option | Meaning |
|---|---|
| `lift`, `lifttop` | Slip recovery time in ms, and a separate time for the top row |
| `pieces` | `0` turns off joining short touches to the swipes beside them |
| `merge`, `fill` | `1` joins pieces before recognition, and adds points across each gap |
| `late`, `latespeed`, `latefrom` | Extends the start of swipes that register late |
| `keys` | A file of `letter x y` lines that replaces the key positions |
| `timing` | `1` times word suggestions instead of replaying |
| `engine2` | `1` waits for swipe engine 2.0's models before replaying, so the replay scores engine 2.0 |

To compare two builds, replay the same session on each and compare how often `prompt` is the first word in `top`, and how often it appears at all.

## Raw input log

**Settings > Raw input log** records everyday typing in any app, so that tuning can use more than the recorder's prompts. It is off by default and works in normal TitanKeys and in TitanKeys Debug, next to the recorder and replay. The phone only records. A tool on a computer must work out corrections from the export.

The log is in `filesDir/rawlog/`, as numbered files of up to 8 MB that each start with a `header` line. The total is at most 200 MB, and the oldest file goes first. **Export log** joins the files, oldest first, into one JSONL file. The export holds the text that was typed, so check it for personal text before you share it or add it to this repo.

Only fields where `Field.learning` is true are recorded, which leaves out passwords, email and web addresses, and fields that ask for no personalised learning. The one exception is a field whose `EditorInfo.privateImeOptions` contains `com.titankeys.rawlog.reference`: it is recorded even though it asks for no personalised learning. That mark overrides nothing else, so a marked password is never recorded.

An app with a recorded field can add a step mark with `InputMethodManager.sendAppPrivateCommand(view, "com.titankeys.rawlog.MARK", extras)`, with the string extras `kind` and `label`.

Each line is one JSON object and `k` is its kind. Times are in `SystemClock.uptimeMillis()`, the same clock as motion and key events. `m`, `key`, `out` and `rec` have the recorder's fields with more added, so a tool can group them into the `events` of a recorder line. `ReplayRunner` copies any line without `events` straight to its output, and no raw log line has `events`.

| `k` | Written | Fields |
|---|---|---|
| `header` | At the start of each file, and when a setting changes | `format`, `t`, `wall` (wall clock), `atlas` (version), `model`, `android`, `slipMillis`, `settings`, `tuning` |
| `gap` | Where logging was skipped. Never link anything across one | `t`, `why`: `start` (logging began), `field` (a field that isn't recorded), `leaving` (input after a key that can move to another field, which can belong to a password), `queue` (the writer fell behind), `error` (a write failed). `n` is the number of lines lost, if known |
| `start`, `end` | `onStartInput` and `onFinishInput` of a recorded field | `t`, and for `start`: `inputType`, `imeOptions`, `reference` (recorded only because of the mark), `restarting`, `selStart`, `selEnd`, `before` |
| `m` | Each touchpad event | `a`, `t`, `rx`, `ry`, `p` (raw points), `s` (keyboard points), `sz` (contact size of each raw point, as touch major) |
| `key` | Each key event | `c`, `d`, `t`, `g` (gesture key), `meta`, `repeat` |
| `out` | What a contact counted as | `type` (`swipe` or `tap`), `n`, `start`, `end`, `deletes` (only for a delete swipe), `key`, `t` |
| `rec` | The words offered for a swipe | `words`, `resumed` (joined to the swipe before it), `scores` (the engine's `[word, total, path, context]`, before spelling and blocked words apply), `ranked` (present when swipe engine 2.0 chose the order: `total` is then the ranker's score, and `path` and `context` are the FUTO engine's, or 0 for a word only CleverKeys found), `t` |
| `text` | Each change the keyboard makes | `t`, `cause`, `via`, `auto`, `del`, `ins`, `aft`, `ok` |
| `pick` | A word chosen from the strip | `t`, `index`, `word`, `offered` |
| `sel` | `onUpdateSelection` | `t`, `start`, `end`, `cs`, `ce` (composing region), `before` (up to 256 characters) |
| `mark` | A step mark | `t`, `kind`, `label` |

In a `text` line, `del` characters before the cursor and `aft` characters after it were replaced with `ins`. `del` is -1 if the app can't say how much it deleted, and `ok` is false if the app refused the edit. `cause` is one of these:

| `cause` | `via` | Meaning |
|---|---|---|
| `commit` | `swipe`, `tap`, `key`, `symbol`, `space`, `enter` | Text that was typed |
| `backspace` | `letter`, `word` | Backspace deleted one letter, or a whole word with Ctrl, Alt or a delete swipe |
| `choose` | | A word chosen from the strip |
| `command` | | A key the app handles itself, such as paste. The `sel` lines after it show what changed |
| `lookalike`, `fullstop`, `spacedrop` | | Automatic changes, with `auto` true: a look-alike swap, two spaces that became a full stop, and a space removed before punctuation or Enter |

## Sample recording

`samples/session-20261108-114501.jsonl` is one session from a Titan 2 Elite: 300 swiped words (200 normal, 100 rushed) and 25 typed sentences, made by one person using both thumbs. All the words and sentences are the recorder's prompts. Lines marked `redo` or `skip` cancel an earlier attempt at that prompt.

To replay it, push it in place of `session.jsonl` in the commands above:

```bash
adb push android/debug/recorder/samples/session-20261108-114501.jsonl "$dir/in.jsonl"
```

Hold back one third of the prompts (`index % 3 == 2`) if you tune anything against it, so that you can check the result on swipes the tuning did not see.

`samples/sentences-20261108-192726.jsonl` is a second session by the same person: 24 sentences (126 words), swiped one word at a time, with `pace` set to `sentence`. It tests how well context helps. Each line holds one whole sentence, so `prompt` has several words and `top` has only the last word's suggestions. Some words were deleted and swiped again, so count only the first try at each word, or retries will raise the score.

## Resetting the connected features

`ConnectedReset` (TitanKeys Debug only) puts news and swipe sharing back to their first state for phone tests, without
clearing data. Only adb can send to it:

```bash
adb shell am broadcast -n com.titankeys.debug/com.atlas.keyboard.ConnectedReset -a com.titankeys.debug.RESET_SHARE
adb shell am broadcast -n com.titankeys.debug/com.atlas.keyboard.ConnectedReset -a com.titankeys.debug.RESET_NEWS
```

The first turns sharing off, deletes the waiting swipes and leaves the offer unanswered, so the guide's last slide asks
again. The second lets every news entry show again.
