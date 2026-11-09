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
      <Row title="Move the cursor" subtitle="Rest your finger on the space bar until you feel a click. Then move your finger to the left or right." wrapSubtitle />
    </Section>
    <Section title="Keys">
      <Row title="Tap keys" subtitle="Touch these keys lightly to type them. Select the keys in Settings." wrapSubtitle />
      <Row title="Hold for symbols" subtitle="Hold a letter to type the symbol on its key. Select the time in Settings." />
      <Row title="Full stop" subtitle="Push the space bar two times." wrapSubtitle />
      <Row title="Shift and Alt" subtitle="Push one time for the next letter only. Push two times quickly to lock. Push again to unlock." wrapSubtitle />
      <Row title="Word suggestions" subtitle="The strip shows words and spelling corrections as you type. Touch a word to type it." wrapSubtitle />
      <Row title="Delete a word" subtitle="Push Alt, then push Backspace. You can also use Ctrl and Backspace, or flick left from Backspace." wrapSubtitle />
      <Row title="More symbols" subtitle="When Alt is on, the strip shows symbols. Touch a symbol to type it. Swipe left or right on the keyboard to see more." wrapSubtitle />
      <Row title="Emoji" subtitle="Sym operates like Shift. The strip shows emoji, with recent emoji first. Swipe left or right on the keyboard to see more, or up or down for the next group." wrapSubtitle />
    </Section>
    <Section title="Your words">
      <Row title="New words" subtitle="TitanKeys learns the new words that you use. To see, add or block words, open Settings and select Your words." wrapSubtitle />
      <Row title="Backup" subtitle="To keep a copy of your words, open Your words and select Export words. To add the words again, select Import words." wrapSubtitle />
    </Section>
    <Section title="Problems">
      <Row title="Check the setup" subtitle="If a part of TitanKeys does not operate correctly, open Settings and select Setup. Make sure that all steps show Done." wrapSubtitle />
      <Row title="Sym does not show emoji" subtitle="In the phone Settings, open Shortcut keys. Set Sym key to Programmable key." wrapSubtitle />
      <Row title="Swiping does not operate in an app" subtitle="Open Scroll assistant and find the app. Make sure that it shows Sliding Mode 1." wrapSubtitle />
    </Section>
  </Screen>;
}
