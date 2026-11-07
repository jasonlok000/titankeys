import { useEffect, useState } from "react";
import { Canvas, CanvasText, Icon, Screen, Stack, Text, replace, useSnapshot } from "ink";
import { arrowOutward, chevronRight } from "ink/icons";
import { allowOverlay, chooseKeyboard, enableKeyboard, onHomeFocus, state } from "../lib/atlas";
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
          <CanvasText x={0} y={0} width={596} height={28} size={20.67} colour="#7f7f84">{`STEP ${step} OF 3`}</CanvasText>
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
  return <Screen>
    <Stack align="stretch" gap={24} onPress={refocus}>
      <Stack align="stretch">
        <Canvas width={1} height={42} />
        <Stack axis="horizontal" align="center" justify="space-between">
          <Text size={49.33}>Atlas</Text>
          <Stack axis="horizontal" align="center" gap={6} onPress={() => replace("/settings")}>
            <Text>Settings</Text>
            <Icon name={chevronRight} size={28} tone="muted" />
          </Stack>
        </Stack>
      </Stack>
      <TryArea placeholder="Swipe a word on the keyboard" focus={focus} />
    </Stack>
  </Screen>;
}

export default function Home() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen />;
  const { enabled, selected, overlay } = result.data;
  if (!enabled) return <Step step={1} title="Enable Atlas" action="Open keyboard settings" onPress={enableKeyboard} />;
  if (!selected) return <Step step={2} title="Choose Atlas" action="Open keyboard list" onPress={chooseKeyboard} />;
  if (!overlay) return <Step step={3} title="Allow overlay" action="Open overlay settings" onPress={allowOverlay} />;
  return <FrontPage />;
}
