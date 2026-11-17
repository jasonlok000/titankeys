# F: drafts still in the code

This is every text in the app that is still marked as F's draft on main (cac09377, 2026-11-17), grouped by the screen it shows on, so F can make one wording pass over all of them.
- Each entry gives the file and line of its marker, then the text as it stands.
- Nothing here has been changed: F rewrites the strings in place, then drops each "F: draft" marker.
- Lines marked "F (docs/words-m1.md)" are F's already, done in M1. They're listed at the end, without their text.

## Settings, main page

- `app/settings/index.tsx:57`: the Typing section's only row, once a second language is on:
  - **Typing in each language**, with the languages on as its value: "English, Español, 中文".

## Settings > Languages

- `app/settings/languages/index.tsx:80`: the list page.
  - Title: **Languages**
  - Subtitle: **Pick a language to turn on how you type it. Hold Sym to hop between them.**
- `app/settings/languages/index.tsx:63`: the subtitle under each language in the list:
  - **«English name» · experimental**, or the English name alone where it differs from the language's own name.
- `app/settings/languages/index.tsx:70`: a script heading in the list:
  - **Japanese**. The others, Latin letters, Cyrillic, Greek, Georgian, Hebrew, Devanagari, Thai, Korean and Chinese, are on the line above, unmarked.

## Settings > Languages > a language

- `app/settings/languages/language.tsx:46`: the subtitle under an experimental language's name:
  - **«English name». Experimental: it types, and its words and fixes are still being tuned.**
- `app/settings/languages/language.tsx:60`: shown once, the first time a second language is on:
  - **Typing settings for each language now live on its page.**
- `app/settings/languages/language.tsx:66`: the limits:
  - **Four languages at a time, tops. Turn one off first.**
  - **Four at a time in one language, tops. Turn one off first.**
- `app/settings/languages/language.tsx:54`: the Options rows' labels. All but one are the labels Settings already used:
  - New: **Double Space types «stop»**, as in "Double Space types 。". Where the stop is ".", it stays **Double Space types a full stop**.
  - Reused: Fix typos as you type, What gets fixed, Capital letter to start a sentence, Space after a sentence, Space after , ; :, Hold a letter for its symbol, Swipe to type words, Spell out shorthand.
- `lib/how-to.tsx:9`: the section between Input methods and Options:
  - Title: **How to type**
  - Its lines are F's own drafts in `docs/howto-drafts.md`, on C's branch; they aren't on main yet.

## Settings: what's off in the phone (lib/system-needs.tsx:12, every line)

Each one shows under a feature's row: a line, then a button to the system screen.

| Need | Line | Button |
|---|---|---|
| Keyboard off | TitanKeys Keyboard is off in Android, so none of this works yet. | Turn on TitanKeys Keyboard |
| Not the keyboard | TitanKeys isn't your keyboard right now, so none of this works yet. | Make TitanKeys your keyboard |
| Overlay | Display over other apps is off, so the touchpad can't reach TitanKeys. | Allow display over other apps |
| Scroll assistant | The phone's Scroll assistant is off, so the touchpad doesn't type. | Open Scroll assistant |
| Accessibility | Blocking needs TitanKeys Keyboard on in Accessibility. It's off, so apps still scroll. | Turn it on in Accessibility |
| fn key | fn isn't a Programmable key, so it can't work as Ctrl. | Open Shortcut keys |
| Sym key | Sym isn't a Programmable key, so Alt + Sym can't reach TitanKeys. | Open Shortcut keys |
| Contacts | Android's Contacts permission is off, so TitanKeys can't read the names. | Allow contacts |

## Settings > Haptics, "Other" (lib/haptics.ts:78)

| Haptic | Label | About |
|---|---|---|
| screen_tap | Tap on the screen | When you tap a tile, button or item the keyboard shows on the screen, and it has no haptic of its own. |
| bug_recording | Record a bug starts | When Record a bug starts. |
| bug_recording_stopped | Record a bug stops | When Record a bug stops. |
| guide_celebration | Guide celebrations | When you finish a part of the guide. |
| guide_blocked | Guide blocks | When the guide blocks something a slide doesn't teach yet. |
| guide_hint | Guide hints | When the guide shows a hint. |

