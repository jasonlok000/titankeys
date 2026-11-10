# TitanKeys on Google Play: summary

For #36. Research and drafts from 2026-11-10, checked against Google's current help pages (sources in each file). Nothing has been built, posted or submitted. This is not legal advice.

## The short answers

- **Can a Reddit post recruit the testers?** Yes. Google suggests community posts. Testers join through a Google Group and an opt-in link.
- **More than 12?** Yes, with no upper limit in a Google Group. Aim for **20 to 30**, because only testers who stay in for 14 days in a row count.
- **Can GitHub users move to Play and keep their words?** Yes, if Play signs with the GitHub key. Set this **before the first upload** (`signing.md`).

## What is ready

| File | What it holds |
|---|---|
| `testers.md` | The closed-test rules, a step-by-step plan, draft answers for the production application, a draft r/unihertz post (not posted), and tester instructions |
| `listing.md` | App name, short and full description (3,240 of 4,000 characters), category, tags, and the graphics list with what each shows |
| `data-safety.md` | Data safety answers for 2.1, item by item |
| `accessibility.md` | Play declaration answers, the in-app disclosure text (No thanks / Agree), and the video shot list |
| `permissions.md` | One reason for each permission |
| `rating-and-audience.md` | Content rating answers, target audience (18+), the other App content answers, and reviewer instructions |
| `signing.md` | Owner steps: PEPK export, upload key, version codes, warnings |
| `build-tasks.md` | For the build agent: an App Bundle from Ink (`--bundle`), the 16 KB fix (ONNX Runtime 1.23.2 or later), the Play build script and its checks |

## What only the owner can do

1. **Make the developer account:** personal, US$25 once, 18 or over.
   - Verify your legal name and address (from Google Payments, maybe with ID and a card), your email and phone (one-time codes), and an Android device through the Play Console app.
   - **Play shows your legal name, your country and your developer email in public.** Your address is shown only if you ever charge money.
2. **Set the app signing key** with PEPK, and make the upload key (`signing.md`). Only you handle the passwords.
3. **Film two videos:** the accessibility declaration video (`accessibility.md`; a screen recording is fine) and the trailer (#57) for the listing.
4. **Recruit the testers and look after them** for 14 days or more (`testers.md`).
5. **Answer the production application** at the end of the test.
6. **Decide the open questions** on #36.

## Order of steps to launch

| Step | Who | Time |
|---|---|---|
| 1. Finish 2.1 on `2.1.0`, including the accessibility disclosure, the 16 KB fix and the App Bundle build (`build-tasks.md`) | agents | with 2.1 |
| 2. Publish the privacy policy at `jasonlok000.github.io/titankeys/privacy/` (#53) | agents, owner approves | with 2.1 |
| 3. Make the Play developer account and get verified | owner | a few days (not verified: Google gives no fixed time) |
| 4. Create the app and **set the signing key before any upload** | owner | 30 minutes |
| 5. Fill in the store listing and all of App content: privacy policy, ads, app access, content rating, target audience, Data safety, and the accessibility declaration with its video | owner, from these drafts | an evening |
| 6. Make the Google Group, set up the closed test, and upload the first App Bundle from `tools/play.sh` | owner and build agent | Google's review: often a few days (not verified) |
| 7. Check that the test build installs over GitHub 2.0.0 and keeps saved words | owner or orchestrator | an hour |
| 8. Post on r/unihertz, in the README and as a GitHub-build announcement; aim for 20 to 30 testers | owner | day 1 |
| 9. Run the test: weekly notes, and at least one update from the feedback | owner and agents | 14 days or more |
| 10. Apply for production | owner | review "usually seven days or less" |
| 11. Publish to production with a staged rollout, and announce it to GitHub users (#42) | owner | |

Realistic total from making the account: **about 4 to 6 weeks**, most of it the 14-day test and two reviews.

## Not verified, or uncertain

- The exact wording of the production questions, the content rating questions and the accessibility declaration form. They show only inside Play Console; the drafts follow Google's help pages.
- r/unihertz's rules on self-promotion (Reddit can't be read from here).
- The Google Groups join setting and the exact opt-in link.
- That ONNX Runtime 1.23.x ships a 16 KB-aligned JNI library. The fix is in its source; the binary needs the check in `build-tasks.md`.
- How Play's new "hybrid signing" (for Android 17) works with a key that you provide. It doesn't affect the Titan 2 Elite on Android 15 or 16.
- Whether the random contributor ID, the phone model and the kept word belong in the categories chosen in `data-safety.md`. The choices there are the cautious ones.
- Whether an IP address that reaches GitHub Pages or Cloudflare, and is never stored or used, needs declaring. The answer assumes that the Worker never logs it.
- Whether Play Console already blocks a new app without 16 KB support, or only from 1 February 2027. It is fixed before upload either way.
- Whether Google's reviewers can judge an app that needs a Titan 2 Elite. The app access instructions and a real video are the mitigation.

## Also good to know

- **Developer verification (2026–2027):** Android will require apps on certified phones to come from verified developers, also when installed from GitHub. Enforcement started on 30 September 2026 in four countries and goes worldwide in 2027. A Play account with Play App Signing registers TitanKeys automatically, and with the same key the GitHub APK is covered too (https://developer.android.com/developer-verification/guides/faq).
- **Key upgrades:** Google will prompt for signing key upgrades. Accepting one would stop GitHub APKs from updating Play installs (`signing.md`).
- **Still open from #36:** the source of the word list `english.combined` (licence credit).
