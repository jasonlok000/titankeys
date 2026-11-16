import { Field, Screen, Section, useAction, useRouteParams, useSnapshot } from "ink";
import { type InputMethodEntry, state, update } from "../../../lib/titankeys";
import { Toggle } from "../../../lib/app-haptics";
import { Note } from "../../../lib/note";
import { MAX_ON, languagesOf } from "./index";
import { spellingLabel } from "../spelling";
import { chineseLabel } from "../chinese";

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
  const { language, methods } = row;
  const { inputMethods } = result.data;
  const full = inputMethods.length >= MAX_ON;
  const set = (im: InputMethodEntry, value: boolean) => {
    const next = value ? [...inputMethods, im.id] : inputMethods.filter(other => other !== im.id);
    if (next.length === 0 || next.length > MAX_ON) return;
    void save.run({ inputMethods: next });
  };
  // F: draft. Under the name, once: what "experimental" means here.
  const subtitle = language.experimental
    ? `${language.nameEnglish}. Experimental: it types, and its words and fixes are still being tuned.`
    : language.nameEnglish === language.name ? undefined : language.nameEnglish;
  return <Screen title={language.name} subtitle={subtitle}>
    <Section title={methods.length > 1 ? "Input methods" : "Input method"}>
      {methods.map(im => <Toggle key={im.id} label={im.name}
        subtitle={im.nameEnglish === im.name || methods.length === 1 ? undefined : im.nameEnglish}
        value={inputMethods.includes(im.id)} onChange={value => set(im, value)} />)}
      {full && <Note>Eight at a time, tops. Turn one off first.</Note>}
    </Section>
    {language.id === "en" && <Section title="Options">
      <Field label="Spelling" href="/settings/spelling">{spellingLabel(result.data)}</Field>
    </Section>}
    {language.id === "zh" && <Section title="Options">
      <Field label="Characters" href="/settings/chinese">{chineseLabel(result.data)}</Field>
    </Section>}
  </Screen>;
}
