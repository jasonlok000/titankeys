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
  const [word, setWord] = useState("");
  const insert = useAction(async (value: string) => {
    await addWord(value.trim());
    setWord("");
  });
  return <Screen title={title}>
    <TextInput value={word} onChange={setWord} onSubmit={value => { if (value.trim()) insert.run(value); }}
      placeholder="Add a word" leadingIcon={add} action="done" autoCapitalise={false}
      error={insert.status === "error" ? insert.error.message : undefined} />
    {words.length
      ? words.map(({ word, description }) => <Row key={word} title={word} subtitle={description || undefined}
        endAction={{ icon: close, onPress: () => remove.run(word) }} />)
      : <Row title={empty} />}
  </Screen>;
}
