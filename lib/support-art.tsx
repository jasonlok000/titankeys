import { useEffect, useRef, useState } from "react";
import { Canvas, CanvasPath, Rectangle } from "ink";
import { RoundedFill, RoundedOutline } from "./rounded";

type Point = readonly [number, number];
export const supportWidth = 594;
export const espresso = "#21130F";
const cream = "#FFF1DB";
const amber = "#FBB979";
const clamp = (n: number) => Math.min(1, Math.max(0, n));
const ease = (n: number) => 1 - (1 - clamp(n)) ** 3;

function oval(cx: number, cy: number, rx: number, ry: number, start = 0, end = 2 * Math.PI): Point[] {
  return Array.from({ length: 33 }, (_, i) => {
    const angle = start + (end - start) * i / 32;
    return [cx + rx * Math.cos(angle), cy + ry * Math.sin(angle)];
  });
}

function OvalFill({ x, y, rx, ry, colour }: { x: number; y: number; rx: number; ry: number; colour: string }) {
  const rows = Math.ceil(ry * 1.4) * 2;
  const height = ry * 2 / rows;
  return Array.from({ length: rows }, (_, i) => {
    const mid = (i + .5) * height - ry;
    const span = rx * Math.sqrt(1 - (mid / ry) ** 2);
    return <Rectangle key={i} x={x - span} y={y + i * height - ry} width={span * 2} height={height} fill={colour} />;
  });
}

function shade(dark: string, light: string, weight: number) {
  const a = Number.parseInt(dark, 16);
  const b = Number.parseInt(light, 16);
  const mixing = clamp(weight);
  const channel = (shift: number) => Math.round(((a >> shift) & 255) * (1 - mixing) + ((b >> shift) & 255) * mixing);
  return `#${((channel(16) << 16) | (channel(8) << 8) | channel(0)).toString(16).padStart(6, "0")}`;
}

function SceneLight({ height, warm = false }: { height: number; warm?: boolean }) {
  const line = (height - 4) / 54;
  return Array.from({ length: 54 }, (_, i) => {
    const y = 2 + i * line;
    const edge = Math.max(0, 27 - Math.min(y - 1, height - 1 - y - line));
    const inset = 29 - Math.sqrt(27 ** 2 - edge ** 2);
    const light = Math.sin((i + .5) * Math.PI / 54) ** 3;
    return <Rectangle key={i} x={inset} y={y} width={supportWidth - 2 * inset} height={line + .04}
      fill={warm ? shade("3A190F", "613116", light) : shade("21130F", "462A20", light)} />;
  });
}

function useProgress(duration: number, reduceMotion = false) {
  const [frame, setFrame] = useState(0);
  useEffect(() => {
    if (reduceMotion) return;
    const start = Date.now();
    const timer = setInterval(() => {
      const elapsed = Math.min(duration, Date.now() - start);
      setFrame(elapsed);
      if (elapsed === duration) clearInterval(timer);
    }, 34);
    return () => clearInterval(timer);
  }, [duration, reduceMotion]);
  return reduceMotion ? duration : frame;
}

