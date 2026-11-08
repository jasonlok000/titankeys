# Agent rules for Atlas

Read [CONTRIBUTING.md](CONTRIBUTING.md) before you change code. Follow its rules and steps, the same as a human contributor.

The human opens all issues and pull requests. Never create an issue or a pull request. When your change is ready, stop and tell the human, so they can open it.

## Tests

Before you tell the human the change is ready, run `bun run check`, then each test whose area your diff touches:

| Diff touches | Run |
| --- | --- |
| `android/kotlin/com/atlas/engine/` (saved words, blocked words, suggestions) | `bash tests/words/run.sh` |
| `engine/EmojiPages.java` or `android/assets/emoji.txt` | `bash tests/emoji/run.sh` |
| `engine/`, `keyboard/Layout.kt`, `org/futo/ml/inference/` or the swipe models | `bash tests/swipe/run.sh` (phone, needs `KOTLIN_HOME`) |
| The swipe preview in `keyboard/` | `bash tests/preview/run.sh` (phone) |
| When the keyboard shows or hides (`keyboard/AtlasIme.kt`) | `python3 tests/ime-window/run.py <app package>` (phone, a text field must be focused) |
| Screens in `app/` | `APP=com.atlas.debug bun run check:phone` (phone, Atlas Debug installed) |

The phone tests need the Titan 2 Elite connected over USB. If it is not connected, say which phone tests you could not run.
