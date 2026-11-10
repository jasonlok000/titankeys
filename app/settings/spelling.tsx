import type { State } from "../../lib/atlas";
import { ChoicePage } from "../../lib/choice";

export const spellingLabel = ({ spelling }: State) => spelling === "american" ? "American" : "British";

export default function SpellingPage() {
  return <ChoicePage title="English spelling"
    subtitle="Colour or color? Pick the spelling for suggestions and typo fixes."
    options={[{ value: "british" as const, label: "British" }, { value: "american" as const, label: "American" }]}
    read={({ spelling }) => spelling}
    write={spelling => ({ spelling })} />;
}
