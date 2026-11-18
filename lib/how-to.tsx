import { Section } from "ink";
import { textFor, translator } from "./language-copy";
import { guideFor } from "./language-page";
import { Note } from "./note";
import type { InputMethodEntry } from "./titankeys";


export function HowTo({ methods, language }: { methods: InputMethodEntry[]; language: string }) {
  const pageText = textFor(language);
  const t = translator(methods.length === 1 ? methods[0].id : language);
  const families: Record<string, string[]> = pageText.points;
  const guide = methods[0] && guideFor(methods[0]);
  const shared = guide?.family && methods.every(method => guideFor(method)?.family === guide.family)
    ? families[guide.family] : undefined;
  const lines = methods.length === 1 && guide?.points?.length ? guide.points : shared;
  return lines?.length ? <Section title={pageText.page.how}>
    {lines.map((line, index) => <Note key={index} accessible>{t(line)}</Note>)}
  </Section> : null;
}
