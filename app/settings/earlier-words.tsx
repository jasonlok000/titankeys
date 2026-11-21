import type { State } from "../../lib/titankeys";
import { ChoicePage } from "../../lib/choice";
import { earlierChoices } from "../../lib/fixes";

type Earlier = State["earlierWords"];
const options = earlierChoices.map(({ value, label }) => ({ value, label }));

export const earlierWordsLabel = ({ earlierWords }: State) =>
  earlierChoices.find(({ value }) => value === earlierWords)?.label ?? "Small fixes";

/** Fix earlier words (owner, 2026-11-20): how far a fix may go back to a finished word. English only. Stand-in wording for F. */
export default function EarlierWordsPage() {
  return <ChoicePage<Earlier> title="Fix earlier words"
    subtitle={"English only. When the next word shows an earlier one was wrong, TitanKeys can go back and fix it.\n" +
      earlierChoices.map(({ label, example }) => `${label}: ${example}.`).join("\n") +
      "\nBackspace right after puts back what you typed."}
    options={options}
    read={({ earlierWords }) => earlierWords}
    write={earlierWords => ({ earlierWords })} />;
}
