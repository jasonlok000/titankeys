# Data safety answers for TitanKeys 2.1 (draft)

For #36, based on the connected-features design in #55 and the 2.1 code in progress (`com.atlas.connected`). Checked against [Google's Data safety guide](https://support.google.com/googleplay/android-developer/answer/10787469) and the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) on 2026-11-10. This is not legal advice.

The form describes **the Play build only**, and every version of it on Play. If a later Play version collects more, update the form first.

## What counts, in Google's words

- **Collected** means the app sends data off the phone. Data that stays on the phone is not collected.
- **Shared** means it goes to a third party. A service provider that only works for you (Cloudflare here) is not a third party. Data the user sends through a clear action of their own is not sharing.
- **End-to-end encryption does not exempt the contributions.** Google's exemption is for data that only the sender and the recipient can read, where the developer can't. Here the owner is the recipient and can read the data, so it must be declared.
- **Optional** is allowed only if every user can choose not to give it. Contributions are off until the user agrees, so they are optional.

## How each 2.1 feature comes out

| Feature | Leaves the phone? | Declare? |
|---|---|---|
| Typing, autocorrect, swipe recognition, both language models, learned words | No | No |
| Clipboard history (#46), text expansion (#47, #50) | No | No |
| Accessibility service (Back, and keys in the Sym panel and launcher search) | No. Keys are used at once on the phone and not kept. | No. Answer "No" in the accessibility declaration too. |
| Contribution queue on the phone | Not until it is sent | Only what is sent (below) |
| **Announcements (#42)** | A download only. The request carries no ID, no cookie and a plain user agent. | No user data is sent. See the IP note below. |
| **Contributions (#34)** | Yes, if the user agrees | **Yes** (below) |
| Send feedback (opens a GitHub issue in the browser, with the phone model and versions filled in) | Only when the user taps it, reads it and submits it in the browser | No: a user-initiated action through another app. |
| Typing log (#16) | No (export is a file the user saves) | No. But see the open question about leaving it out of the Play build. |

## The answers, item by item

### Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** (contributions only) |
| Is all of the user data collected by your app encrypted in transit? | **Yes.** HTTPS, and each batch is also sealed on the phone with the project's public key (X25519 and AES-GCM). |
| Account creation | **My app doesn't allow users to create an account.** **Not verified:** the exact wording of this question in the 2026 form. |
| Do you provide a way for users to request that their data is deleted? | **Yes.** "Delete my contributions" in the app removes everything stored for that contributor ID. Also offer a way by email (see the open questions). |
| Independent security review | No (optional badge) |

### Data types

Answer **"not collected"** for everything except the rows marked **Collected**.

| Category | Data type | Answer | What it covers |
|---|---|---|---|
| Location | Approximate, Precise | Not collected | |
| Personal info | All (name, email, user IDs, address, phone, and so on) | Not collected | The contributor ID is random and not tied to a person, so it is not a "User ID" (Google: "identifiers that relate to an identifiable person"). |
| Financial info | All | Not collected | |
| Health and fitness | All | Not collected | |
| Messages | Emails, SMS or MMS, Other in-app messages | Not collected | No text around the word, and no typed text, is ever sent. |
| Photos and videos, Audio, Files and docs, Calendar, Contacts | All | Not collected | |
| **App activity** | **App interactions** | **Collected** | The swipe path, with times from the start of the swipe, the touch sizes, and the ranker's 18 numbers. |
| App activity | In-app search history, Installed apps, Other actions | Not collected | |
| **App activity** | **Other user-generated content** | **Collected** | The kept word, only when it is a dictionary word. **Uncertain:** a single dictionary word could also be seen as part of "App interactions". Declaring it here is the cautious choice. |
| Web browsing | Web browsing history | Not collected | |
| App info and performance | Crash logs | Not collected | |
| **App info and performance** | **Diagnostics** | **Collected** | The app version, the model versions and the phone model. **Uncertain:** Google has no data type for a phone model. Diagnostics is the closest, and declaring it is the cautious choice. |
| App info and performance | Other app performance data | Not collected | |
| **Device or other IDs** | **Device or other IDs** | **Collected** | The random contributor ID. Google: "identifiers that relate to an individual device, browser or app", with an app instance ID among its examples. |

### For each collected type

The same answers for all four (App interactions, Other user-generated content, Diagnostics, Device or other IDs):

| Question | Answer |
|---|---|
| Collected or shared? | **Collected.** Not shared. Cloudflare stores the sealed files as a service provider, and can't read them. |
| Is this data processed ephemerally? | **No.** It is kept for training. |
| Is collection required, or can users choose? | **Users can choose** (optional). Off by default; "I agree" or "No thanks"; can stop at any time. |
| Why is this data collected? | **Analytics.** Google: "Used to collect data about how users use the app or how it performs." Training the models to recognise swipes better fits this best. **Judgment call:** some developers would also tick "App functionality", because the trained models power the swipe feature. Don't tick Advertising, Personalization, Account management, Developer communications or Fraud prevention. |

### What the listing will then show (roughly)

- No data shared with third parties.
- Data collected: App activity, App info and performance, Device or other IDs. Optional.
- Data is encrypted in transit.
- You can request that data be deleted.

## IP addresses

Every download and upload shows the phone's IP address to the server. Google says to disclose IP addresses "based on how you use them", and to declare location if you use an IP address to find where someone is. TitanKeys doesn't use IP addresses at all, so nothing is declared, **as long as**:

- the contributions Worker never reads, stores or logs the IP address (`CF-Connecting-IP`), and Worker logging is off or strips it;
- GitHub Pages' own server logs are GitHub's, which the owner can't see or use.

**Uncertain:** Google has no explicit rule for an IP address that reaches a host as part of the connection and is never used. The answer above follows Google's "based on how you use them" wording.

## Things the 2.1 code must keep true for these answers

- Contributions never include data from the accessibility service. If they ever did, the accessibility declaration would have to say "Yes, collects data".
- Nothing is sent before the user taps "I agree" on the contributions disclosure. Google: consent must come "before your app can begin to collect".
- The contributions disclosure is its own screen, separate from the accessibility disclosure.
- The announcement request stays free of identifiers: no contributor ID, no version in the request, no cookies.
- Any feed entry for the Play build never links to an APK download. Google Play doesn't allow an app to update itself by any other route ([Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/9888379)).
- The privacy policy (#53) says the same things: what is sent, why, how it is protected, how long it is kept, and how to delete it. Google also wants a privacy contact, and the developer name or app name in the policy.

## Open questions

1. **Retention.** How long are contributions kept? The privacy policy must say. "Until you delete them, or until [N] years after the last upload" is one option.
2. **Deletion after uninstalling.** The 2.1 code in progress files each batch under a hash of the contributor ID (the `X-Contributor` header), and "Delete my contributions" sends the ID so the server can find them. Only the phone knows the ID, so it is lost on uninstall. Options: show the ID in Settings so the user can copy it and email a deletion request, or say in the policy that data from an uninstalled app can't be found. Note that the hashed ID is also an identifier, which is covered by "Device or other IDs".
3. **Contributions in the Play build at all?** If the first Play release ships without contributions, the whole form becomes "No data collected", which is simpler for the first review. Contributions could follow in an update, with the form updated before that update.
4. **Typing log (#16) in the Play build.** It stays on the phone, so it isn't "collected". But a keyboard that records all typed text may draw review questions. The earlier plan on #34 was to leave it out of the Play build. Decide before the first upload.
5. **Purpose:** Analytics only, or Analytics and App functionality.
