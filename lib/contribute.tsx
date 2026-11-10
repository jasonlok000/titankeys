import { Field, Row, Screen, Section, Text, openURL } from "ink";
import { arrowOutward } from "ink/icons";
import { contributeText as text } from "./connected-text";
import { PRIVACY_URL } from "./connected";

/**
 * The full disclosure for data contributions, as Google Play asks: what is sent, what is never sent, and how it is
 * protected, with "I agree" and "No thanks". Nothing is collected until the user agrees here.
 */
export function Disclosure({ onAgree, onDecline }: { onAgree: () => void; onDecline: () => void }) {
  return <Screen title={text.title} subtitle={text.subtitle}>
    <Points title={text.sentTitle} points={text.sent} />
    <Points title={text.neverTitle} points={text.never} />
    <Points title={text.howTitle} points={text.how} />
    <Row title={text.policy} endIcon={arrowOutward} onPress={() => void openURL(PRIVACY_URL)} />
    <Field label={text.agree} onPress={onAgree}>{""}</Field>
    <Field label={text.decline} onPress={onDecline}>{""}</Field>
  </Screen>;
}

function Points({ title, points }: { title: string; points: readonly string[] }) {
  return <Section title={title}>
    {points.map(point => <Text key={point}>{`• ${point}`}</Text>)}
  </Section>;
}
