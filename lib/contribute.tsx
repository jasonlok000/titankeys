import type { ReactNode } from "react";
import { Canvas, CanvasText, Field, Row, Screen, Section, Text, openURL } from "ink";
import { arrowOutward } from "ink/icons";
import { contributeText as text } from "./connected-text";
import { PRIVACY_URL } from "./connected";

/** Long text on these pages uses the subtitle's size, as the rest of Settings does, so that it fits the square screen. */
export const SMALL = 30;
/** The width of a screen's content, as the About page and the front page draw it. */
const WIDTH = 596;

/**
 * The full disclosure for data contributions, as Google Play asks: what is sent, what is never sent, and how it is
 * protected, with "I agree" and "No thanks". Nothing is collected until the user agrees here.
 */
export function Disclosure({ onAgree, onDecline, children }: { onAgree: () => void; onDecline: () => void; children?: ReactNode }) {
  return <Screen title={text.title} subtitle={text.subtitle}>
    <Field label={text.agree} onPress={onAgree}>{""}</Field>
    <Field label={text.decline} onPress={onDecline}>{""}</Field>
    <WhatIsSent />
    <Row title={text.policy} endIcon={arrowOutward} onPress={() => void openURL(PRIVACY_URL)} />
    {children}
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
    {points.map(point => <Text key={point} size={SMALL}>{`• ${point}`}</Text>)}
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
