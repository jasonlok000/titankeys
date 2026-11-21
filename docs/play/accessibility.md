# The accessibility service on Google Play

For #36. Checked against [Use of the AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491) and the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) on 2026-11-10, and the service's uses checked against the 3.0.0 code on 2026-11-21. This is not legal advice.

## What the service does in 3.0 (from the code)

`BackService` (`android/kotlin/com/titankeys/keyboard/BackService.kt`, `android/src/main/res/xml/back_service.xml`):

- `isAccessibilityTool="false"`, which is correct: TitanKeys is not a tool for people with disabilities.
- `canRetrieveWindowContent="false"`: it can't read the screen. It asks for no accessibility events.
- **Back:** with the keyboard up, Back only hides it; with the keyboard gone, the app always gets its Back; with a code pending, Back clears the code (owner, 2026-11-21). The service can send a system Back (`GLOBAL_ACTION_BACK`, `BackService.back`).
- **Keys:** `canRequestFilterKeyEvents="true"`. It turns key filtering on only while the keyboard asks for it (`TitanKeysIme.updateKeyWatch`):
  - while the Sym panel is open, and until a key the panel took is let go, so that Enter and Backspace turn the panel's pages and don't reach the app;
  - while an input method is building text: a code pending on the bar (Chinese) or Chinese's bigger menu open (`engineBuilding`), so that Enter types the code or turns the menu's page and never sends, and Backspace works on the code, even in apps that take those keys first;
  - in the Smart Launcher search field, always, so TitanKeys types there as in any other app, and Back hides the keyboard there instead of the launcher going home;
  - between the first and second press of Back, while the keyboard holds the touchpad, so the second press reaches the app.
- **Touchpad:** while the keyboard has the touchpad, it shows a 1×1 `TYPE_ACCESSIBILITY_OVERLAY` window (`TouchpadOverlay`) on top, because the firmware gives a touchpad swipe to the window on top: the shade when a notification pops up, or a layer another keyboard left behind. It takes no screen touches and reads nothing. It's hidden on the lock screen and when the screen is off.
- Nothing it sees is stored or sent.

**Check before you file the declaration:** 3.0 added a new time when the service hears keys: whenever an input method has a code on the bar (Chinese, Japanese, Korean, Vietnamese and Hindi, `engineBuilding`) or Chinese's bigger menu is open. Then Enter types the code and Backspace edits it. The disclosure's detail list, the declaration text and the video (shot 7b) include it. Any later new use of `BackService.watchKeys` or `performGlobalAction` needs the disclosure, the declaration and the video changed too. Google: "If you change how the API is used, you must submit the form again."

## What Google requires

Because `isAccessibilityTool` is false:

1. **A declaration in Play Console** (the App content page): the purpose, whether data is collected or shared through the service, and a link to a short video.
2. **A prominent disclosure in the app:**
   - inside the app, shown during normal use, not only in a menu or in settings;
   - says what data the service accesses and how it is used;
   - needs an active "yes" (a tap to agree);
   - not only in the privacy policy;
   - **separate from other disclosures** about personal data, so not combined with the contributions screen;
   - the service description in Android's Accessibility settings does not count as the disclosure.
3. **No autonomous actions.** Rule-based actions ("if X happens, do Y") are allowed. TitanKeys' Back and key handling is rule-based.

## 1. The Play Console declaration (draft answers)

| Question | Answer |
|---|---|
| Is your app an accessibility tool? (if asked) | No |
| Why does your app need the AccessibilityService API? | **App functionality** |
| Short description (if there is a text box) | "TitanKeys is a keyboard for the Unihertz Titan 2 Elite. Its accessibility service does three things. It handles Back: with the keyboard up, Back only hides it, and with the keyboard gone, Back reaches the app. At set times, it receives key presses before the app does, so that the keyboard's own keys work: while the symbol panel is open, while a code is being typed in an input method such as Chinese, Japanese or Korean, in the Smart Launcher search field, and between two presses of Back. While you type, it keeps a 1-pixel window on top, so the keyboard's touchpad still reaches the keyboard when a notification pops up. It can't read the screen, and nothing it receives is stored or sent." |
| Does your app collect or share personal or sensitive data through the accessibility capabilities? | **No.** Key presses are handled at once on the phone and never kept or sent. |
| Video link | The declaration video (shot list below), on YouTube as unlisted. **Not verified:** Google's page doesn't say where the video must be hosted. An unlisted YouTube link is the common choice. |

## 2. The in-app disclosure

**Where:** in the guide's setup slide, at the "Let TitanKeys handle Back" step, **before** it opens Accessibility. The same screen shows again when the user taps "Turn on TitanKeys in Accessibility" in Settings > Setup. It is its own screen, with nothing about other permissions or data on it.

