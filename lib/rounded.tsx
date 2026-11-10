import { CanvasPath, Rectangle } from "ink";

/**
 * Rounded boxes for an Ink canvas, which only has square rectangles. Use the radii in lib/radii.ts.
 *
 * A path draws above the canvas's text and icons, so a fill is made of rectangles, which draw in order like any other: a slice
 * for each row of pixels in the corners, and one rectangle for the middle. An outline is one path along the edge, so it never
 * covers what is inside.
 */
type Box = { x: number; y: number; width: number; height: number; radius: number };

/** How far in the curve starts on a row [down] pixels from the top or bottom of a corner of [radius]. */
function indent(radius: number, down: number) {
  const fromCentre = radius - down;
  return radius - Math.sqrt(Math.max(0, radius * radius - fromCentre * fromCentre));
}

const clamp = ({ width, height, radius }: Box) => Math.max(0, Math.min(radius, width / 2, height / 2));

/** A filled box with round corners. [onPress] gets the whole box, square corners included, so the hit area does not change. */
export function RoundedFill(props: Box & { fill: string; onPress?: () => void; keyPrefix?: string }) {
  const { x, y, width, height, fill, onPress, keyPrefix = "rounded" } = props;
  const radius = clamp(props);
  const rows = Math.ceil(radius);
  const slices = [];
  for (let row = 0; row < rows; row++) {
    const inset = indent(radius, row + .5);
    const sliceHeight = Math.min(1, radius - row);
    slices.push(<Rectangle key={`${keyPrefix}-top-${row}`} x={x + inset} y={y + row} width={width - 2 * inset} height={sliceHeight} fill={fill} />);
    slices.push(<Rectangle key={`${keyPrefix}-bottom-${row}`} x={x + inset} y={y + height - row - sliceHeight} width={width - 2 * inset}
      height={sliceHeight} fill={fill} />);
  }
  return [
    ...slices,
    <Rectangle key={`${keyPrefix}-middle`} x={x} y={y + radius} width={width} height={height - 2 * radius} fill={fill} />,
    ...(onPress ? [<Rectangle key={`${keyPrefix}-press`} x={x} y={y} width={width} height={height} fill="#00000000" onPress={onPress} />] : []),
  ];
}

/** The points along the edge of a box with round corners, as a closed line. */
export function roundedOutline(box: Box, steps = 6): (readonly [number, number])[] {
  const { x, y, width, height } = box;
  const radius = clamp(box);
  const corners: [cx: number, cy: number, start: number][] = [
    [x + width - radius, y + radius, -Math.PI / 2],
    [x + width - radius, y + height - radius, 0],
    [x + radius, y + height - radius, Math.PI / 2],
    [x + radius, y + radius, Math.PI],
  ];
  const points: (readonly [number, number])[] = [];
  for (const [cx, cy, start] of corners)
    for (let step = 0; step <= steps; step++) {
      const angle = start + step / steps * Math.PI / 2;
      points.push([cx + radius * Math.cos(angle), cy + radius * Math.sin(angle)] as const);
    }
  points.push(points[0]);
  return points;
}

/** An outlined box with round corners. */
export function RoundedOutline(props: Box & { stroke: string; strokeWidth: number }) {
  const { stroke, strokeWidth, ...box } = props;
  return <CanvasPath points={roundedOutline(box)} stroke={stroke} strokeWidth={strokeWidth} />;
}
