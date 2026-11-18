import { useState } from "react";
import { keyFace, keyLayers, type KeyLayer } from "./key-layers";
import { Button, Canvas, CanvasText, Section } from "ink";
import pageText from "../tools/im/page-text.json";
import { CREAM, MUTED, useAccent } from "./brand";
import { Note } from "./note";
import { guideFor } from "./language-page";
import { RADIUS } from "./radii";
import { RoundedOutline } from "./rounded";
import type { InputMethodEntry, KeyRole } from "./titankeys";

type Cap = { letter?: string; label?: string; role?: KeyRole["key"]; span: number };

const letters = (row: string) => row.split("").map(letter => ({ letter, span: 1 }));
// The Titan's keys, as lib/keyboard.tsx draws them; the special keys that can carry a role are named.
const ROWS: Cap[][] = [
  letters("qwertyuiop"),
  [...letters("asdfghjkl"), { label: "⌫", role: "backspace", span: 1 }],
  [{ label: "alt", span: 1 }, ...letters("zxcvbnm"), { label: "sym", role: "sym", span: 1 }, { label: "↵", role: "enter", span: 1 }],
  [{ label: "⇧", role: "shift", span: 1.2 }, { label: "○", span: 1.2 }, { label: "◁", span: 1.2 }, { label: "", role: "space", span: 2.8 },
    { label: "□", span: 1.2 }, { label: "fn", span: 1.2 }, { label: "⇧", role: "shift", span: 1.2 }],
];
const WIDTH = 594;
const KEY = WIDTH / 10;
const ROW = 56;
const ROLE_NAMES: Record<KeyRole["key"], string> = { space: "Space", shift: "Shift", sym: "Sym", enter: "Enter", backspace: "Backspace" };

function roleText(role: KeyRole) {
  const texts = (pageText as { roles?: Record<string, Record<string, string>> }).roles;
  return texts?.[role.key]?.[role.role];
}

/**
 * A language page's drawing of the keys (owner-approved flow, language-flow.html): what each key types where it isn't its letter,
 * Shift's in orange in the corner, a dot where holding gives more, and the special keys with a role ringed and numbered, which
 * the list under it explains. For a single input method's guide; nothing where it has no keys to show.
 */
export function KeysDrawing({ im, bare = false }: { im: InputMethodEntry; bare?: boolean }) {
  const accent = useAccent();
  const keys = guideFor(im)?.keys;
  const [selected, setLayer] = useState<KeyLayer>("base");
  if (!keys) return null;
  const map = keys.map ?? {};
  const layers = keyLayers(keys);
  const layer = layers.includes(selected) ? selected : "base";
  const roles = (keys.roles ?? []).filter(role => role.key in ROLE_NAMES && roleText(role));
  if (!Object.keys(map).length && !roles.length) return null;
  const number = (key?: string) => roles.findIndex(role => role.key === key) + 1;
  const parts = [];
  for (const [r, row] of ROWS.entries()) {
    let x = 1;
    for (const [c, cap] of row.entries()) {
      const width = cap.span * KEY, y = 1 + r * ROW, id = `${r}-${c}`;
      const at = number(cap.role);
      parts.push(<RoundedOutline key={`k${id}`} x={x + 2} y={y + 2} width={width - 4} height={ROW - 4} radius={RADIUS.small}
        stroke={at ? accent : MUTED} strokeWidth={at ? 2.5 : 1} />);
      if (cap.letter) {
        const glyph = keyFace(keys, layer, cap.letter, im.id, im.id === "zh-zhuyin");
        // Keep every physical legend present, even for a layer that leaves that key unused.
        const count = Array.from(glyph).length;
        parts.push(<CanvasText key={`t${id}`} x={x + 2} y={y + 3} width={width - 4} height={ROW - 11}
          size={Math.min(24, (width - 8) / Math.max(count, 1))} colour={layer === "base" ? CREAM : accent} align="center">
          {glyph}</CanvasText>);
        parts.push(<CanvasText key={`l${id}`} x={x + 5} y={y + ROW - 17} width={24} height={15} size={13} colour={MUTED}>
          {cap.letter.toUpperCase()}</CanvasText>);
      } else {
        parts.push(<CanvasText key={`t${id}`} x={x} y={y} width={width} height={ROW} size={18} colour={at ? CREAM : MUTED} align="center">
          {cap.label ?? ""}</CanvasText>);
      }
      if (at) parts.push(<CanvasText key={`n${id}`} x={x + 6} y={y + 4} width={20} height={20} size={18} colour={accent}>{at}</CanvasText>);
      x += width;
    }
  }
  const body = <>
    {layers.length > 1 && layers.map(value => <Button key={value} selected={value === layer} onPress={() => setLayer(value)}>
      {pageText.page.keyLayers[value]}</Button>)}
    <Canvas width={WIDTH + 2} height={ROW * 4 + 2}>{parts}</Canvas>
    {ROWS.map((row, index) => {
      const entries = row.flatMap(cap => {
        if (!cap.letter) return [];
        const glyph = keyFace(keys, layer, cap.letter, im.id, im.id === "zh-zhuyin");
        return glyph ? [cap.letter.toUpperCase() + " → " + glyph] : [];
      });
      return entries.length ? <Note key={index}>{entries.join("   ·   ")}</Note> : null;
    })}
    {keys.legend ? <Note>{keys.legend}</Note> : null}
    {roles.map((role, index) => <Note key={role.key}>{`${index + 1}  ${ROLE_NAMES[role.key]}: ${roleText(role)}`}</Note>)}
  </>;
  // A card already has a section of its own.
  return bare ? body : <Section title={pageText.page.keys}>{body}</Section>;
}
