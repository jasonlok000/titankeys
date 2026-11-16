import { useEffect, useState } from "react";
import { Button, Field, Screen, Section, SettingsChoices, Stack, TextInput, back, useAction, useRouteParams, useSnapshot } from "ink";
import { Toggle, playHaptic } from "../../lib/app-haptics";
import { FEEDBACK_ADDRESS, copyText } from "../../lib/titankeys";
import { Note } from "../../lib/note";
import { type ReportKind, emailReport, reportKinds, reportState, sendReport } from "../../lib/report";

/**
 * The report form (#97), for Report a bug, Suggest an idea, Record a bug and the crash offer: what kind, what happened,
 * what you expected, an email for a reply if you want one, and what goes with it. Nothing goes until Send. It's sealed on
 * the phone so only we can read it (Reports.kt). If it can't go, it waits on the phone and the form offers email (#74).
 *
 * The route's `kind` picks the kind to start on: "idea", or "recording" from Record a bug. A waiting crash report starts
 * on Crash or freeze.
 */
export default function Report() {
  const params = useRouteParams();
  const result = useSnapshot(reportState);
  // F: drafts, all of this page's lines but the kinds' and the no-mail line (F's, #74).
  if (result.status !== "ready") return <Screen title="Report a bug" />;
  return <ReportForm start={params.kind === "idea" ? "idea" : result.data.crash ? "crash" : null} {...result.data}
    fromRecording={params.kind === "recording"} />;
}

function ReportForm({ start, recording: hasRecording, crash: hasCrash, froze, version, phone, android, fromRecording }: {
  start: ReportKind | null;
  recording: boolean;
  crash: boolean;
  froze: boolean;
  version: string;
  phone: string;
  android: string;
  fromRecording: boolean;
}) {
  const [kind, setKind] = useState<ReportKind | null>(start);
  const [what, setWhat] = useState("");
  const [expected, setExpected] = useState("");
  const [email, setEmail] = useState("");
  const [recording, setRecording] = useState(hasRecording);
  const [outcome, setOutcome] = useState<"sent" | "kept" | "large" | null>(null);
  const [noMail, setNoMail] = useState(false);
  const [copied, setCopied] = useState(false);
  useEffect(() => setRecording(hasRecording), [hasRecording]);
  const idea = kind === "idea";
  const emailOk = email.trim() === "" || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
  const send = useAction(async () => {
    const sent = await sendReport({ kind: kind!, what, expected: idea ? "" : expected, email: email.trim(), recording, crash: hasCrash });
    setOutcome(sent);
    if (sent === "sent") playHaptic("app_picked");
  });
  const mail = useAction(async () => {
    const opened = await emailReport(kind ?? "other");
    setNoMail(!opened);
  });

  if (outcome === "sent") return <Screen title="Sent" subtitle="Thanks. It's with us now, and only we can read it.">
    <Field label="Done" onPress={back}>{""}</Field>
  </Screen>;

  if (outcome) return <Screen title="Couldn't send it"
    subtitle={outcome === "large" ? "It's too big to send from here. It's kept on your phone, so you can email it instead."
      : "No connection, or our server's busy. It's kept on your phone, so you can email it instead, or try again later."}>
    <Field label="Email it instead" onPress={() => void mail.run()}>{""}</Field>
    {noMail && <>
      <Note>{`No mail app here. Send it to ${FEEDBACK_ADDRESS} from wherever you do email.`}</Note>
      <Field label={copied ? "Copied." : "Copy address"}
        onPress={() => void copyText("TitanKeys feedback address", FEEDBACK_ADDRESS).then(() => setCopied(true))}>{""}</Field>
    </>}
    {outcome === "kept" && <Field label="Try again" onPress={() => { setOutcome(null); void send.run(); }}>{""}</Field>}
  </Screen>;

  return <Screen title={idea ? "Suggest an idea" : "Report a bug"}
    subtitle="Goes straight to us, sealed so only we can read it. Nothing's sent until you press Send.">
    <Section title="What's it about?">
      <SettingsChoices value={kind ?? ""} options={[...reportKinds]} onChange={value => {
        playHaptic("app_picked");
        setKind(value as ReportKind);
      }} />
    </Section>
    <Stack gap={24}>
      <TextInput label={idea ? "Your idea" : "What happened?"} value={what} onChange={setWhat}
        placeholder={idea ? "What should TitanKeys do?" : "What you did, and what went wrong"} minLines={3} maxLines={10} action="return" />
      {!idea && <TextInput label="What did you expect?" value={expected} onChange={setExpected} minLines={2} maxLines={6} action="return" />}
      <TextInput label="Your email, if you want a reply" value={email} onChange={setEmail} placeholder="Optional"
        autoCapitalise={false} action="done" />
      {!emailOk && <Note>That email doesn't look right.</Note>}
    </Stack>
    <Section title="Sent with it">
      <Note>{`TitanKeys ${version}, ${phone}, Android ${android}`}</Note>
      {hasRecording && <Toggle label="The recording" value={recording} onChange={setRecording} />}
      {hasRecording && recording && <Note>{fromRecording
        ? "From Record a bug. It shows your touches and keys, so it can show what you typed while it ran."
        : "Your last Record a bug. It shows your touches and keys, so it can show what you typed while it ran."}</Note>}
      {hasCrash && <Note>{froze ? "The freeze report: where it froze, and the versions. Nothing you typed."
        : "The crash report: where it crashed, and the versions. Nothing you typed."}</Note>}
    </Section>
    {send.status === "error" && <Note>{send.error.message}</Note>}
    <Button disabled={!kind || what.trim() === "" || !emailOk || send.status === "pending"} onPress={() => void send.run()}>
      {send.status === "pending" ? "Sending…" : "Send"}
    </Button>
  </Screen>;
}
