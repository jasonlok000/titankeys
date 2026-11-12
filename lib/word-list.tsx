import { useState } from "react";
import { Row, Screen, TextInput, useAction } from "ink";
import { add, close } from "ink/icons";
import { Note } from "./note";

export function WordList({ title, words, empty, placeholder, addWord, removeWord }: {
  title: string;
  words: readonly { word: string; description?: string }[];
  empty: string;
  placeholder: string;
  addWord: (word: string) => Promise<void>;
  removeWord: (word: string) => Promise<void>;
}) {
  const remove = useAction(removeWord);
  const [adding, setAdding] = useState(false);
  const [word, setWord] = useState("");
  const insert = useAction(async (value: string) => {
    await addWord(value.trim());
    setWord("");
    setAdding(false);
  });
  // Pinned so the input, drawn by Android above the app, never scrolls over the title.
  const header = adding
    ? <TextInput value={word} onChange={setWord} onSubmit={value => value.trim() ? insert.run(value) : setAdding(false)}
      placeholder={placeholder} action="done" autoFocus autoCapitalise={false}
      style={{ border: "none", paddingX: 0, paddingY: 0, minHeight: 50, fontSize: 26, placeholderColour: "muted" }}
      error={word && insert.status === "error" ? insert.error.message : undefined} />
    : <Row title="Add word" endIcon={add} onPress={() => setAdding(true)} />;
  return <Screen title={title} header={header}>
    {words.length
      ? words.map(({ word, description }) => <Row key={word} title={word} subtitle={description || undefined}
        endAction={{ icon: close, onPress: () => remove.run(word) }} />)
      : <Note>{empty}</Note>}
  </Screen>;
}
