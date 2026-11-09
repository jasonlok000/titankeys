import { useEffect, useState } from "react";
import { Canvas, CanvasIcon, CanvasText, Rectangle, Screen, Stack, Text, replace, useSnapshot } from "ink";
import { arrowOutward } from "ink/icons";
import guideIcon from "../assets/icons/guide.svg";
import settingsIcon from "../assets/icons/settings.svg";
import { allowBack, allowOverlay, chooseKeyboard, enableKeyboard, markNotesSeen, onHomeFocus, openScrollAssistant, state } from "../lib/atlas";
import { INK, MUTED, ORANGE } from "../lib/brand";
import { hasReleaseNotes } from "../lib/release-notes";
import { WhatsNew } from "../lib/whats-new";
import { TryArea } from "../lib/try";

const WIDTH = 596;
const STEPS = 5;

function Progress({ step }: { step: number }) {
  const gap = 10;
  const segment = (WIDTH - gap * (STEPS - 1)) / STEPS;
  return <Canvas width={WIDTH} height={14}>
    {Array.from({ length: STEPS }, (_, index) => index < step
      ? <Rectangle key={index} x={index * (segment + gap)} y={0} width={segment} height={14} fill={INK} />
      : <Rectangle key={index} x={index * (segment + gap) + 1} y={1} width={segment - 2} height={12} stroke={INK} strokeWidth={2} />)}
  </Canvas>;
}

function Action({ label, onPress }: { label: string; onPress: () => void }) {
  const height = 108;
  const icon = 30;
  return <Canvas width={WIDTH} height={height}>
    <Rectangle x={0} y={0} width={WIDTH} height={height} fill={INK} onPress={onPress} />
    <CanvasText x={0} y={0} width={WIDTH} height={height} size={32} colour={ORANGE} align="center">{label}</CanvasText>
    <CanvasIcon x={WIDTH - icon - 32} y={(height - icon) / 2} size={icon} name={arrowOutward} colour={ORANGE} />
  </Canvas>;
}

function Step({ step, title, hint, action, onPress }: {
  step: number;
  title: string;
  hint: string;
  action: string;
  onPress: () => void;
}) {
  return <Screen centered theme="onboarding">
    <Stack align="center" gap={44}>
      <Progress step={step} />
      <Stack align="center" gap={14}>
        <Canvas width={WIDTH} height={28}>
          <CanvasText x={0} y={0} width={WIDTH} height={28} size={21} colour={MUTED} align="center">{`STEP ${step} OF ${STEPS}`}</CanvasText>
        </Canvas>
        <Text size={60} align="center">{title}</Text>
        <Text size={30} align="center">{hint}</Text>
      </Stack>
      <Action label={action} onPress={onPress} />
    </Stack>
  </Screen>;
}

function FrontPage() {
  const [focus, setFocus] = useState(0);
  const refocus = () => setFocus(value => value + 1);
  useEffect(() => onHomeFocus(refocus), []);
  return <Screen title="TitanKeys" rightActions={[
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
  if (!enabled) return <Step step={1} title="Enable TitanKeys" hint="Turn it on in your list of keyboards." action="Open keyboard settings" onPress={enableKeyboard} />;
  if (!selected) return <Step step={2} title="Choose TitanKeys" hint="Make it the keyboard you type with." action="Open keyboard list" onPress={chooseKeyboard} />;
  if (!overlay) return <Step step={3} title="Allow overlay" hint="Keeps the touchpad working above text selection." action="Open overlay settings" onPress={allowOverlay} />;
  if (!scrollAssistant) return <Step step={4} title="Turn on Scroll assistant" hint="Swiping needs this to read the touchpad." action="Open Scroll assistant" onPress={openScrollAssistant} />;
  if (!backService) return <Step step={5} title="Turn on TitanKeys in Accessibility" hint="Lets one back swipe close the suggestion bar." action="Open accessibility settings" onPress={allowBack} />;
  if (whatsNew && hasReleaseNotes(version)) return <WhatsNew version={version} onClose={() => void markNotesSeen()} />;
  return <FrontPage />;
}
