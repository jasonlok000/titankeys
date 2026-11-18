import { type ReactElement, useState } from "react";
import { Canvas, Field, Icon, Rectangle, Section, Stack, navigate, useAction } from "ink";
import { expandMore, expandLess } from "ink/icons";
import { DIVIDER } from "./brand";
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
export function MethodCard({ im, value, onChange, children }: {
  im: InputMethodEntry;
  value: boolean;
  onChange: (value: boolean) => void;
  children: ReactElement;
}) {
  const pageText = textFor(im.id);
  const t = translator(im.id);
  const [open, setOpen] = useState(false);
  const guide = im.guide;
  const subtitle = guide?.line ? t(guide.line) : undefined;
  const disclosure = open ? pageText.page.hideHow : pageText.page.how;
  return <Stack gap={16}>
    <Stack axis="horizontal" gap={12} align="center">
      <Stack>
        <Toggle label={im.name} subtitle={subtitle} value={value} onChange={onChange} />
      </Stack>
      <Stack accessibilityLabel={`${im.name} · ${disclosure}`} onPress={() => setOpen(!open)}>
        <Icon size={46} name={open ? expandLess : expandMore} tone="muted" />
      </Stack>
    </Stack>
    {open && <>
      <KeysDrawing im={im} bare />
      {guide?.points?.map((line, index) => <Note key={index} accessible>{t(line)}</Note>)}
      {children}
    </>}
    <Canvas width={596} height={2}><Rectangle x={0} y={0} width={596} height={2} fill={DIVIDER} /></Canvas>
  </Stack>;
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
  const content = <>
    {groups.map(group => <Section key={group.title} title={t(group.title)}>
      {group.methods.flatMap(id => {
        const method = methods.find(im => im.id === id);
        return method ? [card(method)] : [];
      })}
    </Section>)}
    {rest.length > 0 && (groups.length ? <Section title={pageText.page.otherMethods}>{rest.map(card)}</Section> : rest.map(card))}
  </>;
  return <Section title={pageText.page.methods}>{content}</Section>;
}
