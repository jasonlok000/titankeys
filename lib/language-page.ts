import languages from "../android/assets/im/languages.json";
import pageText from "../tools/im/page-text.json";
import type { Guide, InputMethodEntry, Language, LanguageValues, State } from "./titankeys";

type SettingId = keyof typeof pageText.settings;
type Reason = keyof typeof pageText.notAvailable;

export type PageSetting = {
  id: SettingId;
  level: "global" | "language" | "input-method";
  available: boolean;
  reason?: Reason;
  methods?: string[];
};

export type LanguagePage = {
  groups: { title: string; methods: string[] }[];
  settings: PageSetting[];
};

const { $global, ...pages } = languages;

export type LanguageRow = { language: Language; methods: InputMethodEntry[]; on: InputMethodEntry[] };
type InstalledMethods = Pick<State, "inputMethods" | "availableInputMethods">;

/**
 * A language's stage (owner, 2026-11-22; docs/languages.md, "Language stages"): Tested where a native speaker has checked its
 * typing (it stays experimental for the rest of the app), Experimental for the other experimental ones, else Polished.
 */
export type Stage = "Polished" | "Tested" | "Experimental";
export const stageOf = (language: Pick<Language, "experimental" | "tested">): Stage =>
  !language.experimental ? "Polished" : language.tested ? "Tested" : "Experimental";

/** Every language in index order, with its enabled methods in switcher order. */
export function languagesOf({ inputMethods, availableInputMethods }: InstalledMethods): LanguageRow[] {
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
}

/** Only enabled languages, in the order they appear in the switcher. */
export function languagesOn(settings: InstalledMethods): LanguageRow[] {
  const order = settings.inputMethods.map(id => settings.availableInputMethods.find(im => im.id === id)?.language.id);
  return languagesOf(settings).filter(row => row.on.length > 0)
    .sort((a, b) => order.indexOf(a.language.id) - order.indexOf(b.language.id));
}

export const languageValues = (settings: Pick<State, keyof LanguageValues | "languageSettings">, id: string): LanguageValues =>
  settings.languageSettings[id] ?? settings;

export function listMethodCount(total: number, on: number) {
  return total > 1 && on > 0 ? pageText.list.methodCount.replace("{n}", String(on)).replace("{total}", String(total)) : undefined;
}

export function languagePage(id: string): LanguagePage | undefined {
  return (pages as Record<string, LanguagePage>)[id];
}

export const globalSettings = $global.settings as PageSetting[];

export function availableSettings(settings: PageSetting[], level: PageSetting["level"], method?: string) {
  return settings.filter(row => row.available && row.level === level &&
    (level !== "input-method" || row.methods?.includes(method ?? "")));
}

export function unavailableNotes(settings: PageSetting[], language: string, text = pageText) {
  const lines = settings.flatMap(row => {
    if (row.level !== "language" || row.available || !row.reason) return [];
    if (row.id === "spelling" || row.id === "chineseCharacters") return [];
    const note = text.notAvailable[row.reason].replaceAll("{X}", language);
    return [row.reason === "englishOnly" || row.reason === "chineseOnly" ? `${text.settings[row.id]}: ${note}` : note];
  });
  return [...new Set(lines)];
}

const englishGuide: Guide = {
  family: "english",
  keys: { capitals: true, roles: [
    { key: "shift", role: "letters" },
    { key: "sym", role: "default" },
    { key: "space", role: "default" },
    { key: "enter", role: "default" },
    { key: "backspace", role: "default" },
  ] },
  practice: pageText.page.englishSample,
};

export const guideFor = (method: InputMethodEntry) => method.guide ?? (method.id === "en" ? englishGuide : null);

/** Only show a shared role when every method assigns the same job to that key. */
export function sharedKeyRoles(methods: InputMethodEntry[]) {
  const roles = methods.map(method => guideFor(method)?.keys?.roles ?? []);
  return (roles[0] ?? []).filter(role => roles.every(keys => keys.some(key => key.key === role.key && key.role === role.role)));
}

export function practiceMethod(methods: InputMethodEntry[], picked: string | null) {
  return methods.find(method => method.id === picked) ?? methods[0];
}

export function practicePrompt(method: InputMethodEntry, text = pageText) {
  const sample = guideFor(method)?.practice;
  return sample ? text.page.trySample.replace("{sample}", sample) : undefined;
}
