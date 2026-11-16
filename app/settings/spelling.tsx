import type { Spelling, State } from "../../lib/atlas";
import { ChoicePage } from "../../lib/choice";

// American first, as the keyboard's default (#80).
const labels: Record<Spelling, string> = { american: "American", british: "British", canadian: "Canadian" };
const options = (["american", "british", "canadian"] as const).map(value => ({ value, label: labels[value] }));

export const spellingLabel = ({ spelling }: State) => labels[spelling] ?? "American";

export default function SpellingPage() {
  return <ChoicePage title="English spelling"
    subtitle={"Colour or color? Pick the spelling for suggestions and typo fixes.\nCanadian: colour and realize. Both. Eh."}
    options={options}
    read={({ spelling }) => spelling}
    write={spelling => ({ spelling })} />;
}
