import { Field } from "ink";
import type { EnterKey, State } from "../../lib/atlas";
import { ChoicePage } from "../../lib/choice";

const labels: Record<EnterKey, string> = { send: "Always send", newline: "Always new line", app: "App decides" };
const options = (["send", "newline", "app"] as const).map(value => ({ value, label: labels[value] }));

export const enterKeyLabel = ({ enterKey }: State) => labels[enterKey];

export default function EnterKeyPage() {
  return <ChoicePage title="Enter key"
    subtitle={"This changes Enter in message boxes. Search and other one-line fields stay as they are.\nAlways send: press Shift and Enter for a new line. In notes, Enter still adds a new line.\nAlways new line: press Shift and Enter to send. Ctrl and Enter sends too."}
    options={options}
    read={({ enterKey }) => enterKey}
    write={enterKey => ({ enterKey })}>
    <Field label="Message apps" href="/settings/enter-apps" />
  </ChoicePage>;
}
