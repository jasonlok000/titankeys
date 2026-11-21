# TitanKeys on Google Play: summary

For #36. Research and drafts from 2026-11-10, checked against Google's current help pages (sources in each file), and updated
on 2026-11-21 for 3.0.0. Nothing has been posted or submitted. This is not legal advice.

## The short answers

- **Can a Reddit post recruit the testers?** Yes. Google suggests community posts. Testers join through a Google Group and an
  opt-in link.
- **More than 12?** Yes, with no upper limit in a Google Group. Aim for **20 to 30**, because only testers who stay in for 14
  days in a row count.
- **Is the closed test needed at all?** Only for personal accounts made **after 13 November 2023**. Google: "Developers with
  personal accounts created after November 13, 2023, must run a closed test"
  ([source](https://support.google.com/googleplay/android-developer/answer/14151465)). An older account skips the 12 testers
  and 14 days, and the page doesn't say which side 13 November itself falls on. **Confirm on the Play Console dashboard:** an
  account that must test shows the closed-test steps and "Apply for production" there.
- **Can GitHub users move to Play and keep their words?** Yes, if Play signs with the GitHub key. Set this **before the first
  upload** (`signing.md`).
- **Version codes for 3.0.0:** GitHub **59** (`ink.toml`), Play **60**. `tools/play.sh` checks that `ink.toml`'s code is odd and
  builds with the next one (`signing.md`).

## What is ready

| File | What it holds |
|---|---|
| `data-safety.md` | Data safety answers for 3.0, item by item: Sharing with daily totals, the report form, the news, and the Support page's board |
| `products.md` | The Support page's 7 products (4 one-time, 3 monthly), Play Console setup, what the support Worker needs from Play, and Play's rules for tips |
| `permissions.md` | Every permission in 3.0.0's merged manifest, with one reason each |
| `accessibility.md` | Play declaration answers, the in-app disclosure text (No thanks / Agree), and the video shot list |
| `rating-and-audience.md` | Content rating answers, target audience (18+), the other App content answers, and reviewer instructions |
| `listing.md` | App name, short and full description, category, tags, and the graphics list with what each shows |
| `testers.md` | The closed-test rules, a step-by-step plan, draft answers for the production application, a draft r/unihertz post (not posted), and tester instructions |
| `signing.md` | Owner steps: PEPK export, upload key, version codes, warnings |
| `build-tasks.md` | The App Bundle from Ink (`--bundle`), the 16 KB check, the Play build script and its checks |
| `asset-packs.md` | A measured prototype that moves the big data into install-time asset packs, for when the bundle's size matters |

In the code, for the Play build: `tools/play.sh` (version code, upload key, the `play` build mark that turns the typing log off),
`tools/ink-bundle.patch` (`ink build --bundle`), `tools/dev/build/play-bundle.sh` (builds it in WSL), `tools/page-size.sh`
(the 16 KB check). ONNX Runtime is already 1.23.2.

**Still written for 2.1, to refresh before filling in the console:** `listing.md` (3.0's languages, Chinese and the Support page),
`testers.md` (the 2.1 announcement wording) and `accessibility.md` (check 3.0's uses of the service).

## What only the owner can do

1. **Make the developer account:** personal, US$25 once, 18 or over.
   - Verify your legal name and address (from Google Payments, maybe with ID and a card), your email and phone (one-time
     codes), and an Android device through the Play Console app.
   - **Play shows your legal name, your country and your developer email in public.** Your address is shown too once you sell
     anything, and the Support page sells coffees. So it will show.
   - If you already have an account made before 13 November 2023, use it: it may skip the closed test (above).
2. **Link a payments profile**, so the coffees can be sold (`products.md`).
3. **Set the app signing key** with PEPK, and make the upload key (`signing.md`). Only you handle the passwords.
4. **Create the 7 products** in Play Console, and set up the support Worker's service account and secrets (`products.md`).
   Decide on the monthly products: Play wants subscriptions to give something every month (`products.md`, risk).
5. **Film two videos:** the accessibility declaration video (`accessibility.md`; a screen recording is fine) and the trailer
   (#57) for the listing.
6. **Recruit the testers and look after them** for 14 days or more (`testers.md`), unless the account is exempt.
7. **Answer the production application** at the end of the test, if there is one.
8. **Decide the open questions** on #36.

## Order of steps to launch

| Step | Who | Time |
|---|---|---|
| 1. Pre-release 3.0.0 and daily-drive it 3 days (the pre-release rule) | owner | 3 days |
| 2. Update the privacy policy at `jasonlok000.github.io/titankeys/privacy/` to match `data-safety.md` (the board's wording, the support Worker's rate counter) | agents, owner approves | with 3.0 |
| 3. Make the Play developer account (or use an older one), get verified, link a payments profile | owner | a few days (not verified: Google gives no fixed time) |
| 4. Create the app and **set the signing key before any upload** | owner | 30 minutes |
| 5. Make the upload key; build the bundle with `tools/dev/build/play-bundle.sh` (version code 60) and upload it to **internal testing**. Billing products can only be made once a build with the Billing Library is uploaded. | owner and build agent | an hour |
| 6. Create the 7 products; deploy the support Worker with the service account; buy each product once as a license tester | owner | an evening |
| 7. Fill in the store listing and all of App content: privacy policy, ads, app access, content rating (it asks about in-app purchases), target audience, Data safety, and the accessibility declaration with its video | owner, from these drafts | an evening |
| 8. Check that the internal build installs over GitHub 3.0.0 and keeps saved words | owner or orchestrator | an hour |
| 9. If the account must test: make the Google Group, set up the closed test, post on r/unihertz, in the README and as a GitHub-build announcement; aim for 20 to 30 testers | owner | day 1 |
| 10. Run the test: weekly notes, and at least one update from the feedback | owner and agents | 14 days or more |
| 11. Apply for production (skipped by an exempt account) | owner | review "usually seven days or less" |
| 12. Publish to production with a staged rollout, and announce it to GitHub users (#42) | owner | |

Realistic total from making the account: **about 4 to 6 weeks** with the closed test, most of it the 14-day test and two
reviews. About 1 to 2 weeks for an account that is exempt.

## Not verified, or uncertain

- The exact wording of the production questions, the content rating questions and the accessibility declaration form. They show
  only inside Play Console; the drafts follow Google's help pages.
- Which side of the closed-test rule an account made on 13 November 2023 falls on.
- Whether Play accepts monthly tips whose only monthly value is a place on the board (`products.md`).
- r/unihertz's rules on self-promotion (Reddit can't be read from here).
- The Google Groups join setting and the exact opt-in link.
- How Play's new "hybrid signing" (for Android 17) works with a key that you provide. It doesn't affect the Titan 2 Elite on
  Android 15 or 16.
- Whether the random IDs, the board's name and the phone model belong in the categories chosen in `data-safety.md`. The choices
  there are the cautious ones.
- Whether an IP address that reaches GitHub Pages or Cloudflare, and is never stored or used beyond a rate counter, needs
  declaring. The answer assumes that the Workers never log it.
- Whether Google's reviewers can judge an app that needs a Titan 2 Elite. The app access instructions and a real video are the
  mitigation.
- The bundle's size: 3.0.0's APK is about 278 MB. Google's limit for the base module is 500 MB (`asset-packs.md`), so it should
  pass without asset packs, but a smaller first download helps on mobile data.

## Also good to know

- **Developer verification (2026–2027):** Android will require apps on certified phones to come from verified developers, also
  when installed from GitHub. Enforcement started on 30 September 2026 in four countries and goes worldwide in 2027. A Play
  account with Play App Signing registers TitanKeys automatically, and with the same key the GitHub APK is covered too
  (https://developer.android.com/developer-verification/guides/faq).
- **Key upgrades:** Google will prompt for signing key upgrades. Accepting one would stop GitHub APKs from updating Play installs
  (`signing.md`).
- **Still open from #36:** the source of the word list `english.combined` (licence credit).
