import { Canvas, CanvasText, Field, Row, Screen, Section, openURL } from "ink";
import { arrowOutward } from "ink/icons";
import { contributeText as text, sharePolicyText as policy } from "./connected-text";
import { PRIVACY_URL } from "./connected";
import { switched } from "./app-haptics";
import { Note, Notes } from "./note";

/** The width of a screen's content, as the About page and the front page draw it. */
const WIDTH = 596;

/**
 * The policy in short, shown every time before Share swipes turns on, as Google Play asks: what's sent and what never
 * is, then a plain choice. Nothing is collected until the user confirms here. Turning it off needs no screen.
 */
export function Disclosure({ onAgree, onDecline }: { onAgree: () => void; onDecline: () => void }) {
  return <Screen title={policy.title} subtitle={policy.subtitle}>
    <Points title={policy.sentTitle} points={policy.sent} />
    <Points title={policy.neverTitle} points={policy.never} />
    <Note>{policy.how}</Note>
    {/* Agreeing turns Share swipes on, so it locks in like a switch. Declining changes nothing, so it stays quiet. */}
    <Field label={policy.agree} onPress={() => {
      switched(true);
      onAgree();
    }}>{""}</Field>
    <Field label={policy.decline} onPress={onDecline}>{""}</Field>
    <Row title={policy.policy} endIcon={arrowOutward} onPress={() => void openURL(PRIVACY_URL)} />
  </Screen>;
}

/** What is sent, what never is, and how it is kept. */
export function WhatIsSent() {
  return <>
    <Points title={text.sentTitle} points={text.sent} />
    <Points title={text.neverTitle} points={text.never} />
    <Points title={text.howTitle} points={text.how} />
  </>;
}

function Points({ title, points }: { title: string; points: readonly string[] }) {
  return <Section title={title}>
    <Notes lines={points} />
  </Section>;
}

/** One line of the swipes' status: a label at the start and its value at the end, both in one colour. */
export function StatusLine({ label, value, colour }: { label: string; value: string; colour: string }) {
  const height = 48;
  return <Canvas width={WIDTH} height={height}>
    <CanvasText x={0} y={0} width={WIDTH} height={height} size={32} colour={colour} align="start">{label}</CanvasText>
    <CanvasText x={0} y={0} width={WIDTH} height={height} size={32} colour={colour} align="end" tabularNumbers>{value}</CanvasText>
  </Canvas>;
}
