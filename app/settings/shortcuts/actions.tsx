import { Field, Screen, Section } from "ink";

// The actions that Alt and a key do in every app. They are fixed, so the page only lists them.
const editing = [
  ["Undo", "Z"], ["Redo", "Y"], ["Select all", "A"], ["Copy", "C"], ["Cut", "X"], ["Paste", "V"], ["Delete word", "Backspace"],
];

export default function Actions() {
  return <Screen title="Actions" subtitle="Hold Alt and push a key to trigger action.">
    <Section title="Editing">
      {editing.map(([action, key]) => <Field key={action} label={action}>{key}</Field>)}
    </Section>
  </Screen>;
}
