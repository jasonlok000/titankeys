# Data safety answers for TitanKeys 3.0 (draft)

For #36. First drafted for 2.1 from the connected-features design in #55, then updated on 2026-11-21 for what 3.0.0 ships:
one Sharing switch with daily totals (#96, [docs/analytics.md](../analytics.md)), the news, the report form (#97), and the
Support page with its supporters board (`server/support/`). Checked against the 3.0.0 code (`com.titankeys.connected`,
`com.titankeys.support`, `server/support/src/`), [Google's Data safety guide](https://support.google.com/googleplay/android-developer/answer/10787469)
and the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311). This is not legal advice.

The form describes **the Play build only**, and every version of it on Play. If a later Play version collects more, update the
form first.

## What counts, in Google's words

- **Collected** means the app sends data off the phone. Data that stays on the phone is not collected.
- **Shared** means it goes to a third party. A service provider that only works for you (Cloudflare here) is not a third party.
  Data the user sends through a clear action of their own is not sharing.
- **End-to-end encryption does not exempt Sharing.** Google's exemption is for data that only the sender and the recipient can
  read, where the developer can't. Here the owner is the recipient and can read the data, so it must be declared.
- **Optional** is allowed only if every user can choose not to give it. Sharing, the report form and the board are all off
  until the user acts, so they are optional.

## How each 3.0 feature comes out

| Feature | Leaves the phone? | Declare? |
|---|---|---|
| Typing, autocorrect, swipe recognition, the language models, learned words, every input method and its word lists | No | No |
| Clipboard history (#46), text expansion (#47, #50) | No | No |
| Accessibility service (Back, and keys in the Sym panel and launcher search) | No. Keys are used at once on the phone and not kept. | No. Answer "No" in the accessibility declaration too. |
| **News** (`Announcements`): one signed file from the website, at most once a day | A download only. The request carries no ID, no cookie and the plain user agent `TitanKeys`. | No user data is sent. See the IP note below. |
| **Sharing (#96):** one switch, "Turn on sharing". Swipe examples (on Wi-Fi) and a sealed day's totals, once a day | Yes, if the user turns it on. Off by default. Nothing is sent from TitanKeys Debug. | **Yes** (below) |
| Sharing's queues on the phone | Not until they are sent. Turning Sharing off deletes what waits. | Only what is sent |
| **Report form (#97):** Send feedback, Record a bug and the crash offer. The kind picked, what the user wrote, an optional email address for a reply, the versions, build and phone model, and a bug recording or crash report if attached | **Yes**, only when the user presses Send. Sealed on the phone with the contributions key, sent to the contributions Worker (`POST /v1/report`). | **Yes** (below) |
| Email, the form's backup (#74): when the report can't be sent, the user's mail app opens to titankeys@everyones-niche.com with it attached | Only when the user sends the mail from their own mail app. TitanKeys sends nothing by mail itself. | No: a user-initiated action through another app. |
| Crash report kept on the phone (#74) | No, until the user sends it with the form | Only what the form sends |
| **Support page, buying:** coffees through Google Play Billing (`products.md`) | Google Play handles the payment. TitanKeys never sees card or payment details. Play gets a random account ID (the SHA-256 of the app's account key) with the purchase. | Not for the payment itself. See "Financial info" below. |
| Support page, the purchase record on the phone (`SupportLedger`) and the typing-day card's totals | No | No |
| **Support page, reading the board:** `GET /v1/leaderboard`, only while the page is open | A download only, with no ID | No user data is sent. |
| **Support page, joining the board:** `POST /v1/support` with the account key, the Play purchase tokens, an optional name and message, and the anonymous choice. Also "This puts you at #N" (`POST /v1/rank-preview`: the account key and an amount) | **Yes**, only after the supporter chooses to share | **Yes** (below) |
| Support page, reporting a board entry: `POST /v1/report` with the entry's public ID and an optional reason | **Yes**, when the user sends it | **Yes**, as user-generated content (below) |
| Back up everything (#65): a JSON file of settings, words and lists | No: a file the user saves where they pick | No |
| Typing log (#16) | Not in the Play build: `tools/play.sh` marks the build `play`, and the log is off there (`AppBuild.play`) | No |

## The answers, item by item

### Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** (Sharing, the report form and the supporters board, all optional) |
| Is all of the user data collected by your app encrypted in transit? | **Yes.** All of it goes over HTTPS. Sharing and reports are also sealed on the phone with the project's public key (X25519 and AES-GCM). The board's requests are HTTPS only, since the board is public. |
| Account creation | **My app doesn't allow users to create an account.** The board's account key is made on the phone and has no login. **Not verified:** the exact wording of this question in the 2026 form. |
| Do you provide a way for users to request that their data is deleted? | **Yes.** Sharing: "Delete what I've sent" in Settings > Sharing deletes the swipes for the contributor ID and the daily totals for each counts ID the phone still holds (a year back). The board: "Remove me" deletes the entry and its purchase history on the Worker. Both also by email to titankeys@everyones-niche.com, as the privacy policy says. |
| Independent security review | No (optional badge) |

### Data types

Answer **"not collected"** for everything except the rows marked **Collected**.

| Category | Data type | Answer | What it covers |
|---|---|---|---|
| Location | Approximate, Precise | Not collected | |
| **Personal info** | **Name** | **Collected, optional** | The name a supporter types to show on the board (up to 80 characters; anonymous sends none). Google's Name includes a nickname. **Uncertain:** it may be a made-up name, but declaring it is the cautious choice. |
| **Personal info** | **Email address** | **Collected, optional** | Only if the user types one in the report form (#97), so that we can reply. |
| Personal info | All others (user IDs, address, phone, and so on) | Not collected | The contributor ID, the counts ID and the board's account key are random and not tied to a person, so they are not a "User ID" (Google: "identifiers that relate to an identifiable person"). |
| **Financial info** | **Purchase history** | **Collected, optional** | Only for supporters who join the board: the Worker keeps, per account, the product ID, the credited cents, and hashes of the purchase token and order ID. It never keeps the raw token or order ID, an email or payment details. |
| Financial info | User payment info, Credit score, Other financial info | Not collected | Google Play handles the payment; TitanKeys never sees it. |
| Health and fitness | All | Not collected | |
| Messages | Emails, SMS or MMS, Other in-app messages | Not collected | No typed text is ever sent. The board's message is a public line, not a message between users; it is under Other user-generated content. |
| Photos and videos, Audio, Files and docs, Calendar | All | Not collected | |
| Contacts | Contacts | Not collected | Read on the phone only, with the user's opt-in and Android's Contacts permission, to recognise first and last names as words. Never sent or shared. |
| **App activity** | **App interactions** | **Collected, optional** | Sharing: the swipe path, with times from the start of the swipe, the touch sizes, the ranker's numbers, and how the word was settled. The daily feature counts, guide progress, and which settings differ from their defaults (changed or not only). A bug recording sent with the report form (#97) also holds touches, key presses and the keyboard's decisions. **Uncertain:** a recording can show what was typed while it ran; declaring it here and under Other user-generated content is the cautious choice. |
| App activity | In-app search history, Other actions | Not collected | |
| App activity | Installed apps | Not collected | Home-screen app names are read on the phone to recognise them as words. Never sent or shared. |
| **App activity** | **Other user-generated content** | **Collected, optional** | Sharing: the kept word, only when it is a dictionary word. The report form: what the user writes, and a bug recording if attached. The board: the supporter's message (up to 120 characters) and the reason in a report of an entry. **Uncertain:** a single dictionary word could also be seen as part of "App interactions". Declaring it here is the cautious choice. |
| Web browsing | Web browsing history | Not collected | |
| **App info and performance** | **Crash logs** | **Collected, optional** | A crash or freeze report the user sends with the report form: where in the code it happened, and the versions. Never typed text. Sharing also sends daily crash and freeze counts, with no details. |
| **App info and performance** | **Diagnostics** | **Collected, optional** | The app version, the build (GitHub, Play or debug), the model versions, the phone model and the Android version. Sharing's speed bands (keyboard open time, swipe read time). **Uncertain:** Google has no data type for a phone model. Diagnostics is the closest, and declaring it is the cautious choice. |
| App info and performance | Other app performance data | Not collected | |
| **Device or other IDs** | **Device or other IDs** | **Collected, optional** | Three random IDs made on the phone: the contributor ID (swipes), the counts ID (daily totals, replaced every 30 days, never sent with the contributor ID) and the board's account key. Google: "identifiers that relate to an individual device, browser or app", with an app instance ID among its examples. |

### For each collected type

Three features collect data. Each type's answers are the union of the features that send it.

**Sharing** (App interactions, Other user-generated content, Diagnostics, Crash logs, Device or other IDs):

| Question | Answer |
|---|---|
| Collected or shared? | **Collected.** Not shared. Cloudflare stores the sealed files as a service provider, and can't read them. |
| Is this data processed ephemerally? | **No.** Swipe examples are kept until used for training, at most 2 years. Daily totals are kept 1 year. |
| Is collection required, or can users choose? | **Users can choose** (optional). Off by default; "Turn on sharing" on its own consent screen; off at any time. |
| Why is this data collected? | **Analytics.** Google: "Used to collect data about how users use the app or how it performs." Training the swipe models and finding what works badly fit this best. **Judgment call:** some developers would also tick "App functionality", because the trained models power the swipe feature. Don't tick Advertising, Personalization, Account management, Developer communications or Fraud prevention. |

**The report form (#97)** (Email address, Crash logs, App interactions, Other user-generated content, Diagnostics):

| Question | Answer |
|---|---|
| Collected or shared? | **Collected.** Not shared. Sealed on the phone; Cloudflare stores it as a service provider and can't read it. |
| Is this data processed ephemerally? | **No.** Kept until the owner has dealt with the report, at most 2 years. |
| Is collection required, or can users choose? | **Users can choose.** Nothing is sent until Send; the email address and the recording are optional. |
| Why is this data collected? | **App functionality** (fixing bugs) and **Developer communications** (a reply, if an email address was given). **Uncertain:** Google's purposes have no "bug reports"; these two are the closest. |

**The supporters board** (Name, Purchase history, Other user-generated content, Device or other IDs):

| Question | Answer |
|---|---|
| Collected or shared? | **Collected.** Not shared: Cloudflare runs the Worker and its database as a service provider. The name and message show in public on the board, but only because the supporter chose to post them, which Google doesn't count as sharing. **Uncertain:** Google has no explicit rule for text a user posts to a public list; if a reviewer asks, the careful change is to also tick "Shared" for Name and Other user-generated content. |
| Is this data processed ephemerally? | **No.** Kept until the supporter taps "Remove me" or the owner deletes the entry. |
| Is collection required, or can users choose? | **Users can choose.** Buying never needs the board. Joining is a separate choice; name and message are optional, and anonymous keeps the credit off the public list. |
| Why is this data collected? | **App functionality.** The board ranks supporters by what Play confirms they gave. Don't tick Analytics or Advertising. |

### What the listing will then show (roughly)

- No data shared with third parties.
- Data collected: Personal info, Financial info, App activity, App info and performance, Device or other IDs. All optional.
- Data is encrypted in transit.
- You can request that data be deleted.

## IP addresses

Every download and upload shows the phone's IP address to the server. Google says to disclose IP addresses "based on how you
use them", and to declare location if you use an IP address to find where someone is.

TitanKeys never uses an IP address to find where someone is, and never keeps one. Two Workers use it against abuse:

- **The contributions Worker** hashes the address with a secret salt (`RATE_SALT`), and Cloudflare's rate limiter counts that
  hash in memory for one minute.
- **The support Worker** counts rank previews (60 an hour) and reports (5 an hour) by a salted hash of the address
  (`RATE_SALT`). Each counter covers one hour and sits in its database (`rate_limits`) until the daily job deletes past hours,
  so up to about a day. The address itself is never stored.
- Worker logging is off on both. GitHub Pages' own server logs are GitHub's, which the owner can't see or use.

**Answer:** nothing is declared for IP addresses. Google's form has no IP address type, and this use finds no location and ties
nothing to a person.

**Uncertain:** Google has no explicit rule for an address used only briefly against abuse. If a reviewer asks, the careful
answer is: "Device or other IDs", collected, processed ephemerally, not shared, purpose "Fraud prevention, security, and
compliance". The privacy policy says the contributions Worker counts the address for one minute; check that it also covers
the support Worker's hourly counter before the first upload.

## Things the 3.0 code must keep true for these answers

- Nothing is counted for sending, or sent, before the user taps "Turn on sharing" for the current envelope. Google: consent must
  come "before your app can begin to collect".
- The Sharing consent screen is its own screen, separate from the accessibility disclosure.
- Nothing sent comes from the Accessibility service, so its declaration stays "No".
- The news request stays free of identifiers: no contributor ID, no version in the request, no cookies.
- The board is fetched only while the Support page is open, and nothing is posted to it before the supporter chooses to share.
- The support Worker never stores a raw purchase token, order ID, email or address, and its Google API field masks leave out
  buyer details.
- There is no advertising ID: no `AD_ID` permission in the merged manifest (`permissions.md`).
- Any feed entry for the Play build never links to an APK download. Google Play doesn't allow an app to update itself by any
  other route ([Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/9888379)).
- The privacy policy says the same things: what is sent, why, how it is protected, how long it is kept, and how to delete it.

**The Sharing envelope and this form.** Counts that fit the kinds in analytics.md §1 (K2 to K6) fall under App interactions,
Diagnostics and Crash logs. Adding one needs no change here. A count that needs a new data type or a purpose other than
Analytics needs this form changed before that release, as well as the policy and the user's consent.

## Decided

1. **Retention:** swipe examples until used for training, at most 2 years after they arrive; daily totals 1 year; reports at
   most 2 years; board entries until removed. R2 and the Workers delete older data by themselves, and the privacy policy says so.
2. **Deletion after uninstalling:** Settings shows the contributor ID with a Copy button, and the user can still delete after
   stopping. The policy says how to ask for deletion with the ID by email. A supporter who loses the account key asks the owner
   to remove the entry (`server/support/README.md`).
3. **Sharing in the Play build:** yes, off by default, as in the GitHub build.
4. **Typing log (#16) in the Play build:** left out. It stays in the GitHub build.
5. **Purpose of Sharing:** Analytics only. Google's Analytics covers "future performance improvements", which is what training
   the swipe models is.

## Not verified, or to check when filling in the form

- The 2026 form's exact names and examples for Name, Purchase history, Crash logs, Diagnostics and App interactions. Check them
  on the live form.
- Whether Google wants Purchase history declared when the purchase itself goes through Play Billing. Declaring it is the
  cautious choice, since the Worker keeps a record per account.
- The privacy policy (`site/privacy/`) still says the public board "shows only cups". The board shows place, name and message,
  never an amount (`server/support/README.md`, owner 2026-11-19). Fix the policy's wording before the first upload.
