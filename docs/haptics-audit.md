# Every press in TitanKeys' own screens

The audit for "Add haptics to every touch screen press on the app" (owner, 2026-11-13). It lists every control you can touch on
TitanKeys' own screens, and what it plays. The rules are in [haptics.md](haptics.md), under TitanKeys' own screens.

- **Press** is the new one: a single Texture tick of 17 ms, a click. Every press that has no meaning of its own plays it.
- **Lock** and **Release** are a switch turned on and off. **Texture** is a stepper's step. **Confirm** is an option picked.
  **Gone** is a confirmed delete. **No** is something the app refused. These keep their meaning, and no Press plays with them.
- Before this, every plain press played nothing. Android's own click was off and still is.
- Every Ink page has Ink's header with **←**, and some have **×**: both play Press, as Ink sends them through the same place
  (`appPressHaptic`, see haptics.md).

Each press goes through one place: `onPress` on the native screens, and `appPressHaptic` with `InkPress` in Ink's pages.
tools/checks/app-haptics.test.ts fails on a press that goes round them, so a new control can't be left silent.

## Ink's pages (Settings and everything in it)

| Control | Plays now | Played before |
|---|---|---|
| **Every page:** ← in the header | Press | Nothing |
| **Every page:** × in the header (Settings, What's new on first open) | Press | Nothing |
| **Every page:** a row that opens a page | Press | Nothing |
| **Every page:** a row that opens a web page (Source code, Privacy policy, Send feedback, Full release notes, a licence) | Press | Nothing |
| **Every page:** a switch, turned on | Lock | Lock |
| **Every page:** a switch, turned off | Release | Release |
| **Settings:** Key calibration (TitanKeys Debug) | Press | Nothing |
| **About:** What's new, Source code, Based on Atlas, Licences, Privacy policy | Press | Nothing |
| **Record a bug:** Record a bug, Allow notifications, Stop, Share it, Record again | Press | Nothing |
| **Record a bug:** Delete it | Gone | Gone |
| **Share swipes:** Share my swipes | Lock, Release | Lock, Release |
| **Share swipes:** Delete waiting swipes | Gone | Gone |
| **Share swipes:** Delete what I've sent, Copy ID, What gets sent, Privacy policy | Press | Nothing |
| **Delete your swipes?:** Delete my swipes | Gone | Gone |
| **Before you share:** I agree | Lock | Lock |
| **Before you share:** No thanks, Privacy policy | Press | Nothing |
| **Your words:** Saved words, Blocked words, Export words, Import words, Remove learned words | Press | Nothing |
| **Saved words, Blocked words:** Add word, ✕ on a word | Press | Nothing |
| **Saved words, Blocked words:** a word the app can't take | No | No |
| **Forget all words?:** Remove all learned words | Gone | Gone |
| **Words exported, Words imported:** Done | Press | Nothing |
| **Couldn't export, Couldn't import:** Try again | Press | Nothing |
| **Message apps:** Add app, an app (flips how Enter sends), ✕ on an app | Press | Nothing |
| **Add app** (Message apps, Exact typing, Hide the bar): an app | Press | Nothing |
| **Enter key, Space after a sentence, English spelling:** an option | Confirm | Confirm |
| **Exact typing, Hide the bar:** Add app, ✕ on an app | Press | Nothing |
| **What gets fixed:** Spell out shorthand | Press | Nothing |
| **Guide:** The fun guide, The boring guide | Press | Nothing |
| **The boring guide:** a topic opening or closing | Press | Nothing |
| **What's new:** a release opening or closing, Full release notes | Press | Nothing |
| **Haptics:** All haptics and each haptic's switch | Lock, Release | Lock, Release |
| **Haptics lab page** (TitanKeys Debug): Open the lab | Press | Nothing |
| **Haptics lab page:** Play the dots, and Shorter, Longer and Reset for the dots' wait | The lab's flick, no Press | The lab's flick |
| **Haptics lab page:** a triple press selection end | Confirm | Confirm |
| **Hold for symbols, Keep swiping:** a preset | Confirm | Confirm |
| **Hold for symbols, Keep swiping:** Shorter, Longer | Texture | Texture |
| **News:** Allow notifications, a news item's action, Don't show again | Press | Nothing |
| **Typing log** (TitanKeys Debug): Export log, Delete all | Press | Nothing |
| **Delete the log?:** Delete the log | Gone | Gone |
| **Setup:** each step and each phone setting | Press | Nothing |
| **Shorthand:** Add shorthand, one of yours, ✕ on one | Press | Nothing |
| **Shorthand, Snippets:** Save | Press, or No if the app can't take it | No if refused, else nothing |
| **Snippets:** Add snippet, a snippet, ✕ on one, Start of each shortcut, Export snippets, Import snippets | Press | Nothing |
| **Start of each shortcut:** a start | Confirm | Confirm |
| **Light touch:** Add a key, ✕ on a key | Press | Nothing |
| **Front page, first run:** Start | Press | Nothing |
| A text field, tapped to type in it | Nothing | Nothing |

A text field plays nothing: Ink doesn't report a tap that puts the caret in a field as a press, and the caret and the keyboard
show where it went. Typing in it is key presses.

## The guide and the playground (native)

| Control | Plays now | Played before |
|---|---|---|
| **Guide:** × in the top bar | Press | Nothing, then a Change swell as the pop-up opened |
| **Leaving already?:** Keep going, Start again, Leave, a touch outside the card | Press | Nothing |
| **Guide:** Skip, Next | Press | Confirm (Guide Next) |
| **Guide:** Back | Press | Nothing |
| **Guide:** a slide's offer button (a setup step, Share my swipes) | Press | Nothing |
| **Guide:** I'd rather not paste, ticked or unticked | Lock, Release | Lock, Release |
| **End screen:** Go type | Press | Confirm (Guide Next) |
| **End screen:** Share my swipes, turned off | Release | Release |
| **End screen:** Share my swipes, to turn it on (opens the policy) | Press | Nothing |
| **End screen:** Start again, The boring guide | Press | Nothing |
| **End screen:** a tap that ends the roast | Press | Nothing |
| **Playground:** Guide, Settings in the header | Press | Nothing |
| **Playground:** the text box, the words a minute | Nothing: a text field, and a label you can't press | Nothing |

Guide Next is gone: Next and Skip are plain presses, so they play Press, as the owner's list has it. Its switch in Settings >
Haptics went with it.

## Other native screens

| Control | Plays now | Played before |
|---|---|---|
| **Before you share** (the guide's full disclosure): Turn on Share swipes | Lock | Nothing |
| **Before you share:** Not now | Press | Nothing |
| **Before you share:** "Trust me bro." | Nothing: a line of text, not a button | Nothing |
| **Let TitanKeys handle Back?:** Agree, No thanks | Press | Nothing |
| **Key calibration** (TitanKeys Debug): its buttons | Press | Nothing |
| **Haptics lab** (TitanKeys Debug): a haptic in the list, a family, All families, Reset | Press | Nothing |
| **Haptics lab:** Play, Play a hold | What they play, no Press | What they play |
| **Recorder, Space gestures, Touch size** (TitanKeys Debug): each button | Press | Nothing |
| Android's own dialogs, such as a permission request, and its back gesture | Nothing | Nothing |

Android's dialogs and its back gesture are the system's, not TitanKeys', so they can't be reached.

## Not covered: the keyboard itself

The bar, the word menu, the Sym, emoji, quick settings and clipboard panels, and exact typing's bar play the keyboard's own
haptics, in haptics.md's families table. They aren't part of this audit.
