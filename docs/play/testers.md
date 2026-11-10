# Testers: the closed test for a new personal account

For #36. Checked against Google's help pages on 2026-11-10. This is not legal advice.

## The answers to the owner's two questions

**Can a Reddit post recruit the testers?** Yes. Google tells new developers to recruit through "relevant communities and online groups" and social media. A post on r/unihertz fits that well, because every tester needs a Titan 2 Elite anyway. Testers join with a link, so nobody has to send you their email address by hand. ([source](https://support.google.com/googleplay/android-developer/answer/14151465))

**Can there be more than 12?** Yes. 12 is the minimum, not a limit. A Google Group has no size limit, and an email list holds up to 2,000 testers. **Aim for 20 to 30.** Only testers who stay in for 14 days in a row count, and some people always drop out. ([source](https://support.google.com/googleplay/android-developer/answer/9845334))

## The rules today

From [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465) and [Set up an open, closed, or internal test](https://support.google.com/googleplay/android-developer/answer/9845334):

| Rule | What Google says |
|---|---|
| Who it applies to | Personal accounts created after 13 November 2023. Production stays off until the test is done. |
| How many | At least 12 testers in a closed test. |
| How long | They must have been opted in "continuously for the preceding 14 days" when you apply. |
| Dropping out | A tester who opts out and back in starts again from day 0. |
| How testers join | By an email list (up to 200 lists per account, 50 per track, 2,000 testers each) or by a Google Group (no size limit). With a group, a tester joins the group first, then opts in. |
| The opt-in link | Shown only once the test is "Published" (after Google's review). The first link can take several hours to work. |
| Finding the app | Testers can't find a closed test by searching on Play. Share the link. |
| Accounts | Testers need a Google account. |
| Reviews | Testers can't post public reviews. They can send private feedback through Play. You should give them a feedback URL or email, which shows on the opt-in page. |
| Applying | Dashboard, then **Apply for production**, then three groups of questions (below). Review "usually takes seven days or less". |
| More testing | Google can ask you to keep testing, for example if fewer than 12 stayed in or if testers didn't use the app much. |

**The production questions** (Google gives the topics, not the exact words):

1. **About your closed test:** how easy it was to find testers, whether they used all the features, whether their use matched what you expect in production, and a summary of their feedback and how you collected it.
2. **About your app:** who it is for, how it gives users value, and how many installs you expect in the first year.
3. **About production readiness:** what you changed because of the test, and how you decided it is ready.

Google's tips for engagement: give clear instructions, say what feedback you want, ask testers to try as many features as they can, give a feedback channel, and tell them to stay in for 14 days.

**Before any of this:** a personal account also has to verify your identity and verify that you have an Android device, through the Play Console mobile app. The fee is US$25, one time. You must be 18 or over. ([source](https://support.google.com/googleplay/android-developer/answer/6112435))

## Step-by-step plan

**Week 0: before the test opens**

1. Make the developer account (personal), pay US$25, verify your ID, phone and email, and verify a device with the Play Console app. Your legal name, your country and your developer email show on Play ([source](https://support.google.com/googleplay/android-developer/answer/13628312)).
2. Create the app. **Set the app signing key before you upload anything** (see `signing.md`). If you skip this, testers who have the GitHub version must uninstall it and lose their saved words.
3. Fill in everything under **App content**: privacy policy URL, ads (no), app access, content rating, target audience, Data safety, and the accessibility declaration with its video. Fill in the store listing. A closed test still goes through Google's review, so it must follow the rules from day one.
4. Make a Google Group, for example "TitanKeys testers". In its settings, let anyone join. (**Not verified:** the exact setting names in Google Groups. Look for "Who can join group".)
5. In Play Console, go to **Test and release > Testing > Closed testing**, use the default closed track, add the Google Group as the testers, set the feedback channel (GitHub issues or an email), and make all countries available.
6. Upload the first App Bundle and send it for review. When the track says **Published**, copy the opt-in link and the Play link. The opt-in link usually looks like `https://play.google.com/apps/testing/com.titankeys` (**not verified**: copy the exact link from the console).
7. Install it yourself from the opt-in link on your own Titan 2 Elite, to check that the link and the update over the GitHub version work. Don't count your own account as one of the 12.

**Day 1: recruit**

8. Post on r/unihertz (draft below). Read the subreddit rules first. If they limit self-promotion, ask the moderators before you post. (**Not verified:** Reddit couldn't be read from here, so the rules are not checked.)
9. Use the other places that already reach Titan 2 Elite owners:
   - the GitHub README and the 2.1 release notes
   - a 2.1 announcement in the app itself, for the GitHub build only (#42): those users already have the phone
   - other Titan 2 Elite communities you know of, if their rules allow it

**Days 1 to 14: keep testers engaged**

10. Check the number of testers in Play Console every few days. If it is under 20, post a reminder.
11. Send one short message a week (in the Google Group or the Reddit thread): what to try this week, and a thank-you.
12. Ship at least one update during the test that fixes something a tester reported. It gives a real answer to "what did you change".
13. Keep notes for the application: how many joined, which features people used, what they reported and what you changed.

**Day 15 and after: apply**

14. When at least 12 testers have been in for 14 days in a row, apply for production. Draft answers are below.
15. Keep the closed test running while Google reviews the application.
16. When production is approved, publish with a staged rollout. Thank the testers. They can stay in the test or leave it. A tester who is on both tracks gets whichever has the higher version code.

## Draft answers for the production application

Fill in the numbers at the end of the test. Keep the answers true: Google can check.

- **How easy was it to recruit testers?** "TitanKeys only works on the Unihertz Titan 2 Elite, so testers had to own one. I posted on r/unihertz, in the GitHub README and as a notice in the GitHub version of the app. [N] people joined, and [M] stayed for the full 14 days."
- **Did testers use all the features?** "Most testers used it as their main keyboard every day. They typed, swiped, picked words from the strip, deleted with a flick, and used symbols, emoji and quick settings. Fewer used [feature]."
- **Did their use match what you expect in production?** "Yes. Production users are Titan 2 Elite owners who type on the physical keys, the same as the testers. One difference: [for example, testers tried more settings than an average user]."
- **Summary of feedback and how it was collected:** "Feedback came through GitHub issues, the Reddit thread and Play's private feedback. The main points were [A, B, C]."
- **Target audience:** "Adults who own a Unihertz Titan 2 Elite and type on its physical keyboard."
- **How does the app give value?** "The Titan 2 Elite's keys are also a touchpad. TitanKeys uses both: normal typing, swiping words on the keys, cursor control and every symbol from the keys, without touching the screen. No other keyboard is built for this phone."
- **Installs in the first year:** pick the lowest honest range. The phone has a small number of owners.
- **What did you change after the test?** "[Version] fixed [A] and [B], which testers reported. [Version] changed [C]."
- **How did you decide it is ready?** "Testers used it daily for 14 days with no crashes left open, the last reported bugs are fixed, and the same features have been used by GitHub users since [date]."

## Draft Reddit post for r/unihertz

Do not post this yet. Replace the links in square brackets first.

> **Title:** Looking for Titan 2 Elite owners to test TitanKeys on Google Play (free, 14 days)
>
> Hi all. I make TitanKeys, a free, open-source keyboard built only for the Titan 2 Elite. You type on the keys as usual, and since the keys are also a touchpad, you can swipe whole words on them too.
>
> Until now it's only been an APK on GitHub. I'd like to put it on Google Play, so it installs and updates like any other app. Google asks new developers to run a closed test first: at least 12 people have to stay in the test for 14 days in a row. I'm hoping for 20 or more, in case a few drop out.
>
> **You need:** a Titan 2 Elite, a Google account, and two weeks of using TitanKeys as your keyboard. A few minutes a day is plenty.
>
> **To join:**
> 1. Join the tester group: [Google Group link]
> 2. Open the test page and tap "Become a tester": [opt-in link]
> 3. Install TitanKeys from Play: [Play link]
> 4. Open it and follow the guide. Then please stay in the test for at least 14 days.
>
> **Already using TitanKeys from GitHub?** The Play version installs over it and keeps your saved words.
>
> **What helps most:** use it every day, try swiping, symbols and emoji, and tell me what works and what doesn't. Reply here, open a GitHub issue, or send private feedback in Play.
>
> **Privacy:** Google shows me the email address of the account you join with. I'll only use it for this test. Privacy policy: [policy link]
>
> TitanKeys is an independent project, not made by or affiliated with Unihertz. Mods, if this isn't OK here, tell me and I'll take it down. Thanks!

## Tester instructions (short)

For the Google Group welcome message, the GitHub README and the feedback page.

> **Thanks for testing TitanKeys!**
>
> 1. On your Titan 2 Elite, use the same Google account for the group, the test page and the Play Store.
> 2. Tap "Become a tester" on [opt-in link], then install from [Play link]. If you have TitanKeys from GitHub, Play updates it in place and keeps your words.
> 3. Open TitanKeys. The guide sets everything up and teaches the gestures in a few minutes.
> 4. Use it as your keyboard for at least 14 days. Please don't leave the test early: if you leave and rejoin, Google starts your 14 days again.
> 5. Try as much as you can: typing, swiping, picking words from the strip, flick to delete, steer and select from Space, symbols, emoji and quick settings.
> 6. Tell me what works and what doesn't: [GitHub issues link], [email], or Play Store > your profile > Manage apps & devices > Beta > TitanKeys > Write feedback.
>
> You can't post a public review during the test. That opens when TitanKeys is on Play for everyone.

## Open points

- Reddit's and r/unihertz's rules were not checked (Reddit can't be read from here).
- The Google Groups join setting and the exact opt-in link format were not checked.
- Whether the owner's own account counts towards the 12 is not stated by Google. Don't rely on it.
