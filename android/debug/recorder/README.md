# Swipe recorder and replay tools

Tools for measuring swipe accuracy on a real Titan 2 Elite. They build only into TitanKeys Debug. Normal TitanKeys never compiles them.

## Record a session

1. Run `bash tools/debug.sh --install`.
2. Make TitanKeys Debug the active keyboard, then open **TitanKeys Recorder**.
3. Swipe each word as it is shown. Use **Next**, **Skip** or **Redo** to move on. The session has 200 normal words, 100 rushed words and 25 typed sentences.
4. Use **Export** to save the session as a JSONL file.

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
| `oldspace` | `1` scores the older Space behaviour |
| `timing` | `1` times word suggestions instead of replaying |

To compare two builds, replay the same session on each and compare how often `prompt` is the first word in `top`, and how often it appears at all.

## Sample recording

`samples/session-20261108-114501.jsonl` is one session from a Titan 2 Elite: 300 swiped words (200 normal, 100 rushed) and 25 typed sentences, made by one person using both thumbs. All the words and sentences are the recorder's prompts. Lines marked `redo` or `skip` cancel an earlier attempt at that prompt.

To replay it, push it in place of `session.jsonl` in the commands above:

```bash
adb push android/debug/recorder/samples/session-20261108-114501.jsonl "$dir/in.jsonl"
```

Hold back one third of the prompts (`index % 3 == 2`) if you tune anything against it, so that you can check the result on swipes the tuning did not see.

`samples/sentences-20261108-192726.jsonl` is a second session by the same person: 24 sentences (126 words), swiped one word at a time, with `pace` set to `sentence`. It tests how well context helps. Each line holds one whole sentence, so `prompt` has several words and `top` has only the last word's suggestions. Some words were deleted and swiped again, so count only the first try at each word, or retries will raise the score.
