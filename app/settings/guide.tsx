import { Row, Screen, Section, replace, useRouteParams } from "ink";
import { arrowOutward, close } from "ink/icons";
import { openGuide } from "../../lib/atlas";

export default function Guide() {
  // Opened from the front page, the guide closes back to it, like Settings does.
  const fromHome = useRouteParams().from === "home";
  const closeAction = fromHome ? { icon: close, onPress: () => replace("/") } : undefined;
  return <Screen title="Guide" subtitle="How to use TitanKeys." rightAction={closeAction}>
    <Section title="Try it">
      <Row title="Interactive guide" subtitle="Use each feature on the keyboard, one at a time." wrapSubtitle endIcon={arrowOutward} onPress={() => void openGuide()} />
    </Section>
    <Section title="Swiping">
      <Row title="Swipe a word" subtitle="Move your finger across the letters. Do not push the keys. Lift your finger to type the word." wrapSubtitle />
      <Row title="Keep swiping after a short lift" subtitle="If your finger leaves the keys for a short time, the swipe continues. Change this time in Settings." wrapSubtitle />
      <Row title="Pick another word" subtitle="Touch a word on the strip above the keyboard." wrapSubtitle />
      <Row title="Turn off swipe typing" subtitle="Turn off Swipe to type words in Settings or in the quick settings. The touchpad then types no words. Light-touch keys and the panel continue to operate." wrapSubtitle />
      <Row title="Scroll assistant outside text fields" subtitle="This is the phone's Scroll assistant: when no text field is active, the touchpad scrolls the app. Turn it off in Settings or in the quick settings to stop that. The touchpad then works only in text fields, and the touchscreen does not change. When it is off, the shortcuts on the letter keys and the space bar shortcut from the phone Settings do not operate. In some apps, Back then takes two pushes: the first lets go of the touchpad, and the second goes back." wrapSubtitle />
      <Row title="Move the cursor" subtitle="Touch the space bar two times quickly. Do not lift your finger after the second touch. Swipe up onto the letters, where the touchpad reads your finger better. Then move your finger to the left or right. The cursor moves slowly near the middle and quickly near the edge of the keyboard. In the middle, the cursor stops." wrapSubtitle />
      <Row title="Select words" subtitle="To select the word at the cursor, touch the space bar two times quickly. Lift your finger after each touch. To select more words, touch the space bar three times quickly. Do not lift your finger after the third touch. Swipe up, then move to the left or right. Move back to the middle to select only the first word again." wrapSubtitle />
      <Row title="Where the touchpad reads" subtitle="The touchpad stops at the middle of the keys at each side, and at the middle of the top and bottom rows. A finger outside this area is not read well." wrapSubtitle />
    </Section>
    <Section title="Keys">
      <Row title="Type with two thumbs" subtitle="Push the keys as on any keyboard. When you push the space bar, TitanKeys corrects typos in the word. To stop this, turn off Fix typos as you type in Settings." wrapSubtitle />
      <Row title="Type with a light touch" subtitle="Touch these keys lightly to type them. You do not have to push them. Select the keys in Settings." wrapSubtitle />
      <Row title="Hold a letter for its symbol" subtitle="Hold a letter to type the symbol printed on its key. Select the time in Settings." wrapSubtitle />
      <Row title="Full stop" subtitle="Push the space bar two times." wrapSubtitle />
      <Row title="Shift and Alt" subtitle="Push one time for the next letter only. Two quick pushes do the same. To cancel, push again after a longer time. To lock with two quick pushes, open Settings and turn on Double push locks Shift and Alt." wrapSubtitle />
      <Row title="Word suggestions" subtitle="The strip shows words and spelling corrections as you type. Touch a word to type it." wrapSubtitle />
      <Row title="Delete a word" subtitle="Push Alt, then push Backspace. You can also use Ctrl and Backspace, or flick left from Backspace." wrapSubtitle />
      <Row title="Delete many words" subtitle="Flick left from Backspace. Do not lift your finger. TitanKeys deletes more words, one after the other. A longer flick deletes the words faster. To stop, lift your finger or push a key." wrapSubtitle />
      <Row title="More symbols" subtitle="Push Sym to open the panel. Each letter key shows a symbol, but Q and A are empty and P shows the page. Rest your finger on a key to mark it. P does not show a mark. Push the key to type the symbol. The panel then closes. To see more, swipe up or down on the keyboard, or push Backspace or Enter. The marks at the bottom of the panel show the groups of symbols. To change the group, swipe left or right, or put your finger on the bottom row of keys." wrapSubtitle />
      <Row title="Emoji" subtitle="Push Sym again to show emoji. The first group is Recent, after you use an emoji. Push a key to type its emoji. Q and A are empty. P shows the page and does not type an emoji. To see more, swipe up or down on the keyboard, or push Backspace or Enter. To change the group, swipe left or right. The marks at the bottom of the panel show each group. Touch a mark to open its group, or put your finger on the bottom row of keys. Hold it there, or move it to the group. You can also push Space. Push Sym again to close the panel." wrapSubtitle />
      <Row title="Quick settings" subtitle="When the panel is open, push Alt to see the quick settings. Rest your finger on a key to see its setting at the bottom of the panel, and if the setting is on or off. Push the key to change the setting. The page stays open. A dim key shows a setting that is off. Push O to open all settings. Push K to show a list of your keyboards. Then select a keyboard. Push Alt again to go back, or push Sym to close the panel." wrapSubtitle />
      <Row title="Modifier bar" subtitle="When Shift, Caps, Alt, or Ctrl is on, the bar above the keyboard shows this in place of the words. A filled label shows a key that you hold. An outline shows a key that you pushed one time. Caps shows that Shift is locked. Rest your finger on a key to see what the key types." wrapSubtitle />
      <Row title="Orange accents" subtitle="Some small marks on the keyboard are orange, such as the key under your finger. To make them white, turn off Orange accents in Settings." wrapSubtitle />
      <Row title="Ctrl shortcuts" subtitle="Hold Ctrl. The bar above the keyboard shows what each shortcut does." wrapSubtitle />
      <Row title="Use a shortcut" subtitle="Hold Ctrl and push the key." />
      <Row title="Use fn as Ctrl" subtitle="Turn this on in Settings to use fn in place of Ctrl." />
    </Section>
    <Section title="Your words">
      <Row title="New words" subtitle="TitanKeys learns the new words that you use. To see, add or block words, open Settings and select Your words." wrapSubtitle />
      <Row title="Backup" subtitle="To keep a copy of your words, open Your words and select Export words. To add the words again, select Import words." wrapSubtitle />
    </Section>
    <Section title="Problems">
      <Row title="Check the setup" subtitle="If a part of TitanKeys does not operate correctly, open Settings and select Setup. Make sure that all steps show Done." wrapSubtitle />
      <Row title="Sym does not open the panel" subtitle="In the phone Settings, open Shortcut keys. Set Sym key to Programmable key." wrapSubtitle />
      <Row title="fn does not operate as Ctrl" subtitle="In the phone Settings, open Shortcut keys. Set fn key to Programmable key." />
      <Row title="Swiping does not operate in an app" subtitle="Open Scroll assistant and find the app. Make sure that it shows Sliding Mode 1." wrapSubtitle />
    </Section>
  </Screen>;
}
