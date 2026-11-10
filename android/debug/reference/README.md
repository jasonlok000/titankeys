# TitanKeys Reference

A separate app that shows you gestures and typing to do while the raw input log of TitanKeys Debug records them. It is plain Java with framework widgets. Normal TitanKeys and TitanKeys Debug do not compile it.

## Build

```bash
bash tools/reference.sh
adb install -r dist/titankeys-reference.apk
```

The script needs `ANDROID_HOME` with platform 36 and build-tools 36.0.0. It signs with `signing/atlas.keystore` and gets the password in the same way as `tools/release.sh`.

`bash tests/reference/run.sh` checks the steps and the word counts of the passages. It does not need a phone.

## Record

1. Make TitanKeys Debug the current keyboard.
2. Open TitanKeys Reference. Push **Open log settings** and turn on **Record my input**.
3. For each step, push **Start**, do the step, then push **Done**. Use **Redo** and **Skip** if necessary.
4. On the last step, push **Open log settings** and use **Export log**.

**New typing style** starts a free-typing session in a style that you name. You can use it on each step, as many times as you want. A style that starts while a step runs cancels that attempt.

## Output

Each button adds a `mark` line to the raw input log. Its format is in `android/debug/recorder/README.md`. The same event adds one line to a timeline file in `Android/data/com.titankeys.reference/files/reference/`. There is one file each time that the app opens. It does not hold the text that you typed.

| Field | Meaning |
|---|---|
| `t` | `SystemClock.uptimeMillis()`, the same clock as the raw input log |
| `wall` | Wall clock, in milliseconds |
| `kind` | `start`, `done`, `redo`, `skip`, `style-start`, `style-end` or `style-type-anything` |
| `label` | The step label, such as `03-grip-and-type`, or the style name |
| `step` | The number of the step that was on screen |
| `passage` | Only on `style-start`: the number of the passage on screen |
| `sent` | False if the keyboard did not serve the field, so the raw input log has no matching mark |

A `redo` or `skip` cancels the attempt that its `start` began. If a step has more than one `done`, use the last one.

The typing passages in `Passages.java` are everyday English on purpose. They are not Simplified Technical English.
