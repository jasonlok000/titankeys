# Content rating, target audience and the other App content answers

For #36. Draft answers for **Policy > App content** in Play Console. Checked against Google's help pages on 2026-11-10. This is not legal advice.

## Content rating (IARC questionnaire)

Every app needs a rating from the International Age Rating Coalition. Apps without one are removed. You enter an email for IARC, pick a category, answer the questions, and submit. Ratings come back for each region (ESRB, PEGI, USK, ClassInd and others). ([Content ratings](https://support.google.com/googleplay/android-developer/answer/9898843), [how to fill it in](https://support.google.com/googleplay/android-developer/answer/188189), [authorities](https://support.google.com/googleplay/android-developer/answer/9859655))

**Not verified:** Google's pages don't list the categories or the questions, which only show inside the console. The answers below follow the usual IARC topics. Match each to the question you see.

| Topic | Answer | Why |
|---|---|---|
| Category | **The non-game, non-social category** ("All other app types", or "Utility, productivity, communication or other" if that is how it is worded) | A keyboard is a utility. It isn't a game, and it isn't a social or communication app: users don't talk to each other through it. |
| Violence, blood, fear or horror | No | |
| Sexual content or nudity | No | |
| Bad language | No (see the note below) | |
| Drugs, alcohol or tobacco | No | |
| Crude humour | No | |
| Gambling, real or simulated | No | |
| Users can talk to or share content with other users in the app | **No** | Text goes into other apps, not through TitanKeys. No chat, no profiles, no uploads anyone else sees. |
| Shares the user's location with other users | No | |
| Users can buy digital goods | No | |
| Unrestricted internet access, or a web browser or search engine | No | It downloads only its own signed news file. A link in a news item opens the browser, a separate app. |
| News, or a news aggregator | No | |
| Anything else that could be mature (NFTs, crypto, and so on) | No | |

**Expected result:** the lowest rating everywhere (for example, ESRB Everyone, PEGI 3). **Not verified** until the questionnaire is submitted.

**Note on bad language:** the dictionary and the next-word model could suggest a rude word that the user didn't type. Most keyboards answer "No" here, because the app shows the user's own words back. **Decided (owner, 2026-11-10):** TitanKeys does not filter offensive words (#61 dropped). Answer "No", as most keyboards do, since the words come from the user's own typing and a general dictionary, not from content the app presents. If a reviewer questions it, the careful fallback is "Yes", mild, with this explanation.

## Target audience and content

The age groups on offer: "Age 5 and under", "Ages 6-8", "Ages 9-12", "Ages 13-15", "Ages 16-17", "Ages 18 and over". Choosing any group under 13 brings in the Families policy. ([source](https://support.google.com/googleplay/android-developer/answer/9867159))

**Answer: "Ages 18 and over" only.**

- It matches #36's earlier plan (adults).
- The Titan 2 Elite is an adult's phone, and Play Console itself needs its owner to be 18 or over.
- The opt-in data contributions are simpler to defend for an adult audience.
- Nothing in the listing looks made for children: no cartoon characters and no childish graphics. Keep it that way, or Google can ask for a Families review.

**"Could your app unintentionally appeal to children?"** (if asked): **No.**

## The other App content declarations

| Declaration | Answer | Note |
|---|---|---|
| **Privacy policy** | `https://jasonlok000.github.io/titankeys/privacy/` | Required for every app. It must also be linked inside the app, be public, not a PDF, and name the developer or the app ([User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)). |
| **Ads** | No, my app does not contain ads | |
| **App access** | **All or some functionality is restricted**, with instructions (below) | No login, but the main features need the Titan 2 Elite's touch-sensitive keys, which a reviewer's phone won't have. |
| **Content rating** | As above | |
| **Target audience and content** | 18 and over | |
| **News app** | No | |
| **Data safety** | See `data-safety.md` | |
| **Advertising ID** | No, the app doesn't use the advertising ID | Check that no library adds `com.google.android.gms.permission.AD_ID` (`build-tasks.md`, C4). |
| **Government app** | No | |
| **Financial features** | None | |
| **Health** | No health features | |
| **Sensitive permissions and APIs: Accessibility** | Filed, with video | See `accessibility.md`. |

**Not verified:** the exact list of declarations in the 2026 console. Fill in whatever else it shows.

### App access: instructions for the reviewer (draft)

> TitanKeys is a keyboard for the Unihertz Titan 2 Elite, a phone with a physical QWERTY keyboard whose keys are also a touchpad. There is no login and no account.
>
> On any phone you can open TitanKeys, go through the guide and the settings, and turn it on as the keyboard. Its main features need the Titan 2 Elite's keys: typing on the physical keys, swiping words across them, the Sym panel and the emoji panel. They can't be shown on a phone without that keyboard.
>
> A video of TitanKeys in use on a Titan 2 Elite: [YouTube link to the trailer or a demo].
>
> The accessibility service is optional and is explained on its own screen in the guide before Android's Accessibility page opens.

**Open point:** Google's reviewers may not have a Titan 2 Elite. There is a risk that a reviewer finds the app "doesn't do anything" on their phone. Clear app access instructions and a real video are the best answer. Google's production questions about the closed test also help, because the testers used it on real phones.
