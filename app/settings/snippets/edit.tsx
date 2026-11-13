import { useState } from "react";
import { Button, Screen, Stack, TextInput, back, useAction, useRouteParams, useSnapshot } from "ink";
import { putSnippet, state } from "../../../lib/atlas";
import { Note } from "../../../lib/note";

/** Adds a snippet, or changes the one whose shortcut is in the route's `shortcut` parameter. */
export default function EditSnippet() {
  const shortcut = useRouteParams().shortcut;
  const previous = typeof shortcut === "string" && shortcut ? shortcut : undefined;
  const result = useSnapshot(state);
  const title = previous ? "Change snippet" : "Add snippet";
  if (result.status !== "ready") return <Screen title={title} />;
  const existing = result.data.snippets.find(snippet => snippet.shortcut === previous);
  return <SnippetForm title={title} start={result.data.snippetStart} previous={existing?.shortcut}
    shortcut={existing?.shortcut ?? ""} text={existing?.text ?? ""} />;
}

function SnippetForm({ title, start, previous, ...initial }: {
  title: string;
  start: string;
  previous?: string;
  shortcut: string;
  text: string;
}) {
  const [shortcut, setShortcut] = useState(initial.shortcut);
  const [text, setText] = useState(initial.text);
  const save = useAction(async () => {
    await putSnippet(shortcut.trim(), text, previous);
    back();
  });
  const ready = shortcut.trim().length > 0 && text.length > 0;
  return <Screen title={title} subtitle="Shortcuts can use letters, numbers, _ - and +. Type one and press Space to get its text.">
    <Stack gap={24}>
      <TextInput label="Shortcut" prefix={start} value={shortcut} onChange={setShortcut} placeholder="addr"
        autoFocus={!previous} autoCapitalise={false} action="done" />
      <TextInput label="Text" value={text} onChange={setText} placeholder="What the shortcut turns into"
        minLines={3} maxLines={10} action="return" />
      {save.status === "error" && <Note>{save.error.message}</Note>}
      <Button disabled={!ready || save.status === "pending"} onPress={() => save.run()}>Save</Button>
    </Stack>
  </Screen>;
}
