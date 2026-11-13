import { Canvas, CanvasIcon, CanvasPath, CanvasText, type IconAsset } from "ink";
import { arrowBack, backspace, circle, keyboardReturn, shift, spaceBar, square } from "ink/icons";
import type { Point } from "./atlas";
import { CREAM, MUTED, useAccent } from "./brand";
import { RADIUS } from "./radii";
import { RoundedOutline } from "./rounded";

type Label = { text: string } | { icon: IconAsset };

const texts = (labels: string) => labels.split(" ").map(text => ({ text }));
const rows: Label[][] = [
  texts("Q W E R T Y U I O P"),
  [...texts("A S D F G H J K L"), { icon: backspace }],
  [...texts("alt Z X C V B N M sym"), { icon: keyboardReturn }],
];
const bottom: [Label, number][] = [
  [{ icon: shift }, 1.2], [{ icon: circle }, 1.2], [{ icon: arrowBack }, 1.2], [{ icon: spaceBar }, 2.8],
  [{ icon: square }, 1.2], [{ text: "fn" }, 1.2], [{ icon: shift }, 1.2],
];
// Fills the screen's content width, with roughly square keys.
const width = 594;
const height = 198;
const key = width / 10;
const row = height / 4;
const icon = 22;

/**
 * The Titan 2 keyboard face, with the latest touchpad trail drawn over it. A faint box shows the part that the touchpad
 * feels: its sensor stops at the middle of the outer columns, of the top row and of the bottom row.
 */
export function KeyboardDiagram({ trail }: { trail: Point[] }) {
  const line = MUTED;
  const label = MUTED;
  const ink = useAccent();
  const keys: { label: Label; x: number; y: number; width: number }[] = rows.flatMap((labels, r) =>
    labels.map((label, c) => ({ label, x: c * key, y: r * row, width: key })));
  let left = 0;
  for (const [label, span] of bottom) {
    keys.push({ label, x: left, y: 3 * row, width: span * key });
    left += span * key;
  }
  return <Canvas width={width + 2} height={height + 2}>
    {keys.map(({ x, y, width: span }, index) =>
      <RoundedOutline key={`key-${index}`} x={x + 1} y={y + 1} width={span} height={row} radius={RADIUS.small} stroke={line} strokeWidth={1} />)}
    {keys.map(({ label: name, x, y, width: span }, index) => "text" in name
      ? <CanvasText key={`label-${index}`} x={x + 1} y={y + 1} width={span} height={row} size={18} colour={label} align="center">
        {name.text}
      </CanvasText>
      : <CanvasIcon key={`label-${index}`} x={x + 1 + (span - icon) / 2} y={y + 1 + (row - icon) / 2} size={icon} name={name.icon} colour={label} />)}
    <RoundedOutline x={1 + width * .05} y={1 + height * .5 / 4} width={width * .9} height={height * (3.54 - .5) / 4} radius={RADIUS.medium}
      stroke={MUTED} strokeWidth={1} />
    {pieces(trail).map((piece, index) => <CanvasPath key={`trail-${index}`}
      points={piece.points.map(([x, y]) => [1 + x * width, 1 + y * height] as const)} stroke={ink} strokeWidth={4 * piece.thickness} />)}
  </Canvas>;
}

/**
 * Splits the trail where its thickness changes, so it is thicker where the touch is larger than usual. Thickness is
 * rounded to eighths, which keeps the pieces few. Each piece starts at the point where the one before it ends.
 */
function pieces(trail: Point[]) {
  const result: { thickness: number; points: Point[] }[] = [];
  for (const point of trail) {
    const thickness = Math.round((point[2] ?? 1) * 8) / 8;
    const last = result.at(-1);
    if (last?.thickness === thickness) last.points.push(point);
    else result.push({ thickness, points: last ? [last.points.at(-1)!, point] : [point] });
  }
  return result;
}

/** How a key cap is drawn: the size of its label and how tall it is, in canvas units. */
const CAP = { height: 44, letter: 44, padding: 14, text: 22, plus: 30 };

/** How wide [KeyCaps] draws [keys]. */
export function keyCapsWidth(keys: readonly string[]) {
  return keys.reduce((width, key, index) => width + capWidth(key) + (index > 0 ? CAP.plus : 0), 0);
}

function capWidth(key: string) {
  return key.length === 1 ? CAP.letter : CAP.padding * 2 + key.length * CAP.text * .6;
}

/**
 * Keys to press together, drawn as caps like the keys in the drawings, with a + between them: "Alt + G". For a canvas, from
 * [x], centred on [y] + [height] / 2.
 */
export function KeyCaps({ keys, x, y, height, keyPrefix = "cap" }: {
  keys: readonly string[];
  x: number;
  y: number;
  height: number;
  keyPrefix?: string;
}) {
  const top = y + (height - CAP.height) / 2;
  const parts = [];
  let left = x;
  for (const [index, key] of keys.entries()) {
    if (index > 0) {
      parts.push(<CanvasText key={`${keyPrefix}-plus-${index}`} x={left} y={top} width={CAP.plus} height={CAP.height} size={CAP.text}
        colour={MUTED} align="center">+</CanvasText>);
      left += CAP.plus;
    }
    const width = capWidth(key);
    parts.push(<RoundedOutline key={`${keyPrefix}-key-${index}`} x={left} y={top} width={width} height={CAP.height}
      radius={RADIUS.small} stroke={MUTED} strokeWidth={1.5} />);
    parts.push(<CanvasText key={`${keyPrefix}-label-${index}`} x={left} y={top} width={width} height={CAP.height} size={CAP.text}
      colour={CREAM} align="center">{key}</CanvasText>);
    left += width;
  }
  return parts;
}
