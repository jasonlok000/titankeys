import { Field, Screen, back } from "ink";
import { clearWords } from "../../../lib/atlas";

export default function Reset() {
  return <Screen title="Reset learned words?" subtitle="Removes every word TitanKeys has learned. This can't be undone.">
    <Field label="Reset learned words" onPress={() => void clearWords().then(back)}>{""}</Field>
  </Screen>;
}
