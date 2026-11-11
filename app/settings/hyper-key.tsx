import { useEffect, useState } from "react";
import { Row, Screen, useAction, useSnapshot } from "ink";
import { allowBack, captureHyperKey, setHyperKey, state, type State } from "../../lib/atlas";

export const hyperKeyLabel = ({ hyperKey }: State) => hyperKey ?? "None";

export default function HyperKey() {
  const result = useSnapshot(state);
  const save = useAction(setHyperKey);
  const [problem, setProblem] = useState<string | null>(null);
  // The screen waits for a key for as long as it is open.
  useEffect(() => captureHyperKey(key => {
    setProblem(key.problem);
    if (!key.problem) save.run(key.code);
  }), []);
  const subtitle = "Push the key that you want to use. Then hold it, or push it, and push a letter to open an app.";
  if (result.status !== "ready") return <Screen title="Hyper key" subtitle={subtitle} />;
  const { hyperKey, backService } = result.data;
  return <Screen title="Hyper key" subtitle={subtitle}>
    {!backService && <Row title="Turn on Atlas in Accessibility" subtitle="App shortcuts need this setting." wrapSubtitle
      onPress={allowBack} />}
    <Row title={hyperKey ?? "No key"} subtitle={problem ?? "Push a key to change it."} wrapSubtitle />
  </Screen>;
}