**Look (#79):** the same style as the Share swipes screen. A short title and one line for each thing the service does, then one line on what it never does and one that TitanKeys works without it. "What exactly does it see?" opens the full detail in place, on the same screen, not on another page. One orange pill, **Agree**, and a quiet **Not now** under it, both pinned to the bottom so they're on screen at any font size. It stays plain, with no jokes: this is the screen Play's reviewers look at.

**Buttons:** "Agree" and "Not now". Only "Agree" counts as agreement. Back, Home or tapping outside counts as "Not now", never as agreement. The screen never closes by itself.

**The text** (`back_disclosure_*` in `android/src/main/res/values/strings.xml`; the writer F's words, docs/words-m1.md):

> **Let TitanKeys handle Back?**
>
> TitanKeys uses Android's Accessibility service for three things. It's optional.
>
> - **Back:** one press hides the keyboard, and the next goes back in the app.
> - **Keys:** at a few moments, it sees which keys you press before the app does.
> - **Touchpad:** it keeps the touchpad on the keys when a notification pops up or another keyboard left a layer behind.
>
> **It can't read your screen, and it doesn't keep or send anything it sees.**
>
> Say no and TitanKeys still types and swipes. Some apps, such as WhatsApp, may need a second press of Back. In Smart Launcher's search, Back goes home instead of hiding the keyboard. A notification or another keyboard's leftover layer can take the touchpad. You can turn it on later in Settings > Setup.
>
> What exactly does it see?
>
> [Agree]
>
> Not now

**"What exactly does it see?"** opens, under the lines:

> It sees which keys you press only at these moments:
>
> - while the Sym panel is open, so Enter and Backspace turn its pages instead of reaching the app;
> - in Smart Launcher's search field, where Smart Launcher would take your keys first, so TitanKeys types there as in any other app, and Back hides the keyboard instead of going home;
> - for a moment after you press Back, so your next press of Back reaches the app.
>
> The keys are used on your phone straight away, then forgotten. It never sees the text in your apps.
>
> For the touchpad, it keeps one invisible dot of a window on top while you type. Your touchpad swipes reach TitanKeys through it, and nothing else does.

**Against Google's rules:** what the service accesses (key presses at named moments, and an overlay for the touchpad) and how it's used are on the first screen, one line each, with the full list one press away on the same screen. The active yes is the pill. It's its own screen, shown during normal use before Accessibility opens, and not combined with the Share swipes screen.

After **Agree**, the app opens Android's Accessibility page, as the guide does today. Android then shows its own warning, which the user also accepts.

After **Not now**, the guide moves on to the next step. The setup row stays "Not done", and tapping it shows this disclosure again.

**Needs a code change:** the guide's front page waits until the service is on today, so a user can't decline. It must let the user go on without it (see `build-tasks.md`).

**The service description in Android's settings** (`back_service_description`) doesn't replace the disclosure. It's shortened to match it (#79, F's words): "Handles Back, and keeps the touchpad on the keys when a notification shows or another keyboard left a layer behind. At a few moments, such as while the Sym panel is open, it sees the keys you press. It can't read your screen, and it doesn't keep or send anything."

## 3. Shot list for the declaration video

Google's list of what the video must show, in order. Record the screen with `adb screenrecord` or the phone's recorder, and add captions where the keys are pressed, because key presses don't show on a screen recording. Optionally, add a short hand-held shot of the keys. Aim for 1 to 2 minutes.

| # | Shot | Google's requirement | Caption |
|---|---|---|---|
| 1 | Start from the home screen. Tap the TitanKeys icon. The guide opens. | Opening the app | "Opening TitanKeys" |
| 2 | Go through the setup slide to the step "Let TitanKeys handle Back". The disclosure screen opens. Hold still on it for a few seconds, then tap "What exactly does it see?" and scroll slowly through the detail, so every line can be read. | The user flow to the disclosure; all text visible | "The disclosure shows before Accessibility opens" |
| 3 | Tap **Not now**. The guide goes on without the service. Show that the keyboard still types. | The flow without consent | "Declining: TitanKeys keeps working without it" |
| 4 | Open Settings > Setup. Tap "Turn on TitanKeys in Accessibility". The disclosure shows again. | Showing the disclosure again | "The disclosure can be shown again" |
| 5 | Tap **Agree**. Android's Accessibility page opens. Turn on TitanKeys and accept Android's own dialog. The app comes back. | The consent flow, including granting the permission | "Agreeing, then turning on the service in Android settings" |
| 6 | In an app with a text field (for example Messages), type, then press Back: the keyboard hides. Press Back again: the app goes back. | A core feature that uses the service | "Back hides the keyboard, then goes back in the app" |
| 7 | Open the Sym panel. Press Enter and Backspace to turn its pages. Show that the text field didn't get a new line or lose a letter. | Core feature (keys while the panel is open) | "In the Sym panel, Enter and Backspace turn the pages" |
| 7b | Switch to Cantonese (Yale), type a code so it shows on the bar, press Enter: the code is typed and nothing is sent. | Core feature (keys while a code is pending) | "While a Chinese code is typed, Enter types it and never sends" |
| 8 | Open Smart Launcher's search. Type a word: it goes in with TitanKeys' fixes. Press Back: the keyboard hides, and the launcher stays on its search. | Core feature (keys in a launcher search) | "In Smart Launcher's search, TitanKeys types and Back hides the keyboard" |
| 9 | In a text field, swipe-type a word on the keys. While the finger is still swiping, a heads-up notification arrives at the top of the screen (send the phone a test message from another device). The swipe carries on and the word is typed; the notification doesn't take the swipe. | Core feature (touchpad above the notification shade) | "When a notification pops up, the touchpad stays on the keys" |

Before recording: use a test text with nothing personal in it, turn off notifications that could appear except the one test message for shot 9 (and keep the phone silent), and record at the phone's normal resolution.

## Open points

- The exact questions and wording in the Play Console declaration form were not seen (they show only inside the console). The table follows Google's help page.
- Smart Launcher must be installed for shot 8. If it isn't, show only shots 6, 7 and 9, and say in the caption that the launcher case works the same way.
