import { Row, Screen, Section } from "ink";
import { arrowOutward, chevronRight } from "ink/icons";
import { openGuide } from "../../lib/atlas";

/**
 * Settings > Guide: the fun guide, which teaches on the keys, and the boring guide, which has every feature in words.
 */
export default function Guide() {
  return <Screen title="Guide" subtitle="Two flavours. Pick one.">
    <Section title="Guides">
      <Row title="The fun guide" subtitle="Learn it on the keys. Takes a few minutes." endIcon={arrowOutward} onPress={() => void openGuide()} />
      <Row title="The boring guide" subtitle="Every feature, in words. All of them." endIcon={chevronRight} href="/settings/guide-boring" />
    </Section>
  </Screen>;
}
