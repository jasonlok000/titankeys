import { Canvas, CanvasText, Section } from "ink";
import pageText from "../tools/im/page-text.json";
import { CREAM, MUTED, useAccent } from "./brand";
import { Note } from "./note";
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
  return texts?.[role.key]?.[role.role] ?? `F: ${role.key}.${role.role}`;
}

/**
 * A language page's drawing of the keys (owner-approved flow, language-flow.html): what each key types where it isn't its letter,
 * Shift's in orange in the corner, a dot where holding gives more, and the special keys with a role ringed and numbered, which
 * the list under it explains. For a single input method's guide; nothing where it has no keys to show.
 */
export function KeysDrawing({ im }: { im: InputMethodEntry }) {
  const accent = useAccent();
  const keys = im.guide?.keys;
  if (!keys) return null;
  const map = keys.map ?? {}, shift = keys.shift ?? {}, held = keys.held ?? {};
  const roles = (keys.roles ?? []).filter(role => role.key in ROLE_NAMES);
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
        const typed = map[cap.letter];
        parts.push(<CanvasText key={`t${id}`} x={x} y={y} width={width} height={ROW} size={24} colour={CREAM} align="center">
          {typed ?? cap.letter.toUpperCase()}</CanvasText>);
        if (typed) parts.push(<CanvasText key={`l${id}`} x={x + 6} y={y + ROW - 24} width={22} height={20} size={18} colour={MUTED}>
          {cap.letter}</CanvasText>);
        if (shift[cap.letter]) parts.push(<CanvasText key={`s${id}`} x={x + width - 26} y={y + 5} width={22} height={20} size={18}
          colour={accent} align="end">{shift[cap.letter]}</CanvasText>);
        else if (held[cap.letter]) parts.push(<CanvasText key={`h${id}`} x={x + width - 20} y={y + 2} width={16} height={20} size={18}
          colour={accent} align="end">•</CanvasText>);
      } else {
        parts.push(<CanvasText key={`t${id}`} x={x} y={y} width={width} height={ROW} size={18} colour={at ? CREAM : MUTED} align="center">
          {cap.label ?? ""}</CanvasText>);
      }
      if (at) parts.push(<CanvasText key={`n${id}`} x={x + 6} y={y + 4} width={20} height={20} size={18} colour={accent}>{at}</CanvasText>);
      x += width;
    }
  }
  // F: draft title.
  return <Section title="The keys">
    <Canvas width={WIDTH + 2} height={ROW * 4 + 2}>{parts}</Canvas>
    {keys.legend ? <Note>{keys.legend}</Note> : null}
    {roles.map((role, index) => <Note key={role.key}>{`${index + 1}  ${ROLE_NAMES[role.key]}: ${roleText(role)}`}</Note>)}
  </Section>;
}
