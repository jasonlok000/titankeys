import { useEffect, useRef, useState } from "react";
import { Canvas, CanvasPath, CanvasText, Rectangle } from "ink";
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

/** A takeaway cup, drawn from the same rounded key shapes and swipe colours as the guide. */
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

  const cx = 298;
  const height = 221;
  const raised = "#342C28";
  const edge = "#574840";
  const gauge = { x: 390, y: 56, width: 22, height: 119, radius: 10 };
  const liquid = 99 * clamp(level);
  const keys = [["Q", 72, 78], ["W", 121, 78], ["E", 170, 78], ["A", 84, 125], ["S", 133, 125],
    ["O", 427, 86], ["P", 476, 86], ["L", 445, 133], ["Del", 494, 133]] as const;
  return <Canvas width={supportWidth} height={height}>
    <RoundedFill x={1} y={1} width={592} height={219} radius={26} fill="#1A1512" />
    <RoundedOutline x={1} y={1} width={592} height={219} radius={26} stroke="#594137" strokeWidth={2} />
    {keys.map(([label, x, y]) => <RoundedFill key={label} x={x} y={y + 4} width={43} height={36} radius={9} fill="#100C0A" />)}
    {keys.map(([label, x, y]) => <RoundedFill key={label} x={x} y={y} width={43} height={35} radius={9} fill={raised} />)}
    {keys.map(([label, x, y]) => <CanvasText key={label} x={x} y={y} width={43} height={35} align="center" size={17} colour="#AB9183">{label}</CanvasText>)}
    <OvalFill x={cx} y={194} rx={92} ry={12} colour="#100B08" />
    {Array.from({ length: 63 }, (_, i) => {
      const y = 56 + i * 2.15;
      const half = 70 - i * .39;
      const sleeve = y >= 92 && y < 153;
      const fill = sleeve ? shade("AE522E", "DE8251", 1 - (y - 92) / 61) : shade("C29373", "FFE8C5", 1 - i / 90);
      return <Rectangle key={i} x={cx - half} y={y} width={half * 2} height={2.18} fill={fill} />;
    })}
    <OvalFill x={cx} y={190} rx={46} ry={8} colour="#D1A281" />
    <RoundedFill x={217} y={33} width={162} height={29} radius={12} fill="#41312A" />
    <RoundedFill x={230} y={27} width={136} height={20} radius={10} fill="#503D33" />
    <RoundedOutline x={217} y={33} width={162} height={29} radius={12} stroke="#A48776" strokeWidth={2} />
    <CanvasPath points={[[234, 42], [353, 42]]} stroke="#6C5448" strokeWidth={3} />
    <CanvasPath points={[[228, 62], [253, 188], [266, 196], [329, 196], [343, 188], [367, 62]]}
      stroke="#F9DAB4" strokeWidth={2.5} />
    <RoundedFill x={272} y={99} width={52} height={45} radius={13} fill="#3A241A" />
    <RoundedOutline x={272} y={99} width={52} height={45} radius={13} stroke="#F3B27F" strokeWidth={2} />
    <CanvasPath points={[[284, 118], [294, 128], [315, 110]]} stroke="#FFBA7C" strokeWidth={4} />
    <RoundedFill {...gauge} fill="#2F2019" />
    <RoundedOutline {...gauge} stroke={edge} strokeWidth={2} />
    <Rectangle x={396} y={165 - liquid} width={10} height={liquid} fill={pouring ? "#FFBC81" : "#DF8354"} />
    {[72, 99, 126, 153].map(y => <CanvasPath key={y} points={[[415, y], [422, y]]} stroke="#836354" strokeWidth={2} />)}
    <CanvasPath points={[[89, 151], [111, 136], [138, 134], [163, 111], [191, 101], [211, 77], [205, 57], [184, 50], [174, 31]]}
      stroke="#683F2C" strokeWidth={8} />
    <CanvasPath points={[[89, 151], [111, 136], [138, 134], [163, 111], [191, 101], [211, 77], [205, 57], [184, 50], [174, 31]]}
      stroke="#DD8259" strokeWidth={2.5} />
    <CanvasPath points={[[91, 150], [92, 150]]} stroke="#FFC590" strokeWidth={7} />
  </Canvas>;
}

export function OrangeGlass({ previous, total, reduceMotion }: { previous: number; total: number; reduceMotion?: boolean }) {
  const frame = useProgress(2450, reduceMotion);
  const arrival = ease(frame / 630);
  const squeeze = ease((frame - 620) / 240);
  const pour = ease((frame - 735) / 980);
  const lift = 8 * Math.sin(Math.PI * clamp((frame - 2000) / 450));
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
  const spillStart = Math.max(1, previous);
  const over = total > spillStart ? clamp((cups - spillStart) / Math.max(.02, total - spillStart)) : 0;
  const stream = clamp(over * 1.65);
  const pool = clamp((over - .48) / .52);
  return <Canvas width={supportWidth} height={270}>
    <RoundedFill x={1} y={1} width={592} height={268} radius={27} fill={glow ? "#3A190F" : "#2B140E"} />
    {glow && <SceneLight height={270} warm />}
    <RoundedOutline x={1} y={1} width={592} height={268} radius={27} stroke="#A65126" strokeWidth={2} />
    <OvalFill x={cx} y={241} rx={116} ry={13} colour="#261007" />
    {pool > 0 && <OvalFill x={cx + 24} y={244} rx={20 + 104 * pool} ry={3 + 8 * pool} colour="#C96525" />}
    {pool > 0 && <OvalFill x={cx + 31} y={242} rx={15 + 67 * pool} ry={2 + 4 * pool} colour="#FFA853" />}
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
    {over > 0 && [-1, 1].map(side => {
      const points: Point[] = Array.from({ length: 17 }, (_, i) => {
        const part = i / 16 * stream;
        return [cx + side * (64 + 29 * Math.sin(part * 2.4)), top + 5 + 141 * part ** 2 - 22 * Math.sin(part * Math.PI)];
      });
      return <CanvasPath key={side} points={points} stroke={amber} strokeWidth={side === 1 ? 6 : 3} />;
    })}
    {frame >= 2000 && [[207, 77], [394, 53], [405, 123]].map(([x, y], i) =>
      <CanvasPath key={i} points={[[x - 5, y], [x, y - 4], [x + 5, y], [x, y + 4], [x - 5, y]]}
        stroke={amber} strokeWidth={2} />)}
  </Canvas>;
}
