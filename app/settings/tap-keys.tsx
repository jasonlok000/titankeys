import { useEffect, useState } from "react";
import { Row, Screen, useAction, useSnapshot } from "ink";
import { add, close } from "ink/icons";
import { captureKey, state, tapKeyLabel, update } from "../../lib/atlas";

export default function TapKeys() {
  const result = useSnapshot(state);
  const save = useAction(update);
  const [adding, setAdding] = useState(false);
  const keys = result.status === "ready" ? result.data.tapKeys : "";
  useEffect(() => {
    if (!adding) return;
    return captureKey(key => {
      setAdding(false);
      if (!keys.includes(key)) save.run({ tapKeys: keys + key });
    });
  }, [adding, keys]);
  return <Screen title="Tap keys" subtitle="Lightly tap a key you add here to type it.">
    {[...keys].map(key => <Row key={key} title={tapKeyLabel(key)}
      endAction={{ icon: close, onPress: () => save.run({ tapKeys: keys.replace(key, "") }) }} />)}
    {adding ? <Row title="Add key" subtitle="Press a letter or Space to add it." />
      : <Row title="Add key" endIcon={add} onPress={() => setAdding(true)} />}
  </Screen>;
}
