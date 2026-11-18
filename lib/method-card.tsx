import { type ReactElement, useState } from "react";
import { Field, Section, navigate, useAction } from "ink";
import { textFor, translator } from "./language-copy";
import { variantLabel } from "../app/settings/variant";
import { Toggle } from "./app-haptics";
import { KeysDrawing } from "./keys-drawing";
import { availableSettings, type LanguagePage, type PageSetting } from "./language-page";
import { Note } from "./note";
import { type InputMethodEntry, type State, update } from "./titankeys";

export function MethodControls({ im, rows, settings }: {
  im: InputMethodEntry;
  rows: PageSetting[];
  settings: State;
}) {
  const pageText = textFor(im.id);
  const save = useAction(update);
  const options = settings.methodOptions?.[im.id];
  return availableSettings(rows, "input-method", im.id).map(({ id }) => {
    if (id === "variant" && im.variants.length > 1) return <Field key={id} label={pageText.settings.variant}
      onPress={() => navigate({ path: "/settings/variant", params: { inputMethod: im.id } })}>{variantLabel(settings, im)}</Field>;
    if (id !== "lazySounds" && id !== "keyPreview") return null;
    return <Toggle key={id} label={pageText.settings[id]} value={options?.[id] ?? (id === "lazySounds")}
      onChange={enabled => save.run({ methodOptions: { [im.id]: { [id]: enabled } } })} />;
  });
}

/** The switch and details for one method on a page that offers several. */
export function MethodCard({ im, name, subtitle, value, onChange, children }: {
  im: InputMethodEntry;
  name: string;
  subtitle?: string;
  value: boolean;
  onChange: (value: boolean) => void;
  children: ReactElement;
}) {
  const pageText = textFor(im.id);
  const t = translator(im.id);
  const [open, setOpen] = useState(false);
  const guide = im.guide;
  return <>
    <Toggle label={name} subtitle={guide?.line ? t(guide.line) : subtitle} value={value} onChange={onChange} />
    <Field label={open ? pageText.page.hideHow : pageText.page.how} onPress={() => setOpen(!open)} />
    {open && <>
      {guide?.points?.map((line, index) => <Note key={index}>{t(line)}</Note>)}
      <KeysDrawing im={im} bare />
      {children}
    </>}
  </>;
}

/** The generated groups, with any ungrouped methods last. */
export function MethodGroups({ page, methods, card, language }: {
  language: string;
  page: LanguagePage | undefined;
  methods: InputMethodEntry[];
  card: (im: InputMethodEntry) => ReactElement;
}) {
  const pageText = textFor(language);
  const t = translator(language);
  const groups = page?.groups ?? [];
  const grouped = new Set(groups.flatMap(group => group.methods));
  const rest = methods.filter(im => !grouped.has(im.id));
  return <>
    {groups.map(group => <Section key={group.title} title={t(group.title)}>
      {group.methods.flatMap(id => {
        const method = methods.find(im => im.id === id);
        return method ? [card(method)] : [];
      })}
    </Section>)}
    {rest.length > 0 && <Section title={groups.length ? pageText.page.otherMethods : pageText.page.methods}>{rest.map(card)}</Section>}
  </>;
}
