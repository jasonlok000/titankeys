import { useEffect, useState } from "react";
import { Field, Screen, Section, useSnapshot } from "ink";
import { MUTED, ORANGE } from "../../lib/brand";
import { bugState, deleteBug, shareBug, startBug, stopBug } from "../../lib/bug";
import { bugText as text, duration } from "../../lib/bug-text";
import { allowNotifications } from "../../lib/connected";
import { StatusLine } from "../../lib/contribute";
import { Note, Notes } from "../../lib/note";

/** Record a bug (#64): record, stop, look at what's in it, then share it through Android's share sheet. */
export default function RecordBug() {
  const result = useSnapshot(bugState);
  // While it records, the time left counts down here too, as the notification may be off.
  const [now, setNow] = useState(0);
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
        <StatusLine label={text.recordingTitle} value={text.left(left)} colour={ORANGE} />
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
    <Field label={text.share} onPress={() => void shareBug(text.shareSubject, text.shareText)}>{""}</Field>
    <Field label={text.again} onPress={() => void startBug()}>{""}</Field>
    <Field label={text.delete} onPress={() => void deleteBug()}>{""}</Field>
  </Screen>;

  return <Screen title={text.title} subtitle={text.subtitle}>
    <Section title={text.howTitle}>
      <Notes lines={text.how} />
    </Section>
    <Field label={text.record} onPress={() => void startBug()}>{""}</Field>
    {!state.notificationsAllowed && <>
      <Field label={text.allow} onPress={() => void allowNotifications()}>{""}</Field>
      <Note>{text.notifications}</Note>
    </>}
  </Screen>;
}
