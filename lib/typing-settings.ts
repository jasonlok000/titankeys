import { availableSettings, languagePage, languagesOn } from "./language-page";
import type { LanguageSetting, LanguageValues, Settings, State } from "./titankeys";

export function typingLanguages(settings: Pick<State, "inputMethods" | "availableInputMethods">, picked?: string) {
  const enabled = languagesOn(settings).map(row => row.language);
  return { enabled, editing: enabled.find(language => language.id === picked) ?? enabled[0] };
}

export function typingRows(language: string) {
  const ids: LanguageSetting[] = ["fixes", "capitals", "sentenceSpaces", "commaSpace", "doubleSpace", "hold", "swipe", "shorthand"];
  const available = availableSettings(languagePage(language)?.settings ?? [], "language");
  return ids.filter(id => available.some(row => row.id === id));
}

/** Preserve the native defaults for English alone. The bridge also mirrors that write into English's own setting. */
export function typingUpdate(settings: Pick<State, "inputMethods">, language: string | undefined,
    values: Partial<LanguageValues>): Partial<Settings> {
  if (!language || (language === "en" && settings.inputMethods.every(id => id === "en"))) return values;
  return { languageSettings: { [language]: values } };
}
