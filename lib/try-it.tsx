import { useState } from "react";
import { Canvas, CanvasText, Field, Section, Stack, TextInput, back } from "ink";
import { DIVIDER, MUTED, useAccent } from "./brand";
import { textFor } from "./language-copy";
import { practiceMethod, practicePrompt } from "./language-page";
import { RADIUS } from "./radii";
import { RoundedOutline } from "./rounded";
import type { InputMethodEntry } from "./titankeys";

function PracticeChip({ name, selected, onPress }: { name: string; selected: boolean; onPress: () => void }) {
  const accent = useAccent();
  const colour = selected ? accent : MUTED;
  return <Stack accessibilityLabel={name} accessibilitySelected={selected} onPress={onPress}>
    <Canvas width={212} height={54}>
      <RoundedOutline x={1} y={1} width={210} height={52} radius={RADIUS.small}
        stroke={selected ? accent : DIVIDER} strokeWidth={2} />
      <CanvasText x={9} y={1} width={194} height={52} size={25} colour={colour} align="center">{name}</CanvasText>
    </Canvas>
  </Stack>;
}

/** The selected practice method applies to this field for as long as it has focus. */
export function TryIt({ methods, language }: { methods: InputMethodEntry[]; language: string }) {
  const pageText = textFor(language);
  const [text, setText] = useState("");
  const [picked, setPicked] = useState<string | null>(null);
  const chosen = practiceMethod(methods, picked);
  const chipRows = [];
  for (let index = 0; index < methods.length; index += 2) {
    chipRows.push(methods.slice(index, index + 2));
  }
  return <Section title={pageText.page.tryIt}>
    {!chosen && <Field label={pageText.page.turnOnToTry} onPress={back} />}
    {chosen && <>
      {methods.length > 1 && <Stack gap={10}>
        {chipRows.map(row => <Stack key={row[0].id} axis="horizontal" gap={10}>
          {row.map(im => <PracticeChip key={im.id} name={im.name} selected={im.id === chosen.id} onPress={() => setPicked(im.id)} />)}
        </Stack>)}
      </Stack>}
      <TextInput label={pageText.page.typeHere} value={text} onChange={setText} action="return"
        description={practicePrompt(chosen, pageText)} privateImeOptions={`com.titankeys.practiceMethod=${chosen.id}`} />
    </>}
  </Section>;
}
