import { Field, Screen, Section, Toggle, useAction, useSnapshot } from "ink";
import { state, update } from "../../../lib/atlas";
import { transfer } from "../../../lib/transfer";

export default function YourWords() {
  const result = useSnapshot(state);
  const save = useAction(update);
  const subtitle = "Your words stay on this phone. TitanKeys never sends them anywhere.";
  if (result.status !== "ready") return <Screen title="Your words" subtitle={subtitle} />;
  const { learning } = result.data;
  return <Screen title="Your words" subtitle={subtitle}>
    <Section title="Learning">
      <Toggle label="Learn new words that I type" value={learning} onChange={learning => save.run({ learning })} />
    </Section>
    <Section title="My words">
      <Field label="Saved words" href="/settings/data/saved-words" />
      <Field label="Blocked words" href="/settings/data/blocked-words" />
    </Section>
    <Section title="Backup">
      <Field label="Export words" onPress={() => void transfer("export", "data")} />
      <Field label="Import words" onPress={() => void transfer("import", "data")} />
      <Field label="Remove learned words" href="/settings/data/reset" />
    </Section>
  </Screen>;
}
