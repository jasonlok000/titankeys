import { Field, Row, Screen, Section, Toggle, useAction, useSnapshot } from "ink";
import { close } from "ink/icons";
import { removeWord, state, update } from "../../../lib/atlas";
import { transfer } from "../../../lib/transfer";

export default function YourData() {
  const result = useSnapshot(state);
  const save = useAction(update);
  const remove = useAction(removeWord);
  const subtitle = "All your data is stored locally and never shared.";
  if (result.status !== "ready") return <Screen title="Your data" subtitle={subtitle} />;
  const { learning, words } = result.data;
  return <Screen title="Your data" subtitle={subtitle}>
    <Section title="Learning">
      <Toggle label="Learn my words" value={learning} onChange={learning => save.run({ learning })} />
    </Section>
    <Section title="Backup">
      <Field label="Export words" onPress={() => void transfer("export", "data")} />
      <Field label="Import words" onPress={() => void transfer("import", "data")} />
      <Field label="Reset learned words" href="/settings/data/reset" />
    </Section>
    <Section title="Saved words">
      {words.length
        ? words.map(({ word, description }) => <Row key={word} title={word} subtitle={description || undefined}
          endAction={{ icon: close, onPress: () => remove.run(word) }} />)
        : <Row title="New words you use appear here." />}
    </Section>
  </Screen>;
}
