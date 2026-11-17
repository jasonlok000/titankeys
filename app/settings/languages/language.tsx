import { Field, Screen, Section, useAction, useRouteParams, useSnapshot } from "ink";
import { type InputMethodEntry, state, update } from "../../../lib/titankeys";
import { Toggle } from "../../../lib/app-haptics";
import { Note } from "../../../lib/note";
import { MAX_LANGUAGES, MAX_PER_LANGUAGE, languagesOf, languagesOn, shownName } from "./index";
import { spellingLabel } from "../spelling";
import { chineseLabel } from "../chinese";
import { HowTo } from "../../../lib/how-to";

/**
 * One language: its input methods, each with a switch, and its own options (English's spelling, Chinese's characters). The
 * route's `language` parameter is the language's id. English can't go off while it's the only one, and eight is the most.
 */
export default function LanguagePage() {
  const id = useRouteParams().language;
  const result = useSnapshot(state);
  const save = useAction(update);
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
  return <Screen title={shownName(language.name, language.nameEnglish, language.script)} subtitle={subtitle}>
    <Section title={methods.length > 1 ? "Input methods" : "Input method"}>
      {methods.map(im => <Toggle key={im.id} label={shownName(im.name, im.nameEnglish, im.script)}
        subtitle={im.nameEnglish === im.name || methods.length === 1 ? undefined : im.nameEnglish}
        value={inputMethods.includes(im.id)} onChange={value => set(im, value)} />)}
      {/* F: draft. */}
      {languagesFull && <Note>Four languages at a time, tops. Turn one off first.</Note>}
      {methodsFull && methods.length > MAX_PER_LANGUAGE && <Note>Four at a time in one language, tops. Turn one off first.</Note>}
    </Section>
    <HowTo methods={methods} />
    {language.id === "en" && <Section title="Options">
      <Field label="Spelling" href="/settings/spelling">{spellingLabel(result.data)}</Field>
    </Section>}
    {language.id === "zh" && <Section title="Options">
      <Field label="Characters" href="/settings/chinese">{chineseLabel(result.data)}</Field>
    </Section>}
  </Screen>;
}
