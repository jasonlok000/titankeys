# Contributing to TitanKeys

Thank you for your help with TitanKeys. This guide tells you how to report a problem, suggest an idea, and send a change.

## Rules

- Open an issue for all bugs, ideas, and questions.
- Add a label to each issue.
- Do not open a pull request before I give the work to you.
- Test each change on a real Unihertz Titan 2 Elite.
- Use issues for all contributions. To send feedback without a GitHub account, use **Settings > Send feedback** in TitanKeys.

## Philosophy

TitanKeys has these goals:

- **Private:** What the user types stays on the device. TitanKeys uses the internet only as the [privacy policy](https://jasonlok000.github.io/titankeys/privacy/) says: signed news, and swipe contributions only with consent. Do not add other network calls, analytics, or tracking.
- **Simple:** Write clear code. Clear code is better than clever code.
- **Made for the Titan 2 Elite:** Each feature must operate well with the physical keyboard.

## Bring TitanKeys to another device

TitanKeys is made for the Unihertz Titan 2 Elite. If you own another phone with a physical keyboard, such as the Titan 2 or a future Communicator, you are welcome to make TitanKeys work on it.

**Do it inside TitanKeys, not in a fork.** A port inside this app gets every new feature and fix, and it stays maintained in one place. Open an issue first to describe the device, and wait for approval, as for any other work.

**Fork if your direction is different.** If you want TitanKeys to go in a direction that I do not agree with, for example a different philosophy, then a fork is the right choice. The GPL-3.0 licence allows it. Give the fork its own name, and keep the credits.

**What is specific to the Titan 2 Elite today:**

- The touchpad: its input device name and ranges (`Touchpad.kt`), and how the firmware sends it to the keyboard's window (`TouchpadOverlay.kt`).
- The keys: the layout (`Layout.kt`), the phone's key map (`Keys.kt`), and the drawing of the keys in the guide.
- The calibration of touch positions (`android/assets/calibration`).
- The vendor settings that setup reads and opens (`PhoneSetup.kt`).
- Swipe engine 2.0: its models were trained on swipes from the Titan 2 Elite's touchpad. Another touchpad needs its own recordings, made with the tools in `android/debug/recorder`.

**Rules for a port:**

- Nothing gets worse on the Titan 2 Elite. A device profile chooses what is different, and the Titan 2 Elite keeps its current behaviour.
- Test on the real device, and on a Titan 2 Elite if you change shared code.
- Share recordings from the new device, so that the engine can learn its touchpad.

## Languages

TitanKeys is English only for now. Other languages are welcome, but only from a native speaker who really knows the language.

I won't build a language I don't speak myself, because I can't tell when it's wrong. So a language happens only if a native speaker brings it and keeps it right: the word list, the autocorrect and the guide's words.

Want to help? Say which language in [issue #56](https://github.com/jasonlok000/titankeys/issues/56).

## Open an issue

1. Search the [issues](https://github.com/jasonlok000/titankeys/issues) first. Make sure that your issue is not a duplicate.
2. Open a new issue.
3. Add one of these labels:

   | Label | Use it for |
   | --- | --- |
   | `bug` | Something does not operate correctly. |
   | `enhancement` | A new feature or a change to a feature. |
   | `accessibility` | A problem for people with disabilities. |
   | `documentation` | A problem in the README or the guide. |
   | `question` | A question about TitanKeys. |

4. For a bug, write these items:
   - The TitanKeys version. Find it in **Settings > About**.
   - The steps that cause the bug.
   - The result that you expect, and the result that you see.
   - A screen recording, if possible.

5. For a feature, write these items:
   - The problem that you have now.
   - The change that you want.
   - Other ways to solve the problem, if you know of them.

## Get approval

I read each issue. If I approve it, I add a comment that tells who does the work:

- **I do the work.** You do not need to do more. I link the fix to the issue.
- **You do the work.** I give the issue to you. Then you can start.

If you want to do the work, write this in the issue. Wait until I give the issue to you before you start. This makes sure that your time is not wasted on work that I cannot merge.

## Set up your computer

### Prerequisites

- [Bun](https://bun.sh/)
- Java 21 (OpenJDK)
- The Android SDK
- `adb`, to install TitanKeys on your device
- A Unihertz Titan 2 Elite with USB debugging on

### Steps

1. Fork the TitanKeys repository on GitHub.
2. Clone [Ink](https://github.com/No-Scrolling/ink) and your fork into the same folder:

   ```bash
   git clone https://github.com/No-Scrolling/ink
   git clone git@github.com:YOUR_USERNAME/titankeys.git
   cd titankeys
   ```

3. Install the dependencies:

   ```bash
   bun install
   ```

4. Set up the native toolchain:

   ```bash
   bash tools/setup-native.sh
   ```

5. Set `JAVA_HOME` and `ANDROID_HOME`. For example, on macOS with Homebrew:

   ```bash
   export JAVA_HOME=/opt/homebrew/opt/openjdk@21
   export PATH="$JAVA_HOME/bin:$PATH"
   export ANDROID_HOME="$HOME/Library/Android/sdk"
   ```

## Make your change

1. Make a branch from `main`. Give the branch a short name, for example `fix-cursor-swipe`.
2. Make your change. Keep each pull request to one issue.
3. Follow the style of the code near your change.
4. Write all text that the user sees in the TitanKeys voice. See [Writing](#writing).
5. Run the checks:

   ```bash
   bun run check
   ```

## Writing

TitanKeys talks like a person: casual, direct, a bit cheeky. This goes for every text the user sees, including the guide, settings, release notes, the README and the website.

- Use contractions: don't, can't, it's, you're.
- Say "press", not "push". Say "once" and "twice", not "one time" and "two times".
- Say "works", not "operates". Say "pick" or "choose", not "select".
- Keep sentences short. Say what to do, not how the system behaves. One idea per line where you can.
- Talk to the user as "you". TitanKeys can say "we" for the people behind it, such as in privacy texts.
- Be dry and confident. Personality comes from plain, slightly blunt statements, not from a joke in every line.
- Swearing is fine, but rarely: only where it really lands.
- A joke never hides the instruction. The user must still know exactly what to do.
- Keep labels short enough for their row on the Titan's screen. Don't change a setting's name without changing every text that mentions it.

Avoid:

- Patterns that sound machine-written: "not just X, but Y", chains like "no X, no Y, no Z", "X. No Y." punchlines, "Wild, right?", lists of three for rhythm, and dashes for asides.
- Lines that try too hard: winking at the reader, calling the user names, forced jokes. If a line makes you wince, cut the joke and keep it plain.

Keep these clean, with no swearing, but still casual: the Accessibility description and its disclosure, permission reasons, consent screens, the privacy policy, store listings, and anything about deleting or losing data.

## Test on your device

Each pull request must have a test on a real Titan 2 Elite. An emulator does not have the physical keyboard.

1. Build and install TitanKeys Debug:

   ```bash
   bash tools/debug.sh --install
   ```

   TitanKeys Debug installs next to TitanKeys. It does not change your saved words.

2. Do the steps from the issue again. Make sure that the problem does not occur.
3. Make sure that swipe typing, suggestions, and cursor movement still operate correctly.

## Open a pull request

1. Push your branch to your fork.
2. Open a pull request into `main`.
3. In the description, write these items:
   - A link to the approved issue, for example `Closes #12`.
   - What you changed.
   - How you tested it on the Titan 2 Elite.
   - A screen recording, if the change is visible.
   - How you used AI, if you used it. Refer to [AI use](#ai-use).
4. Write short commit messages in lowercase, for example `fix cursor jump on swipe`.

I read each pull request. I can ask for changes before I merge it.

## AI use

Pull requests made with AI help are welcome. Use the tools that help you, and tell me how you used them.

In the pull request description, write these items:

- If you used AI (yes or no).
- The tools that you used, for example Claude Code, GitHub Copilot, or ChatGPT.
- How much you used them, for example "wrote the tests", "helped find the bug", or "wrote most of the code".

## License

TitanKeys uses the GPL-3.0-only license, the same as Atlas, which it's based on. When you send a contribution, you agree that it uses the same license.
