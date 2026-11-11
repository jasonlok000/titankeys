import { Field, Screen, Section, Toggle, useAction, useSnapshot } from "ink";
import { type State, exportRawLog, state, update } from "../../lib/atlas";

const title = "Typing log";
const subtitle = "A record of your touches on the keys, the keys you press and the text you type, to help make TitanKeys better. It skips password, email, web address and private fields.";
const note = "The log stays on your phone until you export it. It holds what you typed, so check it for anything personal before you send it to anyone. It tops out at 200 MB, then the oldest data goes first.";

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
