import { useEffect, useState } from "react";
import { Row, Screen, Section, navigate, useAction, useSnapshot } from "ink";
import { add, close } from "ink/icons";
import { allowBack, captureHyperKey, captureKey, setHyperKey, setShortcut, state, type State } from "../../../lib/atlas";

export const shortcutsLabel = ({ hyperKey, shortcuts }: State) =>
  hyperKey ? shortcuts.length === 1 ? "1 app" : `${shortcuts.length} apps` : "Off";

export default function Shortcuts() {
  const result = useSnapshot(state);
  const saveKey = useAction(setHyperKey);
  const remove = useAction((letter: string) => setShortcut(letter, null));
  const [waiting, setWaiting] = useState<"hyper" | "letter" | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  useEffect(() => {
    if (waiting === "hyper") return captureHyperKey(key => {
      // A key with another job shows why, and the screen waits for a different key.
      if (key.problem) return setProblem(`${key.label}: ${key.problem}`);
      setProblem(null);
      setWaiting(null);
      saveKey.run(key.code);
    });
    if (waiting === "letter") return captureKey(key => {
      if (key === " ") return;
      setWaiting(null);
      navigate({ path: "/settings/shortcuts/[letter]", params: { letter: key } });
    });
  }, [waiting]);
  const subtitle = "Push the hyper key and a letter to open an app. Hold the hyper key, or push it and then push the letter in 1 second.";
  if (result.status !== "ready") return <Screen title="App shortcuts" subtitle={subtitle} />;
  const { hyperKey, shortcuts, backService } = result.data;
  const toggle = (next: "hyper" | "letter") => {
    setProblem(null);
    setWaiting(waiting === next ? null : next);
  };
  return <Screen title="App shortcuts" subtitle={subtitle}>
    {!backService && <Row title="Turn on Atlas in Accessibility" subtitle="App shortcuts need this setting." wrapSubtitle
      onPress={allowBack} />}
    <Section title="Hyper key">
      <Row title={waiting === "hyper" ? "Push a key" : hyperKey ?? "Choose a key"}
        subtitle={problem ?? (waiting === "hyper" ? "The key cannot have a different job." : undefined)} wrapSubtitle
        onPress={() => toggle("hyper")}
        endAction={hyperKey && waiting !== "hyper" ? { icon: close, onPress: () => saveKey.run(0) } : undefined} />
    </Section>
    {hyperKey && <Section title="Shortcuts">
      <Row title={waiting === "letter" ? "Push a letter" : "Add shortcut"} endIcon={waiting === "letter" ? undefined : add}
        onPress={() => toggle("letter")} />
      {shortcuts.map(({ letter, name }) => <Row key={letter} title={letter} subtitle={name}
        onPress={() => navigate({ path: "/settings/shortcuts/[letter]", params: { letter } })}
        endAction={{ icon: close, onPress: () => remove.run(letter) }} />)}
    </Section>}
  </Screen>;
}
