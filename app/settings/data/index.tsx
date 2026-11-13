import { Field, Screen, Section, useAction, useSnapshot } from "ink";
import { Toggle } from "../../../lib/app-haptics";
import { state, update } from "../../../lib/atlas";
import { transfer } from "../../../lib/transfer";

export default function YourWords() {
  const result = useSnapshot(state);
  const save = useAction(update);
  const subtitle = "Your words and the names TitanKeys knows stay on your phone. We never send them anywhere.";
  if (result.status !== "ready") return <Screen title="Your words" subtitle={subtitle} />;
  const { learning, contactNames } = result.data;
  return <Screen title="Your words" subtitle={subtitle}>
    <Section title="Learning">
      <Toggle label="Learn new words I type" value={learning} onChange={learning => save.run({ learning })} />
    </Section>
    <Section title="Names on this phone">
      <Toggle label="Names from my contacts" value={contactNames} onChange={contactNames => save.run({ contactNames })} />
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
