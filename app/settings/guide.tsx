import { Row, Screen, Section, replace, useRouteParams } from "ink";
import { arrowOutward, chevronRight, close } from "ink/icons";
import { openGuide } from "../../lib/atlas";

/**
 * Settings > Guide: the fun guide, which teaches on the keys, and the boring guide, which has every feature in words.
 */
export default function Guide() {
  // Opened from the front page, the guide closes back to it, like Settings does.
  const fromHome = useRouteParams().from === "home";
  const closeAction = fromHome ? { icon: close, onPress: () => replace("/") } : undefined;
  return <Screen title="Guide" subtitle="Two flavours. Pick one." rightAction={closeAction}>
    <Section title="Guides">
      <Row title="The fun guide" subtitle="Learn it on the keys. Takes a few minutes." endIcon={arrowOutward} onPress={() => void openGuide()} />
      <Row title="The boring guide" subtitle="Every feature, in words. All of them." endIcon={chevronRight} href="/settings/guide-boring" />
    </Section>
  </Screen>;
}
