import { useState } from "react";
import { Field, Screen, Section, SettingsChoices, navigate, useAction, useSnapshot } from "ink";
import { Toggle, playHaptic } from "../../lib/app-haptics";
import { textFor } from "../../lib/language-copy";
import { availableSettings, globalSettings, languagePage, languageValues, unavailableNotes } from "../../lib/language-page";
import { Note } from "../../lib/note";
import { type LanguageSetting, type LanguageValues, state, update } from "../../lib/titankeys";
import { typingLanguages, typingRows, typingUpdate } from "../../lib/typing-settings";
import pageText from "../../tools/im/page-text.json";
import { fixesLabel } from "./fixes";
import { holdLabel } from "./hold";
import { sentenceSpacesLabel } from "./sentence-spaces";
import { shorthandLabel } from "./shorthand/index";

export default function Typing() {
  const result = useSnapshot(state);
  const save = useAction(update);
  const [picked, setPicked] = useState<string>();
  const [choosing, setChoosing] = useState(false);
  const { typing } = pageText;
  if (result.status !== "ready") return <Screen title={typing.title} />;
  const settings = result.data;
  const { enabled, editing: language } = typingLanguages(settings, picked);
  if (!language) return <Screen title={typing.title} />;

  const local = textFor(language.id);
  const wording = local.settings;
  const own = languageValues(settings, language.id);
  const write = (values: Partial<LanguageValues>) => void save.run(typingUpdate(settings, language.id, values));
  const open = (path: "/settings/hold" | "/settings/sentence-spaces") => navigate({ path, params: { language: language.id } });
  const rows = typingRows(language.id);
  const has = (id: LanguageSetting) => rows.includes(id);
  const doubleSpace = language.stop === "." ? wording.doubleSpace : local.page.languageFullStop.replace("{stop}", language.stop);
  const notes = unavailableNotes(languagePage(language.id)?.settings ?? [], language.name, local);

  return <Screen title={typing.title}>
    {enabled.length > 1 && <>
      <Field label={typing.editing} onPress={() => setChoosing(!choosing)}>{language.name}</Field>
      {choosing && <SettingsChoices value={language.id} options={enabled.map(each => ({ value: each.id, label: each.name }))}
        onChange={id => {
          playHaptic("app_picked");
          setPicked(id);
          setChoosing(false);
        }} />}
    </>}
    <Section title={language.name}>
      {has("fixes") && <Toggle label={language.id === "en" ? typing.fixes : wording.fixes} value={own.autocorrect}
        onChange={autocorrect => write({ autocorrect })} />}
      {language.id === "en" && own.autocorrect && <Field label={local.page.fixes} href="/settings/fixes">{fixesLabel(settings)}</Field>}
      {has("capitals") && <Toggle label={wording.capitals} value={own.autoCapitalise}
        onChange={autoCapitalise => write({ autoCapitalise })} />}
      {has("sentenceSpaces") && <Field label={wording.sentenceSpaces} onPress={() => open("/settings/sentence-spaces")}>
        {sentenceSpacesLabel(settings, language.id)}</Field>}
      {has("commaSpace") && <Toggle label={wording.commaSpace} value={own.commaSpace} onChange={commaSpace => write({ commaSpace })} />}
      {has("doubleSpace") && <Toggle label={doubleSpace} value={own.autoPunctuation}
        onChange={autoPunctuation => write({ autoPunctuation })} />}
      {has("hold") && <Field label={wording.hold} onPress={() => open("/settings/hold")}>{holdLabel(settings, language.id)}</Field>}
      {has("swipe") && <Toggle label={wording.swipe} value={settings.swipeTyping}
        onChange={swipeTyping => void save.run({ swipeTyping })} />}
      {has("shorthand") && <Field label={wording.shorthand} href="/settings/shorthand">{shorthandLabel(settings)}</Field>}
    </Section>
    <Section title={pageText.list.all}>
      {availableSettings(globalSettings, "global").some(row => row.id === "learn") &&
        <Toggle label={pageText.settings.learn} value={settings.learning} onChange={learning => void save.run({ learning })} />}
    </Section>
    {notes.length > 0 && <Section title={local.page.unavailable}>
      {notes.map(line => <Note key={line} accessible>{line}</Note>)}
    </Section>}
  </Screen>;
}
