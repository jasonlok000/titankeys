import { Field, Screen, Section, Toggle, useAction, useSnapshot } from "ink";
import { type State, exportRawLog, state, update } from "../../lib/atlas";

const title = "Typing log";
const subtitle = "A record of your touches on the keys, the keys that you push and the text that you type, to help make TitanKeys better. It does not record password, email, web address or private fields.";
const note = "The log stays on this phone until you export it. It holds the text that you typed. Examine it for personal text before you send it to a different person. The maximum size is 200 MB. When the log is full, TitanKeys removes the oldest data first.";

export const rawLogLabel = ({ rawLog }: State) => rawLog ? "On" : "Off";

const megabytes = (bytes: number) => `${(bytes / 1_048_576).toFixed(1)} MB`;

export default function RawLog() {
  const result = useSnapshot(state);
  const save = useAction(update);
  const exporting = useAction(exportRawLog);
  if (result.status !== "ready") return <Screen title={title} subtitle={subtitle} note={note} />;
  const { rawLog, rawLogBytes } = result.data;
  const exportResult = exporting.status === "pending" ? "Exporting"
    : exporting.status === "error" ? "Failed"
    : exporting.status === "success" && exporting.data ? "Saved" : "";
  return <Screen title={title} subtitle={subtitle} note={note}>
    <Section title="Recording">
      <Toggle label="Record my typing" value={rawLog} onChange={rawLog => save.run({ rawLog })} />
    </Section>
    <Section title="Log">
      <Field label="Log size">{megabytes(rawLogBytes)}</Field>
      <Field label="Export log" disabled={rawLogBytes === 0 || exporting.status === "pending"}
        onPress={() => exporting.run()}>{exportResult}</Field>
      <Field label="Delete all" disabled={rawLogBytes === 0} href="/settings/rawlog-delete" />
    </Section>
  </Screen>;
}
