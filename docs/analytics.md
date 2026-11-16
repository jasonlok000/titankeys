# Analytics and Share swipes: one sharing toggle (#96, draft for the owner)

Status: **draft for the owner to review. No app code yet.** Written 2026-11-16 from the owner's answers on #96. It covers:
- the consent envelope and the never-list;
- the one sharing toggle and its consent screen;
- every count, with its exact definition;
- the daily payload, the Worker endpoint and retention;
- a check against GDPR, Google Play and Android;
- drafts of the privacy policy and Data safety changes.

Text marked **F: draft** goes to F for wording before it ships.

This is not legal advice. The sections marked **Lawyer** are where a lawyer should look before the 3.0 launch.

## What the owner decided

- **Opt in.** Analytics and Share swipes become one thing, behind one sharing toggle. There is no separate switch per kind of sharing.
- **Defaults agreed:**
  - one batch a day;
  - sent to the contributions Worker and kept a year;
  - a random ID that changes every 30 days;
  - only the owner sees it;
  - nothing is sent from TitanKeys Debug;
  - turning it off deletes what's waiting.
- **Keep all the proposed counts** (listed below).
- **Consent to an envelope** (owner: "keep it easy for us. And make sure that it's both legal and google play safe"):
  - The user agrees once. They agree to stated kinds of data, for one stated purpose, with a clear list of what is never collected.
  - A new count inside those kinds needs no new consent.
  - The app's "What's shared" page always lists the counts sent today.

## 1. The envelope

The user agrees to these **kinds** of data, for this **purpose**. Nothing else is in the envelope.

### Purpose (one)

To find out how well TitanKeys works on real phones and make it work better. In practice:
- training and testing the swipe models;
- tuning autocorrect, joins, suggestions and gestures;
- improving the guide;
- finding crashes and slow spots.

The data is never used for ads or profiling. It is never sold or given to anyone, and it is never used to decide anything about a person.

### Kinds of data

| # | Kind | What it covers | Example |
|---|---|---|---|
| K1 | **Swipe examples** | The path, timing and touch size of a swipe; the word kept, only if it's a dictionary word; the swipe engine's ranking numbers; how the word was settled | What Share swipes sends today |
| K2 | **Daily feature counts** | How many times a day a TitanKeys feature acted, and how many of those results were kept, picked, undone or retyped. Totals only, never single events. | "autocorrect made 41 fixes, 3 were undone" |
| K3 | **Speed bands** | How long the keyboard took for a job, counted in coarse bands | "the keyboard opened in under 100 ms 212 times" |
| K4 | **Settings** | Which settings are changed from their defaults, as changed or not changed only, never the value chosen | "Double space for a full stop: off" |
| K5 | **Guide progress** | The furthest slide reached, whether the guide was finished, and which hunts were skipped | "reached slide 14, skipped 2 hunts" |
| K6 | **Crash and freeze counts** | How many times TitanKeys crashed or froze that day, with no details | "1 freeze" |
| K7 | **Technical facts** | The TitanKeys version and build (GitHub or Play), the model versions, the Android version, and the phone model | "3.0.0, play, Android 15, Titan 2 Elite" |
| K8 | **Random IDs** | Each is made on the phone and tied to nothing else: a contributor ID for swipes, and a counts ID that changes every 30 days | |

A new count needs no new consent if all of these hold:
1. It fits one of K2–K6.
2. It serves the purpose above.
3. It breaks nothing on the never-list.

Then it ships with an update to the "What's shared" page and to the list in §3.

Anything else needs the privacy policy, the Data safety form and the user's consent updated first, before the app sends it. That includes:
- a new kind;
- a new purpose;
- anything per event rather than a daily total;
- anything with a time of day;
- anything that breaks the never-list.

### Never collected (the never-list; F: draft)

This list is the promise. It is shown on the consent screen and on the "What's shared" page, word for word.

