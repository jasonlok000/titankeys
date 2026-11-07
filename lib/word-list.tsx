import { useState } from "react";
import { Row, Screen, TextInput, useAction } from "ink";
import { add, close } from "ink/icons";

export function WordList({ title, words, empty, addWord, removeWord }: {
  title: string;
  words: readonly { word: string; description?: string }[];
  empty: string;
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
  return <Screen title={title}>
    {words.length
      ? words.map(({ word, description }) => <Row key={word} title={word} subtitle={description || undefined}
        endAction={{ icon: close, onPress: () => remove.run(word) }} />)
      : <Row title={empty} />}
    {adding ? <>
      <TextInput value={word} onChange={setWord} onSubmit={value => insert.run(value)}
        placeholder="Type a word and press Enter" action="done" autoFocus autoCapitalise={false} disabled={insert.status === "pending"}
        error={insert.status === "error" ? insert.error.message : undefined} />
      <Row title="Cancel" endIcon={close} onPress={() => { setAdding(false); setWord(""); }} />
    </> : <Row title="Add word" endIcon={add} onPress={() => setAdding(true)} />}
  </Screen>;
}
