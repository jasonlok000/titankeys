# The accessibility service on Google Play

For #36. Checked against [Use of the AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491) and the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) on 2026-11-10. This is not legal advice.

## What the service does today (from the code)

`BackService` (`android/kotlin/com/atlas/keyboard/BackService.kt`, `android/src/main/res/xml/back_service.xml`):

- `isAccessibilityTool="false"`, which is correct: TitanKeys is not a tool for people with disabilities.
- `canRetrieveWindowContent="false"`: it can't read the screen. It asks for no accessibility events.
- **Back:** sends a system Back (`GLOBAL_ACTION_BACK`), so one press closes the keyboard and goes back in the app.
- **Keys:** `canRequestFilterKeyEvents="true"`. It turns key filtering on only while the keyboard asks for it (`AtlasIme.updateKeyWatch`):
  - while the Sym panel is open, and until a key the panel took is let go, so that Enter and Backspace turn the panel's pages and don't reach the app;
  - in the Smart Launcher search field, when hold for symbols is on or double-press lock is off, so that holds and two presses of Shift or Alt work there;
  - between the first and second press of Back, while the keyboard holds the touchpad (when "Scroll assistant" is off), so the second press reaches the app.
- **Touchpad (2.1):** while the keyboard has the touchpad, it shows a 1×1 `TYPE_ACCESSIBILITY_OVERLAY` window (`TouchpadOverlay`) above the notification shade, because the firmware gives a touchpad swipe to the shade when a notification pops up. It takes no screen touches and reads nothing. It's hidden on the lock screen and when the screen is off.
- Nothing it sees is stored or sent.