- **What you type.** No text you type, and no text around a word. Counts carry no words at all.
- **Words**, except the word you kept after a swipe, and only if it's in the dictionary. Names and other words that aren't in the dictionary are never sent.
- **Anything from password, email, web address or private fields.** Nothing typed in them is counted either.
- **Your clipboard, snippets and shorthand.**
- **Which apps you use or the names of your apps.**
- **Your contacts.**
- **Your location.** Your internet address is never used to find where you are, and it's never stored.
- **When you type.** There are no times of day: counts are per day, and swipes carry only the milliseconds within each swipe.
- **Who you are.** No name, account, email, phone number, advertising ID or phone ID. The only IDs are TitanKeys' own random ones.
- **Anything from the Accessibility service.**
- **No third parties.** No analytics or ads SDKs. Nothing is sold, shared or used for ads.

## 2. The one toggle

### Where it lives

- **Settings > Share swipes** becomes **Settings > Sharing**. The route stays `/settings/share-policy` for the disclosure.
- One switch, **Share to improve TitanKeys** (F: draft), covers K1–K8.
- The guide's last slide still offers it once, as Share swipes is offered today.
- The page also keeps today's tools:
  - the counts of waiting and sent swipes;
  - **Delete waiting**;
  - **Delete what I've sent**;
  - the contributor ID with **Copy**.
- New on the page:
  - **What's shared**: today's counts, the payload waiting to go, readable, with each count's meaning in plain words;
  - the list of counts the app sends.

### People who already share swipes

They agreed to swipe examples (K1) only. Their swipes keep going, but **counts don't start until they agree to the new screen**.
- The app shows that screen once, after the update.
- "Not now" turns sharing **off** entirely, because there is one toggle. They can turn it back on in Settings at any time.

**Call to confirm:** the other way is to keep swipes going for them on "Not now", which needs a hidden swipes-only state, against "one toggle". The draft turns sharing off.

### The consent screen (F: draft; a clean zone: serious and plain)

> **Before you share**
>
> If you turn this on, TitanKeys sends us two things, so we can see how well it works on real phones and make it better.
>
> **Examples of your swipes.** How each swipe moved across the keys, with its timing and touch size. The word you kept, only if it's in the dictionary. The numbers the swipe engine used to pick it.
>
> **Daily totals.** How often TitanKeys' features worked and how often you undid or changed what they did, such as autocorrect fixes undone or swipes retyped. Also how fast the keyboard was, which settings you changed from their defaults, how far you got in the guide, and how many times it crashed. Only totals for the day, never what you did one by one.
>
> With them go the TitanKeys, Android and model versions, your phone model and random IDs that aren't tied to you.
>
> **What's never sent:** what you type, or the text around a word. Names and other words that aren't in the dictionary. Anything from password, email, web address or private fields. Your clipboard, snippets and shorthand. Which apps you use, your contacts and your location. What time of day you type. Anything that identifies you or your phone.
>
> We may add new totals of the same kinds later. They're always listed under "What's shared" in Settings, and we'll ask again before we send any other kind of data.
>
> It's all encrypted on your phone before it's sent, and only we can read it. Swipes go on Wi-Fi only. You can turn this off at any time, and delete what you've sent, in Settings > Sharing.
>
> [**Turn on sharing**] [Not now]
>
> Read the full privacy policy

Rules for the screen (Google Play and GDPR, see §6):
- It is its own screen, not inside the Accessibility disclosure or the guide's text.
- Nothing is sent before **Turn on sharing**.
- Leaving the screen, Back or Home is not consent.
- "Not now" is as easy to reach as "Turn on".
- Turning it off is one switch, as easy as turning it on.

## 3. The counts

Rules for every count:
- Counted on the phone, in memory and a small file in `noBackupFilesDir`.
- Each day's total is sent once, then cleared.
- Only typing in ordinary fields is counted. Password, email, web address and private fields (`TextFieldPrivacy`), and TitanKeys' own playground, are not.
- A count of 0 is left out of the payload.

"Right after" means the user's very next action on that word, before typing anything else or moving the cursor away. That means one of:
- Backspace, which TitanKeys treats as an undo;
- picking another word for it from the bar;
- deleting it and typing it again.

The keys below are the payload's names.

### Joins (two swipe strokes joined into one word, #83)

| Key | Definition |
|---|---|
| `join.total` | Times the keyboard joined two strokes into one word. |
| `join.undone` | Of `join.total`, times the joined word was undone right after: deleted, split, or replaced from the bar. A wrongly joined word. |
| `join.byHand` | Times two strokes the keyboard typed as two words were put together by hand right after: the space between them deleted, or one word typed over them. A missed join. |

