import { useEffect, useState } from "react";
import { Stack, TextInput } from "ink";
import { type Point, watchTrace } from "./atlas";
import { KeyboardDiagram } from "./keyboard";

/** A text field for trying the keyboard, with the live touchpad trail drawn underneath. */
export function TryArea({ placeholder }: { placeholder: string }) {
  const [text, setText] = useState("");
  const [trail, setTrail] = useState<Point[]>([]);
  useEffect(() => watchTrace(event => setTrail(event.points)), []);
  return <Stack align="stretch" gap={40}>
    <TextInput value={text} onChange={setText} placeholder={placeholder}
      action="return" minLines={5} maxLines={5} autoFocus keepFocus
      style={{ border: "outline", borderWidth: 1.33, borderColour: "#8e8e93", paddingX: 21, paddingY: 23, fontSize: 30 }} />
    <KeyboardDiagram trail={trail} />
  </Stack>;
}