**Check before you file the declaration:** if 2.1 adds any new use of `BackService.watchKeys` or `performGlobalAction` (for example from #45 or #51), the disclosure, the declaration and the video must include it. Google: "If you change how the API is used, you must submit the form again."

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
| Short description (if there is a text box) | "TitanKeys is a keyboard for the Unihertz Titan 2 Elite. Its accessibility service does three things. It sends Back, so one press of Back closes the keyboard and goes back in the app. While the symbol panel is open, in the Smart Launcher search field, and between two presses of Back, it receives the key presses before the app does, so that the keyboard's own keys work there. While you type, it keeps a 1-pixel window above the notification shade, so the keyboard's touchpad still reaches the keyboard when a notification pops up. It can't read the screen, and nothing it receives is stored or sent." |
| Does your app collect or share personal or sensitive data through the accessibility capabilities? | **No.** Key presses are handled at once on the phone and never kept or sent. |
| Video link | The declaration video (shot list below), on YouTube as unlisted. **Not verified:** Google's page doesn't say where the video must be hosted. An unlisted YouTube link is the common choice. |

## 2. The in-app disclosure

**Where:** in the guide's setup slide, at the "Let TitanKeys handle Back" step, **before** it opens Accessibility. The same screen shows again when the user taps "Turn on TitanKeys in Accessibility" in Settings > Setup. It is its own screen, with nothing about other permissions or data on it.

**Buttons:** "No thanks" and "Agree". Back, Home or tapping outside counts as "No thanks", never as agreement. The screen never closes by itself.

**Draft text** (use the tone from #54; check each line against the 2.1 code):

> **Let TitanKeys handle Back?**
>
> TitanKeys can use Android's Accessibility service to make Back and a few keys work better. It's optional.
>
> **What it does**
>
> - **Back:** when you press Back, TitanKeys sends it on for you, so one press closes the keyboard and goes back in the app.
> - **Keys:** at these times only, it sees which keys you press before the app does:
>   - while the Sym panel is open, so Enter and Backspace turn its pages instead of reaching the app;
>   - in the Smart Launcher search field, so holding a key for a symbol and pressing Shift or Alt twice work there;
>   - for a moment after you press Back, so your next press of Back reaches the app.
> - **Touchpad:** while you type, it keeps the touchpad on the keys when a notification shows at the top of the screen.
>
> **What it never does**
>
> - It can't read your screen or the text in your apps.
> - It doesn't keep or send the keys it sees. They're used on your phone straight away, then forgotten.
>
> **If you say no,** TitanKeys still types and swipes. Back may take two presses, and those keys work as they would without TitanKeys. You can turn it on later in Settings > Setup.
>
> [No thanks] [Agree]

After **Agree**, the app opens Android's Accessibility page, as the guide does today. Android then shows its own warning, which the user also accepts.

After **No thanks**, the guide moves on to the next step. The setup row stays "Not done", and tapping it shows this disclosure again.

**Needs a code change:** the guide's front page waits until the service is on today, so a user can't decline. It must let the user go on without it (see `build-tasks.md`). **Check on the phone:** what exactly changes without the service ("Back may take two presses"), so the "If you say no" line is true.

**The service description in Android's settings** (`back_service_description`) can stay, but it doesn't replace the disclosure. Shorten it to match the disclosure, for example: "Sends Back, so one press of Back closes the keyboard and goes back. While the Sym panel is open, in the Smart Launcher search field and for a moment after you press Back, TitanKeys sees the keys you press. While you type, it keeps the touchpad on the keys when a notification shows at the top of the screen. It can't read your screen, and it doesn't keep or send anything."

## 3. Shot list for the declaration video

Google's list of what the video must show, in order. Record the screen with `adb screenrecord` or the phone's recorder, and add captions where the keys are pressed, because key presses don't show on a screen recording. Optionally, add a short hand-held shot of the keys. Aim for 1 to 2 minutes.

| # | Shot | Google's requirement | Caption |
|---|---|---|---|
| 1 | Start from the home screen. Tap the TitanKeys icon. The guide opens. | Opening the app | "Opening TitanKeys" |
| 2 | Go through the setup slide to the step "Let TitanKeys handle Back". The disclosure screen opens. Scroll slowly so every line can be read, and hold still on it for a few seconds. | The user flow to the disclosure; all text visible | "The disclosure shows before Accessibility opens" |
| 3 | Tap **No thanks**. The guide goes on without the service. Show that the keyboard still types. | The flow without consent | "Declining: TitanKeys keeps working without it" |
| 4 | Open Settings > Setup. Tap "Turn on TitanKeys in Accessibility". The disclosure shows again. | Showing the disclosure again | "The disclosure can be shown again" |
| 5 | Tap **Agree**. Android's Accessibility page opens. Turn on TitanKeys and accept Android's own dialog. The app comes back. | The consent flow, including granting the permission | "Agreeing, then turning on the service in Android settings" |
| 6 | In an app with a text field (for example Messages), type, then press Back once: the keyboard closes and the app goes back. | A core feature that uses the service | "One press of Back closes the keyboard and goes back" |
| 7 | Open the Sym panel. Press Enter and Backspace to turn its pages. Show that the text field didn't get a new line or lose a letter. | Core feature (keys while the panel is open) | "In the Sym panel, Enter and Backspace turn the pages" |
| 8 | Open Smart Launcher's search. Hold a letter: its symbol appears. | Core feature (keys in a launcher search) | "In Smart Launcher's search, holding a key types its symbol" |
| 9 | In a text field, swipe-type a word on the keys. While the finger is still swiping, a heads-up notification arrives at the top of the screen (send the phone a test message from another device). The swipe carries on and the word is typed; the notification doesn't take the swipe. | Core feature (touchpad above the notification shade) | "When a notification pops up, the touchpad stays on the keys" |

Before recording: use a test text with nothing personal in it, turn off notifications that could appear except the one test message for shot 9 (and keep the phone silent), and record at the phone's normal resolution.

## Open points

- The exact questions and wording in the Play Console declaration form were not seen (they show only inside the console). The table follows Google's help page.
- Smart Launcher must be installed for shot 8. If it isn't, show only shots 6, 7 and 9, and say in the caption that the launcher case works the same way.