/** A small glass demitasse on a ceramic saucer. The body and coffee have independent reflections. */
export function CoffeeCup({ amount, reduceMotion }: { amount: number; reduceMotion?: boolean }) {
  const [level, setLevel] = useState(amount);
  const current = useRef(amount);
  const [pouring, setPouring] = useState(false);
  useEffect(() => {
    if (reduceMotion || current.current === amount) {
      current.current = amount;
      setLevel(amount);
      setPouring(false);
      return;
    }
    const from = current.current;
    const start = Date.now();
    setPouring(amount > from);
    const timer = setInterval(() => {
      const progress = clamp((Date.now() - start) / 570);
      current.current = from + (amount - from) * ease(progress);
      setLevel(current.current);
      if (progress === 1) {
        clearInterval(timer);
        setPouring(false);
      }
    }, 34);
    return () => clearInterval(timer);
  }, [amount, reduceMotion]);

  const cx = 289;
  const cupTop = 84;
  const cupBottom = 218;
  const coffeeTop = cupBottom - 15 - 109 * clamp(level / .48);
  const radiusAt = (y: number) => 91 - (y - cupTop) * .20;
  const coffeeWidth = radiusAt(coffeeTop) - 9;
  return <Canvas width={supportWidth} height={258}>
    <SceneLight height={258} />
    <RoundedOutline x={1} y={1} width={592} height={256} radius={27} stroke="#68402C" strokeWidth={2} />
    <OvalFill x={cx + 5} y={226} rx={157} ry={16} colour="#100906" />
    <OvalFill x={cx} y={219} rx={128} ry={18} colour="#71452F" />
    <OvalFill x={cx} y={215} rx={121} ry={15} colour="#B77D51" />
    <OvalFill x={cx} y={213} rx={100} ry={11} colour="#5E3625" />
    <CanvasPath points={oval(373, 140, 39, 39, -1.5, 1.7)} stroke="#774A32" strokeWidth={17} />
    <CanvasPath points={oval(373, 140, 39, 39, -1.42, 1.68)} stroke="#E1A76E" strokeWidth={5} />
    {Array.from({ length: 54 }, (_, i) => {
      const y = cupTop + i * 2.4;
      const radius = radiusAt(y);
      const filled = y + 1.2 >= coffeeTop;
      const depth = clamp((y - coffeeTop) / Math.max(1, cupBottom - coffeeTop));
      return <Rectangle key={i} x={cx - radius} y={y} width={radius * 2} height={2.45}
        fill={filled ? shade("4E281D", "995334", 1 - depth) : shade("35211A", "4D3227", 1 - i / 54)} />;
    })}
    <OvalFill x={cx} y={cupBottom - 4} rx={63} ry={10} colour="#45271C" />
    <OvalFill x={cx} y={coffeeTop} rx={coffeeWidth} ry={12} colour="#BE7846" />
    <OvalFill x={cx} y={coffeeTop + 1} rx={coffeeWidth - 6} ry={8} colour="#8F4B2A" />
    <CanvasPath points={oval(cx, coffeeTop - 1, coffeeWidth - 3, 9, .2, 2.5)} stroke="#EEAD70" strokeWidth={3} />
    <CanvasPath points={[[cx - 91, cupTop], [cx - 76, 166], [cx - 64, 203], [cx - 53, 216],
      [cx + 52, 216], [cx + 64, 203], [cx + 76, 166], [cx + 91, cupTop]]} stroke="#DEAA85" strokeWidth={4} />
    <CanvasPath points={oval(cx, cupTop, 91, 13)} stroke={cream} strokeWidth={3} />
    <CanvasPath points={[[cx - 73, 110], [cx - 61, 164], [cx - 52, 189]]} stroke="#BF8A67" strokeWidth={4} />
    <CanvasPath points={[[cx + 66, 125], [cx + 56, 168], [cx + 48, 196]]} stroke="#8D5840" strokeWidth={3} />
    <CanvasPath points={oval(cx, 219, 126, 17, .12, 2.99)} stroke="#CB8C5C" strokeWidth={3} />
    {pouring && <CanvasPath points={[[cx + 26, 38], [cx + 14, 51], [cx + 6, 69], [cx, coffeeTop]]}
      stroke="#D99152" strokeWidth={5} />}
    {!pouring && <>
      <CanvasPath points={Array.from({ length: 24 }, (_, i) => [281 - 10 * Math.sin(i * Math.PI / 18), 63 - i * 1.75])}
        stroke="#A37155" strokeWidth={3} />
      <CanvasPath points={Array.from({ length: 24 }, (_, i) => [306 + 8 * Math.sin(i * Math.PI / 21), 61 - i * 1.45])}
        stroke="#BA8461" strokeWidth={2} />
    </>}
  </Canvas>;
}

