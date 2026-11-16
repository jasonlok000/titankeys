# Words for M1

Every user-facing line M1 needs, by issue. Use them exactly as written. Each table gives the place (a constant, a key or the
screen) and the line. Where a line replaces one in the code today, the old line is shown so it's easy to find.

Rules these follow: CONTRIBUTING's Writing section. Setup parts keep their limits (title one sentence of 32 characters or less,
ending in a full stop; body 72 or less; no numbers but the phone's own "Sliding Mode 1"). The Accessibility disclosure, the
contacts step and anything about sending data are clean: plain, no swearing.

## #66: the cursor slide, and "steer" out of the guide

The slide's line keeps its first letter H, for GO TOUCH GRASS NOW. **The target word is "cursor"**: the cursor starts at the end
of the line, after "follows", and "cursor" is the word right before it (6 letters, doesn't hold the cursor, one word left).

| Place | Today | New |
| --- | --- | --- |
| `STEER_LINE` | Hold the second tap to steer the cursor | Hold the second tap and the cursor follows |
| `steer` slide title | Steer to the underlined word. | Move the cursor to the underlined word. |
| `GuideChapter.Steering` label | Steering | Cursor |
| Space blocked, cursor slide (GuideSteps.kt, `GuideLesson.Space`) | Typed spaces are blocked. Taps on Space steer. | Typed spaces are blocked. Taps on Space move the cursor. |
| Swipe blocked, cursor chapter (`GuideLesson.Swipe`) | Swiping's blocked here. Taps on Space steer. | Swiping's blocked here. Taps on Space move the cursor. |
| `GuideScript.TEXT` (if it still shows anywhere) | …Flick to delete, steer and pick from Space, and… | …Flick to delete, move the cursor from Space, and… |

The slide's stages, done line, praise, help and hints don't say steer, so they stay as they are ("Tap Space twice, and hold the
second tap.", "Now slide. Lift when the cursor's in it.", "Cursor moved. No screen harmed.", "Past it. Slide back a little.").
The chapter's roast ("Wow, you moved a cursor…") stays too.

**Outside the guide, if you want "steer" gone everywhere** (optional; Settings > Haptics in lib/haptics.ts, and the Haptics lab):

| Place | New |
| --- | --- |
| `steer` label | Cursor starts moving |
| `steer` about | When you hold the second tap on Space and the cursor starts to follow your finger. |
| `steer_resumed` label | Cursor picks up after a slip |
| `steer_resumed` about | When your finger slips off and lands again in time, and the cursor keeps following. |
| `nothing_to_pick` about | When a double or triple tap finds no word, or there's no text to move through. Nothing happens, and this says why. |
| `cursor_letter` about | Each letter the cursor passes while you move it. |
| `cursor_space` about | Each space or line end the cursor passes while you move it, so you can count words. |
| HapticTimeline `Haptic.Steer` | Cursor started moving |
| HapticTimeline `Haptic.SteerResumed` | Cursor picked up after a slip |
| HapticsLabActivity practice field | Type, swipe, move the cursor, open panels and menus here. |

## #67: the word menu's keys are I/K, O/L and P/Backspace

| Place | Today | New |
| --- | --- | --- |
| Menu slide, stage 2 | Press I to save it. | Press I/K to save it. |
| Menu slide, stage 3 | Press I again to favourite it. | Press I/K again to favourite it. |
| Menu slide, praise | O steps it back down. P closes the menu. | O/L steps it back down. P/Backspace closes the menu. |
| Chip after Block | Blocked TitanKeys? Rude. I brings it back. | Blocked TitanKeys? Rude. I/K brings it back. |
| Chip after Unblock | Unblocked. Now I saves it. | Unblocked. Now I/K saves it. |
| `MENU_UNSAVED` | Unsaved. Cold. I, then I again. | Unsaved. Cold. I/K, then I/K again. |
| `MENU_MISSED` | I or O. Nothing else does anything here. | I/K or O/L. Nothing else does anything here. |

`MENU_CLOSED`, `MENU_NOT_BOX` and the slide's other blocked lines don't name the keys, so they stay.

**Boring guide, "The word menu" topic** (app/settings/guide-boring.tsx), whole text:

> Hold a word on the bar for its menu. I/K moves the word up a step and O/L moves it down: Blocked, Not saved, Saved, Favourite. Saved means TitanKeys knows the word: it's in the dictionary, you typed it twice, or you saved it. Unsave takes a word out without blocking it, so typing it twice brings it back. Block keeps it out until you unblock it. Rest a finger on I/K or O/L to see what the press will do, then press. P/Backspace closes the menu.

**Settings > Haptics** (lib/haptics.ts), the `about` lines that name keys:

| id | New about |
| --- | --- |
| `menu_press` | A light tick the moment you press I/K or O/L in the word menu, before the step's own haptic plays with its effect. |
| `menu_closed` | When you press P/Backspace or Back and the word menu closes without a pick. |
| `word_saved` | When you save a word from the word menu, with I/K or the Save button. Two light taps settle into a firm one: kept. |
| `word_blocked` | When you block a word from the word menu, with O/L on a word that isn't saved. A firm hit that trails off: gone. |
| `word_favourited` | When you make a saved word a favourite, with I/K or the Favourite button. Save's taps, ending on a stronger hit. |
| `word_unfavourited` | When a word stops being a favourite, with O/L or the Unfavourite button. A light fall: one step down. |
| `word_unsaved` | When you take a saved word out of your words, with O/L or the Unsave button. A medium fall: two steps down. |
| `word_unblocked` | When you take a word off Blocked words from the word menu, with I/K. A light rise: one step up. |
| `menu_spent` | When you press I/K on a word that's already a favourite. Nothing happens, so it says no. |

## #72: Sym and fn in the guide's setup

A `SetupPart`, last, after Back. Limits checked: title 18 characters, body 68, no numbers.

| Place | Line |
| --- | --- |
| Title | Set up Sym and fn. |
| Body | Set both to Programmable key. Sym opens the panel, fn works as Ctrl. |
| Action (button) | Open Shortcut keys |
| Help (back without both set) | Sym key and fn key are both on that page. Pick Programmable key for each. |
| Skip button | Not now |

**The "Not now" warning**, shown when it's skipped:

| Place | Line |
| --- | --- |
| Title | Skip Sym and fn? |
| Body | Then Sym won't open its panel, and that's where symbols, emoji and the clipboard live. And fn won't work as Ctrl, so fn + V won't paste. You can set them later in Settings > Setup. |
| Keep going without them | Skip anyway |
| Go back to the part | Set them up |

**On a slide that needs them, when they were skipped** (one line, no button):

| Slide | Line |
| --- | --- |
| Sym, emoji or clipboard slides | Sym isn't set up, so skip this one. |
| Paste slide (needs fn) | fn isn't set up, so skip this one. |

## #75: phones other than the Titan 2 Elite

**Once, on first launch**, one button:

| Place | Line |
| --- | --- |
| Title | That's not a Titan 2 Elite. |
| Body | TitanKeys is made for the Titan 2 Elite's touch keys, and this phone doesn't have them. Swiping, Space taps and flicks won't work here, and the guide will ask for things you can't do. Typing with the keys should be fine. Carry on at your own risk. |
| Button | Carry on anyway |

**At the top of Settings > Setup**, on those phones:

> This isn't a Titan 2 Elite, so swiping, Space taps and flicks won't work here.

## #79: the short Accessibility disclosure

Clean zone: plain, no jokes, no "Trust me bro." Same layout as Share swipes: title, short lines, the pill, "Not now" under it.

| Place | Line |
| --- | --- |
| Title | Let TitanKeys handle Back? |
| Intro | TitanKeys uses Android's Accessibility service for three things. It's optional. |
| Line: Back | Back: one press closes the keyboard and goes back. |
| Line: keys | Keys: at a few moments, it sees which keys you press before the app does. |
| Line: touchpad | Touchpad: it keeps the touchpad on the keys when a notification pops up. |
| Line: never | It can't read your screen, and it doesn't keep or send anything it sees. |
| Line: if no | Say no and TitanKeys still types and swipes. Back may take two presses. You can turn it on later in Settings > Setup. |
| Pill | Agree |
| Under the pill | Not now |
| Detail toggle | What exactly does it see? |

**The detail**, opened in place under the toggle:

> It sees which keys you press only at these moments:
> - while the Sym panel is open, so Enter and Backspace turn its pages instead of reaching the app;
> - in Smart Launcher's search field, where Smart Launcher would take your keys first, so TitanKeys types there as in any other app;
> - for a moment after you press Back, so your next press of Back reaches the app.
>
> The keys are used on your phone straight away, then forgotten. It never sees the text in your apps.

**`back_service_description`** (Android's own Accessibility page), shorter:

> Makes one press of Back close the keyboard and go back, and keeps the touchpad on the keys when a notification shows. At a few moments, such as while the Sym panel is open, it sees the keys you press. It can't read your screen, and it doesn't keep or send anything.

## #86: the Bar text size page's sample line

It ends mid-word, so the bar fills with "squint", "squinting" and friends:

> Pick a size and type here. Stop squin

## #87: contact names in Quick setup

The last part of Quick setup, optional. Limits checked: title 30 characters, body 61. Clean zone.

| Place | Line |
| --- | --- |
| Title | Teach it your contacts' names. |
| Body | Names swipe and get fixed right. They never leave your phone. |
| Yes (then Android asks) | Use my contacts |
| Not now | Not now |
| Help, if Android said no | Android said no. You can turn it on later in Settings > Setup. |

**Settings > Setup:**

| Place | Line |
| --- | --- |
| Section heading | Optional |
| Row | Names from my contacts |
| Row's value | On, or Off |

## #74: feedback by email

The address everywhere: titankeys@everyones-niche.com. No GitHub row (owner: "no need git").

**Settings > Send feedback:**

| Place | Line |
| --- | --- |
| Title | Send feedback |
| Subtitle | Opens your mail app. A real person reads it. |
| Row | Report a bug |
| Row | Suggest an idea |

**The mails:**

| Mail | Subject | Body |
| --- | --- | --- |
| Report a bug | TitanKeys bug, {version} | What happened?<br><br><br>What should have happened?<br><br><br>TitanKeys version: {version}<br>Phone: {phone}<br>Android: {android} |
| Suggest an idea | TitanKeys idea | What should TitanKeys do?<br><br> |
| Record a bug, Email it | TitanKeys bug recording, {version} | What went wrong:<br><br><br>TitanKeys version: {version}<br>Phone: {phone}<br>Android: {android} |
| Crash report | TitanKeys crash, {version} | What were you doing when it crashed?<br><br><br>The report is attached. It has the error and the versions, nothing you typed. |
| Freeze report | TitanKeys freeze, {version} | What were you doing when it froze?<br><br><br>The report is attached. It has the error and the versions, nothing you typed. |

**Record a bug** (lib/bug-text.ts): `share` "Share it" becomes two buttons:

| Place | Line |
| --- | --- |
| Main button | Email it |
| Second button | Share elsewhere |
| Recording over 10 MB, while zipping | Zipping it so it fits in an email. |

**No mail app** (Send feedback, or Email it):

| Place | Line |
| --- | --- |
| Note | No mail app here. Send it to titankeys@everyones-niche.com from wherever you do email. |
| Button | Copy address |
| After copying | Copied. |
| Extra line for a recording | Or press Share elsewhere to send the file another way. |

**The one-time crash offer**, on the next open. Plain, as it's about sending data:

| Place | Line |
| --- | --- |
| Title (crash) | TitanKeys crashed last time. |
| Title (freeze) | TitanKeys froze last time. |
| Body | Sorry about that. Email us the report so we can fix it? It has the error and the versions, nothing you typed. It's only sent if you send it. |
| Button | Email it |
| Second button | No thanks |

## Owner's notes from the phone, 01:52

### Every slide shows only its own line

I read all 15 lines on their own. One leans on the slides before it:

| Place | Today | New |
| --- | --- | --- |
| `SYM_LINE` | Sym has the rest, terms & conditions apply | Sym has all the symbols, terms & conditions apply |

It keeps its first letter S and its & gap. The rest stand alone as they are, including the triple tap slide's
`GRAB_LINE` ("Grab my whole heart with three taps", whose words to grab are in the line itself) and `HOLD_SYMBOL_LINE` ("Are you
even reading this?", a jab that works without "Read the fine print" above it). The cursor slide's new line is in #66 above, and its
target is inside it.

### The ∞ slide names both ways

Replaced by the ∞ slide in "Every page says where you are" below: it names swiping up on the keys and Enter, not Enter alone.

### Swipes on a panel happen on the keys

Places I found that tell people to swipe on the Sym, emoji or clipboard pages, and the line for each. Quick settings and the
clown, paste and clipboard slides don't mention swiping, so they stay.

| Place | Today | New |
| --- | --- | --- |
| GuideSteps.kt, Sym praise (`used and 4 != 0`) | The bottom row works too. Up or down turns pages. | The bottom row works too. So does swiping up or down on the keys. |
| GuideSteps.kt, Sym praise (`else`) | Up or down turns pages. The bottom row, groups. | Swipe up or down on the keys for pages. The bottom row, groups. |
| GuideSteps.kt, Sym praise (`used and 2 == 0`) | Backspace and Enter turn pages too. | (stays) |
| Boring guide, "The Sym panel" | …For more, swipe up or down on the keys, or press Backspace or Enter. Swipe left or right to change group, or put your finger on the bottom row and slide to one. | …For more, swipe up or down on the keys, or press Backspace or Enter. Swipe left or right on the keys to change group, or put your finger on the bottom row and slide to one. |
| Boring guide, "Emoji" | …Swipe up or down for more, and left or right to change group… | …Swipe up or down on the keys for more, and left or right to change group… |
| Boring guide, "The clipboard" (add after "a few to a page.") | | Swipe up or down on the keys for more, and left or right for Pinned. |
| strings.xml, `clipboard_description` | …Swipe up or down for more, and left or right for Pinned… | …Swipe up or down on the keys for more, and left or right for Pinned… |
| SymPanel.kt, the panel's spoken description | …Swipe left or right to change the group, or put a finger on the bottom row of keys. | …Swipe left or right on the keyboard to change the group, or put a finger on the bottom row of keys. |

The boring guide's "Move the cursor" topic says "Slide left or right" after two taps on Space. That slide is on the keys already,
since the finger stays on Space, so it stays.

## Every page says where you are

Owner: name both ways to the next page, and give instructions for each page, so nobody gets lost. One line per page state, for
every guide slide that opens a panel. The slide shows the line for the state it's in, in place of its stage line.

How the panels work, which these lines follow (SymPanel.kt, Modifiers.panelScroll):
- Symbols and emoji: swipe up on the keys or press Enter for the next page; swipe down on the keys or press Backspace for the one
  before. Swipe left or right on the keys, or slide along the bottom row, to change group.
- Clipboard: swipe up or down on the keys for more clips, left or right for Pinned. Enter pins and Backspace clears there, so they
  don't turn pages.
- Sym from the symbols opens the emoji; from the emoji or the clipboard it closes the panel. Alt goes back to the symbols from the
  clipboard and quick settings. Picking a symbol or emoji types it and closes the panel.

### Add the & (`symmain`)

The & is the first symbol of the first group, on its first page.

| State | Line |
| --- | --- |
| Panel closed | Press Sym. |
| Symbols, first group, first page | It's on this page. Find & and press its key. |
| Symbols, first group, a later page | Too far. Swipe down on the keys or press Backspace. |
| Symbols, another group | Wrong group. The & leads the first one. Swipe left or right on the keys, or slide along the bottom row. |
| Emoji | Emoji already? Patience. Sym again closes them. |
| Clipboard or quick settings | Wrong page. Alt goes back to the symbols. |
| Leaving without picking | Sym twice closes it. |

### Answer honestly, the ∞ hunt (`symgroups`)

The ∞ is on the first group's second page. These replace the stage line, the hunt's hint and the praise.

| Place or state | Line |
| --- | --- |
| Stage 1 (panel closed) | Press Sym, then swipe up on the keys or press Enter for the next page. |
| Symbols, first group, first page | Not on this page. Swipe up on the keys or press Enter. |
| Symbols, first group, second page | It's on this page. Find ∞ and press its key. |
| Symbols, first group, a later page | Too far. Swipe down on the keys or press Backspace. |
| Symbols, another group | Wrong group. The ∞ is in the first one. Swipe left or right on the keys, or slide along the bottom row. |
| Emoji | Not emoji. Sym closes them. Then press Sym once. |
| Clipboard or quick settings | Wrong page. Alt goes back to the symbols. |
| Leaving without picking | Sym twice closes it. |
| `WEIRD_HUNT` hint | It's not on the first page. Swipe up on the keys, or press Enter. |
| `WEIRD_HUNT` skip | Page two, near √ and ≈. Or skip it. It's ok. (stays) |
| Praise | Swipe up or down on the keys for pages, or press Enter or Backspace. The bottom row, groups. |

### Sign it with 🤡, the clown hunt (`clown`)

The 🤡 is in the Smileys group (the skip line says near 💩). Recent comes first once you've used some emoji.

| State | Line |
| --- | --- |
| Panel closed | Press Sym twice for the emoji. |
| Symbols | That's symbols. Press Sym again for the emoji. |
| Emoji, Recent | That's Recent. Swipe left or right on the keys, or slide along the bottom row, to Smileys. |
| Emoji, Smileys, before the clown's page | Not on this page. Swipe up on the keys or press Enter. |
| Emoji, Smileys, the clown's page | It's on this page. Find 🤡 and press its key. |
| Emoji, Smileys, past the clown's page | Too far. Swipe down on the keys or press Backspace. |
| Emoji, another group | Wrong group. The clown's in Smileys. Swipe left or right on the keys, or slide along the bottom row. |
| Clipboard or quick settings | Wrong page. Alt goes back to the symbols, then Sym for the emoji. |
| Leaving without picking | Sym closes the emoji. |

### Paste the one nice clip (`clipboard`)

| State | Line |
| --- | --- |
| Panel closed | Press Sym. |
| Symbols | Press A for the clipboard. |
| Emoji | Emoji? Sure, A works from there too. (stays) |
| Clipboard, the nice clip's page | It's on this page. Rest on each clip to read it, then press the key under the nice one. |
| Clipboard, another page of clips | Not on this page. Swipe up or down on the keys for the others. |
| Clipboard, Pinned | That's Pinned. Swipe left or right on the keys to get back to your clips. |
| Clipboard, any page (a reminder, once) | Enter pins and Backspace clears here, so swipe on the keys to turn pages. |
| Quick settings | Wrong page. Alt goes back, then press A. |
| Leaving without pasting | Sym closes the clipboard. Alt goes back to the symbols. |

### Paste over the orange words (`paste`)

This slide pastes with fn and V and needs no panel. If one opens anyway:

| State | Line |
| --- | --- |
| Symbols | No panel needed here. Sym twice closes it. Then hold fn and press V. |
| Emoji or clipboard | No panel needed here. Sym closes it. Then hold fn and press V. |
| Quick settings | No panel needed here. Alt, then Sym twice, closes it. Then hold fn and press V. |

## #97: the report form

Replaces the `F:` drafts in app/settings/report.tsx, lib/report.ts, AppNativeAdapters.kt (the crash offer), ConnectedAdapter.kt
(the email fallback), app/settings/feedback.tsx and lib/bug-text.ts. Lines marked "(stays)" are the draft's, kept.

**The form** (app/settings/report.tsx):

| Place | Line |
| --- | --- |
| Title | Report a bug |
| Title, from Suggest an idea | Suggest an idea |
| Subtitle | It comes straight to us, sealed so only we can read it. Nothing goes until you press Send. |
| Section | What's it about? (stays) |
| Field, a bug | What happened? (stays) |
| Its placeholder | What you did, and what went wrong (stays) |
| Field, an idea | Your idea (stays) |
| Its placeholder | What should TitanKeys do? (stays) |
| Field | What did you expect? (stays) |
| Its placeholder (new) | What should have happened |
| Field | Your email, if you want a reply (stays) |
| Its placeholder | Optional (stays) |
| Email doesn't look like one | That email doesn't look right. (stays) |
| Section | Sent with it (stays) |
| The versions line | TitanKeys {version}, {phone}, Android {android} (stays) |
| The recording's switch | The recording (stays) |
| Its note, from Record a bug | From Record a bug. It has your touches and keys, so it can show what you typed while it ran. |
| Its note, otherwise | Your last Record a bug. It has your touches and keys, so it can show what you typed while it ran. |
| Crash note | The crash report: where it crashed, and the versions. Nothing you typed. (stays) |
| Freeze note | The freeze report: where it froze, and the versions. Nothing you typed. (stays) |
| Button | Send (stays) |
| Button, while sending | Sending… (stays) |

**The kinds** (lib/report.ts), in order:

| id | Label |
| --- | --- |
| `word` | Wrong word or fix |
| `swiping` | Swiping |
| `touchpad` | Touchpad and Space taps |
| `keyboard` | Keyboard didn't show, or vanished |
| `crash` | Crash or freeze |
| `guide` | The guide |
| `settings` | Settings |
| `idea` | An idea |
| `other` | Something else |

**After Send:**

| Place | Line |
| --- | --- |
| Sent: title | Sent |
| Sent: subtitle | Thanks. It's with us now, and only we can read it. (stays) |
| Sent: button | Done |
| Not sent: title | Couldn't send it (stays) |
| Not sent, too big | It's too big to send from here. It's kept on your phone, so you can email it instead. (stays) |
| Not sent, no connection or busy | No connection, or our server's busy. It's kept on your phone. Try again later, or email it instead. |
| Button | Email it instead (stays) |
| Button, when not sent for the connection | Try again (stays) |
| No mail app | No mail app here. Send it to titankeys@everyones-niche.com from wherever you do email. (stays, from #74) |
| Button | Copy address, then Copied. (stays, from #74) |

**The email fallback** (ConnectedAdapter.kt, `reportEmail`): the subjects stay as drafted ("TitanKeys idea", "TitanKeys crash,
{version}", "TitanKeys bug, {version}"). Body:

> Your report from TitanKeys is attached. Anything to add? Write it here.

**The crash offer** (AppNativeAdapters.kt):

| Place | Line |
| --- | --- |
| Title, crash | TitanKeys crashed last time. (stays) |
| Title, freeze | TitanKeys froze last time. (stays) |
| Body | Sorry about that. Send us the report so we can fix it? It has the error and the versions, nothing you typed. Nothing goes unless you send it. |
| Button | Send it (stays) |
| Second button | No thanks (stays) |

**Send feedback** (app/settings/feedback.tsx):

| Place | Line |
| --- | --- |
| Subtitle | Goes straight to us from TitanKeys. A real person reads it. (stays; replaces #74's "Opens your mail app" subtitle above) |
| Debug-only row | Crash TitanKeys (stays) |

**Record a bug** (lib/bug-text.ts):

| Place | Line |
| --- | --- |
| Fourth how-to line | Check what's in it, then send it to us or share it wherever you like. (stays) |
| Warning, last line (clean zone) | TitanKeys only sends it if you press Send in the report form, sealed so only we can read it. Otherwise it stays on your phone. |
| Main button | Send it (stays; it opens the form, so it replaces #74's "Email it") |
| Second button | Share elsewhere (stays) |

## #65: backup and restore

Replaces the `F:` drafts in app/settings/data/*.tsx, BackupActivity.kt and BackupFile.kt. Plain, as it's about data. The
owner's two Import words lines (`NOT_A_BACKUP`, `DAMAGED`) and row names ("Back up everything", "Restore") stay.

| Place | Line |
| --- | --- |
| Behind the file picker, backing up | Backing up… (stays) |
| Behind the file picker, restoring | Restoring… (stays) |
| No file picker | Couldn't open the file picker. (stays) |
| No file picked | Couldn't open that file. (stays) |
| Saving failed | Couldn't save the backup. Try again. (stays) |
| `TOO_NEW` | This backup comes from a newer TitanKeys. Update this one, then restore it. |
| Backed up: title | Backed up (stays) |
| Backed up: subtitle | Saved. Keep it somewhere safe. It holds your words, so only share it with people you'd show them to. |
| Couldn't back up: title | Couldn't back up (stays) |
| Couldn't restore: title | Couldn't restore (stays) |
| Their button | Try again (stays) |
| Restore confirm: title | Restore from a backup? (stays) |
| Restore confirm: body | Your settings change to the backup's. Words, snippets, shorthand and app lists from it join yours, and nothing here is deleted. TitanKeys restarts when it's done. (stays) |
| Restore confirm: buttons | Pick the file, and Never mind (stays) |
| Restored: title | Restored (stays) |
| Restored: subtitle | Your settings, words and lists from the backup are back. Anything here that wasn't in it is still here. |
| Snippets imported: title | Snippets imported (stays) |
| Snippets imported: subtitle | The snippets are in your list now. (stays) |

## The privacy policy and README, for #97, #65 and #74

Every fact stays as drafted, and these stay plain. I checked each draft, and only the README's Bug reports line changes:

| Place | Line |
| --- | --- |
| Privacy "In short", the count | TitanKeys uses the internet for three things only. Each is strictly limited. (stays) |
| Privacy "In short", Bug reports | **Bug reports:** only when you press Send in the report form, it sends what you wrote, sealed so only we can read it. (stays) |
| Privacy "What stays on your phone", the crash report and backup items | (stay as drafted) |
| Privacy, the report form section | (stays as drafted) |
| README, the count | TitanKeys uses the internet for three things only, and each is strictly limited: (stays) |
| README, Bug reports | **Bug reports, only when you press Send:** **Settings > Send feedback**, **Record a bug** and the crash offer open a form. It sends what you wrote, the versions and your phone's model, and a recording or crash report if one is attached, encrypted on your phone so only we can read it. If it can't be sent, you can email it instead. |

The README line adds "and the crash offer", since the privacy policy says the crash offer opens the same form.

Outside the drafts: the privacy policy's permissions table says "the keys that you push", and CONTRIBUTING says "press".
Suggested: "To send Back, and to read the keys that you press while the Sym panel is open or in a launcher search. It doesn't
read the screen."

## #75 and #86, against the drafts in the code

**#75, the other-phone screen** (app/index.tsx). It's a full screen with big text, so the body is shorter than my first one
above. These replace both.

| Place | Line |
| --- | --- |
| Title | This isn't a Titan 2 Elite |
| Body | TitanKeys is made for its touch keys, so swiping, Space taps and flicks won't work here. Typing on the keys should. Carry on at your own risk. |
| Button | Carry on anyway |
| Setup line (`otherPhoneNote`) | This isn't a Titan 2 Elite, so swiping, Space taps and flicks won't work here. |

The draft's "Everything else does" is left out: it promises more than we've checked on other phones.

**#86, the sample line** (`barTextSizeSample`): use the one above, "Pick a size and type here. Stop squin", in place of the draft.

## #82: CONTRIBUTING's tools line

> The maintainer's own build and phone tools are in [tools/dev](tools/dev/README.md). You don't need them to contribute. (stays)
