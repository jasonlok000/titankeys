# Contributing to Atlas

Thank you for your help with Atlas. This guide tells you how to report a problem, suggest an idea, and send a change.

## Rules

- Open an issue for all bugs, ideas, and questions.
- Add a label to each issue.
- Do not open a pull request before I give the work to you.
- Test each change on a real Unihertz Titan 2 Elite.
- Use issues for all contributions. To send feedback without a GitHub account, use **Settings > Send feedback** in Atlas.

## Philosophy

Atlas has these goals:

- **Local:** All data stays on the device. Do not add network calls, analytics, or tracking.
- **Simple:** Write clear code. Clear code is better than clever code.
- **Made for the Titan 2 Elite:** Each feature must operate well with the physical keyboard.

## Send feedback without GitHub

You do not need a GitHub account to report a bug or suggest an idea.

1. Open Atlas.
2. Go to **Settings > Send feedback**.
3. Write your message, then send it.

I read all feedback. If a report needs work, I open an issue for it.

## Open an issue

1. Search the [issues](https://github.com/juleshall4/atlas/issues) first. Make sure that your issue is not a duplicate.
2. Open a new issue.
3. Add one of these labels:

   | Label | Use it for |
   | --- | --- |
   | `bug` | Something does not operate correctly. |
   | `enhancement` | A new feature or a change to a feature. |
   | `accessibility` | A problem for people with disabilities. |
   | `documentation` | A problem in the README or the guide. |
   | `question` | A question about Atlas. |

4. For a bug, write these items:
   - The Atlas version. Find it in **Settings > About**.
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
- `adb`, to install Atlas on your device
- A Unihertz Titan 2 Elite with USB debugging on

### Steps

1. Fork the Atlas repository on GitHub.
2. Clone [Ink](https://github.com/No-Scrolling/ink) and your fork into the same folder:

   ```bash
   git clone https://github.com/No-Scrolling/ink
   git clone git@github.com:YOUR_USERNAME/atlas.git
   cd atlas
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
4. Write all text that the user sees in [ASD-STE100 Simplified Technical English](https://www.asd-ste100.org/). This includes the guide, settings, and release notes.
5. Run the checks:

   ```bash
   bun run check
   ```

## Test on your device

Each pull request must have a test on a real Titan 2 Elite. An emulator does not have the physical keyboard.

1. Build and install Atlas Debug:

   ```bash
   bash tools/debug.sh --install
   ```

   Atlas Debug installs next to Atlas. It does not change your saved words.

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

You can use AI tools to help you write code. Tell me how you used them.

In the pull request description, write these items:

- If you used AI (yes or no).
- The tools that you used, for example Claude Code or GitHub Copilot.
- How much you used them, for example "helped find the bug" or "wrote most of the code".

Write the pull request description yourself. Do not use AI to write it.

## License

Atlas uses the GPL-3.0-only license. When you send a contribution, you agree that it uses the same license.
