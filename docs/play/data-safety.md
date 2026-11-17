# Data safety answers for TitanKeys 2.1 (draft)

For #36, based on the connected-features design in #55 and the 2.1 code in progress (`com.titankeys.connected`). Checked against [Google's Data safety guide](https://support.google.com/googleplay/android-developer/answer/10787469) and the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) on 2026-11-10. This is not legal advice.

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
| **Report form (#97):** Send feedback, Record a bug and the crash offer. The kind picked, what the user wrote, an optional email address for a reply, the versions, build and phone model, and a bug recording or crash report if attached | **Yes**, only when the user presses Send. Sealed on the phone with the contributions key, sent to the same Worker (`POST /v1/report`). | **Yes** (below) |
| Email, the form's backup (#74): when the report can't be sent, the user's mail app opens to titankeys@everyones-niche.com with it attached | Only when the user sends the mail from their own mail app. TitanKeys sends nothing by mail itself. | No: a user-initiated action through another app. |
| Crash report kept on the phone (#74): where the crash happened in the code, and the versions, never typed text | No, until the user sends it with the form | Only what the form sends |
| Back up everything (#65): a JSON file of settings, words and lists | No: a file the user saves where they pick | No |
| Typing log (#16) | No (export is a file the user saves) | No. But see the open question about leaving it out of the Play build. |

## The answers, item by item

### Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** (contributions only) |
| Is all of the user data collected by your app encrypted in transit? | **Yes.** HTTPS, and each batch is also sealed on the phone with the project's public key (X25519 and AES-GCM). |
| Account creation | **My app doesn't allow users to create an account.** **Not verified:** the exact wording of this question in the 2026 form. |
| Do you provide a way for users to request that their data is deleted? | **Yes.** "Delete my contributions" in the app removes everything stored for that contributor ID. Also by email to titankeys@everyones-niche.com with the contributor ID, as the privacy policy says. |
| Independent security review | No (optional badge) |

### Data types

Answer **"not collected"** for everything except the rows marked **Collected**.

| Category | Data type | Answer | What it covers |
|---|---|---|---|
| Location | Approximate, Precise | Not collected | |
| **Personal info** | **Email address** | **Collected, optional** | Only if the user types one in the report form (#97), so that we can reply. |
| Personal info | All others (name, user IDs, address, phone, and so on) | Not collected | The contributor ID is random and not tied to a person, so it is not a "User ID" (Google: "identifiers that relate to an identifiable person"). |
| Financial info | All | Not collected | |
| Health and fitness | All | Not collected | |
| Messages | Emails, SMS or MMS, Other in-app messages | Not collected | No text around the word, and no typed text, is ever sent. |
| Photos and videos, Audio, Files and docs, Calendar | All | Not collected | |
| Contacts | Contacts | Not collected | Read on the phone only, with the user's opt-in and Android's Contacts permission, to recognise first and last names as words. Never sent or shared. |
| **App activity** | **App interactions** | **Collected** | The swipe path, with times from the start of the swipe, the touch sizes, the ranker's 18 numbers, and how the word was settled (kept, picked from the strip, or corrected). A bug recording sent with the report form (#97) also holds touches, key presses and the keyboard's decisions. **Uncertain:** a recording can show what was typed while it ran; declaring it here and under Other user-generated content is the cautious choice. |
| App activity | In-app search history, Other actions | Not collected | |
| App activity | Installed apps | Not collected | Home-screen app names are read on the phone to recognise them as words. Never sent or shared. |
| **App activity** | **Other user-generated content** | **Collected** | The kept word, only when it is a dictionary word. **Uncertain:** a single dictionary word could also be seen as part of "App interactions". Declaring it here is the cautious choice. Also what the user writes in the report form (#97), and a bug recording if attached. |
| Web browsing | Web browsing history | Not collected | |
| **App info and performance** | **Crash logs** | **Collected, optional** | Only a crash or freeze report the user sends with the report form (#97): where in the code it happened, and the versions. Never typed text. |
| **App info and performance** | **Diagnostics** | **Collected** | The app version, the build (GitHub, Play or debug), the model versions and the phone model. **Uncertain:** Google has no data type for a phone model. Diagnostics is the closest, and declaring it is the cautious choice. |
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

**The report form (#97)** adds Email address and Crash logs, and adds to App interactions, Other user-generated content and Diagnostics. For what it sends:

| Question | Answer |
|---|---|
| Collected or shared? | **Collected.** Not shared. Sealed on the phone; Cloudflare stores it as a service provider and can't read it. |
| Is this data processed ephemerally? | **No.** Kept until the owner has dealt with the report, at most 2 years. |
| Is collection required, or can users choose? | **Users can choose.** Nothing is sent until Send; the email address and the recording are optional. |
| Why is this data collected? | **App functionality** (fixing bugs) and **Developer communications** (a reply, if an email address was given). **Uncertain:** Google's purposes have no "bug reports"; these two are the closest. |

### What the listing will then show (roughly)

- No data shared with third parties.
- Data collected: App activity, App info and performance, Device or other IDs. Optional.
- Data is encrypted in transit.
- You can request that data be deleted.

## IP addresses

Every download and upload shows the phone's IP address to the server. Google says to disclose IP addresses "based on how you use them", and to declare location if you use an IP address to find where someone is.

TitanKeys never uses an IP address to find where someone is, and never keeps one. The one use is the contributions Worker's limit against abuse:

- The Worker hashes the address with a secret salt (`RATE_SALT`), and Cloudflare's rate limiter counts that hash in memory for one minute.
- The address and the hash are never stored or logged. Worker logging is off.
- GitHub Pages' own server logs are GitHub's, which the owner can't see or use.

**Answer:** nothing is declared for IP addresses. Google's form has no IP address type, and this use finds no location and ties nothing to a person.

**Uncertain:** Google has no explicit rule for an address used only for a moment against abuse. If a reviewer asks, the careful answer is: "Device or other IDs", collected, processed ephemerally, not shared, purpose "Fraud prevention, security, and compliance". The privacy policy already says the address is counted for one minute and never kept.

## Things the 2.1 code must keep true for these answers

- Contributions never include data from the accessibility service. If they ever did, the accessibility declaration would have to say "Yes, collects data".
- Nothing is sent before the user taps "I agree" on the contributions disclosure. Google: consent must come "before your app can begin to collect".
- The contributions disclosure is its own screen, separate from the accessibility disclosure.
- The announcement request stays free of identifiers: no contributor ID, no version in the request, no cookies.
- Any feed entry for the Play build never links to an APK download. Google Play doesn't allow an app to update itself by any other route ([Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/9888379)).
- The privacy policy (#53) says the same things: what is sent, why, how it is protected, how long it is kept, and how to delete it. Google also wants a privacy contact, and the developer name or app name in the policy.

## Decided

1. **Retention:** until it is used for training, and at most 2 years after it arrives. R2 deletes older files by itself, and the privacy policy says so.
2. **Deletion after uninstalling:** Settings shows the contributor ID with a Copy button, and the user can still delete after stopping. The policy says how to ask for deletion with the ID by email. The hashed ID is covered by "Device or other IDs".
3. **Contributions in the Play build:** yes, off by default, as in the GitHub build. Everything in 2.1 ships in both builds. The form is filled in as above before the first upload.
4. **Typing log (#16) in the Play build:** left out. It stays in the GitHub build.
5. **Purpose:** Analytics only. Google's Analytics covers "future performance improvements", which is what training the swipe models is.

## 3.0: one sharing switch with daily totals (#96, built, not yet released)

Draft for the owner. The design is in [docs/analytics.md](../analytics.md). Fill these in on the form **before** the first Play release that sends counts, not earlier: the form must match the release on Play.

Share swipes becomes **Sharing**: one opt-in that sends swipe examples and once-a-day totals. All of it fits data types already declared above, so the top-level answers stay the same (collected, not shared, optional, encrypted in transit, deletion on request).

| Data type | Change | Answers for the new part |
|---|---|---|
| App interactions | Adds the daily feature counts (joins, autocorrect and apostrophe fixes, swipes by word length, bar picks, Chinese picking, gestures), guide progress, and which settings differ from their defaults (changed or not only) | Collected; not shared; not ephemeral (kept 1 year); optional; Analytics |
| Diagnostics | Adds speed bands (keyboard open time, swipe read time) and the Android version | Same as the row |
| Crash logs | Adds daily crash and freeze counts, without details, through Sharing. Today Crash logs come only from the report form. | Collected; not shared; not ephemeral (1 year); optional; Analytics |
| Device or other IDs | Adds the counts ID: random, made on the phone, replaced every 30 days, never sent with the contributor ID | Same as the row |

**The envelope and this form.** Counts that fit the kinds in analytics.md §1 (K2 to K6) fall under App interactions, Diagnostics and Crash logs. Adding one needs no change here. A count that needs a new data type or a purpose other than Analytics needs this form changed before that release, as well as the policy and the user's consent.

**Things the 3.0 code must keep true for these answers:**
- Nothing is counted for sending, or sent, before the user taps "Turn on sharing" for the current envelope.
- Nothing comes from the Accessibility service, so its declaration stays "No".
- There is no advertising ID: no `AD_ID` permission in the merged manifest.

**Not verified:**
- The 2026 form's exact names and examples for Crash logs, Diagnostics and App interactions. Check them on the live form when filling it in.
