import type { SentenceSpaces, State } from "../../lib/atlas";
import { ChoicePage } from "../../lib/choice";

const options = [
  { value: "one" as const, label: "One space" },
  { value: "two" as const, label: "Two spaces" },
  { value: "none" as const, label: "Don't add spaces" },
];

export const sentenceSpacesLabel = ({ sentenceSpaces }: State) =>
  options.find(option => option.value === sentenceSpaces)?.label ?? "One space";

export default function SentenceSpacesPage() {
  return <ChoicePage<SentenceSpaces> title="Space after a sentence"
    subtitle="Type . ? or ! and the next letter you type gets its space. Numbers, links, ... and Mr. stay as you type them."
    options={options}
    read={({ sentenceSpaces }) => sentenceSpaces}
    write={sentenceSpaces => ({ sentenceSpaces })} />;
}