### Autocorrect and apostrophe fixes

| Key | Definition |
|---|---|
| `autocorrect.made` | Times autocorrect replaced a typed word on Space or punctuation. |
| `autocorrect.undone` | Of those, times the user undid it right after, or picked what they had typed from the bar. |
| `apostrophe.made` | Times an apostrophe fix (were → we're, its → it's and the like, #73) was made. |
| `apostrophe.undone` | Of those, times it was undone right after. |

### Swipes, by word length

Each swipe key is split by the length of the word that was finally kept, in bands `1-2`, `3-4`, `5-7` and `8+` letters. For example, `swipe.kept.5-7`.

| Key | Definition |
|---|---|
| `swipe.kept.<band>` | Swipes whose first choice was kept. |
| `swipe.picked.<band>` | Swipes where another word was picked from the bar. |
| `swipe.retyped.<band>` | Swipes whose word was deleted right after and then swiped or typed again. The band is the retyped word's length. |

Every swipe ends up in exactly one of the three, so their sum is the day's swipes.

### The bar

| Key | Definition |
|---|---|
| `bar.place.<n>` | Words picked from the bar, by the place they held: `1` is the first. |
| `bar.space` | Picks made by Space, which takes the bar's first word. |
| `bar.tap` | Picks made by tapping a word. |

### Chinese picking

| Key | Definition |
|---|---|
| `zh.pick.space`, `zh.pick.tap`, `zh.pick.shift`, `zh.pick.hold`, `zh.pick.flick` | Chinese candidates picked by each way of picking. |
| `zh.bigMenu` | Times the bigger candidate menu was opened. |

### Gestures (double space, triple space, flick to delete; steering)

| Key | Definition |
|---|---|
| `gesture.flickDelete` | Words deleted by a flick. |
| `gesture.flickDelete.undone` | Of those, times the deleted word was put back right after. |
| `gesture.space` | Double and triple Space taps that did their job, such as a full stop. |
| `gesture.space.undone` | Of those, undone right after. |
| `gesture.steer` | Times the cursor was steered on the touchpad. |
| `gesture.steer.undone` | Of those, times the cursor was put back where it started right after. |

### Speed bands

| Key | Definition |
|---|---|
| `speed.open.<band>` | Times the keyboard opened, by how long it took from Android asking for it to the keys being drawn. Bands: `<50`, `50-100`, `100-200`, `200-500`, `500+` ms. |
| `speed.swipe.<band>` | Swipes, by how long it took from the finger lifting to the word being shown. Bands: `<30`, `30-60`, `60-120`, `120-250`, `250+` ms. |

### Guide

Sent only on a day it changes.

| Key | Definition |
|---|---|
| `guide.furthest` | The ID of the furthest slide reached, such as `cursor`. |
| `guide.finished` | Whether the guide was finished, `true` or `false`. |
| `guide.huntsSkipped` | The IDs of the hunts skipped. |

### Settings

Sent only on a day one changes, and on the first day.

| Key | Definition |
|---|---|
| `settings.changed` | The keys of the settings whose value differs from the default. For a switch, that is just its key. For a choice, only that it changed, never which value was chosen. Typed values such as snippets, shorthand, word lists and app lists are never included, not even as "changed". |

### Crashes

| Key | Definition |
|---|---|
| `crash.count` | Crashes that day. |
| `freeze.count` | Freezes (Android's "not responding") that day. |

Full crash reports still go only through the report form, when the user presses Send (#74).

### Swipe examples

Unchanged from Share swipes: K1, as the privacy policy describes it today. They keep their own queue and the stable contributor ID, and go on Wi-Fi only.

## 4. The daily payload

### When it's sent

- Once a day, by the existing `ConnectedJob`, on any network.
- It's skipped when there's nothing new.
- Never from TitanKeys Debug (`HapticsLab.available` or the build type).

### What's in it

- The day is the phone's local date, with no time.
- At most 7 days wait on the phone. Older ones are dropped.

### How it's sealed

- The JSON is gzipped, then sealed on the phone with the contributions key, as reports are (`Seal.seal`, X25519 and AES-256-GCM).
- It is sent over HTTPS.
- It's a few hundred bytes sealed.

```json
{
  "v": 1,
  "envelope": 1,
  "id": "9f2c41e07b3d4a5c8e1f6a2b3c4d5e6f",
  "day": "2026-11-16",
  "app": "3.0.0",
  "build": "play",
  "android": "15",
  "phone": "Titan 2 Elite",
  "models": { "swipe": "2.0", "lm": "distilgpt2-1" },
  "counts": {
    "join.total": 12, "join.undone": 1, "join.byHand": 2,
    "autocorrect.made": 41, "autocorrect.undone": 3,
    "swipe.kept.3-4": 120, "swipe.picked.5-7": 9, "swipe.retyped.8+": 2,
    "bar.place.1": 30, "bar.place.2": 6, "bar.space": 25, "bar.tap": 11,
    "speed.open.50-100": 212, "speed.swipe.30-60": 140,
    "crash.count": 0
  },
  "guide": { "furthest": "cursor", "finished": false, "huntsSkipped": ["touchpad"] },
  "settings": { "changed": ["double_space_period", "theme"] }
}
```

### The IDs

- `id` is the **counts ID**: 128 random bits from `SecureRandom`, replaced every 30 days.
- It is never the contributor ID, and it is never sent in the same request as swipes.
- The phone keeps the counts IDs of the last 13 periods, a year, so it can ask for their deletion.
- They're kept in `noBackupFilesDir`, out of Android's backup, so an ID never moves to another phone or a reinstall.
- `envelope` is the version of the envelope the user agreed to. The phone sends nothing if the user agreed to an older envelope than the app's.

## 5. The Worker, retention and deletion

### Endpoint

- `POST /v1/counts` on the contributions Worker (`server/contributions/src/worker.js`).
- The body is the sealed payload. The header `X-Counts` carries the counts ID.
- It's write-only, like `/v1/batch` and `/v1/report`: nothing can be read back through it.
- Limits:
  - the same per-address limit as the other routes, hashed and held one minute, never stored;
  - a size cap of 4 KB;
  - one payload per ID per day, so a resend replaces the first.

### Storage

- Each payload is filed under a hash of the counts ID, as swipe batches are under the contributor ID. The date it arrived is kept.
- A daily alarm deletes payloads more than **1 year** old.

### Export

- Only the owner can export, through the existing `/v1/export` token route. Everything comes out still sealed.
- The owner opens it offline and works out totals there.
- No dashboard is hosted anywhere.

### Deletion

- **Turning sharing off:**
  - stops counting and sending at once;
  - deletes today's counts and the waiting days;
  - deletes the swipe queue, as now.
- **Delete what I've sent:** asks the Worker to delete everything for the contributor ID (swipes) and for each counts ID the phone still holds (counts, up to a year back). `POST /v1/delete` gains `"counts": [ids]`.
- **After an uninstall:**
  - The contributor ID can still be sent by email to delete swipes, as now.
  - The counts IDs are gone with the phone. The owner can't tell whose counts are whose, so they can't be singled out.
  - They are deleted after a year anyway.
  - The policy says so plainly (GDPR Art. 11).

### Retention, side by side

| What | On the phone | On the server and with the owner |
|---|---|---|
| Swipe examples | Until sent, at most 4 MB | Until used for training, 2 years at most (unchanged) |
| Daily counts | Until sent, 7 days at most | **1 year** |
| Reports from the form | Until sent | 2 years at most (unchanged) |

## 6. Checked against the rules

### GDPR and UK GDPR (EU and UK users)

**Is it personal data?** Probably yes. Daily counts filed under a random ID are pseudonymous data, and pseudonymous data is still personal data (Recital 26). So GDPR applies even though the owner can't tell who anyone is.

| Point | How the design meets it |
|---|---|
| **Lawful basis: consent** (Art. 6(1)(a)) | Opt in, on its own screen, by a clear action ("Turn on sharing"). Off by default. Nothing is sent before it. |
| **Consent: freely given, specific, informed, unambiguous** (Art. 4(11), Art. 7) | <ul><li>Freely given: TitanKeys works the same without it.</li><li>Specific and informed: one stated purpose, the kinds K1–K8 described in plain words, and the never-list.</li><li>Unambiguous: an affirmative tap.</li></ul> |
| **Proving consent** (Art. 7(1)) | The phone records the date and the envelope version the user agreed to, and each payload carries `envelope`. |
| **Withdrawal as easy as giving** (Art. 7(3)) | One switch in Settings > Sharing. Turning it off stops sending and deletes what's waiting. |
| **Purpose limitation** (Art. 5(1)(b)) | One purpose, improving TitanKeys. A new purpose needs new consent (§1). |
| **Data minimisation** (Art. 5(1)(c)) | <ul><li>Daily totals worked out on the phone; no per-event logs.</li><li>No words in counts; settings as changed or not only.</li><li>Speed in coarse bands, dates without times.</li><li>An ID that changes every 30 days, never joined to the swipe ID.</li></ul> |
| **Storage limitation** (Art. 5(1)(e)) | Counts 1 year; swipes and reports 2 years at most. The server deletes them by itself. |
| **Security** (Art. 32) | Sealed on the phone with a key whose private half is offline. HTTPS. The server can't read anything. No address is stored. |
| **Information to the user** (Art. 13) | The consent screen in short, and the privacy policy in full: who, what, why, basis, retention, rights, processors, transfers, contact. See §7. |
| **The user's rights** (Art. 15–21) | <ul><li>Access: "What's shared" shows what's waiting. Sent data is sealed, and the owner can open it on request with the IDs.</li><li>Erasure: in the app, or by email with the contributor ID.</li><li>Withdrawal: the switch.</li><li>Portability and objection: by email.</li><li>Where the user no longer has an ID, the owner can't find their data, and Art. 11(2) applies. The policy says so.</li></ul> |
| **Processor** (Art. 28) | Cloudflare runs the Worker and its storage as a processor. Its standard DPA covers Workers. |
| **Transfers outside the EU and UK** (Art. 44 on) | Cloudflare may process in the US. It relies on the EU–US Data Privacy Framework and standard contractual clauses. The policy should name this. |
| **The envelope and new counts** | Consent covers the kinds and the purpose as described. A new count inside the same kind and purpose is processing the user already agreed to, so long as the description stays true. The "What's shared" page keeps it visible. A new kind or purpose needs new consent (EDPB Guidelines 05/2020 on consent; Art. 5(1)(b)). |
| **One toggle for two things** | Recital 32: when processing has several purposes, consent should cover all of them. Here both are one purpose, improving TitanKeys, described together, so one consent fits. The EDPB prefers separate consent for separate purposes, and that is the main risk of "one toggle". |
| **ePrivacy** (Art. 5(3); UK PECR reg. 6) | Keeping counts on the phone and reading them back to send them is storing and reading on the user's device. The same opt-in covers it. |

**Lawyer: GDPR**
1. Whether the envelope's kinds (K2–K6) are described specifically enough that new counts inside them need no new consent.
2. Whether one toggle for swipe examples and usage counts is granular enough, or a lawyer would want two boxes on one screen (one switch in Settings could still turn both off).
3. Whether the owner needs an **EU representative** (Art. 27) and a UK representative.
   - TitanKeys is offered to EU and UK users, and the owner is probably outside both.
   - Art. 27(2) exempts occasional processing that is unlikely to be a risk. Daily counts are not occasional.
4. Whether a **record of processing** (Art. 30) is needed.
   - The fewer-than-250-employees exemption doesn't apply to processing that isn't occasional.
   - Writing a one-page record is cheap either way.
5. Whether a **DPIA** (Art. 35) is needed. It's probably not: the data is small, pseudonymous and not sensitive, and there's no profiling. A short written note that says why is good practice.
6. The policy wording on **Art. 11** (data the owner can no longer link to a person) and on **Cloudflare's transfers**.
7. Any **local law where the owner lives**, and for other big markets (California's CCPA is out of scope below its thresholds; Brazil's LGPD is similar to GDPR).

### Google Play

#### User Data policy, "Prominent Disclosure and Consent"

| Requirement | Met by |
|---|---|
| The disclosure is inside the app, in normal use, not only in the policy or a store listing | The consent screen, shown before anything is sent, and the guide's offer |
| It says what data is collected and how it's used | The kinds and the purpose, in plain words, plus the never-list |
| Consent is an affirmative action; leaving or Back is not consent | "Turn on sharing" only |
| It comes before collection starts | Nothing is counted for sending, or sent, before it |
| It is separate from other disclosures | Its own screen, apart from the Accessibility disclosure |

#### Data safety form

- The form must be accurate at all times, so it is updated **before** the first 3.0 Play release that sends counts.
- New counts inside the envelope fall under data types already declared, so they need no form change. That makes the envelope Play-safe.
- A new data type or purpose needs a form update before release.
- The changes are in §8 and in `docs/play/data-safety.md` under "3.0".

#### Other Play points

- **Accessibility API policy:** data from the Accessibility service may only serve its declared purpose. No count may use Accessibility events (Sym panel keys, launcher search). The declaration stays "No, collects no data".
- **No advertising ID** and no other persistent device identifier, so the Play rule against linking device IDs to personal data doesn't come into it. TitanKeys has no `AD_ID` permission. Keep it that way, and check the merged manifest of every release.
- **Families policy:** TitanKeys isn't for children (rating: not for under 13), so it is out of scope.

**Lawyer and Play:**
- The exact wording of the 2026 form's data types (App interactions, Diagnostics, Crash logs).
- Whether a reviewer reads "daily totals of how often you undo autocorrect" as about typed content. The answer is no: no text or keys, only counts. The consent screen says so.

### Android

| Rule | How the design meets it |
|---|---|
| Keyboards can see everything typed. Android warns users of that when they enable a keyboard, and expects keyboards never to send it. | Counts carry no text, and nothing is counted in password or private fields (`TYPE_TEXT_VARIATION_PASSWORD`, `IME_FLAG_NO_PERSONALIZED_LEARNING`, `TextFieldPrivacy`). |
| No hardware or Android IDs (Android discourages `ANDROID_ID` and hardware IDs for analytics) | Random IDs only. A new counts ID every 30 days. |
| Backup | The IDs and the waiting counts live in `noBackupFilesDir`. TitanKeys has no `allowBackup="false"` today, so Auto Backup is on. The sharing files must stay out of it, or an ID would follow the user to a new phone. |
| Background work | WorkManager through `ConnectedJob`, as today. No foreground service, no exact alarms. |
| Network | HTTPS only, through the same `Connected.open`. |
| Permissions | `INTERNET` and `ACCESS_NETWORK_STATE` only, both already declared. Nothing new. |

### Things the 3.0 code must keep true

These are for whoever builds #96 after the owner's yes:
- Nothing is counted for sending, and nothing is sent, until the user agreed to the current envelope version. Debug never sends.
- Counts never come from the Accessibility service and never include a word.
- The counts request carries the counts ID only, never the contributor ID, and goes separately from swipe batches.
- The sharing files stay in `noBackupFilesDir`.
- The "What's shared" page lists exactly the counts the code sends. A check in `tools/checks` should compare the page's list with the payload's keys, so a count can't ship unlisted.
- `docs/analytics.md` §3, the "What's shared" page and the policy change together.

## 7. Privacy policy changes (draft; not on the live site)

The live policy (`site/privacy/index.html`) must describe the app that is out. These changes go live with the 3.0 release that sends counts, not before.

### "In short"

Replace the "Swipe contributions" bullet with (F: draft):

> **Sharing:** only if you agree, it sends examples of your swipes and daily totals of how well the keyboard worked, to make TitanKeys better. Never what you type. It's encrypted on your phone. You can stop and delete it at any time.

### The section "What TitanKeys sends, only if you agree"

Retitle the section **"What TitanKeys sends, only if you agree: sharing"**.

Keep the swipe examples as they are, under **"Swipe examples"**.

Add **"Daily totals"** (F: draft):

> If sharing is on, TitanKeys also keeps daily totals on your phone of how its features worked, and sends one small encrypted batch a day. It has:
> - How many times a day TitanKeys' features acted, and how many of those you kept, picked, undid or retyped: for example joined swipes, autocorrect and apostrophe fixes, swipes by word length, words picked from the bar, Chinese candidates, and gestures.
> - How fast the keyboard opened and read swipes, counted in rough bands.
> - Which settings you changed from their defaults, only as changed or not, never what you chose. Never your snippets, shorthand or word lists.
> - How far you got in the guide, and which hunts you skipped.
> - How many times TitanKeys crashed or froze that day, without details.
> - The TitanKeys, Android and model versions, the phone model, the date (no time), and a random ID that changes every 30 days. It's never the same as your contributor ID and isn't sent with your swipes.
>
> The full list of totals is in Settings > Sharing > What's shared, with what each one means. We may add totals of these same kinds. They show there first, and we'll ask you again before we send any other kind of data or use it for anything else.

Add to **"What is never sent"**:
- "What time of day you type."
- "Anything from the Accessibility service."

Retitle **"Why"** to **"Why, and on what basis"** (F: draft):

> To find out how well TitanKeys works on real phones and to make it better: training the swipe models, tuning autocorrect, joins, suggestions and the guide, and fixing crashes and slow spots. We use it for nothing else. Our legal basis is your consent, which you can take back at any time in Settings.

Add to **"How long it's kept"**:

> Daily totals: on your phone until they're sent, 7 days at most; on the server and with us, 1 year, then deleted automatically.

Add to **"How to stop and delete"**:
- Turning sharing off also deletes the daily totals still waiting.
- **Delete what I've sent** also deletes the totals for the random IDs your phone used in the last year.
- After you remove TitanKeys, those IDs are gone with it. We can't tell which totals were yours, so we can't find or delete them, and they're deleted after a year anyway.

### "Your rights" (new section, before "Children"; F: draft)

> If you're in the EU, the UK or another place with similar laws, you have the right to see, correct, delete and receive a copy of your data, to object to how it's used, and to take back your consent at any time. Taking it back doesn't affect what was done before. To use these rights, email us. Swipe examples are found by your contributor ID; daily totals only while TitanKeys is still on your phone. You can also complain to your data protection authority.
>
> Our server runs on Cloudflare, which processes data for us under its data processing terms. It may process data in the US, under the EU–US Data Privacy Framework and standard contractual clauses.

### Permissions

The Internet row: "For the news and, only if you agree, for sharing."

### Changes to this policy

Keep as it is: "TitanKeys never starts to send a new kind of data without asking you first" already states the envelope rule.

### Elsewhere

These also change:
- the guide's Share swipes topic and offer (`guide-boring.tsx`, `GuideActivity`);
- `lib/connected-text.ts`;
- the Reset texts that name Share swipes.

All of it goes to F.

## 8. Data safety changes (summary; detail in `docs/play/data-safety.md`, section "3.0")

| Data type | Today (2.1) | With sharing (3.0) |
|---|---|---|
| App interactions | Collected, optional: swipe examples, bug recordings | **Also** the daily feature counts, guide progress and settings changed. Same answers: collected, not shared, optional, Analytics. |
| Diagnostics | Collected: versions, phone model | **Also** speed bands and the Android version. Same answers. |
| Crash logs | Collected, optional: report form only | **Also** the daily crash and freeze counts, collected through sharing, optional, Analytics. |
| Device or other IDs | Collected: contributor ID | **Also** the counts ID, which changes every 30 days. Same answers. |
| Other user-generated content | Collected: kept dictionary word, report text | Unchanged |

Unchanged:
- encrypted in transit: yes;
- a way to ask for deletion: yes;
- shared with third parties: no;
- the purpose: Analytics only.

Nothing new is declared under Personal info, Location, Messages or Installed apps.

## Calls to confirm (owner)

1. **Existing sharers:** counts wait for a fresh yes. "Not now" turns sharing off entirely (§2).
2. **The toggle's name:** "Sharing" and "Share to improve TitanKeys". F will word it.
3. **Two IDs:** swipes keep their stable contributor ID, which is needed for deletion after an uninstall. Counts get the separate 30-day ID, so the two can't be joined.
4. **Counts go on any network.** Swipes stay Wi-Fi only.
5. **Speed and length bands** as listed. They can be changed inside the envelope later.
6. **The Android version** is added to the technical facts (K7). Swipes don't send it today.
7. **One consent screen, not two boxes.** That's the owner's choice and is kept, with the GDPR risk named above for the lawyer.
