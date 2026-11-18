import { useRouteParams } from "ink";
import type { SentenceSpaces, State } from "../../lib/titankeys";
import { translator } from "../../lib/language-copy";
import { ChoicePage } from "../../lib/choice";
import { languageValues } from "./languages/index";

const options = [
  { value: "one" as const, label: "One space" },
  { value: "two" as const, label: "Two spaces" },
  { value: "none" as const, label: "Don't add spaces" },
];

/** The value, or [language]'s own where its page shows the row (TypingSettings.PER_LANGUAGE). */
export const sentenceSpacesLabel = (settings: State, language?: string) => {
  const { sentenceSpaces } = language ? languageValues(settings, language) : settings;
  return translator(language)(options.find(option => option.value === sentenceSpaces)?.label ?? "One space");
};

export default function SentenceSpacesPage() {
  const language = useRouteParams().language as string | undefined;
  const t = translator(language);
  return <ChoicePage<SentenceSpaces> title={t("Space after a sentence")}
    subtitle={t("Type . ? or ! and the next letter you type gets its space. Numbers, links, ... and Mr. stay as you type them.")}
    options={options.map(option => ({ ...option, label: t(option.label) }))}
    read={settings => (language ? languageValues(settings, language) : settings).sentenceSpaces}
    write={sentenceSpaces => language ? { languageSettings: { [language]: { sentenceSpaces } } } : { sentenceSpaces }} />;
}
