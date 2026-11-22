import { Field, Screen, Section, navigate, useAction, useRouteParams, useSnapshot } from "ink";
import { type InputMethodEntry, state, update } from "../../../lib/titankeys";
import { Toggle } from "../../../lib/app-haptics";
import { Note } from "../../../lib/note";
import { MAX_LANGUAGES, MAX_PER_LANGUAGE } from "./index";
import { spellingLabel } from "../spelling";
import { chineseLabel } from "../chinese";
import { MethodCard, MethodControls, MethodGroups } from "../../../lib/method-card";
import { availableSettings, languagePage, languagesOf, languagesOn, stageOf } from "../../../lib/language-page";
import { guidePracticeLabel, textFor } from "../../../lib/language-copy";

/**
 * One language: its input methods, each with a switch, and its own options (English's spelling, Chinese's characters). The
 * route's `language` parameter is the language's id. the user keeps at least one input method on.
 */
export default function LanguagePage() {
  const { language: routeLanguage } = useRouteParams();
  const id = typeof routeLanguage === "string" ? routeLanguage : undefined;
  const pageText = textFor(id);
  const result = useSnapshot(state);
  const save = useAction(update);
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
  const stage = stageOf(language);
  const status = pageText.status[stage];
  // F: Tested's note (page-text.json status.testedNote) is stand-in English: "Typing is checked by a native speaker. Fixes and
  // next words are still to come."
  const settings = result.data;
  const page = languagePage(language.id);
  const pageRows = page?.settings ?? [];
  const languageRows = availableSettings(pageRows, "language");
  const hasSpelling = languageRows.some(row => row.id === "spelling");
  const hasCharacters = languageRows.some(row => row.id === "chineseCharacters");
  const wording = pageText.settings;
  const single = methods.length === 1 ? methods[0] : undefined;
  const options = hasSpelling || hasCharacters || (single && availableSettings(pageRows, "input-method", single.id).length > 0);
  return <Screen title={language.name} subtitle={status}>
    {stage === "Experimental" && <Note accessible>{pageText.status.experimentalNote}</Note>}
    {stage === "Tested" && <Note accessible>{pageText.status.testedNote}</Note>}
    {single && <Section title={pageText.page.method}>
      <Toggle label={single.name}
        value={inputMethods.includes(single.id)} onChange={value => set(single, value)} />
    </Section>}
    {options && <Section title={pageText.page.options}>
      {hasSpelling && <Field label={wording.spelling} href="/settings/spelling">{spellingLabel(settings)}</Field>}
      {hasCharacters && <Field label={wording.chineseCharacters} href="/settings/chinese">{chineseLabel(settings)}</Field>}
      {single && <MethodControls im={single} rows={pageRows} settings={settings} />}
    </Section>}
    {!single && <MethodGroups language={language.id} page={page} methods={methods} card={im => <MethodCard key={im.id} im={im}
      value={inputMethods.includes(im.id)} onChange={value => set(im, value)}>
      <MethodControls im={im} rows={pageRows} settings={settings} />
    </MethodCard>} />}
    {!single && <Note accessible>{pageText.list.count.replace("{n}", String(on.length)).replace("{max}", String(methods.length))}</Note>}
    {languagesFull && <Note accessible>{pageText.page.languagesFull}</Note>}
    {methodsFull && methods.length > MAX_PER_LANGUAGE && <Note accessible>{pageText.page.methodsFull}</Note>}
    <Field label={guidePracticeLabel(language.id)}
      onPress={() => navigate({ path: "/settings/languages/guide", params: { language: language.id } })} />
  </Screen>;
}
