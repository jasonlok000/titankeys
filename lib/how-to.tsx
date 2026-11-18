import { Section } from "ink";
import type { InputMethodEntry } from "./titankeys";
import { Note } from "./note";
import { shownName } from "../app/settings/languages/index";

/**
 * A language's how-tos (owner, 2026-11-17: "we shd have basic instructions in text in each language specifically"): what's
 * special to typing each of its input methods, F's lines from tools/im/howto.json. Under the input method's name where the
 * language has several; nothing where none has any. The section title is an F: draft.
 */
export function HowTo({ methods }: { methods: InputMethodEntry[] }) {
  // The guide's short points where C has written them, else the how-to's lines.
  const lines = (im: InputMethodEntry) => im.guide?.points?.length ? im.guide.points : im.howTo;
  const withLines = methods.filter(im => lines(im).length > 0);
  if (withLines.length === 0) return null;
  const named = methods.length > 1;
  // F: draft title.
  return <Section title="How it works">
    {withLines.flatMap(im => lines(im).map((line, index) => <Note key={`${im.id}-${index}`}>
      {(named && index === 0 ? `${shownName(im.name, im.nameEnglish, im.script)}: ` : "") + line}
    </Note>))}
  </Section>;
}
