import { useEffect } from "react";
import { Screen, Section, useAction, useSnapshot } from "ink";
import { Toggle } from "../../lib/app-haptics";
import { type State, previewTouchpad, state, update } from "../../lib/atlas";
import { hapticGroups } from "../../lib/haptics";

export const hapticsLabel = ({ haptics }: State) => haptics ? "On" : "Off";

export default function Haptics() {
  useEffect(previewTouchpad, []);
  const result = useSnapshot(state);
  const save = useAction(update);
  const subtitle = "Only for what your thumb can't feel or see. Key presses never buzz. Swipe across the letters to feel it.";
  if (result.status !== "ready") return <Screen title="Haptics" subtitle={subtitle} />;
  const { haptics, hapticToggles } = result.data;
  return <Screen title="Haptics" subtitle={subtitle}>
    <Toggle label="All haptics" value={haptics} onChange={on => save.run({ haptics: on })} />
    {hapticGroups.map(group => <Section key={group.title} title={group.title}>
      {group.haptics.map(({ id, label }) => <Toggle key={id} label={label} value={hapticToggles[id] ?? true} disabled={!haptics}
        onChange={on => save.run({ hapticToggles: { [id]: on } })} />)}
    </Section>)}
  </Screen>;
}
