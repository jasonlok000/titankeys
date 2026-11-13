import { Canvas, CanvasPath, CanvasText, Stack, Text } from "ink";
import { MUTED, useAccent } from "./brand";
import { RADIUS } from "./radii";
import { RoundedOutline } from "./rounded";

/**
 * The three levels of text in Settings:
 * - rows (Field, Row, Toggle labels): cream, 45 pixels. Only what you can press looks like this.
 * - notes: explanations, steps and fine print. Muted cream, 39 pixels (14 sp on the Titan 2 Elite), the same size as a
 *   page's subtitle and a switch's description in theme.ts. Never cream at row size, so nothing looks pressable that isn't.
 * - section labels and values: orange, from the theme.
 */
export const NOTE_SIZE = 26;

/** An explanation, a step or fine print: smaller than a row and muted, so it never looks like something to press. */
export function Note({ children, align }: { children: string; align?: "start" | "center" }) {
  return <Text size={NOTE_SIZE} tone="muted" align={align}>{children}</Text>;
}

/** Bulleted notes, such as steps or what's sent. */
export function Notes({ lines }: { lines: readonly string[] }) {
  return <>{lines.map(line => <Note key={line}>{`• ${line}`}</Note>)}</>;
}

/** The width of a screen's content, as the About page and the front page draw it. */
const WIDTH = 596;
const ROW_HEIGHT = 48;
const CAP = 44;
const CHEVRON = 18;

/**
 * A setting whose value is a key or a character, such as the snippet prefix: the label, then the value in a small key
 * cap, so a lone mark like ; reads as a key and not as a stray dot. Pressing anywhere on the row opens [onPress].
 */
export function KeyCapField({ label, value, onPress }: { label: string; value: string; onPress: () => void }) {
  const accent = useAccent();
  const capX = WIDTH - CHEVRON - 16 - CAP;
  const top = (ROW_HEIGHT - CAP) / 2;
  const centre = ROW_HEIGHT / 2;
  return <Stack onPress={onPress}>
    <Canvas width={WIDTH} height={ROW_HEIGHT}>
      <CanvasText x={0} y={0} width={capX - 16} height={ROW_HEIGHT} size={30} align="start">{label}</CanvasText>
      <RoundedOutline x={capX} y={top} width={CAP} height={CAP} radius={RADIUS.small} stroke={MUTED} strokeWidth={1.5} />
      <CanvasText x={capX} y={top} width={CAP} height={CAP} size={30} colour={accent} align="center">{value}</CanvasText>
      {/* The same chevron as an inline field that opens a page. */}
      <CanvasPath points={[[WIDTH - 7, centre - 9], [WIDTH, centre], [WIDTH - 7, centre + 9]]} stroke={MUTED} strokeWidth={2} />
    </Canvas>
  </Stack>;
}
