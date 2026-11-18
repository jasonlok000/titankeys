import { useState } from "react";
import { Button, Section, TextInput } from "ink";
import { shownName } from "../app/settings/languages/index";
import type { InputMethodEntry } from "./titankeys";

/**
 * A language page's Try it (owner-approved flow, 2026-11-17): a field to type in with the language's input methods that are on,
 * its sample as the hint. F: draft wording. Nothing typed here is kept.
 */
export function TryIt({ methods }: { methods: InputMethodEntry[] }) {
  const [text, setText] = useState("");
  const [picked, setPicked] = useState<string | null>(null);
  if (methods.length === 0) return null;
  // With more than one on, chips pick whose sample to try; the keyboard types with whichever is on (Sym switches).
  const chosen = methods.find(im => im.id === picked) ?? methods[0];
  const sample = (chosen.guide?.practice || methods.find(im => im.guide?.practice)?.guide?.practice) || undefined;
  return <Section title="Try it">
    {methods.length > 1 && methods.map(im => <Button key={im.id} selected={im.id === chosen.id} onPress={() => setPicked(im.id)}>
      {shownName(im.name, im.nameEnglish, im.script)}</Button>)}
    <TextInput label="F: Type here" value={text} onChange={setText} description={sample ? `F: Try ${sample}` : undefined} />
  </Section>;
}
