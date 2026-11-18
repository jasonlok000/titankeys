import { useState } from "react";
import { Section, TextInput } from "ink";
import type { InputMethodEntry } from "./titankeys";

/**
 * A language page's Try it (owner-approved flow, 2026-11-17): a field to type in with the language's input methods that are on,
 * its sample as the hint. F: draft wording. Nothing typed here is kept.
 */
export function TryIt({ methods }: { methods: InputMethodEntry[] }) {
  const [text, setText] = useState("");
  if (methods.length === 0) return null;
  const sample = methods.find(im => im.guide?.practice)?.guide?.practice;
  return <Section title="Try it">
    <TextInput label="F: Type here" value={text} onChange={setText} description={sample ? `F: Try ${sample}` : undefined} />
  </Section>;
}
