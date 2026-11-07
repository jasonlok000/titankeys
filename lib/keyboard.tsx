import { Canvas, CanvasIcon, CanvasPath, CanvasText, type IconAsset, Rectangle, useColourScheme } from "ink";
import { arrowBack, backspace, circle, gridView, keyboardReturn, shift, spaceBar } from "ink/icons";
import type { Point } from "./atlas";

type Label = { text: string } | { icon: IconAsset };

const texts = (labels: string) => labels.split(" ").map(text => ({ text }));
const rows: Label[][] = [
  texts("Q W E R T Y U I O P"),
  [...texts("A S D F G H J K L"), { icon: backspace }],
  [...texts("alt Z X C V B N M sym"), { icon: keyboardReturn }],
];
const bottom: [Label, number][] = [
  [{ icon: shift }, 1.2], [{ icon: circle }, 1.2], [{ icon: arrowBack }, 1.2], [{ icon: spaceBar }, 2.8],
  [{ icon: gridView }, 1.2], [{ text: "fn" }, 1.2], [{ icon: shift }, 1.2],
];
// Fills the screen's content width, with roughly square keys.
const width = 594;
const height = 198;
const key = width / 10;
const row = height / 4;
const icon = 22;

/** The Titan 2 keyboard face, with the latest touchpad trail drawn over it. */
export function KeyboardDiagram({ trail }: { trail: Point[] }) {
  const dark = useColourScheme() === "dark";
  const line = dark ? "#444444" : "#BBBBBB";
  const label = dark ? "#AAAAAA" : "#555555";
  const ink = dark ? "#FFFFFF" : "#000000";
  const keys: { label: Label; x: number; y: number; width: number }[] = rows.flatMap((labels, r) =>
    labels.map((label, c) => ({ label, x: c * key, y: r * row, width: key })));
  let left = 0;
  for (const [label, span] of bottom) {
    keys.push({ label, x: left, y: 3 * row, width: span * key });
    left += span * key;
  }
  return <Canvas width={width + 2} height={height + 2}>
    {keys.map(({ x, y, width: span }, index) =>
      <Rectangle key={`key-${index}`} x={x + 1} y={y + 1} width={span} height={row} stroke={line} strokeWidth={1} />)}
    {keys.map(({ label: name, x, y, width: span }, index) => "text" in name
      ? <CanvasText key={`label-${index}`} x={x + 1} y={y + 1} width={span} height={row} size={18} colour={label} align="center">
        {name.text}
      </CanvasText>
      : <CanvasIcon key={`label-${index}`} x={x + 1 + (span - icon) / 2} y={y + 1 + (row - icon) / 2} size={icon} name={name.icon} colour={label} />)}
    {trail.length > 0 && <CanvasPath points={trail.map(([x, y]) => [1 + x * width, 1 + y * height] as const)}
      stroke={ink} strokeWidth={4} />}
  </Canvas>;
}
