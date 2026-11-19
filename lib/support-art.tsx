import { type ReactNode, useEffect, useState } from "react";
import { Canvas, CanvasPath, CanvasText, Rectangle } from "ink";
import { CREAM, MUTED, useAccent } from "./brand";
import { RoundedFill, RoundedOutline } from "./rounded";

/** The width of Support's drawings: a screen's content width, as the guide's drawings use. */
export const supportWidth = 594;
const SURFACE = "#110E0C";

/**
 * A takeaway cup in the guide's drawn style: a cream outline and lid, an orange band for the sleeve. Parts for a canvas, at
 * [x], [y], [scale] (1 is 24 x 30 units).
 */
export function cupParts(x: number, y: number, scale: number, key: string, band: string, stroke = CREAM) {
  const at = (px: number, py: number) => [x + px * scale, y + py * scale] as const;
  const width = 1.4 * scale / 1.6;
  return [
    <RoundedFill key={`${key}-band`} x={x + 5.9 * scale} y={y + 15 * scale} width={12.2 * scale} height={6 * scale} radius={scale} fill={band} />,
    <CanvasPath key={`${key}-body`} points={[at(5, 9), at(19, 9), at(17.4, 29), at(6.6, 29), at(5, 9)]} stroke={stroke} strokeWidth={width} />,
    <CanvasPath key={`${key}-lid`} points={[at(3.5, 6.2), at(20.5, 6.2), at(20.5, 9), at(3.5, 9), at(3.5, 6.2)]} stroke={stroke} strokeWidth={width} />,
    <CanvasPath key={`${key}-top`} points={[at(7, 6.2), at(8, 4), at(16, 4), at(17, 6.2)]} stroke={stroke} strokeWidth={width} />,
  ];
}

/**
 * Settings' Support TitanKeys entry, directly above About (owner-approved support flow v4): a card, not a plain row, with the
 * cup, the title, one line and a chevron. The whole card opens the support page.
 */
export function SupportCard({ title, line, onPress }: { title: string; line: string; onPress: () => void }) {
  const height = 128;
  const accent = useAccent();
  return <Canvas width={supportWidth} height={height}>
    <RoundedFill x={1} y={1} width={supportWidth - 2} height={height - 2} radius={28} fill={SURFACE} onPress={onPress} />
    <RoundedOutline x={1} y={1} width={supportWidth - 2} height={height - 2} radius={28} stroke={accent} strokeWidth={1.5} />
    {cupParts(26, 26, 2.6, "card", accent)}
    <CanvasText x={112} y={22} width={410} height={36} size={30} colour={CREAM}>{title}</CanvasText>
    <CanvasText x={112} y={62} width={420} height={48} size={22} colour={MUTED}>{line}</CanvasText>
    <CanvasText x={supportWidth - 52} y={0} width={30} height={height} size={36} colour={accent} align="center">›</CanvasText>
  </Canvas>;
}

const ease = (t: number) => 1 - (1 - Math.min(1, Math.max(0, t))) ** 3;

/** Mixes two #RRGGBB colours. */
function mix(a: string, b: string, t: number) {
  const x = Number.parseInt(a.slice(1), 16), y = Number.parseInt(b.slice(1), 16);
  const channel = (shift: number) => Math.round(((x >> shift) & 255) * (1 - t) + ((y >> shift) & 255) * t);
  return `#${((channel(16) << 16) | (channel(8) << 8) | channel(0)).toString(16).padStart(6, "0")}`;
}

/**
 * The thank-you's warm rise (owner-approved support flow v4): an orange gradient comes up from the bottom to a height that
 * follows the supporter's total in cups (one cup fills it), with a gentle wave on top; past a cup it floods. About 1.8 s, or
 * already there with reduced motion. [done] runs when it settles, so the words can come in on top.
 */
export function RisingThanks({ cups, width, height, reduceMotion, done, children }: {
  cups: number; width: number; height: number; reduceMotion?: boolean; done?: () => void; children?: ReactNode;
}) {
  const accent = useAccent();
  const [t, setT] = useState(reduceMotion ? 1 : 0);
  useEffect(() => {
    if (reduceMotion) { done?.(); return; }
    const start = Date.now();
    const timer = setInterval(() => {
      const next = Math.min(1, (Date.now() - start) / 1800);
      setT(next);
      if (next >= 1) { clearInterval(timer); done?.(); }
    }, 33);
    return () => clearInterval(timer);
  }, [reduceMotion]);
  const target = Math.min(1.08, Math.max(.3, cups)) * height;
  const level = ease(t) * target;
  const top = height - level;
  const bands = 40;
  const phase = (reduceMotion ? 0 : t * 9);
  const wave = Array.from({ length: 25 }, (_, i) => {
    const x = i * width / 24;
    return [x, Math.max(0, top) + Math.sin(i / 3 + phase) * 7] as const;
  });
  return <Canvas width={width} height={height}>
    <Rectangle x={0} y={0} width={width} height={height} fill="#000000" />
    {level > 0 && Array.from({ length: bands }, (_, i) => {
      const y = top + i * level / bands;
      // Pale at the surface, deep orange at the bottom.
      return <Rectangle key={`band-${i}`} x={0} y={y} width={width} height={level / bands + 1} fill={mix("#FFD9AE", accent, i / (bands - 1))} />;
    })}
    {level > 0 && top > -8 && <CanvasPath points={wave} stroke="#FFD9AE" strokeWidth={14} />}
    {children}
  </Canvas>;
}
