import { Field, Row, Screen, Section, navigate, useAction, useSnapshot } from "ink";
import { KeyCapField, Note } from "../../../lib/note";
import { add, close } from "ink/icons";
import { type State, removeSnippet, state, transferSnippets } from "../../../lib/atlas";

const title = "Snippets";
const subtitle = "Type a shortcut and press Space, and it turns into its text. Changed your mind? Press Backspace right after. Snippets stay on your phone and never work in password fields.";

export const snippetsLabel = ({ snippets }: State) => snippets.length === 1 ? "1 snippet" : `${snippets.length} snippets`;

export default function Snippets() {
  const result = useSnapshot(state);
  const remove = useAction(removeSnippet);
  const transfer = useAction(transferSnippets);
  if (result.status !== "ready") return <Screen title={title} subtitle={subtitle} />;
  const { snippets, snippetStart } = result.data;
  return <Screen title={title} subtitle={subtitle}>
    <Section title="Your snippets">
      <Row title="Add snippet" endIcon={add} href="/settings/snippets/edit" />
      {snippets.map(({ shortcut, text }) => <Row key={shortcut} title={snippetStart + shortcut} subtitle={text}
        onPress={() => navigate({ path: "/settings/snippets/edit", params: { shortcut } })}
        endAction={{ icon: close, onPress: () => remove.run(shortcut) }} />)}
    </Section>
    <Section title="Shortcuts">
      <KeyCapField label="Start of each shortcut" value={snippetStart} onPress={() => navigate("/settings/snippets/start")} />
    </Section>
    <Section title="Backup">
      <Field label="Export snippets" onPress={() => void transfer.run("export")} />
      <Field label="Import snippets" onPress={() => void transfer.run("import")} />
      {transfer.status === "error" && <Note>{`That didn't work. ${transfer.error.message}`}</Note>}
    </Section>
  </Screen>;
}
