import languages from "../android/assets/im/languages.json";
import pageText from "../tools/im/page-text.json";
import type { Guide, InputMethodEntry } from "./titankeys";

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

export function languagePage(id: string): LanguagePage | undefined {
  return (pages as Record<string, LanguagePage>)[id];
}

export const globalSettings = $global.settings as PageSetting[];

export function availableSettings(settings: PageSetting[], level: PageSetting["level"], method?: string) {
  return settings.filter(row => row.available && row.level === level &&
    (level !== "input-method" || row.methods?.includes(method ?? "")));
}

export function unavailableNotes(settings: PageSetting[], language: string) {
  const lines = settings.flatMap(row => {
    if (row.level !== "language" || row.available || !row.reason) return [];
    if (row.id === "spelling" || row.id === "chineseCharacters") return [];
    const note = pageText.notAvailable[row.reason].replaceAll("{X}", language);
    return [row.reason === "englishOnly" || row.reason === "chineseOnly" ? `${pageText.settings[row.id]}: ${note}` : note];
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

export function practiceMethod(methods: InputMethodEntry[], picked: string | null) {
  return methods.find(method => method.id === picked) ?? methods[0];
}

export function practicePrompt(method: InputMethodEntry) {
  const sample = guideFor(method)?.practice;
  return sample ? pageText.page.trySample.replace("{sample}", sample) : undefined;
}
