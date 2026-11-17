import { Field, Screen, Section, useAction, useSnapshot } from "ink";
import { Toggle } from "../../lib/app-haptics";
import { type State, exportRawLog, state, update } from "../../lib/titankeys";
import { fileSize } from "../../lib/size";
import { Note } from "../../lib/note";

const title = "Typing log";
const subtitle = "A record of your touches on the keys, the keys you press and the text you type, to help make TitanKeys better. It skips password, email, web address and private fields.";
const note = "The log stays on your phone until you export it. It holds what you typed, so check it for anything personal before you send it to anyone. It tops out at 200 MB, then the oldest data goes first.";

export const rawLogLabel = ({ rawLog }: State) => rawLog ? "On" : "Off";


export default function RawLog() {
  const result = useSnapshot(state);
  const save = useAction(update);
  const exporting = useAction(exportRawLog);
  if (result.status !== "ready") return <Screen title={title} subtitle={subtitle} note={note} />;
  // The full typing log is only in TitanKeys Debug. A public release never offers it, even through a direct link.
  if (!result.data.rawLogAvailable) return <Screen title={title} subtitle="The typing log is only in TitanKeys Debug. To show us a bug, use Settings > Record a bug." />;
  const { rawLog, rawLogBytes } = result.data;
  const exportResult = exporting.status === "pending" ? "Exporting"
    : exporting.status === "error" ? "Failed"
    : exporting.status === "success" && exporting.data ? "Saved" : "";
  return <Screen title={title} subtitle={subtitle} note={note}>
    <Section title="Recording">
      <Toggle label="Record my typing" value={rawLog} onChange={rawLog => save.run({ rawLog })} />
      {/* F: draft. The owner's call for Debug (2026-11-17): on by default, for good. */}
      <Note>{rawLog ? "On from the start in TitanKeys Debug, and it stays on until you turn it off." : "Off. In TitanKeys Debug it starts on; turn it back on here."}</Note>
    </Section>
    <Section title="Log">
      <Field label="Log size">{fileSize(rawLogBytes)}</Field>
      <Field label="Export log" disabled={rawLogBytes === 0 || exporting.status === "pending"}
        onPress={() => exporting.run()}>{exportResult}</Field>
      <Field label="Delete all" disabled={rawLogBytes === 0} href="/settings/rawlog-delete" />
    </Section>
  </Screen>;
}
