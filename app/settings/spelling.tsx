import type { Spelling, State } from "../../lib/atlas";
import { ChoicePage } from "../../lib/choice";

const labels: Record<Spelling, string> = { british: "British", american: "American", canadian: "Canadian" };
const options = (["british", "american", "canadian"] as const).map(value => ({ value, label: labels[value] }));

export const spellingLabel = ({ spelling }: State) => labels[spelling] ?? "British";

export default function SpellingPage() {
  return <ChoicePage title="English spelling"
    subtitle={"Colour or color? Pick the spelling for suggestions and typo fixes.\nCanadian: colour and realize. Both. Eh."}
    options={options}
    read={({ spelling }) => spelling}
    write={spelling => ({ spelling })} />;
}
