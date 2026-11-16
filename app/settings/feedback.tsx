import { useState } from "react";
import { Field, Row, Screen, Section, useSnapshot } from "ink";
import { arrowOutward } from "ink/icons";
import { FEEDBACK_ADDRESS, copyText, forceCrash, sendMail, state } from "../../lib/atlas";
import { Note } from "../../lib/note";

/** F's line for a phone with no mail app. */
const noMailNote = `No mail app here. Send it to ${FEEDBACK_ADDRESS} from wherever you do email.`;

/**
 * Settings > Send feedback (#74): Report a bug and Suggest an idea open the mail app to our address, with the subject and the
 * prompts filled in. No account and no server; TitanKeys sends nothing itself. With no mail app, the address and a Copy button.
 */
export default function Feedback() {
  const result = useSnapshot(state);
  const [noMail, setNoMail] = useState(false);
  const [copied, setCopied] = useState(false);
  const compose = async (subject: string, body: string) => {
    setNoMail(false);
    setNoMail(!await sendMail(subject, body));
  };
  const ready = result.status === "ready" ? result.data : null;
  const details = ready ? `TitanKeys version: ${ready.version}\nPhone: ${ready.phone}\nAndroid: ${ready.android}` : "";
  // F's lines (docs/words-m1.md, #74).
  return <Screen title="Send feedback" subtitle="Opens your mail app. A real person reads it.">
    <Row title="Report a bug" endIcon={arrowOutward} onPress={() => void compose(ready ? `TitanKeys bug, ${ready.version}` : "TitanKeys bug",
      `What happened?\n\n\nWhat should have happened?\n\n\n${details}`)} />
    <Row title="Suggest an idea" endIcon={arrowOutward} onPress={() => void compose("TitanKeys idea", "What should TitanKeys do?\n\n")} />
    {noMail && <>
      <Note>{noMailNote}</Note>
      <Field label={copied ? "Copied." : "Copy address"} onPress={() => void copyText("TitanKeys feedback address", FEEDBACK_ADDRESS).then(() => setCopied(true))}>{""}</Field>
    </>}
    {ready?.build === "debug" && <Section title="TitanKeys Debug">
      {/* Tests the crash offer: TitanKeys crashes, and the next open offers to email the report. F: draft label. */}
      <Field label="Crash TitanKeys" onPress={() => void forceCrash()}>{""}</Field>
    </Section>}
  </Screen>;
}