## Settings > About > Licences (app/settings/licences/index.tsx)

The titles are the projects' names. Only the subtitles are drafts.

- `:26` Noto Sans Georgian: **SIL Open Font Licence. The Georgian letters in Settings.**
- `:28` Noto Sans Hebrew: **SIL Open Font Licence. The Hebrew letters in Settings.**
- `:30` Noto Sans Thai: **SIL Open Font Licence. The Thai letters in Settings.**
- `:32` Noto Sans CJK: **SIL Open Font Licence. The Korean and Chinese characters in Settings.**
- `:40` librime-octagram: **GPL-3.0. Lets Rime weigh which words go together.**
- `:46` words.hk (粵典): **Public domain. How often Cantonese words are used, to put the likelier ones first.**
- `:50` rime-cangjie and rime-quick: **LGPL-3.0. Cangjie 5 and Quick 5; the Cangjie table, from 倉頡之友, is GPL.**
- `:53` Mozc: **BSD-3-Clause. Turns what you type into Japanese.**
- `:53` Mozc's dictionary: **IPAdic, the Okinawa dictionary, Japanese Usage Dictionary and Tamachi, each under its own licence.**

## The guide (android/kotlin/com/titankeys/keyboard)

- `GuideCoach.kt:71`: when the taps are too slow:
  - **That's too slow, not quick taps. Tap faster.**
- `GuideCoach.kt:77`: as Space taps start a mode, where «way» is left or right:
  - **Select mode on. Slide «way», lift at the end.**
  - **Cursor mode on. Slide «way», lift in the word.**
- `GuideSteps.kt:65`: the setup's Back part. The body has room for 72 characters, and "the touchpad holds" is new.
  - Title: **Let TitanKeys handle Back.**
  - Body: **Turn TitanKeys Keyboard on: Back takes one press, the touchpad holds.**
  - Button: **Open Accessibility**
  - Help: **Switch greyed out? Android is being careful. In App info, open the menu and allow restricted settings.**

## The keyboard

- `TitanKeysIme.kt:3931`: what a screen reader hears when the language changes:
  - **Typing in «English name»**, as in "Typing in Spanish".

## TitanKeys Debug only

- `HapticTimeline.kt:156`: the Haptics lab's description of a held flick:
  - **…the lift, and no dots: the chomper ends with the delete.**

## Android's Accessibility screen and the Back disclosure (android/src/main/res/values/strings.xml)

The touchpad part is new in all four: the service now also keeps the touchpad on the keys when another keyboard leaves a layer behind.

- `:3` back_service_description, the service's text on Android's Accessibility screen: **Makes one press of Back close the keyboard and go back, and keeps the touchpad on the keys when a notification shows or another keyboard left a layer behind. At a few moments, such as while the Sym panel is open, it sees the keys you press. It can't read your screen, and it doesn't keep or send anything.**
- `:85` back_disclosure_touchpad: **Touchpad: it keeps the touchpad on the keys when a notification pops up or another keyboard left a layer behind.**
- `:89` back_disclosure_no: **Say no and TitanKeys still types and swipes. Back may take two presses, and a notification or another keyboard's leftover layer can take the touchpad. You can turn it on later in Settings > Setup.**
- `:98` back_disclosure_more_touchpad, in the opened detail: **For the touchpad, it keeps one invisible dot of a window on top while you type. Your touchpad swipes reach TitanKeys through it, and nothing else does.**

## F's already (marked "F (docs/words-m1.md)")

No pass needed. They're listed so a search for "F" finds them here:
- `app/settings/chinese.tsx:5`
- `app/settings/guide-boring.tsx:47, 50, 69`
- `app/settings/index.tsx:51`
- `app/settings/licences/index.tsx:37`
- `lib/haptics.ts:45`
- `GuideSteps.kt:38, 71, 76, 87, 175, 980, 993, 1154, 1489, 2081`
- `Languages.kt:360`
- `RimeEngine.kt:284`
- `strings.xml:2, 80`
