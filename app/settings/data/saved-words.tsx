import { Screen, useSnapshot } from "ink";
import { addWord, removeWord, state } from "../../../lib/atlas";
import { WordList } from "../../../lib/word-list";

export default function SavedWords() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="Saved words" />;
  return <WordList title="Saved words" words={result.data.words} empty="When you use a new word 2 times, it shows here." placeholder="Type a word to add and press Enter"
    addWord={addWord} removeWord={removeWord} />;
}
