import type { EnterKey, State } from "../../lib/atlas";
import { ChoicePage } from "../../lib/choice";

const labels: Record<EnterKey, string> = { send: "Always send", newline: "Always new line", app: "App decides" };
const options = (["send", "newline", "app"] as const).map(value => ({ value, label: labels[value] }));

export const enterKeyLabel = ({ enterKey }: State) => labels[enterKey];

export default function EnterKeyPage() {
  return <ChoicePage title="Enter key"
    subtitle={"This setting changes Enter in messages. Search and other fields with one line do not change.\nAlways send: Push Shift and Enter to add a new line. In notes, Enter adds a new line.\nAlways new line: Push Ctrl and Enter to send the message."}
    options={options}
    read={({ enterKey }) => enterKey}
    write={enterKey => ({ enterKey })} />;
}
