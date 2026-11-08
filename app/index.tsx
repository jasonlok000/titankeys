import { useEffect, useState } from "react";
import { Canvas, CanvasText, Icon, Screen, Stack, Text, replace, useSnapshot } from "ink";
import { arrowOutward } from "ink/icons";
import guideIcon from "../assets/icons/guide.svg";
import settingsIcon from "../assets/icons/settings.svg";
import { allowBack, allowOverlay, chooseKeyboard, enableKeyboard, markNotesSeen, onHomeFocus, openScrollAssistant, state } from "../lib/atlas";
import { hasReleaseNotes } from "../lib/release-notes";
import { WhatsNew } from "../lib/whats-new";
import { TryArea } from "../lib/try";

function Step({ step, title, action, onPress }: {
  step: number;
  title: string;
  action: string;
  onPress: () => void;
}) {
  return <Screen centered theme="onboarding">
    <Stack align="center" gap={14}>
      <Stack align="center" gap={8}>
        <Canvas width={596} height={28}>
          <CanvasText x={0} y={0} width={596} height={28} size={20.67} colour="#7f7f84">{`STEP ${step} OF 5`}</CanvasText>
        </Canvas>
        <Text size={49.33} align="center">{title}</Text>
      </Stack>
      <Stack axis="horizontal" align="center" gap={12} onPress={onPress}>
        <Text>{action}</Text>
        <Icon name={arrowOutward} size={26} tone="muted" />
      </Stack>
    </Stack>
  </Screen>;
}

function FrontPage() {
  const [focus, setFocus] = useState(0);
  const refocus = () => setFocus(value => value + 1);
  useEffect(() => onHomeFocus(refocus), []);
  return <Screen title="Atlas" rightActions={[
    { icon: guideIcon, onPress: () => replace({ path: "/settings/guide", params: { from: "home" } }) },
    { icon: settingsIcon, onPress: () => replace("/settings") },
  ]}>
    <Stack align="stretch" onPress={refocus}>
      <Canvas width={1} height={12} />
      <TryArea placeholder="Swipe a word on the keyboard" focus={focus} />
    </Stack>
  </Screen>;
}

export default function Home() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen />;
  const { enabled, selected, overlay, scrollAssistant, backService, version, whatsNew } = result.data;
  if (!enabled) return <Step step={1} title="Enable Atlas" action="Open keyboard settings" onPress={enableKeyboard} />;
  if (!selected) return <Step step={2} title="Choose Atlas" action="Open keyboard list" onPress={chooseKeyboard} />;
  if (!overlay) return <Step step={3} title="Allow overlay" action="Open overlay settings" onPress={allowOverlay} />;
  if (!scrollAssistant) return <Step step={4} title="Turn on Scroll assistant" action="Open Scroll assistant" onPress={openScrollAssistant} />;
  if (!backService) return <Step step={5} title="Turn on Atlas in Accessibility" action="Open accessibility settings" onPress={allowBack} />;
  if (whatsNew && hasReleaseNotes(version)) return <WhatsNew version={version} onClose={() => void markNotesSeen()} />;
  return <FrontPage />;
}
