import { Field, Screen, Section, useAction, useSnapshot } from "ink";
import { Toggle } from "../../lib/app-haptics";
import { type State, state, update } from "../../lib/titankeys";
import { fixKinds } from "../../lib/fixes";
import { earlierWordsLabel } from "./earlier-words";
import { shorthandLabel } from "./shorthand/index";
import { languageValues } from "../../lib/language-page";

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
  const { fixToggles, fixOnlyWhenSure } = settings;
  // The fixes are English's: its own Fix typos switch decides (TypingSettings.PER_LANGUAGE).
  const { autocorrect } = languageValues(settings, "en");
  return <Screen title={title} subtitle={subtitle}>
    <Section title="Fixes">
      {fixKinds.map(({ id, label, example }) => <Toggle key={id} label={label} subtitle={example} value={fixToggles[id] ?? true}
        disabled={!autocorrect} onChange={on => save.run({ fixToggles: { [id]: on } })} />)}
      <Field label="Spell out shorthand" href="/settings/shorthand">{shorthandLabel(settings)}</Field>
      <Field label="Fix earlier words" href="/settings/earlier-words">{earlierWordsLabel(settings)}</Field>
    </Section>
    <Section title="How sure">
      <Toggle label="Only fix when sure" subtitle="A fix has to win by more before it goes in, and earlier words only get small fixes. You get fewer fixes, and fewer wrong ones."
        value={fixOnlyWhenSure} disabled={!autocorrect} onChange={on => save.run({ fixOnlyWhenSure: on })} />
    </Section>
  </Screen>;
}
