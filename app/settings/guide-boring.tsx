import { useEffect, useState } from "react";
import { Screen, Section, Stack, Text, useSnapshot } from "ink";
import { markNerdSeen, state } from "../../lib/atlas";
import { nerdSplash } from "../../lib/nerd";
import { Topic } from "../../lib/topic";

/**
 * The boring guide: every feature and setting, explained in words. The fun guide teaches the main gestures on the keys and ends by
 * pointing here, so anything that it leaves out belongs here.
 */
export default function BoringGuide() {
  // The first time only, briefly, like a loading screen: we know who reads the manual. It waits for the app's state, so it knows
  // whether it has shown before, and its time starts once it's on the screen.
  const result = useSnapshot(state);
  const ready = result.status === "ready";
  const [phase, setPhase] = useState<"waiting" | "splash" | "done">("waiting");
  useEffect(() => {
    if (!ready || phase !== "waiting") return;
    setPhase(nerdSplash(result.data.nerdSeen) ? "splash" : "done");
  }, [ready]);
  useEffect(() => {
    if (phase !== "splash") return;
    void markNerdSeen();
    const timer = setTimeout(() => setPhase("done"), SPLASH_MILLIS);
    return () => clearTimeout(timer);
  }, [phase]);
  if (phase === "waiting") return <Screen title="The boring guide" />;
  // A splash, big and centred, so the whole line fits: no page title to cut it short.
  if (phase === "splash") return <Screen centered theme="onboarding">
    <Stack align="center" gap={14}>
      <Text size={SPLASH_SIZE} align="center">{"We know you're a nerd."}</Text>
      <Text size={SPLASH_SMALL} align="center">{"It's fine. Somebody has to read the manual."}</Text>
    </Stack>
  </Screen>;
  // The snippet start is the user's own: / unless they picked another.
  const settings = result.status === "ready" ? result.data : undefined;
  const start = settings?.snippetStart ?? "/";
  const startKey = settings ? settings.altKeys[start] : "G";
  const typeStart = !startKey ? `${start} is on the Sym panel.`
    : settings?.holdSymbols === false ? `${start} is Alt and ${startKey}.` : `${start} is Alt and ${startKey}, or hold ${startKey}.`;
  return <Screen title="The boring guide" subtitle="Every feature and every setting, in words. Enjoy, nerd.">
    <Section title="Start here">
      <Topic title="The fun guide">{"The fun guide teaches the main stuff right on the keys, a chapter at a time. Open it from Settings > Guide. This one is for everything else."}</Topic>
      <Topic title="The front page">{"Open TitanKeys and you land on a place to type and swipe whatever you like. The line above the field suggests words if you want something to follow. Type fast and it shows your words a minute, and the screen warms up to orange. Guide and Settings are at the top."}</Topic>
    </Section>
    <Section title="Typing">
      <Topic title="Type like on any keyboard">{"Press the keys. Nothing to unlearn. When you press Space, TitanKeys fixes typos in the word you just typed. Don't want that? Turn off Fix typos as you type in Settings."}</Topic>
      <Topic title="The local LLM">{"Swipes, suggestions and typo fixes get help from a small LLM, DistilGPT-2, that runs right on your phone. It looks at the words before the cursor to guess what you mean. What you type never goes online and never trains any model. The one exception is Share swipes, which is off until you agree: then examples of your swipes come to us to train the swipe models."}</Topic>
      <Topic title="Capital at the start">{"At the start of a sentence, Shift turns on by itself, so the first letter's a capital. Your words stay on the bar, right where they were, and a small A in its corner shows that Shift is on. Want it lowercase? Press Shift once to turn it off. Rather do it all yourself? Turn off Capital letter to start a sentence in Settings."}</Topic>
      <Topic title="Names keep their capitals">{"Names and words like iPhone keep their own capitals, swiped or typed."}</Topic>
      <Topic title="Full stop">{"Press Space twice for a full stop and a space. Turn it off with Double Space types a full stop in Settings."}</Topic>
      <Topic title="Space after punctuation">{"Type a full stop and go straight on to the next word. The space goes in when you type its first letter. Same after , ; and :. Numbers, links and ... stay as you type them. Press Backspace right after to take the space back out. Pick one space, two or none in Settings > Space after a sentence, and turn off Space after , ; : there too."}</Topic>
      <Topic title="Shift and Alt">{"Press once for the next letter only. To cancel, press again after a pause. Want two quick presses to lock them? Turn on Press twice to lock Shift and Alt in Settings. When Shift is locked, the bar says Caps."}</Topic>
      <Topic title="Hold a letter for its symbol">{"Hold a letter and it turns into the symbol printed in its corner. Pick how long you hold in Settings > Hold a letter for its symbol, or turn it off there."}</Topic>
      <Topic title="English spelling">{"Colour or color? Pick British, American or Canadian spelling for suggestions and typo fixes in Settings > English spelling."}</Topic>
      <Topic title="Password, email and number fields">{"These fields get no suggestions and no fixes, and swipes don't type there. The touchpad still moves the cursor, and the bar still shows Shift, Alt and Ctrl."}</Topic>
    </Section>
    <Section title="Touchpad">
      <Topic title="Swipe a word">{"Slide your finger across the letters of a word without pressing. Lift to type it, with a space after. Swipe the next one straight away."}</Topic>
      <Topic title="Wrong word?">{"Touch the right one on the bar. The bar always has the next best guesses, so one touch fixes a swipe that missed."}</Topic>
      <Topic title="Keep swiping after a short lift">{"Fingers slip. If yours leaves the keys for a moment, the swipe carries on. Set how long a moment is in Settings > Keep swiping after a short lift."}</Topic>
      <Topic title="Type with a light touch">{"Some keys type when you barely touch them, so you don't have to press them down. Pick which ones in Settings > Type with a light touch. Press a letter or Space there to add it."}</Topic>
      <Topic title="The touch zone">{"The touch zone is the part of the keys the touchpad can read: up to the middle of the outer keys on each side, and the middle of the top and bottom rows. Outside it, the touchpad can't read your finger well. The interactive guide dims everything outside the touch zone."}</Topic>
      <Topic title="Turn off swipe typing">{"Turn off Swipe to type words in Settings or in the quick settings. The touchpad stops typing words. Light-touch keys and the Sym panel still work."}</Topic>
      <Topic title="Scroll assistant">{"The phone hijacks the touchpad with its own Scroll assistant: when you're not typing, the keys scroll the app. TitanKeys' creator can't stand it, so TitanKeys can block it. Turn it off with Scroll assistant in Settings or in the quick settings, and the touchpad only works in text fields. Until you touch that switch, it copies the phone's own setting. In a text field, the touchpad is all TitanKeys either way. Blocking it has a cost: the shortcuts on the letter keys and the phone's shortcut on Space stop working, and in some apps Back takes two presses. The first lets go of the touchpad, the second goes back."}</Topic>
    </Section>
    <Section title="The bar">
      <Topic title="Word suggestions">{"The bar above the keys shows words and spelling fixes as you type. Touch one to type it. The best guess is always in the middle, and the three places never move. A long word shrinks to fit its place. If it still doesn't fit, it shows the part where it differs from the others, like …mendations. Don't want the bar at all? Turn off Show the bar in Settings."}</Topic>
      <Topic title="The word menu">{"Hold a word on the bar for its menu. I moves the word up: Save, then Favourite. O moves it back down, one step at a time. A word TitanKeys only learned from your typing gets Forget on O, and then it stops showing up. Forgot one by mistake? Undo shows right after. P or Backspace closes the menu. Rest a finger on a key to see its button, then press. The menu waits until you pick."}</Topic>
      <Topic title="A selected word">{"Pick a word in your text and the bar shows it alone in the middle. Touch it for the same menu."}</Topic>
      <Topic title="Your newest copy">{"Right after you copy something, the bar offers it. Touch it to paste. It goes away once you type on. Older copies are in the clipboard: press Sym, then A."}</Topic>
      <Topic title="Shift, Alt and Ctrl">{"When you press or hold Shift, Alt or Ctrl, the bar shows it in place of your words. Filled means you're holding the key, an outline means you pressed it once, and Caps means Shift is locked. Rest your finger on a key to see what it types. The only exception is the capital at the start of a sentence: that one keeps your words."}</Topic>
      <Topic title="Ctrl shortcuts">{"Hold Ctrl and the bar shows what each shortcut does. Press a key while you hold Ctrl to use it."}</Topic>
      <Topic title="Hide the bar">{"Don't want the bar in some app? Pick the apps in Settings > Hide the bar in these apps. To hide it everywhere, turn off Show the bar in Settings. Either way, the keys, holds, Alt and the Sym panel still work."}</Topic>
    </Section>
    <Section title="Deleting">
      <Topic title="Delete a word">{"Flick left, fast, from Backspace or the keys next to it. The interactive guide shades where to aim. Or press Backspace while you hold Shift. Keep Backspace down to go on, word by word. A Shift you only tapped, or one that came on by itself, leaves Backspace as it is. Ctrl and Backspace deletes a word too, as on any Android keyboard, and fn works as Ctrl. Alt and Backspace deletes forward instead, the letter after the cursor, like Delete on a PC, and holding it keeps going. Turn off Alt + Backspace deletes forward in Settings to get Android's own, which deletes the whole line."}</Topic>
      <Topic title="Delete lots of words">{"Flick and keep your finger down. TitanKeys keeps deleting, word after word. Slide further out to go faster, or back onto Backspace to pause. Lift your finger or press a key to stop."}</Topic>
    </Section>
    <Section title="Cursor and picking text">
      <Topic title="Space gestures: tap a bit higher">{"The double and triple taps on Space work best a little higher up. The touch zone stops around the middle of Space, so a tap low on Space can go unfelt. Tap the top half of Space, or the keys right above it, from C to N. Anywhere as wide as Space, on those two rows, counts."}</Topic>
      <Topic title="Move the cursor">{"Tap Space twice, quickly, and keep your finger down after the second tap. Slide left or right. The cursor goes slowly near the middle and fast near the edges, and stops when you come back to the middle. You feel a stronger buzz each time it passes a space. Turn it off with Space taps move and select in Settings."}</Topic>
      <Topic title="Pick a word">{"Tap Space twice, lifting after each tap. That picks the word at the cursor."}</Topic>
      <Topic title="Pick more words">{"Tap Space three times to pick the word at the cursor. Keep your finger down after the third tap and slide to take more words. Lean back the other way to give them back, one at a time. Space taps move and select in Settings turns these taps off, with the cursor ones."}</Topic>
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
      <Topic title="Snippets">{`Type a shortcut like ${start}addr and press Space. It turns into your address, or whatever you saved for it. Press Backspace right after to get the shortcut back. ${typeStart} Make snippets in Settings > Snippets, where you can also pick another symbol for the start and back them up.`}</Topic>
      <Topic title="Next keyboard">{"Hold Alt and press Sym to switch to your next keyboard, as the globe key does on other phones. With the Sym panel open, Alt and Sym do their usual thing. The guide keeps its keyboard, so it does nothing there."}</Topic>
      <Topic title="Exact typing (experimental)">{"In terminals, remote desktops and game streaming, like Termux, AnyDesk and Moonlight, every key goes straight to the app, exactly as you press it. TitanKeys doesn't fix or suggest anything there, and the touchpad stays out of it. Add or remove apps in Settings > Exact typing (experimental)."}</Topic>
      <Topic title="Exact typing bar (experimental)">{"In those apps the bar shows Shift, Alt, Ctrl, Esc, Tab and More. Tap Shift, Alt or Ctrl to hold it for your next key, press and hold to lock it, and tap again to let go. They add to the keys you press: Shift on the keys and Esc on the bar sends Shift and Esc. More has the arrows, Home, End, PgUp, PgDn and F1 to F12. With Alt sends Alt on, Alt, Shift and Ctrl go to the app as real modifiers, and symbols come from Sym or from holding a key."}</Topic>
    </Section>
    <Section title="Your words">
      <Topic title="New words">{"TitanKeys learns the new words you type, so it suggests them next time. Turn that off with Learn new words I type in Settings > Your words. Typing in password fields and private fields teaches it nothing."}</Topic>
      <Topic title="Saved and blocked words">{"Saved words always show up. Blocked words never do. Both lists are in Settings > Your words, where you can add or remove words. The word menu on the bar fills them too."}</Topic>
      <Topic title="Names from your contacts">{"Turn on Names from my contacts in Settings > Your words, and the names on your phone swipe right, with their capitals. They stay on your phone."}</Topic>
      <Topic title="Backup">{"Your words > Export words saves a backup file. Import words brings it back, on this phone or a new one. Remove learned words wipes everything TitanKeys learned from you."}</Topic>
    </Section>
    <Section title="Look and feel">
      <Topic title="Feel the keys">{"A little buzz for each key your swipe crosses, so you can swipe without looking. Pick what buzzes, or turn it all off, in Settings > Haptics."}</Topic>
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
      <Topic title="Messed up your settings">{"Changed so much you can't find your way back? At the bottom of Settings: Reset most things puts every switch and choice back how it started, and your words, snippets, shorthand and app lists stay. Reset literally everything wipes all of that too, like a fresh install. Swipes you already shared stay on our side until you delete them in Settings > Share swipes."}</Topic>
      <Topic title="Sym doesn't open the panel">{"Settings > Setup checks it. Set Sym to Programmable key opens the phone's Shortcut keys: set Sym key to Programmable key there."}</Topic>
      <Topic title="fn doesn't work as Ctrl">{"Settings > Setup checks it. Set fn to Programmable key opens the phone's Shortcut keys: set fn key to Programmable key there."}</Topic>
      <Topic title="Swiping doesn't work in an app">{"Settings > Setup lists the apps that aren't on Sliding Mode 1. Open Scroll assistant, find the app and pick Sliding Mode 1."}</Topic>
      <Topic title="Back takes two presses">{"Turn TitanKeys on in Accessibility. Settings > Setup has the step."}</Topic>
    </Section>
  </Screen>;
}

/** How long the nerd screen shows before the boring guide. */
const SPLASH_MILLIS = 1800;
/** The splash's line and its smaller line, sized like the setup screen's. */
const SPLASH_SIZE = 56;
const SPLASH_SMALL = 28;
