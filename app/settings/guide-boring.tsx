import { Screen, Section, replace, useRouteParams } from "ink";
import { close } from "ink/icons";
import { Topic } from "../../lib/topic";

/**
 * The boring guide: every feature and setting, explained in words. The fun guide teaches the main gestures on the keys and ends by
 * pointing here, so anything that it leaves out belongs here.
 */
export default function BoringGuide() {
  // Opened from the front page, the guide closes back to it, like Settings does.
  const fromHome = useRouteParams().from === "home";
  const closeAction = fromHome ? { icon: close, onPress: () => replace("/") } : undefined;
  return <Screen title="The boring guide" subtitle="Reading the manual? Nerd. Fine, here's everything TitanKeys does." rightAction={closeAction}>
    <Section title="Start here">
      <Topic title="The fun guide">{"The fun guide teaches the main stuff right on the keys, a chapter at a time. Open it from Settings > Guide. This one is for everything else."}</Topic>
      <Topic title="The front page">{"Open TitanKeys and you land on a place to type and swipe whatever you like. The line above the field suggests words if you want something to follow. Type fast and it shows your words a minute, and the screen warms up to orange. Guide and Settings are at the top."}</Topic>
    </Section>
    <Section title="Typing">
      <Topic title="Type like on any keyboard">{"Press the keys. Nothing to unlearn. When you press Space, TitanKeys fixes typos in the word you just typed. Don't want that? Turn off Fix typos as you type in Settings."}</Topic>
      <Topic title="Capital at the start">{"At the start of a sentence, Shift turns on by itself, so the first letter's a capital. Your words stay on the bar, right where they were, and a small A in its corner shows that Shift is on. Want it lowercase? Press Shift once to turn it off. Rather do it all yourself? Turn off Capital letter to start a sentence in Settings."}</Topic>
      <Topic title="Names keep their capitals">{"Names and words like iPhone keep their own capitals, swiped or typed."}</Topic>
      <Topic title="Full stop">{"Press Space twice for a full stop and a space. Turn it off with Full stop on double space in Settings."}</Topic>
      <Topic title="Shift and Alt">{"Press once for the next letter only. To cancel, press again after a pause. Want two quick presses to lock them? Turn on Press twice to lock Shift and Alt in Settings. When Shift is locked, the bar says Caps."}</Topic>
      <Topic title="Hold a letter for its symbol">{"Hold a letter and it turns into the symbol printed in its corner. Pick how long you hold in Settings > Hold a letter for its symbol, or turn it off there."}</Topic>
      <Topic title="Type with a light touch">{"Some keys type when you barely touch them, so you don't have to press them down. Pick which ones in Settings > Type with a light touch. Press a letter or Space there to add it."}</Topic>
      <Topic title="English spelling">{"Colour or color? Pick British or American spelling for suggestions and typo fixes in Settings > English spelling."}</Topic>
      <Topic title="Password, email and number fields">{"These fields get no suggestions and no fixes, and swipes don't type there. The touchpad still moves the cursor, and the bar still shows Shift, Alt and Ctrl."}</Topic>
    </Section>
    <Section title="Swiping">
      <Topic title="Swipe a word">{"Slide your finger across the letters of a word without pressing. Lift to type it, with a space after. Swipe the next one straight away."}</Topic>
      <Topic title="Wrong word?">{"Touch the right one on the bar. The bar always has the next best guesses, so one touch fixes a swipe that missed."}</Topic>
      <Topic title="Keep swiping after a short lift">{"Fingers slip. If yours leaves the keys for a moment, the swipe carries on. Set how long a moment is in Settings > Keep swiping."}</Topic>
      <Topic title="Feel the keys">{"A little buzz for each key your swipe crosses, so you can swipe without looking. Change it or turn it off in Settings > Vibration."}</Topic>
      <Topic title="Where the touchpad reads">{"The touchpad reaches the middle of the outer keys on each side, and the middle of the top and bottom rows. Outside that, it can't read your finger well. The interactive guide dims the part it can't read."}</Topic>
      <Topic title="Turn off swipe typing">{"Turn off Swipe to type words in Settings or in the quick settings. The touchpad stops typing words. Light-touch keys and the Sym panel still work."}</Topic>
      <Topic title="Scroll assistant outside text fields">{"That's the phone's Scroll assistant: outside text fields, the touchpad scrolls the app. Turn it off in Settings or the quick settings and the touchpad only works in text fields. The touchscreen doesn't change. With it off, the shortcuts on the letter keys and the phone's space bar shortcut stop working, and in some apps Back takes two presses: the first lets go of the touchpad, the second goes back."}</Topic>
    </Section>
    <Section title="The bar">
      <Topic title="Word suggestions">{"The bar above the keys shows words and spelling fixes as you type. Touch one to type it. The best guess is always in the middle, and the three places never move. A long word shrinks to fit its place. If it still doesn't fit, it shows the part where it differs from the others, like …mendations."}</Topic>
      <Topic title="The word menu">{"Hold a word on the bar for its menu. Save keeps the word for good, so it's always suggested. Forget makes it go away: it stops showing up, and a word TitanKeys learned from you is unlearned. Changed your mind? Undo shows right after. Back closes the menu. You can pick with the keys too: rest a finger on the top two rows to mark a button, then press. The menu waits until you pick."}</Topic>
      <Topic title="A selected word">{"Pick a word in your text and the bar shows it alone in the middle. Touch it for the same menu: Save or Forget."}</Topic>
      <Topic title="Your newest copy">{"Right after you copy something, the bar offers it. Touch it to paste. It goes away once you type on. Older copies are in the clipboard: press Sym, then A."}</Topic>
      <Topic title="Shift, Alt and Ctrl">{"When you press or hold Shift, Alt or Ctrl, the bar shows it in place of your words. Filled means you're holding the key, an outline means you pressed it once, and Caps means Shift is locked. Rest your finger on a key to see what it types. The only exception is the capital at the start of a sentence: that one keeps your words."}</Topic>
      <Topic title="Ctrl shortcuts">{"Hold Ctrl and the bar shows what each shortcut does. Press a key while you hold Ctrl to use it."}</Topic>
      <Topic title="Hide the bar in an app">{"Don't want the bar in some app? Settings > Hide the bar picks the apps, or hides it everywhere. The keys, holds, Alt and the Sym panel still work there."}</Topic>
    </Section>
    <Section title="Deleting">
      <Topic title="Delete a word">{"Flick left, fast, from Backspace or the keys next to it. The interactive guide shades where to aim. Press Alt, then Backspace works too, and so do Ctrl and Backspace."}</Topic>
      <Topic title="Delete lots of words">{"Flick and keep your finger down. TitanKeys keeps deleting, word after word, and a longer flick goes faster. Lift your finger or press a key to stop."}</Topic>
    </Section>
    <Section title="Cursor and picking text">
      <Topic title="Move the cursor">{"Tap twice, quickly, just above the middle of Space, and keep your finger down after the second tap. Slide left or right. The cursor goes slowly near the middle and fast near the edges, and stops when you come back to the middle. You feel a stronger buzz each time it passes a space. Turn it off with Space taps move the cursor in Settings."}</Topic>
      <Topic title="Pick a word">{"Tap twice just above Space, lifting after each tap. That picks the word at the cursor."}</Topic>
      <Topic title="Pick more words">{"Tap three times to pick the word at the cursor. Keep your finger down after the third tap and slide to take more words. Lean back the other way to give them back, one at a time."}</Topic>
    </Section>
    <Section title="Symbols and emoji">
      <Topic title="Alt symbols">{"Each letter has a symbol printed in its corner. Press Alt, then the letter, to type it. Or hold the letter, if that's on."}</Topic>
      <Topic title="The Sym panel">{"Press Sym for every other symbol. Each letter key shows one. Rest your finger on a key to mark it, then press it to type the symbol, and the panel closes. Q and A are empty, and P shows the page. For more, swipe up or down on the keys, or press Backspace or Enter. Swipe left or right to change group, or put your finger on the bottom row and slide to one."}</Topic>
      <Topic title="Emoji">{"Press Sym twice for emoji. Once you've used some, Recent comes first. Press a key to type its emoji. Swipe up or down for more, and left or right to change group. The marks along the bottom show the groups: touch one, or slide along the bottom row to it. Press Sym again to close the panel."}</Topic>
      <Topic title="Emoji by name">{"Type :smile: and you get 😄. After a colon and two letters, the bar shows emoji that match. Press Backspace right after to get the name back. Turn it off with Type :name: for an emoji in Settings."}</Topic>
      <Topic title="Quick settings">{"With the panel open, press Alt for the quick settings. Rest your finger on a key to see its setting and whether it's on. Press the key to switch it. A dim key means off. O opens all settings, and K lists your keyboards so you can pick another. Press Alt to go back, or Sym to close."}</Topic>
    </Section>
    <Section title="Copy and paste">
      <Topic title="Copy, cut and paste">{"Pick some text, then hold fn and press C to copy, X to cut, or V to paste. fn works as Ctrl, so every Ctrl shortcut works with it."}</Topic>
      <Topic title="Use fn as Ctrl">{"On by default. Want fn for the phone's own shortcuts instead? Turn off Use fn as Ctrl in Settings."}</Topic>
      <Topic title="The clipboard">{"Press Sym, then A. Your last copies are there, newest first, a few to a page. Press a key to paste its clip. Enter pins clips so they stay. Backspace clears the ones that aren't pinned. Alt goes back to the symbols."}</Topic>
    </Section>
    <Section title="Shortcuts">
      <Topic title="Snippets">{"Type a shortcut like ;addr and press Space. It turns into your address, or whatever you saved for it. Press Backspace right after to get the shortcut back. Make them in Settings > Snippets, where you can also change the ; at the start and back them up."}</Topic>
      <Topic title="The Enter key">{"In message apps, Enter can send, or add a new line. Pick which in Settings > Enter key, app by app. Shift and Enter does the other one. Search and other one-line fields stay as they are."}</Topic>
      <Topic title="Exact typing">{"In terminals, remote desktops and game streaming, like Termux, AnyDesk and Moonlight, every key goes straight to the app, exactly as you press it. TitanKeys doesn't fix or suggest anything there, and the touchpad stays out of it. The bar still shows Shift, Alt and Ctrl. Add or remove apps in Settings > Exact typing."}</Topic>
    </Section>
    <Section title="Your words">
      <Topic title="New words">{"TitanKeys learns the new words you type, so it suggests them next time. Turn that off with Learn new words I type in Settings > Your words. Typing in password fields and private fields teaches it nothing."}</Topic>
      <Topic title="Saved and blocked words">{"Saved words always show up. Blocked words never do. Both lists are in Settings > Your words, where you can add or remove words. The word menu on the bar fills them too."}</Topic>
      <Topic title="Names from your contacts">{"Turn on Names from my contacts in Settings > Your words, and the names on your phone swipe right, with their capitals. They stay on your phone."}</Topic>
      <Topic title="Backup">{"Your words > Export words saves a backup file. Import words brings it back, on this phone or a new one. Remove learned words wipes everything TitanKeys learned from you."}</Topic>
    </Section>
    <Section title="Look">
      <Topic title="Orange accents">{"A few small marks on the keyboard are orange, like the key under your finger. Prefer white? Turn off Orange accents in Settings."}</Topic>
    </Section>
    <Section title="News and sharing">
      <Topic title="News">{"TitanKeys can tell you about a new version and anything else worth knowing. It checks the TitanKeys website once a day and sends nothing about you. News shows as a silent notification, never while you type, with Snooze and Don't show again. Settings > News has the same news and a switch to turn it off."}</Topic>
      <Topic title="Share swipes">{"If you agree, TitanKeys sends us examples of your swipes, and we use them to make swiping better on every Titan 2 Elite. It's off until you agree, and you see exactly what's sent first. Swipes are encrypted on your phone and sent on Wi-Fi. You can stop and delete them any time in Settings > Share swipes. The interactive guide asks once, near its end."}</Topic>
      <Topic title="Record a bug">{"Got a bug that's hard to explain? Settings > Record a bug. Press Record, do the thing, come back and press Stop. You see what's in the file, then share it wherever you like. TitanKeys doesn't upload it."}</Topic>
      <Topic title="Send feedback">{"Settings > Send feedback. Tell us what broke, or what you'd love."}</Topic>
    </Section>
    <Section title="Problems">
      <Topic title="Check the setup">{"Something fucked up? Nine times out of ten it's Setup. Settings > Setup, make every step say Done."}</Topic>
      <Topic title="Sym doesn't open the panel">{"In the phone's Settings, open Shortcut keys and set Sym key to Programmable key."}</Topic>
      <Topic title="fn doesn't work as Ctrl">{"In the phone's Settings, open Shortcut keys and set fn key to Programmable key."}</Topic>
      <Topic title="Swiping doesn't work in an app">{"Open Scroll assistant and find the app. It should say Sliding Mode 1."}</Topic>
      <Topic title="Back takes two presses">{"Turn TitanKeys on in Accessibility. Settings > Setup has the step."}</Topic>
    </Section>
  </Screen>;
}
