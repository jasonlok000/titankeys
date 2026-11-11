import { Row, Screen, Section, replace, useRouteParams } from "ink";
import { arrowOutward, close } from "ink/icons";
import { openGuide } from "../../lib/atlas";
import { Topic } from "../../lib/topic";

export default function Guide() {
  // Opened from the front page, the guide closes back to it, like Settings does.
  const fromHome = useRouteParams().from === "home";
  const closeAction = fromHome ? { icon: close, onPress: () => replace("/") } : undefined;
  return <Screen title="Guide" subtitle="How everything works." rightAction={closeAction}>
    <Section title="Try it">
      <Row title="Interactive guide" subtitle="Try every feature on the keys." endIcon={arrowOutward} onPress={() => void openGuide()} />
    </Section>
    <Section title="Swiping">
      <Topic title="Swipe a word">Slide your finger across the letters without pressing. Lift to type the word.</Topic>
      <Topic title="Keep swiping after a short lift">If your finger leaves the keys for a moment, the swipe carries on. Set how long in Settings.</Topic>
      <Topic title="Pick another word">Touch a word on the strip above the keys.</Topic>
      <Topic title="Turn off swipe typing">Turn off Swipe to type words in Settings or the quick settings. The touchpad stops typing words, but light-touch keys and the panel still work.</Topic>
      <Topic title="Scroll assistant outside text fields">That's the phone's Scroll assistant: outside text fields, the touchpad scrolls the app. Turn it off in Settings or the quick settings and the touchpad only works in text fields. The touchscreen doesn't change. With it off, the shortcuts on the letter keys and the phone's space bar shortcut stop working, and in some apps Back takes two presses: the first lets go of the touchpad, the second goes back.</Topic>
      <Topic title="Move the cursor">Touch the space bar twice, quickly, and keep your finger down after the second touch. Slide up onto the letters, where the touchpad reads you better, then move left or right. The cursor goes slowly near the middle and fast near the edges, and stops in the middle. Each time it passes a space, you feel a stronger buzz.</Topic>
      <Topic title="Select words">To select the word at the cursor, touch the space bar twice, quickly, lifting after each touch. To select more, touch it three times and keep your finger down after the third. Slide up, then move left or right. Lean back the other way to give the words back, one at a time.</Topic>
      <Topic title="Where the touchpad reads">The touchpad reaches the middle of the outer keys on each side, and the middle of the top and bottom rows. Outside that, it can't read your finger well.</Topic>
    </Section>
    <Section title="Keys">
      <Topic title="Type as on any keyboard">Press the keys like on any keyboard. When you press the space bar, TitanKeys fixes typos in the word. Don't want that? Turn off Fix typos as you type in Settings.</Topic>
      <Topic title="Type with a light touch">Touch these keys lightly to type them. You don't have to press them. Pick the keys in Settings.</Topic>
      <Topic title="Hold a letter for its symbol">Hold a letter to type the symbol printed on its key. Set how long in Settings.</Topic>
      <Topic title="Full stop">Press the space bar twice.</Topic>
      <Topic title="Shift and Alt">Press once for the next letter only. Two quick presses do the same. To cancel, press again after a pause. To lock them with two quick presses, turn on Press twice to lock Shift and Alt in Settings.</Topic>
      <Topic title="Word suggestions">The strip shows words and spelling fixes as you type. Touch one to type it.</Topic>
      <Topic title="Snippets">{"Type a shortcut like ;addr and press Space. It turns into your address, or whatever you saved for it. Press Backspace right after to get the shortcut back. Set them up in Settings > Snippets."}</Topic>
      <Topic title="Emoji by name">Type :smile: and you get 😄. After a colon and two letters, the strip shows emoji that match. Backspace right after brings the name back.</Topic>
      <Topic title="Exact typing">{"In terminals, remote desktops and game streaming, like Termux, AnyDesk and Moonlight, every key goes straight to the app. TitanKeys doesn't fix or suggest anything there, and the touchpad does nothing. The bar still shows Shift, Alt and Ctrl. Add or remove apps in Settings > Exact typing."}</Topic>
      <Topic title="Hide the bar in an app">{"Don't want the bar in some app? Settings > Hide the bar in these apps. The keys and the Sym panel still work there."}</Topic>
      <Topic title="Password, email and number fields">These fields get no words, and swipes do nothing there. The touchpad still moves the cursor, and the bar still shows Shift, Alt and Ctrl.</Topic>
      <Topic title="Delete a word">Press Alt, then Backspace. Ctrl and Backspace works too, or flick left from Backspace.</Topic>
      <Topic title="Delete many words">Flick left from Backspace and keep your finger down. TitanKeys keeps deleting, word after word, and a longer flick goes faster. Lift your finger or press a key to stop.</Topic>
      <Topic title="More symbols">Press Sym to open the panel. Each letter key shows a symbol, except Q and A, which are empty, and P, which shows the page. Rest your finger on a key to mark it (P doesn't get a mark), then press it to type the symbol and close the panel. For more, swipe up or down on the keys, or press Backspace or Enter. The marks along the bottom show the groups of symbols. To change group, swipe left or right, or put your finger on the bottom row of keys.</Topic>
      <Topic title="Emoji">Press Sym again for emoji. Once you've used some, Recent comes first. Press a key to type its emoji. Q and A are empty, and P shows the page. For more, swipe up or down on the keys, or press Backspace or Enter. Swipe left or right to change group. The marks along the bottom show each group: touch one to open it, or put your finger on the bottom row of keys and hold it there or slide to the group. Space works too. Press Sym again to close the panel.</Topic>
      <Topic title="Quick settings">With the panel open, press Alt for the quick settings. Rest your finger on a key to see its setting at the bottom of the panel, and whether it's on. Press the key to switch it. The page stays open, and a dim key means the setting is off. O opens all settings, and K lists your keyboards so you can pick one. Press Alt to go back, or Sym to close the panel.</Topic>
      <Topic title="Modifier bar">When Shift, Caps or Alt is on, a small label shows next to your suggestions. Filled means you're holding the key, an outline means you pressed it once, and Caps means Shift is locked. Rest your finger on a key to see what it types, and the bar shows that instead of the words. Ctrl shows its shortcuts while you hold it.</Topic>
      <Topic title="Capital at the start">At the start of a sentence, Shift turns on by itself, so the first letter's a capital. Want it lowercase? Press Shift once to turn it off.</Topic>
      <Topic title="Orange accents">A few small marks on the keyboard are orange, like the key under your finger. Prefer white? Turn off Orange accents in Settings.</Topic>
      <Topic title="Ctrl shortcuts">Hold Ctrl and the bar above the keys shows what each shortcut does.</Topic>
      <Topic title="Use a shortcut">Hold Ctrl and press the key.</Topic>
      <Topic title="Use fn as Ctrl">fn works as Ctrl. Want fn for the phone's own shortcuts? Turn off Use fn as Ctrl in Settings.</Topic>
    </Section>
    <Section title="Your words">
      <Topic title="New words">TitanKeys learns the new words you use. To see, add or block words, go to Settings &gt; Your words.</Topic>
      <Topic title="Backup">To keep a copy of your words, go to Your words &gt; Export words. Import words brings them back.</Topic>
    </Section>
    <Section title="Problems">
      <Topic title="Check the setup">Something fucked up? Nine times out of ten it's Setup. Settings &gt; Setup, make every step say Done.</Topic>
      <Topic title="Sym doesn't open the panel">In the phone's Settings, open Shortcut keys and set Sym key to Programmable key.</Topic>
      <Topic title="fn doesn't work as Ctrl">In the phone's Settings, open Shortcut keys and set fn key to Programmable key.</Topic>
      <Topic title="Swiping doesn't work in an app">Open Scroll assistant and find the app. It should say Sliding Mode 1.</Topic>
    </Section>
  </Screen>;
}
