# Store listing draft

For #36. Built on the README's features and the vision in #55. Limits checked on 2026-11-10: app name 30 characters, short description 80, full description 4,000 ([metadata policy](https://support.google.com/googleplay/android-developer/answer/9898842), [graphics](https://support.google.com/googleplay/android-developer/answer/9866151)). Update the feature list once 2.1 is final, and use the wording from #54.

## Rules that shape the text

From the [metadata policy](https://support.google.com/googleplay/android-developer/answer/9898842):

- No emoji, no ALL CAPS name, nothing like "#1", "best" or "Editor's choice" in the name or icon.
- No unattributed testimonials, no keyword lists, no misleading claims.
- **"Data comparison of apps or brands" is a listed violation.** So the README's tables against Atlas stay out of the listing.
- Don't use another brand's logo. Naming the phone to say what TitanKeys works with is fine, but say clearly that TitanKeys isn't made by Unihertz.

The vision ("the ultimate keyboard for the Titan 2 Elite") becomes a goal, not a claim: "to be the keyboard the Titan 2 Elite deserves". That echoes the tagline and avoids a superlative.

## App name (max 30)

| Option | Length | Note |
|---|---|---|
| **TitanKeys** | 9 | Recommended. The safest: just the app's name. The short description names the phone. |
| TitanKeys for Titan 2 Elite | 27 | Easier to find in search. Uses Unihertz's product name in the title. Descriptive use is common, but it raises the trademark question. **Not verified** whether Play's review treats it as a problem. |
| TitanKeys Keyboard | 18 | Says what it is, without naming the phone. |

## Short description (max 80)

**Recommended (74):** The keyboard for the Titan 2 Elite: type and swipe words on the same keys.

Alternative (69): Type, swipe words and reach every symbol on the Titan 2 Elite's keys.

## Full description (3,240 of 4,000 characters)

Plain text. Play doesn't render Markdown, so the headings are capitals and the bullets are "•".

```
TitanKeys is a keyboard made for one phone: the Unihertz Titan 2 Elite. Its keys are also a touchpad, and TitanKeys uses both. Type as you always have, swipe whole words on the same keys, and reach every symbol, emoji and setting without touching the screen.

TitanKeys has one goal: to be the keyboard the Titan 2 Elite deserves.

EVERYTHING FROM THE KEYS

• Nothing to unlearn. Type with two hands or one. When you press Space, TitanKeys fixes a typo in the word you just typed. A small language model on your phone helps it pick the fix that fits the sentence, and typing never waits for it.

• Swipe on the same keys. Slide across the letters of a word without pressing, then lift. If your thumb slips off the top of the keys and comes back, it's still the same word.

• Missed? One touch fixes it. The strip above the keys shows the other words a swipe could be. Touch one to put it in. Before you type, the strip suggests the next word.

• Delete without reaching up. Flick left from Backspace to delete a word. Keep your finger down to delete more, faster the further you slide.

• Steer and select from Space. Tap Space twice, hold the second tap, and slide left or right to move the cursor. You feel a tick for each letter, so you can count without looking. Tap Space three times to select words instead.

• Every symbol within reach. Hold a letter for the symbol printed on it. Press Sym for every other symbol.

• Emoji and settings, no screen. Press Sym twice for emoji. In the panel, Alt opens quick settings, with a letter for each one.

SET UP IN A MINUTE

The guide opens each settings page for you and comes back by itself. Then it teaches each gesture on the real keys, one slide at a time. After that, a playground lets you try anything.

YOUR TYPING STAYS ON YOUR PHONE

Everything you type is handled on your phone, including both language models. TitanKeys never sends what you type. It uses the internet for two things only:

• News about TitanKeys. It downloads a short, signed news file at most once a day, with nothing that identifies you. You can turn news off.

• Swipe examples, only if you choose to share them. They help train the swipe models. They're encrypted on your phone before they're sent, they never include the text around a word, and you can delete them at any time.

WHY IT ASKS FOR PERMISSIONS

• Display over other apps: the Titan sends touchpad input to the window on top, so this keeps your swipes on the keys when an app shows its own pop-ups.

• Accessibility (optional): lets one press of Back close the keyboard and go back. At a few set times, such as while the Sym panel is open, it also sees which keys you press. It can't read your screen.

• Notifications: for TitanKeys news. They're silent and never show while you type.

NEEDS A TITAN 2 ELITE

TitanKeys only works on the Unihertz Titan 2 Elite, because it needs the touch-sensitive keys. It types in English for now.

FREE AND OPEN SOURCE

TitanKeys is free, with no ads. It's open source under the GPL-3.0, based on Atlas by Julian Hall, and powered by FUTO Swipe. Source code and feedback: github.com/jasonlok000/titankeys

TitanKeys is an independent project. It isn't made by, affiliated with or endorsed by Unihertz, Atlas or FUTO. "Titan 2 Elite" is a product of Unihertz.
```

**To add once 2.1 is final** (about 760 characters are left). Add a line only for what really ships, in the same style:

- Clipboard history (#46): "• Paste from your history. Your recent copies wait in the Sym panel, and they never leave your phone."
- Text expansion (#47, #50): "• Type less. A short code turns into the text you saved for it."
- Exact typing (#45): "• Exact typing for terminals and remote desktops. In apps like Termux and AnyDesk, every key goes through as typed."
- Flick up to pick (#44), only if it ships on by default.

**Check before publishing:** the privacy text must match the privacy policy (#53) and the Data safety answers (`data-safety.md`). If the Play build leaves out contributions, remove the "Swipe examples" bullet.

## Category and tags

- **App type:** App.
- **Category: Tools.** The other fits are Productivity and Personalization. **Not verified:** that Gboard and most keyboards are in Tools (from memory). Category list: [Choose a category and tags](https://support.google.com/googleplay/android-developer/answer/9859673).
- **Tags:** up to 5, picked from Play's own list in **Store settings > Manage tags**. Google says to pick only tags tied to the core of the app, not incidental features. Look for tags close to: keyboard, typing, text input, productivity. **Not verified:** Play's tag list can only be seen in the console.
- **Contact details:** the developer email (required, shown in public: vignette@everyones-niche.com, the same as the privacy policy's) and the website `https://jasonlok000.github.io/titankeys` (#58).
- **Privacy policy URL:** `https://jasonlok000.github.io/titankeys/privacy/` (the address the 2.1 code links to; #53).

## Graphics needed

Specifications from [Add preview assets](https://support.google.com/googleplay/android-developer/answer/9866151).

| Asset | Specification | What it should show |
|---|---|---|
| **App icon** | 512 × 512 px, 32-bit PNG with alpha, up to 1 MB | The TitanKeys icon, from `assets/icon.svg` (the current `icon.png` is 960 × 960, so export a 512 version). No badges or text. |
| **Feature graphic** | 1,024 × 500 px, JPEG or 24-bit PNG without alpha | The TitanKeys name and "The keys your Titan deserves." on the app's dark background, with the guide's drawing of the keys and a swipe trail across them. Keep the name and tagline away from the edges, which can be cropped. Google says to avoid device pictures in this graphic, so use the drawing, not a photo of the phone. No store badges, no dates. |
| **Phone screenshots** | 2 to 8. JPEG or 24-bit PNG, 320 to 3,840 px per side, the long side no more than twice the short one | Real screens from the phone (below). The README's crops are only 540 px wide, so take new full-size captures. |
| **Promo video** | A YouTube link: public or unlisted, ads off, not age-restricted, embeddable | The hand-filmed trailer (#57). |

**For featuring on Play,** Google recommends at least 4 screenshots of at least 1,080 px, portrait at 9:16 (1,080 × 1,920). The Titan's screen is close to square, so place each capture on a 1,080 × 1,920 canvas with a short caption. Captions may take at most 20% of the image. No "Download now" and no "best" or "#1".

**Screenshot list** (captions are drafts; use #54's wording):

1. **The strip after a swipe**, with three words in it, in a real app such as a messaging app. Caption: "Swipe words on the keys."
2. **The guide's first slide** ("Nothing to unlearn"), with the line to follow and the drawing of the keys. Caption: "Learn every gesture on the real keys."
3. **The Sym panel** with a group of symbols. Caption: "Every symbol within reach."
4. **The emoji panel.** Caption: "Emoji without touching the screen."
5. **Quick settings** (Alt in the panel), with the letters for each setting. Caption: "Settings from the keys."
6. **Next-word suggestions** on the strip before any letter is typed. Caption: "It suggests the next word."
7. **Settings, the privacy part** (News and contributions, once 2.1 has them). Caption: "Your typing stays on your phone."
8. Optional: **the guide's last slide** ("Built for Titan 2 Elite").

Google's rule on hands: don't show fingers on the device "unless the core experience happens off-device". TitanKeys' core experience is on the physical keys, so one or two photos of a thumb swiping on the keys are justified. Keep them real photos, not renders. Everything else should be real screen captures.

**Promo video notes** (Google's guidance):

- Only the first 30 seconds play by themselves, without sound. Show real use within the first 10 seconds.
- At least 80% of the video should be the real app in use. Keep title cards short.
- Add captions.
- Turn off ads on YouTube.
- If the full trailer is long, a 30 to 60 second cut for Play may work better. That is the owner's call.

## Other listing fields

- **Ads:** none.
- **Price:** free. No in-app purchases.
- **Countries:** all, unless the owner wants to limit them. Titan 2 Elite owners are spread around the world.
- **Devices:** see the open question in `summary.md` on limiting the app to the Titan 2 Elite. Play's device catalogue only excludes devices: by model, or by RAM and chip rules. There is no "only this phone" switch ([source](https://support.google.com/googleplay/android-developer/answer/7353455)).
