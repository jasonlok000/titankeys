import { useState } from "react";
import { Field, Row, Screen, Section, Text, Toggle, back, openURL, useAction, useSnapshot } from "ink";
import { arrowOutward, chevronRight } from "ink/icons";
import { MUTED, ORANGE } from "../../lib/brand";
import { connectedState, copyContributorId, declineContributions, deleteQueued, kilobytes, PRIVACY_URL, setContribute } from "../../lib/connected";
import { contributeText as text, swipes } from "../../lib/connected-text";
import { Disclosure, SMALL, StatusLine } from "../../lib/contribute";

export const contributeLabel = (on: boolean) => on ? "On" : "Off";

export default function Contribute() {
  const result = useSnapshot(connectedState);
  const save = useAction(setContribute);
  const [copied, setCopied] = useState(false);
  if (result.status !== "ready") return <Screen title={text.title} />;
  const state = result.data;
  // The contributor ID and deletion stay here after the user stops, so that what was sent can still be deleted.
  const idSection = state.contributorId !== "" && <Section title={text.idTitle}>
    <Text size={SMALL}>{state.contributorId}</Text>
    <Field label={text.copy} onPress={() => void copyContributorId().then(() => setCopied(true))}>{copied ? text.copied : ""}</Field>
    <Text size={SMALL}>{text.idNote}</Text>
  </Section>;
  if (!state.contribute) return <Disclosure onAgree={() => save.run(true)} onDecline={() => void declineContributions().then(back)}>
    {state.contributorId !== "" && <Field label={text.deleteAll} href="/settings/contribute-delete" />}
    {idSection}
  </Disclosure>;
  const notes = [!state.serverReady && text.notReady, state.deletionPending && text.deletionWaiting].filter(Boolean).join(" ");
  return <Screen title={text.title} subtitle={text.onSubtitle} note={notes || undefined}>
    <Section title={text.statusTitle}>
      {/* One accent for what was sent, muted for what still waits. */}
      <StatusLine label={text.sentLine} value={swipes(state.sent)} colour={ORANGE} />
      <StatusLine label={text.waitingLine} value={`${swipes(state.queued)} · ${kilobytes(state.queuedBytes)}`} colour={MUTED} />
      <Toggle label={text.toggle} value={state.contribute} onChange={on => save.run(on)} />
    </Section>
    <Section title={text.deleteTitle}>
      <Field label={text.deleteWaiting} disabled={state.queued === 0} onPress={() => void deleteQueued()}>{""}</Field>
      <Field label={text.deleteAll} href="/settings/contribute-delete" />
    </Section>
    {idSection}
    <Row title={text.about} endIcon={chevronRight} href="/settings/contribute-about" />
    <Row title={text.policy} endIcon={arrowOutward} onPress={() => void openURL(PRIVACY_URL)} />
  </Screen>;
}
