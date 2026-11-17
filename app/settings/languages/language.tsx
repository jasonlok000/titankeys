import { useEffect, useState } from "react";
import { Field, Screen, Section, navigate, useAction, useRouteParams, useSnapshot } from "ink";
import { type InputMethodEntry, type LanguageSetting, type LanguageValues, markLanguageNoteSeen, state, update } from "../../../lib/titankeys";
import { Toggle } from "../../../lib/app-haptics";
import { Note } from "../../../lib/note";
import { MAX_LANGUAGES, MAX_PER_LANGUAGE, languageValues, languagesOf, languagesOn, shownName, typingPerLanguage } from "./index";
import { spellingLabel } from "../spelling";
import { chineseLabel } from "../chinese";
import { variantLabel } from "../variant";
import { fixesLabel } from "../fixes";
import { holdLabel } from "../hold";
import { sentenceSpacesLabel } from "../sentence-spaces";
import { shorthandLabel } from "../shorthand/index";
import { HowTo } from "../../../lib/how-to";

/**
 * One language: its input methods, each with a switch, and its own options (English's spelling, Chinese's characters). The
 * route's `language` parameter is the language's id. English can't go off while it's the only one, and eight is the most.
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
  if (result.status !== "ready") return <Screen title="Language" />;
  const row = languagesOf(result.data).find(each => each.language.id === id);
  if (!row) return <Screen title="Language" />;
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
  // F: draft. Under the name, once: what "experimental" means here.
  const subtitle = language.experimental
    ? `${language.nameEnglish}. Experimental: it types, and its words and fixes are still being tuned.`
    : language.nameEnglish === language.name ? undefined : language.nameEnglish;
  const settings = result.data;
  const own = languageValues(settings, language.id);
  const write = (values: Partial<LanguageValues>) => void save.run({ languageSettings: { [language.id]: values } });
  const open = (path: string) => navigate({ path, params: { language: language.id } });
  // The typing settings that differ by language, where this one has them (LanguageSettings.kt). F: draft labels.
  const rows = typingPerLanguage(settings) ? language.settings : [];
  const has = (row: LanguageSetting) => rows.includes(row);
  const doubleSpace = language.stop === "." ? "Double Space types a full stop" : `Double Space types ${language.stop}`;
  // An input method with spelling variants picks one here (Portuguese's Brasil or Portugal).
  const withVariants = methods.filter(im => im.variants.length > 1);
  const options = rows.length > 0 || language.id === "en" || language.id === "zh" || withVariants.length > 0;
  return <Screen title={shownName(language.name, language.nameEnglish, language.script)} subtitle={subtitle}>
    {/* F: draft. Once, the first time the settings move. */}
    {moved && <Note>Typing settings for each language now live on its page.</Note>}
    <Section title={methods.length > 1 ? "Input methods" : "Input method"}>
      {methods.map(im => <Toggle key={im.id} label={shownName(im.name, im.nameEnglish, im.script)}
        subtitle={im.nameEnglish === im.name || methods.length === 1 ? undefined : im.nameEnglish}
        value={inputMethods.includes(im.id)} onChange={value => set(im, value)} />)}
      {/* F: draft. */}
      {languagesFull && <Note>Four languages at a time, tops. Turn one off first.</Note>}
      {methodsFull && methods.length > MAX_PER_LANGUAGE && <Note>Four at a time in one language, tops. Turn one off first.</Note>}
    </Section>
    <HowTo methods={methods} />
    {options && <Section title="Options">
      {language.id === "en" && <Field label="Spelling" href="/settings/spelling">{spellingLabel(settings)}</Field>}
      {language.id === "zh" && <Field label="Characters" href="/settings/chinese">{chineseLabel(settings)}</Field>}
      {/* F: draft. */}
      {withVariants.map(im => <Field key={im.id} label={withVariants.length > 1 ? `Spelling, ${im.name}` : "Spelling"}
        onPress={() => navigate({ path: "/settings/variant", params: { inputMethod: im.id } })}>{variantLabel(settings, im)}</Field>)}
      {has("fixes") && <Toggle label="Fix typos as you type" value={own.autocorrect} onChange={autocorrect => write({ autocorrect })} />}
      {/* What gets fixed is English's: its kinds are English's rules. */}
      {has("fixes") && language.id === "en" && own.autocorrect && <Field label="What gets fixed" href="/settings/fixes">{fixesLabel(settings)}</Field>}
      {has("capitals") && <Toggle label="Capital letter to start a sentence" value={own.autoCapitalise}
        onChange={autoCapitalise => write({ autoCapitalise })} />}
      {has("sentenceSpaces") && <Field label="Space after a sentence" onPress={() => open("/settings/sentence-spaces")}>
        {sentenceSpacesLabel(settings, language.id)}</Field>}
      {has("commaSpace") && <Toggle label="Space after , ; :" value={own.commaSpace} onChange={commaSpace => write({ commaSpace })} />}
      {has("doubleSpace") && <Toggle label={doubleSpace} value={own.autoPunctuation}
        onChange={autoPunctuation => write({ autoPunctuation })} />}
      {has("hold") && <Field label="Hold a letter for its symbol" onPress={() => open("/settings/hold")}>
        {holdLabel(settings, language.id)}</Field>}
      {has("swipe") && <Toggle label="Swipe to type words" value={settings.swipeTyping}
        onChange={swipeTyping => void save.run({ swipeTyping })} />}
      {has("shorthand") && <Field label="Spell out shorthand" href="/settings/shorthand">{shorthandLabel(settings)}</Field>}
    </Section>}
  </Screen>;
}
