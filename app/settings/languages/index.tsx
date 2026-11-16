import { Row, Screen, Section, navigate, useSnapshot } from "ink";
import { chevronRight } from "ink/icons";
import { type InputMethodEntry, type Language, type State, state } from "../../../lib/titankeys";
import { Note } from "../../../lib/note";

/** The most input methods on at once: the switcher's places, W to O (InputMethod.MAX_ON). */
export const MAX_ON = 8;

/** A language with its input methods: every one it has, and the ones that are on, in the setting's order. */
export type LanguageRow = { language: Language; methods: InputMethodEntry[]; on: InputMethodEntry[] };

/** Every language, in the index's order (English first), each with its input methods. */
export const languagesOf = ({ inputMethods, availableInputMethods }: State): LanguageRow[] => {
  const rows = new Map<string, LanguageRow>();
  for (const im of availableInputMethods) {
    const row = rows.get(im.language.id) ?? { language: im.language, methods: [], on: [] };
    row.methods.push(im);
    rows.set(im.language.id, row);
  }
  for (const id of inputMethods) {
    const im = availableInputMethods.find(each => each.id === id);
    if (im) rows.get(im.language.id)!.on.push(im);
  }
  return [...rows.values()];
};

/** The languages that are on, in the order each first comes in the setting: the switcher's order. */
export const languagesOn = (settings: State): LanguageRow[] => {
  const rows = languagesOf(settings);
  const order = settings.inputMethods.map(id => settings.availableInputMethods.find(im => im.id === id)?.language.id);
  return rows.filter(row => row.on.length > 0).sort((a, b) => order.indexOf(a.language.id) - order.indexOf(b.language.id));
};

/** Settings' value for Languages: the names of the ones that are on, in order. */
export const languagesLabel = (settings: State) => languagesOn(settings).map(row => row.language.name).join(", ");

/** Under a language's name: its English name, marked experimental where it is. F: draft. */
export const languageSubtitle = (language: Language) =>
  language.experimental ? `${language.nameEnglish} · experimental` : language.nameEnglish === language.name ? undefined : language.nameEnglish;

const SCRIPTS: Record<string, string> = {
  Latn: "Latin letters", Cyrl: "Cyrillic", Grek: "Greek", Geor: "Georgian", Kore: "Korean", Hani: "Chinese",
};

/**
 * Every language. The ones that are on come first, in the switcher's order. Each opens its own page, where its input methods
 * are turned on and its own options are (the Gboard way: owner, 2026-11-16).
 */
export default function LanguagesPage() {
  const result = useSnapshot(state);
  // F: draft.
  const title = "Languages";
  const subtitle = "Pick a language to turn on how you type it. Hold Sym to hop between them.";
  if (result.status !== "ready") return <Screen title={title} subtitle={subtitle} />;
  const rows = languagesOf(result.data);
  const on = languagesOn(result.data);
  const open = (row: LanguageRow) => navigate({ path: "/settings/languages/language", params: { language: row.language.id } });
  const row = (each: LanguageRow) => <Row key={each.language.id} title={each.language.name}
    subtitle={each.on.length > 1 ? each.on.map(im => im.name).join(", ") : languageSubtitle(each.language)}
    endIcon={chevronRight} onPress={() => open(each)} />;
  const groups = new Map<string, LanguageRow[]>();
  for (const each of rows.filter(each => each.on.length === 0))
    groups.set(each.language.script, [...(groups.get(each.language.script) ?? []), each]);
  return <Screen title={title} subtitle={subtitle}>
    <Section title="On">
      {on.map(row)}
      {result.data.inputMethods.length === 1 && <Note>Just one, so Sym has nothing to switch to. Turn on another.</Note>}
    </Section>
    {[...groups].map(([script, each]) => <Section key={script} title={SCRIPTS[script] ?? script}>{each.map(row)}</Section>)}
  </Screen>;
}
