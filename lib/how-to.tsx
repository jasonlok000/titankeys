import { Section } from "ink";
import pageText from "../tools/im/page-text.json";
import { guideFor } from "./language-page";
import { Note } from "./note";
import type { InputMethodEntry } from "./titankeys";

const families: Record<string, string[]> = pageText.points;

export function HowTo({ methods }: { methods: InputMethodEntry[] }) {
  const guide = methods[0] && guideFor(methods[0]);
  const shared = guide?.family && methods.every(method => guideFor(method)?.family === guide.family)
    ? families[guide.family] : undefined;
  const lines = methods.length === 1 && guide?.points?.length ? guide.points : shared;
  return lines?.length ? <Section title={pageText.page.how}>
    {lines.map((line, index) => <Note key={index}>{line}</Note>)}
  </Section> : null;
}
