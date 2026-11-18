import { useState } from "react";
import { Button, Section, TextInput } from "ink";
import pageText from "../tools/im/page-text.json";
import { shownName } from "../app/settings/languages/index";
import { practiceMethod, practicePrompt } from "./language-page";
import { Note } from "./note";
import type { InputMethodEntry } from "./titankeys";

/** The selected practice method applies to this field for as long as it has focus. */
export function TryIt({ methods }: { methods: InputMethodEntry[] }) {
  const [text, setText] = useState("");
  const [picked, setPicked] = useState<string | null>(null);
  const chosen = practiceMethod(methods, picked);
  return <Section title={pageText.page.tryIt}>
    {!chosen && <Note>{pageText.page.turnOnToTry}</Note>}
    {chosen && <>
      {methods.length > 1 && methods.map(im => <Button key={im.id} selected={im.id === chosen.id} onPress={() => setPicked(im.id)}>
        {shownName(im.name, im.nameEnglish, im.script)}</Button>)}
      <TextInput label={pageText.page.typeHere} value={text} onChange={setText} action="return"
        description={practicePrompt(chosen)} privateImeOptions={`com.titankeys.practiceMethod=${chosen.id}`} />
    </>}
  </Section>;
}
