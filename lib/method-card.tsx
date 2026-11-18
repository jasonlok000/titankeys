import { type ReactElement, useState } from "react";
import { Field, Section } from "ink";
import pageText from "../tools/im/page-text.json";
import languages from "../android/assets/im/languages.json";
import { Toggle } from "./app-haptics";
import { KeysDrawing } from "./keys-drawing";
import { Note } from "./note";
import type { InputMethodEntry } from "./titankeys";

/** A setting on a language page, as tools/im/make.py writes it to languages.json (C's schema). */
export type PageSetting = { id: string; level: "language" | "input-method"; available: boolean; reason?: string; methods?: string[] };
export type LanguagePage = { points: string[]; roles: unknown[]; groups: { title: string; methods: string[] }[]; settings: PageSetting[] };

/** A language's page data from languages.json, or null where it has none. */
export function languagePage(id: string): LanguagePage | null {
  return (languages as Record<string, LanguagePage>)[id] ?? null;
}

const text = pageText as { settings?: Record<string, string> };

/**
 * One input method on a language with several (owner-approved flow, language-flow.html: 中文's cards): its switch and line, and,
 * opened, How it works, its keys and its own settings (lazySounds on Yale and Jyutping, radicalPreview on Cangjie and Quick).
 */
export function MethodCard({ im, name, subtitle, value, onChange, settings }: {
  im: InputMethodEntry;
  name: string;
  subtitle?: string;
  value: boolean;
  onChange: (value: boolean) => void;
  settings: PageSetting[];
}) {
  const [open, setOpen] = useState(false);
  const guide = im.guide;
  const points = guide?.points?.length ? guide.points : im.howTo;
  const own = settings.filter(setting => setting.level === "input-method" && setting.available && setting.methods?.includes(im.id));
  return <>
    <Toggle label={name} subtitle={guide?.line || subtitle} value={value} onChange={onChange} />
    {/* F: draft labels. */}
    <Field label={open ? "F: Hide how it works" : "F: How it works"} onPress={() => setOpen(!open)} />
    {open && points.map((line, index) => <Note key={`p${index}`}>{line}</Note>)}
    {open && <KeysDrawing im={im} bare />}
    {open && own.map(setting => <Note key={setting.id}>{`F: ${text.settings?.[setting.id] ?? setting.id} (its switch comes with B's work)`}</Note>)}
  </>;
}

/** A language's input methods under its groups' titles (中文: Cantonese, Mandarin, By shape); ungrouped ones last. */
export function MethodGroups({ page, methods, card }: {
  page: LanguagePage | null;
  methods: InputMethodEntry[];
  card: (im: InputMethodEntry) => ReactElement;
}) {
  const groups = page?.groups ?? [];
  const grouped = new Set(groups.flatMap(group => group.methods));
  const rest = methods.filter(im => !grouped.has(im.id));
  return <>
    {groups.map(group => <Section key={group.title} title={group.title}>
      {group.methods.map(id => methods.find(im => im.id === id)).filter(im => im !== undefined).map(im => card(im!))}
    </Section>)}
    {rest.length > 0 && <Section title={groups.length ? "F: Other input methods" : "Input methods"}>{rest.map(card)}</Section>}
  </>;
}

const reasons = pageText as { notAvailable?: Record<string, string> };

/**
 * What this language can't do, and why (owner-approved flow: settings levels with "not available" notes): each language-level
 * setting languages.json marks unavailable, in page-text's notAvailable words, {X} as the language's English name. F: drafts
 * where page-text has none yet. Input-method rows that don't apply to a method aren't listed: its card simply hasn't them.
 */
export function NotAvailable({ page, english }: { page: LanguagePage | null; english: string }) {
  const rows = (page?.settings ?? []).filter(setting => setting.level === "language" && !setting.available && setting.reason);
  if (!rows.length) return null;
  const said = new Set<string>();
  const lines = rows.map(row => {
    const words = reasons.notAvailable?.[row.reason!];
    return words ? words.replaceAll("{X}", english) : `F: notAvailable.${row.reason}`;
  }).filter(line => !said.has(line) && said.add(line));
  // F: draft title.
  return <Section title="Not in this language">{lines.map(line => <Note key={line}>{line}</Note>)}</Section>;
}
