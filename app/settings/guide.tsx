import { Row, Screen, Section } from "ink";

export default function Guide() {
  return <Screen title="Guide" subtitle="How to use Atlas.">
    <Section title="Swiping">
      <Row title="Swipe a word" subtitle="Slide across the letters without pressing the keys, then lift." wrapSubtitle />
      <Row title="Slip recovery" subtitle="A split-second lift won’t end your swipe or cursor mode." wrapSubtitle />
      <Row title="Pick another word" subtitle="Tap a suggestion on the strip above the keyboard." wrapSubtitle />
    </Section>
    <Section title="Keys">
      <Row title="Tap keys" subtitle="Type chosen keys with a light tap instead of a full press." wrapSubtitle />
      <Row title="Full stop" subtitle="Press space twice." wrapSubtitle />
      <Row title="Move the cursor" subtitle="Rest a finger on space until it clicks, then lean towards either end. Press space to select the word." wrapSubtitle />
      <Row title="Shift and Alt" subtitle="Press once for the next letter, twice quickly to lock, and again to release." wrapSubtitle />
    </Section>
  </Screen>;
}
