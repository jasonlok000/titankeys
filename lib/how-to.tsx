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
  const withLines = methods.filter(im => im.howTo.length > 0);
  if (withLines.length === 0) return null;
  const named = methods.length > 1;
  return <Section title="How to type">
    {withLines.map(im => <Note key={im.id}>
      {(named ? `${shownName(im.name, im.nameEnglish, im.script)}: ` : "") + im.howTo.join(" ")}
    </Note>)}
  </Section>;
}
