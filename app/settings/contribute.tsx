import { useState } from "react";
import { Field, Row, Screen, Section, Text, Toggle, back, openURL, useAction, useSnapshot } from "ink";
import { arrowOutward } from "ink/icons";
import { connectedState, copyContributorId, declineContributions, deleteQueued, kilobytes, PRIVACY_URL, setContribute } from "../../lib/connected";
import { contributeText as text, swipes } from "../../lib/connected-text";
import { Disclosure } from "../../lib/contribute";

export const contributeLabel = (on: boolean) => on ? "On" : "Off";

export default function Contribute() {
  const result = useSnapshot(connectedState);
  const save = useAction(setContribute);
  const [copied, setCopied] = useState(false);
  if (result.status !== "ready") return <Screen title={text.title} />;
  const state = result.data;
  // The contributor ID and deletion stay here after the user stops, so that what was sent can still be deleted.
  const idSection = state.contributorId !== "" && <Section title={text.idTitle}>
    <Text>{state.contributorId}</Text>
    <Field label={text.copy} onPress={() => void copyContributorId().then(() => setCopied(true))}>{copied ? text.copied : ""}</Field>
    <Text>{text.idNote}</Text>
  </Section>;
  if (!state.contribute) return <Disclosure onAgree={() => save.run(true)} onDecline={() => void declineContributions().then(back)}>
    {idSection}
    {state.contributorId !== "" && <Field label={text.deleteAll} href="/settings/contribute-delete" />}
  </Disclosure>;
  const notes = [!state.serverReady && text.notReady, state.deletionPending && text.deletionWaiting].filter(Boolean).join(" ");
  return <Screen title={text.title} note={notes || undefined}>
    <Section title={text.title}>
      <Toggle label={text.toggle} value={state.contribute} onChange={on => save.run(on)} />
    </Section>
    <Section title={text.waitingTitle}>
      <Field label={text.waiting}>{`${swipes(state.queued)}, ${kilobytes(state.queuedBytes)}`}</Field>
      <Field label={text.deleteWaiting} disabled={state.queued === 0} onPress={() => void deleteQueued()}>{""}</Field>
    </Section>
    <Section title={text.sentTitleOn}>
      <Field label={text.sentCount}>{swipes(state.sent)}</Field>
      <Field label={text.deleteAll} href="/settings/contribute-delete" />
    </Section>
    {idSection}
    <Section title={text.sentTitle}>
      {text.sent.map(point => <Text key={point}>{`• ${point}`}</Text>)}
    </Section>
    <Row title={text.policy} endIcon={arrowOutward} onPress={() => void openURL(PRIVACY_URL)} />
  </Screen>;
}
