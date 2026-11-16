# Agent rules

- Read [CONTRIBUTING.md](CONTRIBUTING.md) before you change code, and follow it.
- Run `bun run check`, then the tests for the parts that you changed:
  - Words and suggestions: `bash tests/words/run.sh`
  - Emoji: `bash tests/emoji/run.sh`
  - Swipe joining (no phone needed): `bash tests/swipe/joins.sh`
  - Swipe recognition: `bash tests/swipe/run.sh`
  - Swipe preview: `bash tests/preview/run.sh`
  - Keyboard show and hide: `python3 tests/ime-window/run.py <app package>`
  - App screens: `APP=com.titankeys.debug bun run check:phone`
  - Touch simulator (no phone needed): `bash tools/dev/touch/test/run.sh`
  - Touchpad, Sym, fn and held keys, by the touch simulator: `python3 tools/dev/touch/checks.py` (see [tools/dev](tools/dev/README.md#touch))
- The swipe recognition, swipe preview, keyboard, app screen and touch simulator checks need the phone connected. If it is not
  connected, say which tests you did not run.
