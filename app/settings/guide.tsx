import { Row, Screen, Section, replace, useRouteParams } from "ink";
import { close } from "ink/icons";

export default function Guide() {
  // Opened from the front page, the guide closes back to it, like Settings does.
  const fromHome = useRouteParams().from === "home";
  const closeAction = fromHome ? { icon: close, onPress: () => replace("/") } : undefined;
  return <Screen title="Guide" subtitle="How to use Atlas." rightAction={closeAction}>
    <Section title="Swiping">
      <Row title="Swipe a word" subtitle="Move your finger across the letters. Do not push the keys. Lift your finger to type the word." wrapSubtitle />
      <Row title="Slip recovery" subtitle="If your finger leaves the keyboard for a very short time, the swipe or cursor mode continues." wrapSubtitle />
      <Row title="Pick another word" subtitle="Touch a word on the strip above the keyboard." wrapSubtitle />
      <Row title="Move the cursor" subtitle="Rest your finger lightly on the space bar. Do not push it. When you feel a click, move your finger towards either end to move the cursor. Push the space bar to select the word." wrapSubtitle />
    </Section>
    <Section title="Keys">
      <Row title="Tap keys" subtitle="Touch these keys lightly to type them. You do not have to push them. Select the keys in Settings." wrapSubtitle />
      <Row title="Full stop" subtitle="Push the space bar two times." wrapSubtitle />
      <Row title="Shift and Alt" subtitle="Push one time for the next letter only. Push two times quickly to lock. Push again to unlock." wrapSubtitle />
      <Row title="Delete a word" subtitle="Push Alt, then push Backspace." wrapSubtitle />
      <Row title="More symbols" subtitle="When Alt is on, the strip shows more symbols. Move your finger to the left or right across the keyboard to see more." wrapSubtitle />
      <Row title="Emoji" subtitle="Push Sym one time for one emoji. Push two times quickly to lock. Push again to unlock. The strip shows emoji, with your recent emoji first. Move your finger to the left or right across the keyboard to see more. Move your finger up or down to go to the next group." wrapSubtitle />
    </Section>
    <Section title="Problems">
      <Row title="Check the setup" subtitle="If a part of Atlas does not operate correctly, open Settings and select Setup. Make sure that all steps show Done." wrapSubtitle />
    </Section>
  </Screen>;
}
