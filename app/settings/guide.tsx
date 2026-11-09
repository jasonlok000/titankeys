import { Row, Screen, Section, replace, useRouteParams } from "ink";
import { close } from "ink/icons";

export default function Guide() {
  // Opened from the front page, the guide closes back to it, like Settings does.
  const fromHome = useRouteParams().from === "home";
  const closeAction = fromHome ? { icon: close, onPress: () => replace("/") } : undefined;
  return <Screen title="Guide" subtitle="How to use TitanKeys." rightAction={closeAction}>
    <Section title="Swiping">
      <Row title="Swipe a word" subtitle="Move your finger across the letters. Do not push the keys. Lift your finger to type the word." wrapSubtitle />
      <Row title="Slip recovery" subtitle="If your finger leaves the keyboard for a short time, the swipe continues. Change this time in Settings." wrapSubtitle />
      <Row title="Pick another word" subtitle="Touch a word on the strip above the keyboard." wrapSubtitle />
      <Row title="Turn off swipe typing" subtitle="Turn off Swipe typing in Settings or in the quick settings. The touchpad then types no words. Tap keys and the panel continue to operate." wrapSubtitle />
      <Row title="Move the cursor" subtitle="Touch the bottom row of keys two times quickly. Do not lift your finger after the second touch. Move your finger to the left or right. To select the word at the cursor, lift your finger after each touch." wrapSubtitle />
    </Section>
    <Section title="Keys">
      <Row title="Tap keys" subtitle="Touch these keys lightly to type them. Select the keys in Settings." wrapSubtitle />
      <Row title="Hold for symbols" subtitle="Hold a letter to type the symbol on its key. Select the time in Settings." />
      <Row title="Full stop" subtitle="Push the space bar two times." wrapSubtitle />
      <Row title="Shift and Alt" subtitle="Push one time for the next letter only. Push again to cancel. To lock with two quick pushes, open Settings and turn on Two pushes lock Shift and Alt." wrapSubtitle />
      <Row title="Word suggestions" subtitle="The strip shows words and spelling corrections as you type. Touch a word to type it." wrapSubtitle />
      <Row title="Delete a word" subtitle="Push Alt, then push Backspace. You can also use Ctrl and Backspace, or flick left from Backspace." wrapSubtitle />
      <Row title="More symbols" subtitle="Push Sym to open the panel. Each key shows a symbol. Rest your finger on a key to mark it. Push the key to type the symbol. The panel then closes. To see more, swipe up or down on the keyboard, or push Backspace or Enter." wrapSubtitle />
      <Row title="Emoji" subtitle="Push Sym again to show emoji. The first group is Recent, after you use an emoji. Push a key to type its emoji. To see more, swipe up or down on the keyboard, or push Backspace or Enter. To change the group, swipe left or right. The marks at the bottom of the panel show each group. Touch a mark to open its group, or put your finger on the bottom row of keys and move it to the group. You can also push Space. Push Sym again to close the panel." wrapSubtitle />
      <Row title="Quick settings" subtitle="When the panel is open, push Alt to see the quick settings. Rest your finger on a key to see its setting and if the setting is on or off. Push the key to change the setting. The panel then closes. A dim key shows a setting that is off. Push O to open all settings. Push Alt again to go back, or push Sym to close the panel." wrapSubtitle />
      <Row title="Ctrl shortcuts" subtitle="Hold Ctrl to see the shortcuts on the keys. The top of the panel shows what each shortcut does." wrapSubtitle />
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
