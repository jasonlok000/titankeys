import type { PickFeel, State } from "../../lib/atlas";
import { ChoicePage } from "../../lib/choice";

/** Only TitanKeys Debug shows this page. Each option plays when a word is picked: a double or triple tap, or a word drag step. */
const labels: Record<PickFeel, string> = {
  off: "Usual",
  length: "Longer for longer words",
  syllables: "A tick per syllable",
  weight: "Heavier for longer words",
};
const options = (["off", "length", "syllables", "weight"] as const).map(value => ({ value, label: labels[value] }));

export const pickFeelLabel = ({ pickFeel }: State) => labels[pickFeel];

export default function PickFeelPage() {
  return <ChoicePage title="Word pick feel"
    subtitle="Debug build only. Pick a word with a double or triple tap on Space, or drag over words, and feel the difference."
    options={options}
    read={({ pickFeel }) => pickFeel}
    write={pickFeel => ({ pickFeel })} />;
}
