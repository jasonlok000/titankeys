import { Field, Screen, Section, useSnapshot } from "ink";
import { allowBack, allowOverlay, chooseKeyboard, enableKeyboard, openScrollAssistant, state, type State } from "../../lib/atlas";

const steps = ({ enabled, selected, overlay, scrollAssistant, backService }: State) =>
  [enabled, selected, overlay, scrollAssistant, backService];

const status = (done: boolean) => done ? "Done" : "Not done";

export const setupLabel = (settings: State) => status(steps(settings).every(Boolean));

export default function Setup() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="Setup" />;
  const settings = result.data;
  return <Screen title="Setup">
    <Section title="Steps">
      <Field label="Turn on TitanKeys" onPress={enableKeyboard}>{status(settings.enabled)}</Field>
      <Field label="Make TitanKeys your keyboard" onPress={chooseKeyboard}>{status(settings.selected)}</Field>
      <Field label="Allow display over other apps" onPress={allowOverlay}>{status(settings.overlay)}</Field>
      <Field label="Turn on Scroll assistant" onPress={openScrollAssistant}>{status(settings.scrollAssistant)}</Field>
      <Field label="Turn on TitanKeys in Accessibility" onPress={allowBack}>{status(settings.backService)}</Field>
    </Section>
  </Screen>;
}
