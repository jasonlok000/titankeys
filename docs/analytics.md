# Sharing: swipes and daily totals under one switch (#96)

Status: **built** on `claude/m3-analytics` (2026-11-16), from the owner's answers on #96. The Worker's new route is written and
tested but **not deployed**: it waits for the Orchestrator's go. This doc covers:
- the consent envelope and the never-list;
- the one sharing switch and its short consent screen;
- every count as the code sends it;
- the daily payload, the Worker endpoint and retention;
- the legal reasoning against GDPR, Google Play and Android;
- what changed in the privacy policy and the Data safety answers.

Text marked **F: draft** goes to F for wording.

This is not legal advice. TitanKeys is a hobby project and won't get a lawyer (owner, 2026-11-16: "no bro i wont get a lawyer. this
is a hobbyiest project."), so the open legal points below are kept as notes, not as steps.

## What the owner decided

- **Opt in, one switch, all or nothing.** Analytics and Share swipes are one thing: "either share everything or not share anything
  at all." There is no separate switch per kind of sharing.
- **Defaults agreed:**
  - one batch a day;
  - sent to the contributions Worker and kept a year;
  - a random ID that changes every 30 days;
  - only the owner sees it;
  - nothing is sent from TitanKeys Debug;
  - turning it off deletes what's waiting.
- **Keep all the proposed counts.**
- **Consent to an envelope** (owner: "keep it easy for us. And make sure that it's both legal and google play safe"):
  - The user agrees once. They agree to stated kinds of data, for one stated purpose, with a clear list of what is never collected.
  - A new count inside those kinds needs no new consent.
  - The app's "What's shared" page always lists every count.
- **A short consent screen, the full policy separate** ("keep the full policy seperate. the user main screen shd be still be
  simple and straight to the point").
- **No migration for people who already share swipes** ("there's no real users").

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

## 2. The one switch

### Where it lives

- **Settings > Share swipes** is now **Settings > Sharing**. Its switch, **Share to improve TitanKeys** (F: draft), covers K1–K8.
- The guide's end still offers it once, as before.
- The page keeps:
  - the counts of waiting and sent swipes;
  - **Delete waiting swipes**;
  - **Delete what I've sent**, which now deletes the daily totals too;
  - the contributor ID with **Copy**.
- **What's shared** (`app/settings/contribute-about.tsx`) shows:
  - what is sent and what never is;
  - today's totals so far, as they would go, with each one's meaning;
  - the days waiting to send;
  - every daily total the app can send, from `lib/sharing-counts.ts`.

### Agreeing

- Turning it on records the envelope's version and the day, in "connected" (`sharing_envelope`, `sharing_agreed_on`). That is the
  proof of consent (Art. 7(1)).
- The phone sends totals only while that version is at least the app's (`DailyCounts.ENVELOPE`). A wider envelope waits for a
  new yes.
- Someone who turned Share swipes on before this build has no recorded version, so their totals wait until they turn the switch
  off and on again. The owner said there are no real users to migrate, so nothing asks them.

### The consent screen (F: draft; a clean zone: serious and plain)

It's short, with the full policy one tap away. The app's page (`lib/connected-text.ts`, `sharePolicyText`) and the guide's copy
(`ShareDisclosureActivity`) say the same, word for word.

> **Before you share**
>
> Sharing sends us examples of your swipes and daily totals of how TitanKeys works, so we can make it better. Nothing is sent
> until you turn it on.
>
> **WHAT'S SHARED**
> - Your swipes: how each moved across the keys, and the word you kept, only if it's in the dictionary.
> - Daily totals, such as fixes undone, swipes retyped, slides skipped, settings changed and crashes.
> - Versions, your phone model and random IDs.
>
> **NEVER SHARED**
> - What you type, names, anything from password or private fields, your clipboard, your apps, contacts or location.
>
> It's encrypted on your phone. Turn it off any time in Settings > Sharing, and delete what you've sent.
>
> [**Turn on sharing**] [Not now]
>
> Read the full policy

The rules it keeps (Google Play and GDPR, see §6):
- It is its own screen, not inside the Accessibility disclosure or the guide's text.
- Nothing is sent before **Turn on sharing**.
- Leaving the screen, Back or Home is not consent.
- "Not now" is as easy to reach as "Turn on".
- Turning it off is one switch, as easy as turning it on.

## 3. The counts

**The list is `lib/sharing-counts.ts`.** It holds every key with its meaning, as What's shared shows them.
`tools/checks/sharing.test.ts` fails if the keyboard sends a count missing from it, or if it lists one the keyboard doesn't send.

Rules for every count:
- Counted on the phone, in memory, and saved to `noBackupFilesDir/sharing/counts.json` when a field ends.
- Each day's totals close when the date changes, wait at most 7 days, and are sent once.
- Only typing in fields that allow learning is counted. Password, email, web address and private fields aren't
  (`Field.learning`), and nor are the guide's own fields.
- A count's key must match `DailyCounts.KEY`. The keys are fixed strings in the code, so no word can become one.

"Right after" means the user's very next action on that word: Backspace (TitanKeys' undo), a word from the bar, or deleting it
and typing it again.

