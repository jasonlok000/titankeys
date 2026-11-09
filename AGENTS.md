# Agent rules

- Read [CONTRIBUTING.md](CONTRIBUTING.md) before you change code, and follow it.
- Never create a pull request. When the change is ready, stop and tell the human to open it.
- When you tell the human, say how you helped with the change, so they can fill in [AI use](CONTRIBUTING.md#ai-use) in the pull request.
- Run `bun run check`, then the tests for the parts that you changed:
  - Words and suggestions: `bash tests/words/run.sh`
  - Emoji: `bash tests/emoji/run.sh`
  - Swipe recognition: `bash tests/swipe/run.sh`
  - Swipe preview: `bash tests/preview/run.sh`
  - Keyboard show and hide: `python3 tests/ime-window/run.py <app package>`
  - App screens: `APP=com.titankeys.debug bun run check:phone`
- The last four tests need the phone connected over USB. If it is not connected, say which tests you did not run.
