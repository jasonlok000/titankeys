import { useState } from "react";
import { Button, Section, TextInput } from "ink";
import { textFor } from "./language-copy";
import { practiceMethod, practicePrompt } from "./language-page";
import { Note } from "./note";
import type { InputMethodEntry } from "./titankeys";

/** The selected practice method applies to this field for as long as it has focus. */
export function TryIt({ methods, language }: { methods: InputMethodEntry[]; language: string }) {
  const pageText = textFor(language);
  const [text, setText] = useState("");
  const [picked, setPicked] = useState<string | null>(null);
  const chosen = practiceMethod(methods, picked);
  return <Section title={pageText.page.tryIt}>
    {!chosen && <Note>{pageText.page.turnOnToTry}</Note>}
    {chosen && <>
      {methods.length > 1 && methods.map(im => <Button key={im.id} selected={im.id === chosen.id} onPress={() => setPicked(im.id)}>
        {im.name}</Button>)}
      <TextInput label={pageText.page.typeHere} value={text} onChange={setText} action="return"
        description={practicePrompt(chosen, pageText)} privateImeOptions={`com.titankeys.practiceMethod=${chosen.id}`} />
    </>}
  </Section>;
}
