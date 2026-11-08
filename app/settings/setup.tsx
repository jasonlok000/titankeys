import { Field, Screen, Section, useSnapshot } from "ink";
import { allowOverlay, chooseKeyboard, enableKeyboard, openScrollAssistant, state, type State } from "../../lib/atlas";

const steps = ({ enabled, selected, overlay, scrollAssistant }: State) => [enabled, selected, overlay, scrollAssistant];

const status = (done: boolean) => done ? "Done" : "Not done";

export const setupLabel = (settings: State) => status(steps(settings).every(Boolean));

export default function Setup() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="Setup" />;
  const settings = result.data;
  return <Screen title="Setup">
    <Section title="Steps">
      <Field label="Enable Atlas" onPress={enableKeyboard}>{status(settings.enabled)}</Field>
      <Field label="Choose Atlas" onPress={chooseKeyboard}>{status(settings.selected)}</Field>
      <Field label="Allow overlay" onPress={allowOverlay}>{status(settings.overlay)}</Field>
      <Field label="Turn on Scroll assistant" onPress={openScrollAssistant}>{status(settings.scrollAssistant)}</Field>
    </Section>
  </Screen>;
}
