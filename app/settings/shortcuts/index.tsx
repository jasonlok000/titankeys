import { useEffect, useState } from "react";
import { Field, Row, Screen, navigate, useSnapshot } from "ink";
import { add } from "ink/icons";
import { captureKey, state, type State } from "../../../lib/atlas";

export const shortcutsLabel = ({ shortcuts }: State) => shortcuts.length === 1 ? "1 app" : `${shortcuts.length} apps`;

const choose = (letter: string) => navigate({ path: "/settings/shortcuts/[letter]", params: { letter } });

export default function Apps() {
  const result = useSnapshot(state);
  const [adding, setAdding] = useState(false);
  useEffect(() => {
    if (!adding) return;
    return captureKey(key => {
      if (key === " ") return;
      setAdding(false);
      choose(key);
    });
  }, [adding]);
  const subtitle = "Push the hyper key and a letter to open its app.";
  if (result.status !== "ready") return <Screen title="Apps" subtitle={subtitle} />;
  return <Screen title="Apps" subtitle={subtitle}>
    <Row title={adding ? "Push a letter" : "Add app"} endIcon={adding ? undefined : add} onPress={() => setAdding(!adding)} />
    {result.data.shortcuts.map(({ letter, name }) => <Field key={letter} label={name} onPress={() => choose(letter)}>{letter}</Field>)}
  </Screen>;
}
