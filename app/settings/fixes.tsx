import { Field, Screen, Section, Toggle, useAction, useSnapshot } from "ink";
import { type State, state, update } from "../../lib/atlas";
import { fixKinds } from "../../lib/fixes";
import { shorthandLabel } from "./shorthand/index";

export const fixesLabel = ({ fixToggles }: State) => {
  const on = fixKinds.filter(({ id }) => fixToggles[id] ?? true).length;
  return on === fixKinds.length ? "All" : on === 0 ? "None" : "Some";
};

export default function Fixes() {
  const result = useSnapshot(state);
  const save = useAction(update);
  const title = "What gets fixed";
  const subtitle = "It's all on to start. Switch off what you'd rather fix yourself. Backspace right after a fix puts back what you typed.";
  if (result.status !== "ready") return <Screen title={title} subtitle={subtitle} />;
  const settings = result.data;
  const { autocorrect, fixToggles, fixOnlyWhenSure } = settings;
  return <Screen title={title} subtitle={subtitle}>
    <Section title="Fixes">
      {fixKinds.map(({ id, label, example }) => <Toggle key={id} label={label} subtitle={example} value={fixToggles[id] ?? true}
        disabled={!autocorrect} onChange={on => save.run({ fixToggles: { [id]: on } })} />)}
      <Field label="Spell out shorthand" href="/settings/shorthand">{shorthandLabel(settings)}</Field>
    </Section>
    <Section title="How sure">
      <Toggle label="Only fix when sure" subtitle="A fix has to win by more before it goes in. You get fewer fixes, and fewer wrong ones."
        value={fixOnlyWhenSure} disabled={!autocorrect} onChange={on => save.run({ fixOnlyWhenSure: on })} />
    </Section>
  </Screen>;
}
