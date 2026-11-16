import { useEffect, useState } from "react";
import { Field, Screen, Section, useSnapshot } from "ink";
import { MUTED, useAccent } from "../../lib/brand";
import { bugState, deleteBug, emailBug, shareBug, startBug, stopBug } from "../../lib/bug";
import { bugText as text, duration } from "../../lib/bug-text";
import { allowNotifications } from "../../lib/connected";
import { StatusLine } from "../../lib/contribute";
import { Note, Notes } from "../../lib/note";
import { playHaptic } from "../../lib/app-haptics";
import { FEEDBACK_ADDRESS, copyText, state as atlasState } from "../../lib/atlas";

/** BugRecording.ZIP_OVER_BYTES: a bigger recording is zipped before it's attached, which takes a moment. */
const ZIP_OVER_BYTES = 10 * 1024 * 1024;

/**
 * Record a bug (#64): record, stop, look at what's in it, then email it to us (#74) or share it through Android's share sheet.
 * With no mail app, the page shows the address, and Share elsewhere is right there.
 */
export default function RecordBug() {
  const accent = useAccent();
  const result = useSnapshot(bugState);
  const app = useSnapshot(atlasState);
  // While it records, the time left counts down here too, as the notification may be off.
  const [now, setNow] = useState(0);
  const [noMail, setNoMail] = useState(false);
  const [copied, setCopied] = useState(false);
  const [zipping, setZipping] = useState(false);
  const recording = result.status === "ready" && result.data.recording;
  useEffect(() => {
    if (!recording) return;
    const started = Date.now();
    const tick = setInterval(() => {
      setNow(Date.now() - started);
      if (result.status === "ready" && result.data.elapsed + Date.now() - started > result.data.maxMillis) void bugState.refresh();
    }, 1000);
    return () => clearInterval(tick);
  }, [recording]);
  if (result.status !== "ready") return <Screen title={text.title} subtitle={text.subtitle} />;
  const state = result.data;

  if (state.recording) {
    const left = Math.max(0, Math.round((state.maxMillis - state.elapsed - now) / 1000));
    return <Screen title={text.title} subtitle={text.subtitle}>
      <Section title={text.recordingTitle}>
        <StatusLine label={text.recordingTitle} value={text.left(left)} colour={accent} />
        <Field label={text.stop} onPress={() => void stopBug()}>{""}</Field>
      </Section>
    </Screen>;
  }

  const summary = state.summary;
  if (summary) return <Screen title={text.title}>
    <Section title={text.doneTitle}>
      <StatusLine label={text.length} value={duration(summary.millis)} colour={MUTED} />
      <StatusLine label={text.touches} value={String(summary.touches)} colour={MUTED} />
      <StatusLine label={text.swipes} value={String(summary.swipes)} colour={MUTED} />
      <StatusLine label={text.keys} value={String(summary.keys)} colour={MUTED} />
    </Section>
    <Section title={text.typedTitle}>
      <Note>{summary.words.length ? summary.words.join(" ") : text.nothingTyped}</Note>
    </Section>
    <Section title={text.warningTitle}>
      <Notes lines={text.warning} />
    </Section>
    <Field label={text.email} onPress={() => {
      const about = app.status === "ready" ? app.data : null;
      const details = about ? `TitanKeys version: ${about.version}\nPhone: ${about.phone}\nAndroid: ${about.android}` : "";
      setZipping((summary.bytes ?? 0) > ZIP_OVER_BYTES);
      void emailBug(text.emailSubject(about?.version ?? ""), text.emailText(details))
        .then(sent => setNoMail(!sent))
        .finally(() => setZipping(false));
    }}>{""}</Field>
    {zipping && <Note>{text.zipping}</Note>}
    {noMail && <>
      <Note>{text.noMail(FEEDBACK_ADDRESS)}</Note>
      <Note>{text.noMailRecording}</Note>
      <Field label={copied ? text.copied : text.copyAddress}
        onPress={() => void copyText("TitanKeys feedback address", FEEDBACK_ADDRESS).then(() => setCopied(true))}>{""}</Field>
    </>}
    <Field label={text.share} onPress={() => void shareBug(text.shareSubject, text.shareText)}>{""}</Field>
    <Field label={text.again} onPress={() => void startBug()}>{""}</Field>
    <Field label={text.delete} onPress={() => void deleteBug().then(() => playHaptic("app_deleted"))}>{""}</Field>
  </Screen>;

  return <Screen title={text.title} subtitle={text.subtitle}>
    <Section title={text.howTitle}>
      <Notes lines={text.how} />
      {/* What a recording can show, before you start one, as the privacy policy says. */}
      <Note>{text.beforeRecord}</Note>
    </Section>
    <Field label={text.record} onPress={() => void startBug()}>{""}</Field>
    {!state.notificationsAllowed && <>
      <Field label={text.allow} onPress={() => void allowNotifications()}>{""}</Field>
      <Note>{text.notifications}</Note>
    </>}
  </Screen>;
}