export function OrangeGlass({ previous, total, reduceMotion }: { previous: number; total: number; reduceMotion?: boolean }) {
  const frame = useProgress(2080, reduceMotion);
  const arrival = ease(frame / 630);
  const squeeze = ease((frame - 620) / 240);
  const pour = ease((frame - 735) / 980);
  const lift = 8 * Math.sin(Math.PI * clamp((frame - 1745) / 335));
  const cx = 300;
  const top = 96 - lift;
  const bottom = 231 - lift;
  const cups = previous + (total - previous) * pour;
  const juiceTop = bottom - 10 - 117 * clamp(cups);
  const fruitX = 146 + 154 * arrival;
  const fruitY = 54 - 20 * Math.sin(arrival * Math.PI);
  const fruitRadius = 31 - 22 * squeeze;
  const roll = (arrival - 1) * 2 * Math.PI;
  const leaf = (angle: number, offset = 0): Point =>
    [fruitX + (fruitRadius + offset) * Math.cos(angle + roll), fruitY + (fruitRadius + offset) * Math.sin(angle + roll)];
  const glow = frame > 735;
  const over = total > 1 && frame > 850 && frame < 1970 && cups >= 1;
  return <Canvas width={supportWidth} height={270}>
    <RoundedFill x={1} y={1} width={592} height={268} radius={27} fill={glow ? "#3A190F" : "#2B140E"} />
    {glow && <SceneLight height={270} warm />}
    <RoundedOutline x={1} y={1} width={592} height={268} radius={27} stroke="#A65126" strokeWidth={2} />
    <OvalFill x={cx} y={241} rx={116} ry={13} colour="#261007" />
    {Array.from({ length: 54 }, (_, i) => {
      const y = top + i * 2.4;
      const r = 70 - i * .355;
      const filled = y + 1.2 >= juiceTop;
      const depth = clamp((y - juiceTop) / Math.max(1, bottom - juiceTop));
      return <Rectangle key={i} x={cx - r} y={y} width={r * 2} height={2.45}
        fill={filled ? shade("C25713", "FFB549", 1 - depth) : shade("53250F", "744125", 1 - i / 54)} />;
    })}
    <OvalFill x={cx} y={bottom - 3} rx={50} ry={9} colour={cups > 0 ? "#CC691B" : "#68331B"} />
    {cups > 0 && <OvalFill x={cx} y={juiceTop} rx={52 + 17 * clamp(cups)} ry={8} colour="#FFD47C" />}
    <CanvasPath points={[[230, top], [239, bottom - 27], [249, bottom], [351, bottom], [361, bottom - 27], [370, top]]}
      stroke="#FFD1A2" strokeWidth={4} />
    <CanvasPath points={oval(cx, top, 70, 12)} stroke={cream} strokeWidth={3} />
    <CanvasPath points={[[250, top + 20], [257, bottom - 22]]} stroke="#FFD3A4" strokeWidth={4} />
    <CanvasPath points={[[351, top + 18], [344, bottom - 23]]} stroke="#B75D2F" strokeWidth={3} />
    {frame < 875 && <>
      <OvalFill x={fruitX} y={fruitY} rx={fruitRadius} ry={fruitRadius} colour="#FF9D44" />
      <CanvasPath points={oval(fruitX, fruitY, fruitRadius * .86, fruitRadius * .86, 3.4 + roll, 5.2 + roll)}
        stroke="#FFD27A" strokeWidth={3} />
      <CanvasPath points={[leaf(-1.57), leaf(-1.36, 8), leaf(-1.14, 13)]} stroke="#BEBC67" strokeWidth={4} />
    </>}
    {frame >= 755 && frame < 1690 &&
      <CanvasPath points={[[cx, 66], [cx + 3, 84], [cx - 2, 104], [cx, Math.max(107, juiceTop)]]}
        stroke="#FFBC54" strokeWidth={7} />}
    {over && [-1, 1].map(side => {
      const arc = clamp((frame - 890) / 610);
      return <CanvasPath key={side} points={[[cx + side * 62, top + 6], [cx + side * (77 + 13 * arc), top - 19],
        [cx + side * (96 + 14 * arc), top - 14], [cx + side * (101 + 22 * arc), top + 37 * arc]]}
        stroke={amber} strokeWidth={4} />;
    })}
    {frame >= 1745 && [[207, 77], [394, 53], [405, 123]].map(([x, y], i) =>
      <CanvasPath key={i} points={[[x - 5, y], [x, y - 4], [x + 5, y], [x, y + 4], [x - 5, y]]}
        stroke={amber} strokeWidth={2} />)}
  </Canvas>;
}