| Group | Keys | Where it's counted |
|---|---|---|
| Swipes | `swipe.kept.<length>`, `swipe.picked.<length>`, `swipe.retyped.<length>` | `Composer.settled`, as the word settles; length bands `1-2`, `3-4`, `5-7`, `8+` |
| Swipe speed | `speed.swipe.<band>` | from the finger lifting to the word going in: `<30`, `30-60`, `60-120`, `120-250`, `250+` ms |
| Joins (#83) | `join.total`, `join.undone`, `join.keptApart`, `join.keptApart.redone` | `Composer`'s join decision; "undone" and "redone" when that word was later swapped or retyped |
| Fixes | `autocorrect.made/undone`, `apostrophe.made/undone`, `capital.made/undone` | `Composer.offerUndo`, and Backspace's undo; the kind by `fixKind` |
| The bar | `bar.place.<place>` | a word tapped on the bar, by its place (1 is the first) |
| Chinese and Japanese | `zh.` or `ja.` + `pick.space`, `pick.tap`, `pick.flick`, `pick.menu`, `bigMenu` | the keyboard's choice handling |
| Gestures | `gesture.flickDelete`, `gesture.deleteHold`, `gesture.doubleSpace`, `gesture.tripleSpace`, `gesture.steer`, `gesture.selectWords` | the keyboard, as each gesture does its job |
| Opening speed | `speed.open.<band>` | from a new field's keys being asked for to their next draw: `<50`, `50-100`, `100-200`, `200-500`, `500+` ms |
| Crashes | `crash.count`, `freeze.count` | written as a line when it happens (Java crashes) or found in Android's exit records (freezes, native crashes); added to the day TitanKeys next runs |
| Guide | `guide.furthest`, `guide.finished`, `guide.skipped` | the guide, on each slide and each Skip; sent only on a day it changed |
| Settings | `settings.set` | the keys of the settings that hold a value of the user's own: changed since the install or the last Reset most things. Never the value, never the user's lists. Sent only on a day the list changed. |

**How the built counts differ from the draft** (inside the envelope; calls to confirm):
- **Missed joins.** `join.byHand` (two words merged by hand) can't be told from typing. It became `join.keptApart`: a quick
  touch the keyboard kept as a second word. `join.keptApart.redone` counts the ones whose second word was then swapped or retyped.
- **Capitals.** `capital.made/undone` is new, so that "i" to "I" doesn't fill `autocorrect.made`.
- **The bar.** `bar.space` and `bar.tap` are gone. In English, Space never picks from the bar; Space's own fix is autocorrect,
  counted there. Chinese and Japanese Space picks are `zh.pick.space` and `ja.pick.space`.
- **Chinese picks.** Shift and hold don't pick in today's keyboard, so `pick.menu` takes their place. Japanese is counted too,
  under `ja.`.
- **Gestures.** Their "undone right away" isn't counted, because the keyboard has no undo for them to watch. The delete hold and
  selecting words are counted.
- **Guide.** `guide.skipped` lists every slide skipped before it was done, hunts among them.

### Swipe examples

Unchanged from Share swipes: K1, as the privacy policy describes them. They keep their own queue and the stable contributor ID,
and go on Wi-Fi only.

## 4. The daily payload

### When it's sent

- Once a day, by the existing `ConnectedJob`, on any network.
- It's skipped when there's nothing new.
- Never from TitanKeys Debug (`Connected.build` is "debug"). Debug still counts, so What's shared can be checked on the phone.
- Only under the envelope the user agreed to (§2, "Agreeing").

### What's in it

- The day is the phone's local date, with no time.
- At most 7 days wait on the phone. Older ones are dropped.

### How it's sealed

- The JSON (`DailyCounts.payload`) is gzipped, then sealed on the phone with the contributions key, as reports are (`Seal.seal`, X25519 and AES-256-GCM).
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
  "models": "3f9a1c0b7e2d",
  "counts": {
    "join.total": 12, "join.undone": 1, "join.keptApart": 4, "join.keptApart.redone": 1,
    "autocorrect.made": 41, "autocorrect.undone": 3, "capital.made": 9,
    "swipe.kept.3-4": 120, "swipe.picked.5-7": 9, "swipe.retyped.8+": 2,
    "bar.place.1": 30, "bar.place.2": 6,
    "speed.open.50-100": 212, "speed.swipe.30-60": 140,
    "crash.count": 1
  },
  "guide": { "furthest": "cursor", "finished": false, "skipped": ["touchpad"] },
  "settings": { "set": ["autocorrect", "haptics", "sentence_spaces"] }
}
```

### The IDs

- `id` is the **counts ID**: 128 random bits from `SecureRandom`, replaced every 30 days.
- It is never the contributor ID, and it is never sent in the same request as swipes.
- The phone keeps the counts IDs of the last 13 periods, a year, so it can ask for their deletion.
- They're kept in `noBackupFilesDir`, out of Android's backup, so an ID never moves to another phone or a reinstall.
- `envelope` is the version of the envelope the payload falls under (`DailyCounts.ENVELOPE`).
- Reset literally everything empties the totals but keeps the IDs (`Counts.clear`), so totals already sent can still be deleted.

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

**Open legal points (GDPR).** No lawyer will look (hobby project); these are kept as notes:
1. Whether the envelope's kinds (K2–K6) are described specifically enough that new counts inside them need no new consent.
2. Whether one toggle for swipe examples and usage counts is granular enough, or two boxes on one screen would be safer. The owner chose one switch (one switch in Settings could still turn both off).
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

**Open points (Play):**
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

Each is held by a check in `tools/checks/sharing.test.ts` or `tools/checks/connected.test.ts`:
- Nothing is counted while Sharing is off, and nothing is sent until the user agreed to the current envelope version. Debug never sends.
- Counts never come from the Accessibility service and never include a word.
- The counts request carries the counts ID only, never the contributor ID, and goes separately from swipe batches.
- The sharing files stay in `noBackupFilesDir`.
- The "What's shared" page lists exactly the counts the code sends (`lib/sharing-counts.ts`), so a count can't ship unlisted.
- `docs/analytics.md` §3, the "What's shared" page and the policy change together.

## 7. Privacy policy changes (applied to `site/privacy/index.html`)

These are in the policy now. Its daily totals section says "TitanKeys 3.0 and later", so it stays true for 2.1 until 3.0 is out.

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

1. **The names:** "Sharing" for the page, "Share to improve TitanKeys" for the switch, "Turn on sharing" on the screen. F will word
   them.
2. **Two IDs:** swipes keep their stable contributor ID, which is needed for deletion after an uninstall. Counts get the separate
   30-day ID, so the two can't be joined.
3. **Totals go on any network.** Swipes stay on Wi-Fi only.
4. **Speed and length bands** as listed. They can change inside the envelope later.
5. **The Android version** is now among the facts (K7). Swipes don't send it.
6. **The counts that changed from the draft** (§3): `join.keptApart` for `join.byHand`, `capital.*` added, `bar.space/tap`
   dropped, `pick.menu` for Shift and hold, gestures' "undone" not counted.
7. **Settings as "set"**, not "changed from default": a setting the user changed and then set back to its default still shows,
   until Reset most things.
8. **Earlier sharers** wait for a new yes before totals go (§2, "Agreeing").
