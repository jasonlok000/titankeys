import { useEffect, useState } from "react";
import { Canvas, CanvasPath, CanvasText, Rectangle, Stack } from "ink";
import { CREAM, DIVIDER, useAccent } from "./brand";
import { Note } from "./note";
import { cupCents, type Receipt } from "./support";

const width = 594;
const height = 270;
const duration = 2_000;
const limit = (value: number) => Math.min(1, Math.max(0, value));

export function JuiceGlass({ receipt, reduceMotion }: { receipt: Receipt; reduceMotion?: boolean }) {
  const [elapsed, setElapsed] = useState(0);
  const accent = useAccent();
  useEffect(() => {
    if (reduceMotion) return;
    const start = Date.now();
    const timer = setInterval(() => {
      const next = Math.min(duration, Date.now() - start);
      setElapsed(next);
      if (next === duration) clearInterval(timer);
    }, 32);
    return () => clearInterval(timer);
  }, [reduceMotion]);

  const frame = reduceMotion ? duration : elapsed;
  const bounce = 1 - limit(frame / 550);
  const pour = limit((frame - 580) / 930);
  const cups = (receipt.previousCents + pour * (receipt.totalCents - receipt.previousCents)) / cupCents;
  const fillHeight = Math.round(102 * Math.min(cups, 1));
  const juiceTop = 209 - fillHeight;
  const spill = cups > 1 && frame < 1810 && frame > 650;
  const fruit = frame < 700;
  const size = 36 - 15 * limit((frame - 540) / 180);
  const fruitX = width / 2 - 110 * bounce;
  const fruitY = 61 - 45 * Math.abs(Math.sin(bounce * Math.PI)) * bounce;
  const cheers = limit((frame - 1530) / 340);
  const lift = 9 * Math.sin(cheers * Math.PI);
  return <Stack gap={4} align="center">
    <Canvas width={width} height={height}>
      <Rectangle x={0} y={0} width={width} height={height} fill={DIVIDER} />
      {fillHeight > 0 && <Rectangle x={258} y={juiceTop - lift} width={78} height={fillHeight} fill={accent} />}
      <CanvasPath points={[[243, 103 - lift], [255, 211 - lift], [339, 211 - lift], [351, 103 - lift]]} stroke={CREAM} strokeWidth={2} />
      <CanvasPath points={[[243, 103 - lift], [351, 103 - lift]]} stroke={CREAM} strokeWidth={2} />
      {fruit && <CanvasPath points={[[fruitX, fruitY], [fruitX + 1, fruitY]]} stroke={accent} strokeWidth={size} />}
      {fruit && <CanvasPath points={[[fruitX, fruitY - size / 2], [fruitX + 10, fruitY - size / 2 - 7]]} stroke={CREAM} strokeWidth={3} />}
      {frame >= 570 && frame < 1470 &&
        <CanvasPath points={[[width / 2, 72], [width / 2, Math.max(88, juiceTop)]]} stroke={accent} strokeWidth={5} />}
      {spill && <CanvasPath points={[[331, 108], [357, 90], [371, 107], [379, 132]]} stroke={accent} strokeWidth={4} />}
      {spill && <CanvasPath points={[[253, 108], [232, 93], [219, 103]]} stroke={accent} strokeWidth={3} />}
      <CanvasText x={0} y={222} width={width} height={38} align="center" size={24} colour={CREAM}>
        {`${cups.toFixed(1)} ${cups.toFixed(1) === "1.0" ? "cup" : "cups"}`}
      </CanvasText>
    </Canvas>
    <Note>An orange bounces in, squeezes itself into a glass, and gives a little cheers.</Note>
  </Stack>;
}
