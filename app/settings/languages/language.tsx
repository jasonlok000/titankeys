import { useEffect, useState } from "react";
import { Field, Screen, Section, navigate, useAction, useRouteParams, useSnapshot } from "ink";
import { type InputMethodEntry, type LanguageValues, markLanguageNoteSeen, state, update } from "../../../lib/titankeys";
import { Toggle } from "../../../lib/app-haptics";
import { Note } from "../../../lib/note";
import { MAX_LANGUAGES, MAX_PER_LANGUAGE, languageValues, languagesOf, languagesOn, shownName, typingPerLanguage } from "./index";
import { spellingLabel } from "../spelling";
import { chineseLabel } from "../chinese";
import { fixesLabel } from "../fixes";
import { holdLabel } from "../hold";
import { sentenceSpacesLabel } from "../sentence-spaces";
import { shorthandLabel } from "../shorthand/index";
import { HowTo } from "../../../lib/how-to";
import { TryIt } from "../../../lib/try-it";
import { KeysDrawing } from "../../../lib/keys-drawing";
import { MethodCard, MethodControls, MethodGroups } from "../../../lib/method-card";
import { availableSettings, languagePage, unavailableNotes, type PageSetting } from "../../../lib/language-page";
import pageText from "../../../tools/im/page-text.json";

/**
 * One language: its input methods, each with a switch, and its own options (English's spelling, Chinese's characters). The
 * route's `language` parameter is the language's id. the user keeps at least one input method on.
 */
export default function LanguagePage() {
  const id = useRouteParams().language;
  const result = useSnapshot(state);
  const save = useAction(update);
  // The first time anything but English alone is on, the typing settings move here: a line says so, once, and stays for this
  // visit (owner, 2026-11-17).
  const [moved, setMoved] = useState(false);
  const firstMove = result.status === "ready" && typingPerLanguage(result.data) && !result.data.languageNoteSeen;
  useEffect(() => {
    if (!firstMove) return;
    setMoved(true);
    void markLanguageNoteSeen();
  }, [firstMove]);
  if (result.status !== "ready") return <Screen title={pageText.page.language} />;
  const row = languagesOf(result.data).find(each => each.language.id === id);
  if (!row) return <Screen title={pageText.page.language} />;
  const { language, methods, on } = row;
  const { inputMethods } = result.data;
  // Turning one on can't make a fifth language, nor a fifth input method in this one. Turning off is always fine.
  const languagesFull = on.length === 0 && languagesOn(result.data).length >= MAX_LANGUAGES;
  const methodsFull = on.length >= MAX_PER_LANGUAGE;
  const set = (im: InputMethodEntry, value: boolean) => {
    if (value && (languagesFull || methodsFull)) return;
    const next = value ? [...inputMethods, im.id] : inputMethods.filter(other => other !== im.id);
    if (next.length === 0) return;
    void save.run({ inputMethods: next });
  };
  const subtitle = language.experimental
    ? `${language.nameEnglish}. ${pageText.status.experimentalNote}`
    : language.nameEnglish === language.name ? undefined : language.nameEnglish;
  const settings = result.data;
  const page = languagePage(language.id);
  const own = languageValues(settings, language.id);
  const write = (values: Partial<LanguageValues>) => void save.run({ languageSettings: { [language.id]: values } });
  const open = (path: string) => navigate({ path, params: { language: language.id } });
  const pageRows = page?.settings ?? [];
  const perLanguage = typingPerLanguage(settings);
  const languageRows = availableSettings(pageRows, "language")
    .filter(row => perLanguage || row.id === "spelling" || row.id === "chineseCharacters");
  const has = (id: PageSetting["id"]) => languageRows.some(row => row.id === id);
  const notes = unavailableNotes(pageRows, language.nameEnglish);
  const wording = pageText.settings;
  const doubleSpace = language.stop === "." ? wording.doubleSpace : pageText.page.languageFullStop.replace("{stop}", language.stop);
  const single = methods.length === 1 ? methods[0] : undefined;
  const options = languageRows.length > 0 || (single ? availableSettings(pageRows, "input-method", single.id).length > 0 : false);
  return <Screen title={shownName(language.name, language.nameEnglish, language.script)} subtitle={subtitle}>
    {moved && <Note>{pageText.page.moved}</Note>}
    {single && <Section title={pageText.page.method}>
      <Toggle label={shownName(single.name, single.nameEnglish, single.script)}
        value={inputMethods.includes(single.id)} onChange={value => set(single, value)} />
    </Section>}
    {options && <Section title={pageText.page.options}>
      {has("spelling") && <Field label={wording.spelling} href="/settings/spelling">{spellingLabel(settings)}</Field>}
      {has("chineseCharacters") && <Field label={wording.chineseCharacters} href="/settings/chinese">{chineseLabel(settings)}</Field>}
      {has("fixes") && <Toggle label={wording.fixes} value={own.autocorrect} onChange={autocorrect => write({ autocorrect })} />}
      {has("fixes") && language.id === "en" && own.autocorrect && <Field label={pageText.page.fixes} href="/settings/fixes">{fixesLabel(settings)}</Field>}
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
      {single && <MethodControls im={single} rows={pageRows} settings={settings} />}
    </Section>}
    {notes.length > 0 && <Section title={pageText.page.unavailable}>{notes.map(line => <Note key={line}>{line}</Note>)}</Section>}
    {!single && <MethodGroups page={page} methods={methods} card={im => <MethodCard key={im.id} im={im}
      name={shownName(im.name, im.nameEnglish, im.script)} subtitle={im.nameEnglish === im.name ? undefined : im.nameEnglish}
      value={inputMethods.includes(im.id)} onChange={value => set(im, value)}>
      <MethodControls im={im} rows={pageRows} settings={settings} />
    </MethodCard>} />}
    {languagesFull && <Note>{pageText.page.languagesFull}</Note>}
    {methodsFull && methods.length > MAX_PER_LANGUAGE && <Note>{pageText.page.methodsFull}</Note>}
    <HowTo methods={methods} />
    {single && <KeysDrawing im={single} />}
    <TryIt key={language.id} methods={on} />
  </Screen>;
}
