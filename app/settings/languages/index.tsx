import { Row, Screen, Section, navigate, useSnapshot } from "ink";
import { chevronRight } from "ink/icons";
import { type InputMethodEntry, type Language, type LanguageValues, type State, state } from "../../../lib/titankeys";
import { Note } from "../../../lib/note";
import pageText from "../../../tools/im/page-text.json";

/**
 * The most languages on, and the most input methods on within one (owner, 2026-11-17: "Don't allow selecting more than 4"),
 * as the keyboard's InputMethod.MAX_LANGUAGES and MAX_PER_LANGUAGE. No other total. A setting with more keeps them.
 */
export const MAX_LANGUAGES = 4;
export const MAX_PER_LANGUAGE = 4;

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

/**
 * Whether the typing settings that differ by language live on each language's page (owner, 2026-11-17): yes as soon as
 * anything but English alone is on. With English alone, Settings' main page shows them as it always has.
 */
export const typingPerLanguage = (settings: State) => {
  const on = languagesOn(settings);
  return !(on.length === 1 && on[0].language.id === "en");
};

/** A language's own typing values: today's single values until it sets its own (TypingSettings.PER_LANGUAGE). */
export const languageValues = (settings: State, id: string): LanguageValues => settings.languageSettings[id] ?? settings;

/**
 * Scripts whose letters Ink can't place yet: it draws text without shaping, so Devanagari's vowel signs would sit after
 * their letter (हिन्दी). Their names show in English until it can (docs/languages.md, "Hindi").
 */
const UNSHAPED = new Set(["Deva"]);

/** The name Settings shows for a language or an input method: its own, or English's where Ink can't draw its script. */
export const shownName = (name: string, nameEnglish: string, script: string) => UNSHAPED.has(script) ? nameEnglish : name;

/** Settings' value for Languages: the names of the ones that are on, in order. */
export const languagesLabel = (settings: State) =>
  languagesOn(settings).map(({ language }) => shownName(language.name, language.nameEnglish, language.script)).join(", ");

/** Under a language's name, show its English name and experimental status where applicable. */
export const languageSubtitle = (language: Language) =>
  language.experimental ? `${language.nameEnglish} · ${pageText.status.Experimental}` :
    language.nameEnglish === language.name ? undefined : language.nameEnglish;

const scripts: Record<string, string> = pageText.scripts;

/**
 * Every language. The ones that are on come first, in the switcher's order. Each opens its own page, where its input methods
 * are turned on and its own options are (the Gboard way: owner, 2026-11-16).
 */
export default function LanguagesPage() {
  const result = useSnapshot(state);
  const title = pageText.list.title;
  const subtitle = pageText.list.limit.replace("{max}", String(MAX_LANGUAGES));
  if (result.status !== "ready") return <Screen title={title} subtitle={subtitle} />;
  const rows = languagesOf(result.data);
  const on = languagesOn(result.data);
  const open = (row: LanguageRow) => navigate({ path: "/settings/languages/language", params: { language: row.language.id } });
  const row = (each: LanguageRow) => <Row key={each.language.id}
    title={shownName(each.language.name, each.language.nameEnglish, each.language.script)}
    subtitle={each.on.length > 1 ? each.on.map(im => im.name).join(", ") : languageSubtitle(each.language)}
    endIcon={chevronRight} onPress={() => open(each)} />;
  const groups = new Map<string, LanguageRow[]>();
  for (const each of rows.filter(each => each.on.length === 0))
    groups.set(each.language.script, [...(groups.get(each.language.script) ?? []), each]);
  return <Screen title={title} subtitle={subtitle}>
    <Section title={pageText.list.on}>
      {on.map(row)}
      {result.data.inputMethods.length === 1 && <Note>{pageText.list.onlyOne}</Note>}
    </Section>
    {[...groups].map(([script, each]) => <Section key={script} title={scripts[script] ?? script}>{each.map(row)}</Section>)}
  </Screen>;
}
