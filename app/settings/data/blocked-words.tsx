import { Screen, useSnapshot } from "ink";
import { blockWord, state, unblockWord } from "../../../lib/atlas";
import { WordList } from "../../../lib/word-list";

export default function BlockedWords() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="Blocked words" />;
  return <WordList title="Blocked words" words={result.data.blockedWords.map(word => ({ word }))} empty="Nothing blocked yet." placeholder="Type a word to block and press Enter"
    addWord={blockWord} removeWord={unblockWord} />;
}
