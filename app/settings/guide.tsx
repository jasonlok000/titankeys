import { Row, Screen, Section, replace, useRouteParams } from "ink";
import { arrowOutward, close } from "ink/icons";
import { openGuide } from "../../lib/atlas";
import { Topic } from "../../lib/topic";

export default function Guide() {
  // Opened from the front page, the guide closes back to it, like Settings does.
  const fromHome = useRouteParams().from === "home";
  const closeAction = fromHome ? { icon: close, onPress: () => replace("/") } : undefined;
  return <Screen title="Guide" subtitle="How to use TitanKeys." rightAction={closeAction}>
    <Section title="Try it">
      <Row title="Interactive guide" subtitle="Try each feature on the keys." endIcon={arrowOutward} onPress={() => void openGuide()} />
    </Section>
    <Section title="Swiping">
      <Topic title="Swipe a word">Move your finger across the letters. Do not push the keys. Lift your finger to type the word.</Topic>
      <Topic title="Keep swiping after a short lift">If your finger leaves the keys for a short time, the swipe continues. Change this time in Settings.</Topic>
      <Topic title="Pick another word">Touch a word on the strip above the keyboard.</Topic>
      <Topic title="Turn off swipe typing">Turn off Swipe to type words in Settings or in the quick settings. The touchpad then types no words. Light-touch keys and the panel continue to operate.</Topic>
      <Topic title="Scroll assistant outside text fields">This is the phone's Scroll assistant: when no text field is active, the touchpad scrolls the app. Turn it off in Settings or in the quick settings to stop that. The touchpad then works only in text fields, and the touchscreen does not change. When it is off, the shortcuts on the letter keys and the space bar shortcut from the phone Settings do not operate. In some apps, Back then takes two pushes: the first lets go of the touchpad, and the second goes back.</Topic>
      <Topic title="Move the cursor">Touch the space bar two times quickly. Do not lift your finger after the second touch. Swipe up onto the letters, where the touchpad reads your finger better. Then move your finger to the left or right. The cursor moves slowly near the middle and quickly near the edge of the keyboard. In the middle, the cursor stops. Each time that the cursor goes past a space, you feel a stronger vibration.</Topic>
      <Topic title="Select words">To select the word at the cursor, touch the space bar two times quickly. Lift your finger after each touch. To select more words, touch the space bar three times quickly. Do not lift your finger after the third touch. Swipe up, then move to the left or right. Lean the other way to give the words back, one at a time.</Topic>
      <Topic title="Where the touchpad reads">The touchpad stops at the middle of the keys at each side, and at the middle of the top and bottom rows. A finger outside this area is not read well.</Topic>
    </Section>
    <Section title="Keys">
      <Topic title="Type as on any keyboard">Push the keys as on any keyboard. When you push the space bar, TitanKeys corrects typos in the word. To stop this, turn off Fix typos as you type in Settings.</Topic>
      <Topic title="Type with a light touch">Touch these keys lightly to type them. You do not have to push them. Select the keys in Settings.</Topic>
      <Topic title="Hold a letter for its symbol">Hold a letter to type the symbol printed on its key. Select the time in Settings.</Topic>
      <Topic title="Full stop">Push the space bar two times.</Topic>
      <Topic title="Shift and Alt">Push one time for the next letter only. Two quick pushes do the same. To cancel, push again after a longer time. To lock with two quick pushes, open Settings and turn on Double push locks Shift and Alt.</Topic>
      <Topic title="Word suggestions">The strip shows words and spelling corrections as you type. Touch a word to type it.</Topic>
      <Topic title="Snippets">Type a shortcut, such as ;addr, and push the space bar. The shortcut changes to its text. To get the shortcut back, push Backspace. Add snippets in Settings.</Topic>
      <Topic title="Emoji by name">Type a colon, the name of an emoji and a colon, such as :smile:. The name changes to its emoji. The strip shows emoji as you type the name. To get the name back, push Backspace.</Topic>
      <Topic title="Exact typing">In terminals, remote computers and game streaming, such as Termux, AnyDesk and Moonlight, each key goes to the app as you push it. TitanKeys does not fix words or show suggestions there, and the bar shows Shift, Alt and Ctrl. To add or remove an app, open Settings and select Exact typing. The touchpad sends nothing to these apps.</Topic>
      <Topic title="Hide the bar in an app">To hide the bar above the keys in an app, open Settings and select Hide the bar in these apps. The keys and the Sym panel still operate there.</Topic>
      <Topic title="Password, email and number fields">These fields show no words. Swipes type nothing there, but the touchpad moves the cursor, and the bar shows Shift, Alt and Ctrl.</Topic>
      <Topic title="Delete a word">Push Alt, then push Backspace. You can also use Ctrl and Backspace, or flick left from Backspace.</Topic>
      <Topic title="Delete many words">Flick left from Backspace. Do not lift your finger. TitanKeys deletes more words, one after the other. A longer flick deletes the words faster. To stop, lift your finger or push a key.</Topic>
      <Topic title="More symbols">Push Sym to open the panel. Each letter key shows a symbol, but Q and A are empty and P shows the page. Rest your finger on a key to mark it. P does not show a mark. Push the key to type the symbol. The panel then closes. To see more, swipe up or down on the keyboard, or push Backspace or Enter. The marks at the bottom of the panel show the groups of symbols. To change the group, swipe left or right, or put your finger on the bottom row of keys.</Topic>
      <Topic title="Emoji">Push Sym again to show emoji. The first group is Recent, after you use an emoji. Push a key to type its emoji. Q and A are empty. P shows the page and does not type an emoji. To see more, swipe up or down on the keyboard, or push Backspace or Enter. To change the group, swipe left or right. The marks at the bottom of the panel show each group. Touch a mark to open its group, or put your finger on the bottom row of keys. Hold it there, or move it to the group. You can also push Space. Push Sym again to close the panel.</Topic>
      <Topic title="Quick settings">When the panel is open, push Alt to see the quick settings. Rest your finger on a key to see its setting at the bottom of the panel, and if the setting is on or off. Push the key to change the setting. The page stays open. A dim key shows a setting that is off. Push O to open all settings. Push K to show a list of your keyboards. Then select a keyboard. Push Alt again to go back, or push Sym to close the panel.</Topic>
      <Topic title="Modifier bar">When Shift, Caps, Alt, or Ctrl is on, the bar above the keyboard shows this in place of the words. A filled label shows a key that you hold. An outline shows a key that you pushed one time. Caps shows that Shift is locked. Rest your finger on a key to see what the key types.</Topic>
      <Topic title="Orange accents">Some small marks on the keyboard are orange, such as the key under your finger. To make them white, turn off Orange accents in Settings.</Topic>
      <Topic title="Ctrl shortcuts">Hold Ctrl. The bar above the keyboard shows what each shortcut does.</Topic>
      <Topic title="Use a shortcut">Hold Ctrl and push the key.</Topic>
      <Topic title="Use fn as Ctrl">fn works as Ctrl. To use fn for the phone's own shortcuts, turn off Use fn as Ctrl in Settings.</Topic>
    </Section>
    <Section title="Your words">
      <Topic title="New words">TitanKeys learns the new words that you use. To see, add or block words, open Settings and select Your words.</Topic>
      <Topic title="Backup">To keep a copy of your words, open Your words and select Export words. To add the words again, select Import words.</Topic>
    </Section>
    <Section title="Problems">
      <Topic title="Check the setup">If a part of TitanKeys does not operate correctly, open Settings and select Setup. Make sure that all steps show Done.</Topic>
      <Topic title="Sym does not open the panel">In the phone Settings, open Shortcut keys. Set Sym key to Programmable key.</Topic>
      <Topic title="fn does not operate as Ctrl">In the phone Settings, open Shortcut keys. Set fn key to Programmable key.</Topic>
      <Topic title="Swiping does not operate in an app">Open Scroll assistant and find the app. Make sure that it shows Sliding Mode 1.</Topic>
    </Section>
  </Screen>;
}
