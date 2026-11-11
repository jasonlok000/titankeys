import { useEffect } from "react";
import { Field, Section, useSnapshot } from "ink";
import { type HapticPreset, type State, previewTouchpad, state } from "../../lib/atlas";
import { pickFeelLabel } from "./pick-feel";
import { ChoicePage } from "../../lib/choice";

const options: { value: HapticPreset | "off"; label: string }[] = [
  { value: "off", label: "Off" },
  { value: "normal", label: "Normal" },
  { value: "strong", label: "Strong" },
];

export const vibrationLabel = ({ haptics, hapticPreset }: State) =>
  haptics ? options.find(option => option.value === hapticPreset)?.label ?? "Normal" : "Off";

export default function Vibration() {
  useEffect(previewTouchpad, []);
  const result = useSnapshot(state);
  const debug = result.status === "ready" && result.data.debugBuild;
  return <ChoicePage title="Vibration"
    subtitle="A little buzz for each key your swipe crosses. Swipe across the letters to feel it."
    options={options}
    read={({ haptics, hapticPreset }) => haptics ? hapticPreset : "off"}
    write={value => value === "off" ? { haptics: false } : { haptics: true, hapticPreset: value }}>
    {debug && result.status === "ready" && <Section title="Debug build">
      <Field label="Word pick feel" href="/settings/pick-feel">{pickFeelLabel(result.data)}</Field>
    </Section>}
  </ChoicePage>;
}
