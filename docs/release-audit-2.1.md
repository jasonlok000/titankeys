# Release audit, 2.1

What a TitanKeys 2.1 release APK contains, what in it is only for TitanKeys Debug and how that is kept out, and what's left for
the owner to decide. Done on 2026-11-13 for the owner's "remove the dev stuff for the release build and test thoroughly", before
the GitHub pre-release.

**How:** the release variant of `test-2.1h` (db5ee70 and the accent fix 97d6cea), built with `tools/release.sh` in a copy of its
own, signed with a throwaway keystore made for this audit and nothing else. The owner's signing folder was never read or copied,
and the APK is not shared. The merged manifest, the classes and the files were read from the APK itself, with `aapt2`, `dexdump`
and `unzip`. No phone.

## The APK

- **Package** `com.titankeys`, version 2.1.0 (57), target and compile SDK 36, minimum 34. arm64 only.
- **Size:** 115.9 MB (151 MB unpacked, 88 files). Not debuggable, not test-only, `allowBackup` off.
- **Where the size goes**, unpacked:

  | Part | Size | What it is |
  |---|---|---|
  | Swipe engine 2.0 | 112 MB | The next-word model (`swipe2/predictor/model-int8pc.onnx`, 83 MB), the swipe reader (`ck-titan.onnx`, 6 MB), its ranker and vocabulary (4 MB), and the ONNX runtime (`libonnxruntime.so`, 19 MB). In use: autocorrect's apostrophe choices and the next-word chances read it. |
  | The FUTO swipe engine | 21 MB | Its models and dictionaries in `swipe/`, and its library. |
  | Autocorrect's word lists | 11 MB | `autocorrect/`: pairs, triples and known words. |
  | The app itself | 8 MB | Code (1.2 MB of classes), Ink's library, the settings app, fonts and icons. |

- **Permissions**, exactly these six, each with its reason in the manifest:
  - VIBRATE: haptics.
  - SYSTEM_ALERT_WINDOW: the touchpad overlay.
  - INTERNET and ACCESS_NETWORK_STATE: announcements and contributions only, as the privacy policy says.
  - POST_NOTIFICATIONS: announcements. The app asks first.
  - READ_CONTACTS: only with Names from my contacts on.

## What ships

The merged manifest's components. Only the five marked exported can be reached from outside TitanKeys.

| Component | Exported | Why |
|---|---|---|
| `com.vandam.ink.MainActivity` (Ink's) | yes | The launcher: TitanKeys' settings app. |
| `com.vandam.ink.InkLightSdkReceiver` (Ink's) | yes, behind a permission named "normal" | The Light Phone SDK's marker, which Ink adds to every app. See [For the owner](#for-the-owner). |
| `AtlasIme` | yes, only to the system (BIND_INPUT_METHOD) | The keyboard. |
| `BackService` | yes, only to the system (BIND_ACCESSIBILITY_SERVICE) | Back, Home and the touchpad's scroll. |
| `LearningTransferActivity`, `ContactsPermissionActivity` | no | Backup and import, and the contacts prompt. |
| `ShareDisclosureActivity`, `BackDisclosureActivity` | no | The disclosures before sharing swipes and before the Accessibility service. |
| `FreshStartActivity` | no | Restarts TitanKeys in a new process. |
| `GuideActivity` | no | The guide and the front page. |
| `ConnectedJob`, `NewsReceiver` | no | Announcements and contributions. |
| `BugStopReceiver`, `BugRecordingProvider` | no | Record a bug: the notification's Stop, and the file lent to the app picked in the share sheet. |
| `RawLogExportActivity`, `HapticsLabActivity`, `KeyCalibrationActivity`, `GuideFrameActivity` | no | Debug only. They ship, but do nothing: see below. |

## Debug only, and how it's kept out

TitanKeys Debug is the release build with its own package, `com.titankeys.debug`, and the code in `android/debug/recorder`,
which `tools/debug.sh` copies in. The release never compiles that code. What's in the main source checks its own package name:
nothing that ends in `.debug`, nothing happens.

| Feature | In release? | Gate |
|---|---|---|
| The raw log copy for adb (`RawLogPull`), the recorder, replay, space gesture and touch size screens, and the contribution reset | No | Only in `android/debug/recorder`, which only `tools/debug.sh` adds, with its manifest entries. |
| The full typing log: its recording, its Settings row, its screen and its export | Inert | `RawLogGate` allows only `.debug`. The log never records. Settings never shows the row, and the export refuses. |
| The Haptics lab and Key calibration | Inert | Both screens close at once outside `.debug`. Every lab call, and Key calibration, is refused natively. Settings hides both rows: the lab's state is null outside Debug. A haptic pattern tried in the lab plays only in Debug. |
| The guide's still frames for review (`GuideFrameActivity`) | Inert | **Changed in this audit:** not exported in release, so adb can't open it either. `tools/debug.sh` exports it in Debug only. It still closes at once outside `.debug`. |
| The adb broadcasts: the panel check (`com.titankeys.debug.PANEL`) and the idle bar (`com.titankeys.debug.IDLE_BAR`) | No | Registered only in `.debug`. Every other receiver the keyboard registers is for the system's screen on and off, not exported. |
| Exact typing's log lines | No | Only `.debug` writes them, and they hold key codes and states, never text. |
| The guide's clipboard sandbox | Yes, on purpose | Part of the clipboard lesson: its own clips while that slide is open, never saved. Not a dev hook. |

**Log lines:** no log line anywhere writes the field's text, a typed or saved word, a suggestion or a clip. Error lines carry the
engine's fixed messages, such as "TitanKeys can't save that word.", never the word. A few debug-level lines stay in release and
say only what state the UI was in:
- the bar's held word: whether the finger was on the screen and the menu opened;
- the playground opening and closing;
- the number of examples sent;
- the announcements feed's entry count.

`tools/checks/debug-only.test.ts` holds all of this:
- the release manifest has exactly its 16 components, its exported flags and its 6 permissions;
- every gate above;
- no log line puts typed text into the log.

## For the owner

1. **Ink's Light Phone receiver.** Ink's build adds `InkLightSdkReceiver`: exported, behind a permission
   called "normal" that nothing defines, so any app that declares it can send to it. It only marks the app for the Light Phone's
   launcher, and does nothing on a Titan. Leave it, or have the build drop it (an Ink patch line).
2. **Size: 116 MB, nearly all of it engine 2.0.** Its next-word model is 83 MB of the 112 MB. A GitHub release takes it easily,
   but it's a big download, and it matters for the Play plan: an app bundle's base download is limited to 200 MB. Leave it for 2.1, or ask for a smaller model later.
3. **Record a bug holds key presses.** It ships in every build, as #64 meant: the user starts it, it stops by itself, it skips
   password and private fields, and only the user shares the file. It never holds the field's text. But its keys and the
   keyboard's decisions are enough to rebuild most of what was typed while it ran. Its own screen and the privacy policy should
   say so plainly, if they don't already.
4. **The debug screens are compiled into release, inert.** Four screens and the lab's code are in the APK and can't be opened.
   Moving them into `android/debug` like the recorder would take them out entirely. It's a bigger change, with no effect on what
   a user can reach.
5. **Debug-level log lines.** The four above are harmless, and stripping them would need minifying, which the build doesn't do.
   Leave them.

The phone checks for the pre-release (install the release build over the last one, the guide, the front page, Record a bug and a
backup) are the owner's three days of use. This audit didn't run any.
